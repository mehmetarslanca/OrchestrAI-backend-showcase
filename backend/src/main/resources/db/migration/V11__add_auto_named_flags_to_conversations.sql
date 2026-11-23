ALTER TABLE conversation_projects ADD COLUMN is_auto_named BOOLEAN DEFAULT true NOT NULL;
ALTER TABLE chats ADD COLUMN is_auto_named BOOLEAN DEFAULT true NOT NULL;
