package com.zanzibar.workflow.controller;

import com.zanzibar.workflow.dto.request.ExecuteTransitionRequest;
import com.zanzibar.workflow.dto.response.TransitionResponse;
import com.zanzibar.workflow.service.WorkflowEngineService;
import com.zanzibar.workflow.service.WorkflowActorSignatureService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transitions")
public class WorkflowTransitionController {

    private final WorkflowEngineService workflowEngineService;
    private final WorkflowActorSignatureService actorSignatureService;

    public WorkflowTransitionController(
        WorkflowEngineService workflowEngineService,
        WorkflowActorSignatureService actorSignatureService
    ) {
        this.workflowEngineService = workflowEngineService;
        this.actorSignatureService = actorSignatureService;
    }

    @PostMapping("/execute")
    @PreAuthorize("hasRole('WORKFLOW_SERVICE')")
    public ResponseEntity<TransitionResponse> executeTransition(
        @Valid @RequestBody ExecuteTransitionRequest request,
        @RequestHeader(value = "X-Actor-Signature", required = false) String actorSignature
    ) {
        actorSignatureService.verify(request, actorSignature);
        return ResponseEntity.ok(
            workflowEngineService.validateTransition(request)
        );
    }
    

}
