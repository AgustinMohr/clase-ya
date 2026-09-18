# FE-OAUTH-001 — Login con Google en la UI (React)

**ID:** FE-OAUTH-001
**Estado:** Activa · en REVIEW · **Carpeta:** `docs/specs/active/`
**Depende de:** `OAUTH-001` (backend, implementado) y `PROFILE-001` (tarjetas, parcial).
**Referencia de producto:** `docs/product/product-requirements.md` (Google OAuth "infaltable").

---

## 1. Objetivo

Integrar en la SPA (`frontend/`) el inicio de sesión con **Google (OAuth 2.0 / Google Identity
Services)** usando el backend existente, y dejar la sesión (JWT + estado de usuario) lista para:
mostrar al usuario logueado, habilitar el botón **Contactar** y (después) favoritos y perfil.

No se agrega lógica de negocio nueva al backend: se consume `POST /api/auth/google`.

## 2. Contexto actual (real, verificado)

- **Backend implementado (OAUTH-001)**:
  - `POST /api/auth/google` recibe `{ idToken, role? }`.
  - Valida el `id_token` (firma/JWKS, `iss`, `aud` = Google client id, `exp`, `email_verified`).
  - Nuevos usuarios por Google → `ACTIVE`; **`role` es obligatorio solo si la cuenta no existe**
    (STUDENT/TEACHER; ADMIN → 403). Cuenta password existente con el mismo email verificado se
    vincula (`users.google_sub`); cuentas PENDING/INACTIVE/SUSPENDED → 401.
  - Devuelve el mismo shape que el login clásico: `{ accessToken, tokenType:"Bearer", expiresIn }`.
  - Errores: 400 (token inválido/iss/aud/exp), 401 (deshabilitada / email no verificado),
    403 (ADMIN), 409 (google_sub/email en conflicto).
  - Config: `oauth.google.issuer`, `oauth.google.client-id` (env `GOOGLE_CLIENT_ID`).
- **Backend ya existente** para fallback: `POST /api/auth/register`, `POST /api/auth/login`.
- **Frontend actual**: SPA Vite + React + TS en `frontend/` con proxy `/api → http://localhost:8080`;
  buscador→tarjetas→detalle; login/contactar son **stub**. No hay librería OAuth ni manejo de sesión.
- **CORS**: no aplica para el dev local porque se usa el **proxy de Vite**; si el frontend se sirve
  en otro origen, el backend ya restringe a `FRONTEND_URL`.

## 3. RF (requisitos funcionales)

- **RF-1** Mostrar un botón **"Continuar con Google"** (Google Identity Services) cuando la app tenga
  `VITE_GOOGLE_CLIENT_ID` configurado; si falta, no se muestra (con nota de configuración).
- **RF-2** Al autenticarse con Google, enviar el `id_token` a `POST /api/auth/google` y, en éxito,
  guardar el JWT y el estado de sesión en el cliente.
- **RF-3** Si la cuenta es **nueva** y el backend responde 400 por `role` faltante, la UI debe pedir
  el **rol** (estudiante / profesor) y reintentar con `role` (ver HUMAN DECISION-1).
- **RF-4** Header con estado de usuario: nombre (o email si no hay nombre), y **Cerrar sesión**.
- **RF-5** La sesión debe sobrevivir un refresh de página (según estrategia de almacenamiento,
  HUMAN DECISION-2) y expirar/reaccionar ante un JWT vencido (401 → limpiar sesión y pedir login).
- **RF-6** Mantener el login **email/password** existente como alternativa (decisión del director:
  convivencia) y Google cuando esté configurado.
- **RF-7** El botón **Contactar** del detalle debe exigir sesión: si no hay sesión, abre el login y
  continúa el flujo al terminar.
- **RF-8** Estados de carga y error visibles y no bloqueantes: `loading` durante el intercambio del
  token, mensajes claros por código (400/401/403/409/red), y posibilidad de reintentar.
- **RF-9** Logout limpia token/estado local y no deja datos de sesión en memoria.
- **RF-10** No exponer el `id_token` ni el JWT en logs/UI; no persistir el `id_token`.

## 4. RNF

- **RNF-1** Ninguna llamada incluye `userId`/`studentId`: la identidad la fija el backend.
- **RNF-2** Sin dependencias innecesarias: se evalúa `@react-oauth/google` (o script GIS directo);
  la elección se justifica en el ADR/PLAN (HUMAN DECISION-3).
- **RNF-3** `npm run build` (typecheck + producción) debe quedar verde.
- **RNF-4** El flujo debe funcionar en dev (`localhost:5173` autorizado en Google Cloud) y quedar
  documentado para otros orígenes.

