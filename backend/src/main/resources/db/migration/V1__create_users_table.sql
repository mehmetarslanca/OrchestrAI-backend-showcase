-- =============================================
-- V1: Users tablosu
-- OrchestrAI'nin kimlik doğrulama temeli.
-- GitHub OAuth ile gelen kullanıcı bilgileri burada saklanır.
-- =============================================

CREATE TABLE users (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    github_id   BIGINT          NOT NULL,
    username    VARCHAR(100)    NOT NULL,
    email       VARCHAR(255),
    avatar_url  VARCHAR(500),
    provider    VARCHAR(20)     NOT NULL DEFAULT 'github',
    role        VARCHAR(20)     NOT NULL DEFAULT 'USER',
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_github_id UNIQUE (github_id),
    CONSTRAINT uq_users_email     UNIQUE (email),
    CONSTRAINT chk_users_provider CHECK (provider IN ('github')),
    CONSTRAINT chk_users_role     CHECK (role IN ('USER', 'ADMIN'))
);

CREATE INDEX idx_users_github_id ON users (github_id);
CREATE INDEX idx_users_email     ON users (email);

COMMENT ON TABLE users IS 'GitHub OAuth ile giriş yapan kullanıcılar';
COMMENT ON COLUMN users.github_id IS 'GitHub API tarafından sağlanan benzersiz kullanıcı ID';
COMMENT ON COLUMN users.provider IS 'OAuth sağlayıcısı - şimdilik sadece github';
COMMENT ON COLUMN users.role IS 'Kullanıcı rolü: USER (varsayılan) veya ADMIN';
