# ADR-015 — Google OAuth (flujo ID-token) y vinculación de cuentas

**Estado:** Aceptado (OAUTH-001, fase de diseño; implementación pendiente).

## Contexto
ClaseYa requiere inicio de sesión con Google ("infaltable" según la dirección de producto) junto al
login por email/contraseña actual (decisión del director). El cliente será una SPA (React), la API
es stateless (JWT) y no queremos guardar un `client_secret` en el frontend.

## Decisión
- **Flujo**: el frontend usa **Google Identity Services** y envía el `id_token` a
  `POST /api/auth/google`; el backend **valida** el token (firma contra JWKS de Google, `iss`,
  `aud` = Google client id de la app, `exp`, `email_verified`) y emite el JWT de ClaseYa. No se usa
  el flujo Authorization Code con `client_secret` en el SPA ni se persisten tokens de Google.
- **Identidad**: nueva columna única `users.google_sub` (varchar) para vincular el Google account
  con la cuenta ClaseYa; `email` sigue siendo la identidad lógica.
- **Política (decidida por el director)**: nuevos usuarios por Google → `ACTIVE` (email verificado);
  cuenta password existente con el mismo email verificado → se vincula por email; cuentas
  `PENDING/INACTIVE/SUSPENDED` nunca se activan en silencio.
- **Dependencia**: `spring-security-oauth2-jose` (decodificador/validación OIDC). Sin SDK propietario.

## Consecuencias
- + Sin secretos de cliente en el frontend; consistente con la API stateless y con el SPA futuro.
- + Vínculo estable por `google_sub` (aunque Google cambie el email) + dedupe por email verificado.
- + Reutiliza el pipeline de JWT/roles existente (`AppUserDetails`, `CurrentUser`).
- - Requiere configurar issuer/client-id de Google por entorno; la validación real depende del JWKS
  de Google (los tests usan un emisor/decoder simulado con clave propia).
- - Nuevas cuentas por Google nacen `ACTIVE` mientras el registro password sigue `PENDING` (se
  documenta; un flujo de activación unificado queda como trabajo futuro).
