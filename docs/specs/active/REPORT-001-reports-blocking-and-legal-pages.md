# REPORT-001 — Reportes, bloqueo y páginas legales

**Estado:** Aprobada por el director e implementada (2026-11-11).
**Nivel:** 2 (endpoints, migración, UI y regla de negocio).
**Origen:** `hallazgos-2026-11-11.md` 1.8.
**Relacionado:** `CONTACT-001`, `RATE-001`, `ADMIN-001`, `ADMIN-002`, `docs/product/product-requirements.md` §7/§8/§9.

## 1. Objetivo

Cerrar el hueco de confianza y legal: permitir reportar y bloquear usuarios desde una conversación,
dar a administración una cola de reportes y publicar páginas reales de términos, privacidad y ayuda
con consentimiento explícito en el registro.

## 2. Contexto

- `reports` existe desde `V1` (`reporter_id`, `reported_user_id`, `reason`, `description`, `status`,
  `created_at`, `resolved_at`) pero no tiene entidad, repositorio, endpoint ni UI.
- No existe forma de bloquear a otro usuario.
- `notifications` existe pero su flujo queda fuera de alcance.
- `Footer` muestra “Términos · Privacidad · Ayuda” como texto/botones sin ruta.
- El registro no declara mayoría de edad ni responsabilidad del adulto, aunque el PRD §4/§9 exige
  que la cuenta de un menor la opere su adulto responsable.

## 3. Actores

- **STUDENT / TEACHER**: reporta y bloquea a la otra parte de una conversación.
- **ADMIN**: revisa, resuelve o rechaza reportes.
- **Visitante**: lee términos, privacidad y ayuda.
- **Sistema**: valida identidad desde `CurrentUser`, aplica bloqueo y expone la cola.

## 4. Requisitos funcionales

- **RF-1** `POST /api/reports` crea un reporte del usuario autenticado contra otro usuario.
  `reportedUserId`, `reason` y `description` opcional (máx. 1000).
- **RF-2** No se permite auto-reporte (`400`) ni un segundo reporte `OPEN`/`REVIEWING` del mismo
  reporter contra el mismo reported (`409`).
- **RF-3** `GET /api/admin/reports?status=&page=&size=` lista reportes para ADMIN, más nuevos
  primero, default `status=OPEN`, `size=20`, máximo 50. Incluye nombre visible de reporter y
  reported (nunca email).
- **RF-4** `PATCH /api/admin/reports/{id}` acepta `RESOLVED` o `REJECTED`; solo desde
  `OPEN`/`REVIEWING`; setea `resolved_at`. Un reporte terminal devuelve `409`; uno inexistente
  `404`.
- **RF-5** `POST /api/blocks/{userId}` bloquea; `DELETE /api/blocks/{userId}` desbloquea;
  `GET /api/blocks` lista los usuarios bloqueados por el actor. Auto-bloqueo `400`.
- **RF-6** Si existe bloqueo en cualquier dirección entre dos usuarios, iniciar una conversación
  entre ellos devuelve `403` y enviar un mensaje en su conversación devuelve `403`. La conversación
  existente sigue visible y legible; solo se corta la escritura/contacto.
- **RF-7** La UI de conversación muestra “Reportar” y “Bloquear” en el encabezado. Reportar abre un
  modal con motivo y detalle. Bloquear pide confirmación, deshabilita el composer y ofrece
  “Desbloquear”.
- **RF-8** La UI administrativa en `/admin/reportes` lista reportes, filtra por estado y permite
  resolver/rechazar con confirmación.
- **RF-9** Rutas públicas `/terminos`, `/privacidad` y `/ayuda` con contenido real; el `Footer`
  navega a ellas.
- **RF-10** El registro por email exige un checkbox: “Declaro ser mayor de 18 años o tutor legal y
  acepto los Términos y la Política de Privacidad”. Sin marcarlo no se puede enviar el formulario.
  La aceptación es una declaración de UI; no se persiste en esta iteración.

## 5. Requisitos no funcionales

- Consultas paginadas y batcheadas: la cola admin resuelve nombres con join, sin N+1.
- La verificación de bloqueo es una consulta indexada por par; no agrega latencia perceptible al
  envío de mensajes.
- Migración aditiva e idempotente; sin backfill.
- La UI de reportes/bloqueo reutiliza `Button`, `Field`, `Modal`/primitivas y tokens del Design
  System; no se agrega librería de UI.

## 6. Seguridad

- La identidad del reporter/blocker sale siempre de `SecurityContext` (`CurrentUser`); el request
  nunca acepta `reporterId` ni `blockerId`.
- ADMIN es el único rol que lista y resuelve reportes.
- El reported no ve quién lo reportó ni el detalle del reporte.
- DTOs con `displayName`, rol, motivo, descripción y timestamps; nunca email, dirección, tokens ni
  hashes.
- Un usuario bloqueado no recibe un mensaje revelador: la API responde un `403` genérico.
- `POST /api/reports` y `POST/DELETE /api/blocks` entran en el rate limiting existente de
  `RATE-001`.

## 7. Invariantes

- **I1** Un usuario no puede reportarse ni bloquearse a sí mismo.
- **I2** Un par reporter/reported tiene como máximo un reporte `OPEN`/`REVIEWING`.
- **I3** Solo `OPEN`/`REVIEWING` pueden pasar a `RESOLVED`/`REJECTED`; el estado terminal no vuelve.
- **I4** Un bloqueo en cualquier dirección impide nuevas conversaciones y nuevos mensajes, pero no
  borra el historial.
