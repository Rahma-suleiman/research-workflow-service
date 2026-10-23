package com.zanzibar.workflow.controller;

import com.zanzibar.workflow.dto.request.InstanceActionRequest;
import com.zanzibar.workflow.dto.request.StartInstanceRequest;
import com.zanzibar.workflow.dto.response.InstanceActionResponse;
import com.zanzibar.workflow.dto.response.InstanceResponse;
import com.zanzibar.workflow.dto.response.TransitionHistoryResponse;
import com.zanzibar.workflow.security.WorkflowPrincipal;
import com.zanzibar.workflow.service.TenantAdministrationService;
import com.zanzibar.workflow.service.WorkflowActorSignatureService;
import com.zanzibar.workflow.service.WorkflowInstanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Stateful mode: the engine owns each subject's status. A tenant's backend
 * starts instances and performs signed actions; its administrators may read them.
 */
@RestController
@RequestMapping("/api/instances")
public class WorkflowInstanceController {

    private final WorkflowInstanceService instanceService;
    private final WorkflowActorSignatureService actorSignatureService;
    private final TenantAdministrationService tenantAdministrationService;

    public WorkflowInstanceController(
        WorkflowInstanceService instanceService,
        WorkflowActorSignatureService actorSignatureService,
        TenantAdministrationService tenantAdministrationService
    ) {
        this.instanceService = instanceService;
        this.actorSignatureService = actorSignatureService;
        this.tenantAdministrationService = tenantAdministrationService;
    }

    /** Returns 201 for a new instance, or 200 with the subject's existing instance. */
    @PostMapping
    @PreAuthorize("hasRole('WORKFLOW_SERVICE')")
    public ResponseEntity<InstanceResponse> startInstance(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @Valid @RequestBody StartInstanceRequest request
    ) {
        WorkflowInstanceService.StartResult result =
            instanceService.start(principal.tenantKey(), principal.clientId(), request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
            .body(result.instance());
    }

    @GetMapping("/{instanceId}")
    @PreAuthorize("hasAnyRole('WORKFLOW_SERVICE', 'ADMINISTRATOR')")
    public ResponseEntity<InstanceResponse> getInstance(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @PathVariable UUID instanceId,
        @RequestParam(required = false) String actorRole
    ) {
        return ResponseEntity.ok(
            instanceService.get(principal.tenantKey(), instanceId, actorRole));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('WORKFLOW_SERVICE', 'ADMINISTRATOR')")
    public ResponseEntity<InstanceResponse> findBySubject(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @RequestParam String subjectType,
        @RequestParam String subjectId,
        @RequestParam(required = false) String actorRole
    ) {
        return ResponseEntity.ok(instanceService.findBySubject(
            principal.tenantKey(), subjectType, subjectId, actorRole));
    }

    @GetMapping("/{instanceId}/history")
    @PreAuthorize("hasAnyRole('WORKFLOW_SERVICE', 'ADMINISTRATOR')")
    public ResponseEntity<List<TransitionHistoryResponse>> getHistory(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @PathVariable UUID instanceId
    ) {
        return ResponseEntity.ok(instanceService.history(principal.tenantKey(), instanceId));
    }

    @PostMapping("/{instanceId}/actions/{actionKey}")
    @PreAuthorize("hasRole('WORKFLOW_SERVICE')")
    public ResponseEntity<InstanceActionResponse> performAction(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @PathVariable UUID instanceId,
        @PathVariable String actionKey,
        @Valid @RequestBody InstanceActionRequest request,
        @RequestHeader(value = "X-Actor-Signature", required = false) String actorSignature
    ) {
        actorSignatureService.verifyInstanceAction(instanceId, actionKey, request,
            actorSignature, tenantAdministrationService.signingSecretFor(principal));
        return ResponseEntity.ok(instanceService.perform(
            principal.tenantKey(), principal.clientId(), instanceId, actionKey, request));
    }
}
