# Generic engine migration (roadmap phase 1)

This change removes Research Permit assumptions from the code so the engine can
serve any project. Behaviour is otherwise unchanged: it still validates and
audits transitions; the calling system still persists the subject's status.

## What changed

| Before | After |
|---|---|
| Roles hard-coded in two services | `workflow_roles` registry, managed via `/api/roles`. `SYSTEM` is built in. |
| Application types hard-coded in `WorkflowApplicationTypes` | `workflow_subject_types` registry, managed via `/api/subject-types` |
| "Application type" | "Subject type" (`subjectTypes`, `?subjectType=`) |
| Scope `ALL_APPLICATIONS` | Scope `ALL_TYPES` |
| Reachability always checked from `DRAFT` | Each workflow declares `initialStatus` (required on create) |
| Trigger `PAYMENT` | Trigger `EXTERNAL_EVENT` (payments, signatures, third-party callbacks) |
| `applicationId` (UUID) | `subjectId` (any string up to 150 chars: `[A-Za-z0-9][A-Za-z0-9._:-]*`) |
| No way to edit a draft's settings | `PUT /api/workflows/{id}` (drafts only) |
| `research-workflow-service`, `research_workflow_db` | `workflow-engine`, `workflow_engine_db` |

## Database migration

`V3__generic_engine_registry_and_subjects.sql` runs automatically through Flyway.
It keeps all existing rows:

- Roles and subject types already used by existing workflows are copied into
  the new registries, so published workflows remain valid.
- Existing workflows get `initial_status = 'DRAFT'`.
- `PAYMENT` stages and audit rows become `EXTERNAL_EVENT`.
- Existing UUID application IDs are kept as text in `subject_id`.

Back up the database first (see `PHASE0_BACKUP_AND_ROLLBACK.md`).

Database names changed only in defaults. Either set `WORKFLOW_DB_URL` to keep
using an existing database, or rename it:

```sql
ALTER DATABASE research_workflow_db RENAME TO workflow_engine_db;
ALTER DATABASE research_workflow_test_db RENAME TO workflow_engine_test_db;
```

## Breaking API changes for callers

- `POST /api/workflows`: `applicableApplicationTypes` → `subjectTypes`; new required `initialStatus`.
- `GET /api/workflows/resolve?applicationType=` → `?subjectType=`.
- `POST /api/transitions/execute`: `applicationId` → `subjectId`; `PAYMENT` → `EXTERNAL_EVENT`.
- Responses: `applicableApplicationTypes` → `subjectTypes`; `applicationId` → `subjectId`;
  coverage report fields renamed to `allSubjectTypesCovered` / `subjectTypes[].subjectType`.

The HMAC canonical format is unchanged (`workflow-actor-v1`); `subjectId` takes the
position `applicationId` had, so a UUID subject ID produces the same signature as before.

## Setting up a new project

1. Register roles: `POST /api/roles` `{"key":"REVIEWER","name":"Reviewer"}`
2. Register subject types: `POST /api/subject-types` `{"key":"PURCHASE_ORDER","name":"Purchase order"}`
3. Create a workflow with its `initialStatus`, add stages, check `/validation`, then `/publish`.

`scripts/examples/research-permit-registry.sql` registers the Research Permit
project's full role and type list.