- **I5** La cola admin nunca expone emails ni la identidad del reporter al reported.
- **I6** Las páginas legales son públicas, estáticas y no dependen de sesión.

## 8. API contract

```http
POST /api/reports
Authorization: cookie de sesión o Bearer
Content-Type: application/json

{ "reportedUserId": "9a0b...", "reason": "HARASSMENT", "description": "Insultos reiterados" }
```

`201 Created`

```json
{ "id": "r1...", "status": "OPEN", "reason": "HARASSMENT", "createdAt": "2026-11-11T12:00:00Z" }
```

Errores: `400` auto-reporte o motivo inválido; `401` sin sesión; `404` reported inexistente;
`409` reporte abierto duplicado.

```http
GET /api/admin/reports?status=OPEN&page=0&size=20
```

```json
{
  "content": [
    {
      "id": "r1...",
      "reporterDisplayName": "Ana",
      "reportedDisplayName": "Bruno",
      "reason": "HARASSMENT",
      "description": "Insultos reiterados",
      "status": "OPEN",
      "createdAt": "2026-11-11T12:00:00Z",
      "resolvedAt": null
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

```http
PATCH /api/admin/reports/r1.../resolution
Content-Type: application/json

{ "status": "RESOLVED" }
```

`204 No Content`. Errores: `400` estado no terminal; `404` inexistente; `409` ya resuelto.

```http
POST   /api/blocks/{userId}
DELETE /api/blocks/{userId}
GET    /api/blocks
```

`GET /api/blocks` devuelve `[{ "userId": "...", "displayName": "Bruno" }]`.

Errores de bloqueo: `400` auto-bloqueo; `401` sin sesión; `404` usuario inexistente; `409` ya
bloqueado.

## 9. Datos afectados

Migración `V22__reports_blocks_and_queue.sql`, justificada:

```sql
create table user_blocks (
  id           uuid primary key default gen_random_uuid(),
  blocker_id   uuid not null references users (id) on delete cascade,
  blocked_id   uuid not null references users (id) on delete cascade,
  created_at   timestamptz not null default now(),
  constraint uq_user_blocks_pair unique (blocker_id, blocked_id),
  constraint chk_user_blocks_no_self check (blocker_id <> blocked_id)
);
create index idx_user_blocks_blocked_id on user_blocks (blocked_id);

create unique index uq_reports_open_pair
  on reports (reporter_id, reported_user_id)
  where status in ('OPEN', 'REVIEWING');
create index idx_reports_status_created on reports (status, created_at desc);
```

Entidades nuevas: `Report` (tabla existente `reports`) y `UserBlock`; enums `ReportReason`
(`HARASSMENT`, `SPAM`, `FAKE_PROFILE`, `INAPPROPRIATE`, `OTHER`) y `ReportStatus`
(`OPEN`, `REVIEWING`, `RESOLVED`, `REJECTED`). No se alteran columnas existentes.

## 10. Criterios de aceptación

- **AC-1** Un STUDENT reporta a un TEACHER con motivo válido: `201`, fila `OPEN`; repetir el
  reporte abierto devuelve `409`.
- **AC-2** Auto-reporte devuelve `400`.
- **AC-3** ADMIN lista la cola `OPEN` y resuelve un reporte: `204`, `resolved_at` seteado; volver a
  resolverlo devuelve `409`.
- **AC-4** Un STUDENT bloquea a un TEACHER: `POST /api/conversations` con ese TEACHER devuelve
  `403`; el docente bloqueado tampoco puede iniciar ni responder.
- **AC-5** Desbloquear restaura el contacto y el envío de mensajes.
- **AC-6** Un usuario que no es ADMIN recibe `403` en `/api/admin/reports`.
- **AC-7** `/terminos`, `/privacidad` y `/ayuda` responden 200, el Footer navega a ellas y la SPA no
  rompe el deep link.
- **AC-8** El formulario de registro no se envía sin el checkbox de mayoría de edad/consentimiento.
- **AC-9** Tests de integración cubren ownership, duplicados, bloqueo bidireccional y cola admin.

## 11. Out of scope

- Notificaciones/email al reportar o resolver.
- Suspensión automática del reported.
- Reportes de opiniones (ya existe moderación de reviews) y reportes por mensaje individual.
- Persistencia server-side de la aceptación de términos.
- Apelaciones, historial de auditoría y métricas de abuso.
- Bloqueo desde el perfil público del profesor (solo desde una conversación existente).

## 12. Definition of Done

- [x] Spec aprobada por el director.
- [x] Migración `V22` creada y validada con `ddl-auto=validate` (la suite completa la aplica en CI).
- [x] Endpoints, servicios, repositorios y DTOs con tests unitarios e integración escritos.
- [x] Bloqueo aplicado en contacto y mensajería sin filtrar la existencia de la conversación.
- [x] UI de conversación, admin y páginas legales con tests de frontend.
- [ ] `mvn -B clean test` completo: pendiente de Docker/CI en esta máquina (Docker apagado).
      `test-compile`, tests unitarios y `ArchitectureTest` verdes locally.
- [x] `npm test` (99), `npm run lint`, `npm run build` y `tsc --noEmit` verdes.
- [x] Docs de API/seguridad y este hallazgo actualizados.
