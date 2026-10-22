
package com.zanzibar.workflow.service;

import com.zanzibar.workflow.dto.request.CreateWorkflowRequest;
import com.zanzibar.workflow.dto.request.CreateWorkflowStageRequest;
import com.zanzibar.workflow.dto.response.WorkflowResponse;
import com.zanzibar.workflow.dto.response.WorkflowStageResponse;
import com.zanzibar.workflow.model.WorkflowDefinition;
import com.zanzibar.workflow.model.WorkflowStageConfig;
import com.zanzibar.workflow.enums.TriggerType;
import com.zanzibar.workflow.enums.WorkflowScope;
import com.zanzibar.workflow.enums.WorkflowApplicationTypes;
import com.zanzibar.workflow.enums.WorkflowStatus;
import com.zanzibar.workflow.exception.WorkflowException;
import com.zanzibar.workflow.repository.WorkflowDefinitionRepository;
import com.zanzibar.workflow.repository.WorkflowStageConfigRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional
public class WorkflowManagementService {

    private static final Set<String> ALLOWED_ROLES = Set.of(
        "APPLICANT",
        "ADMIN",
        "ADMINISTRATOR",
        "MANAGER",
        "RESEARCH_OFFICER",
        "SVPO_OFFICER",
        "OCGS_VERIFICATION",
        "ETHICS_OFFICER",
        "FINANCE_OFFICER",
        "OFFICER",
        "COMPLIANCE_OFFICER",
        "INSTITUTION",
        "SYSTEM"
    );

    private static final Pattern ACTION_KEY_PATTERN =
        Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowStageConfigRepository stageRepository;
    private final WorkflowValidationService validationService;

    public WorkflowManagementService(
        WorkflowDefinitionRepository definitionRepository,
        WorkflowStageConfigRepository stageRepository,
        WorkflowValidationService validationService
    ) {
        this.definitionRepository = definitionRepository;
        this.stageRepository = stageRepository;
        this.validationService = validationService;
    }

    public WorkflowResponse createWorkflow(CreateWorkflowRequest request) {
        validateScope(request.scope(), request.applicableApplicationTypes());

        String key = request.workflowKey().trim();

        int nextVersion = definitionRepository
            .findAllByWorkflowKeyOrderByWorkflowVersionDesc(key)
            .stream()
            .mapToInt(WorkflowDefinition::getWorkflowVersion)
            .max()
            .orElse(0) + 1;

        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setWorkflowKey(key);
        workflow.setName(request.name().trim());
        workflow.setDescription(request.description());
        workflow.setScope(request.scope());
        workflow.setStatus(WorkflowStatus.DRAFT);
        workflow.setWorkflowVersion(nextVersion);
        workflow.setActive(false);
        workflow.setApplicableApplicationTypes(
            normalizeApplicationTypes(request.applicableApplicationTypes())
        );

        return toResponse(definitionRepository.save(workflow));
    }

    public WorkflowResponse createNewVersion(UUID sourceWorkflowId) {
        WorkflowDefinition source = getDefinition(sourceWorkflowId);

        if (source.getStatus() == WorkflowStatus.DRAFT) {
            throw new WorkflowException(HttpStatus.CONFLICT,
                "The source workflow is already a draft. Edit that draft instead.");
        }

        int nextVersion = definitionRepository
            .findAllByWorkflowKeyOrderByWorkflowVersionDesc(
                source.getWorkflowKey()
            )
            .stream()
            .mapToInt(WorkflowDefinition::getWorkflowVersion)
            .max()
            .orElse(source.getWorkflowVersion()) + 1;

        WorkflowDefinition draft = new WorkflowDefinition();
        draft.setWorkflowKey(source.getWorkflowKey());
        draft.setName(source.getName());
        draft.setDescription(source.getDescription());
        draft.setScope(source.getScope());
        draft.setStatus(WorkflowStatus.DRAFT);
        draft.setWorkflowVersion(nextVersion);
        draft.setActive(false);
        draft.setApplicableApplicationTypes(
            source.getApplicableApplicationTypes()
        );

        for (WorkflowStageConfig oldStage : source.getStages()) {
            WorkflowStageConfig copy = new WorkflowStageConfig();
            copyStageValues(oldStage, copy);
            draft.addStage(copy);
        }

        return toResponse(definitionRepository.save(draft));
    }

