# ADR-026 — Evidencia en object storage con escaneo (propuesto)

**Estado:** Aceptado (STOR-001, implementado)
**Fecha:** 2026-10-28
**Relacionado:** `STOR-01` de `review.md`, `TEACHER-001` (D5), `ADR-009`.

> **Nota de implementación:** el escaneo quedó **síncrono en la carga** (ClamAV INSTREAM) y
> fail-closed, en lugar de una cola asíncrona: así nunca se guarda evidencia sin escanear y no se
> necesita un worker para el MVP. `scan_status` conserva `PENDING` para una futura cola. La descarga
> sigue pasando por el backend (sin URLs prefirmadas); ambas simplificaciones están en `STOR-001`.

## Contexto

La evidencia de verificación vive en `verification_documents.content bytea` (V8). Se carga completa
en heap, crece con la base y no tiene antivirus ni cuarentena. La decisión D5 documentó el costo,
pero no lo resolvió. El director eligió la opción (a): **object storage privado + URLs prefirmadas +
escaneo**.

## Decisión propuesta

1. **Bucket privado S3-compatible** (Cloudflare R2 recomendado; AWS S3 o MinIO como alternativas).
   Sin acceso público. Cifrado en reposo del proveedor.
2. **La API de carga no cambia:** el profesor sigue subiendo multipart a ClaseYa; el backend valida
   magic bytes/SHA-256 y sube el objeto al bucket con una key opaca
   (`verification/{teacherId}/{uuid}`). No se expone el bucket al navegador para escritura.
3. **Descarga con URL prefirmada de corta vida** (60 s), generada solo después de autorizar el
   documento. Alternativa aceptable: streaming por el backend desde S3; se decide al implementar.
4. **Escaneo asíncrono** (ClamAV sidecar/worker o servicio gestionado) con estados
   `PENDING | CLEAN | INFECTED`. Solo `CLEAN` se sirve; `PENDING`/`INFECTED` quedan en cuarentena y
   el ADMIN ve el estado. Si el escáner no está disponible, el documento queda `PENDING` (fail-closed).
5. **Esquema `V10`:**
   ```sql
   ALTER TABLE verification_documents ALTER COLUMN content DROP NOT NULL;
   ALTER TABLE verification_documents ADD COLUMN storage_key varchar(512);
   ALTER TABLE verification_documents ADD COLUMN scan_status varchar(20) NOT NULL DEFAULT 'CLEAN';
   -- CHECK: content IS NOT NULL OR storage_key IS NOT NULL
   ```
   Las filas existentes conservan `content` (`CLEAN`) y se leen con **dual-read**; un backfill
   opcional las migra al bucket.
6. **Dependencia:** AWS SDK v2 `software.amazon.awssdk:s3` (S3-compatible con endpoint custom).
   Tests con MinIO (Testcontainers) o un `ObjectStorage` en memoria; el escáner se mockea.

## Prerrequisitos para implementar

- Proveedor y bucket: R2 / S3 / MinIO.
- Credenciales (`STORAGE_ENDPOINT`, `STORAGE_REGION`, `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`,
  `STORAGE_SECRET_KEY`).
- Escáner: ClamAV sidecar/worker o servicio gestionado, y su política de timeout/reintentos.
- Retención/purga (D6): cuánto tiempo se guarda la evidencia y quién puede borrarla.

## Consecuencias

- **Positivas:** la base deja de crecer con binarios; cuarentena real; backups más chicos.
- **Costos:** dependencia y credenciales nuevas, V10, cambios en `DocumentStorage`/servicio/tests,
  un worker de escaneo y monitoreo de la cola. La descarga deja de ser un `byte[]` en memoria.
- **Riesgo:** sin el escáner, no se debe habilitar la subida a producción; el estado `PENDING` debe
  ser visible para el ADMIN.
