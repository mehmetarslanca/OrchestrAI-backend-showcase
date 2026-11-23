CREATE TABLE user_provider_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider_key VARCHAR(30) NOT NULL,
    encrypted_secret TEXT,
    secret_hint VARCHAR(64),
    key_version INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    last_validated_at TIMESTAMPTZ,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_provider_credentials_status CHECK (status IN ('PENDING', 'VALID', 'INVALID', 'REVOKED')),
    CONSTRAINT chk_user_provider_credentials_provider CHECK (provider_key IN ('google', 'openai', 'anthropic', 'moonshot', 'zai', 'xai'))
);

CREATE UNIQUE INDEX uq_user_provider_credentials_user_provider
    ON user_provider_credentials (user_id, provider_key);

CREATE INDEX idx_user_provider_credentials_user_status
    ON user_provider_credentials (user_id, status);
