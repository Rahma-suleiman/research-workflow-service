package com.zanzibar.workflow.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * An outbox event, written in the same transaction as the change it reports.
 * The ID is assigned up front because it is part of the JSON payload.
 */
@Entity
@Table(name = "workflow_events")
public class WorkflowEvent {

    @Id
    private UUID id;

    @Column(name = "tenant_key", nullable = false, length = 60, updatable = false)
    private String tenantKey;

    @Column(name = "event_type", nullable = false, length = 60, updatable = false)
    private String eventType;

    @Column(name = "instance_id", updatable = false)
    private UUID instanceId;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTenantKey() {
        return tenantKey;
    }

    public void setTenantKey(String tenantKey) {
        this.tenantKey = tenantKey;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(UUID instanceId) {
        this.instanceId = instanceId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
