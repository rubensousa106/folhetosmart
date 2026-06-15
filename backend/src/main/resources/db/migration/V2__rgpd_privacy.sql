-- =====================================================================
-- Update 5 — Conformidade RGPD
-- Consentimento explícito + registo de auditoria de ações de privacidade.
-- Idempotente (coexiste com db/init.sql atualizado).
-- =====================================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS consent_given_at      TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS consent_version       VARCHAR(10);
ALTER TABLE users ADD COLUMN IF NOT EXISTS deletion_requested_at TIMESTAMPTZ;

-- Auditoria de privacidade. user_id fica NULL após hard delete da conta
-- (ON DELETE SET NULL) para o registo de auditoria sobreviver à eliminação.
CREATE TABLE IF NOT EXISTS consent_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID REFERENCES users(id) ON DELETE SET NULL,
    action      VARCHAR(50),   -- "consent_given" | "consent_withdrawn" | "data_exported" | "account_deleted" | "notifications_consent_given"
    version     VARCHAR(10),
    ip_hash     VARCHAR(64),   -- SHA-256 do IP, nunca o IP em claro
    created_at  TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_consent_log_user ON consent_log (user_id);
CREATE INDEX IF NOT EXISTS idx_consent_log_created ON consent_log (created_at DESC);
