CREATE TABLE user_ai_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    connection_type VARCHAR(50) NOT NULL,
    provider_key VARCHAR(50) NOT NULL,
    auth_method VARCHAR(20) NOT NULL,
    billing_mode VARCHAR(20) NOT NULL,
    connection_status VARCHAR(30) NOT NULL,
    encrypted_access_token TEXT,
    access_token_key_version INTEGER,
    encrypted_refresh_token TEXT,
    refresh_token_key_version INTEGER,
    encrypted_api_key TEXT,
    api_key_hint VARCHAR(64),
    api_key_key_version INTEGER,
    expires_at TIMESTAMPTZ,
    last_validated_at TIMESTAMPTZ,
    reauth_required_at TIMESTAMPTZ,
    provider_account_label VARCHAR(255),
    provider_account_subject VARCHAR(255),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_ai_connections_connection_type CHECK (
        connection_type IN (
            'OPENAI_BYOK',
            'GOOGLE_BYOK',
            'ANTHROPIC_BYOK',
            'OLLAMA_BYOK',
            'OPENAI_COMPATIBLE_BYOK',
            'MOONSHOT_BYOK',
            'ZAI_BYOK',
            'XAI_BYOK',
            'GITHUB_MODELS_USER_LINKED',
            'GITHUB_COPILOT_USER_LINKED',
            'OPENAI_APP_PAID',
            'OPENAI_CODEX_CHATGPT'
        )
    ),
    CONSTRAINT chk_user_ai_connections_auth_method CHECK (
        auth_method IN ('API_KEY', 'OAUTH', 'PLATFORM')
    ),
    CONSTRAINT chk_user_ai_connections_billing_mode CHECK (
        billing_mode IN ('PLATFORM_PAID', 'BYOK', 'USER_LINKED')
    ),
    CONSTRAINT chk_user_ai_connections_connection_status CHECK (
        connection_status IN (
            'PENDING',
            'VALID',
            'INVALID',
            'REVOKED',
            'EXPIRED',
            'REAUTH_REQUIRED',
            'ENTITLEMENT_MISSING',
            'DEGRADED'
        )
    )
);

CREATE UNIQUE INDEX uq_user_ai_connections_user_connection_type
    ON user_ai_connections (user_id, connection_type);

CREATE INDEX idx_user_ai_connections_user_status
    ON user_ai_connections (user_id, connection_status);

CREATE INDEX idx_user_ai_connections_user_provider
    ON user_ai_connections (user_id, provider_key);

WITH legacy_connections AS (
    SELECT
        user_provider_credentials.id AS legacy_credential_id,
        user_provider_credentials.user_id,
        CASE user_provider_credentials.provider_key
            WHEN 'openai' THEN 'OPENAI_BYOK'
            WHEN 'google' THEN 'GOOGLE_BYOK'
            WHEN 'anthropic' THEN 'ANTHROPIC_BYOK'
            WHEN 'ollama' THEN 'OLLAMA_BYOK'
            WHEN 'openai-compatible' THEN 'OPENAI_COMPATIBLE_BYOK'
            WHEN 'moonshot' THEN 'MOONSHOT_BYOK'
            WHEN 'zai' THEN 'ZAI_BYOK'
            WHEN 'xai' THEN 'XAI_BYOK'
            ELSE NULL
        END AS connection_type,
        user_provider_credentials.provider_key,
        user_provider_credentials.status,
        user_provider_credentials.encrypted_secret,
        user_provider_credentials.secret_hint,
        user_provider_credentials.key_version,
        user_provider_credentials.last_validated_at,
        user_provider_credentials.metadata,
        user_provider_credentials.created_at,
        user_provider_credentials.updated_at
    FROM user_provider_credentials
)
INSERT INTO user_ai_connections (
    user_id,
    connection_type,
    provider_key,
    auth_method,
    billing_mode,
    connection_status,
    encrypted_api_key,
    api_key_hint,
    api_key_key_version,
    last_validated_at,
    metadata,
    created_at,
    updated_at
)
SELECT
    legacy_connections.user_id,
    legacy_connections.connection_type,
    legacy_connections.provider_key,
    'API_KEY',
    'BYOK',
    legacy_connections.status,
    legacy_connections.encrypted_secret,
    legacy_connections.secret_hint,
    legacy_connections.key_version,
    legacy_connections.last_validated_at,
    COALESCE(legacy_connections.metadata, '{}'::jsonb)
        || jsonb_build_object('legacyProviderCredentialId', legacy_connections.legacy_credential_id),
    legacy_connections.created_at,
    legacy_connections.updated_at
FROM legacy_connections
WHERE legacy_connections.connection_type IS NOT NULL
ON CONFLICT (user_id, connection_type) DO UPDATE
SET provider_key = EXCLUDED.provider_key,
    auth_method = EXCLUDED.auth_method,
    billing_mode = EXCLUDED.billing_mode,
    connection_status = EXCLUDED.connection_status,
    encrypted_api_key = EXCLUDED.encrypted_api_key,
    api_key_hint = EXCLUDED.api_key_hint,
    api_key_key_version = EXCLUDED.api_key_key_version,
    last_validated_at = EXCLUDED.last_validated_at,
    metadata = COALESCE(user_ai_connections.metadata, '{}'::jsonb) || EXCLUDED.metadata,
    created_at = LEAST(user_ai_connections.created_at, EXCLUDED.created_at),
    updated_at = GREATEST(user_ai_connections.updated_at, EXCLUDED.updated_at);

ALTER TABLE user_ai_preferences
    ADD COLUMN default_model_key VARCHAR(120),
    ADD COLUMN default_connection_id UUID REFERENCES user_ai_connections(id) ON DELETE SET NULL;

UPDATE user_ai_preferences
SET default_model_key = default_heavy_model_key
WHERE default_heavy_model_key IS NOT NULL
  AND default_model_key IS NULL;

UPDATE user_ai_preferences AS preferences
SET default_connection_id = connections.id
FROM user_ai_connections AS connections
WHERE preferences.default_connection_id IS NULL
  AND preferences.default_auth_source = 'BYOK'
  AND preferences.default_heavy_model_key IS NOT NULL
  AND preferences.user_id = connections.user_id
  AND connections.billing_mode = 'BYOK'
  AND split_part(preferences.default_heavy_model_key, ':', 1) = connections.provider_key;

ALTER TABLE user_ai_preferences
    DROP CONSTRAINT IF EXISTS chk_user_ai_preferences_auth_source;

ALTER TABLE user_ai_preferences
    ADD CONSTRAINT chk_user_ai_preferences_auth_source
        CHECK (default_auth_source IN ('PLATFORM', 'BYOK', 'USER_LINKED'));
