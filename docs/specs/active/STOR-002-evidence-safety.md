# STOR-002 — Evidencia segura sin escáner (PDF-only y estado honesto)

**ID:** STOR-002
**Estado:** Aprobada — implementada (2026-10-30). Smoke en Render pendiente.
**Nivel:** 2 (cambio de postura de seguridad + migración)
**Depende de:** `STOR-001` (storage + ClamAV), `TEACHER-001` (circuito de verificación)
**Relacionado:** `ADMIN-002`, `ADR-026`, `ADR-031` (nuevo)
**Origen:** el backend corre en Render Free, donde no hay un ClamAV alcanzable. Hoy eso deja la
subida de evidencia en 503 y, mientras tanto, el guard de arranque obliga a `SCAN_ENABLED=true`.

---

## 1. Objetivo

Permitir que el profesor suba evidencia y que el ADMIN la revise **sin un antivirus en el
servidor**, con el riesgo declarado en vez de simulado: solo PDF, estado de escaneo honesto
(`UNSCANNED`), descarga forzada como adjunto y un interruptor explícito que habilita evidencia sin
escanear.

## 2. Contexto

Lo que **ya existe** y no se toca:

- `DocumentContentValidator` valida por *magic bytes* (no por extensión ni `Content-Type`), tope de
  10 MB, nombre saneado y SHA-256; cuotas de 5 documentos / 25 MB por credencial.
- `ClamAvDocumentScanner` es fail-closed: infectado → 400, escáner caído → 503, y nunca se guarda.
- Ambos endpoints de descarga (`GET /api/teachers/me/documents/{id}/content` y
  `GET /api/admin/documents/{id}/content`) ya usan `ContentDisposition.attachment()`; Spring
  Security ya agrega `X-Content-Type-Options: nosniff` en todas las respuestas (con test).
- `verification_documents.scan_status` tiene `CHECK (PENDING, CLEAN, INFECTED)` y el servicio
  escribe **`CLEAN` siempre**, incluso cuando el escáner es el no-op. Eso es mentir en los datos.
- `ProductionConfigValidator` exige `SCAN_ENABLED=true` fuera de `local`/`test`, sin excepción.

Lo que cambia: el formato aceptado, el valor registrado en `scan_status`, la condición del guard y
dos headers de descarga.

## 3. Actores

- **TEACHER:** sube la evidencia.
- **ADMIN:** la descarga y la revisa con su antivirus local.
- **Sistema:** valida formato, guarda y registra el estado real del escaneo.

## 4. Decisiones

- **D1 — Solo PDF.** `DocumentContentValidator` acepta únicamente `%PDF-`. JPG y PNG se rechazan
  con 400. Menos parsers en el servidor y menos superficie en el navegador. No hay conversión de
  formatos.
- **D2 — Estado honesto.** Se agrega `UNSCANNED` al enum y al `CHECK` (migración `V15`). El
  `DocumentScanner` expone el estado que corresponde registrar: `CLEAN` si escaneó de verdad,
  `UNSCANNED` si es el no-op. El servicio deja de hardcodear `CLEAN`.
- **D3 — El riesgo se declara, no se esconde.** `ProductionConfigValidator` deja de exigir
  `SCAN_ENABLED` incondicionalmente: fuera de `local`/`test`, arrancar sin escáner requiere
  `ALLOW_UNSCANNED_EVIDENCE=true`. Sin ese ack el arranque falla, con un mensaje que nombra la
  variable. El invariante "no se guarda evidencia sin escanear salvo que alguien lo haya declarado"
  se mantiene.
- **D4 — Descarga segura.** Ambos endpoints agregan `Cache-Control: no-store`. El
  `Content-Disposition: attachment` y el `nosniff` ya existen y no se cambian. El filename del
  header se restringe a un charset seguro (`[A-Za-z0-9._ -]`, fallback `document.pdf`) para evitar
  inyección de headers.
- **D5 — Sin visor en la app.** Renderizar un PDF no confiable en el navegador es justamente lo que
  el `attachment` evita. Un visor requiere rasterizar en el servidor (PDFBox → imágenes) o PDF.js
  en un iframe sandboxeado con cambios de CSP: spec aparte.
- **D6 — El grupo de archivos legacy no se migra.** Filas con `content_type` de imagen siguen
  descargándose como adjunto; no se convierten ni se borran.

## 5. Requisitos funcionales

- **RF-1** Un PDF válido por magic bytes se acepta (201) con `content_type = application/pdf`.
- **RF-2** Un JPG, un PNG o cualquier otro formato se rechaza con 400 y mensaje explícito; no se
  crea fila.
- **RF-3** Con `SCAN_ENABLED=false` la fila nueva queda `scan_status = UNSCANNED`.
- **RF-4** Con `SCAN_ENABLED=true` y escaneo OK la fila queda `CLEAN`; infectado → 400 sin fila;
  escáner inalcanzable → 503 sin fila (comportamiento actual, se conserva).
- **RF-5** Fuera de `local`/`test`, con `SCAN_ENABLED=false` y `ALLOW_UNSCANNED_EVIDENCE=false`, la
  aplicación **no arranca** y el mensaje nombra `ALLOW_UNSCANNED_EVIDENCE`.
- **RF-6** Con `ALLOW_UNSCANNED_EVIDENCE=true` y `SCAN_ENABLED=false` la aplicación arranca.
- **RF-7** Ambas descargas responden `Content-Disposition: attachment`,
  `X-Content-Type-Options: nosniff`, `Cache-Control: no-store` y el `Content-Type` almacenado.
- **RF-8** El dueño descarga lo suyo; el ADMIN descarga cualquiera (con log de auditoría, ya
  existente); un tercero recibe 404.
