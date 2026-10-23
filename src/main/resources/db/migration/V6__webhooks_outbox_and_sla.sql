-- V6: integration features.
--
-- * Webhook subscriptions per tenant.
-- * A transactional outbox: events and their per-webhook deliveries are written
--   in the same transaction as the instance change, so a committed status change
--   is never left without its notification. A background dispatcher delivers
--   them with retries.
-- * SLA tracking on instances.

CREATE TABLE workflow_webhooks (
    id UUID PRIMARY KEY,
    tenant_key VARCHAR(60) NOT NULL,
    url VARCHAR(2000) NOT NULL,
    -- '*' or a comma-separated list of event types.
    event_types VARCHAR(500) NOT NULL,
    secret_encrypted TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_webhook_tenant FOREIGN KEY (tenant_key) REFERENCES workflow_tenants (tenant_key),
    CONSTRAINT chk_webhook_url CHECK (url ~ '^https?://'),
    CONSTRAINT chk_webhook_event_types CHECK (LENGTH(TRIM(event_types)) > 0)
);

CREATE INDEX idx_webhook_tenant_active ON workflow_webhooks (tenant_key, active);

CREATE TABLE workflow_events (
    id UUID PRIMARY KEY,
    tenant_key VARCHAR(60) NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    instance_id UUID,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_event_tenant FOREIGN KEY (tenant_key) REFERENCES workflow_tenants (tenant_key),
    CONSTRAINT fk_event_instance FOREIGN KEY (instance_id) REFERENCES workflow_instances (id)
);

CREATE INDEX idx_event_instance ON workflow_events (instance_id, occurred_at);

CREATE TABLE workflow_webhook_deliveries (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    webhook_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_status_code INTEGER,
    last_error TEXT,
    delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_delivery_event FOREIGN KEY (event_id) REFERENCES workflow_events (id),
    CONSTRAINT fk_delivery_webhook FOREIGN KEY (webhook_id) REFERENCES workflow_webhooks (id),
    CONSTRAINT uk_delivery_event_webhook UNIQUE (event_id, webhook_id),
    CONSTRAINT chk_delivery_status CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    CONSTRAINT chk_delivery_attempts CHECK (attempts >= 0)
);

-- The dispatcher polls only due, pending deliveries.
CREATE INDEX idx_delivery_due ON workflow_webhook_deliveries (next_attempt_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_delivery_webhook ON workflow_webhook_deliveries (webhook_id, created_at);

-- SLA tracking: when the instance entered its current status and when the
-- earliest outgoing action with slaDays falls due.
ALTER TABLE workflow_instances
    ADD COLUMN status_entered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
UPDATE workflow_instances SET status_entered_at = updated_at;
ALTER TABLE workflow_instances ADD COLUMN due_at TIMESTAMPTZ;
ALTER TABLE workflow_instances
    ADD COLUMN sla_breach_notified BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill due dates for existing instances from their current status.
UPDATE workflow_instances i
SET due_at = i.status_entered_at + MAKE_INTERVAL(days => sla.min_days)
FROM (
    SELECT workflow_definition_id, from_status, MIN(sla_days) AS min_days
    FROM workflow_stage_configs
    WHERE enabled AND sla_days IS NOT NULL
    GROUP BY workflow_definition_id, from_status
) sla
WHERE sla.workflow_definition_id = i.workflow_definition_id
  AND sla.from_status = i.current_status;

CREATE INDEX idx_instance_due ON workflow_instances (tenant_key, due_at)
    WHERE due_at IS NOT NULL;
