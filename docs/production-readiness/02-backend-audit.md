# 02 — Auditoría de backend y API — ClaseYa

- **Fecha:** 2026-11-17
- **Rama / commit:** `main` @ `52cac65`
- **Modo:** revisión estática de solo lectura. No se modificó código ni dependencias, y **no se ejecutó la suite de tests ni se levantó la aplicación**. Las verificaciones fueron lectura de código, `grep`, `git` y el índice CodeGraph.
- **Alcance:** `src/main/java/com/claseya` (≈15,8 k líneas), `src/main/resources` (`application.yml`, migraciones `V1..V25`), `pom.xml`, `Dockerfile` y los tests que respaldan cada flujo.
- **Clasificación:** **CONFIRMADO** (comprobado en el código), **PROBABLE** (indicios suficientes, falta una prueba o un dato del entorno), **PENDIENTE** (requiere acceso a la infraestructura).
- **Relación con informes previos:** `00-project-baseline.md` (los `R-xx`) y `01-security-audit.md` (los `S-xx`). Aquí no se repiten los hallazgos de seguridad salvo cuando tienen una causa de diseño propia.

---

## 1. Resumen ejecutivo

El backend está bien construido. Las capas están separadas con disciplina (ningún controller referencia un repositorio), la lógica de negocio vive en servicios transaccionales, las respuestas son DTOs, los errores pasan por un único `GlobalExceptionHandler`, y las operaciones sensibles a la concurrencia usan bloqueos pesimistas, restricciones de base de datos y `UPDATE` atómicos (consumo de tokens, reserva de par de conversación, cola de outbox con `FOR UPDATE SKIP LOCKED`). El flujo de verificación tiene una máquina de estados explícita, auditoría append-only y un test de concurrencia.

Los hallazgos relevantes no son de estructura sino de **invariantes del flujo de verificación, resiliencia ante fallos de proveedores y madurez operativa**:

1. **El flujo de verificación permite mutar la evidencia después de presentarla.** Se puede subir un documento a una credencial en `UNDER_REVIEW` o `VERIFIED` (B-03), y los campos de una credencial en revisión siguen siendo editables (B-04). Es lo opuesto a lo que la propia especificación llama inmutabilidad de la evidencia (RF-18/RF-21).
2. **Un error de configuración del proveedor de correo se convierte en pérdida permanente de mensajes** (B-01). Es exactamente lo que ocurrió en producción con el `401` de Brevo.
3. **Orden de bloqueos inconsistente** entre la subida de documentos y las decisiones/presentaciones de verificación, con riesgo de deadlock (B-02, probable).
4. **Datos operativos sin retención** (outbox con emails y tokens expirados, B-05), **llamadas externas sin timeout** (Turnstile, B-06) y **lecturas públicas sin límite ni caché** (B-07).

No hay hallazgos P0. Hay 1 P1, 6 P2 y varios P3 (§3).

| Prioridad | Cantidad |
|---|---:|
| P0 | 0 |
| P1 | 1 |
| P2 | 6 |
| P3 | 9 |
| INFO | 3 |

**Veredicto:** el backend es apto para una beta controlada. Antes de abrir tráfico real conviene cerrar B-01, B-03 y B-04, que son los que afectan directamente al producto (correo de activación y confiabilidad de la verificación).

---

## 2. Arquitectura evaluada

### 2.1 Estructura

Monolito modular con 17 paquetes bajo `com.claseya`. Capas `controller → service → repository → dto`, con `model` y `common` como hojas. **CONFIRMADO** (estructura de paquetes y `docs/architecture/architecture.md`).

| Criterio | Resultado | Evidencia |
|---|---|---|
| Controllers sin repositorios ni lógica | Cumple | `grep -rl "Repository" --include=*Controller.java` → sin resultados |
| Servicios que devuelven entidades por HTTP | No se encontró | Respuestas con records `*Response`; `requireByUser`/`requireVisible` devuelven entidades solo entre servicios |
| Ciclos entre paquetes | Vigilados por ArchUnit | `ArchitectureTest.java:19-23` (`slices().matching("com.claseya.(*)..").should().beFreeOfCycles()`) |
| Mappers | Métodos estáticos `from(...)` en los DTOs y `TeacherSummaryAssembler` | Sin librería de mapeo; coherente y simple |
| Validación | Bean Validation en los request records; reglas de negocio en servicios | `CreateTeacherProfileRequest`, `MessageService.requireValidContent` |

### 2.2 Acoplamiento entre módulos

