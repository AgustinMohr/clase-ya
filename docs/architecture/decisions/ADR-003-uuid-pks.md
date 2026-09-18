# ADR-003 — UUID como claves primarias

**Estado:** Aceptado (implementado).

## Contexto
Elegir una estrategia de IDs coherente para todas las tablas.

## Decisión
Claves primarias **UUID** en todas las tablas: default de columna `gen_random_uuid()` y
`@GeneratedValue(strategy = GenerationType.UUID)` en JPA. Se descartó `BIGSERIAL`.

## Consecuencias
- + IDs no adivinables/escaneables en recursos (menor riesgo de enumeración); cómodo para merges y
  referencias externas.
- + Un único estilo en todo el schema.
- - Índices algo más grandes y UUIDs ilegibles en logs/URLs (aceptado).
