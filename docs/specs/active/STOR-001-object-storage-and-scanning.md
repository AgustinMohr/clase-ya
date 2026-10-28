# STOR-001 — Evidencia en R2 con escaneo ClamAV

**ID:** STOR-001
**Estado:** Aprobada — implementada (2026-10-28)
**Origen:** `STOR-01` de `review.md`; decisión del director: R2 + ClamAV.
**Relacionado:** `TEACHER-001` (D5), `ADR-026`.

---

## 1. Objetivo

Sacar los binarios de evidencia de PostgreSQL (`bytea`), guardarlos en un bucket privado
S3-compatible y escanearlos con ClamAV antes de persistirlos.

## 2. Decisiones

- **D1 — R2/S3 privado.** `DocumentStorage` se implementa con AWS SDK v2:
  - `PostgresDocumentStorage` (default local/tests) mantiene `content`.
  - `S3DocumentStorage` (`STORAGE_PROVIDER=s3`) sube a R2/S3 y guarda `storage_key`.
  - La API de carga **no cambia**: el backend sigue recibiendo el multipart; el bucket no se expone
    al navegador. La descarga sigue pasando por el endpoint autorizado.
- **D2 — Dual-read.** Filas legacy con `content` se leen igual; las nuevas tienen `storage_key` y
  `content IS NULL`. `V10` agrega la constraint `content IS NOT NULL OR storage_key IS NOT NULL`.
- **D3 — ClamAV síncrono y fail-closed.** Antes de guardar, `ClamAvDocumentScanner` habla INSTREAM
  con ClamAV. Infectado → `400`; escáner caído → `503`; nunca se guarda evidencia sin escanear.
  `scan_status` queda `CLEAN` (el valor `PENDING` queda reservado para una cola futura).
- **D4 — Sin URLs prefirmadas.** Se mantiene el flujo actual de descarga por el backend; no se
  expone la key. Una URL prefirmada/streaming directo queda para una fase posterior.
- **D5 — Borrado consistente.** Se borra la fila y se hace flush; luego el objeto. Si borrar el
  objeto falla, la transacción revierte y la fila (y su key) sobreviven.

## 3. Esquema — `V10__verification_object_storage.sql`

```sql
ALTER TABLE verification_documents ALTER COLUMN content DROP NOT NULL;
ALTER TABLE verification_documents
    ADD COLUMN storage_key varchar(512),
    ADD COLUMN scan_status varchar(20) NOT NULL DEFAULT 'CLEAN'
        CHECK (scan_status IN ('PENDING','CLEAN','INFECTED'));
ALTER TABLE verification_documents
    ADD CONSTRAINT chk_verification_documents_storage
        CHECK (content IS NOT NULL OR storage_key IS NOT NULL);
```

## 4. Configuración

`STORAGE_PROVIDER=postgres|s3`, `STORAGE_ENDPOINT`, `STORAGE_REGION` (R2: `auto`),
`STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`, `STORAGE_PATH_STYLE`;
`SCAN_ENABLED=true`, `CLAMAV_HOST`, `CLAMAV_PORT`, `CLAMAV_TIMEOUT_MS`.

Producción: `STORAGE_PROVIDER=s3` y `SCAN_ENABLED=true`. Sin el escáner, el sistema guarda evidencia
sin escanear (solo aceptable en local).

## 5. Tests

- `S3DocumentStorageTest`: key privada, put/get/delete con `S3Client` mockeado, 404 → 404 de dominio
  y dual-read de filas legacy.
- `ClamAvDocumentScannerTest`: OK, `FOUND` → 400, puerto cerrado → 503, contra un servidor TCP fake
  que implementa INSTREAM.
- `VerificationDocumentServiceTest` y `VerificationApiIntegrationTest` actualizados al contrato de
  storage; subida/descarga/borrado siguen verdes sobre PostgreSQL.
- E2E 40/40 (incluye subida de evidencia) con `globalSetup` que espera el seed demo.

## 6. Fuera de alcance

Cola asíncrona de escaneo, URLs prefirmadas de subida directa, backfill de filas legacy a R2,
retención/purga (D6), antivirus gestionado. El adaptador real contra R2 se valida en el despliegue
con las credenciales del bucket.

## 7. Change budget

- **Scope:** storage S3 + scanner ClamAV + V10 + configuración + tests + docs.
- **Archivos:** `DocumentStorage`, `PostgresDocumentStorage`, `S3DocumentStorage`,
  `DocumentScanner`, `NoopDocumentScanner`, `ClamAvDocumentScanner`,
  `VerificationDocumentService`, `VerificationDocument`, `DocumentScanStatus`, `V10`, `pom.xml`,
  tests, `application.yml`, `README.md`, `review.md`, `ADR-026`.
- **Migraciones:** `V10` (motivo: storage externo + estado de escaneo; no destructiva).
- **Dependencias:** `software.amazon.awssdk:s3` (cliente S3/R2). Motivo: object storage.

## 8. Definition of Done

- [x] R2/S3 seleccionable por entorno; PostgreSQL sigue siendo el default local/test.
- [x] ClamAV antes de almacenar, fail-closed.
- [x] V10 aplicada; dual-read de filas legacy.
- [x] Suite backend (324), lint/tsc/build y E2E (40/40) verdes.
- [x] Docs, ADR y `review.md` actualizados.
