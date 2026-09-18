# PLAN — OAUTH-001 · Inicio de sesión con Google

**Fuente:** `docs/specs/active/OAUTH-001-google-login.md` · **Estado:** para aprobación del
director · **No implementa nada todavía.**

---

## 1. Objetivo

Agregar login/registro con Google conviviendo con el login por email+password: el frontend (SPA
futura) envía el `id_token` de Google; el backend lo valida y emite el mismo JWT stateless de
ClaseYa. Nuevos usuarios por Google quedan **ACTIVE**; cuentas password con el mismo email
verificado se vinculan por `google_sub`.

## 2. Estado actual relevante

- Auth: `register`/`login` password (BCrypt, cuentas PENDING), JWT stateless, `AppUserDetails`
  (solo ACTIVE autentica), `CurrentUser`. `User` sin proveedor externo. Migraciones hasta `V5`.
- Sin `oauth2`/`oidc` en `pom.xml`. Frontend no existe (API consumible por tests/curl).

## 3. Decisiones (cerradas)

- D-1 Convive con login password. D-2 Flujo **ID-token → backend valida** (Google Identity
  Services en el SPA); sin `client_secret` en frontend. D-3 Dependencia `spring-security-oauth2-jose`.
- Política: nuevo por Google → ACTIVE; vínculo por email verificado si la cuenta password está
  ACTIVE; PENDING/INACTIVE/SUSPENDED nunca se activan vía Google. `google_sub` único.

## 4. Flujo

```
SPA (Google Identity Services) → id_token
 → POST /api/auth/google { idToken, role? }      (role solo si la cuenta es nueva)
 → Backend valida (firma/JWKS, iss, aud=client-id, exp, email_verified)
 → Busca por google_sub; si no, por email
   · no existe            → crea User (google_sub, email, name, role, status=ACTIVE)
   · existe por google_sub → login (debe ser ACTIVE)
   · existe por email password ACTIVE → vincula google_sub y login
   · cuenta PENDING/INACTIVE/SUSPENDED → rechaza (deshabilitada)
 → actualiza lastLoginAt → LoginResponse {accessToken, tokenType, expiresIn}
```

## 5. Archivos (file-by-file)

| PATH | Tipo | Propósito / cambios |
|------|------|---------------------|
| `src/main/resources/db/migration/V6__google_oauth.sql` | CREATE | `ALTER TABLE users ADD COLUMN google_sub varchar(255); CREATE UNIQUE INDEX uq_users_google_sub ON users (google_sub);` |
| `src/main/java/com/claseya/model/User.java` | MODIFY | Campo `googleSub` (`@Column(name="google_sub")`, nullable). |
| `src/main/java/com/claseya/user/UserRepository.java` | MODIFY | `Optional<User> findByGoogleSub(String)`; `Optional<User> findByEmail(String)` (ya existe). |
| `src/main/java/com/claseya/oauth/dto/GoogleLoginRequest.java` | CREATE | `{ @NotBlank idToken; UserRole role; }` (role opcional para nueva cuenta). |
| `src/main/java/com/claseya/oauth/service/GoogleIdTokenVerifier.java` | CREATE | Valida id_token (decoder inyectado) y claims: `iss` configurado, `aud`=client-id, `email_verified`, `sub`/`email`. Devuelve `{sub,email,name}`. |
| `src/main/java/com/claseya/oauth/service/GoogleAuthService.java` | CREATE | Orquesta: verificar → buscar/crear/vincular según política → emitir JWT (reusa JwtService/AuthService internals) → `LoginResponse`. |
| `src/main/java/com/claseya/oauth/config/GoogleOAuthConfig.java` | CREATE | Beans: `JwtDecoder` (Nimbus `withIssuerLocation` sobre issuer configurado) y `GoogleIdTokenVerifier` (issuer/aud desde `application.yml`). |
| `src/main/java/com/claseya/oauth/controller/GoogleAuthController.java` | CREATE | `POST /api/auth/google`. |
| `src/main/java/com/claseya/security/SecurityConfig.java` | MODIFY | `POST /api/auth/google` permitAll (junto a `/api/auth/**`). |
| `src/main/resources/application.yml` | MODIFY | `oauth.google.client-id: ${GOOGLE_CLIENT_ID:}` y `oauth.google.issuer: ${GOOGLE_ISSUER:https://accounts.google.com}` (defaults dev documentados, sin secretos). |
| `pom.xml` | MODIFY | Dependencia `spring-security-oauth2-jose` (justificación: validación OIDC). |
| Tests | CREATE | `GoogleAuthIntegrationTest` + `GoogleIdTokenVerifierTest` (unit). |
| Docs | MODIFY | auth/security (+ Google) y spec/plan/ADR ya creados. |

**No se crean** módulos especulativos. `AuthService` password no se rompe; se extrae lo mínimo para
emitir el token/`lastLoginAt` si hiciera falta (reuso).

## 6. Base de datos

- **V6** (única; no toca `V1..V5`): columna `google_sub` nullable + índice único.
- Sin cambios en `email`/`status`/`role`; sin tablas nuevas.

## 7. API

- `POST /api/auth/google` (público): request `{ idToken, role? }`.
  - 200 `LoginResponse` (nuevo → 201 opcional con mismo body; se elige 200 para igualar login).
  - 400 token malformado/firma/iss/aud/exp; 403 rol ADMIN no permitido; 401 cuenta deshabilitada /
    `email_verified=false`; 409 `google_sub` duplicado / conflicto de vínculo.
- Errores vía `ApiError`/`GlobalExceptionHandler`. Identity derivada del token validado; nunca de
  campos del cliente.

## 8. Seguridad / tests

- Unit `GoogleIdTokenVerifierTest`: claims iss/aud/exp/email_verified (usando un `JwtDecoder` con
  clave RSA generada en test y tokens firmados por el test — sin red a Google).
- Integración `GoogleAuthIntegrationTest` (Testcontainers): se inyecta un `JwtDecoder` simulado
  (bean de test con issuer/aud configurados y firma propia) para cubrir AC-001..AC-012 de la spec.
- Regresión: suite completa password (register/login/JWT) sigue verde.

## 9. Observabilidad / errores / rollback

- Log INFO de login Google con resultado (sin id_token ni payload). Errores → ApiError.
- Rollback: retirar módulo `oauth`, revertir `User`/repo/config/security/pom; DB con
  `V7__revert_oauth_001` (DROP columna/índice). Nunca editar V6.

## 10. Change budget

- CREATE: V6; `oauth/{dto,service,config,controller}` (~6 clases); 2 suites de test.
- MODIFY: `User`, `UserRepository`, `SecurityConfig`, `application.yml`, `pom.xml` (+1
  dependencia), docs (auth/security).
- DELETE: ninguno. DEPENDENCIAS: `spring-security-oauth2-jose` (justificada).
- Riesgo principal: validación contra Google real requiere credenciales de entorno (no se prueban
  en CI; los tests usan decoder simulado). Documentado en la spec/ADR.

## 11. Definition of Done

- [ ] Spec y ADR-015 aprobados; PLAN aprobado.
- [ ] V6 aplicada (google_sub único); login password sin regresión.
- [ ] Validación id_token (firma/iss/aud/exp/email_verified) implementada y testeada.
- [ ] Nuevo por Google → ACTIVE; vínculo por email verificado; deshabilitadas rechazadas.
- [ ] `POST /api/auth/google` devuelve `LoginResponse`; sin exponer `google_sub`.
- [ ] Tests unit + integración (AC-001..012) y suite completa en verde (`mvn -B clean test`).
- [ ] Change budget respetado; reportado al director.
