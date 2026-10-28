# OPS-002 — Endurecimiento del contenedor

**ID:** OPS-002
**Estado:** Aprobada — en implementación (2026-10-28)
**Origen:** hallazgo `OPS-02` de `review.md`.
**Depende de:** `OPS-001` (health endpoint).

---

## 1. Objetivo

La imagen final corría como **root**, no declaraba `HEALTHCHECK` y arrancaba la JVM sin un límite de
memoria acorde al contenedor.

## 2. Decisiones

- **D1 — Usuario no privilegiado.** La etapa final crea `app` (`groupadd`/`useradd --system`) y
  ejecuta con `USER app`. El JAR se copia con `--chown=app:app`.
- **D2 — Health check real.** `HEALTHCHECK` contra `GET /actuator/health` (OPS-001), cada 30 s,
  timeout 5 s, `start-period` 60 s (Flyway + arranque) y 3 reintentos. Un `503` de health marca el
  contenedor como `unhealthy`.
- **D3 — Memoria.** `-XX:MaxRAMPercentage=75` para respetar el límite del contenedor.
- **D4 — Contexto de build limpio.** `application-local.yml` (gitignoreado) y los `.env` quedan
  fuera de la imagen vía `.dockerignore`; nunca se cuece configuración local en el artefacto.
- **D5 — Los tests no corren en la imagen.** El build sigue con `-DskipTests`; la suite corre en CI
  (`mvn clean verify`). La imagen solo empaqueta.

## 3. Verificación

1. `docker build` de la imagen final.
2. `docker run --entrypoint id` → usuario `app`, no root.
3. Contenedor contra PostgreSQL local → `GET /actuator/health` responde `200` y
   `docker inspect` reporta `healthy`.
4. `docker inspect` del `HEALTHCHECK` y del `User`.

## 4. Fuera de alcance

Escaneo de imagen (Trivy/Grype), firma, SBOM, `docker-compose` y `DEV-01`. No se cambia la imagen
base ni el stack.

## 5. Change budget

- **Scope:** Dockerfile de runtime + `.dockerignore` + docs.
- **Archivos:** `Dockerfile`, `.dockerignore`, `README.md` (si aplica), `review.md`.
- **Migraciones:** ninguna.
- **Dependencias:** ninguna.

## 6. Definition of Done

- [x] La imagen corre como usuario no-root.
- [x] `HEALTHCHECK` contra `/actuator/health` y `-XX:MaxRAMPercentage=75`.
- [x] Configuración local fuera del contexto de build.
- [x] Build y health verificados localmente.
- [x] `review.md` actualizado.
