# ADR-025 — CAPTCHA en el registro con Cloudflare Turnstile

**Estado:** Aceptado (SEC-003, implementado)
**Fecha:** 2026-10-28
**Relacionado:** `SEC-04` de `review.md`, `AUTH-002`, `RATE-001`, `ADR-023`.

## Contexto

`SEC-04` quedó cerrado en su parte de política de contraseñas y enumeración (registro `202`
genérico). El director eligió **CAPTCHA** como mitigación adicional contra registros automatizados.
La alternativa de HIBP (contraseñas filtradas) sigue diferida por `ADR-023`.

## Decisión

1. **Cloudflare Turnstile**, solo en el registro. Es gratuito, no depende de Google y no requiere
   un SDK: el SPA renderiza el widget y el backend valida el token con `RestClient`.
2. **Off por defecto.** En local y tests `CAPTCHA_ENABLED=false`. En producción se activa con
   `CAPTCHA_ENABLED=true`, `TURNSTILE_SECRET` y `VITE_TURNSTILE_SITE_KEY`.
3. **Fail-closed.** Token ausente/inválido → `400`; proveedor inaccesible → `503`. El alta no sigue
   sin desafío.
4. **Antes de buscar el email.** El costo del challenge se paga en todos los intentos, así que no
   reabre una señal de enumeración por timing/estado.
5. **CSP explícita** para `challenges.cloudflare.com` en script/style/connect/frame.

## Consecuencias

- **Positivas:** frena registros automatizados; el registro sigue siendo genérico; no agrega
  dependencias Maven; se puede desactivar por entorno.
- **Costos:** dependencia externa en el alta (si Turnstile cae, el registro responde 503); el SPA
  necesita la site key en build; Cloudflare ve el tráfico del desafío (privacidad, documentado).
- **Operación:** configurar las claves y monitorear los 503. El rate limiting sigue activo.

## Alternativas

- **reCAPTCHA/hCaptcha:** mismo patrón, más peso/privacidad distinta; no hay razón para cambiar.
- **HIBP:** mide otra cosa (contraseñas filtradas); sigue pendiente.
- **Solo rate limiting:** ya existe; el director pidió CAPTCHA.
