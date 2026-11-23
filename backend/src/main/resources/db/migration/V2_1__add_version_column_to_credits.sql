-- =============================================
-- V2.1: Add version column to credits table
-- Required for JPA @Version optimistic locking
-- =============================================

ALTER TABLE credits ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
