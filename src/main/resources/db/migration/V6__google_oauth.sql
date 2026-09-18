-- =============================================================================
-- clase-ya — OAUTH-001: Google sign-in identity link
--
-- Adds users.google_sub: the stable Google identifier used to link a Google
-- account to one ClaseYa account (one-to-one). Email remains the logical
-- identity. No Google tokens are stored.
-- =============================================================================

ALTER TABLE users ADD COLUMN google_sub varchar(255);

CREATE UNIQUE INDEX uq_users_google_sub ON users (google_sub);
