# ClaseYa — Authentication & Authorization (Phase 2)

Stateless, JWT-based authentication and role-based authorization on top of the Phase 1 model.
Users register with `email` + `password` + `role`, accounts start **PENDING** and only become
able to authenticate once an admin flips them to **ACTIVE** (activation flow is a future phase).
Passwords are hashed with BCrypt; tokens are short-lived HMAC-signed JWTs.

Browsers keep the JWT in an **HttpOnly session cookie** (`CLASEYA_SESSION`) and protect unsafe
requests with a CSRF token (SEC-002). The integration suite/tooling may still send
`Authorization: Bearer <jwt>`. The SPA never sees the token.

## Quick path

1. Start PostgreSQL and run the app (`mvn spring-boot:run`).
2. `POST /api/auth/register` → user created with `status=PENDING`.
3. Set the account `ACTIVE` (admin/seed — no activation endpoint yet).
4. `GET /api/auth/csrf` → `{ token }` (browser login needs it).
5. `POST /api/auth/login` → `Set-Cookie: CLASEYA_SESSION` + `{ user, expiresIn }`.
6. Browser: call protected endpoints with `credentials: 'include'` and `X-XSRF-TOKEN` on writes.
   Integration tests/tooling: `Authorization: Bearer <jwt>` (no CSRF).

## Register flow

```
POST /api/auth/register
{ "email": "student@example.com", "password": "Password123", "role": "STUDENT" }

201 Created
{ "id": "<uuid>", "email": "student@example.com", "role": "STUDENT", "status": "PENDING" }
```

- Email is normalized to lowercase before save and lookup.
- Password is BCrypt-hashed; the raw value is never stored or logged.
- `role` must be `STUDENT` or `TEACHER`. `ADMIN` **cannot self-register** (400).
- Duplicate email → **409**. Validation errors → **400** with `fieldErrors`.

## Login flow

```
POST /api/auth/login
{ "email": "student@example.com", "password": "Password123" }

200 OK
Set-Cookie: CLASEYA_SESSION=<jwt>; Path=/; HttpOnly; Secure; SameSite=Lax
{ "user": { "id": "<uuid>", "name": "Jane", "role": "STUDENT" }, "expiresIn": 3600 }
```

- Authenticates via Spring Security `AuthenticationManager` → `DaoAuthenticationProvider`
  using `AppUserDetailsService` (lookup by email) and `BCryptPasswordEncoder`.
- Only `ACTIVE` accounts can authenticate; `PENDING`/`INACTIVE`/`SUSPENDED` fail.
- Any failed attempt → **401** with a generic `Invalid credentials` message (anti-enumeration).
- On success, `users.last_login_at` is updated (UTC).
- The body never contains the JWT. Browsers use the cookie; the integration suite may still use a
  Bearer token, but the product SPA does not.

### Current session and logout

| Endpoint | Behavior |
|---|---|
| `GET /api/auth/me` | Authenticated; returns `{id, name, role}` (no email). The SPA calls it on load. |
| `POST /api/auth/logout` | `204`; clears `CLASEYA_SESSION` (`Max-Age=0`). The JWT remains cryptographically valid until `exp` (no server-side revocation yet). |

### CSRF

- `GET /api/auth/csrf` → `{ "token": "..." }` and an **HttpOnly** `XSRF-TOKEN` cookie.
- Unsafe methods authenticated by cookie require `X-XSRF-TOKEN` (double submit). A missing or stale
  token → **403**.
- The SPA fetches a new token **before every unsafe request** (no cache): the server rotates or
  clears the cookie on writes.
- Requests with `Authorization: Bearer` are exempt from CSRF (a cross-site page cannot set that
  header); this is what keeps the integration suite working.

## JWT

- **Claims**: `sub` (user id), `jti` (unique per token), `iat`, `exp`. **No email/name/role**: the
  backend reloads the user and authorities from the DB on every request.
- **Signature**: HMAC-SHA (jjwt) with a 256-bit+ secret from config.
- **Expiration**: `jwt.expiration` seconds (default 3600). No refresh tokens in this phase.
- **Browser path**: `JwtAuthenticationFilter` reads the `CLASEYA_SESSION` cookie, validates
  signature + expiry, loads the user, and sets the `SecurityContext`.
- **Integration-test path**: the same filter accepts `Authorization: Bearer <jwt>` as a fallback.
- On invalid/expired/tampered tokens the request stays unauthenticated so Spring Security returns
  **401**.
- The user is re-loaded from the DB on each request, so **suspended/inactive users are blocked
  immediately**, even with a previously valid token.

## Authorization by role

| Endpoint | Access |
|----------|--------|
| `POST /api/auth/register`, `/login`, `/google`, `/logout`; `GET /api/auth/csrf` | public |
| `GET /api/auth/me` | any authenticated user |
| `GET /api/test/public` | public |
| `GET /api/test/authenticated` | any authenticated user |
| `GET /api/test/student` | `STUDENT` |
| `GET /api/test/teacher` | `TEACHER` |
| `GET /api/test/admin` | `ADMIN` |
| any other `/api/**` | authenticated (default) |

Roles are stored as `STUDENT`/`TEACHER`/`ADMIN` (Phase 1 enum) and exposed to Spring Security as
`ROLE_STUDENT`/`ROLE_TEACHER`/`ROLE_ADMIN` (single convention, one mapping point in
`AppUserDetails`). `hasRole("STUDENT")` matches `ROLE_STUDENT`.

The `/api/test/*` endpoints are **temporary** (for validating security) and will be removed in a
later phase.

