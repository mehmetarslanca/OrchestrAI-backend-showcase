CREATE TABLE project_generation_preparations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    chat_id UUID REFERENCES chats(id) ON DELETE CASCADE,
    chat_message_count BIGINT,
    source_channel VARCHAR(20) NOT NULL,
    project_name VARCHAR(200) NOT NULL,
    ide_target VARCHAR(30) NOT NULL,
    prompt_summary TEXT NOT NULL,
    structured_analysis JSONB NOT NULL,
    starter_selection JSONB NOT NULL,
    status VARCHAR(30) NOT NULL,
    project_id UUID REFERENCES projects(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT DEFAULT 0
);

CREATE INDEX idx_project_generation_preparations_user_id
    ON project_generation_preparations (user_id, created_at DESC);

CREATE INDEX idx_project_generation_preparations_chat_id
    ON project_generation_preparations (chat_id);