Hay acoplamiento unidireccional a nivel de repositorio entre servicios de módulos distintos (cálculo propio sobre las importaciones de `*Service.java`): `verification → teacher` (7 importaciones de repositorios), `review → messaging/student/teacher`, `messaging → student/teacher`, `favorite → student/teacher`, `student → academic`, `teacher → academic`. No hay ciclos. La regla "sin repositorios cruzados" no existe en ArchUnit y `docs/architecture/guardrails.md` la reconoce como pendiente. **CONFIRMADO**.

Responsabilidades mal ubicadas: la regla de elegibilidad de reseñas ("contacto aceptado") se implementa dentro de `ReviewService` consultando repositorios de `messaging` (`ReviewService.java:~215`, `requireAcceptedContact`), es decir, una regla del dominio de mensajería vive en `review`. Es deuda menor (B-16).

### 2.3 Calidad de la capa transaccional

- Todos los servicios de `service/` usan `@Transactional` o `TransactionTemplate`; ninguno queda sin transacción. **CONFIRMADO** (barrido de `*Service.java`).
- Las llamadas a ClamAV y S3 ocurren **fuera** de la transacción en la subida de documentos (`VerificationDocumentService.java:72-91`) y el worker de correos envía fuera de la transacción de reclamo (`NotificationOutboxWorker.java:79-93`). Es un patrón correcto.
- `spring.jpa.open-in-view=false` (`application.yml`), por lo que no hay lazy loading en la capa web.
- No hay ninguna entidad con `@Version`; la concurrencia se resuelve con 5 consultas de bloqueo pesimista (`findByIdForUpdate`, `findOwnedForUpdate`, `FOR UPDATE SKIP LOCKED`) y restricciones únicas. **CONFIRMADO** (B-13).

---

## 3. Hallazgos priorizados

### P1

#### B-01 — Un `401/403` de Brevo marca el mensaje como `DEAD` de forma permanente (CONFIRMADO)
- **Archivos:** `notification/service/BrevoEmailSender.java:82-85`, `notification/service/NotificationOutboxWorker.java:119-125`, `notification/repository/NotificationOutboxRepository.java` (`markDead`).
- **Evidencia:** `BrevoEmailSender` lanza `PermanentNotificationException` para los estados 400, 401 y 403. El worker la captura, ejecuta `markDead` y no reintenta; además elimina `token` del payload de las filas `ACTIVATION` y `PASSWORD_RESET`.
- **Por qué es un defecto:** un `401` o `403` describe un problema de credenciales o de remitente del servicio, no del mensaje. Cada correo que se intente mientras el problema existe queda perdido, aunque el operador lo corrija minutos después. Esto ocurrió: el registro de prueba terminó con una fila `DEAD` (`Email provider rejected the request (401)`) y el usuario quedó `PENDING` sin correo.
- **Impacto:** pérdida silenciosa de correos de activación, recuperación de contraseña y avisos de contacto; cuentas atrapadas en `PENDING`. El token se borra, así que ni siquiera se puede reenviar la misma fila: hace falta una nueva solicitud del usuario.
- **Endpoints afectados:** `POST /api/auth/register`, `POST /api/auth/resend-activation`, `POST /api/auth/forgot-password`, `POST /api/conversations`.
- **Solución recomendada:** separar los errores del mensaje (400 de payload inválido, destinatario inválido) de los errores del proveedor (401, 403, 429, 5xx). Para estos últimos, reintento con backoff largo y un contador de métrica/alerta, y una herramienta administrativa o consulta documentada para reencolar las filas `DEAD` de un período. Mantener el token en el payload mientras la fila pueda reintentarse.
- **Tests necesarios:** `BrevoEmailSenderTest` con una respuesta 401 que verifique que la excepción es reintentable (o que la fila pasa a `FAILED`); `NotificationOutboxIntegrationTest` que corrija la configuración y compruebe que la fila se entrega después.

### P2

