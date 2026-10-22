-- V1__create_workflow_tables.sql


CREATE TABLE workflow_definitions (
    id UUID PRIMARY KEY,
    workflow_key VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    scope VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    workflow_version INTEGER NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,

    CONSTRAINT uk_workflow_key_version
        UNIQUE (workflow_key, workflow_version),

    CONSTRAINT uk_workflow_id_version
        UNIQUE (id, workflow_version),

    CONSTRAINT chk_workflow_scope
        CHECK (scope IN ('ALL_APPLICATIONS', 'SELECTED_TYPES')),

    CONSTRAINT chk_workflow_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),

    CONSTRAINT chk_workflow_version_positive
        CHECK (workflow_version > 0)
);


CREATE INDEX idx_workflow_status_active ON workflow_definitions (status, active);

CREATE INDEX idx_workflow_key ON workflow_definitions (workflow_key);

-- A workflow can apply to one or more Research Permit application types.
-- ALL_APPLICATIONS workflows do not need rows in this table.

CREATE TABLE workflow_definition_application_types (
    workflow_definition_id UUID NOT NULL,
    application_type VARCHAR(60) NOT NULL,
    CONSTRAINT pk_workflow_application_types PRIMARY KEY (
        workflow_definition_id,
        application_type
    ),
    CONSTRAINT fk_application_types_workflow FOREIGN KEY (workflow_definition_id) REFERENCES workflow_definitions (id) ON DELETE CASCADE,
    CONSTRAINT chk_application_type_not_blank CHECK (
        LENGTH(TRIM(application_type)) > 0
    )
);

CREATE INDEX idx_workflow_application_type ON workflow_definition_application_types (application_type);

-- Each record configures a transition between two workflow statuses.

CREATE TABLE workflow_stage_configs (
    id UUID PRIMARY KEY,
    workflow_definition_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    from_status VARCHAR(80) NOT NULL,
    to_status VARCHAR(80) NOT NULL,
    action_key VARCHAR(100) NOT NULL,
    assigned_role VARCHAR(100) NOT NULL,
    trigger_type VARCHAR(20) NOT NULL,
    stage_order INTEGER NOT NULL DEFAULT 1,
    sla_days INTEGER,
    requires_comments BOOLEAN NOT NULL DEFAULT FALSE,
    external_approval BOOLEAN NOT NULL DEFAULT FALSE,
    external_system_id VARCHAR(200),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stage_workflow FOREIGN KEY (workflow_definition_id) REFERENCES workflow_definitions (id) ON DELETE CASCADE,
    CONSTRAINT uk_workflow_source_action UNIQUE (
        workflow_definition_id,
        from_status,
        action_key
    ),
    CONSTRAINT chk_stage_trigger_type CHECK (
        trigger_type IN ('HUMAN', 'SYSTEM', 'PAYMENT')
    ),
    CONSTRAINT chk_stage_sla_days CHECK (
        sla_days IS NULL
        OR sla_days >= 0
    ),
    CONSTRAINT chk_stage_order CHECK (stage_order > 0),
    CONSTRAINT chk_stage_status_not_blank CHECK (
        LENGTH(TRIM(from_status)) > 0
        AND LENGTH(TRIM(to_status)) > 0
    ),
    CONSTRAINT chk_stage_not_self_transition CHECK (from_status <> to_status)
);

CREATE INDEX idx_stage_workflow_source ON workflow_stage_configs (
    workflow_definition_id,
    from_status,
    enabled
);

CREATE INDEX idx_stage_workflow_destination ON workflow_stage_configs (
    workflow_definition_id,
    to_status
);

-- Audit record for a transition accepted by the workflow service.
-- application_id is an external ID: the permit database owns the application.

CREATE TABLE workflow_transition_logs (
    id UUID PRIMARY KEY,
    workflow_definition_id UUID NOT NULL,
    workflow_version INTEGER NOT NULL,
    application_id UUID NOT NULL,
    from_status VARCHAR(80) NOT NULL,
    to_status VARCHAR(80) NOT NULL,
    action_key VARCHAR(100) NOT NULL,
    actor_id VARCHAR(150),
    actor_role VARCHAR(100),
    comments TEXT,
    idempotency_key VARCHAR(150),
    executed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transition_log_workflow FOREIGN KEY (workflow_definition_id) REFERENCES workflow_definitions (id),
    CONSTRAINT fk_transition_log_workflow_version FOREIGN KEY (
        workflow_definition_id,
        workflow_version
    ) REFERENCES workflow_definitions (id, workflow_version),
    CONSTRAINT uk_transition_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_log_workflow_version CHECK (workflow_version > 0)
);

CREATE INDEX idx_transition_log_application ON workflow_transition_logs (application_id, executed_at);

CREATE INDEX idx_transition_log_workflow ON workflow_transition_logs (
    workflow_definition_id,
    workflow_version,
    executed_at
);