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

1. Student contacts a teacher (`POST /api/conversations {teacherId, message?}`). The optional
   `message` is the contact request itself and is persisted in the **same transaction**: if it is
   invalid (blank or longer than 256 characters) the whole contact fails with 400 and **no empty
   conversation is left behind** (CONTACT-001).
2. Student and teacher exchange messages (`POST /{id}/messages`).
3. Either participant lists conversations, reads messages and marks them read.

## Endpoints (stateless JWT)

| Method | Path | Role | Description |
|--------|------|------|-------------|
| `POST` | `/api/conversations` | STUDENT | Start (or reuse) the conversation with a teacher, with an optional first `message` → 201 new / 200 existing |
| `GET` | `/api/conversations?page=&size=` | STUDENT/TEACHER | User's conversations (own only) |
| `GET` | `/api/conversations/{id}` | participant | Conversation detail (other participant + unread) |
| `GET` | `/api/conversations/{id}/thread?before=&limit=` | participant | Detail + one page of messages with a keyset cursor (REVIEW2-003) |
| `GET` | `/api/conversations/{id}/messages?page=&size=` | participant | Legacy offset pagination (oldest first); kept for compatibility |
| `POST` | `/api/conversations/{id}/messages` | participant | Send a message → 201 |
| `PATCH` | `/api/conversations/{id}/read` | participant | Mark the other participant's messages read → 204 |

## Contact notification (REVIEW2-002)

The contact email is **not sent inside the transaction**:

1. `ConversationService.start` inserts a row in `notification_outbox` in the same transaction
   (`dedupe_key = contact:<conversationId>`). Nothing is sent yet.
2. The worker (`NotificationWorkerScheduler`, every 15 s) claims due rows with
   `FOR UPDATE SKIP LOCKED` and a 2-minute lease, then sends **after** the claim transaction
   commits. A crashed instance releases the lease automatically.
3. Delivery retries with backoff (1, 5, 15, 60, 240 min); after 5 attempts the row is `DEAD`.
   A permanent failure (no transport configured, recipient gone) goes straight to `DEAD`.
4. The email carries a single-use link `/mensajes/{id}?token=...`. Only the SHA-256 hash is stored
   (`message_link_tokens`), with a 7-day TTL. `POST /api/auth/message-link` exchanges it for the
   normal session cookie; a second attempt fails, and the SPA strips the token from the URL.

Without any email transport configured (SMTP or Brevo) and without
`app.notification.log-links`, delivery is a permanent failure: the contact exists, but no email is
sent (the row is `DEAD` with the reason).

Registration uses the same outbox (`ACTIVATION`, `ADR-029`). The difference is deliberate: the
transport check runs **inside** the registration transaction (`ActivationLinkSender.isConfigured()`),
so without any email transport the request answers 503 and the account is never created. With a
transport configured, the email is delivered by the worker and a transient failure is retried.

The worker emits `claseya.notification.outbox.delivered|failed|dead` (tagged by `event_type`) and
logs every outcome with the `outboxId`. Payloads are never logged: for `ACTIVATION` they carry the
raw token until the row is terminal.

## Security & ownership

- Identity always comes from the `SecurityContext` (`CurrentUser`). `senderId`/`studentId`/
  `participantIds` are never accepted from the client (spoofing is ignored — covered by tests).
- Only `STUDENT` can start conversations; teachers only reply to existing ones. Starting requires a
  `StudentProfile` (409 otherwise — never auto-created).
- A teacher is only contactable when **ACTIVE with a PUBLISHED announcement** (TEACHER-001 D1);
  otherwise 404. Verification is no longer required to be contacted.
- Non-participants get **404** on detail/messages/send/read (conversation existence is not leaked).
- DISABLED accounts (`SUSPENDED`/`INACTIVE`) cannot authenticate at all (JWT filter, Phase 2), so
  all protected routes answer 401; historical conversations/messages are never deleted.

## Conversation rules

- **One conversation per student ↔ teacher pair (V1)**: re-POSTing returns the existing
  conversation (200) instead of creating a second one. **Enforced in the database** since V12:
  `conversation_pairs (student_user_id, teacher_user_id)` is the natural key, and creation uses
  `INSERT ... ON CONFLICT DO NOTHING`; the loser deletes its empty candidate and reuses the winner.
  Concurrent contacts resolve to one conversation (RACE-01, covered by a concurrency test).
- A conversation is always exactly two participants (student + teacher).
- Contacting an already-contacted teacher with a new `message` returns `200` **and appends** the
  message to the existing conversation (it is never dropped).

## Pagination

Reuses `SearchResultPage`. Conversations: `page>=0`, `1<=size<=50`, ordered `updatedAt DESC, id ASC`
(`updatedAt` is bumped whenever a message is sent). Messages: `page>=0`, `1<=size<=100`, ordered
`createdAt ASC, id ASC`. Both stable.

### Thread keyset (REVIEW2-003)

The SPA opens a conversation with `GET /{id}/thread`:

- without `before`: the **latest** page (default `limit=50`, max `100`), returned oldest first;
- with `before=<messageId>`: up to `limit` messages **strictly older** than that message;
- `hasMore` says whether older messages exist; `nextBefore` is the oldest returned message id and
  the cursor for the next call.

The cursor is `(created_at, id)`, so a message sent while the user scrolls does not duplicate or
skip rows. The anchor must belong to the same conversation; otherwise the endpoint answers `404`.
The old offset endpoint remains for compatibility and is not used by the SPA.

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
`GET /{id}/thread` uses a constant number of queries too: access/conversation, other participant,
unread count, and one keyset page (join-fetch sender).

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
{ "teacherId": "uuid", "message": "Hola, ..." }  # same, plus the first message (CONTACT-001)

POST /api/conversations/{id}/messages
Authorization: Bearer <teacher>
{ "content": "Hola, tengo disponibilidad" }       # 201

GET /api/conversations?page=0&size=20
Authorization: Bearer <student>                   # newest activity first

GET /api/conversations/{id}/thread?limit=50
Authorization: Bearer <student>                   # detail + latest page (oldest first)

GET /api/conversations/{id}/thread?before=<messageId>&limit=50
Authorization: Bearer <student>                   # older page, same shape

PATCH /api/conversations/{id}/read
Authorization: Bearer <student>                   # 204
```

## Decisions

- `GET /{id}` and messaging access hide existence with 404 for non-participants.
- Message content is trimmed, 1..256 characters; messages are immutable (no edit/delete).
- Messages are never physically deleted in this phase; a future archive/delete policy is out of
  scope.
- SUSPENDED semantics follow the global auth rule (non-ACTIVE ⇒ unauthenticated, 401), keeping
  history intact.
- REPORT-001: a block in either direction makes `POST /api/conversations` and
  `POST /api/conversations/{id}/messages` return 403 for both sides. The existing conversation
  stays readable; only new contacts and messages are cut.
- The Messages page polls every 20 s while the tab is visible (list badges + open thread), so a
  reply shows up without a manual refresh.

## Prepared for WebSockets (future)

All write/read logic lives in `ConversationService`/`MessageService`, not in controllers. A later
WebSocket/STOMP adapter will call the same services to persist messages and bump `updatedAt`; no
data-model changes are required. Rate limiting (anti-spam) is documented as future work.
