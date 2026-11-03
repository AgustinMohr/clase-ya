# VERIF-001 — Cola de revisión de documentación

**ID:** VERIF-001
**Estado:** Implementada (2026-10-30). E2E agregado, no ejecutado (puerto 8080 ocupado).
**Nivel:** 2 (capacidad nueva + migración + regla de negocio)
**Depende de:** `ADMIN-001`, `ADMIN-002`, `STOR-002`
**Relacionado:** `TEACHER-001`, `ADR-026`, `ADR-031`

---

## 1. Objetivo

Darle al ADMIN la pantalla para revisar la documentación de los profesores: cola de credenciales,
detalle con los documentos, **casilla de "revisado" por documento**, estado de escaneo real y la
decisión por credencial (`VERIFIED` / `REJECTED` / `MORE_INFO_REQUIRED`).

## 2. Contexto

Ya existe y se reutiliza:

- `GET /api/admin/verifications` (cola, default `UNDER_REVIEW`, más antiguos primero, paginada).
- `GET /api/admin/verifications/{teacherId}` (detalle: credenciales, documentos, historial).
- `POST .../credentials/{educationId}/decision` y `POST .../revoke` (auditoría append-only).
- Descarga admin con `attachment` + `nosniff` + `no-store` (STOR-002).

Falta: la UI de administración, el estado de escaneo en el detalle admin y una marca de revisión por
documento. Hoy el admin no tiene forma de registrar que ya miró un archivo.

## 3. Actores

- **ADMIN:** revisa y decide.
- **TEACHER:** presentó la credencial (no ve la marca de revisión).
- **Sistema:** guarda la marca y aplica la regla de decisión.

## 4. Decisiones

- **D1 — Casilla por documento.** `verification_documents` gana `reviewed_at` y `reviewed_by`
  (migración `V16`). Dos endpoints idempotentes: `POST` marca, `DELETE` desmarca. La identidad del
  revisor sale de `CurrentUser`.
- **D2 — El detalle admin usa su propio DTO.** `AdminVerificationDocumentView` agrega `scanStatus`,
  `reviewedAt` y `reviewedByName`. El DTO del profesor (`VerificationDocumentResponse`) **no
  cambia**: la revisión interna no se expone al profesor.
- **D3 — Verificar exige revisión completa.** `VERIFIED` requiere ≥1 documento y **todos**
  revisados; si falta alguno → 400 con mensaje explícito. `REJECTED` y `MORE_INFO_REQUIRED` no
  exigen revisión.
- **D4 — La marca es una ayuda de trabajo, no auditoría.** La auditoría real es
  `verification_decisions` (append-only). Desmarcar no borra decisiones.
- **D5 — Escaneo visible.** El detalle muestra `CLEAN` / `UNSCANNED` (y `INFECTED`/`PENDING` si
  aparecieran); `UNSCANNED` se resalta porque implica revisar con el antivirus local.
- **D6 — Cola con filtro de estado**, default `UNDER_REVIEW`; el detalle se abre al hacer click en
  una fila.
- **D7 — Revocar queda fuera de esta UI.** El endpoint existe; su pantalla es otra spec.

## 5. Requisitos funcionales

- **RF-1** `POST /api/admin/documents/{documentId}/reviewed` marca (204) y es idempotente.
- **RF-2** `DELETE /api/admin/documents/{documentId}/reviewed` desmarca (204) y es idempotente.
- **RF-3** Un no-ADMIN recibe 403; un documento inexistente 404.
- **RF-4** El detalle admin devuelve por documento: `scanStatus`, `reviewedAt`, `reviewedByName`.
- **RF-5** `decide(VERIFIED)` sin documentos o con alguno sin revisar → 400; no cambia el estado.
- **RF-6** `decide(REJECTED|MORE_INFO_REQUIRED)` sigue funcionando sin revisión previa y exige
  `method` (y `reason` para `MORE_INFO_REQUIRED`).
- **RF-7** `/admin/verificaciones` muestra la cola, permite filtrar por estado y abrir el detalle.
- **RF-8** El detalle agrupa documentos por credencial, con nombre, tipo, fecha, badge de escaneo,
  casilla "revisado" y descarga.
- **RF-9** Cada credencial en `UNDER_REVIEW` ofrece el formulario de decisión (acción, método,
  motivo). `VERIFIED` se deshabilita si faltan documentos sin revisar, con el motivo visible.
