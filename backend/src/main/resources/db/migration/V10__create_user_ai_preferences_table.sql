CREATE TABLE user_ai_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    default_heavy_model_key VARCHAR(120),
    default_auth_source VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_ai_preferences_auth_source CHECK (default_auth_source IN ('PLATFORM', 'BYOK'))
);