#### B-02 — Orden de bloqueos inconsistente entre la subida de documentos y las decisiones de verificación (PROBABLE)
- **Archivos:** `verification/service/VerificationDocumentService.java:93-117` (`persist`), `verification/service/VerificationSubmissionService.java:50-51,60,71` (`lockOwnProfile`), `verification/service/VerificationDecisionService.java:71,108`, `teacher/repository/TeacherProfileRepository.java:40-42`, `teacher/repository/TeacherEducationRepository.java:28-30`.
- **Evidencia:** `submit` y `decide` bloquean primero la fila del **perfil** (`findByIdForUpdate`) y luego actualizan la fila de la **credencial** (`saveAndFlush`). `persist` bloquea primero la fila de la **credencial** (`findOwnedForUpdate`) y luego inserta un documento con claves foráneas al perfil y a la credencial.
- **Escenario:** una decisión del administrador y una subida del profesor para la misma credencial, concurrentes. La decisión retiene el bloqueo del perfil y espera el de la credencial; la subida retiene el de la credencial y, al insertar la fila con FK al perfil, espera el del perfil. PostgreSQL detecta el ciclo y aborta una de las dos con `40P01`.
- **Impacto:** una de las dos operaciones falla con `500 Internal Server Error` (la excepción de deadlock cae en `handleUnexpected`, `GlobalExceptionHandler.java:178`) y no se reintenta. Es raro pero ocurre justo en el momento más delicado (la revisión).
- **Qué falta para confirmarlo:** una prueba de concurrencia. `VerificationConcurrencyIntegrationTest` solo cubre dos decisiones simultáneas (`twoConcurrentDecisions_oneWinsAndTheOtherConflicts`).
- **Solución recomendada:** adoptar un único orden (perfil → credencial → documento) en todos los caminos, es decir, bloquear el perfil también en `persist`; o capturar `PessimisticLockingFailureException`/`DeadlockLoserDataAccessException` y devolver `409` con un reintento controlado.
- **Test necesario:** prueba de integración con hilos que ejecute `upload` y `decide`/`submit` a la vez sobre la misma credencial y verifique que no hay `500`.

#### B-03 — Se pueden subir documentos a una credencial `UNDER_REVIEW` o `VERIFIED` (CONFIRMADO)
- **Archivos:** `verification/service/VerificationDocumentService.java:72-91` (`upload`), `:93-117` (`persist`); contraste con `:198-207` (`delete`).
- **Evidencia:** `upload` solo comprueba propiedad (`requireOwnedCredential`), contenido y cuota. No comprueba el estado de la credencial. En cambio `delete` bloquea cualquier borrado cuando la credencial fue presentada, tiene un estado distinto de `PENDING` o tiene decisiones, con el comentario "Evidence is immutable once it has been presented (RF-18/RF-21)".
- **Escenario 1:** un profesor con la credencial `VERIFIED` sube documentos nuevos sin revisar; quedan asociados a una credencial ya aprobada.
- **Escenario 2:** el administrador marcó todos los documentos como revisados y decide `VERIFIED` mientras el profesor sube otro; la comprobación `requireReviewedEvidence` (`VerificationDecisionService.java:~115`) lee los documentos antes de que se confirme la subida.
- **Impacto:** el conjunto de evidencia sobre el que se decidió ya no es el que está almacenado; la auditoría pierde valor. Contradice la intención de RF-18/RF-21 (inmutabilidad asimétrica: se puede añadir, no quitar).
- **Endpoint afectado:** `POST /api/teachers/me/education/{educationId}/documents`.
- **Solución recomendada:** rechazar la subida (409) cuando la credencial esté en `UNDER_REVIEW` o `VERIFIED`; permitirla en `PENDING`, `MORE_INFO_REQUIRED` y `REJECTED`, que son los estados en los que el profesor debe poder completar la evidencia. Hacerlo bajo el mismo bloqueo (B-02).
- **Tests necesarios:** subir un documento en cada estado y verificar 201/409; test de concurrencia subida vs decisión.

#### B-04 — Los campos de una credencial en revisión siguen siendo editables (CONFIRMADO)
- **Archivo:** `teacher/service/TeacherEducationService.java:61-87`.
- **Evidencia:** `update` congela institución, título y años solo cuando el estado es `VERIFIED` (`:68`). En `UNDER_REVIEW` el profesor puede cambiar esos campos libremente. `VerificationDecision` no guarda una copia de los datos sobre los que se decidió.
- **Escenario:** el profesor presenta "Licenciatura en Matemática, UBA", el administrador revisa el PDF y, antes de decidir, el profesor cambia el título a otro. El administrador aprueba la credencial con el contenido modificado.
- **Impacto:** la verificación puede quedar asociada a datos distintos de los revisados; se muestra una insignia "Verificado" sobre un texto que nadie validó.
- **Endpoint afectado:** `PUT /api/teachers/me/education/{id}`.
- **Solución recomendada:** congelar los campos materiales también en `UNDER_REVIEW` (o devolver la credencial a `PENDING` al editarla), y guardar en la decisión un resumen (`institution`, `degree`, años) de lo aprobado.
- **Test necesario:** `update` en `UNDER_REVIEW` con cambio material → 409.

