package com.zanzibar.workflow.model;



import com.zanzibar.workflow.enums.TriggerType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "workflow_stage_configs",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_workflow_source_action",
        columnNames = {
            "workflow_definition_id",
            "from_status",
            "action_key"
        }
    ),
    indexes = {
        @Index(
            name = "idx_stage_workflow_source",
            columnList = "workflow_definition_id, from_status, enabled"
        ),
        @Index(
            name = "idx_stage_workflow_destination",
            columnList = "workflow_definition_id, to_status"
        )
    }
)
public class WorkflowStageConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_definition_id", nullable = false)
    private WorkflowDefinition workflowDefinition;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "from_status", nullable = false, length = 80)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 80)
    private String toStatus;

    @Column(name = "action_key", nullable = false, length = 100)
    private String actionKey;

    @Column(name = "assigned_role", nullable = false, length = 100)
    private String assignedRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private TriggerType triggerType = TriggerType.HUMAN;

    @Column(name = "stage_order", nullable = false)
    private Integer stageOrder = 1;

    @Column(name = "sla_days")
    private Integer slaDays;

    @Column(name = "requires_comments", nullable = false)
    private boolean requiresComments = false;

    @Column(name = "external_approval", nullable = false)
    private boolean externalApproval = false;

    @Column(name = "external_system_id", length = 200)
    private String externalSystemId;

    @Column(nullable = false)
    private boolean enabled = true;

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

    public WorkflowDefinition getWorkflowDefinition() {
        return workflowDefinition;
    }

    public void setWorkflowDefinition(
        WorkflowDefinition workflowDefinition
    ) {
        this.workflowDefinition = workflowDefinition;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public String getAssignedRole() {
        return assignedRole;
    }

    public void setAssignedRole(String assignedRole) {
        this.assignedRole = assignedRole;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(TriggerType triggerType) {
        this.triggerType = triggerType;
    }

    public Integer getStageOrder() {
        return stageOrder;
    }

    public void setStageOrder(Integer stageOrder) {
        this.stageOrder = stageOrder;
    }

    public Integer getSlaDays() {
        return slaDays;
    }

    public void setSlaDays(Integer slaDays) {
        this.slaDays = slaDays;
    }

    public boolean isRequiresComments() {
        return requiresComments;
    }

    public void setRequiresComments(boolean requiresComments) {
        this.requiresComments = requiresComments;
    }

    public boolean isExternalApproval() {
        return externalApproval;
    }

    public void setExternalApproval(boolean externalApproval) {
        this.externalApproval = externalApproval;
    }

    public String getExternalSystemId() {
        return externalSystemId;
    }

    public void setExternalSystemId(String externalSystemId) {
        this.externalSystemId = externalSystemId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

