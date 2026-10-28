# AUTH-001 — Registro email + contraseña con activación

**ID:** AUTH-001
**Estado:** Aprobada — en implementación (2026-10-28)
**Carpeta:** `docs/specs/active/`
**Origen:** hallazgo `AUTH-01` de `review.md` (HIGH).
**Depende de:** `OAUTH-001` (Google), `SEC-002` (cookie de sesión), `RATE-001` (rate limiting).

---

## 1. Objetivo

Completar de punta a punta el alta con **email + contraseña**, que hoy queda a mitad de camino:
`POST /api/auth/register` crea cuentas `PENDING` y no existe forma de activarlas, mientras la UI
ofrece un login por contraseña que nunca puede funcionar para una cuenta nueva.

Al terminar, un visitante puede: crear una cuenta desde la SPA, recibir un email con un enlace de
activación de un solo uso, activarla y entrar con su contraseña. Google OAuth sigue siendo el
camino principal y no se modifica.

## 2. Contexto verificado

- `AuthService.register` crea `User` con `status = PENDING`, `passwordHash` BCrypt y rol
  `STUDENT`/`TEACHER` (ADMIN rechazado).
- `AppUserDetails` solo autentica `ACTIVE`; no existe endpoint ni tarea de activación.
- `GoogleAuthService` crea cuentas `ACTIVE` (email ya verificado por Google) y **rechaza** cuentas
  `PENDING` en el login con Google (`requireActive`). Por eso una cuenta password sin activar no
  tiene ninguna salida: ni password ni Google.
- `pom.xml` no tiene transporte de email ni `spring.mail.*` configurado.
- `LoginModal` ofrece email/contraseña sin ningún enlace a registro y sin explicar el estado
  `PENDING`.
- La SPA ya usa cookie `HttpOnly` + CSRF (`ADR-020`) y rutas reales (`ARCH-001`).
- `application-local.yml` está gitignoreado (ver `DEV-01`), así que cualquier modo de desarrollo
  debe activarse por variable de entorno, no por ese archivo.

## 3. Decisiones

- **D1 — Convivencia (no exclusividad).** Google OAuth es el camino principal obligatorio del PRD;
  email + contraseña queda como **segundo camino soportado** y completo. Es consistente con
  `OAUTH-001` D-1. Se registra en `ADR-021`.
- **D2 — Activación por token de un solo uso.** El alta envía un enlace; el token se guarda
  **hasheado** (SHA-256), vence a las 24 h, es de un solo uso y se invalida al reenviar.
- **D3 — La cuenta pasa a `ACTIVE` al activar.** No se agrega un estado nuevo. Se registra
  `users.email_verified_at` como evidencia de verificación.
- **D4 — Sin enumeración en el reenvío.** `POST /api/auth/resend-activation` responde `202` siempre,
  exista o no la cuenta y esté o no pendiente.
- **D5 — El registro conserva `409` para email existente.** Cambiar esa respuesta es parte de
  `SEC-04` (sigue abierto) y no se mezcla en este cambio.
- **D6 — Salida operativa para ADMIN.** `POST /api/admin/users/{id}/activate` permite activar
  manualmente si el correo no llega. No marca `email_verified_at`.
- **D7 — Transporte de email por SMTP.** `spring-boot-starter-mail` con configuración por entorno.
  Sin SMTP, el registro falla con `503` y rollback (nunca una cuenta huérfana). Solo en desarrollo
  se puede habilitar el log del enlace con `AUTH_LOG_ACTIVATION_LINKS=true` (default `false`).
- **D8 — Google `origin_mismatch` no se arregla en código.** Es configuración de Google Cloud
  Console (Authorized JavaScript origins). El código solo agrega diagnóstico accionable y
  documentación; ver §8.

## 4. Requisitos funcionales

- **RF-1** La SPA ofrece `/registro` con email, contraseña, confirmación y elección de rol
  (estudiante / profesor). Valida en cliente y muestra errores del backend por campo.
- **RF-2** Un registro válido responde `201` con el shape actual y dispara el email de activación.
- **RF-3** `/activar?token=...` consume el token: éxito → mensaje + CTA a ingresar; token inválido,
  vencido o usado → mensaje único y opción de reenviar.
- **RF-4** `POST /api/auth/resend-activation` responde `202` siempre y, si corresponde, emite un
  token nuevo e invalida los anteriores.
- **RF-5** El modal de login enlaza a `/registro` y, ante un `401`, sugiere revisar el email de
  activación y ofrece reenviarlo sin revelar si la cuenta existe.
- **RF-6** Un ADMIN puede activar una cuenta pendiente por API.
- **RF-7** Un usuario `ACTIVE` que activa de nuevo con un token válido obtiene `204` sin efectos.
- **RF-8** Ningún token se registra en logs salvo que `AUTH_LOG_ACTIVATION_LINKS=true` (solo dev).

## 5. API

| Método | Ruta | Actor | Respuesta |
|---|---|---|---|
| POST | `/api/auth/register` | público | `201` + shape actual; `400` validación; `409` email existente; `503` sin transporte de email |
| POST | `/api/auth/activate` | público | `204`; `400` token inválido/vencido/usado |
| POST | `/api/auth/resend-activation` | público | `202` siempre |
| POST | `/api/admin/users/{userId}/activate` | ADMIN | `204`; `404` usuario inexistente |

`POST /api/auth/activate` recibe `{ "token": "<string>" }`.
`POST /api/auth/resend-activation` recibe `{ "email": "<string>" }`.

