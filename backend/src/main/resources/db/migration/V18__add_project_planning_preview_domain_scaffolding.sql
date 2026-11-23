ALTER TABLE project_generation_preparations
    ADD COLUMN IF NOT EXISTS contract_version VARCHAR(20) NOT NULL DEFAULT '1.0',
    ADD COLUMN IF NOT EXISTS snapshot_json JSONB;

CREATE TABLE IF NOT EXISTS project_planning_previews (
    id UUID PRIMARY KEY,
    preparation_id UUID NOT NULL REFERENCES project_generation_preparations(id) ON DELETE CASCADE,
    selected_starter_key VARCHAR(120),
    planning_mode VARCHAR(50) NOT NULL,
    preview_status VARCHAR(50) NOT NULL,
    quality_summary JSONB,
    repair_status JSONB,
    preview_contract_version VARCHAR(20) NOT NULL,
    blueprint_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_project_planning_previews_preparation_id
    ON project_planning_previews (preparation_id, created_at DESC);
