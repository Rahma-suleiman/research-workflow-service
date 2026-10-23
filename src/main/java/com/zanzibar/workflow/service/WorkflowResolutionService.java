
package com.zanzibar.workflow.service;

import com.zanzibar.workflow.dto.response.WorkflowResponse;
import com.zanzibar.workflow.model.WorkflowDefinition;
import com.zanzibar.workflow.enums.WorkflowScope;
import com.zanzibar.workflow.enums.WorkflowStatus;
import com.zanzibar.workflow.exception.WorkflowException;
import com.zanzibar.workflow.repository.WorkflowDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;

import java.util.Locale;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WorkflowResolutionService {

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowManagementService managementService;
    private final WorkflowRegistryService registryService;

    public WorkflowResolutionService(
        WorkflowDefinitionRepository definitionRepository,
        WorkflowManagementService managementService,
        WorkflowRegistryService registryService
    ) {
        this.definitionRepository = definitionRepository;
        this.managementService = managementService;
        this.registryService = registryService;
    }

    @Transactional(readOnly = true)
    public WorkflowResponse resolveForNewSubject(String subjectType) {
        if (subjectType == null || subjectType.isBlank()) {
            throw new WorkflowException("Subject type is required.");
        }

        String normalizedType = subjectType.trim().toUpperCase(Locale.ROOT);
        if (!registryService.activeSubjectTypeKeys().contains(normalizedType)) {
            throw new WorkflowException(HttpStatus.BAD_REQUEST,
                "Unknown or inactive subject type: " + subjectType);
        }

        List<WorkflowDefinition> matches =
            definitionRepository.findActivePublishedForSubjectType(
                WorkflowStatus.PUBLISHED,
                WorkflowScope.ALL_TYPES,
                WorkflowScope.SELECTED_TYPES,
                normalizedType
            );

        if (matches.isEmpty()) {
            throw new WorkflowException(HttpStatus.NOT_FOUND,
                "No active published workflow is configured for subject type: "
                + normalizedType);
        }

        if (matches.size() > 1) {
            throw new WorkflowException(HttpStatus.CONFLICT,
                "More than one active workflow matches subject type: "
                + normalizedType + ". Resolve the conflicting workflow configurations.");
        }

        return managementService.getWorkflow(matches.get(0).getId());
    }

    @Transactional(readOnly = true)
    public WorkflowResponse getPinnedWorkflow(
        UUID workflowDefinitionId,
        Integer workflowVersion
    ) {
        WorkflowDefinition workflow = definitionRepository
            .findById(workflowDefinitionId)
            .orElseThrow(() -> new WorkflowException(HttpStatus.NOT_FOUND,
                "Pinned workflow not found: " + workflowDefinitionId));

        if (!workflow.getWorkflowVersion().equals(workflowVersion)) {
            throw new WorkflowException(HttpStatus.CONFLICT,
                "Workflow version mismatch. Requested version "
                + workflowVersion
                + " but the stored definition is version "
                + workflow.getWorkflowVersion()
                + "."
            );
        }

        if (workflow.getStatus() == WorkflowStatus.DRAFT) {
            throw new WorkflowException(HttpStatus.CONFLICT,
                "A draft workflow cannot be used by a subject.");
        }
        if (workflow.getStatus() == WorkflowStatus.PUBLISHED && !workflow.isActive()) {
            throw new WorkflowException(HttpStatus.CONFLICT,
                "The pinned workflow is marked PUBLISHED but is inactive. Repair its lifecycle state.");
        }

        // Archived workflows remain retrievable for subjects that
        // were already pinned to that version.
        return managementService.getWorkflow(workflowDefinitionId);
    }
}
