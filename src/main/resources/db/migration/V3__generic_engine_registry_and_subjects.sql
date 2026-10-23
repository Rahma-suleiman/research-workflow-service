-- V3: remove domain-specific assumptions so the engine can serve any project.
--
-- * Roles and subject types become data (registries) instead of code constants.
-- * "Application type" becomes the generic "subject type".
-- * Each workflow declares its own initial status instead of assuming DRAFT.
-- * The PAYMENT trigger becomes the generic EXTERNAL_EVENT trigger.
-- * Transition logs reference any external subject ID, not only UUIDs.
--
-- Existing rows are preserved; roles and subject types already in use are
-- backfilled into the new registries so published workflows stay valid.

-- 1. Role registry. SYSTEM is a built-in reserved role and is never stored.
CREATE TABLE workflow_roles (
    role_key VARCHAR(100) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_role_key_format CHECK (role_key ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT chk_role_key_not_reserved CHECK (role_key <> 'SYSTEM')
);

INSERT INTO workflow_roles (role_key, name)
SELECT DISTINCT role, role
FROM (
    SELECT UPPER(TRIM(UNNEST(STRING_TO_ARRAY(assigned_role, ',')))) AS role
    FROM workflow_stage_configs
) used
WHERE role ~ '^[A-Z][A-Z0-9_]*$'
  AND role <> 'SYSTEM';

-- 2. Subject-type registry (what a workflow applies to: an application type,
--    an order type, a document type, ...).
CREATE TABLE workflow_subject_types (
    type_key VARCHAR(60) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_subject_type_key_format CHECK (type_key ~ '^[A-Z][A-Z0-9_]*$')
);

UPDATE workflow_definition_application_types
SET application_type = UPPER(TRIM(application_type));

INSERT INTO workflow_subject_types (type_key, name)
SELECT DISTINCT application_type, application_type
FROM workflow_definition_application_types;

-- 3. Rename application types to subject types on workflow definitions.
ALTER TABLE workflow_definition_application_types
    RENAME TO workflow_definition_subject_types;
ALTER TABLE workflow_definition_subject_types
    RENAME COLUMN application_type TO subject_type;
ALTER TABLE workflow_definition_subject_types
    RENAME CONSTRAINT pk_workflow_application_types TO pk_workflow_subject_types;
ALTER TABLE workflow_definition_subject_types
    RENAME CONSTRAINT fk_application_types_workflow TO fk_subject_types_workflow;
ALTER TABLE workflow_definition_subject_types
    RENAME CONSTRAINT chk_application_type_not_blank TO chk_subject_type_not_blank;
ALTER INDEX idx_workflow_application_type RENAME TO idx_workflow_subject_type;

ALTER TABLE workflow_definition_subject_types
    ADD CONSTRAINT fk_definition_subject_type_registry
    FOREIGN KEY (subject_type) REFERENCES workflow_subject_types (type_key);

-- 4. Generic scope name.
ALTER TABLE workflow_definitions DROP CONSTRAINT chk_workflow_scope;
UPDATE workflow_definitions SET scope = 'ALL_TYPES' WHERE scope = 'ALL_APPLICATIONS';
ALTER TABLE workflow_definitions
    ADD CONSTRAINT chk_workflow_scope CHECK (scope IN ('ALL_TYPES', 'SELECTED_TYPES'));

-- 5. Configurable initial status. Existing workflows started at DRAFT.
ALTER TABLE workflow_definitions
    ADD COLUMN initial_status VARCHAR(80) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE workflow_definitions ALTER COLUMN initial_status DROP DEFAULT;
ALTER TABLE workflow_definitions
    ADD CONSTRAINT chk_workflow_initial_status
    CHECK (initial_status ~ '^[A-Z][A-Z0-9_]*$');

-- 6. Generic external trigger.
ALTER TABLE workflow_stage_configs DROP CONSTRAINT chk_stage_trigger_type;
UPDATE workflow_stage_configs SET trigger_type = 'EXTERNAL_EVENT' WHERE trigger_type = 'PAYMENT';
ALTER TABLE workflow_stage_configs
    ADD CONSTRAINT chk_stage_trigger_type
    CHECK (trigger_type IN ('HUMAN', 'SYSTEM', 'EXTERNAL_EVENT'));

ALTER TABLE workflow_transition_logs DROP CONSTRAINT chk_transition_log_trigger_type;
UPDATE workflow_transition_logs SET trigger_type = 'EXTERNAL_EVENT' WHERE trigger_type = 'PAYMENT';
ALTER TABLE workflow_transition_logs
    ADD CONSTRAINT chk_transition_log_trigger_type
    CHECK (trigger_type IN ('HUMAN', 'SYSTEM', 'EXTERNAL_EVENT'));

-- 7. Subjects are owned by the calling system and may use any ID format.
ALTER TABLE workflow_transition_logs RENAME COLUMN application_id TO subject_id;
ALTER TABLE workflow_transition_logs
    ALTER COLUMN subject_id TYPE VARCHAR(150) USING subject_id::text;
ALTER INDEX idx_transition_log_application RENAME TO idx_transition_log_subject;
