# ADMIN-001 — Shell del panel de administración

**ID:** ADMIN-001
**Estado:** Aprobada — implementada (2026-10-30). Ejecución E2E pendiente (puerto 8080 ocupado).
**Nivel:** 2 (superficie de administración nueva; rol y visibilidad)
**Origen:** mapa de capacidades aprobado: `admin-shell` → `admin-directory` → `evidence-safety` →
`verification-review`.
**Relacionado:** `TEACHER-001`, `AUTH-001`, `SEC-002`, `ARCH-001`, `ADR-020`.

---

## 1. Objetivo

Habilitar una superficie de administración separada de la experiencia pública: solo una sesión
`ADMIN` puede entrar, y el panel nace con su layout, navegación y guardas listas para los módulos
siguientes (directorio y revisión de documentación), **sin exponer ningún dato todavía**.

## 2. Contexto

- `GET /api/auth/me` ya devuelve `{ id, name, role }` (`SessionUserResponse`), y la SPA lo resuelve
  al montar porque el JWT vive en una cookie HttpOnly (`ADR-020`). **No hace falta un endpoint nuevo
  para saber si la sesión es admin.**
- `/api/admin/**` ya exige `hasRole("ADMIN")` en `SecurityConfig`; los controllers existentes son
  `AdminAccountController` (`/api/admin/users`) y `AdminVerificationController` (`/api/admin`).
- `ProtectedRoute` hoy tipa `role?: 'STUDENT' | 'TEACHER'`.
- El seed demo trae `admin@claseya.dev` / `Password123` (ACTIVE, ADMIN) justamente para probar el
  panel.
- En producción **no existe forma de crear un ADMIN** (`ADMIN` no puede auto-registrarse). El alta
  es un `UPDATE` manual documentado (§11).
- `ARCH-001`: router en modo librería, cada pantalla con URL propia y `React.lazy` con fallback de
  skeletons.

## 3. Actores

- **ADMIN:** única cuenta que ve y usa el panel.
- **STUDENT / TEACHER:** no ven la entrada ni el panel.
- **Sistema:** guardas de ruta y autorización de API.

## 4. Decisiones

- **D1 — El shell no agrega API.** Se apoya en `/api/auth/me` y en la autorización existente. Esta
  spec no crea endpoints ni toca el backend.
- **D2 — `403`, no `404`.** Una sesión sin rol ADMIN ve la pantalla de acceso restringido (la misma
  `ProtectedRoute`) y la API responde `403`, como ya lo hace `SecurityConfig`. La existencia del
  panel no es un secreto del producto; el `404` sigue reservado para recursos ajenos
  (conversaciones, documentos).
- **D3 — Entrada en el Navbar solo para ADMIN**, con el mismo patrón `buttonStyles({ variant:
  'ghost', size: 'sm' })` que "Mi anuncio" y "Favoritos". Para el resto no existe en el DOM.
- **D4 — Layout propio.** `/admin` usa una barra lateral en `lg+` y tabs horizontales en mobile
  (nunca scroll horizontal de página). Reutiliza `Card`, `EmptyState`, `Badge`, `Skeleton`. El
  `Footer` público no se renderiza en `/admin`.
- **D5 — Navegación declarativa y sin enlaces muertos.** La lista de secciones es tipada
  (`id`, `label`, `icon`, `path`, `enabled`). Una sección `enabled: false` no se renderiza como
  enlace; los próximos módulos se agregan a esa lista.
- **D6 — Sin datos en el resumen.** El resumen muestra identidad del admin (nombre + rol) y el
  estado "sin módulos todavía". Nada de conteos: llegan con `admin-directory`.
- **D7 — Carga diferida** de la página con `React.lazy`, igual que el resto.

## 5. Requisitos funcionales

- **RF-1** `/admin` es privada: sin sesión se ofrece el login y, al autenticarse, se entra al panel.
- **RF-2** Con sesión no-ADMIN se muestra "No tenés acceso" y no se renderiza ningún contenido del
  panel.
- **RF-3** Con sesión ADMIN se ve el layout, el nombre del admin, el título "Administración" y la
  sección "Resumen".
- **RF-4** El Navbar muestra la entrada "Admin" solo para ADMIN; para STUDENT, TEACHER y anónimo no
  se renderiza.
- **RF-5** Cerrar sesión estando en `/admin` redirige al inicio (`/admin` entra en
  `isPrivatePath`).
