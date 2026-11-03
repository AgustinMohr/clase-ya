# REVIEW2-003 — Hilo de mensajes con paginación keyset

**Estado:** Implementada y verificada (2026-11-03); pendiente de revisión final del director.
**Origen:** `review2.md` PERF-02. **Nivel:** 2 (endpoint y DTO nuevos).
**Relacionado:** `docs/messaging.md`, `CONTACT-001`, `REVIEW2-002`, `ADR-020`.

## 1. Objetivo

Abrir una conversación con **una sola request** que devuelva el detalle del hilo y su última página
de mensajes, y permitir cargar mensajes anteriores con paginación **keyset** (`before`), sin perder
historial, sin duplicar mensajes y sin depender de offsets.

## 2. Contexto

- `frontend/src/pages/MessagesPage.tsx` dispara 4-5 requests al abrir un hilo:
  `GET /api/conversations/{id}`, `GET /api/conversations/{id}/messages?page=0&size=100`, a veces otra
  página más, `PATCH /api/conversations/{id}/read` y `GET /api/conversations`.
- La UI solo muestra la **última página de 100 mensajes** y no ofrece “cargar anteriores”. En
  conversaciones largas se pierde historial visible.
- `GET /{id}/messages` usa offset (`page`/`size`): si entra un mensaje nuevo mientras se navega,
  una página posterior puede duplicar o saltear filas.
- `SearchResultPage` no expone un cursor, así que el cliente no puede pedir “los anteriores a este
  mensaje”.
- `V4__messaging_support.sql` ya tiene `idx_messages_conversation_created (conversation_id,
  created_at)`, suficiente para el rango y el orden del keyset.

## 3. Actores

- **STUDENT / TEACHER** participante de la conversación.
- **Sistema / SPA**: consume el endpoint y renderiza el hilo.

## 4. Requisitos funcionales

- **RF-1** `GET /api/conversations/{conversationId}/thread` devuelve, en un único DTO:
  `id`, `otherParticipant`, `unreadCount`, `updatedAt`, la última página de `messages` (orden
  **cronológico ascendente**) y los cursores `hasMore` / `nextBefore`.
- **RF-2** `limit` es opcional, default **50**, rango **1..100**. Fuera de rango → `400` con
  `ApiError`.
- **RF-3** `before=<messageId>` devuelve hasta `limit` mensajes **estrictamente anteriores** a ese
  mensaje. El ancla debe pertenecer a la misma conversación; si no existe o pertenece a otra →
  `404` (no se filtra su existencia).
- **RF-4** La conversación se resuelve desde el `SecurityContext` (`CurrentUser`). Un usuario que no
  participa recibe `404`.
- **RF-5** La SPA abre el hilo con **una** llamada a `/thread`, muestra “Cargar mensajes anteriores”
  cuando `hasMore` es verdadero y, al hacer clic, antepone los mensajes anteriores **conservando la
  posición de scroll**.
- **RF-6** Enviar un mensaje sigue funcionando y se refleja en el hilo; el endpoint `POST
  /{id}/messages` no cambia.
- **RF-7** Los endpoints existentes `GET /{id}` y `GET /{id}/messages` se mantienen sin cambios
  (compatibilidad); la SPA deja de usarlos para abrir el hilo.

## 5. Requisitos no funcionales

- **RNF-1** Número constante de consultas por request: conversación/acceso, otro participante,
  contador de no leídos y una consulta de mensajes con `join fetch` del remitente. Sin N+1.
- **RNF-2** La consulta keyset usa el orden estable `(created_at, id)` y reutiliza
  `idx_messages_conversation_created`; no requiere migración nueva.
- **RNF-3** El endpoint es de solo lectura y no bloquea escrituras del mismo hilo.
- **RNF-4** La respuesta no crece con el historial: como máximo `limit` mensajes.

## 6. Seguridad

- La identidad siempre sale de `SecurityContext`; el request **no** acepta `userId`,
  `participantId` ni `senderId`.
- Un no participante recibe **404**, igual que un `before` ajeno o inexistente.
- El DTO no expone emails, direcciones, coordenadas, tokens ni hashes. Solo `displayName`, `role`,
  contenido y metadatos del mensaje.
- El rate limiting existente sigue aplicando; no se agregan rutas públicas.

## 7. Invariantes

- **I1** `messages` siempre viene ordenado de más viejo a más nuevo.
- **I2** Con `before`, todos los mensajes devueltos son estrictamente anteriores al ancla
  (`created_at < ancla.created_at`, o `created_at = ancla.created_at && id < ancla.id`).
