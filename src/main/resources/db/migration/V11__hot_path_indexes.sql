-- =============================================================================
-- DB-01: indexes for hot read paths (review2.md)
--
-- Justification by query shape, not by guess:
--
--   1) messages unread counts and read marking. MessageRepository.countUnread,
--      countUnreadGrouped and markConversationRead all filter by
--      (conversation_id, sender_id <> me, read_at IS NULL). The existing
--      (conversation_id, created_at) index narrows to the conversation and then
--      scans every message in it. The partial index keeps only unread rows, so
--      the cost is proportional to unread messages, not to thread length.
--
--   2) subjects catalog. GET /api/subjects reads active subjects ordered by name
--      on every landing page load. The partial index serves both the filter and
--      the ordering without touching inactive rows.
--
-- Indexes only: no table, column, constraint or data change. Re-measure with
-- EXPLAIN (ANALYZE, BUFFERS) once production has real volume; if a plan does not
-- use an index, drop it instead of keeping write overhead.
-- =============================================================================

CREATE INDEX idx_messages_unread
    ON messages (conversation_id)
    WHERE read_at IS NULL;

CREATE INDEX idx_subjects_active_name
    ON subjects (name)
    WHERE active;
