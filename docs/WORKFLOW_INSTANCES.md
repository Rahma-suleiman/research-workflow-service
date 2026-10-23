# Workflow instances (roadmap phase 3)

The engine can now own each subject's current status. Your backend no longer
stores the status or protects it against concurrent updates. It asks the engine
to perform an action, and the engine applies it atomically.

| | Validate-only mode | Instance mode (recommended) |
|---|---|---|
| Endpoint | `POST /api/transitions/execute` | `/api/instances/**` |
| Who stores the status | Your system | The engine |
| Source of the "from" status | The caller sends it | The engine's stored value |
| Concurrent conflicting actions | Your system must prevent them | Serialized by a row lock; only one can win |
| Pinning the workflow version | Your system stores id and version | Stored on the instance |

Both modes use the same rules (trigger type, assigned roles, required comments)
and the same audit log. Idempotency keys are unique per tenant across both.

## Endpoints

SERVICE client (writes and reads); the tenant's ADMIN client may also read:

- `POST /api/instances` `{"subjectType": "PURCHASE_ORDER", "subjectId": "PO-1001"}`
  - Starts the subject's active workflow at its `initialStatus` and returns `201`.
  - If the subject already has an instance, returns it with `200`, so the call is safe to retry.
- `GET /api/instances/{id}?actorRole=REVIEWER` and `GET /api/instances?subjectType=...&subjectId=...&actorRole=...`
  - Returns the current status, `terminal`, and `availableActions`.
  - With `actorRole`, only the actions that role may perform are listed. Use this to show the right buttons in your UI.
- `POST /api/instances/{id}/actions/{actionKey}` (needs the `X-Actor-Signature` header)

  ```json
  {
    "expectedStatus": "SUBMITTED",
    "triggerType": "HUMAN",
    "actorId": "user-42",
    "actorRole": "REVIEWER",
    "comments": "Looks good",
    "idempotencyKey": "po-1001-approve-7f3a"
  }
  ```

  - `expectedStatus` is optional. Send the status the user was looking at; if the instance has moved on since, the request gets `409` and nothing changes.
  - The response holds `fromStatus`, `toStatus`, `replayed`, and the updated instance.
- `GET /api/instances/{id}/history` returns the applied transitions, oldest first.

## Guarantees

- **One instance per subject:** unique on tenant, subject type and subject ID.
- **Pinned version:** an instance keeps the workflow version it started with, even after a newer version is published and the old one is archived.
- **Atomic:** the status change and its audit row commit together, or neither does.
- **Serialized:** actions on one instance take a row lock (`SELECT ... FOR NO KEY UPDATE`). If two users act at the same moment, one succeeds. The other is re-checked against the new status and normally gets `422` (the action no longer applies) or `409` (stale `expectedStatus`).
- **Idempotent:** retrying with the same idempotency key and the same request returns the original result with `replayed: true`. Reusing a key for a different request gets `409`.

## Signature

Instance actions use their own payload, `workflow-instance-action-v1`, described in
`docs/ACTOR_SIGNATURE.md`. A signature made for validate-only mode is not accepted here.

## Response codes

| Code | Meaning |
|---|---|
| 401 | Missing or invalid signature |
| 403 | The actor's role may not perform this action |
| 404 | The instance does not exist in your tenant |
| 409 | Stale `expectedStatus`, idempotency key reused for a different request, or a concurrent start/retry |
| 422 | The action is not available from the current status, the trigger type is wrong, or comments are required |
