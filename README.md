# Workflow Engine

A reusable Spring Boot service for defining versioned approval workflows and
validating status transitions for any project. Each project registers its own
roles and subject types (an application type, an order type, a document type,
...) and designs workflows as a graph of statuses and actions.

The engine can run in two modes:

- **Instance mode (recommended):** the engine stores each subject's current status and applies actions atomically. See `docs/WORKFLOW_INSTANCES.md`.
- **Validate-only mode:** the engine validates and audits a transition, and the calling system stores the status itself.

## Concepts

- **Tenant**: one project served by the engine, with isolated data and its own client app credentials. Existing single-project data lives in the built-in `DEFAULT` tenant. See `docs/MULTI_TENANCY.md`.
- **Subject type**: the kind of record a workflow governs. Registered under `/api/subject-types`.
- **Role**: a role your users hold. Registered under `/api/roles`. `SYSTEM` is built in and is used for `SYSTEM` and `EXTERNAL_EVENT` triggers.
- **Workflow definition**: versioned (`DRAFT` → `PUBLISHED` → `ARCHIVED`), with an `initialStatus` and a scope (`ALL_TYPES` or `SELECTED_TYPES`).
- **Stage**: one allowed transition `fromStatus` + `actionKey` → `toStatus`, with assigned roles, a trigger type (`HUMAN`, `SYSTEM` or `EXTERNAL_EVENT`), optional SLA, and a comments requirement.
- **Subject**: one record in your system, identified by `subjectId` (any string ID).
- **Instance**: a subject moving through its pinned workflow version, holding its current status.

## Requirements

- Java 21
- PostgreSQL
- Maven (or the included Maven Wrapper)
- Database `workflow_engine_db` (or set `WORKFLOW_DB_URL`) and a database user with the required schema privileges

## Run locally

Set the environment variables listed in `.env.example`, then run:

```bash
./mvnw spring-boot:run
```

If the Maven Wrapper is not executable after extracting the archive:

```bash
chmod +x mvnw
./mvnw spring-boot:run
```

The service listens on port `8081`. Swagger UI is at `http://localhost:8081/swagger-ui/index.html`.

## Endpoints

Every endpoint works on the caller's own tenant. The tenant comes from the credentials, never from the request.

Platform administrator (the environment admin account):

- `GET|POST /api/platform/tenants`, `PUT /api/platform/tenants/{key}` — onboard and (de)activate projects.
- `GET|POST /api/platform/tenants/{key}/clients` — issue ADMIN or SERVICE client credentials (shown once).
- `PUT /api/platform/clients/{clientId}`, `POST /api/platform/clients/{clientId}/rotate-credentials`.

Tenant administrator (`ADMINISTRATOR`: the environment admin for `DEFAULT`, or a tenant's ADMIN client):

- `GET|POST /api/roles`, `PUT /api/roles/{key}` — role registry.
- `GET|POST /api/subject-types`, `PUT /api/subject-types/{key}` — subject-type registry.
- `POST /api/workflows`, `PUT /api/workflows/{id}` — create a draft, or edit a draft's settings.
- `POST|PUT|DELETE /api/workflows/{id}/stages[/{stageId}]` — edit the transitions of a draft.
- `POST /api/workflows/{id}/versions` — copy a published or archived workflow into a new draft.
- `POST /api/workflows/{id}/publish` — validate, publish, and archive the previous version of the same key.
- `GET /api/workflows/{id}/validation` — read-only graph and configuration report.
- `GET /api/workflows/coverage` — whether every active subject type has exactly one active workflow.

Service account (`WORKFLOW_SERVICE`: the environment service account for `DEFAULT`, or a tenant's SERVICE client):

- `GET /api/workflows/resolve?subjectType=PURCHASE_ORDER` — the active workflow for a new subject; store its id and version on the subject.
- `GET /api/workflows/{id}/versions/{version}` — the pinned version for an existing subject.
- `POST /api/instances` — start a subject's workflow (repeat calls return the existing instance).
- `GET /api/instances/{id}`, `GET /api/instances?subjectType=&subjectId=` — current status and available actions (`?actorRole=` filters them). Also readable by the tenant administrator.
- `POST /api/instances/{id}/actions/{actionKey}` — apply a signed action to the stored status.
- `GET /api/instances/{id}/history` — applied transitions.
- `POST /api/transitions/execute` — validate-only mode: validate and audit one transition.

Action and transition requests also require the `X-Actor-Signature` header described in `docs/ACTOR_SIGNATURE.md`.

## Onboarding a new project

1. The platform administrator creates a tenant and its ADMIN and SERVICE clients (`docs/MULTI_TENANCY.md`). A single-project deployment can skip this and use the environment accounts in `DEFAULT`.
2. Register your roles and subject types (via the API, or a SQL seed like `scripts/examples/research-permit-registry.sql`).
3. Create a workflow with its `initialStatus` and subject types, add stages, check `/validation`, then `/publish`.
4. In your backend, call `POST /api/instances` when a subject is created, and store the returned instance id.
5. Show users the `availableActions` for their role. Send each action to `/api/instances/{id}/actions/{actionKey}`, signed with your SERVICE client's signing secret, with a unique idempotency key and the `expectedStatus` the user saw.

In validate-only mode, call `/resolve` instead and pin the workflow id and version yourself. For each action, send a signed `/api/transitions/execute` request with a unique idempotency key. Apply the returned `toStatus` with a compare-and-set update (`... WHERE status = :fromStatus`).

## Database verification

Run the read-only SQL diagnostics in `scripts/verify-workflow.sql`:

```bash
psql -d workflow_engine_db -f scripts/verify-workflow.sql
```

See `docs/PHASE0_BACKUP_AND_ROLLBACK.md` before applying migrations, and
`docs/GENERIC_ENGINE_MIGRATION.md` when upgrading an existing Research Permit installation.

## Tests

```bash
./mvnw clean verify
```

The integration tests use the separate `workflow_engine_test_db` database defined in `src/test/resources/application-test.properties`.

## Production

See `docs/PHASE3_DATA_SECURITY_HARDENING.md` for security and transition hardening, and
`docs/PRODUCTION_READINESS_GATE.md` for the evidence required before a production release.
Use `.env.example` only as a reference for variable names: replace every example value, and never commit real secrets.

Set `SPRING_PROFILES_ACTIVE=prod` and inject strong secrets through a secret manager or protected environment.
Then confirm the application starts with the real deployment configuration. OpenAPI/Swagger is disabled by default in production.

## Roadmap

1. Generic vocabulary: registries, subject types, configurable initial status (done).
2. Tenants and client apps: per-project credentials, signing secrets, and data isolation (done).
3. Workflow instances: the engine owns each subject's current status and applies transitions atomically (done).
4. Integration: webhooks/outbox, SLA tracking, external-approval callbacks.
5. Packaging: Docker image and client SDKs.
