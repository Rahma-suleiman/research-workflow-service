# Phase 2 — Verify the saved workflow

This phase checks the workflow service's saved configuration before connecting it to the Research Permit backend.

## New read-only endpoints

Use an authenticated administrator account:

- `GET /api/workflows/{workflowId}/validation` — validates the workflow graph and configuration, including drafts. It does not publish or modify anything.
- `GET /api/workflows/coverage` — checks all supported application types against active published workflows.

Example local URLs:

- `http://localhost:8081/api/workflows/f3ccca23-b655-4e65-a1ed-ea1d1b8c8a4e/validation`
- `http://localhost:8081/api/workflows/coverage`

Swagger: `http://localhost:8081/swagger-ui/index.html`

The validation report checks scope, supported application types, workflow lifecycle flags, enabled transitions, status/action syntax, duplicate stage order, duplicate source/action pairs, roles, trigger-role combinations, external approval settings, and reachability from `DRAFT`.

A `publishable: true` result means these workflow-service checks passed. It does not prove the separate Research Permit backend uses the same status/action keys. That contract still must be checked before integration.

The coverage endpoint returns one result per supported type:

- `RESOLVED`: exactly one active published workflow matches.
- `MISSING`: no active published workflow matches.
- `AMBIGUOUS`: multiple active published workflows match.

A newly created draft is expected to show `MISSING` in coverage until a suitable workflow has been published. Do not publish only to make this report green; publish only after the workflow graph and official process have been reviewed.

## Database verification

Run the read-only SQL diagnostics from the project root:

```bash
psql -d research_workflow_db -f scripts/verify-workflow.sql
```

Edit the `workflow_id` variable at the top of the script if checking another workflow. The script reports the definition, transition counts, all transitions, application types, active published coverage, and duplicate stage checks.

Expected values for the user's current draft (before changes to it):

- Workflow ID: `f3ccca23-b655-4e65-a1ed-ea1d1b8c8a4e`
- Workflow version: `2`
- Status: `DRAFT`
- Transition count: `21`
- Enabled transitions: `21`
- Assigned application types: all four supported application types

Because this workflow is a draft, the active published coverage report may correctly show `MISSING` for all four types if no other workflow is published.

## Safety notes

- Back up `research_workflow_db` before applying migrations or modifying records.
- Do not edit an already-applied Flyway migration. Use a new migration for future schema changes.
- These diagnostics are read-only.
- Do not test against production data.
- The validation report is a pre-publication aid, not a substitute for reviewing the official workflow or testing the integrated application.
