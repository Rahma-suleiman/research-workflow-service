# Tenants and client apps (roadmap phase 2)

One deployment can serve several projects. Each project is a **tenant** with
isolated data and its own credentials.

## Accounts

| Account | Where it is defined | Role | Tenant |
|---|---|---|---|
| Platform administrator | `WORKFLOW_ADMIN_*` environment variables | `PLATFORM_ADMIN` + `ADMINISTRATOR` | `DEFAULT` |
| Default service | `WORKFLOW_SERVICE_*` environment variables | `WORKFLOW_SERVICE` | `DEFAULT` |
| Tenant ADMIN client | `workflow_client_apps` (created via API) | `ADMINISTRATOR` | its tenant |
| Tenant SERVICE client | `workflow_client_apps` (created via API) | `WORKFLOW_SERVICE` | its tenant |

A single-project deployment needs nothing new: the environment accounts keep
working against the `DEFAULT` tenant, which owns all data created before V4.

The tenant is always taken from the authenticated account, never from the
request. A workflow belonging to another tenant is reported as not found.

## What is isolated per tenant

- Workflow definitions. Keys and versions are unique per tenant, and publishing
  only checks for overlaps within the tenant.
- Role and subject-type registries. Two tenants may both register `APPLICANT`.
- Transition logs and idempotency keys. Each log row also records the `client_id`
  that submitted it.
- HMAC signing secrets. Each SERVICE client signs with its own secret.

## Onboarding a project

As the platform administrator:

```http
POST /api/platform/tenants
{"key": "PROCUREMENT", "name": "Procurement system"}

POST /api/platform/tenants/PROCUREMENT/clients
{"clientId": "procurement-admin", "kind": "ADMIN"}

POST /api/platform/tenants/PROCUREMENT/clients
{"clientId": "procurement-backend", "kind": "SERVICE"}
```

Each client response contains a generated `password`. SERVICE clients also get a
`signingSecret`. **These are shown only once.** Give them to the project through a
secure channel and store them in its secret manager.

The project then uses its ADMIN client to register roles and subject types and to
design workflows. Its backend uses the SERVICE client for `/resolve`, the pinned
version endpoint, and signed `/api/transitions/execute` calls.

## Operations

- `GET /api/platform/tenants`, `PUT /api/platform/tenants/{key}` (`{"name", "active"}`):
  deactivating a tenant blocks all of its client apps. `DEFAULT` cannot be deactivated.
- `GET /api/platform/tenants/{key}/clients`: lists clients. No secrets are shown.
- `PUT /api/platform/clients/{clientId}` (`{"active": false}`): blocks one client.
- `POST /api/platform/clients/{clientId}/rotate-credentials`: issues a new password
  (and, for SERVICE clients, a new signing secret). The old values stop working
  immediately.

## Secret storage

- Client passwords are stored as BCrypt hashes.
- Signing secrets must be readable to verify HMAC signatures, so they are encrypted
  with AES-256-GCM. The key is `WORKFLOW_SECRET_ENCRYPTION_KEY` (`openssl rand -base64 32`),
  and the client ID is bound as associated data.
- The key is required in production. Without it, SERVICE clients cannot be created
  or verified (HTTP 503). ADMIN clients and the environment accounts still work.
- If the key is lost or replaced, rotate the credentials of every SERVICE client.

## Database migration

`V4__tenants_and_client_apps.sql` adds `workflow_tenants` and
`workflow_client_apps`, adds `tenant_key` to definitions, registries and
transition logs, and assigns every existing row to `DEFAULT`. It was tested by
upgrading a V3 database containing a published workflow and audit rows.
