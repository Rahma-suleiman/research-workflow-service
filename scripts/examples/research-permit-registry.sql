-- Example project vocabulary: the Research Permit System.
-- The engine ships with empty registries; each project registers its own roles
-- and subject types (or uses POST /api/roles and POST /api/subject-types).
-- Safe to re-run. Run with:
--   psql -d workflow_engine_db -f scripts/examples/research-permit-registry.sql

INSERT INTO workflow_roles (role_key, name) VALUES
    ('APPLICANT', 'Applicant'),
    ('ADMIN', 'Admin'),
    ('ADMINISTRATOR', 'Administrator'),
    ('MANAGER', 'Manager'),
    ('RESEARCH_OFFICER', 'Research officer'),
    ('SVPO_OFFICER', 'SVPO officer'),
    ('OCGS_VERIFICATION', 'OCGS verification'),
    ('ETHICS_OFFICER', 'Ethics officer'),
    ('FINANCE_OFFICER', 'Finance officer'),
    ('OFFICER', 'Officer'),
    ('COMPLIANCE_OFFICER', 'Compliance officer'),
    ('INSTITUTION', 'Institution')
ON CONFLICT (role_key) DO NOTHING;

INSERT INTO workflow_subject_types (type_key, name) VALUES
    ('FOREIGN_RESEARCHER', 'Foreign researcher application'),
    ('LOCAL_RESEARCHER', 'Local researcher application'),
    ('INSTITUTION_RESEARCH', 'Institution research application'),
    ('INSTITUTION_COMPANY', 'Institution company application')
ON CONFLICT (type_key) DO NOTHING;