- **RF-6** La sección activa se marca con `aria-current="page"`.
- **RF-7** El título del documento es "Administración | ClaseYa" (`usePageMeta`).

## 6. Requisitos no funcionales

- Ninguna llamada de red nueva al cargar `/admin` más allá de la sesión que ya resuelve la app.
- Accesibilidad: `nav` con `aria-label="Administración"`, foco visible, targets ≥44px en mobile.
- Responsive: `lg:grid-cols-[240px_1fr]`; en mobile las tabs scrollean **dentro** de su contenedor.
- Dark/light por tokens; sin colores hardcodeados.

## 7. Seguridad

- La autoridad es la API: el guard del frontend es UX. `/api/admin/**` sigue exigiendo
  `ROLE_ADMIN`.
- No se exponen datos nuevos: el shell no llama endpoints y no muestra emails ni PII.
- La entrada del Navbar no se renderiza para otros roles (no es un enlace oculto por CSS).
- La sesión sigue siendo cookie HttpOnly; no se agrega ningún token legible por JS.

## 8. Invariantes

- **I1** Un STUDENT o TEACHER nunca ve contenido del panel ni un enlace a él.
- **I2** El shell no crea ni modifica datos.
- **I3** Ninguna ruta `/admin/**` queda accesible sin pasar por `ProtectedRoute role="ADMIN"`.

## 9. API contract

Sin cambios. `GET /api/auth/me` sigue devolviendo `{ id, name, role }`.

## 10. Datos afectados

Ninguno. Sin migración.

## 11. Operación: alta del primer ADMIN

No se agrega endpoint (auto-promoverse sería un agujero). En el deploy real, promover una cuenta
existente desde la consola SQL de Neon:

```sql
UPDATE users SET role = 'ADMIN' WHERE lower(email) = lower('tu-correo@dominio');
```

La cuenta debe estar `ACTIVE`. En local, el seed ya trae `admin@claseya.dev` / `Password123`.
Queda documentado en el README.

## 12. Criterios de aceptación

- **CA-1** `isPrivatePath('/admin')` y `isPrivatePath('/admin/cualquier-cosa')` → `true`.
- **CA-2** Con sesión ADMIN, `AdminPage` renderiza layout, nombre y "Resumen".
- **CA-3** Con sesión STUDENT, muestra el gate de acceso restringido y no el contenido.
- **CA-4** `Navbar`: con ADMIN muestra "Admin"; con STUDENT, TEACHER o anónimo no aparece.
- **CA-5** Logout desde `/admin` navega a `/`.
- **CA-6** `npm run lint`, `npm test` y `npm run build` verdes.
- **CA-7** E2E con `admin@claseya.dev`: login → `/admin` visible; login de estudiante → acceso
  restringido.

## 13. Out of scope

- Listado y detalle de usuarios (`admin-directory`).
- Cola de revisión, descargas y decisiones (`evidence-safety`, `verification-review`).
- Acciones de cuenta (activar/suspender) en la UI.
- Endpoints nuevos, cambios de schema, cualquier cambio de backend.
- Vista del panel para roles distintos de ADMIN.

## 14. Change budget

- **Scope:** ruta + guard + entrada de Navbar + layout/página del shell + tests + docs.
- **Archivos esperados:**
  - nuevos: `frontend/src/pages/AdminPage.tsx`, `frontend/src/components/admin/AdminLayout.tsx`
  - modificados: `frontend/src/routes.ts`, `frontend/src/App.tsx`,
    `frontend/src/components/routing/ProtectedRoute.tsx`, `frontend/src/components/ui/Navbar.tsx`
  - tests: `routes` (`isPrivatePath`), `AdminPage` y `Navbar` (nuevos o ampliados)
  - docs: `docs/frontend/DESIGN.md` (tabla de rutas) y `README.md` (runbook del ADMIN)
- **Migraciones esperadas:** ninguna.
- **Dependencias nuevas esperadas:** ninguna.

## 15. Definition of Done

- [x] RF-1..RF-7 implementados y trazables a tests (unit/componente).
- [x] `lint` (0 errores), `test` (39/39) y `build` (tsc + CSP) verdes.
- [ ] E2E `@admin`: spec agregado en `tests/admin/admin.spec.ts`; no ejecutado porque el backend local ocupa 8080 con cookies `Secure`.
- [x] Ningún endpoint nuevo; `/api/admin/**` intacto.
- [x] `DESIGN.md` y `README.md` actualizados.
- [ ] Reportado al humano.
