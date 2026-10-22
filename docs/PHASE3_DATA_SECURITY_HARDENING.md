# Phase 3 — Data, transition, and security hardening

This phase strengthens the standalone workflow service without integrating it with the Research Permit API or frontend.

## Included in this package

- Transition request validation rejects malformed status/action/role identifiers and limits comments to 4,000 characters.
- Production profile keeps Hibernate schema validation enabled, hides error details, and disables Swagger/OpenAPI by default.
- HTTP security headers explicitly deny framing and enable content-type and cache-control protections.
- Health probe groups are enabled while health details remain hidden.
- Hikari connection-pool limits are configurable through environment variables.
- Tests cover HMAC signature binding to comments and idempotency keys, and rejection of idempotency-key reuse with a different trigger type.

## Transition semantics and limitations

`POST /api/transitions/execute` records that a configured transition was validated. It does **not** update the Research Permit application record. The permit backend remains the owner of application status and must perform its status update in its own transaction/integration design. Do not describe the transition log as proof that the permit database status changed.

The signature binds the workflow ID/version, application ID, source status, action, trigger, actor, role, comments, and idempotency key. The database unique constraint makes an idempotency key globally unique. Retrying the same signed request with the same key is safe; reusing the key for a different request is rejected. Keep the signing secret shared only between trusted services and rotate it through a coordinated secret-management process.

## Production profile

Activate with `SPRING_PROFILES_ACTIVE=prod`. Configure at least:

- `WORKFLOW_DB_URL`
- `WORKFLOW_DB_USERNAME`
- `WORKFLOW_DB_PASSWORD`
- `WORKFLOW_ADMIN_USERNAME`
- `WORKFLOW_ADMIN_PASSWORD`
- `WORKFLOW_SERVICE_USERNAME`
- `WORKFLOW_SERVICE_PASSWORD`
- `WORKFLOW_ACTOR_SIGNING_SECRET` (at least 32 characters; use a cryptographically random secret)

The application fails during startup when production credentials or signing secret are weak/default. Do not commit these values to source control, paste them into tickets, or put them in shell history. Prefer a secret manager or protected deployment environment.

Swagger/OpenAPI is disabled by default in the `prod` profile. Only set `WORKFLOW_API_DOCS_ENABLED=true` when it is protected by network access and authentication appropriate to your deployment.

## Operational requirements not proven by unit tests

- Terminate HTTPS at a trusted ingress/reverse proxy; do not expose HTTP Basic credentials over plain HTTP.
- Restrict database network access and use a least-privilege database account.
- Keep actuator endpoints private except health probes; do not expose metrics or environment/configuration endpoints publicly.
- Test backup restoration, Flyway migration from a production-like database copy, log retention, alerting, and secret rotation.
- Run dependency and container-image vulnerability scans and an independent security review.
- Run concurrency tests against PostgreSQL. Mock-based unit tests do not prove database lock behavior or deployment safety.
- Verify that exactly one intended active published workflow resolves for each application type before switching production traffic.

## Verification

```bash
chmod +x mvnw
./mvnw clean verify
```

Run this against the exact extracted package and keep the complete output. A successful local build is necessary but not sufficient for production approval.

Run the environment preflight before starting a production deployment:

```bash
./scripts/preflight-production.sh
```

It checks required variables and obvious weak defaults; it does not connect to PostgreSQL or prove TLS, permissions, secret entropy, or runtime health.
