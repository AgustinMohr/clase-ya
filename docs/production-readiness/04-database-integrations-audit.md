# 04 — Auditoría de Base de Datos e Integraciones

- Fecha: 2026-11-17
- Alcance: esquema PostgreSQL (Flyway V1..V25), entidades JPA, repositorios/specifications, configuración de conexión, transacciones, almacenamiento de evidencia, integraciones externas (Google, Brevo/SMTP, Turnstile, R2/S3, ClamAV).
- Método: **análisis estático del repositorio**. No se abrió ninguna conexión a Neon ni a otra base; no se ejecutó `EXPLAIN`, ni migraciones, ni tests. No se modificó código, datos ni infraestructura. No se imprimió ningún secreto (ninguno fue leído).
- Clasificación: **CONFIRMADO** (evidencia en repositorio), **PROBABLE** (deducido), **PENDIENTE** (requiere acceso a Neon/Render/R2/Brevo).
- Prioridades: P0 bloquea salida a producción; P1 antes de abrir tráfico; P2 corto plazo; P3 mejora.
- Relación con informes previos: complementa `00` (baseline), `01` (seguridad) y `02` (backend). Los hallazgos ya descritos allí se referencian por su ID (B-xx, S-xx). Existe además `docs/audits/2026-11-13-production-infrastructure-report.md`, que ya señala backups/PITR como pendiente de verificar; esta auditoría confirma que sigue sin evidencia.

## 1. Resumen ejecutivo

El esquema está bien diseñado para el tamaño del producto: claves UUID, `timestamptz`, enums como `varchar + CHECK`, FKs `RESTRICT` para datos históricos, unicidad real en la base para los invariantes de negocio (un par conversación estudiante-profesor, una reseña por par, tokens con hash único, ventanas de disponibilidad sin solapamiento con `EXCLUDE USING gist`) y `ddl-auto=validate`. El outbox transaccional usa `FOR UPDATE SKIP LOCKED` con lease. Las llamadas externas desde transacciones tienen timeouts acotados salvo Turnstile.

Lo que **no se puede afirmar** con el repositorio: backups, PITR, retención, límites de conexión reales de Neon, planes de ejecución, versioning del bucket y estado real de los secretos. Todo eso es **PENDIENTE** y es el riesgo principal de esta capa.

Resultado: **0 P0, 3 P1, 7 P2, 8 P3**.

P1:
- DB-01: sin evidencia de backups/PITR ni de prueba de restauración; RPO/RTO sin definir.
- DB-02: sin configuración de pool (Hikari) ni de timeouts de conexión pensada para Neon (autosuspend, pooler, límite de conexiones).
- DB-03: evidencia documental sensible sin escaneo y, según el modo de almacenamiento, dentro de la base (`bytea`) o en un bucket sin versioning verificado; sin política de retención/purga.

Veredicto: apto para beta controlada **solo si** DB-01 se verifica (backup restaurable probado) antes de aceptar datos reales.

## 2. Modelo de datos

### 2.1 Tablas (según migraciones y entidades)

| Dominio | Tablas | Observación |
|---|---|---|
| Identidad | `users`, `email_verification_tokens`, `password_reset_tokens`, `message_link_tokens` | `users`: `uq_users_email`, `uq_users_google_sub`, `session_version`. Tokens con `token_hash` UNIQUE (SHA-256). |
| Catálogo | `universities`, `academic_units`, `careers`, `subjects`, `career_subjects` | UNIQUEs compuestas; `active` para baja lógica. |
| Perfiles | `student_profiles`, `teacher_profiles`, `teacher_education`, `teacher_subjects`, `teacher_modalities`, `availability_windows` | `teacher_profiles.user_id` UNIQUE + FK `RESTRICT`. |
| Mensajería | `conversations`, `conversation_participants`, `conversation_pairs`, `messages` | Par único por `conversation_pairs`. |
| Reseñas | `reviews`, `review_moderation_events` | `uq_reviews_student_teacher`; agregados denormalizados en `teacher_profiles` (`rating_average`, `rating_count`). |
| Seguridad/seguridad social | `reports`, `user_blocks`, `notifications` (legacy) | Ver 2.3. |
| Verificación | `verification_documents`, `verification_decisions` | Evidencia en `bytea` (legado) o `storage_key` (R2/S3); `scan_status`. |
| Outbox | `notification_outbox` | `dedupe_key` UNIQUE, índice parcial de pendientes. |
| Eliminadas | `bookings`, `calendly_integrations` (V17) | Destructivo, ya aplicado. |

### 2.2 Puntos fuertes verificados