#### B-05 — Sin política de retención: outbox con datos personales y tokens expirados (CONFIRMADO)
- **Archivos:** `notification/repository/NotificationOutboxRepository.java`, `model/NotificationOutboxEntry.java`, repositorios de tokens (`EmailVerificationTokenRepository`, `PasswordResetTokenRepository`, `MessageLinkTokenRepository`).
- **Evidencia:** la búsqueda de `delete`/`purge`/`retention`/`cleanup` en `src/main` solo encuentra el cierre de buckets del rate limiter (`security/ratelimit`) y el borrado de bloqueos de usuario. No hay trabajo que elimine filas `SENT`/`DEAD` del outbox ni tokens usados o vencidos. Las filas de contacto conservan el nombre del estudiante y el email del destinatario es resoluble por `recipient_user_id`; las de activación y reseteo guardan el email en el payload.
- **Impacto:** crecimiento ilimitado de tablas, y datos personales (emails, nombres) retenidos indefinidamente sin necesidad; se complica una futura solicitud de borrado de datos (R-10).
- **Solución recomendada:** un trabajo programado diario que elimine o anonimice `SENT`/`DEAD` con más de N días y tokens expirados o usados; documentar la retención.
- **Test necesario:** test de integración del trabajo de limpieza con filas de cada estado y edad.

#### B-06 — Llamadas externas sin timeout configurado: Turnstile (y Google JWKS por defecto) (CONFIRMADO / PROBABLE el efecto)
- **Archivos:** `auth/captcha/TurnstileCaptchaVerifier.java:32-41,59-65`, `auth/service/AuthService.java:62,72`, `oauth/config/GoogleOAuthConfig.java`.
- **Evidencia:** el cliente de Turnstile se construye con `builder.baseUrl(verifyUrl).build()` sin fábrica con tiempos límite, mientras que el cliente de Brevo sí los define (`BrevoEmailClientConfig.java:25-32`, 10 s) y S3 y ClamAV también (`application.yml`, `app.storage.*`). `application.yml` no define `spring.http.client.*`. `register` es `@Transactional` (`AuthService.java:62`) y llama a `captchaVerifier.verify` al inicio (`:72`).
- **Impacto:** si Cloudflare responde lento o no responde, el hilo del servidor queda bloqueado (se agotan los hilos de Tomcat) y, según cuándo adquiera la conexión la transacción, puede retener una conexión del pool. El propio proyecto lo identificó como riesgo `TX-01` para SMTP y Brevo.
- **Endpoints afectados:** `POST /api/auth/register`, `POST /api/human/verify`.
- **Solución recomendada:** asignar tiempos de conexión y lectura al `RestClient` de Turnstile (igual que Brevo) y mover la verificación del CAPTCHA fuera de la transacción de registro.
- **Test necesario:** prueba con un servidor simulado que no responde y verificación de que `verify` falla con `503` en el tiempo configurado.
- **Relacionado (P3):** `GoogleIdTokenVerifier.java:38-41` captura toda excepción de `decode` y responde `400 Invalid Google token`, de modo que una caída de Google (descubrimiento del issuer o JWKS inaccesible) se reporta al usuario como token inválido en lugar de `503`. La biblioteca usa sus tiempos por defecto; no hay configuración propia.

#### B-07 — Lecturas públicas sin límite de tasa ni caché; sitemap de hasta 10 000 filas por request (CONFIRMADO)
- **Archivos:** `security/ratelimit/RateLimitFilter.java` (`scopeFor`, solo `POST`), `teacher/service/SitemapService.java:33,90-93`, `teacher/service/TeacherSearchService.java:60-75`.
- **Evidencia:** `scopeFor` devuelve `null` para cualquier método que no sea `POST`, de modo que `GET /api/teachers`, `GET /api/teachers/{id}`, `GET /api/subjects/**` y `GET /sitemaps/**` no tienen límite. `SitemapService` carga hasta `10_000` perfiles con su usuario por cada solicitud a `/sitemaps/teachers-{n}.xml` (`findAll(TeacherSpecifications.visible(), pageable)` más la consulta de conteo). No hay `@Cacheable` ni cabeceras de caché observadas en el servicio.
- **Escenario:** un bot repite `GET /sitemaps/teachers-1.xml` o consultas de búsqueda con filtros costosos (cuatro subconsultas `EXISTS` de la regla D1 por fila).
- **Impacto:** consumo de CPU, memoria y conexiones de la base de datos; en una instancia pequeña de Render y con Neon, degradación del servicio completo.
- **Solución recomendada:** caché del sitemap (en memoria con TTL o en la CDN con `Cache-Control`), un límite por IP para lecturas públicas (con la salvedad de S-01) y un máximo de tamaño de página más bajo para el sitemap.
- **Test necesario:** prueba de carga (`scripts/load`) sobre los endpoints públicos con el sitemap incluido.

