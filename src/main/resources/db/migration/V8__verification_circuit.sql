-- =============================================================================
-- clase-ya — TEACHER-001: circuito de verificación documental (base del slice B)
--
-- 1) Amplía teacher_profiles.verification_status de 3 a 5 estados.
-- 2) Agrega estado de verificación POR CREDENCIAL (teacher_education) + momento de
--    presentación; elimina el boolean is_verified (D2: una sola fuente de verdad).
-- 3) verification_documents: evidencia privada e inmutable (bytea, D5).
-- 4) verification_decisions: auditoría append-only con estado anterior/nuevo.
-- =============================================================================

ALTER TABLE teacher_profiles
    DROP CONSTRAINT teacher_profiles_verification_status_check;

ALTER TABLE teacher_profiles
    ADD CONSTRAINT teacher_profiles_verification_status_check
        CHECK (verification_status IN ('PENDING', 'UNDER_REVIEW', 'VERIFIED', 'REJECTED', 'MORE_INFO_REQUIRED'));

ALTER TABLE teacher_education
    ADD COLUMN verification_status varchar(20) NOT NULL DEFAULT 'PENDING'
        CHECK (verification_status IN ('PENDING', 'UNDER_REVIEW', 'VERIFIED', 'REJECTED', 'MORE_INFO_REQUIRED')),
    ADD COLUMN submitted_at timestamptz,
    DROP COLUMN is_verified;

CREATE INDEX idx_teacher_education_verification
    ON teacher_education (verification_status, submitted_at);

CREATE TABLE verification_documents (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id           uuid NOT NULL REFERENCES teacher_profiles (id) ON DELETE RESTRICT,
    teacher_education_id uuid NOT NULL REFERENCES teacher_education (id) ON DELETE RESTRICT,
    type                 varchar(40) NOT NULL
                         CHECK (type IN ('DIPLOMA', 'ENROLLMENT_CERTIFICATE',
                                         'ANALYTICAL_CERTIFICATE', 'POSTGRADUATE_CERTIFICATE',
                                         'PROFESSIONAL_LICENSE', 'FOREIGN_DEGREE')),
    original_filename    varchar(255) NOT NULL,
    content_type         varchar(100) NOT NULL,
    size_bytes           bigint NOT NULL CHECK (size_bytes > 0),
    sha256               varchar(64) NOT NULL,
    content              bytea NOT NULL,
    uploaded_at          timestamptz NOT NULL DEFAULT now(),
    uploaded_by          uuid NOT NULL REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_verification_documents_teacher
    ON verification_documents (teacher_id);

CREATE INDEX idx_verification_documents_education
    ON verification_documents (teacher_education_id);

CREATE TABLE verification_decisions (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id           uuid NOT NULL REFERENCES teacher_profiles (id) ON DELETE RESTRICT,
    teacher_education_id uuid REFERENCES teacher_education (id) ON DELETE RESTRICT,
    document_id          uuid REFERENCES verification_documents (id) ON DELETE RESTRICT,
    admin_user_id        uuid NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    previous_status      varchar(20),
    new_status           varchar(20) NOT NULL,
    decision             varchar(20) NOT NULL
                         CHECK (decision IN ('VERIFIED', 'REJECTED', 'MORE_INFO_REQUIRED', 'REVOKED')),
    method               varchar(40) NOT NULL
                         CHECK (method IN ('INSTITUTION_CHECK', 'OFFICIAL_REGISTRY',
                                           'DIGITAL_DOCUMENT_CHECK', 'DOCUMENT_ANALYSIS', 'OTHER')),
    reason               text,
    decided_at           timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_verification_decisions_teacher
    ON verification_decisions (teacher_id, decided_at);

CREATE INDEX idx_verification_decisions_education
    ON verification_decisions (teacher_education_id);
