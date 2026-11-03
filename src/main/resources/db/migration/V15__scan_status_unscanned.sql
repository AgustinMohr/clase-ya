-- =============================================================================
-- STOR-002: allow evidence stored without an antivirus in the deployment.
--
-- The scan result must reflect what actually happened. `UNSCANNED` says the file
-- was never checked (scanning disabled and explicitly acknowledged with
-- ALLOW_UNSCANNED_EVIDENCE), instead of lying with `CLEAN`. Historical rows keep
-- their value.
-- =============================================================================

ALTER TABLE verification_documents DROP CONSTRAINT verification_documents_scan_status_check;

ALTER TABLE verification_documents ADD CONSTRAINT verification_documents_scan_status_check
    CHECK (scan_status IN ('PENDING', 'CLEAN', 'INFECTED', 'UNSCANNED'));
