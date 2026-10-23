-- Example project vocabulary: the Research Permit System.
-- The engine ships with empty registries; each project registers its own roles
-- and subject types (or uses POST /api/roles and POST /api/subject-types).
-- Rows go to the DEFAULT tenant; replace DEFAULT to seed another tenant.
-- Safe to re-run. Run with:
--   psql -d workflow_engine_db -f scripts/examples/research-permit-registry.sql

INSERT INTO workflow_roles (tenant_key, role_key, name) VALUES
    ('DEFAULT', 'APPLICANT', 'Applicant'),
    ('DEFAULT', 'ADMIN', 'Admin'),
    ('DEFAULT', 'ADMINISTRATOR', 'Administrator'),
    ('DEFAULT', 'MANAGER', 'Manager'),
    ('DEFAULT', 'RESEARCH_OFFICER', 'Research officer'),
    ('DEFAULT', 'SVPO_OFFICER', 'SVPO officer'),
    ('DEFAULT', 'OCGS_VERIFICATION', 'OCGS verification'),
    ('DEFAULT', 'ETHICS_OFFICER', 'Ethics officer'),
    ('DEFAULT', 'FINANCE_OFFICER', 'Finance officer'),
    ('DEFAULT', 'OFFICER', 'Officer'),
    ('DEFAULT', 'COMPLIANCE_OFFICER', 'Compliance officer'),
    ('DEFAULT', 'INSTITUTION', 'Institution')
ON CONFLICT (tenant_key, role_key) DO NOTHING;

INSERT INTO workflow_subject_types (tenant_key, type_key, name) VALUES
    ('DEFAULT', 'FOREIGN_RESEARCHER', 'Foreign researcher application'),
    ('DEFAULT', 'LOCAL_RESEARCHER', 'Local researcher application'),
    ('DEFAULT', 'INSTITUTION_RESEARCH', 'Institution research application'),
    ('DEFAULT', 'INSTITUTION_COMPANY', 'Institution company application')
ON CONFLICT (tenant_key, type_key) DO NOTHING;
