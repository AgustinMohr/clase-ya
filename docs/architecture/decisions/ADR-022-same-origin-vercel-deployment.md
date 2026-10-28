# ADR-022 — Deploy same-origin del SPA y el API en Vercel

**Estado:** Aceptado (2026-10-28)
**Relacionado:** `ADR-020` (cookie de sesión y CSRF), `SEC-002`, `AUTH-001`.

## Contexto

En el deploy de Vercel, el login fallaba con `403 Access denied` por **ambos** caminos (Google y
email + contraseña), mientras que en localhost funcionaba. El 403 no era de credenciales: lo emite
`RestAccessDeniedHandler`, al que solo llega `CsrfFilter` (`/api/auth/**` es `permitAll`).

La causa es la topología cross-site. El SPA vive en `*.vercel.app` y el API en otro dominio. El
doble submit CSRF depende de la cookie `XSRF-TOKEN`, y con `SameSite=Lax` (default de
`auth.cookie.same-site`) el navegador no envía esa cookie en un `fetch` cross-site. El header
`X-XSRF-TOKEN` llega, la cookie no, y el POST falla. La misma cookie es la sesión
(`CLASEYA_SESSION`), así que aunque el login pasara, el usuario quedaría deslogueado.

Configurar `SameSite=None; Secure` "arregla" el síntoma, pero depende de cookies de terceros: Safari
(ITP) y Chrome las bloquean cada vez más. No es una base estable para la sesión.

## Decisión

1. **El SPA y el API se sirven bajo el mismo origen público.** Vercel proxea `/api/*` al backend
   mediante `frontend/vercel.json`:
   - `routes` con `{ "handle": "filesystem" }` para que los assets sigan sirviéndose primero.
   - `/api/(.*)` → `${BACKEND_URL}/api/$1` (el host del backend sale de la variable `BACKEND_URL` de
     Vercel; Vercel expande variables de entorno en `dest` con `env`).
   - Fallback SPA a `/index.html`.
2. **La SPA llama a `/api` en su propio origen.** `VITE_API_URL` debe quedar **vacío** en Vercel (es
   una variable de build: hay que redeployar al cambiarla). En local se mantiene el proxy de Vite.
3. **Cookies first-party.** `COOKIE_SAME_SITE=Lax` y `COOKIE_SECURE=true`. El navegador ve la cookie
   emitida por el host de Vercel, así que CSRF y sesión funcionan sin depender de cookies de
   terceros.
4. **CORS deja de ser el mecanismo de acceso entre SPA y API**, pero `FRONTEND_URL` debe seguir
   incluyendo el origen de Vercel: el navegador manda `Origin` en los POST y el backend lo valida
   aunque la request llegue proxeada.
5. **Google OAuth:** el origen de la SPA (`https://<proyecto>.vercel.app`) debe estar en
   *Authorized JavaScript origins* del OAuth client. Es independiente del proxy.

## Consecuencias

- **Positivas:** CSRF y sesión funcionan en todos los navegadores; desaparecen los errores de CORS
  entre SPA y API; una sola URL pública para compartir.
- **Costos / riesgos:**
  - Todo el tráfico del API (incluida la subida de evidencia de hasta 10 MB) atraviesa Vercel. Hay
    que verificar ese flujo contra los límites de body del plan. Si lo corta (413), las salidas son:
    servir SPA y API en subdominios del **mismo sitio** (por ejemplo `app.example.com` y
    `api.example.com`, donde `SameSite=Lax` sí viaja sin proxy), bajar el tope de archivo, o migrar
    la evidencia a object storage (`STOR-01`). Una subida directa cross-site no sirve: perdería la
    cookie de sesión.
  - `BACKEND_URL` es obligatoria en Vercel; si falta, el rewrite queda inválido.
  - **Rate limiting:** el backend ve las IPs del edge de Vercel. Hay que activar
    `RATE_LIMIT_TRUST_FORWARDED_HEADERS=true` (Vercel sobrescribe `X-Forwarded-For`); si queda en
    `false`, todos los usuarios comparten el mismo bucket y aparecen `429` cruzados.
  - Los **preview deployments** tienen un origen distinto por deploy: si se usan, hay que
    registrarlos en Google o probar sobre el dominio de producción.
  - Un backend caído ahora también afecta al dominio del SPA (el proxy devuelve el error del
    backend).
- **Alternativas descartadas:** `SameSite=None` cross-site (se rompe con el bloqueo de cookies de
  terceros); volver a `Bearer` en `sessionStorage` (reabre `SEC-02`).