- CHECK constraints de dominio: `role`, `status`, `latitude/longitude`, `rating_average 0..5`, `rating_count >= 0`, `size_bytes > 0`, `day_of_week 1..7`, `start/end_minutes`, duración mínima 60 min, `mode`, decisiones y métodos de verificación (V1, V5, V8).
- `EXCLUDE USING gist` evita solapamiento de disponibilidad por profesor (V5, requiere `btree_gist`).
- `chk_verification_documents_storage`: al menos `content` o `storage_key` (V10).
- `chk_conversation_pairs_distinct`: un usuario no conversa consigo mismo (V12).
- Lock pesimista y constraints como última línea de defensa (`findOwnedForUpdate`, `findByIdForUpdate`).

### 2.3 Inconsistencias y datos potencialmente inválidos

| ID | Prioridad | Estado | Hallazgo |
|---|---|---|---|
| DB-04 | P2 | CONFIRMADO | `uq_users_email` es sensible a mayúsculas; la normalización a minúsculas ocurre solo en la aplicación (`AuthService.java:161-162`, `GoogleAuthService.java:114`). Una cuenta creada o editada por SQL (por ejemplo desde la consola de Neon, como se hizo al pasar una cuenta a ADMIN) con mayúsculas escapa a `findByEmail` y permite duplicados `A@x.com` / `a@x.com`. Falta `CHECK (email = lower(email))` o índice único sobre `lower(email)`. Migración nueva (Nivel 2). |
| DB-05 | P2 | CONFIRMADO | `scan_status` se añadió en V10 con `DEFAULT 'CLEAN'`: toda evidencia previa a V10 quedó marcada como limpia sin haber sido escaneada. V15 incorporó `UNSCANNED` pero no reclasificó filas existentes. En una base nueva no hay impacto; con datos legados sí. Verificar con `SELECT scan_status, count(*) FROM verification_documents GROUP BY 1;` (lectura). |
| DB-06 | P2 | CONFIRMADO | Vínculo `users` ↔ perfil con `RESTRICT` y sin flujo de eliminación de cuenta (R-10 en 00): eliminar un usuario obliga a borrar a mano en orden de FKs (`verification_decisions`, `verification_documents`, `teacher_education`, `reviews`, etc.). Mezcla `CASCADE` (mensajería, tokens, outbox) y `RESTRICT` (verificación, reseñas): borrar un usuario destruye conversaciones de la otra parte pero no sus reseñas. Hace falta definir política de borrado/anonimización (también legal: derecho de supresión). |
| DB-07 | P3 | CONFIRMADO | `teacher_profiles.rating_average/rating_count` son datos derivados denormalizados; no hay CHECK ni job que los reconcilie con `reviews`. Una moderación fallida a mitad deja el agregado desfasado. Existe un cálculo transaccional, pero no verificación periódica. |
| DB-08 | P3 | CONFIRMADO | `notifications` (V1) quedó fuera de alcance según `AGENTS.md` pero la tabla se conserva. Sin uso verificado; riesgo bajo de datos huérfanos. |
| DB-09 | P3 | CONFIRMADO | `verification_decisions.previous_status/new_status` son `varchar(20)` sin CHECK (solo `decision` y `method` lo tienen). Un valor inválido no lo rechaza la base. |
| DB-10 | P3 | PROBABLE | `teacher_profiles.city` y otros textos públicos sin longitud máxima a nivel de base en algunos campos `text` (bio, address, reason, `last_error`). La validación está en DTOs (`MAX_TEACHER_BIO_LENGTH 2000`); la base no la repite. |
| DB-11 | P3 | CONFIRMADO | Sin `@Version`/bloqueo optimista en entidades (ya B-12); sobreescritura de perfil por dos pestañas es posible. |

## 3. Hallazgos priorizados

### P1

**DB-01 (P1, PENDIENTE) — Backups y recuperación sin evidencia.**
Neon gestiona backups/PITR según plan; el repositorio no contiene ninguna referencia a retención, ventana de restauración, RPO/RTO ni a una prueba de restauración (`docs/audits/2026-11-13-production-infrastructure-report.md:46,304-319` lo lista como tarea pendiente). No hay backup propio de la aplicación. Si la evidencia está en R2, el backup de Neon no la cubre. Consecuencia: un borrado o corrupción accidental (por ejemplo un `UPDATE`/`DELETE` manual desde la consola de Neon, práctica ya usada en este proyecto) podría ser irrecuperable. Ver sección 8.

