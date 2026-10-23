# Webhooks, SLA tracking and external approvals (roadmap phase 4)

These features apply to workflow instances (`docs/WORKFLOW_INSTANCES.md`).

## Events

| Event | Sent when | Key `data` fields |
|---|---|---|
| `instance.started` | An instance is created | `status`, `dueAt` |
| `instance.transitioned` | An action is applied | `fromStatus`, `toStatus`, `actionKey`, `actorId`, `actorRole`, `comments`, `terminal`, `dueAt`, `transitionLogId` |
| `instance.external_approval_requested` | The instance enters a status that has an `externalApproval` action | `status`, `actionKey`, `toStatus`, `externalSystemId` |
| `instance.sla_breached` | The instance passes its due date (sent once per status) | `status`, `statusEnteredAt`, `dueAt` |

Every event also includes `instanceId`, `subjectType`, `subjectId` and
`workflowVersion`. Every event except `instance.sla_breached` also includes `workflowKey`.

```json
{
  "id": "6a1f...",
  "type": "instance.transitioned",
  "tenantKey": "PROCUREMENT",
  "occurredAt": "2026-10-23T09:15:02.481Z",
  "data": { "instanceId": "...", "subjectType": "PURCHASE_ORDER", "subjectId": "PO-1001",
            "fromStatus": "SUBMITTED", "toStatus": "APPROVED", "...": "..." }
}
```

## Managing webhooks (tenant ADMINISTRATOR)

- `POST /api/webhooks` `{"url": "https://erp.example.com/hooks/workflow", "eventTypes": ["*"]}`
  - Returns `signingSecret` **once**.
  - `eventTypes` takes event names or `"*"` for all of them.
- `GET /api/webhooks` and `PUT /api/webhooks/{id}` `{"url", "eventTypes", "active"}`.
- `POST /api/webhooks/{id}/rotate-secret` issues a new signing secret, shown once.
- `GET /api/webhooks/{id}/deliveries?status=FAILED` shows the last 100 deliveries.
- `POST /api/webhooks/deliveries/{deliveryId}/retry` re-queues a delivery with a fresh retry budget.

## Delivery guarantees

- **Transactional outbox:** events are written in the same database transaction as the change. A committed change always produces its events, and a rejected or rolled-back action produces none.
- **At least once:** a background job sends each delivery and retries non-2xx answers and network errors with exponential backoff: 30s, 1m, 2m, … capped at 6h. After `WORKFLOW_WEBHOOKS_MAX_ATTEMPTS` (default 8) the delivery becomes `FAILED`, and you can retry it manually.
- **Duplicates are possible** after crashes or timeouts. De-duplicate by the event `id`.
- **Order** is not guaranteed across retries. Use `occurredAt` and `transitionLogId`.
- **Several nodes:** safe to run on every node, because work is claimed with `FOR UPDATE SKIP LOCKED`.
- **Redirects** are not followed.

## Verifying a delivery

Each request carries:

```
X-Workflow-Event: instance.transitioned
X-Workflow-Delivery: <delivery id>
X-Workflow-Timestamp: 1761210902
X-Workflow-Signature: v1=<hex HMAC-SHA256(secret, timestamp + "." + rawBody)>
```

Recompute the HMAC over the **raw** request body and compare in constant time.
Reject timestamps older than a few minutes to block replays.

```java
String expected = "v1=" + hex(hmacSha256(secret, timestamp + "." + rawBody));
boolean valid = MessageDigest.isEqual(
    expected.getBytes(UTF_8), signatureHeader.getBytes(UTF_8));
```

## Webhook URL policy (SSRF protection)

Webhook URLs are checked when saved and again before each delivery.

- **Private network:** by default URLs must not resolve to private, loopback, link-local, carrier-grade NAT or cloud-metadata addresses. Set `WORKFLOW_WEBHOOKS_ALLOW_PRIVATE_NETWORK=true` only for single-tenant internal deployments.
- **HTTPS:** the `prod` profile requires HTTPS (`WORKFLOW_WEBHOOKS_REQUIRE_HTTPS`).
- **Credentials:** URLs that contain credentials are rejected.
- **Known limit (DNS rebinding):** the address check and the connection resolve DNS separately. For strict environments, also restrict outbound traffic at the network level.

## SLA tracking

- **Due date:** when an instance enters a status, `dueAt` = entry time + the **smallest** `slaDays` among the enabled actions leaving that status. If none of those actions has `slaDays`, there is no due date.
- **Reset:** entering a new status resets `dueAt` and the breach notification.
- **Breach report:** a background job publishes `instance.sla_breached` once per breach.
- **Overdue list:** `GET /api/instances/overdue` (SERVICE or ADMINISTRATOR) lists the tenant's overdue instances.
- **Upgrade:** the V6 migration back-fills `dueAt` for instances that already exist.

## External approvals

A stage with `externalApproval: true` and an `externalSystemId` represents a decision
made by another system, such as an identity verifier or a payment provider.

1. When the instance enters the stage's source status, the engine emits `instance.external_approval_requested` with the `externalSystemId` and the `actionKey` to report back on.
2. Your integration forwards the request to that system.
3. When the answer arrives, your backend performs the action as usual, normally with `triggerType: EXTERNAL_EVENT` and `actorRole: SYSTEM`. Validation warns when an external-approval stage uses another trigger.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `WORKFLOW_BACKGROUND_JOBS_ENABLED` | `true` | Run webhook delivery and SLA checks on this node |
| `WORKFLOW_WEBHOOKS_POLL_INTERVAL_MS` | `5000` | Delay between delivery batches |
| `WORKFLOW_WEBHOOKS_MAX_ATTEMPTS` | `8` | Attempts before `FAILED` |
| `WORKFLOW_WEBHOOKS_TIMEOUT_SECONDS` | `10` | Per-request timeout |
| `WORKFLOW_WEBHOOKS_ALLOW_PRIVATE_NETWORK` | `false` | Allow private/loopback targets |
| `WORKFLOW_WEBHOOKS_REQUIRE_HTTPS` | `false` (`true` in `prod`) | Require HTTPS targets |
| `WORKFLOW_SLA_POLL_INTERVAL_MS` | `60000` | Delay between SLA checks |

Webhook signing secrets are encrypted with `WORKFLOW_SECRET_ENCRYPTION_KEY`, so that
key is required to create webhooks.
