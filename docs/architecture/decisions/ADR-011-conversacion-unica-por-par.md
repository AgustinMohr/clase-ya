# ADR-011 — Una conversación por par (estudiante-profesor) en V1

**Estado:** Aceptado (implementado en `messaging`).

## Contexto
Evitar conversaciones duplicadas cuando un estudiante contacta varias veces al mismo profesor, con
un modelo genérico de participantes (Conversation N:N User).

## Decisión
En V1 hay **una única conversación por par** student-teacher: al crear se busca la existente entre
los dos usuarios y se devuelve (HTTP 200) en lugar de crear otra.

**Actualización (RACE-01, V12):** la unicidad ya **no** depende del service. El par es la clave
natural de la conversación, así que vive en `conversation_pairs`
`(student_user_id, teacher_user_id)` con PK compuesta y `conversation_id` único. La creación hace
`INSERT ... ON CONFLICT DO NOTHING`; el request que pierde borra su conversación candidata (vacía) y
reutiliza la del ganador. La carrera de doble POST queda cerrada por la base, no por una lectura
previa.

## Consecuencias
- + UX simple y sin conversaciones duplicadas en el flujo normal.
- + Garantía de unicidad por par en la base, incluidas requests concurrentes (RACE-01).
- + Las conversaciones legacy sin fila en `conversation_pairs` siguen funcionando por el lookup
  previo; no se hizo backfill para no colisionar con pares ya duplicados.
- - Si el producto requiere "reabrir/agrupar" conversaciones, cambiará la regla (requiere spec).
