# SEC-002 — Sesión con cookie HttpOnly, CSRF y CSP

**Estado:** Aprobada e implementada (280 tests backend + 35 E2E verdes; pendiente de commit)
**Nivel de cambio:** 2 (seguridad + contrato de autenticación)
**Origen:** hallazgo `SEC-02` de `review.md`
**ADR asociado:** `ADR-020-cookie-session-and-csrf.md`

---

## 1. ID

`SEC-002` — Migración de la sesión del navegador a cookie `HttpOnly` con CSRF, claims mínimos del JWT y CSP.

---

## 2. Objetivo

Sacar el JWT del alcance de JavaScript, eliminar PII del token y endurecer el navegador con CSRF, CSP y cabeceras de seguridad, sin cambiar el modelo stateless del backend.

---

## 3. Contexto

- El JWT se guardaba en `sessionStorage` y se enviaba como `Authorization: Bearer` (ADR-016). Cualquier XSS podía leerlo.
- `JwtService` emitía `email` (PII) y `role`; el backend no confiaba en esos claims (el filtro usa solo `sub` y recarga el usuario).
- No había CSRF, CSP ni cabeceras explícitas de `Referrer-Policy` / `Permissions-Policy`.
- `index.html` tenía un script inline anti-flash que impedía una CSP estricta sin hash.
- La decisión de producto fue adoptar el camino **B**: cookie `HttpOnly` + CSRF, en lugar de solo endurecer `sessionStorage` (ADR-020).
- La API sigue siendo stateless: el JWT firmado viaja en una cookie y no hay sesión HTTP de servidor.

---

## 4. Actores

| Actor | Rol |
|---|---|
| Visitante / estudiante / profesor | Inicia sesión y usa la SPA. |
| Atacante con XSS | Intenta leer credenciales o actuar como el usuario. |
| Atacante cross-site | Intenta CSRF sobre requests autenticados por cookie. |
| Navegador | Aplica cookies, CSRF, CSP y cabeceras. |
| Operaciones | Configura `Secure`/`SameSite`, HTTPS y cabeceras del hosting. |

---

## 5. Requisitos funcionales

- **RF-1 — Cookie de sesión:** `POST /api/auth/login` y `/api/auth/google` emiten `CLASEYA_SESSION`
  (`HttpOnly`, `Secure` configurable, `SameSite` configurable, `Path=/`, cookie de sesión). El token
  **nunca** aparece en el body ni en `sessionStorage`.
- **RF-2 — Respuesta de login:** `{ user: { id, name, role }, expiresIn }`. Sin `accessToken` ni
  `tokenType`. `POST /api/auth/register` no cambia.
- **RF-3 — Sesión actual:** `GET /api/auth/me` (autenticado) devuelve el mismo DTO `user`, sin email.
  Es la única forma en que la SPA conoce el rol al cargar.
- **RF-4 — Logout:** `POST /api/auth/logout` borra la cookie (`Max-Age=0`) y responde `204`.
  Es idempotente; sin revocación server-side (el JWT sigue válido hasta `exp`).
- **RF-5 — CSRF:**
  - `GET /api/auth/csrf` devuelve `{ token }` y emite la cookie `XSRF-TOKEN` (`HttpOnly`).
  - Todo método unsafe autenticado por cookie exige el header `X-XSRF-TOKEN` con el token vigente.
  - Los requests con `Authorization: Bearer` quedan exentos (un sitio cross-site no puede poner ese
    header; lo usa la suite de integración/tooling).
  - La SPA **pide un token nuevo antes de cada request unsafe** (no lo cachea): el servidor rota o
    limpia la cookie al escribir y un token viejo produciría `403`.
- **RF-6 — Claims del JWT:** `sub`, `jti`, `iat`, `exp`. Sin `email`, `name` ni `role`: el backend
  recarga el usuario y sus authorities en cada request. `jti` habilita una futura revocación.
- **RF-7 — Frontend:** los requests usan `credentials: 'include'`; no hay token en JS; al montar se
  resuelve `/api/auth/me`; las rutas privadas esperan esa carga y muestran un skeleton; el Navbar
  muestra una etiqueta de rol (`Estudiante`/`Profesor`/`Administrador`), no el email.
- **RF-8 — CORS:** `allowCredentials=true`, orígenes exactos y `X-XSRF-TOKEN` en allowed headers.
- **RF-9 — CSP:** `index.html` publica una CSP con `default-src 'self'`, `object-src 'none'`,
  `base-uri 'self'`, `form-action 'self'`, `script-src 'self' https://accounts.google.com` (sin
  inline/eval en producción), `style-src 'self' 'unsafe-inline' https://accounts.google.com`,
  `img-src 'self' data: https:`, `font-src 'self' data:`, `connect-src 'self' <VITE_API_URL>
  https://accounts.google.com`, `frame-src https://accounts.google.com`, `manifest-src 'self'` y
  `worker-src 'self'`.