    public WorkflowResponse addStage(
        UUID workflowId,
        CreateWorkflowStageRequest request
    ) {
        WorkflowDefinition workflow = getDefinition(workflowId);
        ensureDraft(workflow);
        validateStage(request);

        WorkflowStageConfig stage = new WorkflowStageConfig();
        copyRequestValues(request, stage);

        workflow.addStage(stage);

        return toResponse(definitionRepository.save(workflow));
    }

    public WorkflowResponse updateStage(
        UUID workflowId,
        UUID stageId,
        CreateWorkflowStageRequest request
    ) {
        WorkflowDefinition workflow = getDefinition(workflowId);
        ensureDraft(workflow);
        validateStage(request);

        WorkflowStageConfig stage = stageRepository.findById(stageId)
            .orElseThrow(() -> new WorkflowException(
                HttpStatus.NOT_FOUND, "Workflow stage not found: " + stageId));

        if (!workflowId.equals(
                stage.getWorkflowDefinition().getId())) {
            throw new WorkflowException(HttpStatus.NOT_FOUND,
                "The stage does not belong to this workflow.");
        }

        copyRequestValues(request, stage);
        return toResponse(definitionRepository.save(workflow));
    }

    public void deleteStage(UUID workflowId, UUID stageId) {
        WorkflowDefinition workflow = getDefinition(workflowId);
        ensureDraft(workflow);

        WorkflowStageConfig stage = stageRepository.findById(stageId)
            .orElseThrow(() -> new WorkflowException(
                HttpStatus.NOT_FOUND, "Workflow stage not found: " + stageId));

        if (!workflowId.equals(
                stage.getWorkflowDefinition().getId())) {
            throw new WorkflowException(HttpStatus.NOT_FOUND,
                "The stage does not belong to this workflow.");
        }

        workflow.removeStage(stage);
        definitionRepository.save(workflow);
    }

    public WorkflowResponse publishWorkflow(UUID workflowId) {
        WorkflowDefinition workflow = getDefinition(workflowId);
        ensureDraft(workflow);
        validateBeforePublish(workflow);

        // Never silently archive a different workflow key: that can remove
        // coverage for application types not included in the new workflow.
        // A new version may replace only the active version of the same key
        // and only when its scope is unchanged.
        List<WorkflowDefinition> activeWorkflows =
            definitionRepository.findByStatusAndActiveTrue(WorkflowStatus.PUBLISHED);
        List<WorkflowDefinition> versionsToArchive = new ArrayList<>();

        for (WorkflowDefinition existing : activeWorkflows) {
            if (existing.getId().equals(workflow.getId())
                    || !scopesOverlap(existing, workflow)) {
                continue;
            }
            if (!existing.getWorkflowKey().equals(workflow.getWorkflowKey())) {
                throw new WorkflowException(HttpStatus.CONFLICT,
                    "Publishing this workflow would overlap active workflow '"
                        + existing.getWorkflowKey() + "'. Resolve the scope conflict first.");
            }
            if (!sameScope(existing, workflow)) {
                throw new WorkflowException(HttpStatus.CONFLICT,
                    "A new version must keep the same scope and application types as the active version.");
            }
            versionsToArchive.add(existing);
        }

        // Changes are in one transaction: either replacement and publication
        // both commit, or neither does.
        for (WorkflowDefinition existing : versionsToArchive) {
            existing.setActive(false);
            existing.setStatus(WorkflowStatus.ARCHIVED);
            definitionRepository.save(existing);
        }

        workflow.setStatus(WorkflowStatus.PUBLISHED);
        workflow.setActive(true);
        workflow.setPublishedAt(java.time.Instant.now());

        return toResponse(definitionRepository.save(workflow));
    }

