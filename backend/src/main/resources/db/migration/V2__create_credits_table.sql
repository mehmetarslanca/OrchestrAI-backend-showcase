-- =============================================
-- V2: Credits tablosu
-- Her kullanıcının API kullanım kredisini takip eder.
-- User ile 1:1 ilişki — her kullanıcıda tam olarak bir kredi satırı.
-- =============================================

CREATE TABLE credits (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL,
    balance     INTEGER     NOT NULL DEFAULT 5,
    total_used  INTEGER     NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_credits_user    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_credits_user_id UNIQUE (user_id),
    CONSTRAINT chk_credits_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT chk_credits_total_used_non_negative CHECK (total_used >= 0)
);

COMMENT ON TABLE credits IS 'Kullanıcı başına API kullanım kredisi';
COMMENT ON COLUMN credits.balance IS 'Kalan kredi miktarı. Her blueprint isteğinde 1 düşer.';
COMMENT ON COLUMN credits.total_used IS 'Toplam kullanılmış kredi sayısı (analitik amaçlı)';