- **RF-10 — Sin scripts inline:** el bootstrap de tema vive en `public/theme-init.js`.
- **RF-11 — Cabeceras:** el API agrega `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
  `Referrer-Policy: no-referrer` y `Permissions-Policy` (cámara/micrófono/geolocalización/pagos
  deshabilitados). El HTML versiona `_headers` (XFO, XCTO, Referrer, Permissions y
  `Content-Security-Policy: frame-ancestors 'none'`) y un meta referrer de fallback.
- **RF-12 — Verificación en build:** `npm run build` falla si la CSP de producción permite
  inline/eval en `script-src` o si hay scripts inline.
- **RF-13 — Compatibilidad:** login email/contraseña, Google, registro, logout, restauración de
  sesión, favoritos, mensajería, Mi anuncio y los 35 E2E siguen funcionando.

---

## 6. Requisitos no funcionales

- **Rendimiento:** un GET extra a `/api/auth/csrf` por escritura; sin impacto medible en lecturas.
- **Escalabilidad:** no hay estado de sesión en el servidor. El JWT y el token CSRF viven en cookies;
  cualquier instancia puede validarlos con el mismo secreto.
- **Despliegue:** HTTPS obligatorio; `COOKIE_SECURE=true`. `SameSite=Lax` para SPA/API same-site;
  `SameSite=None` + `Secure` si el API está en otro sitio, con CORS de credenciales.
- **Determinismo:** tests con MockMvc para cookies/CSRF; E2E con el flujo real.

---

## 7. Seguridad

- **XSS:** el JWT no es legible por JavaScript; un XSS no puede exfiltrarlo. La CSP reduce la
  superficie de ejecución. Un XSS same-origin todavía puede actuar como el usuario mientras la
  página esté abierta (no puede leer la cookie).
- **CSRF:** doble submit cookie + header; el token se obtiene por un GET solo legible desde orígenes
  CORS permitidos; `SameSite` como defensa adicional.
- **PII:** el token y el DTO de sesión no incluyen email ni nombre de otros usuarios; la UI no
  muestra emails.
- **Google:** allowlist solo `https://accounts.google.com` para script/frame/estilo/conexión.
- **Autorización:** sigue saliendo de `SecurityContext` + DB; el rol del cliente no es autoridad.

---

## 8. Invariantes

- Ningún token es accesible desde JavaScript ni se persiste en `localStorage`/`sessionStorage`.
- Todo request unsafe autenticado por cookie lleva un CSRF válido.
- Ningún token emitido contiene `email`/`name`/`role`.
- Un request con Bearer no requiere CSRF y sigue autenticando.
- La cookie de sesión es `HttpOnly` y `Secure` fuera de desarrollo local.

---

## 9. API contract

| Método | Ruta | Cambio |
|---|---|---|
| POST | `/api/auth/login` | `Set-Cookie: CLASEYA_SESSION`; body `{user, expiresIn}` (sin token) |
| POST | `/api/auth/google` | Igual |
| GET | `/api/auth/me` | **Nuevo**; `{id, name, role}` autenticado |
| POST | `/api/auth/logout` | **Nuevo**; `204`, limpia la cookie |
| GET | `/api/auth/csrf` | **Nuevo**; `{token}` + cookie `XSRF-TOKEN` |
| POST/PUT/PATCH/DELETE | `/api/**` (cookie) | Requiere `X-XSRF-TOKEN`; Bearer exento |

---

## 10. Datos afectados

Ninguno. Sin migraciones ni cambios de esquema.

---

## 11. Criterios de aceptación

- **AC-1 (RF-1/2):** login responde 200 con `user`/`expiresIn`, `Set-Cookie: CLASEYA_SESSION`
  `HttpOnly; SameSite=Lax`, y sin `accessToken`/`tokenType`.
- **AC-2 (RF-3):** `/api/auth/me` con la cookie devuelve el usuario; sin cookie → 401; sin email.
- **AC-3 (RF-4):** logout responde 204 y la cookie de sesión queda con `Max-Age=0`.
- **AC-4 (RF-5):** un POST cookie-autenticado sin CSRF → 403; con `/csrf` + header → éxito; Bearer
  sin CSRF → éxito.
- **AC-5 (RF-6):** el JWT decodificado solo tiene `sub`, `jti`, `iat`, `exp`; dos tokens tienen
  `jti` distinto.
- **AC-6 (RF-7):** la SPA no usa `sessionStorage` para el token; el Navbar muestra el rol; las rutas
  privadas esperan `/me` antes de decidir.
- **AC-7 (RF-9/10/12):** `npm run build` pasa el chequeo de CSP; `dist/index.html` no tiene scripts
  inline ni `unsafe-inline`/`unsafe-eval` en `script-src`.
- **AC-8 (RF-11):** el API devuelve las cabeceras; el HTML tiene `_headers` y meta referrer.
- **AC-9 (RF-13):** suite backend completa y E2E 35/35 verdes.

---

## 12. Out of scope

- Refresh tokens y revocación server-side de JWT (logout solo limpia la cookie; un token robado
  sigue válido hasta `exp`).
- `report-to`/`report-uri` de CSP.
- Nonces/hashes para `style-src`.
- Allowlist de `img-src` (SEC-06).
- WAF/CDN, HSTS a nivel de hosting, pruebas con un Google Client ID real.

---

## 13. Definition of Done

- [x] Spec y ADR-020 aprobados.
- [x] Cookie HttpOnly + `/me` + `/logout` + CSRF implementados.
- [x] JWT sin PII, con `jti`.
- [x] Frontend sin token en JS; CSRF por request; rol en Navbar.
- [x] CSP, `_headers`, cabeceras del API y chequeo de build.
- [x] Tests backend (280) y E2E (35/35) verdes.
- [x] Docs actualizados.
- [x] Sin migraciones ni endpoints fuera de los declarados.
