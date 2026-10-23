-- Read-only diagnostics for the workflow engine.
-- Run with: psql -d workflow_engine_db -f scripts/verify-workflow.sql
-- Change the workflow ID below when checking a different definition.

\set workflow_id 'f3ccca23-b655-4e65-a1ed-ea1d1b8c8a4e'

\echo '1. Workflow definition'
SELECT
    id,
    tenant_key,
    workflow_key,
    name,
    scope,
    initial_status,
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

\echo '4. Subject types assigned to this workflow'
SELECT subject_type
FROM workflow_definition_subject_types
WHERE workflow_definition_id = :'workflow_id'::uuid
ORDER BY subject_type;

\echo '5. Active published workflow coverage by registered subject type, per tenant'
SELECT
    t.tenant_key,
    t.type_key AS subject_type,
    COUNT(w.id) AS matching_active_published_workflows,
    CASE COUNT(w.id)
        WHEN 0 THEN 'MISSING'
        WHEN 1 THEN 'RESOLVED'
        ELSE 'AMBIGUOUS'
    END AS resolution_result,
    STRING_AGG(w.workflow_key || ' v' || w.workflow_version, ', ' ORDER BY w.workflow_key)
        AS matching_workflows
FROM workflow_subject_types t
LEFT JOIN workflow_definitions w
    ON w.tenant_key = t.tenant_key
   AND w.status = 'PUBLISHED'
   AND w.active = TRUE
   AND (
       w.scope = 'ALL_TYPES'
       OR (
           w.scope = 'SELECTED_TYPES'
           AND EXISTS (
               SELECT 1
               FROM workflow_definition_subject_types st
               WHERE st.workflow_definition_id = w.id
                 AND st.subject_type = t.type_key
           )
       )
   )
WHERE t.active = TRUE
GROUP BY t.tenant_key, t.type_key
ORDER BY t.tenant_key, t.type_key;

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
