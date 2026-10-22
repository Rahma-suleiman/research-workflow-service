# Phase 1 hardening changes

## Changes in this version

- Publishing a different workflow key with overlapping scope now returns HTTP 409 instead of silently archiving the existing workflow.
- Publishing a new version can archive the prior active version only when the workflow key and scope/application types are unchanged. Archived versions remain retrievable for pinned applications.
- Publishing validates supported Research Permit application types, unique stage order, and that every enabled transition is reachable from `DRAFT`.
- Workflow definitions use optimistic locking (`entity_version`) to detect concurrent updates.
- Transition requests now require `X-Actor-Signature`, an HMAC-SHA256 signature from the trusted Research Permit backend. HTTP Basic alone does not prove the actor's role. See `docs/ACTOR_SIGNATURE.md`.
- The engine no longer grants an implicit administrator bypass for every human action. An administrator role must be explicitly configured on a stage if the policy permits it.
- Transition audit records store trigger type. Idempotency compares workflow/version, application, source status, action, trigger, actor ID, actor role, and comments. Database uniqueness/concurrency conflicts return HTTP 409.
- Missing resources, invalid transitions, authorization failures, conflicts, and malformed input now return more appropriate HTTP statuses without exposing database exception details.
- The production profile rejects known sample/short database, administrator, service, or actor-signing secrets.
- Added unit tests for publishing scope conflicts, unreachable transitions, actor signature validation, role authorization, draft rejection, and idempotent retries.

## Important migration note

V1 was not edited. Flyway will apply `V2__add_workflow_locking_and_transition_trigger.sql` to an existing database. First back up the database and test this migration on a copy. Do not delete Flyway history or edit V1 to force the migration.

## Verification limitation

The source and POM were inspected and the POM XML parses. A Maven compile/test run could not be completed in the packaging environment because `./mvnw` could not download the Maven distribution from Maven Central. Run `./mvnw clean verify` (or `mvn clean verify`) in your development environment and resolve any build/test failures before using this build. The existing Spring context test needs a reachable PostgreSQL database.

## Not yet complete

This is a Phase 1 hardening baseline, not a production certification. The Research Permit backend has not been connected. Its HTTP client must generate the exact actor signature, send the header, and coordinate the application-status update reliably. Full integration, load tests, deployment controls, monitoring, backups, recovery drills, and user acceptance testing remain future phases.


## Phase 2 additions in this package

- Added `WorkflowValidationService` and the read-only administrator endpoint `GET /api/workflows/{workflowId}/validation`.
- The validation report checks workflow scope and application types, lifecycle flags, stage order uniqueness, transition syntax, supported roles, trigger/role compatibility, external approval settings, and reachability from `DRAFT`.
- Publishing now calls the same validation service, so the diagnostic report and publication use the same graph checks.
- Added `GET /api/workflows/coverage` to report `RESOLVED`, `MISSING`, or `AMBIGUOUS` coverage for all four supported application types.
- Centralized the four supported application types in `WorkflowApplicationTypes`.
- Added read-only PostgreSQL diagnostics in `scripts/verify-workflow.sql`, Phase 0 backup instructions, and Phase 2 validation documentation.

## Phase 2 verification limitation

The validation service and tests were added to the source. A Maven test run could not be completed in this environment: no system Maven is installed and the Maven Wrapper could not download Maven from Maven Central. Run `./mvnw clean verify` in your development environment. Treat this package as requiring a successful local build and review before replacing your working copy.