#### B-08 — Pool de conexiones y ciclo de vida sin configurar para Neon (PENDIENTE)
- **Archivo:** `src/main/resources/application.yml` (sin `spring.datasource.hikari.*`).
- **Evidencia:** no hay `maximum-pool-size`, `connection-timeout`, `max-lifetime`, `idle-timeout` ni `keepalive-time`. Se usan los valores por defecto de HikariCP (10 conexiones).
- **Impacto probable:** con una base serverless que corta conexiones inactivas o escala a cero, las conexiones obsoletas del pool producen errores intermitentes al primer request; con 10 conexiones por instancia y varias instancias se puede superar el límite de la capa gratuita.
- **Solución recomendada:** definir explícitamente el tamaño máximo, `max-lifetime` menor que el tiempo de corte del proveedor y `keepalive-time`; verificar los límites del plan de Neon (**PENDIENTE**, dato de infraestructura).

### P3

- **B-09 — `DataIntegrityViolationException` siempre es `409` y los fallos de bloqueo son `500`** (CONFIRMADO). `GlobalExceptionHandler.java:86-90` responde `409 "Resource already exists or conflicts with existing data"` ante cualquier violación de integridad, incluidas `NOT NULL` y `CHECK`, que son errores de servidor o de validación (400/500) y no un conflicto. Los deadlocks y los timeouts de bloqueo (`PessimisticLockingFailureException`) caen en `handleUnexpected` (`:178`, 500). **Solución:** distinguir por `SQLState` (23505 único → 409; 23502/23514 → 500 registrado) y mapear `40P01`/`55P03` a `409` o `503` con `Retry-After`. **Test:** forzar una violación `CHECK` y un deadlock.
- **B-10 — Entrega al menos una vez: posible correo duplicado** (CONFIRMADO). `NotificationOutboxWorker.java:112-113` envía y luego marca `SENT` en otra transacción; si el `markSent` falla o la instancia muere entre ambos pasos, la fila vuelve a `PENDING` al vencer el lease (2 minutos, `markSending`) y se reenvía. Para activación y reseteo el reenvío es inocuo; para contacto emite un token de enlace nuevo. **Solución:** aceptarlo y documentarlo, o guardar un identificador de idempotencia (Brevo admite cabecera `idempotencyKey` en algunos planes; verificar en la documentación del proveedor).
- **B-11 — La mensajería no comprueba el estado del destinatario** (CONFIRMADO). `MessageService.send` (`MessageService.java:89-118`) valida acceso y bloqueos, pero no que el otro participante esté `ACTIVE` ni que el perfil del profesor siga visible. Un profesor suspendido sigue recibiendo mensajes. Es una decisión de producto, no un fallo de seguridad. **Solución:** definir la regla (¿se puede escribir a una cuenta suspendida?) y probarla.
- **B-12 — Los avisos por email solo existen para el primer contacto** (CONFIRMADO, producto). `ConversationService.java:139` encola `enqueueContactRequest` solo al crear la conversación; los mensajes posteriores no avisan. Si el profesor no entra, no ve las respuestas. No es un defecto de código, pero afecta a la utilidad del producto.
- **B-13 — Sin bloqueo optimista** (CONFIRMADO). Ninguna entidad tiene `@Version`. Dos pestañas que actualicen el perfil, una credencial o una reseña a la vez resuelven por «gana la última escritura». Impacto bajo; relevante para el perfil del profesor.
- **B-14 — Propiedades duplicadas de URL base** (CONFIRMADO). `app.public-url`, `app.notification.link-base-url` y `app.auth.activation.base-url` leen todas `APP_BASE_URL` (`application.yml`). Es consistente hoy, pero cada una se valida o se usa por separado (`NotificationOutboxWorker.java`, `AccountActivationService`). **Solución:** una sola propiedad.
- **B-15 — Configuración por entorno solo por perfil `local`** (CONFIRMADO). No hay `application-prod.yml`; producción depende de variables de entorno y de `ProductionConfigValidator`, que actúa «fuera de `local`/`test`». Es una decisión válida, pero hay que mantener la lista de variables en un solo documento (R-09 del informe 00).
- **B-16 — Regla de elegibilidad de reseñas en el módulo equivocado** (CONFIRMADO). Ver §2.2; `ReviewService` consulta repositorios de `messaging`. Mover la comprobación a `messaging` (por ejemplo `ConversationService.hasTeacherReplied(...)`).
- **B-17 — Logs: sin formato estructurado por defecto ni correlación en el worker** (CONFIRMADO). El patrón incluye `requestId` (`application.yml`, `logging.pattern.level`), y `RequestIdFilter` valida el identificador entrante, pero el JSON estructurado depende de `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`, y las filas del outbox no guardan el `requestId` del request que las generó; por eso los logs `Outbox … DEAD` no se relacionan con el registro que las originó.

### INFO

