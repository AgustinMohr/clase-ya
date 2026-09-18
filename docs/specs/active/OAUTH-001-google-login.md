# OAUTH-001 — Inicio de sesión con Google OAuth

**ID:** OAUTH-001
**Estado:** Activa · en REVIEW · **Carpeta:** `docs/specs/active/`
**Última actualización:** 2026-09-02
**Referencia de producto:** `docs/product/product-requirements.md` (Google OAuth "infaltable").

---

## 1. Objetivo

Permitir el registro e inicio de sesión con **Google** manteniendo el login por email/contraseña
existente (decisión del director). El login con Google entrega el mismo **JWT stateless** de ClaseYa
y respeta las reglas de estado de cuenta vigentes. Es el primer paso de la dirección
"directorio + contacto seguro" (permite que estudiantes/padres y profesores entren sin fricción).

## 2. Contexto (estado real del repositorio)

- Auth actual: `POST /api/auth/register` (email+password, BCrypt, rol STUDENT/TEACHER, cuenta
  **PENDING**) y `POST /api/auth/login` → `{accessToken, tokenType, expiresIn}`. JWT sin estado;
  el filtro recarga el usuario y **solo `status = ACTIVE`** se autentica.
- `User`: `email` único (normalizado minúsculas), `passwordHash` (BCrypt), `role` (STUDENT/TEACHER/
  ADMIN), `status`, `name` (público, opcional, V3), timestamps, `lastLoginAt`. **No hay** campo de
  proveedor externo ni `google_sub`.
- `pom.xml`: sin `oauth2`/`oidc`; sin email. Roles `ROLE_STUDENT/TEACHER/ADMIN`; mapeo único en
  `AppUserDetails`.
- El frontend es una SPA futura (React); hoy la API es consumible por curl/tests.

## 3. Decisiones adoptadas (de la dirección)

- **D-1** Google OAuth se agrega **conviviendo** con el registro/login por email+password.
- **D-2** El flujo técnico recomendado es **Google Identity Services (SPA) → `id_token` → backend**:
  el frontend obtiene el `id_token` de Google y lo envía a ClaseYa; el backend **valida** el token
  (issuer, audience, firma contra JWKS de Google) y emite el JWT de ClaseYa. Evita guardar un
  `client_secret` en el cliente y encaja con la API stateless actual. (Se capturará en ADR durante
  el diseño.)
- **D-3** Dependencia nueva prevista: `spring-security-oauth2-jose` (validación OIDC/JWKS) y
  configuración de issuer de Google por entorno. Sin SDK propietario.

## 4. Modelo de identidad

Nuevas columnas en `users` (migración `V6`):

```
google_sub  varchar(255)  NULL  UNIQUE   -- identificador estable de Google (sub)
```

- `email` sigue siendo único/normalizado y es la identidad lógica de ClaseYa.
- `google_sub` se usa para **vincular** la cuenta de Google con la de ClaseYa.
- No se guardan tokens de Google (el `id_token` es efímero; no persistir).
- No se modifica `passwordHash` ni el flujo password existente.

### Política de vinculación y estado (decidida por el director)

1. **Usuario nuevo (no existe por email ni `google_sub`)**: se crea con el rol indicado en el
   request, `name` desde Google, `email` verificado por Google y **`status = ACTIVE`** (decisión del
   director: Google ya verificó el email). El registro por email+password sigue `PENDING` hasta que
   exista un flujo de activación (fuera de OAUTH-001).
2. **Usuario existente por `google_sub`**: login normal (debe ser `ACTIVE`).
3. **Usuario existente por email (cuenta password) sin `google_sub`**: se **vincula**
   (`google_sub` = sub de Google) y se le permite entrar, **solo si** la cuenta está `ACTIVE` y el
   email coincide exactamente con el email verificado de Google (**opción recomendada, decidida**).
   Nunca se vincula a una cuenta ya vinculada a otro `google_sub`.
4. **Cuenta `PENDING`/`INACTIVE`/`SUSPENDED`**: Google **no** la activa en silencio; se rechaza con
   el mismo tratamiento de "cuenta deshabilitada" (401/403). El usuario debe usar el flujo
   password/activación correspondiente.

## 5. API (conceptual)

### POST /api/auth/google
- Actor: visitante (sin token).
- Request (JSON):
```json
{
  "idToken": "<id_token de Google>",
  "role": "STUDENT"            // requerido solo si la cuenta es nueva
}
```
- Flujo del backend:
  1. Validar `idToken` (firma/issuer/aud/exp; email_verified=true).
  2. Buscar por `google_sub`; si no, por `email` normalizado.
  3. Si no existe → crear cuenta (rol + `name` + `email` + `google_sub`; estado según política
     confirmada). Si existe → verificar estado y vincular si aplica.
  4. Emitir el mismo `LoginResponse` (`accessToken`, `tokenType`, `expiresIn`).
  5. Actualizar `lastLoginAt`.
