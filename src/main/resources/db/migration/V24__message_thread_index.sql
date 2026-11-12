-- PERF-001: keyset pagination for the message thread.
-- findLatestThreadPage/findThreadPageBefore filter by conversation_id and order by
-- (created_at DESC, id DESC). V4 covers (conversation_id, created_at); the new index adds the
-- stable id tiebreaker so the plan does not need an extra sort over the thread.

create index idx_messages_conversation_created_id
    on messages (conversation_id, created_at desc, id desc);
