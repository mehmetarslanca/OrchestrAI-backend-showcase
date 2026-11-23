ALTER TABLE project_planning_previews
    ADD COLUMN IF NOT EXISTS preview_cache_key VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_project_planning_previews_cache_key
    ON project_planning_previews (preparation_id, preview_cache_key, created_at DESC);
