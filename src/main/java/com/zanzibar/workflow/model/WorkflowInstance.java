package com.zanzibar.workflow.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One subject (a record in the calling system) moving through a pinned
 * workflow version. The engine owns its current status.
 */
@Entity
@Table(
    name = "workflow_instances",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_instance_subject",
        columnNames = {"tenant_key", "subject_type", "subject_id"}
    )
)
public class WorkflowInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_key", nullable = false, length = 60, updatable = false)
    private String tenantKey;

    @Column(name = "subject_type", nullable = false, length = 60, updatable = false)
    private String subjectType;

    @Column(name = "subject_id", nullable = false, length = 150, updatable = false)
    private String subjectId;

    @Column(name = "workflow_definition_id", nullable = false, updatable = false)
    private UUID workflowDefinitionId;

    @Column(name = "workflow_version", nullable = false, updatable = false)
    private Integer workflowVersion;

    @Column(name = "current_status", nullable = false, length = 80)
    private String currentStatus;

    @Column(name = "status_entered_at", nullable = false)
    private Instant statusEnteredAt;

    /** When the earliest outgoing action with slaDays falls due; null when none has one. */
    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "sla_breach_notified", nullable = false)
    private boolean slaBreachNotified = false;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion = 0L;

    @Column(name = "created_by_client", length = 100, updatable = false)
    private String createdByClient;

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

    public String getTenantKey() {
        return tenantKey;
    }

    public void setTenantKey(String tenantKey) {
        this.tenantKey = tenantKey;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public UUID getWorkflowDefinitionId() {
        return workflowDefinitionId;
    }

    public void setWorkflowDefinitionId(UUID workflowDefinitionId) {
        this.workflowDefinitionId = workflowDefinitionId;
    }

    public Integer getWorkflowVersion() {
        return workflowVersion;
    }

    public void setWorkflowVersion(Integer workflowVersion) {
        this.workflowVersion = workflowVersion;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(String currentStatus) {
        this.currentStatus = currentStatus;
    }

    public Instant getStatusEnteredAt() {
        return statusEnteredAt;
    }

    public void setStatusEnteredAt(Instant statusEnteredAt) {
        this.statusEnteredAt = statusEnteredAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public boolean isSlaBreachNotified() {
        return slaBreachNotified;
    }

    public void setSlaBreachNotified(boolean slaBreachNotified) {
        this.slaBreachNotified = slaBreachNotified;
    }

    public Long getLockVersion() {
        return lockVersion;
    }

    public String getCreatedByClient() {
        return createdByClient;
    }

    public void setCreatedByClient(String createdByClient) {
        this.createdByClient = createdByClient;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