**DB-02 (P1, PROBABLE) — Sin configuración de pool ni timeouts para Neon.**
`application.yml` define solo `url/username/password` (`:13-16`); no hay `spring.datasource.hikari.*`. Se usan los valores por defecto de HikariCP (pool 10, `connectionTimeout` 30 s, `maxLifetime` 30 min, `idleTimeout` 10 min, sin `keepaliveTime`, sin `socketTimeout`/`connectTimeout` de JDBC). Riesgos específicos de Neon: autosuspend del cómputo (primera conexión lenta, conexiones inactivas cortadas por el servidor), límite de conexiones del plan (con el endpoint directo, 10 por instancia más el worker puede agotarlo si se escala a varias instancias) y diferencias entre endpoint directo y pooler (`-pooler`, PgBouncer en modo transacción, incompatible con ciertos usos como `FOR UPDATE SKIP LOCKED` fuera de transacción, advisory locks de sesión y prepared statements si no se configura). Flyway necesita conexión directa. No hay `statement_timeout` ni `lock_timeout`, de modo que una consulta o un lock colgado retiene una conexión sin límite (relevante para el hallazgo B-02, deadlocks). Ver sección 5.

**DB-03 (P1, CONFIRMADO/PENDIENTE) — Evidencia documental sensible.**
- El escáner por defecto es `NoopDocumentScanner` (`SCAN_ENABLED=false`); la app arranca fuera de local solo con `ALLOW_UNSCANNED_EVIDENCE` (S-04 en 01).
- `STORAGE_PROVIDER=postgres` (valor por defecto) guarda PDFs de identidad/títulos como `bytea` en la misma base que los datos relacionales: aumenta tamaño de backups, de tiempo de restauración y de exposición ante cualquier acceso a la base (incluida la consola de Neon, donde un `SELECT *` los mostraría).
- Con `s3`, el bucket debe ser privado; el código no puede garantizarlo. No se verificó cifrado en reposo, versioning, lifecycle ni política de retención (ADR-026 deja "D6 retención/purga" abierto, `ADR-026:49`).
- La descarga carga el archivo completo en memoria (`storage.load(document)` devuelve `byte[]`, `VerificationDocumentService.java`), con tope de 10 MB por archivo (aceptable).
- Sin política de retención ni purga de documentos de cuentas rechazadas o eliminadas.

### P2

| ID | Estado | Hallazgo |
|---|---|---|
| DB-04 | CONFIRMADO | Email único sin normalización en base (sección 2.3). |
| DB-05 | CONFIRMADO | `scan_status` legado `CLEAN` sin escaneo (2.3). |
| DB-06 | CONFIRMADO | Sin política de borrado/anonimización de cuenta (2.3). |
| DB-12 | CONFIRMADO | **Búsqueda de materias sin índice** utilizable: `unaccent(lower(s.name)) like '%' || unaccent(lower(:query)) || '%'` (`SubjectRepository.java:32,37`). El patrón con `%` inicial no usa B-tree y `unaccent` no es `IMMUTABLE`, así que no puede indexarse tal cual; hace seq scan sobre `subjects`. Con un catálogo de cientos/miles de filas es aceptable; no escala a cientos de miles. Para escalar: `pg_trgm` + envoltorio inmutable de `unaccent` + índice GIN (migración Nivel 2). Medir con `EXPLAIN` (PENDIENTE). |
| DB-13 | CONFIRMADO | **Filtro de ciudad no indexable**: `lower(trim(city)) = ?` (`TeacherSpecifications.java:168-172`). No hay índice sobre `city` ni sobre la expresión. Es una de las condiciones del buscador público (el endpoint más expuesto). Solución: columna normalizada o índice sobre la expresión. |
| DB-14 | PROBABLE | **Búsqueda de profesores con múltiples subconsultas `EXISTS`** (anuncio publicado exige subconsultas por materia, modalidad y disponibilidad + `user.status`) construidas con Criteria (`TeacherSpecifications.java:57-110`), más `count` para paginación. Sin `EXPLAIN` no se puede garantizar el plan. Índices que ayudan: `idx_teacher_subjects_career_subject_id`, `idx_teacher_subjects_subject_id`, `idx_teacher_modalities_modality`, `idx_availability_windows_teacher`, `idx_teacher_profiles_verified_rating`. No existe índice sobre `users.status` + `name` combinado ni parcial para perfiles publicados. Con pocos miles de profesores es suficiente; revisar al crecer. |
| DB-15 | CONFIRMADO | **Directorio admin** `lower(u.email) like :q ... or lower(u.name) like :q` (`AdminUserDirectoryRepository.java:34-35,43-44`): sin índice por `lower(...)`; seq scan. Solo ADMIN; tamaño bajo. |
| DB-16 | CONFIRMADO | **Sin retención/purga** de `notification_outbox`, tokens usados/vencidos, `messages` o evidencia (B-05 en 02). Crecimiento ilimitado; `notification_outbox.payload` guarda email y nombres. |
| DB-17 | PROBABLE | **Subida a R2 fuera de la transacción de base de datos, y borrado dentro de ella**: `persist` sube el objeto y luego inserta la fila; si la transacción falla se llama `deleteOrphan` (best-effort, `VerificationDocumentService.java:85-92`). Un fallo de red en la limpieza deja objetos huérfanos sin referencia ni job de reconciliación. En `delete`, el objeto se borra después de `flush` pero antes del commit: si el commit falla tras borrar el objeto, queda una fila apuntando a un objeto inexistente (descarga 500/404). Riesgo bajo; falta reconciliación periódica. |

