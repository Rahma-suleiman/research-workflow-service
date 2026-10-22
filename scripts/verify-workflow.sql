-- Read-only diagnostics for the Research Workflow Service.
-- Run with: psql -d research_workflow_db -f scripts/verify-workflow.sql
-- Change the workflow ID below when checking a different definition.

\set workflow_id 'f3ccca23-b655-4e65-a1ed-ea1d1b8c8a4e'

\echo '1. Workflow definition'
SELECT
    id,
    workflow_key,
    name,
    scope,
    status,
    workflow_version,
    active,
    published_at
FROM workflow_definitions
WHERE id = :'workflow_id'::uuid;

\echo '2. Transition counts'
SELECT
    COUNT(*) AS total_stages,
    COUNT(*) FILTER (WHERE enabled) AS enabled_stages,
    COUNT(*) FILTER (WHERE NOT enabled) AS disabled_stages
FROM workflow_stage_configs
WHERE workflow_definition_id = :'workflow_id'::uuid;

\echo '3. All transition configurations'
SELECT
    stage_order,
    name,
    from_status,
    to_status,
    action_key,
    assigned_role,
    trigger_type,
    enabled
FROM workflow_stage_configs
WHERE workflow_definition_id = :'workflow_id'::uuid
ORDER BY stage_order, id;

\echo '4. Application types assigned to this workflow'
SELECT application_type
FROM workflow_definition_application_types
WHERE workflow_definition_id = :'workflow_id'::uuid
ORDER BY application_type;

\echo '5. Active published workflow coverage by application type'
WITH application_types(application_type) AS (
    VALUES
        ('FOREIGN_RESEARCHER'),
        ('LOCAL_RESEARCHER'),
        ('INSTITUTION_RESEARCH'),
        ('INSTITUTION_COMPANY')
)
SELECT
    t.application_type,
    COUNT(w.id) AS matching_active_published_workflows,
    CASE COUNT(w.id)
        WHEN 0 THEN 'MISSING'
        WHEN 1 THEN 'RESOLVED'
        ELSE 'AMBIGUOUS'
    END AS resolution_result,
    STRING_AGG(w.workflow_key || ' v' || w.workflow_version, ', ' ORDER BY w.workflow_key)
        AS matching_workflows
FROM application_types t
LEFT JOIN workflow_definitions w
    ON w.status = 'PUBLISHED'
   AND w.active = TRUE
   AND (
       w.scope = 'ALL_APPLICATIONS'
       OR (
           w.scope = 'SELECTED_TYPES'
           AND EXISTS (
               SELECT 1
               FROM workflow_definition_application_types at
               WHERE at.workflow_definition_id = w.id
                 AND at.application_type = t.application_type
           )
       )
   )
GROUP BY t.application_type
ORDER BY t.application_type;

\echo '6. Duplicate stage order check (should return no rows)'
SELECT stage_order, COUNT(*) AS duplicate_count
FROM workflow_stage_configs
WHERE workflow_definition_id = :'workflow_id'::uuid
GROUP BY stage_order
HAVING COUNT(*) > 1
ORDER BY stage_order;

\echo '7. Duplicate source/action check (should return no rows)'
SELECT from_status, action_key, COUNT(*) AS duplicate_count
FROM workflow_stage_configs
WHERE workflow_definition_id = :'workflow_id'::uuid
GROUP BY from_status, action_key
HAVING COUNT(*) > 1
ORDER BY from_status, action_key;
