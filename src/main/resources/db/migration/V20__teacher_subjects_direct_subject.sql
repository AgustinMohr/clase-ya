-- =============================================================================
-- PROFILE-004: teachers can publish a subject without a university career.
--
-- Before this migration every teacher_subjects row pointed to a CareerSubject,
-- so a school tutor (inglés básico, apoyo escolar, primaria) could not publish.
-- A row now targets exactly one of:
--   * career_subject_id (legacy/tertiary catalog), or
--   * subject_id (direct subject, independent of career/university).
--
-- Existing rows keep their career_subject_id; no data is rewritten.
-- =============================================================================

ALTER TABLE teacher_subjects
    ALTER COLUMN career_subject_id DROP NOT NULL;

ALTER TABLE teacher_subjects
    ADD COLUMN subject_id uuid REFERENCES subjects (id) ON DELETE RESTRICT;

ALTER TABLE teacher_subjects
    ADD CONSTRAINT chk_teacher_subjects_exactly_one_target CHECK (
        (career_subject_id IS NOT NULL AND subject_id IS NULL)
        OR (career_subject_id IS NULL AND subject_id IS NOT NULL)
    );

-- The existing UNIQUE (teacher_id, career_subject_id) allows many NULL career
-- subjects, so direct links need their own partial unique index.
CREATE UNIQUE INDEX uq_teacher_subjects_direct_subject
    ON teacher_subjects (teacher_id, subject_id)
    WHERE subject_id IS NOT NULL;

CREATE INDEX idx_teacher_subjects_subject_id
    ON teacher_subjects (subject_id)
    WHERE subject_id IS NOT NULL;
