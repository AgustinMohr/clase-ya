# ClaseYa — Authentication & Authorization

Stateless, JWT-based authentication and role-based authorization. There are two supported ways to
get an account:

- **Google OAuth** (primary, per the PRD): the account starts `ACTIVE` because Google already
  verified the email.
- **Email + password** (secondary, complete since AUTH-001): the account starts `PENDING` and only
  authenticates after the owner opens the single-use activation link sent by email.

Passwords are hashed with BCrypt. Browsers keep the JWT in an **HttpOnly session cookie**
(`CLASEYA_SESSION`) and protect unsafe requests with a CSRF token (SEC-002). The integration
suite/tooling may still send `Authorization: Bearer <jwt>`. The SPA never sees the token.

## Quick path (email + password)

1. Start PostgreSQL and run the app (`mvn spring-boot:run`).
2. Configure SMTP (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`,
   `APP_BASE_URL`). Without SMTP, registration answers `503`.
   - Local only: `AUTH_LOG_ACTIVATION_LINKS=true` writes the activation link to the application log
     instead of sending it.
3. `POST /api/auth/register` → user created with `status=PENDING` + activation email.
4. Open the link (`${APP_BASE_URL}/activar?token=...`) → the SPA calls `POST /api/auth/activate`.
5. `GET /api/auth/csrf` → `{ token }` (browser login needs it).
6. `POST /api/auth/login` → `Set-Cookie: CLASEYA_SESSION` + `{ user, expiresIn }`.
7. Browser: call protected endpoints with `credentials: 'include'` and `X-XSRF-TOKEN` on writes.
   Integration tests/tooling: `Authorization: Bearer <jwt>` (no CSRF).

## Register

```
POST /api/auth/register
{ "email": "student@example.com", "password": "Password123", "role": "STUDENT" }

202 Accepted
(no body)
```

- Email is normalized to lowercase before save and lookup.
- **Password policy (AUTH-002 / SEC-04):** at least 10 characters, at least one letter and one
  digit, and at most **72 UTF-8 bytes** (BCrypt silently truncates beyond that). Violations →
  **400** with `fieldErrors.password`.
- The response is **always 202** and never reveals whether the email exists (anti-enumeration):
  - new email → `PENDING` account + activation email;
  - existing `PENDING` → the previous token is invalidated and a new link is sent;
  - existing `ACTIVE` → no email, and the password is **not** changed.
- `role` must be `STUDENT` or `TEACHER`. `ADMIN` **cannot** self-register (400).
- The activation email is sent inside the registration transaction: if there is no mail transport
  (or SMTP fails), the account is **rolled back** and the API answers **503**.

## Activation

| Endpoint | Request | Response |
|---|---|---|
| `POST /api/auth/activate` | `{ "token": "<string>" }` | `204`; `400` invalid/expired/used |
| `POST /api/auth/resend-activation` | `{ "email": "<string>" }` | `202` always |

- Only a SHA-256 digest of the token is stored (`email_verification_tokens`), with 24 h TTL and
  `used_at`. A successful activation sets `users.status = ACTIVE` and `users.email_verified_at`.
- Resending invalidates every outstanding token of that account.
- The resend endpoint never reveals whether the email exists (202 for unknown emails too).
- Both endpoints share the registration rate-limit bucket by IP (`429` + `Retry-After`).
- ADMIN escape hatch: `POST /api/admin/users/{userId}/activate` → `204`. It sets `ACTIVE` but does
  **not** set `email_verified_at`.

## Login

```
POST /api/auth/login
{ "email": "student@example.com", "password": "Password123" }

200 OK
Set-Cookie: CLASEYA_SESSION=<jwt>; Path=/; HttpOnly; Secure; SameSite=Lax
{ "user": { "id": "<uuid>", "name": "Jane", "role": "STUDENT" }, "expiresIn": 3600 }
```

- Authenticates via Spring Security `AuthenticationManager` → `DaoAuthenticationProvider` using
  `AppUserDetailsService` and `BCryptPasswordEncoder`.
- Only `ACTIVE` accounts can authenticate; `PENDING`/`INACTIVE`/`SUSPENDED` fail.
- Any failed attempt → **401** with a generic `Invalid credentials` message (anti-enumeration). The
  SPA offers the activation-resend help for every 401 instead of trying to detect the state.
- On success, `users.last_login_at` is updated (UTC).
- The body never contains the JWT.

## Session and logout

| Endpoint | Behavior |
|---|---|
| `GET /api/auth/me` | Authenticated; returns `{id, name, role}` (no email). The SPA calls it on load. |
| `POST /api/auth/logout` | `204`; clears `CLASEYA_SESSION` (`Max-Age=0`). The JWT remains cryptographically valid until `exp` (no server-side revocation yet). |

## CSRF

- `GET /api/auth/csrf` → `{ "token": "..." }` and an **HttpOnly** `XSRF-TOKEN` cookie.
- Unsafe methods authenticated by cookie require `X-XSRF-TOKEN` (double submit). A missing or stale
  token → **403**.
- The SPA fetches a new token **before every unsafe request** (no cache).
- Requests with `Authorization: Bearer` are exempt (a cross-site page cannot set that header).

## JWT

- **Claims**: `sub` (user id), `jti`, `iat`, `exp`. **No email/name/role**: the backend reloads the
  user and authorities from the DB on every request.
- **Signature**: HMAC-SHA (jjwt) with a 256-bit+ secret from `JWT_SECRET`.
- **Expiration**: `jwt.expiration` seconds (default 3600). No refresh tokens.
- **Browser path**: `JwtAuthenticationFilter` reads `CLASEYA_SESSION`, validates signature + expiry,
  loads the user, and sets the `SecurityContext`.

## Google OAuth

- `POST /api/auth/google` validates the `id_token` (JWKS, `iss`, `aud`, `exp`, `email_verified`).
- New Google accounts start `ACTIVE`; existing password accounts link by verified email; accounts
  `PENDING`/`INACTIVE`/`SUSPENDED` are rejected.
- `GOOGLE_CLIENT_ID` (backend) and `VITE_GOOGLE_CLIENT_ID` (SPA) must be the **same** Web client.

### `origin_mismatch`

Google Identity Services returns `Error 400: origin_mismatch` when the page origin is not listed in
the OAuth client. This is a Cloud Console setting, not an application bug:

1. Google Cloud Console → **APIs & Services** → **Credentials**.
2. Open the **OAuth 2.0 Client ID** of type *Web application*.
3. Under **Authorized JavaScript origins** add every origin that serves the SPA, exactly as the
   browser reports it:
   - `http://localhost:5173` for Vite dev.
   - `http://127.0.0.1:5173` only if you open the app through the IP (Google treats it as a
     different origin).
   - The production origin (for example `https://claseya.example`), with no trailing slash.
4. Save and wait a few minutes for propagation.

The SPA shows the current origin and the warning above the Google button in development, so the
exact string to register is never a guess.

## Deployment topology

The SPA and the API must share a public origin. If the SPA calls a backend on another site, the
`XSRF-TOKEN` cookie (`SameSite=Lax`) is not sent on cross-site requests and every unsafe call —
including both login methods — fails with `403 Access denied`. Vercel proxies `/api/*` to the
backend (`frontend/vercel.json`, `ADR-022`):

- `BACKEND_URL` (Vercel) = backend origin, no trailing slash.
- `VITE_API_URL` empty in Vercel (build-time variable: redeploy after removing it).
- Backend: `FRONTEND_URL=https://<project>.vercel.app` (exact), `COOKIE_SAME_SITE=Lax`,
  `COOKIE_SECURE=true`, and `RATE_LIMIT_TRUST_FORWARDED_HEADERS=true` (Vercel overwrites
  `X-Forwarded-For`; otherwise every user shares one rate-limit bucket).
- Google: register the Vercel origin under Authorized JavaScript origins.
- Evidence uploads (up to 10 MB) traverse Vercel; verify the body limit. If it cuts them (413),
  serve the SPA and the API from subdomains of the same site (`app.example.com` + `api.example.com`,
  where `SameSite=Lax` works without a proxy), lower the file cap, or move evidence to object
  storage. A direct cross-site upload would lose the session cookie.
