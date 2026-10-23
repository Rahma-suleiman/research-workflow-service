package com.zanzibar.workflow.model;



import com.zanzibar.workflow.enums.WorkflowScope;
import com.zanzibar.workflow.enums.WorkflowStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
    name = "workflow_definitions",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_workflow_tenant_key_version",
            columnNames = {"tenant_key", "workflow_key", "workflow_version"}
        ),
        @UniqueConstraint(
            name = "uk_workflow_id_version",
            columnNames = {"id", "workflow_version"}
        )
    }
)
public class WorkflowDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_key", nullable = false, length = 60, updatable = false)
    private String tenantKey;

    @Column(name = "workflow_key", nullable = false, length = 100)
    private String workflowKey;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WorkflowScope scope = WorkflowScope.SELECTED_TYPES;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WorkflowStatus status = WorkflowStatus.DRAFT;

    @Column(name = "workflow_version", nullable = false)
    private Integer workflowVersion = 1;

    @Version
    @Column(name = "entity_version", nullable = false)
    private Long entityVersion = 0L;

    @Column(nullable = false)
    private boolean active = false;

    @Column(name = "initial_status", nullable = false, length = 80)
    private String initialStatus;

    @ElementCollection
    @CollectionTable(
        name = "workflow_definition_subject_types",
        joinColumns = @JoinColumn(name = "workflow_definition_id")
    )
    @Column(name = "subject_type", nullable = false, length = 60)
    private Set<String> subjectTypes = new LinkedHashSet<>();

    @OneToMany(
        mappedBy = "workflowDefinition",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @OrderBy("stageOrder ASC")
    private List<WorkflowStageConfig> stages = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

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

    public void addStage(WorkflowStageConfig stage) {
        stages.add(stage);
        stage.setWorkflowDefinition(this);
    }

    public void removeStage(WorkflowStageConfig stage) {
        stages.remove(stage);
        stage.setWorkflowDefinition(null);
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

    public String getWorkflowKey() {
        return workflowKey;
    }

    public void setWorkflowKey(String workflowKey) {
        this.workflowKey = workflowKey;
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

    public WorkflowScope getScope() {
        return scope;
    }

    public void setScope(WorkflowScope scope) {
        this.scope = scope;
    }

    public WorkflowStatus getStatus() {
        return status;
    }

    public void setStatus(WorkflowStatus status) {
        this.status = status;
    }

    public Integer getWorkflowVersion() {
        return workflowVersion;
    }

    public void setWorkflowVersion(Integer workflowVersion) {
        this.workflowVersion = workflowVersion;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getInitialStatus() {
        return initialStatus;
    }

    public void setInitialStatus(String initialStatus) {
        this.initialStatus = initialStatus;
    }

    public Set<String> getSubjectTypes() {
        return subjectTypes;
    }

    public void setSubjectTypes(Set<String> subjectTypes) {
        this.subjectTypes =
            subjectTypes == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(subjectTypes);
    }

    public List<WorkflowStageConfig> getStages() {
        return stages;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
}
