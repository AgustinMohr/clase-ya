-- REPORT-001: reports queue and user blocking.
--
-- user_blocks persists the safety relationship; the unique pair and the self-check are
-- invariants. The partial unique index keeps one open report per reporter/reported pair,
-- and the status/created index serves the admin queue. No existing column changes.

create table user_blocks (
    id          uuid primary key default gen_random_uuid(),
    blocker_id  uuid not null references users (id) on delete cascade,
    blocked_id  uuid not null references users (id) on delete cascade,
    created_at  timestamptz not null default now(),
    constraint uq_user_blocks_pair unique (blocker_id, blocked_id),
    constraint chk_user_blocks_no_self check (blocker_id <> blocked_id)
);

create index idx_user_blocks_blocked_id on user_blocks (blocked_id);

create unique index uq_reports_open_pair
    on reports (reporter_id, reported_user_id)
    where status in ('OPEN', 'REVIEWING');

create index idx_reports_status_created on reports (status, created_at desc);
