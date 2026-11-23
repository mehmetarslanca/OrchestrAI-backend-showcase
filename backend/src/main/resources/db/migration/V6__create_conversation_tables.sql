-- Phase 9: Chat & Conversation System
-- Kullanıcı bazlı konuşma projeleri, chat session'ları ve mesajlar

-- Konuşma projeleri (kullanıcı bazlı gruplar)
CREATE TABLE conversation_projects (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_conv_projects_user_id ON conversation_projects(user_id);

-- Chat session'ları (proje altında)
CREATE TABLE chats (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_project_id     UUID NOT NULL REFERENCES conversation_projects(id) ON DELETE CASCADE,
    title                       VARCHAR(200) NOT NULL,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chats_conv_project ON chats(conversation_project_id);

-- Mesajlar (chat altında)
CREATE TABLE messages (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id     UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    role        VARCHAR(20) NOT NULL,
    content     TEXT NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_message_role CHECK (role IN ('USER', 'ASSISTANT'))
);

CREATE INDEX idx_messages_chat_id ON messages(chat_id);
CREATE INDEX idx_messages_created_at ON messages(created_at ASC);