### P3

DB-07, DB-08, DB-09, DB-10, DB-11 (sección 2.3) y:

- **DB-18 (P3, CONFIRMADO)** — `idx_messages_conversation_sender` (V1) y `idx_messages_conversation_created` (V4) quedan parcialmente redundantes frente a `idx_messages_conversation_created_id` (V24) y `idx_messages_unread` (V11). Sobrecoste de escritura menor; V11 ya recomienda medir y eliminar los que no se usen.
- **DB-19 (P3, CONFIRMADO)** — Índices de tablas eliminadas: `idx_bookings_*` se eliminaron con `DROP TABLE` en V17 (sin residuo).
- **DB-20 (P3, CONFIRMADO)** — Solo existe `SchemaIndexIntegrationTest` para verificar índices; no hay test que compare el schema resultante con las entidades más allá de `ddl-auto=validate`, ni test de migración desde una base con datos (ver 7).
- **DB-21 (P3, PROBABLE)** — `idx_teacher_profiles_location (latitude, longitude)` (V1) sin consulta conocida que lo use en producción (la búsqueda por distancia se ordena en memoria/consulta, `TeacherSearchService.java:132`): posible índice sin uso.
- **DB-22 (P3, CONFIRMADO)** — `DemoDataQuarantineService` ejecuta SQL directo con `lower(email) like ?` (`DemoDataQuarantineService.java:28`) en el arranque; inofensivo, pero es lógica de datos fuera de los repositorios.

## 4. Problemas de integridad

Resumen de garantías por invariante:

| Invariante | Dónde se garantiza | Estado |
|---|---|---|
| Email único | `uq_users_email` + normalización en app | Parcial (DB-04) |
| Un perfil de profesor por usuario | `uq_teacher_profiles_user` | Bien |
| Una conversación por par | `conversation_pairs` PK + `ON CONFLICT DO NOTHING` | Bien (conversaciones anteriores a V12 sin backfill, posible duplicado histórico) |
| Una reseña por par | `uq_reviews_student_teacher` | Bien |
| Sin solapamiento de disponibilidad | `EXCLUDE USING gist` | Bien |
| Token de un solo uso | `token_hash` UNIQUE + `used_at` | Bien |
| Email no duplicado en outbox | `uq_notification_outbox_dedupe` | Bien (entrega al menos una vez; B-10) |
| Cuota de documentos por credencial | Servicio con lock de fila (`findOwnedForUpdate`) | Solo en servicio; sin constraint (no expresable fácilmente) |
| Estados de verificación consistentes | `VerificationAggregator` en servicio; CHECK en perfil | Solo en aplicación (DB-09) |
| Agregados de rating | Servicio al publicar/moderar | Sin reconciliación (DB-07) |

Casos concretos de datos inválidos posibles: emails con mayúsculas creados por SQL (DB-04); evidencia legada marcada `CLEAN` (DB-05); conversación sin par registrado anterior a V12; filas `notifications` huérfanas; objetos en R2 sin fila (DB-17).

## 5. Problemas de rendimiento

N+1: los `@ManyToOne` son `LAZY` (`model/*.java`) y los listados clave usan `@EntityGraph` (`CareerRepository.java:16`, `FavoriteRepository.java:28`, `ConversationParticipantRepository.java:45`) o consultas con proyección/join (el directorio admin está documentado "sin N+1", `AdminUserDirectoryRepository.java:20`). `open-in-view=false` evita cargas perezosas fuera de transacción. No se encontró `@OneToMany` ni `@ManyToMany`. Sin ejecutar la aplicación con logging de SQL no se puede descartar N+1 en `TeacherSummaryAssembler` (PROBABLE sin riesgo evidente; confirmar con `spring.jpa.properties.hibernate.generate_statistics` en un entorno de prueba).

