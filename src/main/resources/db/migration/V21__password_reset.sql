-- =============================================================================
-- AUTH-003: password reset by single-use email link.
--
-- The token is stored as a SHA-256 hash; the raw value only travels in the
-- emailed link and in the outbox payload until the worker marks it SENT/DEAD.
-- A reset does not reveal whether the email exists: the request endpoint always
-- answers 202.
-- =============================================================================

CREATE TABLE password_reset_tokens (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  varchar(64) NOT NULL,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens (user_id);

ALTER TABLE notification_outbox
    DROP CONSTRAINT notification_outbox_event_type_check;

ALTER TABLE notification_outbox
    ADD CONSTRAINT notification_outbox_event_type_check
    CHECK (event_type IN ('CONTACT_REQUEST', 'ACTIVATION', 'PASSWORD_RESET'));
