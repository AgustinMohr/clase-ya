-- =============================================================================
-- REVIEW2-002: transactional outbox + single-use message links
--
-- Why:
--   1) Emails used to be sent inside the transaction that created the account
--      (AUTH-001) and the contact notification did not exist at all. Sending
--      after commit through an outbox removes network I/O from the transaction
--      and makes delivery retryable.
--   2) The teacher's contact email carries a single-use link. It gets its own
--      table (not email_verification_tokens): different purpose, TTL and
--      audience, and revoking one flow must not affect the other.
--
-- The worker claims rows with FOR UPDATE SKIP LOCKED and a 2-minute lease, so
-- a crashed instance does not leave a row stuck in SENDING.
-- =============================================================================

CREATE TABLE notification_outbox (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type        varchar(40) NOT NULL
                      CHECK (event_type IN ('CONTACT_REQUEST')),
    recipient_user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    dedupe_key        varchar(160) NOT NULL,
    payload           jsonb NOT NULL,
    status            varchar(20) NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'DEAD')),
    attempts          int NOT NULL DEFAULT 0,
    next_attempt_at   timestamptz NOT NULL DEFAULT now(),
    locked_until      timestamptz,
    last_error        text,
    created_at        timestamptz NOT NULL DEFAULT now(),
    sent_at           timestamptz,
    CONSTRAINT uq_notification_outbox_dedupe UNIQUE (dedupe_key)
);

CREATE INDEX idx_notification_outbox_due
    ON notification_outbox (next_attempt_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE TABLE message_link_tokens (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    conversation_id uuid NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    token_hash      varchar(64) NOT NULL,
    expires_at      timestamptz NOT NULL,
    used_at         timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_message_link_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_message_link_tokens_user ON message_link_tokens (user_id);
