package com.zanzibar.workflow.controller;

import com.zanzibar.workflow.dto.request.CreateRegistryEntryRequest;
import com.zanzibar.workflow.dto.request.UpdateRegistryEntryRequest;
import com.zanzibar.workflow.dto.response.RegistryEntryResponse;
import com.zanzibar.workflow.service.WorkflowRegistryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Administrators register the roles and subject types used by their project
 * before referencing them in workflow definitions.
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
    public ResponseEntity<List<RegistryEntryResponse>> listRoles() {
        return ResponseEntity.ok(registryService.listRoles());
    }

    @PostMapping("/roles")
    public ResponseEntity<RegistryEntryResponse> createRole(
        @Valid @RequestBody CreateRegistryEntryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(registryService.createRole(request));
    }

    @PutMapping("/roles/{roleKey}")
    public ResponseEntity<RegistryEntryResponse> updateRole(
        @PathVariable String roleKey,
        @Valid @RequestBody UpdateRegistryEntryRequest request
    ) {
        return ResponseEntity.ok(registryService.updateRole(roleKey, request));
    }

    @GetMapping("/subject-types")
    public ResponseEntity<List<RegistryEntryResponse>> listSubjectTypes() {
        return ResponseEntity.ok(registryService.listSubjectTypes());
    }

    @PostMapping("/subject-types")
    public ResponseEntity<RegistryEntryResponse> createSubjectType(
        @Valid @RequestBody CreateRegistryEntryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(registryService.createSubjectType(request));
    }

    @PutMapping("/subject-types/{typeKey}")
    public ResponseEntity<RegistryEntryResponse> updateSubjectType(
        @PathVariable String typeKey,
        @Valid @RequestBody UpdateRegistryEntryRequest request
    ) {
        return ResponseEntity.ok(registryService.updateSubjectType(typeKey, request));
    }
}