Los endpoints públicos quedan bajo el rate limit de registro existente (`REGISTER_IP`); no se crea
un scope nuevo en este cambio.

## 6. Modelo de datos — `V9__email_account_activation.sql`

```sql
ALTER TABLE users ADD COLUMN email_verified_at timestamptz;

CREATE TABLE email_verification_tokens (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  varchar(64) NOT NULL UNIQUE,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_email_verification_tokens_user ON email_verification_tokens (user_id);
```

Motivo: la activación necesita un token de un solo uso auditable; `email_verified_at` deja
constancia de que la verificación fue por correo (distinta de una activación manual por ADMIN).
`token_hash` es SHA-256 hexadecimal; el token en claro nunca se persiste.

## 7. Backend

- `auth/service/AccountActivationService`: emite, valida y consume tokens; activa al usuario.
- `auth/service/ActivationMailSender` (interfaz) + `SmtpActivationMailSender` (cuando
  `spring.mail.host` está definido) + `LoggingActivationMailSender` (solo si
  `app.auth.activation.log-links=true`) + `UnavailableActivationMailSender` (fallback → `503`).
- `AuthService.register` delega la emisión del token; el envío ocurre dentro de la transacción para
  que un fallo de correo no deje una cuenta inactivable.
- Configuración nueva: `app.auth.activation.base-url` (`APP_BASE_URL`, default
  `http://localhost:5173`), `token-ttl` (`AUTH_ACTIVATION_TTL`, default `PT24H`) y
  `log-links` (`AUTH_LOG_ACTIVATION_LINKS`, default `false`). SMTP por `MAIL_HOST`, `MAIL_PORT`,
  `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`.
- El enlace es `${base-url}/activar?token=<token>`.

## 8. Google `origin_mismatch`

El error `400: origin_mismatch` lo emite Google Identity Services cuando el origen de la página no
está en **Authorized JavaScript origins** del OAuth Client. No hay cambio de código que lo resuelva.
Este cambio aporta:

- Mensaje de error del botón con el origen actual y la ruta exacta a configurar en la consola.
- Aviso en desarrollo cuando la SPA se abre por `127.0.0.1` en lugar de `localhost` (son orígenes
  distintos para Google).
- Pasos concretos en `docs/auth.md` y `README.md`, incluyendo el origen de producción.

## 9. Frontend

- `pages/RegisterPage.tsx`, `pages/ActivateAccountPage.tsx`, rutas `/registro` y `/activar`.
- `AuthContext`: `register`, `activate`, `resendActivation`.
- `LoginModal`: enlace "Crear cuenta" y hint de activación tras `401`.
- `GoogleSignInButton`: diagnóstico de origen (sin cambiar el flujo OAuth).

## 10. Tests

Backend (integración, Testcontainers):

1. Registro válido → usuario `PENDING`, token emitido, email capturado por el sender de test.
2. Activar con token válido → `204`, `status = ACTIVE`, `email_verified_at` seteado, login OK.
3. Token vencido → `400`, cuenta sigue `PENDING`.
4. Token reutilizado → `400`.
5. Reenvío invalida el token anterior.
6. Reenvío con email inexistente → `202` y sin email.
7. Activación de una cuenta ya `ACTIVE` con token válido → `204` idempotente.
8. ADMIN activa manualmente → `204`, `ACTIVE`, `email_verified_at` nulo.
9. Rate limit del reenvío → `429`.
10. Regresión: registro/validación/409/login existentes siguen verdes.

Frontend (Playwright): `/registro` valida y muestra confirmación; `/activar` con token inválido
muestra error; el modal enlaza a `/registro`.

## 11. Fuera de alcance

Cambiar el `409` del registro (`SEC-04`), CAPTCHA, política de complejidad de contraseñas
(`SEC-04`), verificación de email para cuentas creadas por Google, recuperación de contraseña,
revocación de JWT al activar, plantillas HTML de correo, proveedor de email gestionado.

## 12. Change budget

- **Scope:** backend de activación + email + endpoints + migración `V9`; SPA de registro y
  activación; diagnóstico de Google; ADR-021 y docs.
- **Archivos esperados:** `AuthService`, `AuthController`, `AuthIntegrationTest`, nuevos
  `AccountActivationService`, sender de email, `ActivationTokenRepository`, `EmailVerificationToken`,
  `User`; `SecurityConfig` (ruta admin ya cubierta), `application.yml`; frontend `App.tsx`,
  `routes.ts`, `api.ts`, `AuthContext.tsx`, `LoginModal.tsx`, `GoogleSignInButton.tsx`, páginas
  nuevas; `docs/auth.md`, `README.md`, `review.md`, `ADR-021`.
- **Migraciones:** `V9__email_account_activation.sql` (token de activación + `email_verified_at`).
- **Dependencias:** `spring-boot-starter-mail` (gestión de versión por el BOM de Spring Boot).
  Motivo: transporte SMTP para el enlace de activación.

## 13. Definition of Done

- [x] Spec aprobada por el director.
- [x] `ADR-021` documenta la convivencia de ambos caminos y el ciclo de activación.
- [x] `V9` aplicada; `ddl-auto=validate` sigue verde.
- [x] Endpoints y UI implementados; `mvn -B clean test` (291/291) y `npm run build` verdes.
- [x] E2E de registro y activación verdes (40/40).
- [x] `docs/auth.md`, README y `review.md` actualizados.
- [x] Sin cambios a `SEC-04`, `SEC-05` ni a las migraciones históricas.