    public WorkflowResponse getWorkflow(UUID workflowId) {
        return toResponse(getDefinition(workflowId));
    }

    public List<WorkflowResponse> getAllWorkflows() {
        return definitionRepository.findAll()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    private WorkflowDefinition getDefinition(UUID workflowId) {
        return definitionRepository.findById(workflowId)
            .orElseThrow(() -> new WorkflowException(
                HttpStatus.NOT_FOUND, "Workflow not found: " + workflowId));
    }

    private void ensureDraft(WorkflowDefinition workflow) {
        if (workflow.getStatus() != WorkflowStatus.DRAFT) {
            throw new WorkflowException(HttpStatus.CONFLICT,
                "Only DRAFT workflows can be modified. "
                    + "Create a new version before editing a published workflow.");
        }
    }

    private void validateScope(
        WorkflowScope scope,
        Set<String> applicationTypes
    ) {
        if (scope == null) {
            throw new WorkflowException("Workflow scope is required.");
        }

        if (scope == WorkflowScope.SELECTED_TYPES
                && (applicationTypes == null || applicationTypes.isEmpty())) {
            throw new WorkflowException(
                "SELECTED_TYPES requires at least one application type."
            );
        }
        if (scope == WorkflowScope.ALL_APPLICATIONS
                && applicationTypes != null && !applicationTypes.isEmpty()) {
            throw new WorkflowException(
                "ALL_APPLICATIONS workflows must not list selected application types."
            );
        }
        if (applicationTypes != null) {
            for (String type : applicationTypes) {
                if (type == null || !WorkflowApplicationTypes.SUPPORTED.contains(
                        type.trim().toUpperCase(Locale.ROOT))) {
                    throw new WorkflowException(
                        "Unsupported application type: " + type
                    );
                }
            }
        }
    }

    private Set<String> normalizeApplicationTypes(Set<String> types) {
        if (types == null) {
            return new LinkedHashSet<>();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String type : types) {
            if (type == null || type.isBlank()) {
                throw new WorkflowException(
                    "Application types cannot be blank."
                );
            }
            normalized.add(type.trim().toUpperCase(Locale.ROOT));
        }
        return normalized;
    }

    private void validateStage(CreateWorkflowStageRequest stage) {
        if (!ACTION_KEY_PATTERN.matcher(stage.actionKey().trim()).matches()) {
            throw new WorkflowException(
                "Invalid action key. Use letters, numbers and underscores; "
                + "the first character must be a letter."
            );
        }

        String role = normalizeRole(stage.assignedRole());

        for (String assigned : role.split(",")) {
            if (!ALLOWED_ROLES.contains(assigned.trim())) {
                throw new WorkflowException(
                    "Unsupported assigned role: " + assigned
                );
            }
        }

        if ((stage.triggerType() == TriggerType.SYSTEM
                || stage.triggerType() == TriggerType.PAYMENT)
                && !role.equals("SYSTEM")) {
            throw new WorkflowException(
                "SYSTEM and PAYMENT transitions must be assigned to SYSTEM."
            );
        }

        if (stage.triggerType() == TriggerType.HUMAN
                && List.of(role.split(",")).contains("SYSTEM")) {
            throw new WorkflowException(
                "HUMAN transitions must be assigned only to human roles; SYSTEM cannot be included."
            );
        }

        if (stage.fromStatus().trim().equalsIgnoreCase(
                stage.toStatus().trim())) {
            throw new WorkflowException(
                "A transition cannot lead back to the same status."
            );
        }

        if (stage.externalApproval()
                && (stage.externalSystemId() == null
                    || stage.externalSystemId().isBlank())) {
            throw new WorkflowException(
                "External system ID is required for external approval."
            );
        }
    }

    private void validateBeforePublish(WorkflowDefinition workflow) {
        var report = validationService.validateDefinition(workflow);
        if (!report.publishable()) {
            throw new WorkflowException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Workflow cannot be published: "
                    + String.join(" ", report.errors())
            );
        }
    }

