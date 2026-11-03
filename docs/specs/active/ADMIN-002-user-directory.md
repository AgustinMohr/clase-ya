# ADMIN-002 — Directorio de usuarios y estado de cuenta

**ID:** ADMIN-002
**Estado:** Aprobada — implementada (2026-10-30). Ejecución E2E pendiente (puerto 8080 ocupado).
**Nivel:** 2 (endpoint nuevo + lectura de datos de cuentas + acción de seguridad)
**Depende de:** ADMIN-001 (shell del panel)
**Relacionado:** `TEACHER-001` (estado de verificación), `AUTH-001`, `ADR-020`.

---

## 1. Objetivo

Darle al ADMIN un directorio paginado de cuentas (profesores y estudiantes) con los datos
mínimos para operar, y permitirle cambiar el estado de una cuenta (activar, suspender, restaurar)
desde el panel. Es la puerta de entrada al módulo siguiente: la revisión de documentación.

## 2. Contexto

- `AdminAccountController` ya expone `POST /api/admin/users/{userId}/activate` (PENDING → ACTIVE)
  pero **no hay forma de listar usuarios** ni de suspender.
- `User` tiene `email`, `name`, `role` (`STUDENT|TEACHER|ADMIN`), `status`
  (`PENDING|ACTIVE|INACTIVE|SUSPENDED`), `createdAt`, `lastLoginAt`, `emailVerifiedAt`.
- `JwtAuthenticationFilter` **carga la cuenta en cada request** y solo autentica si
  `AppUserDetails.isEnabled()` (status `ACTIVE`). Por eso suspender surte efecto en el request
  siguiente del usuario, sin esperar a que expire el token.
- `TeacherSpecifications.visible()` exige `user.status = ACTIVE`: un profesor suspendido
  desaparece de la búsqueda pública y de su detalle (404) en el acto.
- El `email` no se expone en DTOs públicos ni personales; acá es la identificación de la cuenta
  para soporte (decisión D1).
- `SearchResultPage` (`teacher.dto`) ya es la forma de página compartida (`content`, `page`,
  `size`, `totalElements`, `totalPages`).

## 3. Actores

- **ADMIN:** consulta el directorio y cambia estados.
- **STUDENT / TEACHER:** aparecen listados; no acceden al panel.
- **Sistema:** autenticación (el estado se evalúa por request) y visibilidad pública.

## 4. Decisiones

- **D1 — El email se muestra al ADMIN.** Es el identificador de la cuenta para soporte y ya es
  visible para el usuario dueño en otros flujos. Sigue prohibido en DTOs públicos y personales.
  El directorio no expone `passwordHash`, `googleSub` ni `email_verified_at`.
- **D2 — Estudiantes con datos mínimos.** Solo `name`, `email`, `status`, `createdAt` y si tiene
  perfil de estudiante (`studentProfileComplete`). Nada académico: son menores.
- **D3 — Profesores con estado de verificación.** `teacherVerificationStatus` para poder ver qué
  falta revisar. La completitud del anuncio queda para el detalle (evita subqueries por fila).
- **D4 — Una sola consulta con proyección.** El listado se resuelve con una consulta que proyecta
  el DTO y hace `LEFT JOIN` a `teacher_profiles`/`student_profiles`; prohibido resolverlo con N+1.
- **D5 — Orden fijo.** `createdAt DESC, id ASC` (paginación estable). No hay `sort` elegido por el
  cliente: no hace falta whitelist.
- **D6 — Suspender es inmediato y reversible.** Se apoya en el chequeo por request. La UI lo trata
  como acción reversible: ejecuta y ofrece "Deshacer" (§12 de `DESIGN.md`).
- **D7 — Sin tabla de auditoría nueva.** Las transiciones de estado emiten una línea de log
  estructurada (`adminAction`, `actorId`, `targetUserId`, `from`, `to`). Una tabla `admin_actions`
  queda fuera de alcance; se documenta como deuda.