## 5. API (contrato cliente)

```
POST /api/auth/google
  body: { idToken: string, role?: "STUDENT" | "TEACHER" }
  200 -> { accessToken, tokenType: "Bearer", expiresIn }
  400 (token inválido / faltan claims / role requerido) | 401 (deshabilitada/email no verificado)
  403 (ADMIN) | 409 (email/google ya vinculado)
```
Reutiliza el mismo almacenamiento de sesión que `POST /api/auth/login`.

## 6. Datos afectados

- **Backend: sin cambios** (endpoint y `users.google_sub` ya existen).
- **Frontend**: nuevo contexto de auth, cliente API y componentes; `frontend/.env.example` con
  `VITE_GOOGLE_CLIENT_ID`. Sin migraciones.

## 7. Criterios de aceptación

- AC-001 Con `VITE_GOOGLE_CLIENT_ID` configurado, el botón Google aparece; sin configurarlo, no se
  muestra y se indica cómo configurarlo.
- AC-002 Google válido de un usuario nuevo (rol elegido en UI) → sesión iniciada y header con el
  nombre; el JWT se usa en llamadas posteriores (`Authorization: Bearer`).
- AC-003 Google válido de una cuenta existente → sesión iniciada sin pedir rol.
- AC-004 Token de Google inválido/expirado/aud incorrecta → error 400 claro y sin sesión.
- AC-005 Cuenta PENDING/INACTIVE/SUSPENDED → 401 y se informa que la cuenta no está habilitada.
- AC-006 Rol ADMIN en cuenta nueva → 403 y no se crea sesión.
- AC-007 Refresh de página conserva o no la sesión según la estrategia decidida (HUMAN DECISION-2).
- AC-008 JWT vencido/ inválido en una llamada → se limpia la sesión y se pide login.
- AC-009 Contactar sin sesión abre el login; con sesión abre el modal de contacto (simulado).
- AC-010 Logout limpia la sesión y el header vuelve al estado deslogueado.
- AC-011 `npm run build` verde y sin logs de tokens.

## 8. Decisiones adoptadas (director, 2026-09-02)

1. **Rol para cuentas nuevas**: la UI envía el `id_token` sin rol; si el backend responde 400
   "role is required", se pide el rol (estudiante / profesor) y se reintenta con `role`.
2. **Almacenamiento de sesión**: **`sessionStorage`** (se pierde al cerrar la pestaña; sin refresh
   tokens todavía).
3. **Librería**: **`@react-oauth/google`** (dependencia nueva, justificada en ADR-016) con
   `GoogleOAuthProvider` condicionado a `VITE_GOOGLE_CLIENT_ID`.
4. **Login clásico**: se mantiene visible junto a Google (email/password ya funcionan).
5. **Testing de UI**: solo `npm run build` (typecheck + producción) + verificación manual; sin
   Vitest/Testing Library en esta etapa.

## 9. Out of scope

Refresh tokens y renovación silenciosa; otros proveedores; UI de perfil; backend de contacto
(CONTACT-001); recuperación de contraseña; MFA; tests E2E automatizados (salvo decisión en
HUMAN DECISION-5).

## 10. Change budget (estimación para el PLAN)

- **CREATE (frontend)**: `auth/AuthContext.tsx`, `components/LoginModal.tsx`,
  `components/GoogleButton.tsx` (o equivalente), `.env.example`.
- **MODIFY (frontend)**: `src/api.ts` (POST autenticado, manejo 401), `src/App.tsx` (header + flujo
  login/contacto), `index.html` (GIS si aplica), `src/styles.css`.
- **Backend**: sin cambios. **Migraciones**: ninguna. **Dependencias**: `@react-oauth/google`
  (solo si HUMAN DECISION-3 elige la librería; si no, ninguna).
- **Docs**: esta spec + ADR si se decide dependencia/flujo de sesión + notas de configuración.

## 11. Definition of Done

- [ ] Spec aprobada y HUMAN DECISIONS resueltas.
- [ ] Botón Google (condicional a config) + login email/password funcionando.
- [ ] Sesión con JWT + header + logout; `contactar` exige sesión.
- [ ] Manejo de loading/errores por código (400/401/403/409/red) y 401 global → limpiar sesión.
- [ ] Sin `id_token` persistido ni logueado; sin identidad enviada por el cliente.
- [ ] `npm run build` verde; verificación manual documentada; configuración Google Cloud explicada
      (orígenes autorizados, `VITE_GOOGLE_CLIENT_ID`).
- [ ] Change budget respetado; reportado al director.
