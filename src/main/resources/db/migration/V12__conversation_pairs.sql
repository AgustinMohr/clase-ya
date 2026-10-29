-- =============================================================================
-- RACE-01: one conversation per student<->teacher pair, enforced by the database
--
-- ConversationService used to read-then-create: two concurrent POST /api/conversations
-- from the same pair could both see no conversation and create one each. ADR-011
-- accepted the limitation; this table closes it.
--
-- The pair is the natural key of a conversation, so it is also the lock: the service
-- does INSERT ... ON CONFLICT (student_user_id, teacher_user_id) DO NOTHING. The
-- request that loses the insert reuses the winner's conversation instead of creating a
-- duplicate.
--
-- No backfill: legacy conversations keep working through the existing lookup, and
-- backfilling could collide with a pair that already has two conversations from the old
-- race. New conversations are protected from the moment this migration runs.
-- =============================================================================

CREATE TABLE conversation_pairs (
    student_user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    teacher_user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    conversation_id uuid NOT NULL UNIQUE REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT pk_conversation_pairs PRIMARY KEY (student_user_id, teacher_user_id),
    CONSTRAINT chk_conversation_pairs_distinct CHECK (student_user_id <> teacher_user_id)
);