## Protecting a new endpoint

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers(HttpMethod.POST, "/api/teachers/**").hasRole("TEACHER")
    .anyRequest().authenticated())
```

And resolve the caller from the security context (never trust a client-sent user id):

```java
UUID currentUserId = currentUser.id();
```

## Password handling

- `BCryptPasswordEncoder` (Spring Security). No MD5/SHA-256/AES/Base64 hacks.
- Only `password_hash` exists in the schema; hashing is one-way, never "decrypted".
- Logs never contain passwords, hashes, JWTs, or OAuth tokens.

## Configuration (environment variables)

| Variable | Default | Purpose |
|----------|---------|---------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/claseya` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `claseya` / `claseya` | DB credentials |
| `JWT_SECRET` | dev-only placeholder | JWT signing key (≥ 256-bit). **Must be overridden in any non-dev environment; never committed.** |
| `JWT_EXPIRATION` | `3600` | Access token lifetime in seconds |
| `COOKIE_SECURE` | `true` | `Secure` flag for the session/CSRF cookies. Set `false` **only** for local HTTP dev. |
| `COOKIE_SAME_SITE` | `Lax` | `Lax` for a same-site SPA/API; `None` (+ `Secure`) if the API is on another site. |
| `FRONTEND_URL` | `http://localhost:3000` | Allowed CORS origin(s), comma-separated |

Example (local HTTP):

```powershell
$env:JWT_SECRET="<long-random-secret>"
$env:JWT_EXPIRATION="3600"
$env:FRONTEND_URL="http://localhost:5173"
$env:COOKIE_SECURE="false"
$env:COOKIE_SAME_SITE="Lax"
mvn spring-boot:run
```

## Security decisions

- **Stateless**: `SessionCreationPolicy.STATELESS`. No HTTP session on the server; the signed JWT
  travels in the `CLASEYA_SESSION` HttpOnly cookie (browser) or `Authorization: Bearer` (integration tests).
- **CSRF enabled**: cookie-authenticated unsafe methods require `X-XSRF-TOKEN` (double submit).
  Bearer requests are exempt (`BearerAwareCsrfMatcher`).
- **Cookies**: `HttpOnly`, `Secure` (except local HTTP), `SameSite` configurable, `Path=/`.
  The CSRF cookie is HttpOnly too; the SPA gets the token from the `/csrf` body.
- **Headers**: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
  `Referrer-Policy: no-referrer`, `Permissions-Policy` (camera/mic/geo/payment disabled).
- **CORS**: only configured origins (no `*`), with `Authorization`, `Content-Type` and
  `X-XSRF-TOKEN` headers and credentials, overridable via `FRONTEND_URL`.
- **Anti-enumeration**: a single generic message on all authentication failures; no per-field
  "user not found" vs "wrong password".
- **Errors**: consistent `ApiError` JSON `{ timestamp, status, error, message, path, fieldErrors? }`
  with 400/401/403/409; no stack traces or internal details.
- **Authorization enforcement**: roles enforced via `hasRole` on the filter chain; the
  `AccessDeniedHandler` returns 403 JSON, the `AuthenticationEntryPoint` returns 401 JSON.

## Testing the API

```bash
# 1. CSRF token (also stores the HttpOnly cookie in cookies.txt)
curl -c cookies.txt -b cookies.txt http://localhost:8080/api/auth/csrf
# {"token":"<csrf>"}

# optional: register (also needs the CSRF token; the account starts PENDING)
curl -i -b cookies.txt -c cookies.txt -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: <csrf>" \
  -d '{"email":"student@example.com","password":"Password123","role":"STUDENT"}'

# 2. login (send the token from the previous response)
curl -i -c cookies.txt -b cookies.txt -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: <csrf>" \
  -d '{"email":"student@example.com","password":"Password123"}'
# Set-Cookie: CLASEYA_SESSION=...; HttpOnly; SameSite=Lax
# {"user":{"id":"...","role":"STUDENT"},"expiresIn":3600}

# 3. current session
curl -b cookies.txt -c cookies.txt http://localhost:8080/api/auth/me

# 4. a write: fetch a fresh CSRF token first, then send it
curl -b cookies.txt -c cookies.txt http://localhost:8080/api/auth/csrf
curl -b cookies.txt -c cookies.txt -X POST http://localhost:8080/api/auth/logout \
  -H "X-XSRF-TOKEN: <csrf>"

# 5. Integration tests may keep using a Bearer token (no CSRF)
curl http://localhost:8080/api/test/authenticated -H "Authorization: Bearer <jwt>"
```

The automated suite (`mvn test`, Testcontainers-backed) covers register/login/JWT/roles/password,
the cookie session, `/me`, logout, CSRF enforcement, and expired/tampered/wrong-key tokens.

## Google sign-in (OAUTH-001)

`POST /api/auth/google` accepts a Google `id_token` (Google Identity Services on the SPA). The
backend validates signature/issuer/audience/expiry and `email_verified`, links accounts by
`users.google_sub` (unique), and returns the same `LoginResponse` plus the `CLASEYA_SESSION` cookie.
New Google accounts start
`ACTIVE`; existing password accounts are linked only when ACTIVE and their email matches the
verified Google email; disabled/PENDING accounts are never activated by Google. Config:
`oauth.google.issuer` / `oauth.google.client-id` (env `GOOGLE_ISSUER` / `GOOGLE_CLIENT_ID`).

## Not implemented (future)

Refresh tokens, email verification, password reset, MFA, other social providers (Google is done),
profiles (post-contact), Calendly, notifications, and the rest of the post-Phase modules.
