-- =============================================
-- V4: Enum case constraint fixes
-- Aligns PostgreSQL CHECK constraints with JPA @Enumerated(EnumType.STRING)
-- which persists Java enum constant names in UPPERCASE.
--
-- Fixes:
--   BUG-06: users.provider 'github' → 'GITHUB'
--   BUG-07: projects.ide_target 'cursor' → 'CURSOR' etc.
-- =============================================

-- BUG-06: Fix provider column
UPDATE users SET provider = UPPER(provider) WHERE provider != UPPER(provider);
ALTER TABLE users DROP CONSTRAINT chk_users_provider;
ALTER TABLE users ADD CONSTRAINT chk_users_provider CHECK (provider IN ('GITHUB'));
ALTER TABLE users ALTER COLUMN provider SET DEFAULT 'GITHUB';

-- BUG-07: Fix ide_target column
UPDATE projects SET ide_target = UPPER(ide_target) WHERE ide_target != UPPER(ide_target);
ALTER TABLE projects DROP CONSTRAINT chk_projects_ide_target;
ALTER TABLE projects ADD CONSTRAINT chk_projects_ide_target CHECK (ide_target IN ('CURSOR', 'ANTIGRAVITY', 'WINDSURF', 'GENERIC'));
