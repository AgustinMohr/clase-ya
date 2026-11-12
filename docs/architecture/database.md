# ClaseYa — Base de datos (tal como está implementada)

Documenta la persistencia real: PostgreSQL + JPA/Hibernate + Flyway. No cambia el schema.

## Motor y mapeo

- **PostgreSQL** como única base. **Flyway** define el schema
  (`src/main/resources/db/migration/V1..V21`); Hibernate corre con `ddl-auto=validate`, por lo que
  el modelo JPA debe coincidir con las migraciones.
- Mapeo JPA en el módulo `model`; todas las asociaciones se mapean del lado owning y las N:M son
  entidades join explícitas (sin `@ManyToMany`).

## Estrategias

- **IDs**: UUID en todas las tablas, generados con `gen_random_uuid()` (default de columna) y
  `@GeneratedValue(strategy = GenerationType.UUID)` en JPA.
- **Timestamps**: `timestamptz` (UTC) ↔ `Instant`. `created_at`/`updated_at` por DB (`now()`) y por
  Hibernate (`@CreationTimestamp`/`@UpdateTimestamp`). `updated_at` solo donde el dominio lo pide.
- **Enums**: `varchar` + CHECK (no tipos enum de PG), mapeados con `@Enumerated(EnumType.STRING)`.

## Entidades y relaciones principales

```
users 1:0..1 student_profiles N:0..1 universities | careers  (académico opcional desde V19)
users 1:0..1 teacher_profiles 1:N teacher_education
                            | N:M career_subjects o subjects (vía teacher_subjects, V20)
                            | N:M teacher_modalities (ONLINE/IN_PERSON)
                            | 1:N availability_windows
                            | 1:N verification_documents
                            | 1:N verification_decisions
teacher_education 1:N verification_documents
users 1:N email_verification_tokens
users 1:N password_reset_tokens
universities 1:N academic_units 1:N careers 1:N career_subjects N:1 subjects
student_profiles N:M teacher_profiles (vía favorites)
users N:M conversations (vía conversation_participants) 1:N messages
users 1:N notification_outbox (recipient_user_id)
conversation_pairs (student_user_id, teacher_user_id) -> conversation  (V12: una por par)
notification_outbox (dedupe_key único, status/attempts/locked_until)  (V13/V14)
message_link_tokens (user_id, conversation_id, token_hash único, expires_at/used_at)  (V13)
reviews                            (activa desde V18; una por par estudiante↔profesor)
review_moderation_events           (auditoría append-only de ocultar/restaurar)
reports                            (activa desde V22; reportes de usuarios y cola admin)
user_blocks                        (V22; bloqueo bidireccional de contacto/mensajes)
notifications                      (reservada para futuro; sin entidad JPA)
bookings/calendly_integrations     (eliminadas en V17; ver ADR-032)
```

`users.name` (V3) es el nombre público opcional que alimenta `displayName` en búsqueda/favoritos/
mensajería.

`student_profiles` (V19) usa `level` (`PRIMARIA`/`SECUNDARIA`/`TERCIARIO`/`UNIVERSIDAD`/`ADULTO`) y
`city` como datos mínimos. `university_id`, `career_id` y `current_year` son opcionales, pero la
universidad y la carrera deben venir juntas o ambas nulas (lo valida el servicio).

`teacher_subjects` (V20) apunta a exactamente un target: `career_subject_id` (catálogo académico) o
`subject_id` (materia directa, sin carrera). El CHECK lo garantiza en SQL y la búsqueda pública
resuelve ambos caminos.

## Constraints destacadas

- UNIQUE de 1:1 perfiles ↔ user (`student_profiles.user_id`, `teacher_profiles.user_id`).
- UNIQUE del catálogo: `universities.slug`, `careers.slug` (global), `subjects.slug` y
  `subjects.normalized_name`, pares `(university_id, name/code)`, `(academic_unit_id, code)`,
  `(career_id, subject_id)`, `(teacher_id, career_subject_id)`.
- `favorites (student_id, teacher_id)`; `conversation_participants (conversation_id, user_id)`;
  `conversation_pairs (student_user_id, teacher_user_id)` con `conversation_id` único (V12, RACE-01).
  `reviews` está activa desde `V18` (una por par, estado PUBLISHED/HIDDEN, respuesta y moderación);
  `reports` está activa desde `V22` (un reporte abierto por par reporter/reported) y `user_blocks`
  guarda el bloqueo; `notifications` sigue reservada para futuro sin entidad JPA. `reviews.booking_id` se
  eliminó en V17 y `bookings`/`calendly_integrations` ya no existen (ver `ADR-032`).
- CHECK de rangos (rating 1-5, lat/lng, `current_year`, `duration_minutes`, años, semestre),
  CHECK de enums y `reporter_id <> reported_user_id`.
- `messages`: FK compuesta `(conversation_id, sender_id)` → `conversation_participants` — garantiza
  en SQL que el sender es participante. `content` CHECK `length(trim(content)) > 0`.
