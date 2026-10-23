# Testing the workflow engine

This guide tests the engine on its own, using generic example projects (purchase
orders, document review). No Research Permit data is needed: start from an empty
test database.

There are three levels of testing:

| Level | What it proves | Time |
|---|---|---|
| 1. Automated tests (`./mvnw clean verify`) | Code-level rules, concurrency and migrations against PostgreSQL | ~1 min |
| 2. End-to-end scenario (`scripts/e2e/smoke_test.py`) | The running application, over real HTTP, with real webhooks | ~30 s |
| 3. Manual testing (Swagger UI or curl) | Your own scenarios and exploratory testing | as needed |

## Requirements

- Java 21, PostgreSQL running locally, Python 3 (standard library only), `openssl`.

## 1. Automated tests

The test profile uses its own database, `workflow_engine_test_db`:

```bash
createdb workflow_engine_test_db
export WORKFLOW_DB_PASSWORD=...   # password of workflow_service_user
./mvnw clean verify               # expect: Tests run: 86, Failures: 0, Errors: 0
```

To use a different database user, override the connection:

```bash
./mvnw clean verify \
  -Dspring.datasource.url=jdbc:postgresql://localhost:5432/workflow_engine_test_db \
  -Dspring.datasource.username=$USER -Dspring.datasource.password=
```

The duplicate-key warning in the log comes from the concurrency test and is expected.

## 2. End-to-end scenario

Terminal 1 starts the engine with throwaway local credentials, on a database it
creates (`workflow_engine_e2e`):

```bash
scripts/e2e/start-engine.sh
```

Terminal 2 runs the scenario:

```bash
python3 scripts/e2e/smoke_test.py
```

Expected ending: `76 passed, 0 failed`. Each run onboards a new tenant, so you can
run it as often as you like on the same database.

What it checks, in order:

| Section | Checks |
|---|---|
| Platform | Create a tenant; issue ADMIN and SERVICE clients; secrets never listed |
| Registries | Register roles and subject types; SYSTEM reserved; duplicates rejected |
| Design | Draft, edit, add stages; bad roles and triggers rejected; validation catches unreachable stages; publish; published workflows are read-only; coverage |
| Instances | Start (idempotent), find by subject, actions per role; 401 for unsigned or wrong secret, 403 for wrong role, 422 for wrong status or missing comments, 409 for stale status or reused key; replayed retries; full NEW → SUBMITTED → APPROVED → PAID path; history |
| Concurrency | Two approvers act at the same moment: exactly one wins |
| Versioning | v2 published, v1 archived; in-flight instances stay on v1; new ones use v2 |
| Validate-only mode | Resolve, plus a signed `/api/transitions/execute` |
| Isolation | Other tenants get 404; each role is limited to its own endpoints |
| Webhooks and SLA | Started, transitioned, external-approval and SLA-breach events arrive signed; delivery log |
| Credentials | Rotation invalidates the old password and secret; deactivation locks the client out |

To run the scenario against another engine, set the environment variables
`ENGINE_URL`, `ENGINE_ADMIN_USER` and `ENGINE_ADMIN_PASSWORD`. That engine must allow
webhooks to localhost (`WORKFLOW_WEBHOOKS_ALLOW_PRIVATE_NETWORK=true`) and should
check SLAs every few seconds (`WORKFLOW_SLA_POLL_INTERVAL_MS=2000`).

## 3. Manual testing

With `scripts/e2e/start-engine.sh` running, open
<http://localhost:8081/swagger-ui/index.html> and click **Authorize**.

| Account | Username | Password | Can do |
|---|---|---|---|
| Platform administrator, also DEFAULT tenant admin | `e2e_admin` | `e2e-admin-password-local-only` | `/api/platform/**`, plus registries, workflows and webhooks of DEFAULT |
| DEFAULT tenant service | `e2e_service` | `e2e-service-password-local-only` | Instances, resolve, transitions |

The DEFAULT service account signs with `e2e-signing-secret-local-only-0123456789`.
Clients you create under `/api/platform` get their own passwords and secrets, shown once.

### Example: document review over curl

```bash
A=e2e_admin:e2e-admin-password-local-only
S=e2e_service:e2e-service-password-local-only
U=http://localhost:8081
J='Content-Type: application/json'

curl -u $A -H "$J" -d '{"key":"REVIEWER","name":"Reviewer"}' $U/api/roles
curl -u $A -H "$J" -d '{"key":"DOCUMENT","name":"Document"}' $U/api/subject-types
curl -u $A -H "$J" -d '{"workflowKey":"doc-review","name":"Doc review","initialStatus":"DRAFT",
  "scope":"SELECTED_TYPES","subjectTypes":["DOCUMENT"]}' $U/api/workflows        # note the "id"
curl -u $A -H "$J" -d '{"name":"Review","fromStatus":"DRAFT","toStatus":"REVIEWED",
  "actionKey":"REVIEW","assignedRole":"REVIEWER","triggerType":"HUMAN","stageOrder":1,
  "requiresComments":false,"externalApproval":false,"enabled":true}' $U/api/workflows/<id>/stages
curl -u $A -X POST $U/api/workflows/<id>/publish

curl -u $S -H "$J" -d '{"subjectType":"DOCUMENT","subjectId":"DOC-1"}' $U/api/instances   # note the instance "id"
```

Actions must be signed. Save the exact body you will send, then compute the
signature with the helper:

```bash
echo '{"expectedStatus":"DRAFT","triggerType":"HUMAN","actorId":"rev-1",
  "actorRole":"REVIEWER","idempotencyKey":"doc1-review"}' > body.json
SIG=$(python3 scripts/e2e/sign.py action --secret e2e-signing-secret-local-only-0123456789 \
      --instance-id <instance id> --action REVIEW --body body.json)
curl -u $S -H "$J" -H "X-Actor-Signature: $SIG" -d @body.json \
     $U/api/instances/<instance id>/actions/REVIEW
```

For validate-only mode, use `sign.py transition --secret ... --body body.json`.

### Manual checklist

- [ ] Changing any signed field after signing (role, comments, key) gives 401.
- [ ] Sending the same action twice with the same `idempotencyKey` returns `"replayed": true`.
- [ ] `GET /api/instances/{id}?actorRole=...` lists only that role's actions.
- [ ] `GET /api/workflows/{id}/validation` explains why a broken draft cannot be published.
- [ ] A webhook to `http://127.0.0.1/...` is rejected when the engine runs without `WORKFLOW_WEBHOOKS_ALLOW_PRIVATE_NETWORK=true`.
- [ ] Starting with `SPRING_PROFILES_ACTIVE=prod` and a missing `WORKFLOW_SECRET_ENCRYPTION_KEY` refuses to start.
- [ ] Stopping the webhook receiver, performing actions, then restarting it: the deliveries arrive after retry, or show as `FAILED` and can be retried with `POST /api/webhooks/deliveries/{id}/retry`.

## Clean up

```bash
dropdb workflow_engine_e2e      # scenario database
rm -rf .e2e                     # local encryption key (secrets in that database become unreadable)
```
