package com.zanzibar.workflow.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A tenant's HTTP endpoint that receives workflow events. The signing secret
 * is encrypted with the webhook ID bound as associated data, so the ID is
 * assigned before the first save.
 */
@Entity
@Table(name = "workflow_webhooks")
public class WorkflowWebhook {

    @Id
    private UUID id;

    @Column(name = "tenant_key", nullable = false, length = 60, updatable = false)
    private String tenantKey;

    @Column(nullable = false, length = 2000)
    private String url;

    @Column(name = "event_types", nullable = false, length = 500)
    private String eventTypes;

    @Column(name = "secret_encrypted", nullable = false, columnDefinition = "TEXT")
    private String secretEncrypted;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(String eventTypes) {
        this.eventTypes = eventTypes;
    }

    public String getSecretEncrypted() {
        return secretEncrypted;
    }

    public void setSecretEncrypted(String secretEncrypted) {
        this.secretEncrypted = secretEncrypted;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
