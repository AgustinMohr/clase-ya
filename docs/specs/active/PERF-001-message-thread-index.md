# PERF-001 — Índice del hilo de mensajes

**Estado:** Aprobada por el director e implementada (2026-11-11).
**Nivel:** 2 (migración de esquema, sin cambio de comportamiento).
**Origen:** `hallazgos-2026-11-11.md` sección 2, “Índice de hilo de mensajes”.
**Relacionado:** `REVIEW2-003`, `V4`, `V11`.

## 1. Objetivo

Servir la paginación keyset del hilo (`where conversation_id = ? order by created_at desc, id
desc`) con un índice compuesto, sin escanear el hilo completo.

## 2. Contexto

- `V4` crea `idx_messages_conversation_created (conversation_id, created_at)` (ascendente).
- `V11` crea un índice parcial `idx_messages_unread (conversation_id) WHERE read_at IS NULL`.
- `MessageRepository.findLatestThreadPage` y `findThreadPageBefore` ordenan por
  `(created_at desc, id desc)`; el desempate por `id` no está cubierto.
- La consulta trae `limit + 1` filas, así que el índice debe devolver el prefijo sin sort extra.

## 3. Requisitos

- **RF-1** Crear `idx_messages_conversation_created_id (conversation_id, created_at desc, id
  desc)`.
- **RF-2** No eliminar ni editar índices históricos; `V4`/`V11` se conservan.
- **RF-3** El test de esquema exige el índice nuevo para evitar una degradación silenciosa.

## 4. Datos afectados

Migración `V24__message_thread_index.sql`; solo índice, sin tablas/columnas/datos.

## 5. Criterios de aceptación

- **AC-1** `pg_indexes` contiene `idx_messages_conversation_created_id`.
- **AC-2** `EXPLAIN (ANALYZE, BUFFERS)` del hilo no muestra `Sort` sobre el hilo completo con
  volumen suficiente (verificación manual en producción).
- **AC-3** La suite de mensajería sigue verde.

## 6. Out of scope

- Índices de `rating_average`/`price_per_hour` (se medirán aparte).
- Cambios de consulta o contrato.
- Drop de índices existentes.

## 7. Definition of Done

- [x] Migración `V24`.
- [x] Test de esquema actualizado.
- [x] Docs de base de datos actualizados.
- [ ] `mvn -B clean test` completo (Docker/CI).