- **RF-10** La sección "Verificación" del `AdminLayout` pasa a `enabled: true`.

## 6. Requisitos no funcionales

- Sin N+1 en la cola (ya usa conteos por lote). El detalle carga los documentos del profesor en una
  consulta.
- La UI nunca scrollea horizontal; cola y detalle en `lg:grid-cols-[360px_1fr]`.
- Toda mutación refresca detalle y cola.

## 7. Seguridad

- `/api/admin/**` ADMIN-only; el actor sale de `CurrentUser`.
- La revisión y las decisiones no se exponen al profesor.
- La descarga sigue siendo adjunto con `no-store`.
- No se agrega ningún endpoint público.

## 8. Invariantes

- **I1** Una credencial no pasa a `VERIFIED` con documentos sin revisar.
- **I2** Marcar/desmarcar no altera el estado de la credencial.
- **I3** El DTO del profesor no incorpora datos internos de revisión.

## 9. API contract

```
POST   /api/admin/documents/{documentId}/reviewed  → 204 | 403 | 404
DELETE /api/admin/documents/{documentId}/reviewed  → 204 | 403 | 404

GET /api/admin/verifications/{teacherId}
200 {
  teacherId, teacherName, profileStatus,
  credentials: [{ educationId, institution, degree, status, submittedAt, documentCount, requirement }],
  documents: [{ id, educationId, type, originalFilename, contentType, sizeBytes, uploadedAt,
                scanStatus, reviewedAt, reviewedByName }],
  history: [{ id, educationId, documentId, decision, previousStatus, newStatus, method, reason,
              reviewerName, decidedAt }]
}
```

`POST .../decision` mantiene su contrato; agrega el 400 de RF-5.

## 10. Datos afectados

`V16__verification_document_review.sql`:

```sql
ALTER TABLE verification_documents
    ADD COLUMN reviewed_at timestamptz,
    ADD COLUMN reviewed_by uuid,
    ADD CONSTRAINT fk_verification_documents_reviewed_by
        FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE RESTRICT;
```

## 11. Criterios de aceptación

- **CA-1** Marcar dos veces → 204; `reviewedAt`/`reviewedByName` presentes; desmarcar → 204 y nulos.
- **CA-2** TEACHER → 403; documento inexistente → 404.
- **CA-3** `VERIFIED` con un documento sin revisar → 400 y la credencial sigue `UNDER_REVIEW`.
- **CA-4** `VERIFIED` con todo revisado → 200 y `profileStatus` recalculado.
- **CA-5** `REJECTED` sin revisar → 200.
- **CA-6** Frontend: cola + detalle + checkbox + decisión; `lint`, `test`, `build` verdes.
- **CA-7** E2E: admin abre la cola, revisa un documento, decide y la credencial sale de la cola.

## 12. Out of scope

- Revocar desde la UI (endpoint existe).
- Visor PDF, escaneo, notificaciones al profesor.
- Filtros por fecha o por profesor; búsqueda en la cola.
- Acciones masivas.

## 13. Change budget

- **Scope:** marca de revisión por documento, DTO admin, gate de decisión, UI de cola/detalle, tests.
- **Archivos:** `V16`, `VerificationDocument`, `VerificationDocumentRepository` (si hace falta),
  `VerificationDocumentService`, `VerificationDecisionService`,
  `AdminVerificationController`, `VerificationQueryService`,
  `verification/dto/AdminVerificationDocumentView`, `AdminVerificationDetail`,
  `api.ts`, `pages/AdminVerificationsPage.tsx`, `components/admin/AdminLayout.tsx`, `App.tsx`,
  tests backend/frontend/E2E, `docs/verification.md`, `DESIGN.md`.
- **Migraciones:** `V16` (no destructiva).
- **Dependencias:** ninguna.

## 14. Definition of Done

- [x] RF-1..RF-10 con tests (integración + componente).
- [x] `mvn -B clean test` en verde (407 tests).
- [x] `lint` (0), `test` (52/52) y `build` (tsc + CSP) verdes.
- [x] E2E `@admin` de revisión agregado (`tests/admin/admin-verification.spec.ts`); no ejecutado por el puerto 8080 ocupado.
- [x] `docs/verification.md` y `DESIGN.md` actualizados.
