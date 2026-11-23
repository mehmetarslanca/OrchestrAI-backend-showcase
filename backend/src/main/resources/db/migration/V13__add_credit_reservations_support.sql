ALTER TABLE credits
    ADD COLUMN reserved_credits INTEGER NOT NULL DEFAULT 0;

ALTER TABLE credits
    ADD CONSTRAINT chk_credits_reserved_non_negative CHECK (reserved_credits >= 0);

COMMENT ON COLUMN credits.reserved_credits IS 'Reserve edilen ama henuz capture edilmeyen kredi miktari';
