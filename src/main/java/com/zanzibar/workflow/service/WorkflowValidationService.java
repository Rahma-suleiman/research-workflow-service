package com.zanzibar.workflow.service;

import com.zanzibar.workflow.dto.response.WorkflowCoverageResponse;
import com.zanzibar.workflow.dto.response.WorkflowValidationResponse;
import com.zanzibar.workflow.enums.TriggerType;
import com.zanzibar.workflow.enums.WorkflowScope;
import com.zanzibar.workflow.enums.WorkflowStatus;
import com.zanzibar.workflow.exception.WorkflowException;
import com.zanzibar.workflow.model.WorkflowDefinition;
import com.zanzibar.workflow.model.WorkflowStageConfig;
import com.zanzibar.workflow.repository.WorkflowDefinitionRepository;
import com.zanzibar.workflow.repository.WorkflowStageConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Read-only diagnostics for validating workflow definitions before publication
 * and checking active published coverage. It never publishes or mutates data.
 */
@Service
public class WorkflowValidationService {

    private static final Pattern STATUS_PATTERN =
        Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final Pattern ACTION_PATTERN =
        Pattern.compile("[A-Z][A-Z0-9_]*");

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowStageConfigRepository stageRepository;
    private final WorkflowRegistryService registryService;

    public WorkflowValidationService(
        WorkflowDefinitionRepository definitionRepository,
        WorkflowStageConfigRepository stageRepository,
        WorkflowRegistryService registryService
    ) {
        this.definitionRepository = definitionRepository;
        this.stageRepository = stageRepository;
        this.registryService = registryService;
    }

    @Transactional(readOnly = true)
    public WorkflowValidationResponse validateWorkflow(String tenantKey, UUID workflowId) {
        WorkflowDefinition workflow = definitionRepository.findByIdAndTenantKey(workflowId, tenantKey)
            .orElseThrow(() -> new WorkflowException(
                HttpStatus.NOT_FOUND, "Workflow not found: " + workflowId));
        return validateDefinition(workflow);
    }

