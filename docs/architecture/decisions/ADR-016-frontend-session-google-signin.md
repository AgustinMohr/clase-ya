# ADR-016 — Sesión de frontend y Google Sign-In en la SPA

**Estado:** Aceptado (FE-OAUTH-001, implementación).

## Contexto
La SPA (Vite + React) necesita iniciar sesión (Google y email/password), mantener una sesión y usar
el JWT del backend stateless (sin refresh tokens todavía). El backend ya expone
`POST /api/auth/google` (valida `id_token`) y `POST /api/auth/login`.

## Decisión
- **Librería**: `@react-oauth/google` (Google Identity Services) condicionada a
  `VITE_GOOGLE_CLIENT_ID`; si no hay client-id, el botón de Google no se renderiza.
- **Sesión**: el JWT se guarda en **`sessionStorage`** (se pierde al cerrar la pestaña). No se
  persisten `id_token` ni datos sensibles; el display del usuario se deriva del propio JWT.
- **Flujo de rol**: la UI envía el `id_token` sin rol; si el backend responde 400 "role is
  required", se pide el rol y se reintenta con `role`. Cuentas existentes no ven fricción.
- **Convivencia**: el login email/password se mantiene visible junto a Google.
- **401**: una respuesta 401 en llamadas autenticadas limpia la sesión y pide login de nuevo.

## Consecuencias
- + Menos fricción (Google) sin romper el login clásico; cero cambios de backend.
- + `sessionStorage` reduce la exposición frente a `localStorage` en un contexto con XSS.
- - Requiere configurar un OAuth Client de Google (JS origins) y `VITE_GOOGLE_CLIENT_ID`.
- - Sin refresh tokens, la sesión termina al expirar el JWT o al cerrar la pestaña.
- - Nueva dependencia de frontend (justificada: evita implementar/parsear GIS a mano).
