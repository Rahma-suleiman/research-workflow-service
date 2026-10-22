
package com.zanzibar.workflow.controller;

import com.zanzibar.workflow.dto.request.CreateWorkflowRequest;
import com.zanzibar.workflow.dto.request.CreateWorkflowStageRequest;
import com.zanzibar.workflow.dto.response.WorkflowResponse;
import com.zanzibar.workflow.dto.response.WorkflowValidationResponse;
import com.zanzibar.workflow.dto.response.WorkflowCoverageResponse;
import com.zanzibar.workflow.service.WorkflowManagementService;
import com.zanzibar.workflow.service.WorkflowResolutionService;
import com.zanzibar.workflow.service.WorkflowValidationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
        @Valid @RequestBody CreateWorkflowRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(managementService.createWorkflow(request));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<List<WorkflowResponse>> getAllWorkflows() {
        return ResponseEntity.ok(managementService.getAllWorkflows());
    }

    @GetMapping("/{workflowId}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<WorkflowResponse> getWorkflow(
        @PathVariable UUID workflowId
    ) {
        return ResponseEntity.ok(
            managementService.getWorkflow(workflowId)
        );
    }

    @PostMapping("/{workflowId}/versions")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<WorkflowResponse> createNewVersion(
        @PathVariable UUID workflowId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(managementService.createNewVersion(workflowId));
    }

    @PostMapping("/{workflowId}/stages")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<WorkflowResponse> addStage(
        @PathVariable UUID workflowId,
        @Valid @RequestBody CreateWorkflowStageRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(managementService.addStage(workflowId, request));
    }

    @PutMapping("/{workflowId}/stages/{stageId}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<WorkflowResponse> updateStage(
        @PathVariable UUID workflowId,
        @PathVariable UUID stageId,
        @Valid @RequestBody CreateWorkflowStageRequest request
    ) {
        return ResponseEntity.ok(
            managementService.updateStage(workflowId, stageId, request)
        );
    }

    @DeleteMapping("/{workflowId}/stages/{stageId}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<Void> deleteStage(
        @PathVariable UUID workflowId,
        @PathVariable UUID stageId
    ) {
        managementService.deleteStage(workflowId, stageId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{workflowId}/publish")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<WorkflowResponse> publishWorkflow(
        @PathVariable UUID workflowId
    ) {
        return ResponseEntity.ok(
            managementService.publishWorkflow(workflowId)
        );
    }


    /**
     * Read-only validation report for a workflow, including drafts.
     * This endpoint does not publish or modify the workflow.
     */
    @GetMapping("/{workflowId}/validation")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<WorkflowValidationResponse> validateWorkflow(
        @PathVariable UUID workflowId
    ) {
        return ResponseEntity.ok(validationService.validateWorkflow(workflowId));
    }

    /**
     * Shows whether every supported application type has exactly one
     * active published workflow. Administrators can use this before publishing.
     */
    @GetMapping("/coverage")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<WorkflowCoverageResponse> inspectWorkflowCoverage() {
        return ResponseEntity.ok(validationService.inspectApplicationTypeCoverage());
    }

    @GetMapping("/resolve")
    @PreAuthorize("hasRole('WORKFLOW_SERVICE')")
    public ResponseEntity<WorkflowResponse> resolveWorkflow(
        @RequestParam String applicationType
    ) {
        return ResponseEntity.ok(
            resolutionService.resolveForNewApplication(applicationType)
        );
    }

    @GetMapping("/{workflowId}/versions/{version}")
    @PreAuthorize("hasRole('WORKFLOW_SERVICE')")
    public ResponseEntity<WorkflowResponse> getPinnedWorkflow(
        @PathVariable UUID workflowId,
        @PathVariable Integer version
    ) {
        return ResponseEntity.ok(
            resolutionService.getPinnedWorkflow(workflowId, version)
        );
    }
}
// read more on

// then explain to be briefly using simple english why are we doing these steps"Our next steps
// Milestone 1 — Build and existing tests
// Passed
// Your latest log is the evidence.
// Milestone 2 — PostgreSQL concurrency tests
// Test simultaneous updates, duplicate transition requests, and workflow publication behavior using a real PostgreSQL database.
// Milestone 3 — Remaining security checks
// Check authentication, role permissions, request validation, HMAC signatures, production configuration, and database access.
// Milestone 4 — Evidence and decision
// Record which checks passed and which issues must be fixed before production"and they were for which phase ,also briefly write step by step to 