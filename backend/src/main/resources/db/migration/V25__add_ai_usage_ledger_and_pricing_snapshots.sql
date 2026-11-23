CREATE TABLE ai_pricing_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_key VARCHAR(50) NOT NULL,
    connection_type VARCHAR(50) NOT NULL,
    billing_mode VARCHAR(20) NOT NULL,
    model_key VARCHAR(120) NOT NULL,
    concrete_model_id VARCHAR(255),
    rate_card_version VARCHAR(80),
    input_cost_per_million_usd NUMERIC(18, 8),
    output_cost_per_million_usd NUMERIC(18, 8),
    cached_input_cost_per_million_usd NUMERIC(18, 8),
    reasoning_cost_per_million_usd NUMERIC(18, 8),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ai_pricing_snapshots_connection_type CHECK (
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
    CONSTRAINT chk_ai_pricing_snapshots_billing_mode CHECK (
        billing_mode IN ('PLATFORM_PAID', 'BYOK', 'USER_LINKED')
    ),
    CONSTRAINT chk_ai_pricing_snapshots_input_price CHECK (
        input_cost_per_million_usd IS NULL OR input_cost_per_million_usd >= 0
    ),
    CONSTRAINT chk_ai_pricing_snapshots_output_price CHECK (
        output_cost_per_million_usd IS NULL OR output_cost_per_million_usd >= 0
    ),
    CONSTRAINT chk_ai_pricing_snapshots_cached_input_price CHECK (
        cached_input_cost_per_million_usd IS NULL OR cached_input_cost_per_million_usd >= 0
    ),
    CONSTRAINT chk_ai_pricing_snapshots_reasoning_price CHECK (
        reasoning_cost_per_million_usd IS NULL OR reasoning_cost_per_million_usd >= 0
    )
);

CREATE INDEX idx_ai_pricing_snapshots_lookup
    ON ai_pricing_snapshots (provider_key, connection_type, billing_mode, model_key, captured_at DESC);

CREATE TABLE ai_usage_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    connection_id UUID REFERENCES user_ai_connections(id) ON DELETE SET NULL,
    pricing_snapshot_id UUID REFERENCES ai_pricing_snapshots(id) ON DELETE RESTRICT,
    request_id VARCHAR(255),
    provider_key VARCHAR(50) NOT NULL,
    connection_type VARCHAR(50) NOT NULL,
    billing_mode VARCHAR(20) NOT NULL,
    surface VARCHAR(30) NOT NULL,
    model_key VARCHAR(120) NOT NULL,
    concrete_model_id VARCHAR(255),
    provider_request_id VARCHAR(255),
    usage_source VARCHAR(20) NOT NULL,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    cached_input_tokens BIGINT NOT NULL DEFAULT 0,
    reasoning_tokens BIGINT NOT NULL DEFAULT 0,
    estimated_cost_usd NUMERIC(18, 8),
    reconciled_cost_usd NUMERIC(18, 8),
    reconciliation_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ai_usage_events_connection_type CHECK (
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
    CONSTRAINT chk_ai_usage_events_billing_mode CHECK (
        billing_mode IN ('PLATFORM_PAID', 'BYOK', 'USER_LINKED')
    ),
    CONSTRAINT chk_ai_usage_events_surface CHECK (
        surface IN ('CHAT', 'PLANNING', 'GENERATION', 'INTERNAL_LIGHT_TASK')
    ),
    CONSTRAINT chk_ai_usage_events_usage_source CHECK (
        usage_source IN ('PROVIDER', 'ESTIMATED')
    ),
    CONSTRAINT chk_ai_usage_events_reconciliation_status CHECK (
        reconciliation_status IN ('PENDING', 'RECONCILED', 'NOT_SUPPORTED', 'FAILED')
    ),
    CONSTRAINT chk_ai_usage_events_status CHECK (
        status IN ('STARTED', 'COMPLETED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT chk_ai_usage_events_input_tokens CHECK (input_tokens >= 0),
    CONSTRAINT chk_ai_usage_events_output_tokens CHECK (output_tokens >= 0),
    CONSTRAINT chk_ai_usage_events_cached_input_tokens CHECK (cached_input_tokens >= 0),
    CONSTRAINT chk_ai_usage_events_reasoning_tokens CHECK (reasoning_tokens >= 0),
    CONSTRAINT chk_ai_usage_events_estimated_cost CHECK (
        estimated_cost_usd IS NULL OR estimated_cost_usd >= 0
    ),
    CONSTRAINT chk_ai_usage_events_reconciled_cost CHECK (
        reconciled_cost_usd IS NULL OR reconciled_cost_usd >= 0
    ),
    CONSTRAINT chk_ai_usage_events_completed_after_started CHECK (
        completed_at IS NULL OR completed_at >= started_at
    )
);

CREATE INDEX idx_ai_usage_events_user_started_at
    ON ai_usage_events (user_id, started_at DESC);

CREATE INDEX idx_ai_usage_events_connection_started_at
    ON ai_usage_events (connection_id, started_at DESC);

CREATE INDEX idx_ai_usage_events_request_id
    ON ai_usage_events (user_id, request_id);

CREATE INDEX idx_ai_usage_events_provider_request_id
    ON ai_usage_events (provider_key, provider_request_id);
