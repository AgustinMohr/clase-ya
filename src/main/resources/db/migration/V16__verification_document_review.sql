-- =============================================================================
-- VERIF-001: per-document review mark for the admin review queue.
--
-- The checkbox is a working aid for the reviewer, not an audit trail (that is
-- verification_decisions, append-only). reviewed_by is RESTRICT so a user row
-- with review history is never hard-deleted.
-- =============================================================================

ALTER TABLE verification_documents
    ADD COLUMN reviewed_at timestamptz,
    ADD COLUMN reviewed_by uuid;

ALTER TABLE verification_documents
    ADD CONSTRAINT fk_verification_documents_reviewed_by
        FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE RESTRICT;