- **I3** Llamadas sucesivas con `nextBefore` no duplican ni saltean mensajes.
- **I4** `hasMore` es verdadero si y solo si existen mensajes más viejos que el más viejo devuelto.
- **I5** `nextBefore` es el id del mensaje más viejo devuelto; es `null`/ausente si no hay mensajes.
- **I6** Los endpoints viejos conservan su contrato y comportamiento.

## 8. API contract

```http
GET /api/conversations/{conversationId}/thread?limit=2
Authorization: Bearer <participant>
```

```json
{
  "id": "2f1c...",
  "otherParticipant": {
    "id": "9a0b...",
    "displayName": "Ana Prof",
    "role": "TEACHER"
  },
  "unreadCount": 2,
  "updatedAt": "2026-11-03T12:00:00Z",
  "messages": [
    {
      "id": "m3",
      "conversationId": "2f1c...",
      "senderId": "9a0b...",
      "senderDisplayName": "Ana Prof",
      "content": "Mensaje 3",
      "createdAt": "2026-11-03T11:58:00Z"
    },
    {
      "id": "m4",
      "conversationId": "2f1c...",
      "senderId": "9a0b...",
      "senderDisplayName": "Ana Prof",
      "content": "Mensaje 4",
      "createdAt": "2026-11-03T11:59:00Z"
    }
  ],
  "hasMore": true,
  "nextBefore": "m3"
}
```

```http
GET /api/conversations/{conversationId}/thread?before=m3&limit=2
```

Devuelve los dos mensajes inmediatamente anteriores a `m3` (`m1`, `m2`), con
`hasMore`/`nextBefore` recalculados. `readAt` y `nextBefore` se omiten cuando son `null`, igual que
el resto de los DTOs del proyecto.

**Errores**

| Status | Caso |
|---|---|
| `400` | `limit < 1` o `limit > 100`, o `before` no es un UUID válido |
| `401` | Sin sesión válida |
| `404` | Conversación inexistente, no participante, o `before` fuera de la conversación |

## 9. Datos afectados

- Sin tablas nuevas ni columnas nuevas.
- Sin migración: se reutiliza `idx_messages_conversation_created` (`V4`). El desempate por `id`
  solo ordena filas con el mismo `created_at`.
- No se modifican entidades JPA ni constraints.

## 10. Criterios de aceptación

- **AC-1** Con 5 mensajes y `limit=2`, el thread devuelve `msg 3`, `msg 4`, `hasMore=true` y
  `nextBefore=msg3`.
- **AC-2** Con `before=msg3&limit=2`, devuelve `msg 1`, `msg 2`, `hasMore=true` y
  `nextBefore=msg1`.
- **AC-3** Con `before=msg0&limit=2`, devuelve `messages=[]`, `hasMore=false` y sin `nextBefore`.
- **AC-4** Un no participante recibe `404`; un `before` de otra conversación también.
- **AC-5** `limit=0` y `limit=101` devuelven `400`.
- **AC-6** La SPA abre el hilo con una llamada a `/thread` (más `PATCH /read` y el refresco de la
  lista), muestra el botón cuando `hasMore`, y al cargar anteriores antepone sin duplicar y sin
  saltar el scroll al fondo.
- **AC-7** Los tests existentes de `GET /{id}/messages` y `GET /{id}` siguen verdes.

## 11. Out of scope

- WebSockets o mensajería en tiempo real.
- Editar/borrar mensajes.
- Eliminar los endpoints viejos de mensajes.
- Cursor para la lista de conversaciones.
- Búsqueda de texto en mensajes.
- Migraciones o índices nuevos.
- Cambios de autenticación, autorización o rate limiting.

## 12. Definition of Done

- [x] Spec aprobada por el director (2026-11-03).
- [x] Backend: DTO, endpoint `GET /{id}/thread`, consultas keyset y validación de `limit`/`before`.
- [x] Tests de integración: última página, `before`, fin de historial, 404 de ownership, 400 de
      `limit`.
- [x] Frontend: `api.conversationThread`, `MessagesPage` con “Cargar anteriores” y preservación de
      scroll; test unitario del cliente HTTP.
- [x] `docs/messaging.md` y `review2.md` actualizados.
- [x] `mvn -B clean test` (427 tests) y `npm test` (55/55) verdes; `tsc`, ESLint y build con CSP OK.
- [x] Sin migración ni dependencias nuevas.
