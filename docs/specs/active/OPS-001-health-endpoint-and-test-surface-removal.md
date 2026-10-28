# OPS-001 — Retiro de `/api/test/*` y health check operativo

**ID:** OPS-001
**Estado:** Aprobada — implementada (2026-10-28)
**Origen:** hallazgos `SEC-03` y `OPS-01` de `review.md`.
**Depende de:** `SEC-002`, `ADR-020`.

---

## 1. Objetivo

Sacar del artefacto desplegable el andamiaje de pruebas de seguridad (`/api/test/*`, que devuelve
`userId`, `email` y `role` a cualquier autenticado) y reemplazar la sonda de salud de Playwright por
un endpoint operativo real.

## 2. Contexto

- `web/SecurityTestController` existe desde la fase inicial y sigue mapeado en producción.
- Playwright lo usa como health check (`/api/test/public`).
- No hay Actuator ni ningún endpoint de salud/liveness.
- `SEC-03` y `OPS-01` comparten la misma dependencia: sin un reemplazo, retirar el controller rompe
  la suite E2E.

## 3. Decisiones

- **D1** Se elimina `web/SecurityTestController` y todas las reglas `/api/test/**` de
  `SecurityConfig`. No queda ningún endpoint de prueba en el artefacto.
- **D2** Se agrega `spring-boot-starter-actuator` exponiendo **solo** `health`, con
  `show-details=never`. Nada de métricas, env, beans ni heap en la web.
- **D3** El indicador de salud de **mail se desactiva**: el SMTP es opcional (sin él el registro
  responde `503`) y no debe marcar la aplicación como `DOWN` ni disparar reinicios en la plataforma.
- **D4** `GET /actuator/health` es público; el resto de `/actuator/**` no existe (no está expuesto).
- **D5** Los tests que usaban `/api/test/*` pasan a endpoints reales:
  roles → `/api/favorites`, `/api/teachers/me`, `/api/admin/verifications`; identidad →
  `/api/auth/me`; descubrimiento público → `/api/teachers`.

## 4. API y configuración

| Endpoint | Acceso | Respuesta |
|---|---|---|
| `GET /actuator/health` | público | `200 {"status":"UP"}`; `503` si la base no responde |

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
  health:
    mail:
      enabled: false
```

## 5. Tests

1. `GET /actuator/health` sin token → `200` y `status=UP`, sin `components`.
2. `GET /api/test/public` con un Bearer válido → `404` (el endpoint ya no existe).
3. Matriz de roles reescrita contra endpoints reales (STUDENT/TEACHER/ADMIN).
4. Regresión: identidad con token válido, inválido, vencido y usuario suspendido → `/api/auth/me`.
5. E2E: Playwright espera `/actuator/health` para considerar el backend listo.

## 6. Fuera de alcance

Métricas (`/actuator/metrics`, Prometheus), logs estructurados, request IDs, trazas y alertas
(resto de `OPS-01`); usuario no-root y `HEALTHCHECK` del Dockerfile (`OPS-02`).

## 7. Change budget

- **Scope:** eliminar el controller de prueba y sus reglas; agregar Actuator health; reescribir los
  tests que dependían del andamiaje; actualizar la sonda de Playwright y la documentación.
- **Archivos:** `SecurityConfig`, `pom.xml`, `application.yml`, `web/SecurityTestController` (delete),
  `AuthIntegrationTest`, `frontend/playwright.config.ts`, `README.md`, `AGENTS.md`,
  `docs/architecture/{architecture,guardrails,security}.md`, `review.md`.
- **Migraciones:** ninguna.
- **Dependencias:** `spring-boot-starter-actuator` (versión del BOM de Spring Boot). Motivo: health
  probe para el orquestador sin exponer métricas ni detalles.

## 8. Definition of Done

- [x] `/api/test/*` eliminado del código y de la configuración de seguridad.
- [x] `GET /actuator/health` público y sin detalles; mail excluido.
- [x] Tests backend y E2E verdes sin el andamiaje.
- [x] Docs y `review.md` actualizados.
- [x] Sin cambios de schema ni de contrato de producto.
