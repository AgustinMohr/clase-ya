-- =============================================================================
-- REVIEW2-002: the outbox also delivers activation emails
--
-- Why:
--   V13 created notification_outbox with a CHECK that only allowed
--   CONTACT_REQUEST. RF-1 requires *every* email to be enqueued inside the
--   business transaction, and registration (AUTH-001) is the other place that
--   sends one. V13 is left untouched because it may already be applied; the
--   constraint is widened here instead.
--
-- The raw activation token travels in the payload only while the row is
-- PENDING/FAILED/SENDING: the worker clears the payload when it marks SENT or
-- DEAD, so the credential is not kept at rest after delivery (ADR-029).
-- =============================================================================

ALTER TABLE notification_outbox
    DROP CONSTRAINT notification_outbox_event_type_check;

ALTER TABLE notification_outbox
    ADD CONSTRAINT notification_outbox_event_type_check
    CHECK (event_type IN ('CONTACT_REQUEST', 'ACTIVATION'));
