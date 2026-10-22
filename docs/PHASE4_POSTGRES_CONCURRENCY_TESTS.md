# Phase 4 — PostgreSQL concurrency and security integration tests

## Safety

The automated integration tests use `src/test/resources/application-test.properties`, which pins the datasource URL to `jdbc:postgresql://localhost:5432/research_workflow_test_db`. Do not change it to `research_workflow_db`.

The tests create a uniquely named workflow and transition-log idempotency key. Cleanup deletes only the transition log with this test's unique key prefix and the workflow created by that test. Do not run these tests against production data.

## Run the complete suite

From the project root, enter the database password interactively so it is not saved in the command itself:

```bash
read -rsp "PostgreSQL password for workflow_service_user: " WORKFLOW_TEST_DB_PASSWORD
echo
export WORKFLOW_TEST_DB_PASSWORD
./mvnw clean verify
result=$?
unset WORKFLOW_TEST_DB_PASSWORD
exit $result
```

Use the password for `workflow_service_user`. If the database login is not configured for password authentication, resolve the local PostgreSQL authentication setup before running tests. Do not put passwords in this file or commit them.

## What the integration tests cover

- The Spring application context starts against the separate test database.
- Two simultaneous requests with the same idempotency key create at most one transition log.
- Two concurrent updates to the same workflow entity do not both silently overwrite one another; one stale update should be rejected by JPA optimistic locking.
- Workflow administration returns 401 without authentication, 403 for the workflow-service role, and 200 for the administrator test account.

Existing unit tests also run as part of `clean verify`.

## Interpreting results

A successful `BUILD SUCCESS` means the tests passed in that environment; it does not certify production readiness. If a concurrency test fails, preserve the full failure output. Do not repeatedly rerun a failing concurrency test to hide a race. Investigate the failing assertion or database exception first.
