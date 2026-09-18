-- =============================================================================
-- clase-ya — Phase 3: teacher teaching modalities
--
-- A teacher may offer classes ONLINE, IN_PERSON, or both. Modeled as a join
-- table (N:N teacher <-> modality) so both values can coexist for one teacher.
-- =============================================================================

CREATE TABLE teacher_modalities (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id  uuid NOT NULL REFERENCES teacher_profiles (id) ON DELETE CASCADE,
    modality    varchar(20) NOT NULL CHECK (modality IN ('ONLINE', 'IN_PERSON')),
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_teacher_modalities_teacher_modality UNIQUE (teacher_id, modality)
);
