-- =============================================================================
-- clase-ya — Phase 6: messaging query support
--
-- The messaging tables (conversations, conversation_participants, messages)
-- already exist from Phase 1. This only adds an index for the common
-- per-conversation message ordering used by message listing and by the
-- last-message lookup. No tables or constraints are changed.
-- =============================================================================

CREATE INDEX idx_messages_conversation_created
    ON messages (conversation_id, created_at);