- **B-18.** La respuesta del profesor a una reseña se puede reemplazar sin límite (`ReviewService.java:143-151`). La especificación lo permite (OPINION-001 RF-10: «crear o reemplazar su respuesta»); el `README.md` dice «responder una vez». Es solo una inconsistencia de documentación.
- **B-19.** Los códigos de estado son coherentes: `201` para creaciones, `204` para borrados y acciones sin cuerpo, `202` para flujos asíncronos de autenticación y `200` con cuerpo para el resto. Las acciones se modelan como `POST /{id}/{acción}` (`/api/availability/{id}/disable`, `/api/admin/reviews/{id}/hide`), mientras que el resto usa `PATCH`/`PUT` (`PATCH /api/conversations/{id}/read`, `PATCH /api/admin/reports/{id}/resolution`). Es una inconsistencia de estilo, no un defecto.
- **B-20.** `revoke` revoca «la última credencial verificada» (`VerificationDecisionService.java:~120`); si un profesor tiene varias credenciales `VERIFIED`, el perfil sigue `VERIFIED` tras la primera revocación. Es coherente con `VerificationAggregator` (cualquier credencial verificada eleva el perfil), pero conviene documentarlo para el administrador.

---

## 4. Endpoints afectados

| Endpoint | Hallazgos |
|---|---|
| `POST /api/auth/register`, `resend-activation`, `forgot-password` | B-01, B-06, B-10 |
| `POST /api/human/verify` | B-06 |
| `POST /api/auth/google` | B-06 (P3) |
| `POST /api/conversations`, `POST /api/conversations/{id}/messages` | B-01, B-11, B-12 |
| `POST /api/teachers/me/education/{id}/documents` | B-02, B-03 |
| `POST /api/teachers/me/education/{id}/submit`, `POST /api/teachers/me/verification/submit` | B-02 |
| `PUT /api/teachers/me/education/{id}` | B-04 |
| `POST /api/admin/verifications/{teacherId}/credentials/{educationId}/decision`, `…/revoke` | B-02, B-20 |
| `POST /api/teachers/*/reviews`, `PUT /api/reviews/{id}` | B-13, B-16 |
| `GET /api/teachers`, `GET /api/teachers/{id}`, `GET /api/subjects/**`, `GET /sitemaps/**` | B-07 |

---

## 5. Riesgos de consistencia

| Riesgo | Mecanismo actual | Brecha |
|---|---|---|
| Un solo par estudiante-profesor tiene una sola conversación | Tabla `conversation_pairs` con `INSERT … ON CONFLICT DO NOTHING`; test `ConversationConcurrencyIntegrationTest` | Ninguna. Correcto. |
| Un solo uso de tokens | `UPDATE … WHERE used_at IS NULL AND expires_at > :now` | Ninguna. Correcto. |
| Estado agregado del perfil (RF-14) | Bloqueo pesimista del perfil + `VerificationAggregator` | B-02 (orden de bloqueos). |
| Cuota de documentos por credencial | Bloqueo de la fila de la credencial + doble comprobación | B-03 (estado), B-02. |
| Agregado de rating | `lockTeacher` antes de `recalculate`; `UNIQUE (student_id, teacher_id)` (`V18`) | Ninguna encontrada. La comprobación previa `existsBy…` puede perder una carrera, y el índice único responde con `409` por `handleDataIntegrityViolation`. |
| Entrega de correo | Outbox con `SKIP LOCKED`, lease de 2 minutos | B-01 (pérdida por error de configuración), B-10 (duplicados). |
| Disponibilidad sin solapamientos | `EXCLUDE USING gist` (`V5`) | Ninguna. |
| Lost update de perfiles | Última escritura gana | B-13. |

---

## 6. Riesgos de rendimiento

