-- =============================================================================
-- AUTH-001: email + password registration with activation
--
-- The password path used to create PENDING accounts with no way to activate them.
-- Accounts now activate through a single-use token delivered by email, so the
-- backend needs somewhere to store the token (hashed) and the proof that the
-- address was verified (email_verified_at).
--
-- The raw token is never persisted: token_hash is the SHA-256 hex digest.
-- =============================================================================

ALTER TABLE users ADD COLUMN email_verified_at timestamptz;

CREATE TABLE email_verification_tokens (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  varchar(64) NOT NULL,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_email_verification_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_email_verification_tokens_user ON email_verification_tokens (user_id);
