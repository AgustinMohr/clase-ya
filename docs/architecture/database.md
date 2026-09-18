# ClaseYa — Base de datos (tal como está implementada)

Documenta la persistencia real: PostgreSQL + JPA/Hibernate + Flyway. No cambia el schema.

## Motor y mapeo

- **PostgreSQL** como única base. **Flyway** define el schema
  (`src/main/resources/db/migration/V1..V4`); Hibernate corre con `ddl-auto=validate`, por lo que
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
users 1:0..1 student_profiles N:1 universities | careers
users 1:0..1 teacher_profiles 1:N teacher_education
                            | N:M career_subjects (vía teacher_subjects)
                            | N:M teacher_modalities (ONLINE/IN_PERSON)
universities 1:N academic_units 1:N careers 1:N career_subjects N:1 subjects
student_profiles N:M teacher_profiles (vía favorites)
users N:M conversations (vía conversation_participants) 1:N messages
bookings 1:0..1 reviews            (reservas/reviews: schema listo, sin lógica)
reports/notifications/calendly_integrations (modelo preparado, sin lógica)
```

`users.name` (V3) es el nombre público opcional que alimenta `displayName` en búsqueda/favoritos/
mensajería.

## Constraints destacadas

- UNIQUE de 1:1 perfiles ↔ user (`student_profiles.user_id`, `teacher_profiles.user_id`).
- UNIQUE del catálogo: `universities.slug`, `careers.slug` (global), `subjects.slug` y
  `subjects.normalized_name`, pares `(university_id, name/code)`, `(academic_unit_id, code)`,
  `(career_id, subject_id)`, `(teacher_id, career_subject_id)`.
- `favorites (student_id, teacher_id)`; `conversation_participants (conversation_id, user_id)`;
  `reviews.booking_id`; `calendly_integrations.teacher_id`.
- CHECK de rangos (rating 1-5, lat/lng, `current_year`, `duration_minutes`, años, semestre),
  CHECK de enums y `reporter_id <> reported_user_id`.
- `messages`: FK compuesta `(conversation_id, sender_id)` → `conversation_participants` — garantiza
  en SQL que el sender es participante. `content` CHECK `length(trim(content)) > 0`.
- `availability_windows` (V5): `teacher_id`, `day_of_week` 1..7, `start_minutes`/`end_minutes`,
  `mode` nullable, `status` AVAILABLE/DISABLED; CHECKs `end > start` y duración ≥ 60 min;
  **exclusion constraint GiST** `(teacher_id =, day_of_week =, int4range(start,end,'[)') &&)` con
  extensión `btree_gist` (impide solapamientos por profesor y día, incluso ante concurrencia;
  contiguos permitidos). `teacher_profiles.availability_note` (text).

## Índices relevantes

- `users(email)` (unique), `users(role)`, `users(status)`, `teacher_profiles(verification_status)`,
  `(verification_status, rating_average DESC, rating_count DESC)` (V3), `(latitude, longitude)`.
- Catálogo/búsqueda: `career_subjects(subject_id)`, `teacher_subjects(career_subject_id)`,
  `teacher_modalities(modality)` (V3).
- Favoritos/mensajería: el UNIQUE de `favorites(student_id, teacher_id)` cubre por `student_id`;
  `conversation_participants(user_id)`, `messages(conversation_id, sender_id)`,
  `messages(conversation_id, created_at)` (V4).

## Reglas de migraciones

- El schema es propiedad de Flyway; **nunca** editar migraciones aplicadas/históricas (`V1..V{n}`).
- Cambios nuevos = `V{n+1}__descripcion.sql` con motivo justificado y sin tocar lo anterior.
- Histórico real: `V1` create schema · `V2` teacher_modalities · `V3` users.name + índices de
  búsqueda · `V4` índice de mensajería · `V5` availability_windows (AVAIL-001).

## Borrado y normalización

- **Soft delete** en catálogo (`active=false`) y en cuentas (`status`); borrado físico casi no
  existe. FKs `RESTRICT` protegen datos históricos (bookings/reviews/mensajes/participantes);
  `CASCADE` solo en datos de propiedad efímera (education, teacher_subjects, modalities,
  notifications, integración Calendly).
- Normalización ~3NF: nombres de universidad/carrera/materia nunca duplicados; referencias por FK.
  Excepciones justificadas: `teacher_profiles.rating_average/count` (agregados cacheados para
  ordenar/buscar; los mantendrá el módulo de reviews) y `career_subjects.name_override` (dato de
  dominio: el nombre que la carrera usa para la materia).
