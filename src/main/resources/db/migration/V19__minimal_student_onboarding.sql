-- =============================================================================
-- PROFILE-003: minimal student onboarding.
--
-- The university/career pair was mandatory, which blocked primary, secondary and
-- adult learners (and contradicted the PRD's minimal profile). The academic data
-- becomes optional; level and city are the new primary public attributes.
--
-- No data is deleted: existing academic rows keep their university, career and
-- current year. Existing rows get level=UNIVERSIDAD when they have an academic
-- pair, so the profile is immediately usable after the migration.
-- =============================================================================

ALTER TABLE student_profiles
    ALTER COLUMN university_id DROP NOT NULL,
    ALTER COLUMN career_id DROP NOT NULL,
    ALTER COLUMN current_year DROP NOT NULL;

ALTER TABLE student_profiles
    ADD COLUMN level varchar(20)
        CHECK (level IS NULL OR level IN ('PRIMARIA', 'SECUNDARIA', 'TERCIARIO', 'UNIVERSIDAD', 'ADULTO')),
    ADD COLUMN city varchar(100);

UPDATE student_profiles
SET level = 'UNIVERSIDAD'
WHERE level IS NULL
  AND university_id IS NOT NULL
  AND career_id IS NOT NULL;

CREATE INDEX idx_student_profiles_level ON student_profiles (level);