    @Transactional(readOnly = true)
    public WorkflowValidationResponse validateDefinition(WorkflowDefinition workflow) {
        List<WorkflowStageConfig> stages =
            stageRepository.findByWorkflowDefinition_IdOrderByStageOrderAsc(
                workflow.getId());

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> subjectTypes = normalizedTypes(workflow.getSubjectTypes());
        Set<String> registeredTypes =
            registryService.activeSubjectTypeKeys(workflow.getTenantKey());
        Set<String> registeredRoles = registryService.activeRoleKeys(workflow.getTenantKey());
        String initialStatus = normalize(workflow.getInitialStatus());

        if (workflow.getScope() == null) {
            errors.add("Workflow scope is missing.");
        } else if (workflow.getScope() == WorkflowScope.SELECTED_TYPES) {
            if (subjectTypes.isEmpty()) {
                errors.add("SELECTED_TYPES requires at least one subject type.");
            }
            for (String type : subjectTypes) {
                if (!registeredTypes.contains(type)) {
                    errors.add("Unknown or inactive subject type: " + type);
                }
            }
        } else if (workflow.getScope() == WorkflowScope.ALL_TYPES
                && !subjectTypes.isEmpty()) {
            errors.add("ALL_TYPES must not list selected subject types.");
        }

        if (!STATUS_PATTERN.matcher(initialStatus).matches()) {
            errors.add("Workflow initial status is missing or invalid.");
        }

        if (workflow.getStatus() == WorkflowStatus.DRAFT && workflow.isActive()) {
            errors.add("A DRAFT workflow must not be active.");
        }
        if (workflow.getStatus() == WorkflowStatus.PUBLISHED && !workflow.isActive()) {
            errors.add("A PUBLISHED workflow must be active.");
        }
        if (workflow.getStatus() == WorkflowStatus.ARCHIVED && workflow.isActive()) {
            errors.add("An ARCHIVED workflow must not be active.");
        }

        if (stages.isEmpty()) {
            errors.add("The workflow has no transition configurations.");
        }

        Set<Integer> orders = new HashSet<>();
        Set<String> transitionKeys = new HashSet<>();
        List<WorkflowStageConfig> enabledStages = new ArrayList<>();

        for (WorkflowStageConfig stage : stages) {
            if (stage.getStageOrder() == null || stage.getStageOrder() <= 0) {
                errors.add("Stage '" + stage.getName() + "' must have a positive stageOrder.");
            } else if (!orders.add(stage.getStageOrder())) {
                errors.add("Duplicate stageOrder " + stage.getStageOrder() + ".");
            }

            String from = normalize(stage.getFromStatus());
            String to = normalize(stage.getToStatus());
            String action = normalize(stage.getActionKey());
            String role = normalizeRole(stage.getAssignedRole());

            if (!STATUS_PATTERN.matcher(from).matches()) {
                errors.add("Stage '" + stage.getName() + "' has an invalid fromStatus.");
            }
            if (!STATUS_PATTERN.matcher(to).matches()) {
                errors.add("Stage '" + stage.getName() + "' has an invalid toStatus.");
            }
            if (!ACTION_PATTERN.matcher(action).matches()) {
                errors.add("Stage '" + stage.getName() + "' has an invalid actionKey.");
            }
            if (from.equals(to)) {
                errors.add("Stage '" + stage.getName() + "' transitions to the same status.");
            }

            String key = from + "::" + action;
            if (!transitionKeys.add(key)) {
                errors.add("Duplicate source-status/action pair: " + key + ".");
            }

            if (role.isBlank()) {
                errors.add("Stage '" + stage.getName() + "' has no assigned role.");
            } else {
                for (String assignedRole : role.split(",")) {
                    if (!registeredRoles.contains(assignedRole.trim())) {
                        errors.add("Stage '" + stage.getName()
                            + "' has unknown or inactive role '" + assignedRole.trim() + "'.");
                    }
                }
            }

            if (stage.getTriggerType() == null) {
                errors.add("Stage '" + stage.getName() + "' has no triggerType.");
            }
            if ((stage.getTriggerType() == TriggerType.SYSTEM
                    || stage.getTriggerType() == TriggerType.EXTERNAL_EVENT)
                    && !WorkflowRegistryService.SYSTEM_ROLE.equals(role)) {
                errors.add("SYSTEM/EXTERNAL_EVENT stage '" + stage.getName()
                    + "' must be assigned to SYSTEM.");
            }
            if (stage.getTriggerType() == TriggerType.HUMAN
                    && List.of(role.split(",")).contains(WorkflowRegistryService.SYSTEM_ROLE)) {
                errors.add("HUMAN stage '" + stage.getName()
                    + "' cannot be assigned to SYSTEM.");
            }
            if (stage.isExternalApproval()
                    && (stage.getExternalSystemId() == null
                        || stage.getExternalSystemId().isBlank())) {
                errors.add("Stage '" + stage.getName()
                    + "' requires externalSystemId when externalApproval is true.");
            }
            if (stage.isExternalApproval()
                    && stage.getTriggerType() != TriggerType.EXTERNAL_EVENT) {
                warnings.add("External approval stage '" + stage.getName()
                    + "' uses trigger " + stage.getTriggerType() + ". The external system's"
                    + " answer is normally applied with an EXTERNAL_EVENT trigger.");
            }
            if (stage.isEnabled()) {
                enabledStages.add(stage);
            }
        }

        if (enabledStages.isEmpty()) {
            errors.add("The workflow has no enabled transitions.");
        } else {
            Set<String> reachable = reachableStatuses(enabledStages, initialStatus);
            if (enabledStages.stream().noneMatch(
                    stage -> initialStatus.equals(normalize(stage.getFromStatus())))) {
                errors.add("No enabled transition starts from the initial status "
                    + initialStatus + ".");
            }

            List<String> unreachable = enabledStages.stream()
                .filter(stage -> !reachable.contains(normalize(stage.getFromStatus())))
                .map(stage -> stage.getActionKey() + " (from "
                    + stage.getFromStatus() + ")")
                .toList();

            if (!unreachable.isEmpty()) {
                errors.add("Enabled transitions are unreachable from " + initialStatus + ": "
                    + String.join(", ", unreachable) + ".");
            }

            Set<String> destinationStatuses = enabledStages.stream()
                .map(stage -> normalize(stage.getToStatus()))
                .collect(Collectors.toSet());
            Set<String> sourceStatuses = enabledStages.stream()
                .map(stage -> normalize(stage.getFromStatus()))
                .collect(Collectors.toSet());
            Set<String> deadEnds = new LinkedHashSet<>(destinationStatuses);
            deadEnds.removeAll(sourceStatuses);
            if (!deadEnds.isEmpty()) {
                warnings.add("These destination statuses have no configured outgoing transition: "
                    + String.join(", ", deadEnds)
                    + ". Confirm each is an intended terminal status.");
            }
        }

        return new WorkflowValidationResponse(
            workflow.getId(),
            workflow.getWorkflowKey(),
            workflow.getWorkflowVersion(),
            workflow.getStatus(),
            errors.isEmpty(),
            stages.size(),
            (int) stages.stream().filter(WorkflowStageConfig::isEnabled).count(),
            List.copyOf(errors),
            List.copyOf(warnings)
        );
    }

