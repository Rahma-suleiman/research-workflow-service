package com.zanzibar.workflow.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * One project served by this engine. All workflow data, registries and
 * client apps belong to exactly one tenant.
 */
@Entity
@Table(name = "workflow_tenants")
public class WorkflowTenant {

    /** Tenant that owns data created before multi-tenancy and the environment accounts. */
    public static final String DEFAULT_TENANT = "DEFAULT";

    @Id
    @Column(name = "tenant_key", length = 60)
    private String key;

    @Column(nullable = false, length = 200)
    private String name;

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

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
