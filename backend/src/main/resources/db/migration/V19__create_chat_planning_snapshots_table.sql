CREATE TABLE chat_planning_snapshots (
    id UUID PRIMARY KEY,
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    last_summarized_message_count BIGINT NOT NULL,
    snapshot_contract_version VARCHAR(20) NOT NULL,
    snapshot_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_chat_planning_snapshots_chat_id UNIQUE (chat_id)
);

CREATE INDEX idx_chat_planning_snapshots_chat_id ON chat_planning_snapshots(chat_id);