- **Búsqueda pública** (`TeacherSearchService.search`): especificación con cuatro `EXISTS` por fila para la regla D1 (`TeacherSpecifications.java:~60-90`), carga del usuario con `@EntityGraph` (`TeacherProfileRepository.java:31-33`) y resumen por lotes (`TeacherSummaryAssembler` usa `findByTeacher_IdIn`, sin N+1). Tamaño máximo de página 50 (`TeacherSearchService.java:34`). Los índices existentes cubren `verification_status`, `modality`, ubicación (`V1`, `V3`). No se midió el plan de ejecución. **PENDIENTE:** ejecutar `EXPLAIN` con un volumen realista.
- **Autocompletado de materias** (`SubjectRepository.searchActiveByNameIgnoringAccents`): `unaccent(lower(name)) LIKE '%…%'` no puede usar un índice B-tree; hay `idx_subjects_active_name` (`V11`) para el orden, pero no un índice trigram. Con un catálogo pequeño es irrelevante; con decenas de miles de materias sería un barrido secuencial. **P3.**
- **Materias populares** (`SubjectRepository.findPopular`): una consulta agregada con `UNION` y `count(distinct)` sobre todas las materias de profesores activos; razonable, pero no tiene caché y se ejecuta en la portada. **P3.**
- **Hilo de mensajes**: paginación por cursor (`REVIEW2-003`) con el índice `idx_messages_conversation_created_id` (`V24`). Correcto.
- **Lista de conversaciones**: la última mensajería de cada conversación usa una consulta con subselect correlacionado (`MessageRepository.findLastPerConversation`, `:93-103`), una consulta por página; correcto con el índice `V4`/`V24`.
- **Directorio de administración**: `lower(email) LIKE :q ESCAPE '!'` con comodín inicial; sin índice. Aceptable con pocos usuarios (**P3**).
- **Subida de documentos**: `file.getBytes()` carga hasta 10 MB en memoria por request (`TeacherVerificationController.java`), y ClamAV recibe el arreglo completo. Con el límite de 20 subidas por hora y usuario y la cuota de memoria de la JVM (`-XX:MaxRAMPercentage=75`) es manejable en una instancia pequeña, pero 12 solicitudes simultáneas de 10 MB suman 120 MB. **P3.**
- **Hilos y bloqueo**: el procesamiento es síncrono sobre Tomcat; el worker del outbox corre en el programador de Spring (un hilo) y usa `fixedDelay`, de modo que una entrega lenta retrasa al resto del lote (10 mensajes, timeout de 10 s por mensaje: hasta 100 s por pasada en el peor caso). **P3.**

---

## 7. Integraciones externas

| Integración | Timeout | Reintentos | Idempotencia | Fallo parcial | Resultado |
|---|---|---|---|---|---|
| **Brevo** (HTTP) | 10 s conexión y lectura (`BrevoEmailClientConfig`) | Backoff por fila, máx. 5 intentos (`application.yml`) | Al menos una vez (B-10) | Fila `DEAD` ante 400/401/403 (**B-01**) | Mejorable |
| **SMTP** | 5 s / 10 s / 10 s (`application.yml`) | Igual que Brevo | Al menos una vez | Igual | Correcto |
| **Google ID token** (JWKS) | Por defecto de la biblioteca (no configurado) | No | No aplica | Caída → `400` en vez de `503` (B-06) | Mejorable |
| **Turnstile** | **Ninguno** (B-06) | No | No aplica | `RestClientException` → `503` (`TurnstileCaptchaVerifier.java:69`) | Corregir |
| **S3/R2** | 20 s por llamada / 10 s por intento (`application.yml`) | El SDK reintenta | La clave es un UUID nuevo; un fallo posterior borra el huérfano (`deleteOrphan`) | Un objeto huérfano si falla la limpieza (aceptado en el código) | Correcto |
| **ClamAV** (INSTREAM) | 5 s (`app.storage.scan.timeout-ms`) | No | No aplica | Escáner caído → `503`, infectado → `400` | Correcto |

---

## 8. Configuración y dependencias

- **Versiones:** Java 17, Spring Boot 3.5.16, jjwt 0.12.6, bucket4j 8.21.0, Testcontainers 1.20.4 (`pom.xml`). El análisis de vulnerabilidades de dependencias Maven **no** se ejecutó (S-09).
- **Defaults de desarrollo en `application.yml`:** `jwt.secret` (validado), `DB_*` con credenciales de desarrollo (sin validador, S-11), `COOKIE_SECURE=true` por defecto (correcto), `RATE_LIMIT_ENABLED=true`, `DEMO_SEED=false` (correcto), `CAPTCHA_ENABLED=false` y `SCAN_ENABLED=false` (validados fuera de `local`/`test`), `AUTH_LOG_ACTIVATION_LINKS=false` y `MESSAGE_LINK_LOG=false` (**sin validador**, S-10).
- **Inconsistencias entre entornos:** el perfil `local` activa el seed, el log de enlaces y el almacenamiento `postgres` (`application-local.example.yml`); producción cambia a S3/R2, CAPTCHA y ClamAV por variables. El mecanismo es sólido, pero no hay un test que ejecute el arranque completo con un conjunto de variables de producción de ejemplo (solo pruebas unitarias del validador).
- **Pool de conexiones y HTTP clients:** ver B-08 y B-06.

---

## 9. Logging y observabilidad

