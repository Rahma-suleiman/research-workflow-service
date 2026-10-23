-- V5: workflow instances. The engine can now own each subject's current
-- status and apply transitions atomically, instead of only validating them.
-- Validate-only transitions (/api/transitions/execute) keep working unchanged.

CREATE TABLE workflow_instances (
    id UUID PRIMARY KEY,
    tenant_key VARCHAR(60) NOT NULL,
    subject_type VARCHAR(60) NOT NULL,
    subject_id VARCHAR(150) NOT NULL,
    workflow_definition_id UUID NOT NULL,
    workflow_version INTEGER NOT NULL,
    current_status VARCHAR(80) NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_by_client VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_instance_tenant FOREIGN KEY (tenant_key) REFERENCES workflow_tenants (tenant_key),
    -- Pins the instance to one immutable workflow version.
    CONSTRAINT fk_instance_workflow_version FOREIGN KEY (workflow_definition_id, workflow_version)
        REFERENCES workflow_definitions (id, workflow_version),
    -- One workflow instance per subject.
    CONSTRAINT uk_instance_subject UNIQUE (tenant_key, subject_type, subject_id),
    CONSTRAINT chk_instance_status CHECK (current_status ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT chk_instance_subject_id CHECK (LENGTH(TRIM(subject_id)) > 0)
);

CREATE INDEX idx_instance_workflow_status
    ON workflow_instances (workflow_definition_id, current_status);

-- Transitions applied to an instance reference it; validate-only rows leave it null.
ALTER TABLE workflow_transition_logs ADD COLUMN instance_id UUID;
ALTER TABLE workflow_transition_logs
    ADD CONSTRAINT fk_transition_log_instance
    FOREIGN KEY (instance_id) REFERENCES workflow_instances (id);
CREATE INDEX idx_transition_log_instance
    ON workflow_transition_logs (instance_id, executed_at);