Índices existentes relevantes: por rol/estado de usuario, hot paths de mensajería (V11, V24), subjects activos por nombre, outbox pendiente (parcial), reseñas por profesor/estado/fecha (V18), verificación por estado y fecha (V8), `uq_users_google_sub`.

Índices o consultas problemáticas: DB-12, DB-13, DB-14, DB-15 (arriba). Además:

- Paginación: `PageRequest` con `Sort` en lista blanca (`TeacherSort.parse`), correcta; `Page` ejecuta una consulta `count` por búsqueda (costo duplicado); para tablas grandes conviene keyset (ya aplicado en el hilo de mensajes, V24).
- El sitemap carga hasta 10.000 perfiles por petición sin caché (B-07), inclusive con la base de Neon dormida.
- Ordenamiento por `verified/rating` apoyado en `idx_teacher_profiles_verified_rating`; el orden por distancia se calcula en la consulta (no indexable).
- Sondeo de mensajes del frontend cada 20 s por pestaña (ver 03) multiplica lecturas sobre `conversation_participants`/`messages` con conexiones de Neon.

`EXPLAIN`: **no ejecutado** (no hay acceso de lectura autorizado a la base en esta sesión). Comandos sugeridos, solo en una réplica/branch de prueba de Neon con datos sintéticos:

```sql
EXPLAIN (ANALYZE, BUFFERS) SELECT ... -- consulta capturada con spring.jpa.show-sql en staging
SELECT relname, seq_scan, idx_scan FROM pg_stat_user_tables ORDER BY seq_scan DESC;
SELECT indexrelname, idx_scan FROM pg_stat_user_indexes WHERE idx_scan = 0;
```

## 6. Riesgos de seguridad y privacidad

| ID | Prioridad | Estado | Riesgo |
|---|---|---|---|
| DB-23 | P1 | PENDIENTE | **Credenciales de base de datos**: `DB_URL/USERNAME/PASSWORD` tienen defaults de desarrollo (`claseya/claseya`, `application.yml:13-16`) y `comandos.txt` en la raíz del repositorio documenta comandos locales con riesgo de apuntar a Neon de producción (R-05/S-11). Confirmar que `comandos.txt` no contiene cadenas de conexión reales y que `.gitignore` cubre archivos locales. Rotar la contraseña de Neon si alguna vez se pegó en el chat, logs o repositorio. Revisar historial de Git (no auditado). |
| DB-24 | P2 | CONFIRMADO | **PII en la base y en logs**: `users.email`, nombres, mensajes completos de conversaciones, `notification_outbox.payload` (email, nombre, enlace) y `last_error` (texto libre del proveedor). `SmtpEmailSender` registra el email del destinatario en errores. Sin cifrado a nivel de columna; depende del cifrado en reposo de Neon (no verificado). |
| DB-25 | P2 | CONFIRMADO | **Tokens y enlaces en el outbox**: el payload de `CONTACT_REQUEST`/activación puede contener el enlace de un solo uso hasta que se marca `SENT` o se elimina; B-01 muestra que ante 401/403 el token se borra del payload. Las filas `SENT` conservan metadatos indefinidamente (DB-16). Con `AUTH_LOG_ACTIVATION_LINKS`/`MESSAGE_LINK_LOG` activos los enlaces pasan a los logs de Render (S-10; ya ocurrió en este proyecto). |
| DB-26 | P2 | CONFIRMADO | **Separación entre usuarios**: toda lectura de recursos propios filtra por identidad del `SecurityContext` (reglas de `AGENTS.md`; verificado en conversaciones con 404 a no participantes y en documentos con `findOwnedForUpdate`/`filter(d -> d.getTeacher().getId().equals(profile.getId()))`). La descarga admin de evidencia está limitada a `ROLE_ADMIN` (`SecurityConfig.java:117`). No se encontraron consultas que acepten IDs de propietario del cliente. No hay Row Level Security en PostgreSQL: la separación depende solo de la aplicación. |
| DB-27 | P2 | PENDIENTE | **Acceso a la base fuera de la aplicación**: la consola de Neon permite ver todos los datos y todos los PDFs si están en `bytea`. Verificar qué miembros del proyecto Neon tienen acceso, si hay roles de solo lectura separados de los de la aplicación, y que el rol de la aplicación no sea propietario/superusuario (Flyway necesita DDL; usar roles distintos para migrar y para ejecutar es lo recomendable). |
| DB-28 | P3 | CONFIRMADO | `statement_timeout` y `idle_in_transaction_session_timeout` no configurados (DB-02). |
| DB-29 | P3 | PENDIENTE | TLS a la base: la URL por defecto es `jdbc:postgresql://localhost` sin `sslmode`; para Neon la URL debe incluir `sslmode=require`. Verificar el valor real de `DB_URL` sin exponerlo. |

