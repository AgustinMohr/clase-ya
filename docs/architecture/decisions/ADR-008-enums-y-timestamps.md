# ADR-008 — Enums varchar+CHECK y timestamps UTC

**Estado:** Aceptado (implementado).

## Contexto
Representar valores de dominio (roles, estados, modalidades, modos) y timestamps de forma
consistente y portable.

## Decisión
- Enums almacenados como `varchar` + **CHECK** (no tipos enum de PostgreSQL) y mapeados con
  `@Enumerated(EnumType.STRING)`: cambiar valores = migración simple y visible en dumps.
- Timestamps como `timestamptz` (UTC) ↔ `Instant`; los tests fuerzan JVM UTC.

## Consecuencias
- + Portabilidad, dumps legibles y valores controlados por CHECK.
- - Nuevos valores de enum requieren migración (vista como ventaja).
- - Todo datetime debe tratarse en UTC (regla de testing: `-Duser.timezone=UTC`).
