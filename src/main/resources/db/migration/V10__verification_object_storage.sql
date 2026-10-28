-- =============================================================================
-- STOR-01: verification evidence moves to S3-compatible object storage (R2)
--
-- The bytes may live in `content` (legacy PostgreSQL rows) or in an object
-- storage bucket referenced by `storage_key` (new rows). At least one must be
-- present. `scan_status` records the antivirus result; only CLEAN evidence is
-- served (ClamAV runs synchronously before storing a new document).
-- =============================================================================

ALTER TABLE verification_documents ALTER COLUMN content DROP NOT NULL;

ALTER TABLE verification_documents
    ADD COLUMN storage_key varchar(512),
    ADD COLUMN scan_status varchar(20) NOT NULL DEFAULT 'CLEAN'
        CHECK (scan_status IN ('PENDING', 'CLEAN', 'INFECTED'));

ALTER TABLE verification_documents
    ADD CONSTRAINT chk_verification_documents_storage
        CHECK (content IS NOT NULL OR storage_key IS NOT NULL);
