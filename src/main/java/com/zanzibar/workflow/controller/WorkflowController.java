package com.zanzibar.workflow.controller;

import com.zanzibar.workflow.dto.request.CreateWorkflowRequest;
import com.zanzibar.workflow.dto.request.CreateWorkflowStageRequest;
import com.zanzibar.workflow.dto.request.UpdateWorkflowRequest;
import com.zanzibar.workflow.dto.response.WorkflowResponse;
import com.zanzibar.workflow.dto.response.WorkflowValidationResponse;
import com.zanzibar.workflow.dto.response.WorkflowCoverageResponse;
import com.zanzibar.workflow.service.WorkflowManagementService;
import com.zanzibar.workflow.service.WorkflowResolutionService;
import com.zanzibar.workflow.service.WorkflowValidationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.zanzibar.workflow.security.WorkflowPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowManagementService managementService;
    private final WorkflowResolutionService resolutionService;
    private final WorkflowValidationService validationService;

    public WorkflowController(
        WorkflowManagementService managementService,
        WorkflowResolutionService resolutionService,
        WorkflowValidationService validationService
    ) {
        this.managementService = managementService;
        this.resolutionService = resolutionService;
        this.validationService = validationService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<WorkflowResponse> createWorkflow(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @Valid @RequestBody CreateWorkflowRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(managementService.createWorkflow(principal.tenantKey(), request));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<List<WorkflowResponse>> getAllWorkflows(
        @AuthenticationPrincipal WorkflowPrincipal principal
    ) {
        return ResponseEntity.ok(managementService.getAllWorkflows(principal.tenantKey()));
    }

    @GetMapping("/{workflowId}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<WorkflowResponse> getWorkflow(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @PathVariable UUID workflowId
    ) {
        return ResponseEntity.ok(
            managementService.getWorkflow(principal.tenantKey(), workflowId)
        );
    }

    @PutMapping("/{workflowId}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<WorkflowResponse> updateWorkflow(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @PathVariable UUID workflowId,
        @Valid @RequestBody UpdateWorkflowRequest request
    ) {
        return ResponseEntity.ok(
            managementService.updateWorkflow(principal.tenantKey(), workflowId, request)
        );
    }

    @PostMapping("/{workflowId}/versions")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<WorkflowResponse> createNewVersion(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @PathVariable UUID workflowId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(managementService.createNewVersion(principal.tenantKey(), workflowId));
    }

    @PostMapping("/{workflowId}/stages")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<WorkflowResponse> addStage(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @PathVariable UUID workflowId,
        @Valid @RequestBody CreateWorkflowStageRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(managementService.addStage(principal.tenantKey(), workflowId, request));
    }

    @PutMapping("/{workflowId}/stages/{stageId}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<WorkflowResponse> updateStage(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @PathVariable UUID workflowId,
        @PathVariable UUID stageId,
        @Valid @RequestBody CreateWorkflowStageRequest request
    ) {
        return ResponseEntity.ok(
            managementService.updateStage(principal.tenantKey(), workflowId, stageId, request)
        );
    }

    @DeleteMapping("/{workflowId}/stages/{stageId}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<Void> deleteStage(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @PathVariable UUID workflowId,
        @PathVariable UUID stageId
    ) {
        managementService.deleteStage(principal.tenantKey(), workflowId, stageId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{workflowId}/publish")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<WorkflowResponse> publishWorkflow(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @PathVariable UUID workflowId
    ) {
        return ResponseEntity.ok(
            managementService.publishWorkflow(principal.tenantKey(), workflowId)
        );
    }


    /**
     * Read-only validation report for a workflow, including drafts.
     * This endpoint does not publish or modify the workflow.
     */
    @GetMapping("/{workflowId}/validation")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<WorkflowValidationResponse> validateWorkflow(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @PathVariable UUID workflowId
    ) {
        return ResponseEntity.ok(
            validationService.validateWorkflow(principal.tenantKey(), workflowId));
    }

    /**
     * Shows whether every active registered subject type has exactly one
     * active published workflow. Administrators can use this before publishing.
     */
    @GetMapping("/coverage")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<WorkflowCoverageResponse> inspectWorkflowCoverage(
        @AuthenticationPrincipal WorkflowPrincipal principal
    ) {
        return ResponseEntity.ok(
            validationService.inspectSubjectTypeCoverage(principal.tenantKey()));
    }

    @GetMapping("/resolve")
    @PreAuthorize("hasRole('WORKFLOW_SERVICE')")
    public ResponseEntity<WorkflowResponse> resolveWorkflow(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @RequestParam String subjectType
    ) {
        return ResponseEntity.ok(
            resolutionService.resolveForNewSubject(principal.tenantKey(), subjectType)
        );
    }

    @GetMapping("/{workflowId}/versions/{version}")
    @PreAuthorize("hasRole('WORKFLOW_SERVICE')")
    public ResponseEntity<WorkflowResponse> getPinnedWorkflow(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @PathVariable UUID workflowId,
        @PathVariable Integer version
    ) {
        return ResponseEntity.ok(
            resolutionService.getPinnedWorkflow(principal.tenantKey(), workflowId, version)
        );
    }
}