- Respuesta: igual forma que `/api/auth/login`.
- Errores: 400 (token inválido/malformado/aud/iss), 401 (cuenta deshabilitada / email no verificado
  en Google), 409 (email ya usado por otra cuenta de Google / conflicto de vínculo), 403 (rol no
  permitido si se intenta ADMIN).

No se aceptan `userId`/`studentId` del cliente: la identidad la establece el token validado.

## 6. Seguridad

- Validación estricta del `id_token` contra JWKS de Google (issuer configurado por entorno:
  `https://accounts.google.com`), `aud` = client id de Google (config), expiración y
  `email_verified`.
- Sin `client_secret` en el frontend (flujo ID token). `client_id` de Google por entorno.
- No loguear `id_token` ni payload completo.
- No aceptar tokens de otros emisores.
- Los roles siguen siendo STUDENT/TEACHER/ADMIN (ADMIN no auto-registrable).
- Tests de vinculación/account-takeover: no se permite vincular email de una cuenta ajena cuando la
  cuenta ya está activa y no hay coincidencia de email verificado.

## 7. Invariantes

- INV-1 `google_sub` es único (un Google account = a lo sumo una cuenta ClaseYa).
- INV-2 Un usuario puede tener a lo sumo un `google_sub`.
- INV-3 El `email` de la cuenta vinculada coincide con el email verificado de Google.
- INV-4 Google no cambia el estado de una cuenta deshabilitada/PENDING existente.
- INV-5 No se persisten tokens de acceso/refresh de Google.
- INV-6 El login con Google produce el mismo JWT de ClaseYa (claims y vigencia iguales).

## 8. Datos afectados

- Migración `V6__google_oauth.sql`: `ALTER TABLE users ADD COLUMN google_sub varchar(255);` +
  `CREATE UNIQUE INDEX uq_users_google_sub ON users (google_sub);`
- Config `application.yml`: `oauth.google.client-id` y `oauth.google.issuer` (por entorno; sin
  secretos en el repo).

## 9. Tests (mapeo AC)

- AC-001 Sin token → 401 (los endpoints de auth siguen públicos, el resto igual).
- AC-002 `idToken` de Google válido de un **usuario nuevo** → crea cuenta (rol del request) y
  devuelve `LoginResponse`.
- AC-003 El mismo Google (mismo `sub`) vuelve a iniciar sesión → **misma cuenta**, sin duplicar.
- AC-004 Google de un **email ya registrado por password (ACTIVE)** → se vincula y loguea.
- AC-005 Google de un email de cuenta **PENDING/INACTIVE/SUSPENDED** → no se activa; respuesta de
  cuenta deshabilitada.
- AC-006 `idToken` **expirado / firma inválida / issuer distinto / audience distinta** → 400/401 sin
  efectos.
- AC-007 `idToken` con `email_verified=false` → rechazado.
- AC-008 intento de registrar rol **ADMIN** por Google → 403.
- AC-009 `google_sub` duplicado (intento de vincular el mismo Google a dos cuentas) → 409 /
  constraint DB.
- AC-010 respuesta y JWT no exponen `google_sub` ni tokens; mismo shape que login.
- AC-011 (regresión) login/registro por password sigue funcionando.
- AC-012 (nuevo usuario vía Google) estado resultante según la política confirmada (ACTIVE o PENDING).

## 10. Out of scope

Refresh tokens, logout revocación, otros proveedores (GitHub/Facebook), MFA, `userinfo` API de
Google, botón/frontend (la SPA integrará Google Identity Services en otra fase), vínculo de cuentas
por UI (solo automático por email, según política), activación de cuentas PENDING.

## 11. Change budget (estimación)

- **CREATE**: migración `V6__google_oauth.sql`; módulo `oauth`/extensión de `auth`
  (controller/service/dto `GoogleLoginRequest`); validador/decoder de `id_token` (JwtDecoder
  OIDC); tests `OAuthGoogleIntegrationTest` + unit del validador; ADR (flujo ID-token y
  vinculación) en la fase de diseño.
- **MODIFY**: `User` (+`googleSub`), repositorio (+lookups por sub/email), `SecurityConfig`
  (ruta pública `/api/auth/google`), `application.yml`, docs (auth/security/product).
- **DEPENDENCIAS**: `spring-security-oauth2-jose` (justificado). Sin SDK propietario.
- **DELETE**: ninguno.

## 12. Definition of Done

- [ ] Spec aprobada (decisiones de estado/vinculación resueltas por el director: nuevos por Google =
  ACTIVE; vinculación por email verificado).
- [ ] ADR del flujo OIDC/ID-token y de vinculación por email creado.
- [ ] Migración `V6` aplicada (sin tocar `V1..V5`); `google_sub` único.
- [ ] Validación de `id_token` (firma/iss/aud/exp/email_verified) implementada y testeada.
- [ ] Nuevos usuarios por Google, vinculación y manejo de estados según política aprobada.
- [ ] Regresión del login/registro password verde.
- [ ] Tests de seguridad (token inválido/expirado/issuer/aud, duplicados, account-takeover) OK.
- [ ] `mvn -B clean test` en verde; docs actualizadas; change budget respetado; reportado al director.
