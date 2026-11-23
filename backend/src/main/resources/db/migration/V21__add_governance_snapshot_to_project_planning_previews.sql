ALTER TABLE project_planning_previews
    ADD COLUMN IF NOT EXISTS governance_snapshot JSONB;
