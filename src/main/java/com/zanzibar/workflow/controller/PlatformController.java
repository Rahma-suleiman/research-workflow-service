package com.zanzibar.workflow.controller;

import com.zanzibar.workflow.dto.request.CreateClientAppRequest;
import com.zanzibar.workflow.dto.request.CreateTenantRequest;
import com.zanzibar.workflow.dto.request.UpdateClientAppRequest;
import com.zanzibar.workflow.dto.request.UpdateTenantRequest;
import com.zanzibar.workflow.dto.response.ClientAppResponse;
import com.zanzibar.workflow.dto.response.IssuedCredentialsResponse;
import com.zanzibar.workflow.dto.response.TenantResponse;
import com.zanzibar.workflow.service.TenantAdministrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Platform administration: onboard projects as tenants and issue their
 * client app credentials. Only the environment administrator can call these.
 */
@RestController
@RequestMapping("/api/platform")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformController {

    private final TenantAdministrationService tenantService;

    public PlatformController(TenantAdministrationService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping("/tenants")
    public ResponseEntity<List<TenantResponse>> listTenants() {
        return ResponseEntity.ok(tenantService.listTenants());
    }

    @PostMapping("/tenants")
    public ResponseEntity<TenantResponse> createTenant(
        @Valid @RequestBody CreateTenantRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(tenantService.createTenant(request));
    }

    @PutMapping("/tenants/{tenantKey}")
    public ResponseEntity<TenantResponse> updateTenant(
        @PathVariable String tenantKey,
        @Valid @RequestBody UpdateTenantRequest request
    ) {
        return ResponseEntity.ok(tenantService.updateTenant(tenantKey, request));
    }

    @GetMapping("/tenants/{tenantKey}/clients")
    public ResponseEntity<List<ClientAppResponse>> listClients(
        @PathVariable String tenantKey
    ) {
        return ResponseEntity.ok(tenantService.listClients(tenantKey));
    }

    @PostMapping("/tenants/{tenantKey}/clients")
    public ResponseEntity<IssuedCredentialsResponse> createClient(
        @PathVariable String tenantKey,
        @Valid @RequestBody CreateClientAppRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(tenantService.createClient(tenantKey, request));
    }

    @PutMapping("/clients/{clientId}")
    public ResponseEntity<ClientAppResponse> updateClient(
        @PathVariable String clientId,
        @Valid @RequestBody UpdateClientAppRequest request
    ) {
        return ResponseEntity.ok(tenantService.updateClient(clientId, request));
    }

    @PostMapping("/clients/{clientId}/rotate-credentials")
    public ResponseEntity<IssuedCredentialsResponse> rotateCredentials(
        @PathVariable String clientId
    ) {
        return ResponseEntity.ok(tenantService.rotateCredentials(clientId));
    }
}
