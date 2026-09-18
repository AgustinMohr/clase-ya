# ADR-010 — Ownership por SecurityContext y DTOs que nunca son entidades

**Estado:** Aceptado (implementado).

## Contexto
Evitar IDOR y exponer el modelo JPA en la API.

## Decisión
- La identidad que actúa sale siempre del `SecurityContext` vía `security.CurrentUser`; los
  endpoints nunca aceptan `userId`/`studentId`/`senderId`/`participantIds` para decidir quién
  actúa, y el alcance del recurso es por usuario autenticado.
- Las respuestas son **records DTO** (con factories `from(entity)`); nunca se devuelven entidades
  JPA ni campos sensibles (email/address/coordenadas exactas/hashes/tokens).

## Consecuencias
- + El IDOR queda estructuralmente difícil y cubierto por tests.
- + Contrato de API estable y sin fugas.
- - Cada módulo mantiene su conjunto de DTOs (coste de mantenimiento aceptado).
