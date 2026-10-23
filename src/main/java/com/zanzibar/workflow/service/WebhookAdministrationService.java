package com.zanzibar.workflow.service;

import com.zanzibar.workflow.dto.request.CreateWebhookRequest;
import com.zanzibar.workflow.dto.request.UpdateWebhookRequest;
import com.zanzibar.workflow.dto.response.IssuedWebhookSecretResponse;
import com.zanzibar.workflow.dto.response.WebhookDeliveryResponse;
import com.zanzibar.workflow.dto.response.WebhookResponse;
import com.zanzibar.workflow.enums.DeliveryStatus;
import com.zanzibar.workflow.enums.WorkflowEventType;
import com.zanzibar.workflow.exception.WorkflowException;
import com.zanzibar.workflow.model.WorkflowWebhook;
import com.zanzibar.workflow.model.WorkflowWebhookDelivery;
import com.zanzibar.workflow.repository.WorkflowWebhookDeliveryRepository;
import com.zanzibar.workflow.repository.WorkflowWebhookRepository;
import com.zanzibar.workflow.security.SecretCipher;
import com.zanzibar.workflow.security.SecretGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** A tenant administrator's webhook subscriptions and their delivery log. */
@Service
public class WebhookAdministrationService {

    private final WorkflowWebhookRepository webhookRepository;
    private final WorkflowWebhookDeliveryRepository deliveryRepository;
    private final SecretCipher secretCipher;
    private final WebhookUrlPolicy urlPolicy;

    public WebhookAdministrationService(
        WorkflowWebhookRepository webhookRepository,
        WorkflowWebhookDeliveryRepository deliveryRepository,
        SecretCipher secretCipher,
        WebhookUrlPolicy urlPolicy
    ) {
        this.webhookRepository = webhookRepository;
        this.deliveryRepository = deliveryRepository;
        this.secretCipher = secretCipher;
        this.urlPolicy = urlPolicy;
    }

    @Transactional(readOnly = true)
    public List<WebhookResponse> list(String tenantKey) {
        return webhookRepository.findByTenantKeyOrderByCreatedAtAsc(tenantKey).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public IssuedWebhookSecretResponse create(String tenantKey, CreateWebhookRequest request) {
        WorkflowWebhook webhook = new WorkflowWebhook();
        webhook.setId(UUID.randomUUID());
        webhook.setTenantKey(tenantKey);
        webhook.setUrl(urlPolicy.check(request.url()).toString());
        webhook.setEventTypes(normalizeEventTypes(request.eventTypes()));
        webhook.setActive(true);
        return issueSecret(webhook, "Webhook created.");
    }

    @Transactional
    public WebhookResponse update(String tenantKey, UUID webhookId, UpdateWebhookRequest request) {
        WorkflowWebhook webhook = getWebhook(tenantKey, webhookId);
        webhook.setUrl(urlPolicy.check(request.url()).toString());
        webhook.setEventTypes(normalizeEventTypes(request.eventTypes()));
        webhook.setActive(request.active());
        return toResponse(webhookRepository.save(webhook));
    }

    @Transactional
    public IssuedWebhookSecretResponse rotateSecret(String tenantKey, UUID webhookId) {
        return issueSecret(getWebhook(tenantKey, webhookId),
            "Signing secret rotated. Deliveries from now on use the new secret.");
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliveryResponse> deliveries(
        String tenantKey,
        UUID webhookId,
        DeliveryStatus status
    ) {
        WorkflowWebhook webhook = getWebhook(tenantKey, webhookId);
        List<WorkflowWebhookDelivery> deliveries = status == null
            ? deliveryRepository.findTop100ByWebhookIdOrderByCreatedAtDesc(webhook.getId())
            : deliveryRepository.findTop100ByWebhookIdAndStatusOrderByCreatedAtDesc(
                webhook.getId(), status);
        return deliveries.stream().map(this::toResponse).toList();
    }

    /** Schedules a failed (or pending) delivery for immediate redelivery with a fresh retry budget. */
    @Transactional
    public WebhookDeliveryResponse retryDelivery(String tenantKey, UUID deliveryId) {
        WorkflowWebhookDelivery delivery = deliveryRepository.findById(deliveryId)
            .filter(found -> webhookRepository
                .findByIdAndTenantKey(found.getWebhookId(), tenantKey).isPresent())
            .orElseThrow(() -> new WorkflowException(HttpStatus.NOT_FOUND,
                "Webhook delivery not found: " + deliveryId));
        if (delivery.getStatus() == DeliveryStatus.DELIVERED) {
            throw new WorkflowException(HttpStatus.CONFLICT, "This delivery already succeeded.");
        }
        delivery.setStatus(DeliveryStatus.PENDING);
        delivery.setAttempts(0);
        delivery.setNextAttemptAt(Instant.now());
        delivery.setLastError(null);
        return toResponse(deliveryRepository.save(delivery));
    }

    private IssuedWebhookSecretResponse issueSecret(WorkflowWebhook webhook, String message) {
        String secret = SecretGenerator.generate();
        webhook.setSecretEncrypted(secretCipher.encrypt(secret, webhook.getId().toString()));
        WorkflowWebhook saved = webhookRepository.save(webhook);
        return new IssuedWebhookSecretResponse(toResponse(saved), secret,
            message + " Store the signing secret now; it cannot be retrieved again.");
    }

    private WorkflowWebhook getWebhook(String tenantKey, UUID webhookId) {
        return webhookRepository.findByIdAndTenantKey(webhookId, tenantKey)
            .orElseThrow(() -> new WorkflowException(HttpStatus.NOT_FOUND,
                "Webhook not found: " + webhookId));
    }

    private String normalizeEventTypes(List<String> eventTypes) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String type : eventTypes) {
            String value = type.trim();
            if (!value.equals("*") && WorkflowEventType.fromWireName(value).isEmpty()) {
                throw new WorkflowException("Unknown event type: " + value + ". Use one of "
                    + Arrays.stream(WorkflowEventType.values()).map(WorkflowEventType::wireName)
                        .toList() + " or *.");
            }
            normalized.add(value);
        }
        return normalized.contains("*") ? "*" : String.join(",", normalized);
    }

    private WebhookResponse toResponse(WorkflowWebhook webhook) {
        return new WebhookResponse(webhook.getId(), webhook.getUrl(),
            List.of(webhook.getEventTypes().split(",")), webhook.isActive(),
            webhook.getCreatedAt(), webhook.getUpdatedAt());
    }

    private WebhookDeliveryResponse toResponse(WorkflowWebhookDelivery delivery) {
        return new WebhookDeliveryResponse(delivery.getId(), delivery.getEventId(),
            delivery.getStatus(), delivery.getAttempts(), delivery.getNextAttemptAt(),
            delivery.getLastStatusCode(), delivery.getLastError(), delivery.getDeliveredAt(),
            delivery.getCreatedAt());
    }
}
