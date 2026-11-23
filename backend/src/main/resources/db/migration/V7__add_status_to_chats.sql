-- Phase 9: Add status to chats for concurrency control
ALTER TABLE chats ADD COLUMN status VARCHAR(50) DEFAULT 'IDLE' NOT NULL;
