package com.zanzibar.workflow.controller;

import com.zanzibar.workflow.dto.request.CreateWebhookRequest;
import com.zanzibar.workflow.dto.request.UpdateWebhookRequest;
import com.zanzibar.workflow.dto.response.IssuedWebhookSecretResponse;
import com.zanzibar.workflow.dto.response.WebhookDeliveryResponse;
import com.zanzibar.workflow.dto.response.WebhookResponse;
import com.zanzibar.workflow.enums.DeliveryStatus;
import com.zanzibar.workflow.security.WorkflowPrincipal;
import com.zanzibar.workflow.service.WebhookAdministrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** A tenant administrator's webhook subscriptions and delivery log. */
@RestController
@RequestMapping("/api/webhooks")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class WebhookController {

    private final WebhookAdministrationService webhookService;

    public WebhookController(WebhookAdministrationService webhookService) {
        this.webhookService = webhookService;
    }

    @GetMapping
    public ResponseEntity<List<WebhookResponse>> listWebhooks(
        @AuthenticationPrincipal WorkflowPrincipal principal
    ) {
        return ResponseEntity.ok(webhookService.list(principal.tenantKey()));
    }

    @PostMapping
    public ResponseEntity<IssuedWebhookSecretResponse> createWebhook(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @Valid @RequestBody CreateWebhookRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(webhookService.create(principal.tenantKey(), request));
    }

    @PutMapping("/{webhookId}")
    public ResponseEntity<WebhookResponse> updateWebhook(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @PathVariable UUID webhookId,
        @Valid @RequestBody UpdateWebhookRequest request
    ) {
        return ResponseEntity.ok(
            webhookService.update(principal.tenantKey(), webhookId, request));
    }

    @PostMapping("/{webhookId}/rotate-secret")
    public ResponseEntity<IssuedWebhookSecretResponse> rotateSecret(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @PathVariable UUID webhookId
    ) {
        return ResponseEntity.ok(webhookService.rotateSecret(principal.tenantKey(), webhookId));
    }

    @GetMapping("/{webhookId}/deliveries")
    public ResponseEntity<List<WebhookDeliveryResponse>> listDeliveries(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @PathVariable UUID webhookId,
        @RequestParam(required = false) DeliveryStatus status
    ) {
        return ResponseEntity.ok(
            webhookService.deliveries(principal.tenantKey(), webhookId, status));
    }

    @PostMapping("/deliveries/{deliveryId}/retry")
    public ResponseEntity<WebhookDeliveryResponse> retryDelivery(
        @AuthenticationPrincipal WorkflowPrincipal principal,
        @PathVariable UUID deliveryId
    ) {
        return ResponseEntity.ok(
            webhookService.retryDelivery(principal.tenantKey(), deliveryId));
    }
}
