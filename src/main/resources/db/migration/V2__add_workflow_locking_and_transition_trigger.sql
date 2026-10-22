-- Add optimistic locking for concurrent workflow edits/publications.
ALTER TABLE workflow_definitions
    ADD COLUMN entity_version BIGINT NOT NULL DEFAULT 0;

-- Preserve the trigger used for each validated decision so idempotency checks
-- can compare all security-relevant request fields. V1 remains unchanged.
ALTER TABLE workflow_transition_logs
    ADD COLUMN trigger_type VARCHAR(20) NOT NULL DEFAULT 'HUMAN';

UPDATE workflow_transition_logs log
SET trigger_type = stage.trigger_type
FROM workflow_stage_configs stage
WHERE stage.workflow_definition_id = log.workflow_definition_id
  AND stage.from_status = log.from_status
  AND stage.action_key = log.action_key;

ALTER TABLE workflow_transition_logs
    ADD CONSTRAINT chk_transition_log_trigger_type
    CHECK (trigger_type IN ('HUMAN', 'SYSTEM', 'PAYMENT'));

ALTER TABLE workflow_transition_logs
    ALTER COLUMN trigger_type DROP DEFAULT;