- **D8 — Acciones explícitas, sin `PATCH` genérico.** Se mantiene `/activate` (contrato existente)
  y se agregan `/suspend` y `/restore`. No se cambia el contrato actual.

## 5. Requisitos funcionales

- **RF-1** `GET /api/admin/users` devuelve `SearchResultPage<AdminUserSummary>` con filtros
  opcionales `role`, `status` y `query` (email o nombre, sin distinguir mayúsculas).
- **RF-2** Paginación: `page` ≥ 0 (0-based), `1 ≤ size ≤ 50`, default 20. Otro valor → `400`.
- **RF-3** `query` se recorta, acepta 2–100 caracteres y escapa `%` y `_` para que no actúen como
  comodines. Con menos de 2 caracteres → `400`.
- **RF-4** Cada fila trae `id`, `email`, `name`, `role`, `status`, `createdAt`, `lastLoginAt`,
  `teacherVerificationStatus` (solo TEACHER) y `studentProfileComplete` (solo STUDENT).
- **RF-5** `POST /api/admin/users/{id}/suspend`: `ACTIVE → SUSPENDED` (204). Si ya estaba
  `SUSPENDED`, 204 idempotente. Cualquier otro estado → 409. Suspender la propia cuenta → 409.
- **RF-6** `POST /api/admin/users/{id}/restore`: `SUSPENDED → ACTIVE` (204). Si ya estaba `ACTIVE`,
  204 idempotente. Cualquier otro estado → 409.
- **RF-7** `POST /api/admin/users/{id}/activate` se mantiene igual (PENDING → ACTIVE).
- **RF-8** Usuario inexistente → 404.
- **RF-9** Un usuario suspendido recibe 401 en su siguiente request, aunque su token siga vigente.
- **RF-10** Un profesor suspendido desaparece de `/api/teachers` y su detalle responde 404.
- **RF-11** En la UI, `/admin/usuarios` muestra filtros (rol, estado, búsqueda con Enter/botón),
  listado, estado vacío, skeletons y paginación. Cada fila ofrece la acción que corresponde a su
  estado, deshabilitada para la propia cuenta.
- **RF-12** La sección "Usuarios" del `AdminLayout` pasa a `enabled: true`.

## 6. Requisitos no funcionales

- Una consulta de listado + una de conteo por request; sin N+1.
- Respuesta acotada: `size` máximo 50.
- La UI nunca scrollea horizontal: filas en tarjeta, no tabla HTML.
- Acciones con feedback inmediato (toast) y refresco de la fila afectada.

## 7. Seguridad

- `/api/admin/**` ya exige `ROLE_ADMIN`; el guard del frontend es solo UX.
- El actor sale de `security.CurrentUser`, nunca del cliente.
- No se exponen hashes, `googleSub`, tokens ni datos académicos de estudiantes.
- El email solo aparece en este DTO de administración.
- Suspender una cuenta no borra datos: es reversible.
- Las transiciones quedan en el log de la aplicación con el `requestId` del filtro existente.

## 8. Invariantes

- **I1** Un STUDENT/TEACHER nunca accede a `/api/admin/**`: `403`.
- **I2** El estado de una cuenta se evalúa en cada request; no hay sesiones "zombi".
- **I3** Un ADMIN no puede suspenderse a sí mismo.
- **I4** Ninguna acción de este módulo modifica roles ni credenciales.

## 9. API contract

```
GET /api/admin/users?role=TEACHER&status=ACTIVE&query=ana&page=0&size=20
200 {
  "content": [{
    "id": "uuid",
    "email": "ana@example.com",
    "name": "Ana",
    "role": "TEACHER",
    "status": "ACTIVE",
    "createdAt": "2026-10-01T12:00:00Z",
    "lastLoginAt": "2026-10-30T09:00:00Z",
    "teacherVerificationStatus": "UNDER_REVIEW",
    "studentProfileComplete": null
  }],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}

POST /api/admin/users/{id}/suspend  → 204 | 404 | 409
POST /api/admin/users/{id}/restore  → 204 | 404 | 409
POST /api/admin/users/{id}/activate → 204 | 404 | 409   (existente)
```

