# ADR-020 — Sesión con cookie HttpOnly y CSRF

**Estado:** Aceptado (SEC-002, implementado)
**Fecha:** 2026-10-27
**Reemplaza:** la parte de almacenamiento/claims de ADR-016 (la UI de Google Sign-In sigue vigente).

## Contexto

La auditoría `SEC-02` detectó que el JWT se guardaba en `sessionStorage`, con `email` como claim y
sin CSP ni cabeceras de seguridad. Cualquier XSS podía leer el token y reutilizarlo durante una hora.

El dueño del producto eligió el camino **B**: migrar la sesión del navegador a una cookie
`HttpOnly` con protección CSRF, en lugar de solo endurecer `sessionStorage` con CSP.

La API es stateless (JWT firmado, sin sesión HTTP de servidor). El token debe seguir validándose en
cualquier instancia con el secreto compartido, y la suite de integración que usa Bearer no debería
romperse.

## Decisión

1. **Cookie de sesión:** `POST /api/auth/login` y `/api/auth/google` emiten `CLASEYA_SESSION` con
   `HttpOnly`, `Secure` configurable (`COOKIE_SECURE`, default true), `SameSite` configurable
   (`COOKIE_SAME_SITE`, default `Lax`), `Path=/`, sin `Max-Age` (cookie de sesión). El token no se
   serializa en el body.
2. **Sesión del SPA:** `GET /api/auth/me` devuelve `{id, name, role}`. `POST /api/auth/logout`
   limpia la cookie. El Navbar muestra una etiqueta de rol, no el email.
3. **CSRF:** doble submit cookie + header.
   - `GET /api/auth/csrf` devuelve `{token}` y emite `XSRF-TOKEN` **HttpOnly**.
   - Los métodos unsafe autenticados por cookie exigen `X-XSRF-TOKEN`.
   - La SPA pide un token **antes de cada request unsafe** (no lo cachea): el servidor rota/limpia
     la cookie al escribir y un token cacheado produciría `403` en la siguiente escritura.
   - Los requests con `Authorization: Bearer` están exentos: un sitio cross-site no puede poner ese
     header, y así sigue funcionando la suite de integración.
4. **Claims mínimos:** el JWT queda con `sub`, `jti`, `iat` y `exp`. Se eliminan `email` y `role`;
   el backend recarga usuario y authorities desde la base en cada request.
5. **CSP y cabeceras:** CSP inyectada en `index.html` (dev permite inline/eval para Vite; producción
   los prohíbe y se verifica en build); bootstrap de tema externalizado; `Referrer-Policy`,
   `Permissions-Policy`, XCTO y XFO en el API; `_headers` para el HTML y documentación del
   equivalente en otros hostings.
6. **Bearer se conserva** para la suite de integración/tooling; la SPA nunca ve un token.

### Alternativas consideradas

- **A — Mantener `sessionStorage` + CSP:** menor cambio, pero el token sigue siendo robable por un
  XSS y el email seguía en el JWT. El dueño del producto eligió B.
- **C — No cambiar:** rechazada; no responde al hallazgo.
- **D — Token en memoria + refresh en cookie:** requiere refresh tokens y revocación; se difiere.

## Consecuencias

- **+** El JWT es invisible para JavaScript: un XSS no puede exfiltrarlo para reutilizarlo.
- **+** El token deja de contener PII; el rol y el nombre salen de la base.
- **+** CSRF explícito con doble submit y `SameSite`; sin sesión HTTP de servidor.
- **+** Base para refresh tokens/revocación (`jti`) sin cambiar el formato.
- **−** Logout no revoca el JWT: un token robado sigue válido hasta `exp` (1 h).
- **−** La SPA hace un GET extra a `/csrf` por cada escritura.
- **−** La configuración es sensible al despliegue: `SameSite=Lax` exige SPA/API same-site;
  `SameSite=None` exige `Secure` y HTTPS.
- **−** `style-src 'unsafe-inline'` sigue siendo necesario por atributos `style` de React/GIS.
- **−** `frame-ancestors` requiere cabecera del hosting (`_headers`/nginx); el `<meta>` no lo aplica.
- **−** Los previews sociales y la indexación sin JS siguen dependiendo de la fase de SSR.

## Futuro

- Refresh tokens + revocación server-side (usar `jti`) y logout con invalidación real.
- `report-to`/`report-uri` de CSP.
- Nonces/hashes para `style-src`.
- Endurecer `img-src` cuando SEC-06 mueva las fotos a almacenamiento propio.