- **RF-9** Un nombre de archivo con comillas, saltos de línea o caracteres de control no rompe el
  header ni el registro.

## 6. Requisitos no funcionales

- Sin dependencias nuevas: no entra PDFBox ni una librería de visores.
- Sin cambios de cuota, tamaño máximo ni del flujo de guardado (escaneo → storage → transacción).
- La migración es no destructiva y aplica sobre una tabla con datos (solo amplía el `CHECK`).

## 7. Seguridad

- La evidencia sigue siendo privada: solo el dueño y el ADMIN, por endpoints autorizados.
- `UNSCANNED` es visible para el ADMIN; el módulo de revisión mostrará el aviso (fuera de alcance).
- Riesgo residual aceptado y documentado en `ADR-031`: sin antivirus, un PDF malicioso puede
  afectar a quien lo abra. Mitigaciones: descarga forzada (nunca render inline), `nosniff`,
  `no-store`, acceso solo ADMIN, log de auditoría y revisión con el antivirus local del operador.
- `ALLOW_UNSCANNED_EVIDENCE` no relaja ninguna otra validación (autorización, cuotas, formato).

## 8. Invariantes

- **I1** Nunca se sirve evidencia inline: siempre `attachment`.
- **I2** El `scan_status` registrado refleja lo que realmente pasó.
- **I3** Sin escáner, no se guarda evidencia salvo ack explícito en el entorno.
- **I4** Un archivo rechazado no deja fila ni objeto huérfano.

## 9. API contract

Sin cambios de rutas ni de cuerpos. Solo cambian las respuestas de error del formato (400) y los
headers de descarga. La subida sigue siendo multipart `file` + `type`.

## 10. Datos afectados

Migración `V15__scan_status_unscanned.sql`:

```sql
ALTER TABLE verification_documents DROP CONSTRAINT verification_documents_scan_status_check;
ALTER TABLE verification_documents ADD CONSTRAINT verification_documents_scan_status_check
    CHECK (scan_status IN ('PENDING', 'CLEAN', 'INFECTED', 'UNSCANNED'));
```

Los valores históricos (`CLEAN`) quedan como están.

## 11. Configuración

| Variable | Default | Uso |
|---|---|---|
| `SCAN_ENABLED` | `false` | Escaneo ClamAV antes de guardar |
| `ALLOW_UNSCANNED_EVIDENCE` | `false` | Ack explícito para arrancar sin escáner fuera de `local`/`test` |

## 12. Criterios de aceptación

- **CA-1** `DocumentContentValidator`: PDF acepta; JPEG/PNG/texto rechazan 400; extensión mentirosa
  no engaña (magic bytes).
- **CA-2** Subida con `SCAN_ENABLED=false` → 201 y `scan_status=UNSCANNED` en la respuesta y en la
  base.
- **CA-3** `ClamAvDocumentScanner` OK → `CLEAN`; `FOUND` → 400; puerto cerrado → 503.
- **CA-4** `ProductionConfigValidatorTest`: sin ack falla; con ack arranca; con `SCAN_ENABLED=true`
  no hace falta ack.
- **CA-5** `VerificationApiIntegrationTest`: descarga del dueño y del ADMIN con los tres headers;
  tercero → 404.
- **CA-6** La migración `V15` aplica sobre la base de test y el `CHECK` acepta `UNSCANNED`.
- **CA-7** `mvn -B clean test`, `npm run lint`, `npm test`, `npm run build` verdes.
- **CA-8** Smoke en Render Free: subir un PDF → 201 → el ADMIN lo descarga como adjunto → el
  antivirus local lo analiza al abrirlo.

## 13. Out of scope

- Visor PDF en la app, rasterización o PDF.js.
- Escaneo asíncrono con cuarentena (`PENDING` sigue reservado).
- Desplegar ClamAV.
- Convertir o rechazar imágenes históricas.
- El badge y el aviso de "sin escanear" en la UI de revisión (`verification-review`).
- Notificar al profesor el resultado de la revisión.

## 14. Change budget

- **Scope:** PDF-only, `UNSCANNED`, guard con ack, headers de descarga, migración `V15`, tests,
  docs y `ADR-031`.
- **Archivos esperados:**
  - backend: `verification/service/DocumentContentValidator.java`,
    `model/enums/DocumentScanStatus.java`, `verification/service/DocumentScanner.java`,
    `NoopDocumentScanner.java`, `ClamAvDocumentScanner.java`,
    `VerificationDocumentService.java`, `security/ProductionConfigValidator.java`,
    `verification/controller/TeacherVerificationController.java`,
    `verification/controller/AdminVerificationController.java`,
    `src/main/resources/application.yml`
  - migración: `V15__scan_status_unscanned.sql`
  - tests: `DocumentContentValidatorTest` (nuevo), `ClamAvDocumentScannerTest`,
    `VerificationDocumentServiceUploadTest`, `ProductionConfigValidatorTest`,
    `VerificationApiIntegrationTest`
  - docs: `docs/verification.md`, `README.md`,
    `docs/architecture/decisions/ADR-031-unscanned-evidence.md`
- **Migraciones esperadas:** `V15` (ampliar el `CHECK` de `scan_status`; no destructiva).
- **Dependencias nuevas esperadas:** ninguna.

## 15. Definition of Done

- [x] RF-1..RF-9 implementados y trazables a tests.
- [x] `mvn -B clean test` en verde (401 tests).
- [x] `lint` (0), `test` (48/48) y `build` del frontend verdes (sin cambios de frontend).
- [x] `README`, `docs/verification.md` y `ADR-031` actualizados.
- [ ] Smoke en Render Free: subir PDF, descargar como adjunto y revisar con el antivirus local (pendiente de deploy).
