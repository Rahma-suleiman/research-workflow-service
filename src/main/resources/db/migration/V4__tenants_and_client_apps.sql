-- V4: tenants and client apps, so one deployment can serve several projects
-- with isolated data and per-project credentials.
--
-- Existing rows move into the built-in DEFAULT tenant, so single-project
-- deployments keep working with the environment-configured accounts.

CREATE TABLE workflow_tenants (
    tenant_key VARCHAR(60) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_tenant_key_format CHECK (tenant_key ~ '^[A-Z][A-Z0-9_]*$')
);

INSERT INTO workflow_tenants (tenant_key, name) VALUES ('DEFAULT', 'Default tenant');

-- Credentials for a tenant's administrators (ADMIN) or backend (SERVICE).
-- Passwords are BCrypt hashes. SERVICE signing secrets are AES-GCM encrypted
-- with WORKFLOW_SECRET_ENCRYPTION_KEY because HMAC verification needs the value.
CREATE TABLE workflow_client_apps (
    id UUID PRIMARY KEY,
    tenant_key VARCHAR(60) NOT NULL,
    client_id VARCHAR(100) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    signing_secret_encrypted TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    credentials_rotated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_client_app_client_id UNIQUE (client_id),
    CONSTRAINT fk_client_app_tenant FOREIGN KEY (tenant_key) REFERENCES workflow_tenants (tenant_key),
    CONSTRAINT chk_client_app_kind CHECK (kind IN ('ADMIN', 'SERVICE')),
    CONSTRAINT chk_client_app_client_id CHECK (client_id ~ '^[a-z][a-z0-9_.-]*$'),
    CONSTRAINT chk_client_app_service_secret CHECK (
        kind <> 'SERVICE' OR signing_secret_encrypted IS NOT NULL
    )
);

CREATE INDEX idx_client_app_tenant ON workflow_client_apps (tenant_key);

-- Workflow definitions: keys and versions are unique per tenant.
ALTER TABLE workflow_definitions
    ADD COLUMN tenant_key VARCHAR(60) NOT NULL DEFAULT 'DEFAULT';
ALTER TABLE workflow_definitions ALTER COLUMN tenant_key DROP DEFAULT;
ALTER TABLE workflow_definitions
    ADD CONSTRAINT fk_workflow_tenant
    FOREIGN KEY (tenant_key) REFERENCES workflow_tenants (tenant_key);
ALTER TABLE workflow_definitions DROP CONSTRAINT uk_workflow_key_version;
ALTER TABLE workflow_definitions
    ADD CONSTRAINT uk_workflow_tenant_key_version
    UNIQUE (tenant_key, workflow_key, workflow_version);
DROP INDEX idx_workflow_status_active;
DROP INDEX idx_workflow_key;
CREATE INDEX idx_workflow_tenant_status_active
    ON workflow_definitions (tenant_key, status, active);

-- Registries become per tenant. Subject-type membership is validated by the
-- application against the workflow's own tenant registry.
ALTER TABLE workflow_definition_subject_types
    DROP CONSTRAINT fk_definition_subject_type_registry;

ALTER TABLE workflow_roles ADD COLUMN tenant_key VARCHAR(60) NOT NULL DEFAULT 'DEFAULT';
ALTER TABLE workflow_roles ALTER COLUMN tenant_key DROP DEFAULT;
ALTER TABLE workflow_roles DROP CONSTRAINT workflow_roles_pkey;
ALTER TABLE workflow_roles ADD CONSTRAINT pk_role_registry PRIMARY KEY (tenant_key, role_key);
ALTER TABLE workflow_roles
    ADD CONSTRAINT fk_role_tenant
    FOREIGN KEY (tenant_key) REFERENCES workflow_tenants (tenant_key);

ALTER TABLE workflow_subject_types ADD COLUMN tenant_key VARCHAR(60) NOT NULL DEFAULT 'DEFAULT';
ALTER TABLE workflow_subject_types ALTER COLUMN tenant_key DROP DEFAULT;
ALTER TABLE workflow_subject_types DROP CONSTRAINT workflow_subject_types_pkey;
ALTER TABLE workflow_subject_types
    ADD CONSTRAINT pk_subject_type_registry PRIMARY KEY (tenant_key, type_key);
ALTER TABLE workflow_subject_types
    ADD CONSTRAINT fk_subject_type_tenant
    FOREIGN KEY (tenant_key) REFERENCES workflow_tenants (tenant_key);

-- Transition logs: idempotency keys are unique per tenant, and each row
-- records which client app submitted it.
ALTER TABLE workflow_transition_logs
    ADD COLUMN tenant_key VARCHAR(60) NOT NULL DEFAULT 'DEFAULT';
ALTER TABLE workflow_transition_logs ALTER COLUMN tenant_key DROP DEFAULT;
ALTER TABLE workflow_transition_logs ADD COLUMN client_id VARCHAR(100);
ALTER TABLE workflow_transition_logs
    ADD CONSTRAINT fk_transition_log_tenant
    FOREIGN KEY (tenant_key) REFERENCES workflow_tenants (tenant_key);
ALTER TABLE workflow_transition_logs DROP CONSTRAINT uk_transition_idempotency_key;
ALTER TABLE workflow_transition_logs
    ADD CONSTRAINT uk_transition_tenant_idempotency_key
    UNIQUE (tenant_key, idempotency_key);