    private boolean sameScope(WorkflowDefinition first, WorkflowDefinition second) {
        if (first.getScope() != second.getScope()) return false;
        if (first.getScope() == WorkflowScope.ALL_APPLICATIONS) return true;
        return normalizeApplicationTypes(first.getApplicableApplicationTypes())
            .equals(normalizeApplicationTypes(second.getApplicableApplicationTypes()));
    }

    private boolean scopesOverlap(
        WorkflowDefinition first,
        WorkflowDefinition second
    ) {
        if (first.getScope() == WorkflowScope.ALL_APPLICATIONS
                || second.getScope() == WorkflowScope.ALL_APPLICATIONS) {
            return true;
        }

        return first.getApplicableApplicationTypes()
            .stream()
            .anyMatch(second.getApplicableApplicationTypes()::contains);
    }

    private String normalizeRole(String role) {
        return role.trim()
            .toUpperCase(Locale.ROOT)
            .replace("ROLE_", "");
    }

    private void copyRequestValues(
        CreateWorkflowStageRequest request,
        WorkflowStageConfig stage
    ) {
        stage.setName(request.name().trim());
        stage.setDescription(request.description());
        stage.setFromStatus(request.fromStatus().trim().toUpperCase(Locale.ROOT));
        stage.setToStatus(request.toStatus().trim().toUpperCase(Locale.ROOT));
        stage.setActionKey(request.actionKey().trim().toUpperCase(Locale.ROOT));
        stage.setAssignedRole(normalizeRole(request.assignedRole()));
        stage.setTriggerType(request.triggerType());
        stage.setStageOrder(request.stageOrder());
        stage.setSlaDays(request.slaDays());
        stage.setRequiresComments(request.requiresComments());
        stage.setExternalApproval(request.externalApproval());
        stage.setExternalSystemId(request.externalSystemId());
        stage.setEnabled(request.enabled());
    }

    private void copyStageValues(
        WorkflowStageConfig source,
        WorkflowStageConfig target
    ) {
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setFromStatus(source.getFromStatus());
        target.setToStatus(source.getToStatus());
        target.setActionKey(source.getActionKey());
        target.setAssignedRole(source.getAssignedRole());
        target.setTriggerType(source.getTriggerType());
        target.setStageOrder(source.getStageOrder());
        target.setSlaDays(source.getSlaDays());
        target.setRequiresComments(source.isRequiresComments());
        target.setExternalApproval(source.isExternalApproval());
        target.setExternalSystemId(source.getExternalSystemId());
        target.setEnabled(source.isEnabled());
    }

    private WorkflowResponse toResponse(WorkflowDefinition workflow) {
        List<WorkflowStageResponse> stages = new ArrayList<>();

        for (WorkflowStageConfig stage : workflow.getStages()) {
            stages.add(new WorkflowStageResponse(
                stage.getId(),
                stage.getName(),
                stage.getDescription(),
                stage.getFromStatus(),
                stage.getToStatus(),
                stage.getActionKey(),
                stage.getAssignedRole(),
                stage.getTriggerType(),
                stage.getStageOrder(),
                stage.getSlaDays(),
                stage.isRequiresComments(),
                stage.isExternalApproval(),
                stage.getExternalSystemId(),
                stage.isEnabled()
            ));
        }

        return new WorkflowResponse(
            workflow.getId(),
            workflow.getWorkflowKey(),
            workflow.getName(),
            workflow.getDescription(),
            workflow.getScope(),
            workflow.getStatus(),
            workflow.getWorkflowVersion(),
            workflow.isActive(),
            Set.copyOf(workflow.getApplicableApplicationTypes()),
            List.copyOf(stages),
            workflow.getCreatedAt(),
            workflow.getUpdatedAt(),
            workflow.getPublishedAt()
        );
    }
}
