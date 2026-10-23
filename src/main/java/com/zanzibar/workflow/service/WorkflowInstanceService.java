package com.zanzibar.workflow.service;

import com.zanzibar.workflow.dto.request.InstanceActionRequest;
import com.zanzibar.workflow.dto.request.StartInstanceRequest;
import com.zanzibar.workflow.dto.response.AvailableActionResponse;
import com.zanzibar.workflow.dto.response.InstanceActionResponse;
import com.zanzibar.workflow.dto.response.InstanceResponse;
import com.zanzibar.workflow.dto.response.TransitionHistoryResponse;
import com.zanzibar.workflow.enums.TriggerType;
import com.zanzibar.workflow.exception.WorkflowException;
import com.zanzibar.workflow.model.WorkflowDefinition;
import com.zanzibar.workflow.model.WorkflowInstance;
import com.zanzibar.workflow.model.WorkflowStageConfig;
import com.zanzibar.workflow.model.WorkflowTransitionLog;
import com.zanzibar.workflow.repository.WorkflowDefinitionRepository;
import com.zanzibar.workflow.repository.WorkflowInstanceRepository;
import com.zanzibar.workflow.repository.WorkflowStageConfigRepository;
import com.zanzibar.workflow.repository.WorkflowTransitionLogRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stateful workflow execution: the engine stores each subject's current status
 * and applies actions atomically. Concurrent actions on one instance are
 * serialized by a row lock, so two conflicting actions can never both succeed.
 */
@Service
public class WorkflowInstanceService {

