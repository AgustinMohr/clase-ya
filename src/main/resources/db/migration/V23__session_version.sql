-- AUTH-004: server-side session revocation without a per-request lookup.
-- Every JWT carries the user's session version; logout/reset increments it. Existing rows start
-- at 0, so no backfill is needed.

alter table users
    add column session_version int not null default 0;