## 7. Estado de migraciones

- Herramienta: **Flyway**, 25 migraciones (`V1`..`V25`), ~880 líneas, sin huecos de numeración ni migraciones repetibles (`R__`) y sin duplicados. `ddl-auto=validate`; `flyway.enabled=true` con `locations: classpath:db/migration`; las migraciones corren al arrancar la aplicación.
- Convención respetada: nuevas versiones `V{n+1}`; `AGENTS.md` prohíbe editar históricas. No se verificó el checksum contra una base real (`flyway info`/`validate` requiere acceso).
- **Cambios destructivos históricos (ya aplicados)**: V17 `DROP TABLE bookings, calendly_integrations` y `DROP COLUMN reviews.booking_id`; V18 **`DELETE FROM reviews`** (deduplicación de pares, conserva la más reciente; el propio comentario recomienda backup previo); V8 `DROP COLUMN is_verified`; V10/V15/V14/V21 reemplazan CHECK (`DROP CONSTRAINT` + `ADD`) sin transacción de gracia. Todo ello implica que **no hay camino de vuelta** a esquemas anteriores sin restaurar un backup (relacionado con DB-01).
- **Rollback**: no existen migraciones de reversión (Flyway Community no las ofrece) ni documento de procedimiento. La estrategia efectiva es "restaurar backup + desplegar versión compatible" (documentado en la auditoría de infraestructura, `:319`). Sin ventana de mantenimiento definida.
- **Arranque automático de migraciones en cada instancia**: con varias instancias Flyway usa bloqueo de la tabla `flyway_schema_history`, por lo que la concurrencia es segura; sin embargo, un deploy con una migración larga (por ejemplo `CREATE INDEX` sin `CONCURRENTLY`, como en V11/V18/V24) bloquea escrituras sobre esa tabla durante la creación. Con volumen alto, crear índices con `CONCURRENTLY` exige migraciones fuera de transacción. Hoy el volumen es bajo (aceptable).
- **Compatibilidad app/esquema**: despliegue "big bang" (la app nueva ejecuta la migración al arrancar). La versión anterior de la aplicación que siga corriendo durante un deploy continuo (Render arranca la nueva antes de apagar la vieja) verá un esquema más nuevo: seguro mientras las migraciones sean aditivas (patrón expand/contract), y V17/V18 no lo fueron. No hay política escrita de compatibilidad N-1.
- **Extensiones**: `btree_gist` (V5) y `unaccent` (V25) requieren que el rol tenga permiso `CREATE EXTENSION` en Neon (permitidas en Neon; PENDIENTE de comprobar el rol de migración en producción).
- **Pruebas**: Testcontainers (`AbstractPostgresTest`) ejecuta todas las migraciones desde cero en cada suite; `SchemaIndexIntegrationTest` verifica índices. No hay prueba de migración sobre datos preexistentes (por ejemplo, V18 con duplicados).
- **Semilla de demo**: `db/seed/demo-data.sql` solo con `DEMO_SEED=true` (por defecto `false`) y `DemoDataProductionGuard`/`DemoDataQuarantineService`; verificar que la variable no esté activa en Render (PENDIENTE).

## 8. Estado de backups

| Aspecto | Estado | Evidencia |
|---|---|---|
| Backups automáticos de Neon | **PENDIENTE** (no verificado) | Sin evidencia en repo; `docs/audits/2026-11-13-production-infrastructure-report.md:306` pide verificarlo |
| Retención / ventana PITR | **PENDIENTE** | Depende del plan de Neon (Free ofrece ventana corta); no documentada |
| Prueba de restauración | **No realizada / sin evidencia** | Marcada como obligatoria en el informe de infraestructura (`:315`) |
| RPO / RTO | **No definidos** | `:312-314` |
| Backup propio (dump programado) | **No existe** | `:306` |
| Evidencia en R2 | Backup de Neon no la cubre; versioning/lifecycle **PENDIENTE** | `:308-309` |
| Secretos fuera del repo | Recomendado, no verificable | `:310` |
| Procedimiento escrito de restauración | **Parcial** (principios en `:315-319`; sin pasos ni responsables) | |

No se supone que existan backups recuperables. Riesgo: pérdida de datos por error humano, migración defectuosa o compromiso de cuenta; en el modo `bytea`, restaurar significa también restaurar PDFs.

## 9. Integraciones