Errores con la forma compartida `ApiError`.

## 10. Datos afectados

Sin migración. Solo se actualiza `users.status` (y `updated_at`).

## 11. Criterios de aceptación

- **CA-1** `GET` sin filtros devuelve todas las cuentas paginadas y ordenadas por `createdAt DESC`.
- **CA-2** Filtros `role`, `status` y `query` combinados devuelven el subconjunto correcto;
  `query=100%` no matchea todo (escape de comodines).
- **CA-3** `query` de 1 carácter, `size=0`, `size=51` o `page=-1` → 400.
- **CA-4** Anónimo → 401; STUDENT → 403; ADMIN → 200.
- **CA-5** Suspender a un estudiante activo → 204; su siguiente request autenticado → 401.
- **CA-6** Suspender un profesor activo lo saca de `/api/teachers` y su detalle da 404; restaurarlo
  lo devuelve.
- **CA-7** Autosuspenderse → 409.
- **CA-8** Suspender dos veces → 204 la segunda vez; restaurar dos veces → 204 la segunda vez.
- **CA-9** La respuesta no contiene `passwordHash`, `googleSub` ni `emailVerifiedAt`.
- **CA-10** Frontend: la página lista, filtra, pagina y ejecuta suspender/restaurar con toast;
  `npm run lint`, `npm test`, `npm run build` verdes.
- **CA-11** E2E: admin entra a `/admin/usuarios`, encuentra un estudiante, lo suspende (la fila
  muestra `SUSPENDIDA`) y lo restaura.

## 12. Out of scope

- Detalle de la cuenta y de sus credenciales/documentos (`verification-review`).
- Acciones masivas, export CSV, edición de perfiles, borrado de cuentas, reset de contraseña.
- Notificar por email al usuario suspendido.
- Tabla de auditoría `admin_actions` (deuda explícita, D7).
- Orden configurable y filtros por fecha.
- Cambios de rol desde la UI.

## 13. Change budget

- **Scope:** endpoint de listado + transiciones suspend/restore + página de directorio + paginación
  compartida + tests + docs.
- **Archivos esperados:**
  - backend nuevos: `auth/controller/AdminUserDirectoryController.java`,
    `auth/service/AdminUserService.java`, `auth/dto/AdminUserSummaryResponse.java`,
    `auth/repository/AdminUserDirectoryRepository.java`
  - backend modificados: `auth/controller/AdminAccountController.java` (suspend/restore),
    `auth/service/AccountActivationService.java` solo si se reutiliza (si no, no se toca)
  - frontend nuevos: `pages/AdminUsersPage.tsx`, `components/ui/Pagination.tsx`
  - frontend modificados: `api.ts`, `App.tsx`, `components/admin/AdminLayout.tsx` (enable
    `users`), `pages/SearchPage.tsx` (usa `Pagination`, refactor sin cambio de comportamiento)
  - tests: `AdminDirectoryIntegrationTest` (backend), `AdminUsersPage.test.tsx`,
    `Pagination.test.tsx`, `tests/admin/admin-users.spec.ts` (E2E)
  - docs: `DESIGN.md` (ruta + patrón de suspender con Deshacer), `README.md` si aplica
- **Migraciones esperadas:** ninguna.
- **Dependencias nuevas esperadas:** ninguna.

## 14. Definition of Done

- [x] RF-1..RF-12 implementados y trazables a tests (integración + componente).
- [x] `mvn -B clean test` en verde (388 tests).
- [x] `npm run lint` (0), `npm test` (48/48) y `npm run build` (tsc + CSP) verdes.
- [ ] E2E `@admin` del directorio agregado (`tests/admin/admin-users.spec.ts`); no ejecutado por el puerto 8080 ocupado.
- [x] Sin cambios de contrato fuera de los endpoints declarados.
- [x] `DESIGN.md` actualizado (rutas, patrón y `Pagination`).
