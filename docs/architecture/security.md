# ClaseYa — Seguridad (tal como está implementada)

El estado de cada regla es **IMPLEMENTADO** salvo que diga **FUTURO**. Documenta el comportamiento
real; ver el paquete `security` y `SecurityConfig`.

## Autenticación

- JWT sin estado. `POST /api/auth/register` (solo STUDENT/TEACHER; ADMIN no puede
  auto-registrarse) crea una cuenta `PENDING`; `POST /api/auth/login` autentica email+password a
  través del `AuthenticationManager` de Spring Security → `DaoAuthenticationProvider` con
  `AppUserDetailsService` + `BCryptPasswordEncoder`, y devuelve `{user, expiresIn}` + cookie
  `CLASEYA_SESSION` (`HttpOnly`, `Secure`, `SameSite`). El JWT no viaja en el body.
- **Ciclo de cuenta**: solo `status = ACTIVE` puede autenticarse. Las cuentas
  `PENDING`/`INACTIVE`/`SUSPENDED` se tratan como deshabilitadas (`AppUserDetails.isEnabled()`),
  por lo que un token ya emitido se rechaza en el siguiente request (el filtro recarga el usuario
  en cada petición).
- **Decisiones de seguridad IMPLEMENTADAS**: sin sesión HTTP de servidor
  (`SessionCreationPolicy.STATELESS`); cookie de sesión `HttpOnly` para el navegador (Bearer queda
  para la suite de integración/tooling); CSRF por doble submit (`X-XSRF-TOKEN`) para requests
  autenticados por cookie;
  `GET /api/auth/me` resuelve el rol sin claims de rol; un único mensaje genérico de login
  (anti-enumeración).
- **AUTH-001 (implementado)**: el registro por contraseña queda `PENDING` hasta que el dueño abre
  un enlace de activación de un solo uso (token SHA-256, TTL 24 h). `POST /api/auth/activate`,
  reenvío genérico `202`, y activación manual por ADMIN. Sin SMTP el registro responde `503` y no
  crea la cuenta.
- **FUTURO**: refresh tokens, revocación server-side de JWT (usar `jti`), password reset, MFA,
  otros proveedores sociales.
- **Google OAuth (OAUTH-001, implementado)**: `POST /api/auth/google` valida el `id_token` de
  Google (firma/JWKS, `iss`, `aud`, `exp`, `email_verified`) y vincula por `users.google_sub`
  (único). Nuevos por Google = `ACTIVE`; una cuenta password solo se vincula si está `ACTIVE` y su
  email coincide con el email verificado de Google; cuentas `PENDING`/`INACTIVE`/`SUSPENDED` nunca
  se activan vía Google. Sin tokens de Google persistidos; sin `client_secret` en el SPA.

## Contraseñas y secretos

- Contraseñas hasheadas con BCrypt; solo se guarda `password_hash`. Nunca se loguea ni se devuelve.
- Política de alta (AUTH-002): mínimo 10 caracteres, al menos una letra y un dígito, máximo 72 bytes
  UTF-8 (límite real de BCrypt). El registro responde **202 genérico** para no enumerar cuentas.
- **Contenedor (OPS-002):** la imagen final corre como usuario `app` (no-root), declara
  `HEALTHCHECK` contra `/actuator/health` y arranca la JVM con `-XX:MaxRAMPercentage=75`. La
  configuración local (`application-local.yml`, `.env`) queda fuera del contexto de build.
- `JWT_SECRET`/`JWT_EXPIRATION`/`DB_*`/`FRONTEND_URL` vienen de variables de entorno.
  `application.yml` tiene defaults **solo de desarrollo**; no son secretos de producción.
- Claims del JWT mínimos: `sub` (id de usuario), `jti` (único), `iat`, `exp`. **Sin email, nombre
  ni rol**: el backend recarga usuario y authorities desde la base en cada request. `jti` habilita
  una futura lista de revocación.
- Cookies: `CLASEYA_SESSION` (`HttpOnly`, `Secure` configurable, `SameSite` configurable) y
  `XSRF-TOKEN` (`HttpOnly`) para el doble submit. Variables `COOKIE_SECURE` /
  `COOKIE_SAME_SITE`; ver ADR-020. En producción el SPA y el API se sirven bajo el mismo origen
  (proxy `/api` en Vercel, ADR-022): una cookie `SameSite=Lax` cross-site no llega y el login
  responde `403 Access denied`.
- Cabeceras del API: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
  `Referrer-Policy: no-referrer` y `Permissions-Policy` con cámara/micrófono/geolocalización/pagos
  deshabilitados. El HTML versiona `_headers` y una CSP (SEC-002).

