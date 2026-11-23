-- V15: Create project_artifacts table and move heavy blueprint data

CREATE TABLE project_artifacts (
    project_id      UUID            PRIMARY KEY,
    final_tree      JSONB           NOT NULL,
    metadata        JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT fk_project_artifacts_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

-- Backfill existing data
INSERT INTO project_artifacts (project_id, final_tree, metadata)
SELECT id, blueprint, metadata
FROM projects
WHERE blueprint IS NOT NULL;

-- Remove heavy column from projects
ALTER TABLE projects DROP COLUMN blueprint;

COMMENT ON TABLE project_artifacts IS 'Final IDE-specific file tree and generation metadata separate from project summary';
