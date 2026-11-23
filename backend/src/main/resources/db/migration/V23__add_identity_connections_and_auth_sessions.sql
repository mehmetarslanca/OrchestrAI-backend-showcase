ALTER TABLE users
    ALTER COLUMN github_id DROP NOT NULL;

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS uq_users_email;

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS chk_users_provider;

ALTER TABLE users
    ADD CONSTRAINT chk_users_provider CHECK (provider IN ('GITHUB', 'GOOGLE'));

CREATE TABLE user_identity_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    provider_login VARCHAR(255),
    email VARCHAR(255),
    avatar_url VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_identity_connections_provider CHECK (provider IN ('GITHUB', 'GOOGLE'))
);

CREATE UNIQUE INDEX uq_user_identity_connections_provider_user
    ON user_identity_connections (provider, provider_user_id);

CREATE UNIQUE INDEX uq_user_identity_connections_user_provider
    ON user_identity_connections (user_id, provider);

CREATE INDEX idx_user_identity_connections_user_id
    ON user_identity_connections (user_id);

INSERT INTO user_identity_connections (user_id, provider, provider_user_id, provider_login, email, avatar_url)
SELECT id, provider, github_id::text, username, email, avatar_url
FROM users
WHERE github_id IS NOT NULL
ON CONFLICT DO NOTHING;

CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_auth_sessions_token_hash
    ON auth_sessions (token_hash);

CREATE INDEX idx_auth_sessions_user_id
    ON auth_sessions (user_id);

CREATE INDEX idx_auth_sessions_expires_at
    ON auth_sessions (expires_at);
