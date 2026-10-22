# Production readiness gate — standalone workflow service

Do not mark the service production-ready until every applicable gate below has evidence attached to the release record.

## Build and tests

- [ ] `./mvnw clean verify` passes on the release commit.
- [ ] Tests run against PostgreSQL, including Flyway migration from a copy of the current schema.
- [ ] Concurrency tests cover simultaneous publication and duplicate transition requests.
- [ ] Coverage includes authorization failures, invalid transition, missing comments, signature mismatch, idempotency mismatch, and archived/pinned workflow behavior.

## Workflow data

- [ ] Backup and restore have been tested, not only documented.
- [ ] Every application type resolves to exactly one intended active published workflow.
- [ ] Workflow graph is validated; terminal statuses are explicitly reviewed.
- [ ] The active version and all existing workflow IDs/versions are recorded before any publication or replacement.
- [ ] No workflow is published merely to silence a coverage warning.

## Security

- [ ] `prod` profile is active and strong credentials are injected from a secret manager.
- [ ] TLS is enforced between callers and the service (including any reverse proxy hop where required).
- [ ] Service and administrator credentials are distinct and rotated.
- [ ] Actor-signing secret is random, at least 32 characters, protected, and rotation is documented.
- [ ] Only the intended service identity can call transition execution; only administrators can manage workflow definitions.
- [ ] Swagger/OpenAPI is disabled or reachable only through an approved internal access path.
- [ ] Dependency/container scans and independent security review have no unresolved critical/high findings.

## Operations

- [ ] Health probes work in the target runtime and do not disclose sensitive details.
- [ ] Logs are structured, retained appropriately, and exclude credentials, signatures, and sensitive application data.
- [ ] Alerts exist for repeated authorization failures, database errors, and service unavailability.
- [ ] Database backup, restore, migration rollback/forward-fix, and secret rotation have been rehearsed.
- [ ] Resource limits, connection-pool sizing, timeouts, and deployment rollback have been load-tested.

## Scope boundary

This gate covers only the standalone workflow service. It does not certify Research Permit backend integration, frontend behavior, or end-to-end application-status persistence. Those need separate integration and acceptance gates.
