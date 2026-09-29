# docs/specs — Especificaciones de producto

Este directorio gobierna las specs de funcionalidad. **No se implementa ninguna feature de
producto sin una spec aprobada** (ver `docs/development-workflow.md`).

Una spec se exige **solo para cambios de Nivel 2 (feature de producto)**. Los cambios de Nivel 0
(no funcionales) y Nivel 1 (cambios chicos de comportamiento) tienen camino corto y **no generan
archivos**: el nivel se decide con la tabla de "Niveles de cambio" en `docs/development-workflow.md`.

## Carpetas

- `active/` — specs aprobadas y en curso (o en review).
- `completed/` — specs ya implementadas y verificadas (se conservan como histórico/evidencia).
- Este README define el formato obligatorio.

## Formato obligatorio de una spec

Aplica a toda spec que se escriba (Nivel 2). Cada spec es un archivo `SPEC-XXX-slug-corto.md` en
`active/` (o `completed/`). Secciones obligatorias:

1. **ID** — `SPEC-XXX` (número correlativo) y título.
2. **Objetivo** — qué problema resuelve, en una o dos frases.
3. **Contexto** — situación actual del repo que motiva la spec (referencias a ADRs/módulos).
4. **Actores** — quiénes intervienen (rol: STUDENT/TEACHER/ADMIN, sistema, admin de datos...).
5. **Requisitos funcionales** — lista numerada `RF-1..n`, concreta y verificable.
6. **Requisitos no funcionales** — rendimiento/consulta (anti-N+1), concurrencia, escalado esperado.
7. **Seguridad** — autenticación/autorización/ownership/visibilidad y qué campos NO se exponen.
8. **Invariantes** — reglas que el sistema nunca debe violar (unicidades, estados, datos derivados).
9. **API contract (si corresponde)** — endpoints, métodos, status HTTP, request/response ejemplo y
   errores.
10. **Datos afectados** — tablas/entidades (¿migración `V{n+1}`? ¿índices?).
11. **Criterios de aceptación** — escenarios de prueba concretos (cada RF debería trazarse aquí).
12. **Out of scope** — qué NO se implementa en esta spec.
13. **Definition of Done** — checklist específico (además del DoD general de AGENTS.md).

## Reglas

- El nivel del cambio define si hace falta spec: Nivel 2 **sí**; Nivel 0 y 1 **no** (ver
  `docs/development-workflow.md`).
- No inventar requisitos: toda decisión debe ser trazable a esta spec (o a un ADR si es técnica).
- Si una spec necesita cambiar arquitectura/seguridad/schema/contrato fuera de su alcance, se
  reporta antes de seguir.
- Una spec pasa a `completed/` solo cuando la implementación está verificada y los tests verdes.
- Las ambigüedades con impacto arquitectónico se resuelven en REVIEW con el arquitecto/director,
  no por suposición del agente.
