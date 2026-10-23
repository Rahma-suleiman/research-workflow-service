package com.zanzibar.workflow.service;

import com.zanzibar.workflow.enums.WorkflowEventType;
import com.zanzibar.workflow.model.WorkflowEvent;
import com.zanzibar.workflow.model.WorkflowWebhook;
import com.zanzibar.workflow.model.WorkflowWebhookDelivery;
import com.zanzibar.workflow.repository.WorkflowEventRepository;
import com.zanzibar.workflow.repository.WorkflowWebhookDeliveryRepository;
import com.zanzibar.workflow.repository.WorkflowWebhookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Transactional outbox: records an event and one pending delivery per
 * subscribed webhook inside the caller's transaction. If the change that
 * caused the event rolls back, so does the event. Nothing is sent here; the
 * {@link WebhookDispatcher} delivers after commit.
 */
@Service
public class WorkflowEventPublisher {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final WorkflowWebhookRepository webhookRepository;
    private final WorkflowEventRepository eventRepository;
    private final WorkflowWebhookDeliveryRepository deliveryRepository;

    public WorkflowEventPublisher(
        WorkflowWebhookRepository webhookRepository,
        WorkflowEventRepository eventRepository,
        WorkflowWebhookDeliveryRepository deliveryRepository
    ) {
        this.webhookRepository = webhookRepository;
        this.eventRepository = eventRepository;
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(
        String tenantKey,
        WorkflowEventType type,
        UUID instanceId,
        Map<String, Object> data
    ) {
        List<WorkflowWebhook> subscribers = webhookRepository.findByTenantKeyAndActiveTrue(tenantKey)
            .stream()
            .filter(webhook -> subscribes(webhook, type))
            .toList();
        if (subscribers.isEmpty()) {
            return;
        }

        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", eventId.toString());
        envelope.put("type", type.wireName());
        envelope.put("tenantKey", tenantKey);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("data", data);

        WorkflowEvent event = new WorkflowEvent();
        event.setId(eventId);
        event.setTenantKey(tenantKey);
        event.setEventType(type.wireName());
        event.setInstanceId(instanceId);
        event.setPayload(JSON.writeValueAsString(envelope));
        event.setOccurredAt(occurredAt);
        eventRepository.save(event);

        for (WorkflowWebhook webhook : subscribers) {
            WorkflowWebhookDelivery delivery = new WorkflowWebhookDelivery();
            delivery.setEventId(eventId);
            delivery.setWebhookId(webhook.getId());
            delivery.setNextAttemptAt(occurredAt);
            deliveryRepository.save(delivery);
        }
    }

    static boolean subscribes(WorkflowWebhook webhook, WorkflowEventType type) {
        return Arrays.stream(webhook.getEventTypes().split(","))
            .map(String::trim)
            .anyMatch(value -> value.equals("*") || value.equals(type.wireName()));
    }
}
