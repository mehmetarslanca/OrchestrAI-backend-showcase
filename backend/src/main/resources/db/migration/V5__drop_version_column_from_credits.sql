-- =============================================
-- V5: Drop version column from credits table
--
-- Reason: Concurrency protocol mismatch fix.
-- CreditService uses PESSIMISTIC_WRITE lock (findByUserIdForUpdate).
-- The @Version (optimistic locking) column is redundant and can cause
-- OptimisticLockException on transaction commit when both are present.
-- Pessimistic lock is the single source of truth for credit deduction.
-- =============================================

ALTER TABLE credits DROP COLUMN IF EXISTS version;
