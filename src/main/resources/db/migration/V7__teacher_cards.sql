-- =============================================================================
-- clase-ya — PROFILE-001: public teacher card fields
--
-- Adds optional display fields used by the public teacher card and detail:
-- price per hour, city/zone and a photo URL. None of them are private-visible;
-- email/address/exact coordinates remain hidden.
-- =============================================================================

ALTER TABLE teacher_profiles
    ADD COLUMN price_per_hour numeric(10, 2),
    ADD COLUMN city varchar(100),
    ADD COLUMN photo_url varchar(500);
