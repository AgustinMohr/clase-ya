# ADR-011 — Una conversación por par (estudiante-profesor) en V1

**Estado:** Aceptado (implementado en `messaging`).

## Contexto
Evitar conversaciones duplicadas cuando un estudiante contacta varias veces al mismo profesor, con
un modelo genérico de participantes (Conversation N:N User).

## Decisión
En V1 hay **una única conversación por par** student-teacher: al crear se busca la existente entre
los dos usuarios y se devuelve (HTTP 200) en lugar de crear otra. La regla se garantiza en el
**service** + tests porque no es expresable como constraint SQL simple sobre
`conversation_participants`. La carrera de creación simultánea se documenta como limitación
aceptada (un raro doble POST podría crear dos; el service resuelve determinísticamente después).

## Consecuencias
- + UX simple y sin conversaciones duplicadas en el flujo normal.
- - Sin garantía DB absoluta de unicidad por par (limitación documentada).
- - Si el producto requiere "reabrir/agrupar" conversaciones, cambiará la regla (requiere spec).