- `availability_windows` (V5): `teacher_id`, `day_of_week` 1..7, `start_minutes`/`end_minutes`,
  `mode` nullable, `status` AVAILABLE/DISABLED; CHECKs `end > start` y duración ≥ 60 min;
  **exclusion constraint GiST** `(teacher_id =, day_of_week =, int4range(start,end,'[)') &&)` con
  extensión `btree_gist` (impide solapamientos por profesor y día, incluso ante concurrencia;
  contiguos permitidos). `teacher_profiles.availability_note` (text).
- `verification_documents` (V8/V10/V15/V16): CHECK `content IS NOT NULL OR storage_key IS NOT NULL`,
  `scan_status` en `PENDING`/`CLEAN`/`INFECTED`/`UNSCANNED`; `verification_decisions` es
  append-only con CHECK de decisión/método. `email_verification_tokens.token_hash`,
  `password_reset_tokens.token_hash`, `message_link_tokens.token_hash` y
  `notification_outbox.dedupe_key` son UNIQUE.
- `reviews` (V18): UNIQUE `(student_id, teacher_id)`, `status` PUBLISHED/HIDDEN, respuesta del
  profesor y campos de moderación. `review_moderation_events` es append-only y referencia la review
  y al admin.

## Índices relevantes

- `users(email)` (unique), `users(role)`, `users(status)`, `teacher_profiles(verification_status)`,
  `(verification_status, rating_average DESC, rating_count DESC)` (V3), `(latitude, longitude)`.
- Catálogo/búsqueda: `career_subjects(subject_id)`, `teacher_subjects(career_subject_id)`,
  `teacher_subjects(subject_id) WHERE subject_id IS NOT NULL` (V20), `teacher_modalities(modality)`
  (V3).
- Favoritos/mensajería: el UNIQUE de `favorites(student_id, teacher_id)` cubre por `student_id`;
  `conversation_participants(user_id)`, `messages(conversation_id, sender_id)`,
  `messages(conversation_id, created_at)` (V4).
- Caminos calientes (V11): `messages(conversation_id) WHERE read_at IS NULL` (conteo y marcado de
  no leídos) y `subjects(name) WHERE active` (catálogo de la landing).
- Verificación/outbox: `verification_documents(teacher_id)` y `(teacher_education_id)`,
  `verification_decisions(teacher_id, decided_at)`,
  `notification_outbox(next_attempt_at) WHERE status IN ('PENDING','FAILED')`,
  `message_link_tokens(user_id)`, `email_verification_tokens(user_id)` y
  `password_reset_tokens(user_id)` (V21). La cola admin relee
  credenciales con `join fetch` para evitar N+1.
- Reviews (V18): `reviews(teacher_id, status, created_at DESC)` para el listado público y
  `review_moderation_events(review_id, created_at DESC)` para la auditoría.
- Safety (V22): `user_blocks` UNIQUE `(blocker_id, blocked_id)` + índice por `blocked_id`;
  `reports` con índice parcial único `(reporter_id, reported_user_id) WHERE status IN
  ('OPEN','REVIEWING')` y `(status, created_at DESC)` para la cola admin.

## Reglas de migraciones

- El schema es propiedad de Flyway; **nunca** editar migraciones aplicadas/históricas (`V1..V{n}`).
- Cambios nuevos = `V{n+1}__descripcion.sql` con motivo justificado y sin tocar lo anterior.
- Histórico real: `V1` create schema · `V2` teacher_modalities · `V3` users.name + índices de
  búsqueda · `V4` índice de mensajería · `V5` availability_windows · `V6` Google OAuth · `V7`
  teacher cards · `V8` verification circuit · `V9` activación por email · `V10` object storage ·
  `V11` índices de caminos calientes · `V12` conversación única por par (RACE-01) · `V13` outbox y
  links de contacto (REVIEW2-002) · `V14` evento `ACTIVATION` en el outbox · `V15` estado
  `UNSCANNED` · `V16` revisión por documento (VERIF-001) · `V17` drop de `bookings`/
  `calendly_integrations` y de `reviews.booking_id` (ADR-032) · `V18` habilita reviews/rating
  (OPINION-001): estado, respuesta, moderación, UNIQUE por par e índice de listado · `V19` hace
  opcional el bloque académico del estudiante y agrega `level`/`city` (PROFILE-003) · `V20` permite
  que `teacher_subjects` apunte a una materia directa sin carrera (PROFILE-004) · `V21` agrega
  `password_reset_tokens` y el evento `PASSWORD_RESET` del outbox (AUTH-003) · `V22` activa
  `reports` y agrega `user_blocks` para reportes/bloqueo (REPORT-001) · `V23` agrega
  `users.session_version` para revocar sesiones en logout/reset (AUTH-004).

## Borrado y normalización

- **Soft delete** en catálogo (`active=false`) y en cuentas (`status`); borrado físico casi no
  existe. FKs `RESTRICT` protegen datos históricos (mensajes, participantes, reviews y reports);
  `CASCADE` solo en datos de propiedad efímera (education, teacher_subjects, modalities,
  user_blocks).
- Normalización ~3NF: nombres de universidad/carrera/materia nunca duplicados; referencias por FK.
  Excepciones justificadas: `teacher_profiles.rating_average/count` (agregados cacheados para
  ordenar/buscar; los recalcula `ReviewService` desde las reviews PUBLISHED) y
  `career_subjects.name_override` (dato de dominio: el nombre que la carrera usa para la materia).
