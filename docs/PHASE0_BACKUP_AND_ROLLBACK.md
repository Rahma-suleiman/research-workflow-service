# Phase 0 — Protect the current project and database

Before applying any code or database changes:

1. Keep the original `research-workflow-service-phase1-hardened.zip` untouched.
2. Copy the project folder to a dated backup or commit the current source to version control.
3. Back up the workflow database:

   ```bash
   pg_dump --format=custom --file=research_workflow_db_before_phase2.dump research_workflow_db
   ```

4. Confirm the backup can be listed:

   ```bash
   pg_restore --list research_workflow_db_before_phase2.dump
   ```

5. Test schema changes and application code on a development copy, not on production data.

The Phase 2 validation feature adds application code and read-only diagnostic endpoints; it does not require a new database migration. The Phase 1 V2 migration is still part of this project and must be tested against a copy of your database before you use this version.

If restoring a database, stop the application first and restore only to the intended development database. Do not use a destructive restore against production without the approved recovery procedure.
