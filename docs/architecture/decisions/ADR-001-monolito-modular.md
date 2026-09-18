# ADR-001 — Monolito modular

**Estado:** Aceptado (implementado en `com.claseya`).

## Contexto
La aplicación es un único backend. Se evaluó microservicios y se descartó para el alcance actual.

## Decisión
Construir un **monolito modular**: un solo artefacto Spring Boot y una sola base de datos, con el
código agrupado por dominios (`auth`, `academic`, `student`, `teacher`, `favorite`, `messaging`,
`security`, `user`, `common`, `model`). Los módulos `model` y `common` son hojas; el resto puede
usarlos, con reuso de helpers de presentación entre módulos permitido a nivel service/DTO.

## Consecuencias
- + Simplicidad de despliegue y operación; sin overhead de red ni consistencia distribuida.
- - La disciplina de límites entre paquetes es por convención (propuesta de ArchUnit en
  `docs/architecture/guardrails.md`, no aplicada).
- - Migrar a servicios requiere espec y ADR explícito.
