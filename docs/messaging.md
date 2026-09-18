# ClaseYa — Internal Messaging (Phase 6)

REST messaging between students and teachers built on the Phase 1 messaging model. Students start
a conversation with a teacher; both participants read/reply; messages can be marked as read. No
WebSockets yet — the services are controller-independent so a future WebSocket adapter can reuse
`MessageService`.

## Model

```
Conversation (id, created_at, updated_at)
  1 ── N ConversationParticipant (conversation_id, user_id, UNIQUE(conversation_id, user_id))
Conversation 1 ── N Message (conversation_id, sender_id -> users, content, created_at, read_at)
```

Phase 1 already provided everything, including a composite FK
`(conversation_id, sender_id) → conversation_participants` that guarantees **in SQL** that a
message sender is a participant, and the participant uniqueness constraint. Migration
`V4__messaging_support.sql` only adds `messages(conversation_id, created_at)` for message ordering /
last-message lookups. No tables were changed.

## Flow

1. Student contacts a teacher (`POST /api/conversations {teacherId}`).
2. Student and teacher exchange messages (`POST /{id}/messages`).
3. Either participant lists conversations, reads messages and marks them read.

## Endpoints (stateless JWT)

| Method | Path | Role | Description |
|--------|------|------|-------------|
| `POST` | `/api/conversations` | STUDENT | Start (or reuse) the conversation with a teacher → 201 new / 200 existing |
| `GET` | `/api/conversations?page=&size=` | STUDENT/TEACHER | User's conversations (own only) |
| `GET` | `/api/conversations/{id}` | participant | Conversation detail (other participant + unread) |
| `GET` | `/api/conversations/{id}/messages?page=&size=` | participant | Paginated messages (oldest first) |
| `POST` | `/api/conversations/{id}/messages` | participant | Send a message → 201 |
| `PATCH` | `/api/conversations/{id}/read` | participant | Mark the other participant's messages read → 204 |

## Security & ownership

- Identity always comes from the `SecurityContext` (`CurrentUser`). `senderId`/`studentId`/
  `participantIds` are never accepted from the client (spoofing is ignored — covered by tests).
- Only `STUDENT` can start conversations; teachers only reply to existing ones. Starting requires a
  `StudentProfile` (409 otherwise — never auto-created).
- A teacher is only contactable when **VERIFIED + ACTIVE** (Phase 4 rule); otherwise 404.
- Non-participants get **404** on detail/messages/send/read (conversation existence is not leaked).
- DISABLED accounts (`SUSPENDED`/`INACTIVE`) cannot authenticate at all (JWT filter, Phase 2), so
  all protected routes answer 401; historical conversations/messages are never deleted.

## Conversation rules

- **One conversation per student ↔ teacher pair (V1)**: re-POSTing returns the existing
  conversation (200) instead of creating a second one. Enforced in the service; a simple SQL
  constraint is not expressible through the generic participant model, so this is service+test
  enforced and the concurrency limitation is documented (a rare race could create two; the
  service always resolves deterministically afterwards).
- A conversation is always exactly two participants (student + teacher).

## Pagination

Reuses `SearchResultPage`. Conversations: `page>=0`, `1<=size<=50`, ordered `updatedAt DESC, id ASC`
(`updatedAt` is bumped whenever a message is sent). Messages: `page>=0`, `1<=size<=100`, ordered
`createdAt ASC, id ASC`. Both stable.

## unread / read

- `unreadCount` = messages of the conversation from the *other* sender with `readAt IS NULL`,
  computed with grouped SQL (never loaded into Java).
- `PATCH /read` sets `readAt = now (UTC)` only on the other participant's unread messages; the
  caller's own messages are never touched (verified by tests). Bulk update uses
  `clearAutomatically`/`flushAutomatically` to keep the persistence context consistent.
- `lastMessage` (conversation list) is fetched with a single correlated query, not one per row.

## Anti-N+1

`GET /api/conversations` = 1 paged query for participations (+ fetch of each conversation) and a
constant 3 batched queries for: other participants (join-fetch user), last message per
conversation, and unread counts per conversation. Message pages use a single join-fetch query.

## Privacy

Conversation/message payloads expose only `displayName`, `role` and message content. No email,
address, coordinates, password hashes or tokens.

## Errors

Reuses `ApiError`: 400 (invalid page/size/content), 401 (not authenticated), 403 (wrong role),
404 (conversation not accessible / teacher not visible / profile-less target), 409 (no student
profile). 405 added for wrong methods.

## Examples

```http
POST /api/conversations
Authorization: Bearer <student>
{ "teacherId": "uuid" }                          # 201 (new) or 200 (existing)

POST /api/conversations/{id}/messages
Authorization: Bearer <teacher>
{ "content": "Hola, tengo disponibilidad" }       # 201

GET /api/conversations?page=0&size=20
Authorization: Bearer <student>                   # newest activity first

PATCH /api/conversations/{id}/read
Authorization: Bearer <student>                   # 204
```

## Decisions

- `GET /{id}` and messaging access hide existence with 404 for non-participants.
- Message content is trimmed, 1..5000 characters; messages are immutable (no edit/delete).
- Messages are never physically deleted in this phase; a future archive/delete policy is out of
  scope.
- SUSPENDED semantics follow the global auth rule (non-ACTIVE ⇒ unauthenticated, 401), keeping
  history intact.

## Prepared for WebSockets (future)

All write/read logic lives in `ConversationService`/`MessageService`, not in controllers. A later
WebSocket/STOMP adapter will call the same services to persist messages and bump `updatedAt`; no
data-model changes are required. Rate limiting (anti-spam) is documented as future work.