## Autorización

Authorities: `ROLE_STUDENT`, `ROLE_TEACHER`, `ROLE_ADMIN` (mapeo único en `AppUserDetails`).

| Área | Regla (SecurityConfig, en orden) |
|------|----------------------------------|
| `POST /api/auth/**`, `GET /actuator/health` | permitAll |
| Lecturas del catálogo académico (`GET /api/universities/**`, `academic-units`, `careers`, `subjects`, `career-subjects`) | públicas |
| Escrituras del catálogo (POST/PUT/DELETE sobre lo anterior) | `ADMIN` |
| `GET /api/teachers`, `GET /api/teachers/{id}` (descubrimiento público) | públicas (solo VERIFIED+ACTIVE, aplicado en backend) |
| `/api/teachers/me*` y rutas de perfil propio | `TEACHER` o `ADMIN` |
| `/api/students/**` | `STUDENT` o `ADMIN` |
| `/api/favorites/**` | `STUDENT` |
| `POST /api/conversations` | `STUDENT` |
| `/api/conversations/**` | `STUDENT` o `TEACHER` (validación de participante adentro) |
| cualquier otro `/api/**` | autenticado |

Los matchers de seguridad se evalúan en orden (las rutas literales `/me` preceden al wildcard
público).

## Ownership

- La identidad que actúa siempre es `CurrentUser.id()` desde el `SecurityContext`; el cliente nunca
  envía `userId`/`studentId`/`senderId`/`participantIds`.
- El alcance del recurso es por usuario autenticado (`/me`, "mis favoritos", "mis conversaciones");
  los borrados son por dueño+objetivo, nunca por un id crudo de fila expuesto a otros.
- Quien no participa de una conversación recibe **404** (no se revela su existencia).

## Visibilidad pública (aplicada en backend)

La búsqueda/detalle/favoritos/contacto de profesores solo considera profesores con
`verificationStatus = VERIFIED` **y** `user.status = ACTIVE`. Cualquier otro caso se comporta como
404. La regla se aplica en el backend, nunca delegada al frontend.

## Datos sensibles

Los DTOs públicos y personales nunca incluyen emails, direcciones, coordenadas exactas, tokens,
hashes ni campos internos. La ubicación exacta solo se usa internamente para distancia geo.

## Errores

401 → `RestAuthenticationEntryPoint` (`ApiError` JSON); 403 → `RestAccessDeniedHandler`;
errores de método/validación/recurso → `GlobalExceptionHandler`. Sin stack traces/SQL.
SUSPENDED/INACTIVE no pueden autenticarse, por lo que las rutas protegidas responden 401; los
datos históricos (conversaciones, favoritos, perfiles) nunca se borran para cuentas deshabilitadas.

## Rate limiting y anti-abuso (RATE-001, IMPLEMENTADO)

Limitador de token buckets **en memoria y por instancia** (Bucket4j `bucket4j_jdk17-core`), aplicado
en un filtro de Spring Security después del JWT y antes de la autorización:

| Scope | Clave | Default |
|---|---|---|
| Login | IP | 30/min |
| Login | email normalizado | 5 fallos/15 min (se resetea con login exitoso) |
| Registro | IP | 20/h |
| Google login | IP | 30/min |
| Crear conversación | usuario | 10/h |
| Enviar mensaje | usuario | 60/min |
| Subir evidencia | usuario | 20/h |

- Las requests bloqueadas reciben **429** con `ApiError` genérico, `Retry-After` y `Cache-Control:
  no-store`.
- Las claves se derivan con SHA-256 + salt por proceso; no se persisten ni se loguean IPs/emails
  crudos (solo scope + hash truncado).
- `security.rate-limit.trust-forwarded-headers` debe quedar en `false` si la app puede recibir
  tráfico directo; activarlo solo detrás de un proxy que saneé el header.
- Si la evaluación del limitador falla, la request se permite y se registra `ERROR` (fail-open); el
  limitador es defensa en profundidad.
- **Limitación:** al ser en memoria, con N instancias el límite efectivo puede ser N veces el
  configurado. La versión distribuida (Redis) queda fuera de alcance hasta una spec futura.

## CORS

Restringido a `cors.allowed-origins` (`FRONTEND_URL`, default `http://localhost:3000`); métodos,
headers `Authorization`/`Content-Type`; credenciales permitidas; sin wildcard.

## FUTURO (documentado, no implementado)

Rate limiting **distribuido** (Redis); refresh tokens; verificación de email/activación; password
reset; MFA; social login; panel de moderación admin; transporte WebSockets (reutiliza los services
existentes).
