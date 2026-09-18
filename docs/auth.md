# ClaseYa — Authentication & Authorization (Phase 2)

Stateless, JWT-based authentication and role-based authorization on top of the Phase 1 model.
Users register with `email` + `password` + `role`, accounts start **PENDING** and only become
able to authenticate once an admin flips them to **ACTIVE** (activation flow is a future phase).
Passwords are hashed with BCrypt; tokens are short-lived HMAC-signed JWTs.

## Quick path

1. Start PostgreSQL and run the app (`mvn spring-boot:run`).
2. `POST /api/auth/register` → user created with `status=PENDING`.
3. Set the account `ACTIVE` (admin/seed — no activation endpoint yet).
4. `POST /api/auth/login` → `{ accessToken, tokenType, expiresIn }`.
5. Call protected endpoints with `Authorization: Bearer <token>`.

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
{ "accessToken": "<jwt>", "tokenType": "Bearer", "expiresIn": 3600 }
```

- Authenticates via Spring Security `AuthenticationManager` → `DaoAuthenticationProvider`
  using `AppUserDetailsService` (lookup by email) and `BCryptPasswordEncoder`.
- Only `ACTIVE` accounts can authenticate; `PENDING`/`INACTIVE`/`SUSPENDED` fail.
- Any failed attempt → **401** with a generic `Invalid credentials` message (anti-enumeration).
- On success, `users.last_login_at` is updated (UTC).

## JWT

- **Claims**: `sub` (user id), `email`, `role`, `iat`, `exp`. Nothing sensitive.
- **Signature**: HMAC-SHA (jjwt) with a 256-bit+ secret from config.
- **Expiration**: `jwt.expiration` seconds (default 3600). No refresh tokens in this phase.
- **Request path**: header `Authorization: Bearer <jwt>` → `JwtAuthenticationFilter` validates
  signature + expiry, loads the user, and sets the `SecurityContext`. On invalid/expired/tampered
  tokens the request stays unauthenticated so Spring Security returns **401**.
- The user is re-loaded from the DB on each request, so **suspended/inactive users are blocked
  immediately**, even with a previously valid token.

## Authorization by role

| Endpoint | Access |
|----------|--------|
| `POST /api/auth/**` | public |
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
| `FRONTEND_URL` | `http://localhost:3000` | Allowed CORS origin(s), comma-separated |

Example:

```powershell
$env:JWT_SECRET="<long-random-secret>"
$env:JWT_EXPIRATION="3600"
$env:FRONTEND_URL="http://localhost:3000"
mvn spring-boot:run
```

## Security decisions

- **Stateless**: `SessionCreationPolicy.STATELESS`, JWT in the `Authorization` header. No HTTP
  sessions.
- **CSRF disabled**: the API is cookie/session-free and uses a Bearer header, so CSRF does not
  apply. Revisit if session-based flows are ever introduced.
- **CORS**: only configured origins (no `*`), with `Authorization`/`Content-Type` headers and
  credentials, overridable via `FRONTEND_URL` for other environments.
- **Anti-enumeration**: a single generic message on all authentication failures; no per-field
  "user not found" vs "wrong password".
- **Errors**: consistent `ApiError` JSON `{ timestamp, status, error, message, path, fieldErrors? }`
  with 400/401/403/409; no stack traces or internal details.
- **Authorization enforcement**: roles enforced via `hasRole` on the filter chain; the
  `AccessDeniedHandler` returns 403 JSON, the `AuthenticationEntryPoint` returns 401 JSON.

## Testing the API

```bash
# register
curl -i -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"student@example.com","password":"Password123","role":"STUDENT"}'

# activate (no endpoint yet: do it directly in the DB or via seed)
# UPDATE users SET status='ACTIVE' WHERE email='student@example.com';

# login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"student@example.com","password":"Password123"}'

# protected endpoint with token
curl http://localhost:8080/api/test/authenticated \
  -H "Authorization: Bearer <token>"
```

The automated suite (`mvn test`, Testcontainers-backed) covers register/login/JWT/roles/password,
including expired, tampered, and wrong-key tokens, duplicate email, and suspended/inactive/pending
accounts.

## Google sign-in (OAUTH-001)

`POST /api/auth/google` accepts a Google `id_token` (Google Identity Services on the SPA). The
backend validates signature/issuer/audience/expiry and `email_verified`, links accounts by
`users.google_sub` (unique), and returns the same `LoginResponse`. New Google accounts start
`ACTIVE`; existing password accounts are linked only when ACTIVE and their email matches the
verified Google email; disabled/PENDING accounts are never activated by Google. Config:
`oauth.google.issuer` / `oauth.google.client-id` (env `GOOGLE_ISSUER` / `GOOGLE_CLIENT_ID`).

## Not implemented (future)

Refresh tokens, email verification, password reset, MFA, other social providers (Google is done),
profiles (post-contact), Calendly, notifications, and the rest of the post-Phase modules.
