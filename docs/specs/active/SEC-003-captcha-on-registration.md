# SEC-003 — CAPTCHA en el registro

**ID:** SEC-003
**Estado:** Aprobada — implementada (2026-10-28)
**Origen:** `SEC-04` de `review.md` (decisión del director: CAPTCHA).
**Relacionado:** `AUTH-002` (registro genérico), `RATE-001`.

---

## 1. Objetivo

Agregar una prueba humana al alta de cuentas para frenar registros automatizados sin volver a
introducir una señal de enumeración. Se usa **Cloudflare Turnstile**.

## 2. Decisiones

- **D1 — Turnstile, solo en registro.** El token viaja en `RegisterRequest.captchaToken`; el backend
  lo verifica contra `https://challenges.cloudflare.com/turnstile/v0/siteverify` con
  `TURNSTILE_SECRET`. Login y el resto de los flujos siguen cubiertos por RATE-001.
- **D2 — Off por defecto.** `CAPTCHA_ENABLED` arranca en `false` (tests/local). En producción se
  activa junto con `TURNSTILE_SECRET` y el `VITE_TURNSTILE_SITE_KEY` del SPA.
- **D3 — Fail-closed.** Sin token o con token inválido → `400`. Si Turnstile no responde → `503`
  (no se saltea el desafío en silencio).
- **D4 — La verificación ocurre antes de buscar el email.** Todos los intentos pagan el mismo costo;
  el registro sigue respondiendo `202` genérico.
- **D5 — CSP explícita.** `script-src`, `style-src`, `connect-src` y `frame-src` incluyen
  `https://challenges.cloudflare.com`.
- **D6 — El widget es opcional en la UI.** Solo se renderiza si `VITE_TURNSTILE_SITE_KEY` existe;
  en desarrollo sin clave muestra una nota.

## 3. API

```
POST /api/auth/register
{ "email": "...", "password": "...", "role": "STUDENT", "captchaToken": "<turnstile>" }

202 Accepted  (sin body)
400           si el token falta o es inválido
503           si Turnstile no está disponible
```

## 4. Configuración

| Variable | Dónde | Default |
|---|---|---|
| `CAPTCHA_ENABLED` | backend | `false` |
| `TURNSTILE_SECRET` | backend | vacío (obligatorio si enabled) |
| `TURNSTILE_VERIFY_URL` | backend | `https://challenges.cloudflare.com/turnstile/v0/siteverify` |
| `VITE_TURNSTILE_SITE_KEY` | frontend (build) | vacío |

## 5. Tests

- Unit de `TurnstileCaptchaVerifier` con `MockRestServiceServer`: disabled, éxito, success=false,
  token faltante y proveedor caído.
- Integración: el token se reenvía; un CAPTCHA inválido devuelve 400 y no crea la cuenta.
- El mock del verificador en `AbstractWebIntegrationTest`/`RateLimitIntegrationTest` evita llamadas
  reales.
- E2E: con CAPTCHA deshabilitado y sin site key, el registro sigue funcionando.

## 6. Fuera de alcance

HIBP/zxcvbn (sigue diferido en `ADR-023`), CAPTCHA en login, CAPTCHA invisible/score, reCAPTCHA o
hCaptcha, pruebas contra el proveedor real.

## 7. Change budget

- **Scope:** verificador backend + token en el DTO + widget/estado en el SPA + CSP + docs/tests.
- **Archivos:** `auth/captcha/*`, `RegisterRequest`, `AuthService`, `application.yml`, tests,
  `TurnstileWidget.tsx`, `RegisterPage.tsx`, `api.ts`, `AuthContext.tsx`, `vite.config.ts`,
  `docs/auth.md`, `README.md`, `review.md`, `ADR-025`.
- **Migraciones:** ninguna.
- **Dependencias:** ninguna (se usa `RestClient` de Spring).

## 8. Definition of Done

- [x] CAPTCHA verificado server-side y fail-closed.
- [x] Registro genérico intacto; token opcional cuando está deshabilitado.
- [x] Widget + CSP; sin site key no rompe el flujo.
- [x] Tests backend, lint, tsc, build y E2E verdes.
- [x] Docs y `review.md` actualizados.
