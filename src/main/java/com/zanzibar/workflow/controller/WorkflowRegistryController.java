package com.zanzibar.workflow.controller;

import com.zanzibar.workflow.dto.request.CreateRegistryEntryRequest;
import com.zanzibar.workflow.dto.request.UpdateRegistryEntryRequest;
import com.zanzibar.workflow.dto.response.RegistryEntryResponse;
import com.zanzibar.workflow.service.WorkflowRegistryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.zanzibar.workflow.security.WorkflowPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * A tenant's administrators register the roles and subject types used by
 * their project before referencing them in workflow definitions.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class WorkflowRegistryController {

    private final WorkflowRegistryService registryService;

    public WorkflowRegistryController(WorkflowRegistryService registryService) {
        this.registryService = registryService;
    }

    @GetMapping("/roles")
    public ResponseEntity<List<RegistryEntryResponse>> listRoles(
        @AuthenticationPrincipal WorkflowPrincipal principal
    ) {
        return ResponseEntity.ok(registryService.listRoles(principal.tenantKey()));
    }

    @PostMapping("/roles")
    public ResponseEntity<RegistryEntryResponse> createRole(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @Valid @RequestBody CreateRegistryEntryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(registryService.createRole(principal.tenantKey(), request));
    }

    @PutMapping("/roles/{roleKey}")
    public ResponseEntity<RegistryEntryResponse> updateRole(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @PathVariable String roleKey,
        @Valid @RequestBody UpdateRegistryEntryRequest request
    ) {
        return ResponseEntity.ok(
            registryService.updateRole(principal.tenantKey(), roleKey, request));
    }

    @GetMapping("/subject-types")
    public ResponseEntity<List<RegistryEntryResponse>> listSubjectTypes(
        @AuthenticationPrincipal WorkflowPrincipal principal
    ) {
        return ResponseEntity.ok(registryService.listSubjectTypes(principal.tenantKey()));
    }

    @PostMapping("/subject-types")
    public ResponseEntity<RegistryEntryResponse> createSubjectType(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @Valid @RequestBody CreateRegistryEntryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(registryService.createSubjectType(principal.tenantKey(), request));
    }

    @PutMapping("/subject-types/{typeKey}")
    public ResponseEntity<RegistryEntryResponse> updateSubjectType(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @PathVariable String typeKey,
        @Valid @RequestBody UpdateRegistryEntryRequest request
    ) {
        return ResponseEntity.ok(
            registryService.updateSubjectType(principal.tenantKey(), typeKey, request));
    }
}