    private static final Pattern ACTION_KEY_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]*");

    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowStageConfigRepository stageRepository;
    private final WorkflowTransitionLogRepository logRepository;
    private final WorkflowResolutionService resolutionService;
    private final WorkflowEngineService engineService;

    public WorkflowInstanceService(
        WorkflowInstanceRepository instanceRepository,
        WorkflowDefinitionRepository definitionRepository,
        WorkflowStageConfigRepository stageRepository,
        WorkflowTransitionLogRepository logRepository,
        WorkflowResolutionService resolutionService,
        WorkflowEngineService engineService
    ) {
        this.instanceRepository = instanceRepository;
        this.definitionRepository = definitionRepository;
        this.stageRepository = stageRepository;
        this.logRepository = logRepository;
        this.resolutionService = resolutionService;
        this.engineService = engineService;
    }

    public record StartResult(InstanceResponse instance, boolean created) {}

    /**
     * Starts the active workflow for a subject at its initial status. Starting
     * a subject that already has an instance returns the existing instance.
     */
    @Transactional
    public StartResult start(String tenantKey, String clientId, StartInstanceRequest request) {
        String subjectType = normalize(request.subjectType());
        String subjectId = request.subjectId().trim();

        WorkflowInstance existing = instanceRepository
            .findByTenantKeyAndSubjectTypeAndSubjectId(tenantKey, subjectType, subjectId)
            .orElse(null);
        if (existing != null) {
            return new StartResult(toResponse(existing, null), false);
        }

        WorkflowDefinition workflow =
            resolutionService.resolveDefinitionForNewSubject(tenantKey, subjectType);

        WorkflowInstance instance = new WorkflowInstance();
        instance.setTenantKey(tenantKey);
        instance.setSubjectType(subjectType);
        instance.setSubjectId(subjectId);
        instance.setWorkflowDefinitionId(workflow.getId());
        instance.setWorkflowVersion(workflow.getWorkflowVersion());
        instance.setCurrentStatus(normalize(workflow.getInitialStatus()));
        instance.setCreatedByClient(clientId);

        try {
            return new StartResult(toResponse(instanceRepository.saveAndFlush(instance), null), true);
        } catch (DataIntegrityViolationException exception) {
            throw new WorkflowException(HttpStatus.CONFLICT,
                "This subject's instance was started concurrently. Retry to fetch it.", exception);
        }
    }

    @Transactional(readOnly = true)
    public InstanceResponse get(String tenantKey, UUID instanceId, String actorRole) {
        return toResponse(getInstance(tenantKey, instanceId), actorRole);
    }

    @Transactional(readOnly = true)
    public InstanceResponse findBySubject(
        String tenantKey,
        String subjectType,
        String subjectId,
        String actorRole
    ) {
        WorkflowInstance instance = instanceRepository
            .findByTenantKeyAndSubjectTypeAndSubjectId(
                tenantKey, normalize(subjectType), subjectId == null ? "" : subjectId.trim())
            .orElseThrow(() -> new WorkflowException(HttpStatus.NOT_FOUND,
                "No workflow instance exists for this subject."));
        return toResponse(instance, actorRole);
    }

    @Transactional(readOnly = true)
    public List<TransitionHistoryResponse> history(String tenantKey, UUID instanceId) {
        WorkflowInstance instance = getInstance(tenantKey, instanceId);
        return logRepository.findByInstanceIdOrderByExecutedAtAsc(instance.getId()).stream()
            .map(log -> new TransitionHistoryResponse(log.getId(), log.getActionKey(),
                log.getFromStatus(), log.getToStatus(), log.getTriggerType(), log.getActorId(),
                log.getActorRole(), log.getComments(), log.getClientId(), log.getExecutedAt()))
            .toList();
    }

    /**
     * Applies an action to the instance's stored status. The status change and
     * its audit row commit together or not at all.
     */
    @Transactional
    public InstanceActionResponse perform(
        String tenantKey,
        String clientId,
        UUID instanceId,
        String actionKey,
        InstanceActionRequest request
    ) {
        String action = normalize(actionKey);
        if (!ACTION_KEY_PATTERN.matcher(action).matches()) {
            throw new WorkflowException("Invalid action key: " + actionKey);
        }

        // Lock first: a concurrent action on this instance waits here and then
        // sees the status (and idempotency record) committed by the first one.
        WorkflowInstance instance = instanceRepository.findForUpdate(instanceId, tenantKey)
            .orElseThrow(() -> new WorkflowException(HttpStatus.NOT_FOUND,
                "Workflow instance not found: " + instanceId));

        String idempotencyKey = request.idempotencyKey().trim();
        WorkflowTransitionLog previous = logRepository
            .findByTenantKeyAndIdempotencyKey(tenantKey, idempotencyKey)
            .orElse(null);
        if (previous != null) {
            return replayOrReject(previous, instance, action, request);
        }

        String fromStatus = instance.getCurrentStatus();
        if (request.expectedStatus() != null && !request.expectedStatus().isBlank()
                && !normalize(request.expectedStatus()).equals(fromStatus)) {
            throw new WorkflowException(HttpStatus.CONFLICT,
                "The instance is in status " + fromStatus + ", not "
                    + normalize(request.expectedStatus()) + ". Reload it and retry.");
        }

        WorkflowDefinition workflow = getPinnedDefinition(instance);
        WorkflowStageConfig stage = engineService.authorize(workflow, fromStatus, action,
            request.triggerType(), request.actorId(), request.actorRole(), request.comments());
        String toStatus = normalize(stage.getToStatus());

        WorkflowTransitionLog log = new WorkflowTransitionLog();
        log.setTenantKey(tenantKey);
        log.setClientId(clientId);
        log.setInstanceId(instance.getId());
        log.setWorkflowDefinitionId(workflow.getId());
        log.setWorkflowVersion(workflow.getWorkflowVersion());
        log.setSubjectId(instance.getSubjectId());
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setActionKey(action);
        log.setTriggerType(stage.getTriggerType());
        log.setActorId(request.actorId().trim());
        log.setActorRole(normalizeRole(request.actorRole()));
        log.setComments(request.comments());
        log.setIdempotencyKey(idempotencyKey);

        instance.setCurrentStatus(toStatus);
        try {
            instanceRepository.saveAndFlush(instance);
            WorkflowTransitionLog saved = logRepository.saveAndFlush(log);
            return new InstanceActionResponse(saved.getId(), action, fromStatus, toStatus,
                false, toResponse(instance, workflow, null));
        } catch (DataIntegrityViolationException exception) {
            // The idempotency key was taken concurrently by another instance's action.
            throw new WorkflowException(HttpStatus.CONFLICT,
                "A request with this idempotency key was processed concurrently. "
                    + "Retry the same request with the same key.", exception);
        }
    }

    private InstanceActionResponse replayOrReject(
        WorkflowTransitionLog previous,
        WorkflowInstance instance,
        String action,
        InstanceActionRequest request
    ) {
        boolean sameRequest = instance.getId().equals(previous.getInstanceId())
            && previous.getActionKey().equals(action)
            && previous.getTriggerType() == request.triggerType()
            && Objects.equals(previous.getActorId(), request.actorId().trim())
            && Objects.equals(previous.getActorRole(), normalizeRole(request.actorRole()))
            && Objects.equals(previous.getComments(), request.comments());
        if (!sameRequest) {
            throw new WorkflowException(HttpStatus.CONFLICT,
                "This idempotency key was already used for a different request.");
        }
        return new InstanceActionResponse(previous.getId(), previous.getActionKey(),
            previous.getFromStatus(), previous.getToStatus(), true, toResponse(instance, null));
    }

    private WorkflowInstance getInstance(String tenantKey, UUID instanceId) {
        return instanceRepository.findByIdAndTenantKey(instanceId, tenantKey)
            .orElseThrow(() -> new WorkflowException(HttpStatus.NOT_FOUND,
                "Workflow instance not found: " + instanceId));
    }

    private WorkflowDefinition getPinnedDefinition(WorkflowInstance instance) {
        return definitionRepository
            .findByIdAndTenantKey(instance.getWorkflowDefinitionId(), instance.getTenantKey())
            .orElseThrow(() -> new IllegalStateException(
                "Pinned workflow is missing for instance " + instance.getId()));
    }

    private InstanceResponse toResponse(WorkflowInstance instance, String actorRole) {
        return toResponse(instance, getPinnedDefinition(instance), actorRole);
    }

    /**
     * Includes the actions available from the current status. When actorRole
     * is given, only actions that role may perform are listed; terminal is
     * true when no enabled action leaves the current status at all.
     */
    private InstanceResponse toResponse(
        WorkflowInstance instance,
        WorkflowDefinition workflow,
        String actorRole
    ) {
        List<WorkflowStageConfig> outgoing = stageRepository
            .findByWorkflowDefinition_IdAndFromStatusAndEnabledTrue(
                workflow.getId(), instance.getCurrentStatus());
        String role = actorRole == null || actorRole.isBlank() ? null : normalizeRole(actorRole);

        List<AvailableActionResponse> actions = outgoing.stream()
            .filter(stage -> role == null || mayPerform(stage, role))
            .sorted(Comparator.comparing(WorkflowStageConfig::getStageOrder))
            .map(stage -> new AvailableActionResponse(stage.getActionKey(), stage.getName(),
                stage.getToStatus(), roles(stage), stage.getTriggerType(),
                stage.isRequiresComments(), stage.getSlaDays()))
            .toList();

        return new InstanceResponse(instance.getId(), instance.getSubjectType(),
            instance.getSubjectId(), workflow.getId(), workflow.getWorkflowKey(),
            workflow.getWorkflowVersion(), instance.getCurrentStatus(), outgoing.isEmpty(),
            actions, instance.getCreatedAt(), instance.getUpdatedAt());
    }

    private boolean mayPerform(WorkflowStageConfig stage, String role) {
        if (stage.getTriggerType() != TriggerType.HUMAN) {
            return WorkflowRegistryService.SYSTEM_ROLE.equals(role);
        }
        return roles(stage).contains(role);
    }

    private List<String> roles(WorkflowStageConfig stage) {
        return Arrays.stream(stage.getAssignedRole().split(","))
            .map(this::normalizeRole)
            .toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRole(String value) {
        return normalize(value).replaceFirst("^ROLE_", "");
    }
}
