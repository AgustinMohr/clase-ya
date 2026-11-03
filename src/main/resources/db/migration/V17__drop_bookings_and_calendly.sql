-- =============================================================================
-- V17 — Legacy table cleanup (ADR-032)
--
-- Product direction: reviews, reports, notifications and the cached rating
-- aggregates stay for a future implementation. Bookings and Calendly are no
-- longer part of the product, so their tables are removed.
--
-- reviews.booking_id is dropped because bookings no longer exists. The reviews
-- table itself, its student/teacher/rating/comment columns and the rating
-- aggregates on teacher_profiles are kept. A future review spec will define a
-- new linkage (conversation/contact) instead of a booking.
--
-- Destructive by design: existing booking rows and the booking linkage of
-- existing reviews are discarded. Back up those tables before applying this
-- migration in an environment that still holds real data.
-- =============================================================================

ALTER TABLE reviews DROP CONSTRAINT IF EXISTS reviews_booking_id_fkey;
ALTER TABLE reviews DROP CONSTRAINT IF EXISTS uq_reviews_booking;
ALTER TABLE reviews DROP COLUMN booking_id;

DROP TABLE calendly_integrations;
DROP TABLE bookings;
