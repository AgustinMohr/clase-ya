-- =============================================================================
-- clase-ya — AVAIL-001: weekly recurring teacher availability
--
-- Adds:
--   1) availability_windows: recurring weekly windows (day of week + minutes of
--      day) that teachers publish. A window is a wall-clock pattern, NOT an
--      instant; concrete dates are materialized later by the booking feature.
--   2) btree_gist extension + an exclusion constraint so the database prevents
--      overlapping windows per (teacher, day) even under concurrency.
--   3) teacher_profiles.availability_note: optional public descriptive note.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE availability_windows (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id     uuid NOT NULL REFERENCES teacher_profiles (id) ON DELETE RESTRICT,
    day_of_week    int NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_minutes  int NOT NULL CHECK (start_minutes BETWEEN 0 AND 1439),
    end_minutes    int NOT NULL CHECK (end_minutes BETWEEN 1 AND 1440),
    mode           varchar(20) CHECK (mode IN ('ONLINE', 'IN_PERSON')),
    status         varchar(20) NOT NULL DEFAULT 'AVAILABLE'
                   CHECK (status IN ('AVAILABLE', 'DISABLED')),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_availability_end_gt_start CHECK (end_minutes > start_minutes),
    CONSTRAINT chk_availability_min_duration CHECK (end_minutes - start_minutes >= 60),
    -- Contiguous windows are allowed (half-open [start, end)); overlaps are not.
    CONSTRAINT ex_availability_windows_no_overlap EXCLUDE USING gist (
        teacher_id WITH =,
        day_of_week WITH =,
        int4range(start_minutes, end_minutes, '[)') WITH &&
    )
);

CREATE INDEX idx_availability_windows_teacher
    ON availability_windows (teacher_id, day_of_week, start_minutes);

ALTER TABLE teacher_profiles ADD COLUMN availability_note text;
