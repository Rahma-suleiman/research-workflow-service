# Research Workflow Service

Spring Boot workflow-definition and transition-validation service for the Research Permit System.

## Current scope

This package contains Phase 1 hardening and Phase 2 workflow validation diagnostics. It is not yet integrated with the Research Permit backend or frontend and is not certified for production deployment.

## Requirements

- Java 21
- PostgreSQL
- Maven (or the included Maven Wrapper, if its configured distribution can be downloaded)
- Database `research_workflow_db` and a database user with the required schema privileges

## Run locally

Configure the environment variables documented in `src/main/resources/application.properties`, then run:

```bash
./mvnw spring-boot:run
```

If the Maven Wrapper is not executable after extracting the archive:

```bash
chmod +x mvnw
./mvnw spring-boot:run
```

The service listens on port `8081`. Swagger UI is at `http://localhost:8081/swagger-ui/index.html`.

## Phase 2 diagnostic endpoints

- `GET /api/workflows/{workflowId}/validation` — read-only configuration/graph validation report.
- `GET /api/workflows/coverage` — coverage report for all supported application types.
- `GET /api/workflows/resolve?applicationType=FOREIGN_RESEARCHER` — resolves one active published workflow for a new application.
- `GET /api/workflows/{workflowId}/versions/{version}` — retrieves a pinned non-draft version.

Administrative endpoints require the configured administrator account. Resolution endpoints require the workflow service account. Transition validation also requires the trusted actor signature described in `docs/ACTOR_SIGNATURE.md`.

## Database verification

Run the read-only SQL diagnostics in `scripts/verify-workflow.sql`:

```bash
psql -d research_workflow_db -f scripts/verify-workflow.sql
```

See `docs/PHASE0_BACKUP_AND_ROLLBACK.md` and `docs/PHASE2_WORKFLOW_VALIDATION.md` before making changes.

## Tests

```bash
./mvnw clean verify
```

A successful build/test run has not yet been confirmed in the packaging environment because Maven could not be downloaded there. Run this command in the development environment and fix any reported failures before integration.

## Scope exclusions

This version does not include the Research Permit backend client, frontend changes, deployment automation, or production release sign-off. Those are later phases.


## Phase 3 hardening additions

See `docs/PHASE3_DATA_SECURITY_HARDENING.md` for the latest security and transition hardening changes and `docs/PRODUCTION_READINESS_GATE.md` for evidence required before a production release. Use `.env.example` as a variable-name reference only; replace all example values and never commit real secrets.

Production profile: set `SPRING_PROFILES_ACTIVE=prod`, inject strong secrets through a secret manager/protected environment, and verify the application starts with the real deployment configuration. OpenAPI/Swagger is disabled by default in production.

The transition endpoint validates and audits a transition decision; it does not persist status to the Research Permit database. Backend integration and end-to-end status persistence remain outside this package.
