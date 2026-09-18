-- =============================================================================
-- clase-ya — Phase 4: public display name + teacher search support
--
-- 1) Public display name for users (profiles had no name field yet). Optional;
--    teachers set it through their profile. Never exposes email publicly.
-- 2) Indexes that help the teacher-search hot paths:
--    - verified teachers ordered by rating (default listing/sort)
--    - modality lookups (filter by modality; (teacher_id, modality) already covered)
--    - teacher coordinates for the bounding-box geo pre-filter
--      (idx_teacher_profiles_location already exists from Phase 1)
-- =============================================================================

ALTER TABLE users ADD COLUMN name varchar(255);

CREATE INDEX idx_teacher_profiles_verified_rating
    ON teacher_profiles (verification_status, rating_average DESC, rating_count DESC);

CREATE INDEX idx_teacher_modalities_modality
    ON teacher_modalities (modality);