- **Correlación:** `RequestIdFilter` acepta un `X-Request-Id` válido (`[A-Za-z0-9._-]{8,128}`) o genera uno, lo coloca en el MDC y en la respuesta; el patrón de log lo incluye. **CONFIRMADO**.
- **Formato:** texto plano por defecto; JSON (`ecs`) con una variable de entorno (`LOGGING_STRUCTURED_FORMAT_CONSOLE`).
- **Información sensible:** no se encontraron registros de contraseñas ni JWT. Los enlaces solo se registran con las banderas de desarrollo (S-10). `log.warn("Turnstile verification is unavailable", ex)` imprime la traza completa de la excepción (`TurnstileCaptchaVerifier.java:69`), sin datos del usuario.
- **Métricas:** el worker publica contadores `claseya.notification.outbox.{delivered,failed,dead}` con la etiqueta `event_type` (`NotificationOutboxWorker.java`), y existe `OutboxHealthIndicator`. `/actuator/metrics` es solo para `ADMIN` y no hay exportador (Prometheus pendiente, `docs/architecture/security.md`). En consecuencia **nadie recibe una alerta** cuando aparecen filas `DEAD` (B-01). **PENDIENTE:** confirmar si hay un sistema de alertas en Render.
- **Diagnóstico:** los errores `5xx` se registran con traza completa (`GlobalExceptionHandler.java:178-181`) y el cliente recibe un mensaje genérico; correcto.

---

## 10. Recomendaciones

1. **B-01:** reclasificar `401/403/429/5xx` del proveedor como reintentables, con alerta sobre el contador `dead`, y una consulta documentada para reencolar. (Es la que más afecta al producto hoy.)
2. **B-03 y B-04:** bloquear subidas y ediciones materiales cuando la credencial está en revisión o verificada. Son cambios de reglas de negocio y de contrato → **Nivel 2** según `AGENTS.md`.
3. **B-02:** unificar el orden de bloqueo y mapear los errores de bloqueo a `409`.
4. **B-06:** timeouts para Turnstile y mover el CAPTCHA fuera de la transacción.
5. **B-07:** caché y límite para lecturas públicas.
6. **B-05:** trabajo de retención del outbox y los tokens.
7. **B-08:** configuración explícita de Hikari para Neon.
8. Resto (P3): según capacidad.

---

## 11. Tests necesarios

| ID | Test | Tipo |
|---|---|---|
| B-01 | Respuesta 401 de Brevo deja la fila reintentable; tras corregir la configuración se entrega | Unitario + integración |
| B-02 | Subida concurrente con decisión/presentación de la misma credencial sin `500` | Integración con hilos |
| B-03 | Subida de documento en cada estado de la credencial (201 o 409) | Integración |
| B-04 | `PUT` con cambio material en `UNDER_REVIEW` → 409 | Integración |
| B-05 | Trabajo de limpieza elimina outbox y tokens vencidos y conserva los vigentes | Integración |
| B-06 | Turnstile que no responde → `503` dentro del tiempo configurado | Unitario con servidor simulado |
| B-07 | Carga sobre los endpoints públicos y el sitemap con volumen realista; cabecera de caché | Carga / integración |
| B-09 | Violación `CHECK` → 500 registrado; deadlock → 409/503 | Integración |
| B-11 | Mensaje a una cuenta suspendida (regla definida) | Integración |
| General | `EXPLAIN` de búsqueda con datos realistas; arranque con variables de producción de ejemplo | Manual / CI |

La suite existente cubre muchos de estos flujos (88 archivos de test, 528 `@Test`), pero **no se ejecutó** en esta auditoría y no se revisó el detalle de sus aserciones.

---

## 12. Plan de remediación

| Fase | Contenido | Nivel (`AGENTS.md`) |
|---|---|---|
| **Inmediata** (antes de abrir tráfico) | Confirmar remitente y clave de Brevo; reencolar o reenviar las activaciones perdidas; B-01 con alerta de `dead` | B-01: Nivel 1 si solo cambia la clasificación de errores; Nivel 2 si se añade un endpoint o una tabla |
| **Corta** (1-2 semanas) | B-03, B-04 (invariantes de verificación), B-02 (orden de bloqueos), B-06 (timeouts), B-08 (pool) | B-03/B-04: Nivel 2 (reglas de negocio); B-02, B-06, B-08: Nivel 1 |
| **Media** | B-05 (retención), B-07 (caché y límites públicos), B-09 (mapeo de errores), B-17 (correlación del outbox) | B-05 y B-07: Nivel 2 (migración o contrato) |
| **Continua** | P3 y reglas ArchUnit de repositorios cruzados (ver `guardrails.md`) | Nivel 1 |

**No se modificó ningún archivo del proyecto salvo este informe** (y la corrección de un número de línea en `00-project-baseline.md`, `BrevoEmailSender.java:82-85`).
