# Workflow Engine

A reusable Spring Boot service for defining versioned approval workflows and
validating status transitions for any project. Each project registers its own
roles and subject types (an application type, an order type, a document type,
...) and designs workflows as a graph of statuses and actions.

The engine validates and audits each transition. The calling system stores the
subject's current status and applies the returned `toStatus`.

## Concepts

- **Subject type**: the kind of record a workflow governs. Registered under `/api/subject-types`.
- **Role**: a role your users hold. Registered under `/api/roles`. `SYSTEM` is built in and is used for `SYSTEM` and `EXTERNAL_EVENT` triggers.
- **Workflow definition**: versioned (`DRAFT` → `PUBLISHED` → `ARCHIVED`), with an `initialStatus` and a scope (`ALL_TYPES` or `SELECTED_TYPES`).
- **Stage**: one allowed transition `fromStatus` + `actionKey` → `toStatus`, with assigned roles, a trigger type (`HUMAN`, `SYSTEM` or `EXTERNAL_EVENT`), optional SLA, and a comments requirement.
- **Subject**: one record in your system, identified by `subjectId` (any string ID).

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

Administrator account (`ADMINISTRATOR`):

- `GET|POST /api/roles`, `PUT /api/roles/{key}` — role registry.
- `GET|POST /api/subject-types`, `PUT /api/subject-types/{key}` — subject-type registry.
- `POST /api/workflows`, `PUT /api/workflows/{id}` — create a draft, or edit a draft's settings.
- `POST|PUT|DELETE /api/workflows/{id}/stages[/{stageId}]` — edit the transitions of a draft.
- `POST /api/workflows/{id}/versions` — copy a published or archived workflow into a new draft.
- `POST /api/workflows/{id}/publish` — validate, publish, and archive the previous version of the same key.
- `GET /api/workflows/{id}/validation` — read-only graph and configuration report.
- `GET /api/workflows/coverage` — whether every active subject type has exactly one active workflow.

Service account (`WORKFLOW_SERVICE`, used by the calling backend):

- `GET /api/workflows/resolve?subjectType=PURCHASE_ORDER` — the active workflow for a new subject; store its id and version on the subject.
- `GET /api/workflows/{id}/versions/{version}` — the pinned version for an existing subject.
- `POST /api/transitions/execute` — validate and audit one transition. Also requires the `X-Actor-Signature` header described in `docs/ACTOR_SIGNATURE.md`.

## Onboarding a new project

1. Register your roles and subject types (via the API, or a SQL seed like `scripts/examples/research-permit-registry.sql`).
2. Create a workflow with its `initialStatus` and subject types, add stages, check `/validation`, then `/publish`.
3. In your backend, call `/resolve` when a subject is created and pin the workflow id and version.
4. For each user action, send a signed `/api/transitions/execute` request with a unique idempotency key. Apply the returned `toStatus` with a compare-and-set update (`... WHERE status = :fromStatus`).

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
2. Tenants and client apps: per-project credentials, signing secrets, and data isolation.
3. Workflow instances: the engine owns each subject's current status and applies transitions atomically.
4. Integration: webhooks/outbox, SLA tracking, external-approval callbacks.
5. Packaging: Docker image and client SDKs.
