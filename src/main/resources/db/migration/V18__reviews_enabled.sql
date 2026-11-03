-- =============================================================================
-- V18 — Reviews and rating (OPINION-001, ADR-033)
--
-- Enables the reviews table retained by V17/ADR-032:
--   * one published/hidden review per student<->teacher pair;
--   * one editable teacher response;
--   * admin moderation with an append-only audit trail;
--   * rating aggregates on teacher_profiles are derived from PUBLISHED reviews.
--
-- Existing duplicate pairs (possible in legacy data created before V17) are
-- reduced to the newest review before the UNIQUE is added. Back up the table
-- before applying this migration in an environment with real data.
-- =============================================================================

DELETE FROM reviews r
USING reviews newer
WHERE r.student_id = newer.student_id
  AND r.teacher_id = newer.teacher_id
  AND (r.created_at, r.id) < (newer.created_at, newer.id);

ALTER TABLE reviews
    ADD COLUMN status varchar(20) NOT NULL DEFAULT 'PUBLISHED'
        CHECK (status IN ('PUBLISHED', 'HIDDEN')),
    ADD COLUMN teacher_response text,
    ADD COLUMN teacher_responded_at timestamptz,
    ADD COLUMN moderated_by uuid,
    ADD COLUMN moderated_at timestamptz,
    ADD COLUMN moderation_reason text;

ALTER TABLE reviews
    ADD CONSTRAINT fk_reviews_moderated_by
        FOREIGN KEY (moderated_by) REFERENCES users (id) ON DELETE RESTRICT;

ALTER TABLE reviews
    ADD CONSTRAINT uq_reviews_student_teacher UNIQUE (student_id, teacher_id);

CREATE INDEX idx_reviews_teacher_status_created
    ON reviews (teacher_id, status, created_at DESC);

CREATE TABLE review_moderation_events (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id      uuid NOT NULL REFERENCES reviews (id) ON DELETE RESTRICT,
    admin_user_id  uuid NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    action         varchar(20) NOT NULL CHECK (action IN ('HIDDEN', 'RESTORED')),
    reason         text,
    created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_review_moderation_events_review
    ON review_moderation_events (review_id, created_at DESC);
