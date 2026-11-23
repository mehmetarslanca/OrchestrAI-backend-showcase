ALTER TABLE user_provider_credentials
    DROP CONSTRAINT IF EXISTS chk_user_provider_credentials_provider;

ALTER TABLE user_provider_credentials
    ADD CONSTRAINT chk_user_provider_credentials_provider
        CHECK (provider_key IN ('google', 'openai', 'anthropic', 'ollama', 'openai-compatible', 'moonshot', 'zai', 'xai'));
