package com.zanzibar.workflow.service;

import com.zanzibar.workflow.dto.request.ExecuteTransitionRequest;
import com.zanzibar.workflow.dto.response.TransitionResponse;
import com.zanzibar.workflow.enums.TriggerType;
import com.zanzibar.workflow.enums.WorkflowStatus;
import com.zanzibar.workflow.exception.WorkflowException;
import com.zanzibar.workflow.model.WorkflowDefinition;
import com.zanzibar.workflow.model.WorkflowStageConfig;
import com.zanzibar.workflow.model.WorkflowTransitionLog;
import com.zanzibar.workflow.repository.WorkflowDefinitionRepository;
import com.zanzibar.workflow.repository.WorkflowStageConfigRepository;
import com.zanzibar.workflow.repository.WorkflowTransitionLogRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class WorkflowEngineService {

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowStageConfigRepository stageRepository;
    private final WorkflowTransitionLogRepository logRepository;

    public WorkflowEngineService(
        WorkflowDefinitionRepository definitionRepository,
        WorkflowStageConfigRepository stageRepository,
        WorkflowTransitionLogRepository logRepository
    ) {
        this.definitionRepository = definitionRepository;
        this.stageRepository = stageRepository;
        this.logRepository = logRepository;
    }

    @Transactional
    public TransitionResponse validateTransition(
        String tenantKey,
        String clientId,
        ExecuteTransitionRequest request
    ) {
        String idempotencyKey = request.idempotencyKey().trim();
        WorkflowTransitionLog previous = logRepository
            .findByTenantKeyAndIdempotencyKey(tenantKey, idempotencyKey)
            .orElse(null);
        if (previous != null) {
            return returnExistingOrReject(previous, request);
        }

        WorkflowDefinition workflow = definitionRepository
            .findByIdAndTenantKey(request.workflowDefinitionId(), tenantKey)
            .orElseThrow(() -> new WorkflowException(
                HttpStatus.NOT_FOUND,
                "Workflow not found: " + request.workflowDefinitionId()
            ));

        if (!workflow.getWorkflowVersion().equals(request.workflowVersion())) {
            throw new WorkflowException(HttpStatus.CONFLICT,
                "The requested workflow version does not match the stored version.");
        }

        String fromStatus = normalize(request.currentStatus());
        String actionKey = normalize(request.actionKey());
        WorkflowStageConfig stage = authorize(workflow, fromStatus, actionKey,
            request.triggerType(), request.actorId(), request.actorRole(), request.comments());

        WorkflowTransitionLog log = new WorkflowTransitionLog();
        log.setTenantKey(tenantKey);
        log.setClientId(clientId);
        log.setWorkflowDefinitionId(workflow.getId());
        log.setWorkflowVersion(workflow.getWorkflowVersion());
        log.setSubjectId(request.subjectId().trim());
        log.setFromStatus(fromStatus);
        log.setToStatus(normalize(stage.getToStatus()));
        log.setActionKey(actionKey);
        log.setTriggerType(stage.getTriggerType());
        log.setActorId(request.actorId().trim());
        log.setActorRole(normalize(request.actorRole()));
        log.setComments(request.comments());
        log.setIdempotencyKey(idempotencyKey);

        try {
            WorkflowTransitionLog saved = logRepository.saveAndFlush(log);
            return response(saved, "The configured transition is valid. The calling system "
                + "must persist the subject status separately.");
        } catch (DataIntegrityViolationException exception) {
            // A concurrent request may win the unique idempotency-key insert.
            // Do not claim success for this request; caller can retry the same key.
            throw new WorkflowException(HttpStatus.CONFLICT,
                "A request with this idempotency key was processed concurrently. "
                    + "Retry the same request with the same key.", exception);
        }
    }

    /**
     * The transition rules shared by validate-only transitions and workflow
     * instances: returns the configured stage for this status and action, or
     * rejects the request when the workflow, trigger, actor role or comments
     * do not satisfy it.
     */
    public WorkflowStageConfig authorize(
        WorkflowDefinition workflow,
        String fromStatus,
        String actionKey,
        TriggerType triggerType,
        String actorId,
        String actorRole,
        String comments
    ) {
        if (workflow.getStatus() == WorkflowStatus.DRAFT) {
            throw new WorkflowException(HttpStatus.CONFLICT,
                "Transitions cannot use a draft workflow.");
        }
        if (workflow.getStatus() == WorkflowStatus.PUBLISHED && !workflow.isActive()) {
            throw new WorkflowException(HttpStatus.CONFLICT,
                "The published workflow is inactive and cannot accept new transitions.");
        }
        // Archived versions remain valid for subjects pinned to that version.

        String from = normalize(fromStatus);
        String action = normalize(actionKey);
        WorkflowStageConfig stage = stageRepository
            .findByWorkflowDefinition_IdAndFromStatusAndActionKeyAndEnabledTrue(
                workflow.getId(), from, action
            )
            .orElseThrow(() -> new WorkflowException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Action " + action + " is not configured for status " + from
                    + " in workflow version " + workflow.getWorkflowVersion() + "."));

        if (stage.getTriggerType() != triggerType) {
            throw new WorkflowException(HttpStatus.UNPROCESSABLE_ENTITY,
                "The action requires trigger type " + stage.getTriggerType()
                    + ", not " + triggerType + ".");
        }
        validateActor(stage, actorId, actorRole);
        if (stage.isRequiresComments() && isBlank(comments)) {
            throw new WorkflowException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Comments are required for action " + action + ".");
        }
        return stage;
    }

    private void validateActor(WorkflowStageConfig stage, String actorId, String actorRole) {
        String role = normalize(actorRole);
        if (isBlank(actorId)) {
            throw new WorkflowException(HttpStatus.FORBIDDEN, "Actor identity is required.");
        }

        if (stage.getTriggerType() == TriggerType.SYSTEM
                || stage.getTriggerType() == TriggerType.EXTERNAL_EVENT) {
            if (!WorkflowRegistryService.SYSTEM_ROLE.equals(role)
                    || !WorkflowRegistryService.SYSTEM_ROLE.equals(
                        normalize(stage.getAssignedRole()))) {
                throw new WorkflowException(HttpStatus.FORBIDDEN,
                    "This action requires an authorized SYSTEM caller.");
            }
            return;
        }

        boolean allowedRole = Arrays.stream(stage.getAssignedRole().split(","))
            .map(this::normalize)
            .anyMatch(role::equals);
        // Administrators are not implicitly authorized to perform every human
        // action. Add an explicit administrator role to a stage when policy allows it.
        if (!allowedRole) {
            throw new WorkflowException(HttpStatus.FORBIDDEN,
                "Role " + role + " is not permitted to execute action "
                    + stage.getActionKey() + ".");
        }
    }

    private TransitionResponse returnExistingOrReject(
        WorkflowTransitionLog previous,
        ExecuteTransitionRequest request
    ) {
        boolean sameRequest =
            previous.getWorkflowDefinitionId().equals(request.workflowDefinitionId())
            && previous.getWorkflowVersion().equals(request.workflowVersion())
            && previous.getSubjectId().equals(safeTrim(request.subjectId()))
            && previous.getFromStatus().equals(normalize(request.currentStatus()))
            && previous.getActionKey().equals(normalize(request.actionKey()))
            && previous.getTriggerType() == request.triggerType()
            && Objects.equals(previous.getActorId(), safeTrim(request.actorId()))
            && Objects.equals(previous.getActorRole(), normalize(request.actorRole()))
            && Objects.equals(previous.getComments(), request.comments());

        if (!sameRequest) {
            throw new WorkflowException(HttpStatus.CONFLICT,
                "This idempotency key was already used for a different request.");
        }
        return response(previous, "This transition request was already validated.");
    }

    private TransitionResponse response(WorkflowTransitionLog log, String message) {
        return new TransitionResponse(
            log.getWorkflowDefinitionId(), log.getWorkflowVersion(), log.getSubjectId(),
            log.getFromStatus(), log.getToStatus(), log.getActionKey(), "VALIDATED",
            message, log.getId()
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.trim().toUpperCase(Locale.ROOT).replaceFirst("^ROLE_", "");
    }
}
