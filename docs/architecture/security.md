# ClaseYa — Seguridad (tal como está implementada)

El estado de cada regla es **IMPLEMENTADO** salvo que diga **FUTURO**. Documenta el comportamiento
real; ver el paquete `security` y `SecurityConfig`.

## Autenticación

- JWT sin estado. `POST /api/auth/register` (solo STUDENT/TEACHER; ADMIN no puede
  auto-registrarse) crea una cuenta `PENDING`; `POST /api/auth/login` autentica email+password a
  través del `AuthenticationManager` de Spring Security → `DaoAuthenticationProvider` con
  `AppUserDetailsService` + `BCryptPasswordEncoder`, y devuelve
  `{accessToken, tokenType:"Bearer", expiresIn}`.
- **Ciclo de cuenta**: solo `status = ACTIVE` puede autenticarse. Las cuentas
  `PENDING`/`INACTIVE`/`SUSPENDED` se tratan como deshabilitadas (`AppUserDetails.isEnabled()`),
  por lo que un token ya emitido se rechaza en el siguiente request (el filtro recarga el usuario
  en cada petición).
- **Decisiones de seguridad IMPLEMENTADAS**: sin sesión HTTP
  (`SessionCreationPolicy.STATELESS`); CSRF deshabilitado (API sin cookies/sesiones); un único
  mensaje genérico de login (anti-enumeración).
- **FUTURO**: refresh tokens, verificación de email, flujo de activación (el registro queda PENDING
  hasta que exista un mecanismo de activación), password reset, MFA, otros proveedores sociales.
- **Google OAuth (OAUTH-001, implementado)**: `POST /api/auth/google` valida el `id_token` de
  Google (firma/JWKS, `iss`, `aud`, `exp`, `email_verified`) y vincula por `users.google_sub`
  (único). Nuevos por Google = `ACTIVE`; una cuenta password solo se vincula si está `ACTIVE` y su
  email coincide con el email verificado de Google; cuentas `PENDING`/`INACTIVE`/`SUSPENDED` nunca
  se activan vía Google. Sin tokens de Google persistidos; sin `client_secret` en el SPA.

## Contraseñas y secretos

- Contraseñas hasheadas con BCrypt; solo se guarda `password_hash`. Nunca se loguea ni se devuelve.
- `JWT_SECRET`/`JWT_EXPIRATION`/`DB_*`/`FRONTEND_URL` vienen de variables de entorno.
  `application.yml` tiene defaults **solo de desarrollo**; no son secretos de producción.
- Claims del JWT mínimos: `sub` (id de usuario), `email`, `role`, `iat`, `exp`. Nada sensible
  (hashes/tokens) dentro del token.

## Autorización

Authorities: `ROLE_STUDENT`, `ROLE_TEACHER`, `ROLE_ADMIN` (mapeo único en `AppUserDetails`).

| Área | Regla (SecurityConfig, en orden) |
|------|----------------------------------|
| `POST /api/auth/**`, `GET /api/test/public` | permitAll |
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

## CORS

Restringido a `cors.allowed-origins` (`FRONTEND_URL`, default `http://localhost:3000`); métodos,
headers `Authorization`/`Content-Type`; credenciales permitidas; sin wildcard.

## FUTURO (documentado, no implementado)

Rate limiting/anti-spam; refresh tokens; verificación de email/activación; password reset; MFA;
social login; panel de moderación admin; transporte WebSockets (reutiliza los services existentes).
