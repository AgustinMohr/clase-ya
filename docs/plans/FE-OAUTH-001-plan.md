# PLAN — FE-OAUTH-001 · Login Google en la UI

**Fuente:** `docs/specs/active/FE-OAUTH-001-google-login-ui.md` (decisiones cerradas) · ADR-016.

## Objetivo
SPA con login Google (`@react-oauth/google`) + login email/password, sesión en `sessionStorage`,
header con usuario/logout, y gating del botón Contactar (contacto simulado).

## Archivos
- CREATE `frontend/src/auth/AuthContext.tsx` (sesión, login, google, logout, 401 handler).
- CREATE `frontend/src/components/GoogleSignInButton.tsx` (usa `GoogleLogin`, condicional a client-id).
- CREATE `frontend/src/components/LoginModal.tsx` (email/password + Google + pedido de rol).
- CREATE `frontend/src/components/ContactModal.tsx` (contacto simulado, localStorage).
- CREATE `frontend/src/vite-env.d.ts`, `frontend/.env.example`.
- MODIFY `frontend/src/api.ts` (POST auth, sessionStorage, ApiError, `Bearer`, 401).
- MODIFY `frontend/src/App.tsx`, `frontend/src/main.tsx` (provider condicional), `src/styles.css`.
- MODIFY `frontend/package.json` (+ `@react-oauth/google`).

## Change budget
Frontend-only. **Backend sin cambios. Sin migraciones.** 1 dependencia de frontend justificada
(ADR-016). Verificación: `npm run build` + manual (`npm run dev` con backend `:8080`).

## Riesgos
Google real requiere OAuth Client (origins `http://localhost:5173`); sin client-id solo email/password.
El contacto es simulado (backend CONTACT-001 pendiente).

## DoD
Botón Google condicional + email/password; sesión en sessionStorage; header/logout; Contactar exige
sesión; errores 400/401/403/409/red con reintento de rol; `npm run build` verde; sin tokens en logs.
