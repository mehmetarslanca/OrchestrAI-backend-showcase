-- =============================================
-- V3: Projects tablosu
-- Kullanıcının LLM'e gönderdiği her blueprint isteğinin kaydı.
-- blueprint kolonu JSONB olarak saklanır — LLM çıktıları
-- için esnek, schema-less depolama sağlar.
-- =============================================

CREATE TABLE projects (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL,
    project_name    VARCHAR(200)    NOT NULL,
    ide_target      VARCHAR(30)     NOT NULL,
    original_prompt TEXT            NOT NULL,
    status          VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    blueprint       JSONB,
    metadata        JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT fk_projects_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_projects_ide_target CHECK (ide_target IN ('cursor', 'antigravity', 'windsurf', 'generic')),
    CONSTRAINT chk_projects_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_projects_user_id   ON projects (user_id);
CREATE INDEX idx_projects_status    ON projects (status);
CREATE INDEX idx_projects_created   ON projects (created_at DESC);

COMMENT ON TABLE projects IS 'Her blueprint generation isteğinin kaydı';
COMMENT ON COLUMN projects.ide_target IS 'Hedef IDE: cursor, antigravity, windsurf veya generic';
COMMENT ON COLUMN projects.blueprint IS 'LLM tarafından üretilen blueprint JSON çıktısı (JSONB)';
COMMENT ON COLUMN projects.metadata IS 'LLM analiz sonuçları, token kullanımı vb. ek bilgiler (JSONB)';
COMMENT ON COLUMN projects.status IS 'İstek durumu: PENDING → PROCESSING → COMPLETED/FAILED';