    @Transactional(readOnly = true)
    public WorkflowCoverageResponse inspectSubjectTypeCoverage(String tenantKey) {
        List<WorkflowDefinition> activePublished =
            definitionRepository.findByTenantKeyAndStatusAndActiveTrue(
                tenantKey, WorkflowStatus.PUBLISHED);

        List<WorkflowCoverageResponse.SubjectTypeCoverage> results =
            new ArrayList<>();

        for (String type : registryService.activeSubjectTypeKeys(tenantKey)
                .stream().sorted().toList()) {
            List<WorkflowDefinition> matches = activePublished.stream()
                .filter(workflow -> matchesType(workflow, type))
                .toList();

            if (matches.isEmpty()) {
                results.add(new WorkflowCoverageResponse.SubjectTypeCoverage(
                    type, "MISSING", 0, null, null, null,
                    "No active published workflow covers this subject type."));
            } else if (matches.size() > 1) {
                results.add(new WorkflowCoverageResponse.SubjectTypeCoverage(
                    type, "AMBIGUOUS", matches.size(), null, null, null,
                    "Multiple active published workflows cover this subject type."));
            } else {
                WorkflowDefinition match = matches.get(0);
                results.add(new WorkflowCoverageResponse.SubjectTypeCoverage(
                    type, "RESOLVED", 1, match.getId(), match.getWorkflowKey(),
                    match.getWorkflowVersion(),
                    "Exactly one active published workflow covers this subject type."));
            }
        }

        boolean allCovered = results.stream()
            .allMatch(result -> "RESOLVED".equals(result.result()));
        return new WorkflowCoverageResponse(allCovered, List.copyOf(results));
    }

    private boolean matchesType(WorkflowDefinition workflow, String type) {
        if (workflow.getScope() == WorkflowScope.ALL_TYPES) {
            return true;
        }
        return workflow.getScope() == WorkflowScope.SELECTED_TYPES
            && normalizedTypes(workflow.getSubjectTypes()).contains(type);
    }

    private Set<String> reachableStatuses(
        List<WorkflowStageConfig> stages,
        String initialStatus
    ) {
        Map<String, List<String>> graph = stages.stream().collect(
            Collectors.groupingBy(
                stage -> normalize(stage.getFromStatus()),
                Collectors.mapping(stage -> normalize(stage.getToStatus()),
                    Collectors.toList())
            )
        );

        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(initialStatus);
        visited.add(initialStatus);

        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (String next : graph.getOrDefault(current, List.of())) {
                if (visited.add(next)) {
                    queue.addLast(next);
                }
            }
        }
        return visited;
    }

    private Set<String> normalizedTypes(Set<String> types) {
        if (types == null) return Set.of();
        return types.stream()
            .filter(type -> type != null && !type.isBlank())
            .map(type -> type.trim().toUpperCase(Locale.ROOT))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeRole(String value) {
        if (value == null) return "";
        return java.util.Arrays.stream(value.split(","))
            .map(role -> role.trim().toUpperCase(Locale.ROOT)
                .replaceFirst("^ROLE_", ""))
            .collect(Collectors.joining(","));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
