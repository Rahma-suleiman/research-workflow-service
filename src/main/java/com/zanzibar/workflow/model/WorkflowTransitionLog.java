package com.zanzibar.workflow.model;


import jakarta.persistence.*;

import com.zanzibar.workflow.enums.TriggerType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "workflow_transition_logs",
    indexes = {
        @Index(
            name = "idx_transition_log_subject",
            columnList = "subject_id, executed_at"
        ),
        @Index(
            name = "idx_transition_log_workflow",
            columnList =
                "workflow_definition_id, workflow_version, executed_at"
        )
    },
    uniqueConstraints = @UniqueConstraint(
        name = "uk_transition_idempotency_key",
        columnNames = "idempotency_key"
    )
)
public class WorkflowTransitionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_definition_id", nullable = false)
    private UUID workflowDefinitionId;

    @Column(name = "workflow_version", nullable = false)
    private Integer workflowVersion;

    @Column(name = "subject_id", nullable = false, length = 150)
    private String subjectId;

    @Column(name = "from_status", nullable = false, length = 80)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 80)
    private String toStatus;

    @Column(name = "action_key", nullable = false, length = 100)
    private String actionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private TriggerType triggerType;

    @Column(name = "actor_id", length = 150)
    private String actorId;

    @Column(name = "actor_role", length = 100)
    private String actorRole;

    @Column(columnDefinition = "TEXT")
    private String comments;

    @Column(name = "idempotency_key", length = 150)
    private String idempotencyKey;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @PrePersist
    protected void onCreate() {
        if (executedAt == null) {
            executedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
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

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public String getActionKey() {
        return actionKey;
    }

    public void setActionKey(String actionKey) {
        this.actionKey = actionKey;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(TriggerType triggerType) {
        this.triggerType = triggerType;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }
}