| Integración | Uso | Timeouts | Reintentos / idempotencia | Credenciales | Hallazgos |
|---|---|---|---|---|---|
| **Google OAuth** | Validación del ID token en backend (`GoogleIdTokenVerifier`, `oauth.google.issuer`, `client-id`) | `JwtDecoder` de Spring inyectado (`GoogleIdTokenVerifier.java:7`); timeout de descarga de claves no configurado explícitamente en el repositorio (PENDIENTE de confirmar) | Sin estado propio; vinculación por `google_sub` único | `GOOGLE_CLIENT_ID` (público); sin secreto de cliente | Un fallo de decodificación devuelve 400 en vez de 503 (B-06); `client-id` vacío se detecta en arranque (validador de producción). |
| **Correo (Brevo API / SMTP)** | `BrevoEmailSender` (HTTPS) o `SmtpEmailSender`; encolado por outbox | Brevo: 10 s (`api-call-timeout`); SMTP: 5/10/10 s | Outbox: 5 intentos con backoff, `dedupe_key`, lease 2 min; entrega al menos una vez (duplicados posibles, B-10); **401/403/400 → DEAD sin reintento y se borra el token (B-01)** | `EMAIL_API_KEY`, `MAIL_*` por entorno; no se leyeron | `from` por defecto `no-reply@claseya.app` (dominio sin verificar → entregabilidad SPF/DKIM PENDIENTE); sin purga (DB-16); logs con PII en SMTP (DB-24). |
| **Cloudflare Turnstile** | Verificación en registro y en el gate humano | **Sin timeouts** en `RestClient`, dentro de `@Transactional` (B-06) | Sin reintento; el fallo del verificador bloquea el registro | `TURNSTILE_SECRET` por entorno; site key pública | Backend no impone el gate humano (S-05). |
| **Almacenamiento R2/S3** | `S3DocumentStorage`: `putObject`, `getObject`, `deleteObject` | 20 s total, 10 s por intento (`api-call-timeout`, `api-attempt-timeout`) | El SDK reintenta por defecto; la subida usa clave nueva (idempotente); limpieza de huérfanos best-effort (DB-17) | `STORAGE_ACCESS_KEY/SECRET_KEY` por entorno | Sin cifrado/versioning verificados (DB-03); límite 10 MB y 25 MB por credencial; sin integridad `sha256` contra el objeto al descargar (el hash se guarda pero no se re-verifica al leer). |
| **ClamAV** | `ClamAvDocumentScanner` (TCP INSTREAM) | `CLAMAV_TIMEOUT_MS` 5 s | Sin reintento; si cae, subidas 503; por defecto desactivado | Sin credenciales (red interna) | Render Free no aloja sidecar; sin escaneo en la configuración probable (S-04). |
| **Neon (PostgreSQL)** | Base única | Sin configuración (DB-02) | Hikari reintenta conexión; sin circuit breaker | `DB_*` | DB-01, DB-02, DB-23, DB-29. |
| **Vercel / Render** | Proxy `/api`, hosting | Timeout de funciones/proxy de Vercel (PENDIENTE; relevante para subidas de 10 MB) | — | — | Límite de cuerpo de Vercel para subidas (infra-report `:325`). |

Fallos parciales relevantes:
- Registro: usuario creado `PENDING` + fila de outbox en la misma transacción; el envío ocurre después. Si el proveedor rechaza de forma permanente, la cuenta queda sin posibilidad de activación hasta pedir reenvío (B-01).
- Subida de evidencia: objeto en R2 sin fila si la base falla (se intenta limpiar); fila sin objeto si el borrado en `delete` ocurre antes de un commit que falla.
- Google/Turnstile caídos: el mensaje al usuario puede ser engañoso (400 en vez de 503).

## 10. Recomendaciones

| # | Acción | Nivel (AGENTS.md) | Prioridad |
|---|---|---|---|
| 1 | Verificar en Neon: plan, retención/PITR, branches; ejecutar una restauración a un branch y levantar staging contra él; definir RPO/RTO y documentar el procedimiento | Operativo (sin código) | P1 |
| 2 | Configurar Hikari (`maximum-pool-size` acorde al plan de Neon, `connection-timeout`, `max-lifetime` menor que el idle de Neon, `keepalive-time`), `sslmode=require`, `connectTimeout/socketTimeout`, y decidir endpoint directo (Flyway) vs pooler (aplicación) | 1 | P1 |
| 3 | Decidir almacenamiento definitivo de evidencia: R2 privado con cifrado, versioning y lifecycle; plan para migrar los `bytea` legados; activar ClamAV o aceptar formalmente el riesgo | 2 (decisión de seguridad/datos) | P1 |
| 4 | Revisar `comandos.txt`, historial de Git y variables de Render/Vercel por credenciales; rotar contraseñas de Neon/Brevo si hubo exposición; usar roles separados (migración vs ejecución) y acceso mínimo a la consola de Neon | Operativo | P1 |
| 5 | Definir y aplicar política de retención/purga: outbox, tokens vencidos, evidencia de cuentas rechazadas, mensajes | 2 | P2 |
| 6 | Añadir `CHECK (email = lower(email))` o índice único `lower(email)` (verificar primero que no existan duplicados) | 2 (migración nueva `V26`) | P2 |
| 7 | Reclasificar evidencia legada `CLEAN` sin escaneo (`UNSCANNED`) | 2 (migración + decisión) | P2 |
| 8 | Medir con `EXPLAIN (ANALYZE, BUFFERS)` en un branch de prueba: búsqueda de profesores, materias, ciudad, directorio admin, sitemap; crear índices solo con evidencia (trigram/expresión) | 1–2 | P2 |
| 9 | Corregir B-01 (DEAD sin retry), timeouts de Turnstile y respuesta 503 de Google (informe 02) | 1 | P1/P2 |
| 10 | Política de eliminación/anonimización de cuenta y flujo de borrado ordenado | 2 | P2 |
| 11 | Añadir `statement_timeout`, `lock_timeout` e `idle_in_transaction_session_timeout` al rol de la aplicación | 1 | P3 |
| 12 | Job de reconciliación: agregados de rating y objetos huérfanos en R2 | 2 | P3 |
| 13 | Test de migración con datos previos (V18 con duplicados) y documento de compatibilidad N-1 (expand/contract) | 1 | P3 |
| 14 | Revisar índices sin uso (`idx_teacher_profiles_location`, `idx_messages_conversation_*`) con `pg_stat_user_indexes` | 1 | P3 |

## 11. Procedimientos pendientes de verificar

Requieren acceso de solo lectura autorizado; ninguno se ejecutó. No exponer valores de secretos en las salidas.

1. **Neon (panel)**: plan, región, endpoint directo vs pooler, retención de historial/PITR, branches, `max_connections`, autosuspend, miembros con acceso, roles de base de datos.
2. **Prueba de restauración**: crear un branch a un instante anterior, arrancar la app de staging contra él con la versión compatible, comprobar login, búsqueda, mensajes y `flyway info`.
3. **Flyway**: `flyway info`/`validate` contra producción (o `SELECT version, success, checksum FROM flyway_schema_history ORDER BY installed_rank;`) para confirmar que V1..V25 están aplicadas, sin entradas fallidas.
4. **Datos**: `SELECT count(*) FROM users WHERE email <> lower(email);` (DB-04); `SELECT scan_status, count(*) FROM verification_documents GROUP BY 1;` (DB-05); recuento de `notification_outbox` por `status` y de filas `DEAD` con `last_error` (B-01); `SELECT count(*) FROM verification_documents WHERE content IS NOT NULL;` (cuánta evidencia sigue en la base).
5. **Rendimiento**: `EXPLAIN (ANALYZE, BUFFERS)` de las consultas capturadas en staging; `pg_stat_statements` y `pg_stat_user_indexes` (índices sin uso).
6. **R2/S3**: bucket privado, bloqueo de acceso público, cifrado en reposo, versioning, lifecycle, política de acceso de la clave, región/endpoint.
7. **Brevo**: remitente/dominio verificado, SPF/DKIM/DMARC, lista de IP autorizadas de la API key (causa habitual del 401 reportado), cuota diaria.
8. **Render**: variables efectivas (sin imprimir valores): `SCAN_ENABLED`, `ALLOW_UNSCANNED_EVIDENCE`, `STORAGE_PROVIDER`, `DEMO_SEED`, `AUTH_LOG_ACTIVATION_LINKS`, `MESSAGE_LINK_LOG`, `BEHIND_TRUSTED_PROXY`, `DB_URL` con `sslmode`.
9. **Git**: búsqueda de secretos en el historial (`gitleaks` o equivalente; no se instaló ninguna herramienta en esta auditoría) y revisión de `comandos.txt`.
10. **Google**: orígenes autorizados exactos y timeouts de descarga de JWKS.
11. **Turnstile**: hostnames permitidos y comportamiento ante caída del servicio.

## Aspectos no verificables en esta auditoría

Estado real de Neon, Render, Vercel, R2, Brevo y Google; contenido de la base; planes de ejecución; resultados de tests; historial de Git; valores de las variables de entorno; el plan de Neon contratado y sus límites.
