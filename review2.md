# Revisión técnica — ClaseYa (segunda pasada)

**Fecha:** 29 de octubre de 2026
**Commit base:** `563302f` (`feat(verification): store evidence in R2 with ClamAV scanning`)
**Alcance:** backend Spring Boot 3.5.16 / Java 17, frontend React 18 + TypeScript 5.6, esquema
Flyway `V1..V10`, configuración, Docker, CI y documentación normativa.
**Método:** revisión estática del código y de los documentos rectores (`AGENTS.md`, PRD,
`docs/architecture/*`, specs activas y ADRs), más `mvn -B -q -DskipTests compile` y
`npx tsc --noEmit` (ambos OK). **La suite de integración no se ejecutó en esta pasada** (requiere
Docker y ~10 min); los conteos de tests se obtuvieron por análisis estático (349 `@Test`, 40 E2E,
24 unit de Vitest).
**Naturaleza:** informe de solo lectura. No se modificó código de producto.

> **Corrección de premisa.** El pedido inicial describía ClaseYa como plataforma de *reserva de
> clases en tiempo real*. Eso **no** coincide con la dirección vigente del repositorio:
> `docs/product/product-requirements.md:6-9` y `AGENTS.md` definen un **directorio + contacto
> seguro**, sin reservas, agenda, pagos ni Calendly. `BOOK-001` y `AVAIL-001` están deprecadas como
> rumbo. Este informe se evalúa contra esa dirección, y las funcionalidades propuestas (§5) la
> respetan.

---

## 1. Resumen ejecutivo

ClaseYa sigue por encima del promedio en disciplina de backend. La identidad se resuelve siempre
desde `SecurityContext` (`CurrentUser`); hay DTOs en lugar de entidades; paginación con topes y
whitelist de orden; `ApiError` sin stack traces; Flyway como única fuente del esquema con
`ddl-auto=validate`; cookies `HttpOnly` con CSRF de doble submit; rate limiting con buckets
acotados; y una suite de integración real con Testcontainers. **No encontré bypass de autorización,
inyección SQL, IDOR ni exposición pública de emails, direcciones o coordenadas.**

El problema ya no es "OWASP clásico". Es **integridad de las reglas de dominio, acoplamiento entre
módulos y los bordes de la infraestructura**:

1. **La regla de visibilidad D1 está implementada dos veces** (una `Specification` canónica y una
   consulta JPQL copiada en `FavoriteRepository`). Esta es exactamente la deriva que produjo
   `PRIV-01` y `BUG-03` en la primera revisión: se corrigió el síntoma, no la causa.
2. **`teacher` y `verification` se importan mutuamente**, igual que `teacher` y `availability`.
   Dos ciclos de paquetes que impiden aplicar ArchUnit y obligan a tocar tres módulos por cada
   cambio de visibilidad o de completitud.
3. **La carrera de conversaciones duplicadas (`BUG-01` de `review.md`) sigue abierta.** El informe
   anterior la listó como hallazgo y su tabla de seguimiento la omitió; el veredicto "no quedan
   hallazgos abiertos" es incorrecto en ese punto.
4. **Hay I/O de red dentro de transacciones de base de datos** (SMTP en el registro, ClamAV + S3 en
   la subida de evidencia). Con el pool por defecto de Hikari (10 conexiones) y un SMTP lento, el
   registro se convierte en un cuello de botella que tumba todo el API.
5. **El catálogo de materias no tiene paginación real**: `GET /api/subjects` devuelve la tabla
   completa y el SPA lo pide **dos veces** en la landing (una en `App`, otra en `LandingPage`),
   creyendo que pide 50.
6. **Las protecciones de producción dependen de variables que, si faltan, fallan en abierto**:
   `CAPTCHA_ENABLED=false` y `SCAN_ENABLED=false` por defecto, mientras la documentación afirma que
   el escaneo y el CAPTCHA están activos.

**Veredicto:** el código está listo para un MVP con tráfico bajo. Para sostener la promesa de
"directorio" (catálogo grande, SEO, contactos confiables) hay que cerrar los cinco ítems de
severidad alta y agregar dos capacidades que hoy son huecos funcionales del PRD: **notificación al
profesor cuando recibe un contacto** (§7.4 del PRD) y **opiniones** (§8).

---

## 2. Tabla de hallazgos

| ID | Severidad | Eje | Hallazgo |
|---|---|---|---|
| ARCH-01 | 🟠 HIGH | Arquitectura | Ciclos `teacher ↔ verification` y `teacher ↔ availability`; D1 duplicada en favoritos |
| RACE-01 | 🟠 HIGH | Correctitud / Datos | Creación concurrente de conversaciones duplicadas (sin constraint de par) |
| TX-01 | 🟠 HIGH | Performance / Resiliencia | SMTP, ClamAV y S3 dentro de transacciones de base |
| DATA-01 | 🟠 HIGH | Performance / Escalabilidad | `GET /api/subjects` sin paginación; doble fetch en la landing |
| CFG-01 | 🟠 HIGH | Seguridad / Operaciones | Defaults `false` para CAPTCHA y antivirus sin guard de arranque |
| DB-01 | 🟡 MEDIUM | Performance | Índices faltantes en los caminos calientes (conversaciones, mensajes, materias) |
| RATE-01 | 🟡 MEDIUM | Seguridad / Performance | Evicción O(n log n) bajo lock global al alcanzar `maxEntries` |
| PERF-01 | 🟡 MEDIUM | Performance | Una consulta de usuario por request en el filtro JWT, sin caché, más re-fetch en services |
| FE-01 | 🟡 MEDIUM | Frontend / Resiliencia | Skeleton infinito si falla el catálogo; errores silenciados |
| FE-02 | 🟡 MEDIUM | Frontend | Sin timeout, sin retry, y doble round-trip de CSRF por cada escritura |
| PERF-02 | 🟡 MEDIUM | Frontend | Abrir un hilo dispara 4-5 requests y no permite scroll-back |
| DOC-01 | 🟡 MEDIUM | Documentación | `security.md` se contradice sobre D1; README afirma un modelo `Review` que ya no existe — **corregido** |
| OPS-01 | 🟡 MEDIUM | CI / Supply chain | Sin cobertura medida, sin escaneo de dependencias Maven ni de imagen |
| TEST-01 | 🟡 MEDIUM | Testing | Sin tests de concurrencia, sin cobertura exigida, sin ArchUnit |
| A11Y-01 | 🔵 LOW | Frontend | Tarjeta clickeable no operable por teclado; sin ErrorBoundary |
| SEC-02 | 🔵 LOW | Seguridad | Enumeración por timing en registro; token de activación con doble consumo |
| PERF-03 | 🔵 LOW | Backend | Consultas redundantes en `StudentProfileService`, `TeacherProfileService` y el assembler |
| UX-02 | 🔵 LOW | Producto | La landing promete "opiniones" que no existen |

No se identificaron hallazgos **CRITICAL** (sin bypass de autenticación/autorización, sin inyección,
sin fuga masiva de datos).

---

## 3. Detalle

### 🟠 HIGH

#### ARCH-01 — Dos ciclos de paquetes y una regla de dominio duplicada

- **Ubicación:** `src/main/java/com/claseya/teacher/service/TeacherEducationService.java:15-16`
  importa `verification.repository.*`; `verification/service/VerificationDocumentService.java:14`
  importa `teacher.service.TeacherProfileService`; `teacher/service/AnnouncementCompletenessService.java:3`
  importa `availability.repository.AvailabilityWindowRepository`; `availability/service/AvailabilityService.java:22`
  importa `teacher.service.TeacherVisibilityService`; `favorite/repository/FavoriteRepository.java:23-57`.
- **Descripción:** el grafo real de dependencias es:

  ```
  teacher -> availability, verification
  availability -> teacher
  verification -> teacher
  ```

  Es decir, dos ciclos. Además, `FavoriteRepository.findVisibleByStudent` **reescribe** la regla D1
  en JPQL (nombre, bio, materia activa, modalidad, disponibilidad, usuario ACTIVE) en lugar de
  delegar en `TeacherSpecifications.visibleTeacher`. El propio comentario del método admite el
  problema: *"Keep these predicates in sync"*.
- **Impacto:** un cambio en D1 (por ejemplo, exigir `emailVerifiedAt` o agregar "nivel") exige
  editar tres módulos y dos copias de la regla. La copia de favoritos no tiene test unitario: solo la
  cubre un test de integración con dos escenarios. Ya ocurrió dos veces (`PRIV-01`, `BUG-03`). Los
  ciclos también bloquean cualquier ArchUnit futuro (`docs/architecture/guardrails.md:25-36` ya
  registra la deuda).
- **Remedio:** invertir las dependencias con puertos pequeños en el módulo que *posee* el concepto,
  y hacer que favoritos use la `Specification` canónica (§4.1 y §4.2).

#### RACE-01 — Conversaciones duplicadas bajo concurrencia (sigue abierto)

- **Ubicación:** `src/main/java/com/claseya/messaging/service/ConversationService.java:85-99`;
  `src/main/resources/db/migration/V1__create_schema.sql` (`uq_conversation_participants` es
  `(conversation_id, user_id)`).
- **Descripción:** el flujo es *leer-y-luego-crear* dentro de una transacción `READ_COMMITTED`. Dos
  POST simultáneos del mismo par estudiante↔profesor pueden no ver ninguna conversación, crear dos y
  ambas insertar participantes. No hay constraint de par, ni `pg_advisory_xact_lock`, ni
  reintento por conflicto. `ADR-011` lo reconoce como limitación aceptada, pero **no hay garantía en
  base**.
- **Impacto:** hilos divididos, mensajes duplicados en la bandeja, historial partido y datos difíciles
  de consolidar. En `review.md` figuraba como `BUG-01` y **no aparece en su tabla de seguimiento**:
  la conclusión "no quedan hallazgos abiertos" es incorrecta.
- **Remedio:** tabla de par con PK compuesta + `INSERT ... ON CONFLICT DO NOTHING` (requiere
  migración `V11` y spec, ver §4.3).

#### TX-01 — I/O de red dentro de transacciones

- **Ubicación:** `auth/service/AuthService.java:63-105` +
  `auth/service/AccountActivationService.java:59-73` (envío SMTP dentro de la transacción de
  registro); `verification/service/VerificationDocumentService.java:62-109` (ClamAV
  `scanner.scan` + S3 `storage.store` dentro de `@Transactional`).
- **Descripción:** el registro hace `saveAndFlush(user)` y a continuación `mailSender.sendActivation`
  **antes del commit**. La subida de evidencia abre la transacción, escanea por TCP (timeout 5 s),
  sube a S3 y recién entonces hace `saveAndFlush`. El pool por defecto de HikariCP es 10.
- **Impacto:** con 10 registros concurrentes y un SMTP lento (sin timeouts configurados en
  `spring.mail.properties`), **todas** las conexiones quedan tomadas esperando red: el API completo
  se degrada, no solo el registro. En uploads el efecto es idéntico con ráfagas de verificación.
  Además, el `INSERT` de `users` mantiene el lock del índice único de email durante la espera.
- **Remedio:** mover los efectos externos fuera de la transacción (§4.4). Consecuencia explícita: si
  el envío falla después del commit, hay que reintentar (outbox) en lugar de revertir la cuenta.

#### DATA-01 — `GET /api/subjects` devuelve el catálogo completo y se pide dos veces

- **Ubicación:** `academic/controller/SubjectController.java:33-35`;
  `academic/service/SubjectService.java:28-33`; `frontend/src/api.ts:401`;
  `frontend/src/App.tsx:76-78`; `frontend/src/pages/LandingPage.tsx:36`.
- **Descripción:** el endpoint acepta solo `query`; **ignora `size`**. El cliente pide
  `/api/subjects?size=50` y recibe todas las materias activas (`findByActiveTrueOrderByNameAsc`,
  sin índice sobre `active`). En la landing, `App` y `LandingPage` llaman a `api.subjects()` por
  separado: **dos descargas completas del catálogo por visita**, sin caché ni ETag.
- **Impacto:** el payload y el tiempo del primer render crecen linealmente con el catálogo. Un
  directorio real tiene decenas de miles de `career_subjects` y miles de `subjects`: se traduce en
  cientos de KB a MBs por visitante anónimo, en la ruta más caliente del producto. También impide
  usar CDN de forma efectiva.
- **Remedio:** paginar o acotar (`limit` con whitelist), cachear en el cliente y deduplicar el fetch.
  **Ojo:** cambiar el contrato de `/api/subjects` es un cambio de API y requiere aprobación; una
  alternativa sin romper contrato es un `GET /api/subjects/popular?limit=12` nuevo.

#### CFG-01 — Protecciones de producción que fallan en abierto

- **Ubicación:** `src/main/resources/application.yml` (`CAPTCHA_ENABLED:false`,
  `SCAN_ENABLED:false`, `DEMO_SEED:false`), `docs/architecture/security.md:43-45`.
- **Descripción:** el CAPTCHA (SEC-04) y el escaneo antimalware (STOR-01) están **off por defecto** y
  se activan por variable de entorno. No hay validador de arranque que exija `SCAN_ENABLED=true` y
  `CAPTCHA_ENABLED=true` fuera del perfil `local`, a diferencia de `JwtSecretValidator`, que sí
  falla rápido. La documentación afirma que el escaneo ocurre "antes de guardar".
- **Impacto:** un despliegue que olvide una variable arranca "sano", sirve evidencia sin escanear y
  acepta registros sin desafío humano. La documentación miente sobre el estado real: el fallo es
  silencioso y la detección llega tarde.
- **Remedio:** un `ProductionConfigValidator` análogo a `JwtSecretValidator` que falle al arrancar si
  el perfil activo no es `local`/`test` y `scan.enabled` o `captcha.enabled` son falsos (o que al
  menos emita `ERROR` en `/actuator/health` como indicador propio).

---

### 🟡 MEDIUM

#### DB-01 — Índices faltantes en los caminos calientes

- **Ubicación:** `V1__create_schema.sql`, `V4__messaging_support.sql`, `V5__availability_windows.sql`.
- **Descripción y evidencia:** los índices actuales cubren búsqueda de profesores, modalidades,
  disponibilidad, verificación y mensajes por `(conversation_id, created_at)`. Faltan:

  | Consulta | Filtro/orden | Índice sugerido |
  |---|---|---|
  | `ConversationService.list` (`:129`) | `ORDER BY conversation.updatedAt DESC` | `conversations (updated_at DESC, id)` |
  | `countUnreadGrouped` (`MessageRepository`) | `conversation_id IN (...) AND sender_id <> ? AND read_at IS NULL` | `messages (conversation_id, read_at) WHERE read_at IS NULL` |
  | `SubjectService.list` (`:30-31`) | `active = true ORDER BY name` | `subjects (name) WHERE active` |
  | `findVisibleByStudent` | correlaciones sobre `favorite.teacher_id` | `favorites (student_id, created_at DESC)` |

- **Impacto:** con pocos datos no se nota; con miles de conversaciones y decenas de miles de
  materias aparecen sorts en memoria y *seq scans* en la bandeja y en la landing.
- **Remedio:** `V11` con los índices, justificada con `EXPLAIN (ANALYZE, BUFFERS)` antes/después
  (el repo ya tiene ese hábito documentado en `PERF-01`).

#### RATE-01 — La evicción del limitador es O(n log n) bajo lock

- **Ubicación:** `security/ratelimit/InMemoryRateLimitStore.java:75-96`.
- **Descripción:** cuando el mapa alcanza `maxEntries` (50 000), **cada** clave nueva entra a
  `evictIfNeeded`, que copia el mapa completo (`new ArrayList<>(entries.entrySet())`), lo ordena y
  elimina el 10 %. El comentario dice que evita "un escaneo O(n) por request", pero hace exactamente
  eso, más un sort.
- **Impacto:** un atacante que rote identificadores (IPs detrás de un proxy, o emails) puede forzar
  ~50k copias + sort por request mientras el `writeLock` bloquea todas las demás inserciones. Es un
  vector de degradación de CPU/memoria en el propio mecanismo de defensa.
- **Remedio:** evicción amortizada real (muestreo aleatorio de N entradas + expulsión del más viejo),
  `Caffeine` con `maximumSize`/`expireAfterAccess`, o un `LinkedHashMap` acotado con `removeEldest`.

#### PERF-01 — El filtro JWT consulta la base en cada request

- **Ubicación:** `security/JwtAuthenticationFilter.java:50`;
  `security/AppUserDetailsService.java:31-36`; `auth/service/AuthService.java:152-157`.
- **Descripción:** cada request autenticada hace `SELECT users WHERE id = ?` para reconstruir el
  principal; luego varios services vuelven a buscar el mismo usuario o su perfil
  (`AuthService.me`, `TeacherProfileService.requireByUser`, `ConversationService.start`).
- **Impacto:** 2-3 round-trips a la base antes de la lógica de negocio, en **todas** las rutas. Es
  correcto desde seguridad (revocación inmediata) pero escala mal con tráfico.
- **Remedio:** caché de corta vida (Caffeine, 30-60 s) para `AppUserDetails` por `sub`, invalidada en
  cambio de estado/rol. **Trade-off explícito:** un usuario suspendido seguiría autenticado hasta el
  TTL; hay que documentarlo en el ADR y decidir si es aceptable. Alternativa sin caché: no volver a
  consultar el usuario en los services cuando ya está en el principal.

#### FE-01 — Skeleton infinito y errores silenciados

- **Ubicación:** `frontend/src/pages/SearchPage.tsx:129-155`; `frontend/src/App.tsx:76-78`;
  `frontend/src/pages/LandingPage.tsx:36-45`.
- **Descripción:** si `api.subjects()` falla, `subjects` queda vacío; al buscar un término,
  `runSearch` guarda `pendingTerm` y **retorna sin apagar `loading`** (`:141-143`). El efecto que
  reintenta depende de `subjects.length` (`:213-219`), que nunca cambia. Resultado: spinner
  permanente, sin mensaje ni reintento. Además `App` y `LandingPage` descartan el error con
  `.catch(() => undefined)`.
- **Impacto:** un fallo transitorio del catálogo deja la búsqueda colgada y sin salida; el usuario
  solo puede recargar. Es el peor estado posible en la pantalla principal.
- **Remedio:** estado de error explícito para el catálogo + reintento con backoff; no condicionar la
  salida del spinner a un `length` que puede no cambiar.

#### FE-02 — Cliente HTTP sin timeout, sin retry y con CSRF por escritura

- **Ubicación:** `frontend/src/api.ts:332-349`, `:375-398`.
- **Descripción:** `fetch` no usa `AbortController` ni timeout: una API colgada bloquea la UI
  indefinidamente. Cada escritura hace **primero** `GET /api/auth/csrf` (segundo round-trip),
  incluso para `login`, `register` y `activate`, que no necesitan CSRF. No hay reintento ni respeto
  de `Retry-After` ante 429/503. El comentario afirma que el servidor "rota" el token después de
  escribir; con `CookieCsrfTokenRepository` el token no rota en cada escritura, así que el motivo del
  no-cacheo no se sostiene.
- **Impacto:** latencia duplicada en cada mutación (móvil: dos RTT por click), y fallos duros ante
  redes lentas. Un 429 se muestra como error genérico sin guía.
- **Remedio:** cachear el token en memoria e invalidarlo ante 403 (re-fetch + un reintento); timeout
  de 15 s con `AbortSignal.timeout`; backoff para 429/503 respetando `Retry-After`.

#### PERF-02 — Abrir un hilo dispara 4-5 requests y no hay scroll-back

- **Ubicación:** `frontend/src/pages/MessagesPage.tsx:92-107`.
- **Descripción:** `api.conversation` + `api.messages(0,100)` + eventualmente
  `api.messages(lastPage,100)` + `markConversationRead` + `loadConversations(true)`. La UI carga
  **solo la última página de 100 mensajes** y no ofrece cargar anteriores.
- **Impacto:** en conversaciones largas se pierde historial visible y se paga varias veces el costo
  de la misma pantalla.
- **Remedio:** endpoint de hilo que devuelva participantes + última página en un solo DTO, y
  "cargar anteriores" con `before=<messageId>` (keyset) en lugar de offset.

#### DOC-01 — El repositorio se contradice a sí mismo

- **Ubicación y evidencia:**
  - `docs/architecture/security.md:100-102` dice que la visibilidad pública exige
    `verificationStatus = VERIFIED` **y** `user.status = ACTIVE`; la línea 79 del mismo archivo y
    `TeacherSpecifications.java:41-56` dicen **ACTIVE + anuncio publicado** (la verificación solo
    agrega confianza). Contradicción dentro de la misma página.
  - `README.md:115-116` y `:652` afirman que el modelo `Review` y los agregados existen; `ADR-024` y
    `src/main/java/com/claseya/model/` confirman que `Review`, `Booking`, `CalendlyIntegration`,
    `Report` y `Notification` fueron eliminados.
  - `docs/testing/testing-strategy.md:11` declara **301 tests**; el árbol tiene **324 métodos de
    test** (conteo estático de anotaciones `@Test`/`@ParameterizedTest`; un conteo ingenuo de la
    cadena `@Test` da 349 porque también matchea emails de prueba como `...@test.com`).
  - `docs/architecture/security.md:147-151` lista "verificación de email/activación" y "social
    login" como FUTURO; ambos están implementados (`AUTH-001`, `OAUTH-001`).
  - **Contradicción de alcance:** el PRD §7 (pasos 4-5) exige notificar al profesor por email cuando
    recibe un contacto, pero `AGENTS.md` enumera "notificaciones" como fuera de alcance hasta que
    exista spec, `ADR-024` las descarta como rumbo y `ConversationService.start` no dispara ningún
    aviso. Las tres fuentes normativas dicen cosas distintas sobre la misma capacidad.
- **Impacto:** en un repo que se declara "única fuente de verdad", esto es deuda activa: cualquier
  agente que lea `security.md` implementará una regla incorrecta. Es el tipo de documento que se usa
  para decidir.
- **Remedio:** corregir los puntos anteriores y agregar al DoD un chequeo de "reglas de visibilidad y
  flujos de auth citados en docs == código".
- **Estado (29/10/2026):** corregido en el working tree — `docs/architecture/security.md`,
  `README.md`, `docs/testing/testing-strategy.md`, `docs/product/domain.md`,
  `docs/teacher-search.md`, `docs/academic-profiles.md` y un comentario del demo seed. La
  contradicción PRD §7 vs `AGENTS.md`/`ADR-024` sobre notificar contactos **queda abierta**: es una
  decisión de producto, no un error de redacción, y no se resolvió en silencio.

#### OPS-01 — CI sólido, pero sin cobertura ni escaneo de dependencias Maven

- **Ubicación:** `.github/workflows/ci.yml:15-122`; `pom.xml` (sin JaCoCo, SpotBugs, Checkstyle ni
  `dependency-check`).
- **Descripción:** el pipeline corre backend (`mvn clean verify`), lint + tests + build + `npm audit`
  y E2E con Postgres. Falta: cobertura con umbral, escaneo de vulnerabilidades para Maven
  (`dependency-review` no sustituye un análisis del árbol Maven), CodeQL y escaneo de la imagen
  (Trivy/Grype). Tampoco hay branch protection verificable en el repo.
- **Impacto:** una CVE en Spring/Tomcat/Jackson/AWS SDK no genera señal hasta que alguien mira. La
  cobertura puede caer sin que nadie lo note: los 349 tests no dicen qué porcentaje del dominio
  cubren.
- **Remedio:** JaCoCo con `check` (piso inicial realista, p. ej. 60 % líneas y 70 % en `service/`),
  `dependency-check` o `osv-scanner` programado, CodeQL para Java/TS, Trivy sobre la imagen.

#### TEST-01 — Faltan las pruebas que atraparían los hallazgos de este informe

- **Ubicación:** `src/test/java` (349 `@Test`), `frontend/src/**/*.test.ts*` (24), `frontend/tests`
  (40 E2E).
- **Descripción:** hay excelente cobertura funcional (IDOR, roles, visibilidad, archivos, rate
  limit), pero no existen:
  - test de **concurrencia** para `ConversationService.start` (dos hilos, mismo par);
  - test unitario de la **regla D1 en favoritos** (solo integración, y sobre una copia de la regla);
  - tests de **contrato** del API (OpenAPI/JSON schema) que detecten cambios de payload;
  - tests de la **capa HTTP del frontend** (`api.ts`: CSRF, 401, timeout) — hoy nada la cubre;
  - ArchUnit que congele la dirección de dependencias (y evite nuevos ciclos);
  - presupuesto de rendimiento (k6/Gatling) sobre búsqueda y bandeja.
- **Impacto:** los hallazgos ARCH-01, RACE-01 y FE-02 son indetectables con la suite actual. El E2E
  corre con `workers: 1` y estado compartido: es lento y frágil por diseño (`playwright.config.ts`).

---

### 🔵 LOW

#### A11Y-01 — Tarjeta clickeable y ausencia de ErrorBoundary

- `frontend/src/components/ui/primitives.tsx` (`Card` con `interactive`) y
  `components/teacher/TeacherCard.tsx:22-27`: es un `div` con `onClick`, sin `role`, `tabIndex` ni
  manejo de `Enter`/`Space`. El botón "Ver perfil" salva la operabilidad, pero el área clickeable no
  es accesible. Además `frontend/src/main.tsx` no tiene `ErrorBoundary`: un error de render deja la
  pantalla en blanco sin recuperación.

#### SEC-02 — Enumeración por timing y doble consumo del token

- `auth/service/AuthService.java:78-104`: para un email inexistente se ejecuta BCrypt (~100 ms) y
  para uno existente se retorna antes; la diferencia es medible si el CAPTCHA está apagado
  (ver CFG-01). El mensaje es genérico, el tiempo no.
- `auth/service/AccountActivationService.java:78-109`: dos activaciones concurrentes del mismo token
  pueden pasar el chequeo `usedAt == null`; el efecto es idempotente, pero conviene un
  `UPDATE ... WHERE used_at IS NULL` con conteo de filas para que sea estrictamente de un solo uso.

#### PERF-03 — Consultas redundantes en services

- `student/service/StudentProfileService.java:84-90` hace `findByIdAndActiveTrue(careerId)` y luego
  `existsByIdAndAcademicUnit_University_Id(...)`: la segunda consulta se evita comparando la unidad
  ya cargada.
- `teacher/service/TeacherProfileService.java:69-75` hace `findByUser_Id` y después
  `findById(userId)`: el `User` ya está en el principal.
- `teacher/service/TeacherSummaryAssembler.java:52-56` vuelve a cargar los perfiles con
  `findWithUserByIds` aunque la página ya los trajo; se resuelve con `@EntityGraph(attributePaths="user")`
  en la consulta de búsqueda.

#### UX-02 — La landing promete opiniones inexistentes

`frontend/src/pages/LandingPage.tsx:20` ("Mirá el perfil, las opiniones y el precio") y el beneficio
"Sin vueltas" hablan de opiniones; `formatRating` muestra "Sin opiniones" siempre porque no hay
lógica de creación. Es una promesa de producto que hoy no se puede cumplir (§5.3 la resuelve).

---

## 4. Refactorizaciones de alto impacto

Ordenadas por relación beneficio/riesgo. **Ninguna se implementó**; cada una requiere su nivel de
cambio (Nivel 1 con test de regresión, o Nivel 2 con spec).

### 4.1 Una sola definición de D1 (elimina la clase de bug de `PRIV-01`)

Hacer que `FavoriteRepository` deje de tener su propia copia y consuma la regla canónica:

```java
// favorite/repository/FavoriteRepository.java
public interface FavoriteRepository extends JpaRepository<Favorite, UUID>,
        JpaSpecificationExecutor<Favorite> {
    Optional<Favorite> findByStudent_IdAndTeacher_Id(UUID studentId, UUID teacherId);
}

// favorite/service/FavoriteService.java
private Specification<Favorite> visibleFavorites(UUID studentId) {
    return (root, query, cb) -> {
        Path<TeacherProfile> teacher = root.get("teacher");
        return cb.and(
                cb.equal(root.get("student").get("id"), studentId),
                TeacherSpecifications.visibleTeacher(teacher, query, cb));
    };
}
```

Beneficio: un cambio de D1 se hace en un solo lugar y favoritos lo hereda. Riesgo: la consulta pasa
de JPQL escrita a mano a Criteria; hay que conservar el test de integración de los dos escenarios
(profesor suspendido / anuncio despublicado) y agregar el unitario de la especificación.

### 4.2 Romper los ciclos con puertos

El módulo que **posee** el concepto define la interfaz; el otro la implementa.

```java
// teacher/service/EducationEvidencePolicy.java  (puerto del módulo teacher)
public interface EducationEvidencePolicy {
    boolean hasEvidence(UUID educationId);
    boolean hasDecisions(UUID educationId);
}

// verification/service/VerificationEvidencePolicy.java  (adaptador)
@Service
class VerificationEvidencePolicy implements EducationEvidencePolicy { /* ... */ }
```

Para `teacher ↔ availability`, lo más barato es **mover** `AnnouncementCompletenessService` a
`availability` (o exponer `AvailabilityPresencePort.existsByTeacher(teacherId)` desde
`availability`), porque la completitud es una vista del anuncio y la disponibilidad es una de sus
piezas. Beneficio: habilita ArchUnit y hace explícitas las aristas. Riesgo: bajo, son cambios de
paquete con tests existentes.

### 4.3 Conversaciones idempotentes (requiere migración y spec)

```sql
-- V11__conversation_pairs.sql  (justificación: garantía de unicidad del par bajo concurrencia)
CREATE TABLE conversation_pairs (
    student_user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    teacher_user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    conversation_id uuid NOT NULL UNIQUE REFERENCES conversations (id) ON DELETE CASCADE,
    PRIMARY KEY (student_user_id, teacher_user_id),
    CONSTRAINT chk_conversation_pairs_distinct CHECK (student_user_id <> teacher_user_id)
);
```

Con `INSERT ... ON CONFLICT DO NOTHING` y relectura del ganador, la carrera desaparece a nivel de
base. Alternativa sin tabla nueva: `pg_advisory_xact_lock(hashtext(par))` al inicio de `start`.
Trade-off: la tabla agrega escritura y migración; el advisory lock no deja rastro auditable y
serializa por hash.

### 4.4 Efectos externos fuera de la transacción (outbox)

```java
// Registro: commit primero, enviar después, reintentar si falla.
@Transactional
public void register(RegisterRequest request) {
    // ... crear usuario PENDING y token ...
    outbox.enqueue(ActivationEmail.of(user.getId(), token.getId()));
}

@Scheduled(fixedDelay = 30_000)
public void deliver() { /* toma pendientes con FOR UPDATE SKIP LOCKED, envía, marca */ }
```

Beneficio: el pool de conexiones deja de depender de SMTP/S3/ClamAV; los fallos de envío se
reintentan con backoff y quedan observables. Costo: una tabla `outbox`, un worker y una spec
(Nivel 2). Para la subida de evidencia, el mínimo es escanear y subir **antes** de abrir la
transacción y guardar solo el resultado.

### 4.5 Partir el cliente HTTP del frontend

`frontend/src/api.ts` tiene 523 líneas y mezcla tipos, transporte y 50 endpoints. Extraer:

```
src/api/http.ts       // fetch, timeout, CSRF (cache + re-fetch en 403), retry 429/503
src/api/auth.ts
src/api/teachers.ts
src/api/messaging.ts
src/api/verification.ts
```

Beneficio: el transporte se testea una vez (Vitest, sin DOM con `fetch` mockeado) y desaparece la
duplicación entre `request` y `requestForm`.

### 4.6 Caché de catálogo en el cliente

Un `useCatalog()` a nivel de provider (o React Query) que cargue `subjects`, `universities` y
`careerSubjects` una vez, con `staleTime` y reintento, elimina el doble fetch de la landing y el
estado de error colgado (`FE-01`).

### 4.7 Guard de configuración de producción

```java
@Component
class ProductionConfigValidator {
    ProductionConfigValidator(Environment env) {
        if (Arrays.asList(env.getActiveProfiles()).contains("local")) return;
        require(env, "app.storage.scan.enabled", "ClamAV must be enabled outside local");
        require(env, "app.auth.captcha.enabled", "Turnstile must be enabled outside local");
    }
}
```

### 4.8 Estrategia de testing propuesta

| Capa | Objetivo | Herramienta | Qué agregar |
|---|---|---|---|
| Unit | reglas puras | JUnit 5 + AssertJ | `FavoriteVisibilitySpecificationTest`, `ConversationStartConcurrencyTest` (con `CountDownLatch`) |
| Arquitectura | dirección de dependencias | ArchUnit | reglas de `guardrails.md` + "no cycles" |
| Integración | contratos HTTP y DB | Testcontainers | concurrencia real de conversaciones, doble activación, carrera de favoritos |
| Contrato | payloads | `springdoc` + JSON schema o Pact | detectar cambios de DTO sin spec |
| Frontend unit | transporte y lógica | Vitest + MSW | `http.ts` (CSRF, 401, timeout), `useCatalog` |
| E2E | flujos críticos | Playwright | login, contacto, favoritos, verificación; bajar a ~15 escenarios y paralelizar por usuario sembrado |
| Carga | búsqueda y bandeja | k6 | p95 de `/api/teachers` y `/api/conversations` con 5k profesores |

Regla de oro: **cada hallazgo de este informe entra con su test de regresión antes del fix**.

---

## 5. Funcionalidades propuestas (5)

Todas respetan la dirección "directorio + contacto seguro" y **excluyen** reservas, agenda, pagos y
Calendly (`product-requirements.md:10, 117-124`). Cada una es Nivel 2: requiere spec aprobada,
change budget y ADR si toca decisiones existentes.

> **Nota de alcance:** opiniones (§5.3) y notificaciones (§5.4) figuran hoy en `AGENTS.md` como
> "fuera de alcance hasta que exista spec", y `ADR-024` descarta los modelos legacy de reviews y
> notifications. Por eso estas propuestas **no son implementables sin una spec aprobada y un ADR que
> actualice `ADR-024`**; el PRD §8 y §7 las exigen, así que la decisión pendiente es de producto, no
> de código. §5.1, §5.2 y §5.5 no chocan con ninguna decisión vigente.

### 5.1 Directorio indexable (SEO + prerender)

- **Descripción:** rutas públicas con HTML renderizado en el servidor para perfiles y búsquedas,
  `sitemap.xml` generado desde profesores publicados, `canonical`, Open Graph y JSON-LD (`Person`,
  `Service`, `AggregateRating` cuando existan opiniones). `robots.txt` ya existe; falta el sitemap.
- **Valor:** es el canal de crecimiento natural de un directorio. Hoy la SPA entrega metadatos solo
  en el cliente (`usePageMeta`), sin HTML previo: el indexado de perfiles y búsquedas queda a merced
  del render de JavaScript de cada crawler y de los previews sociales. Sin esto, el catálogo no
  compone.
- **Stack:** React Router 7 en *framework mode* (o prerender con `vite-plugin-ssr`/`react-snap`
  para las rutas públicas), endpoint `GET /sitemap.xml` en el backend alimentado por una vista de
  profesores visibles, CDN con `stale-while-revalidate`. Costo: alto (cambia el modelo de
  despliegue), beneficio: alto.

### 5.2 Ranking y búsqueda difusa por materia

- **Descripción:** búsqueda full-text sobre `bio` + materias + institución (`tsvector`), tolerancia a
  errores tipográficos en el autocompletado (`pg_trgm`) y un score de relevancia que combine
  coincidencia, rating, completitud del anuncio, verificaciones y distancia.
- **Valor:** hoy el término debe coincidir **exactamente** con el nombre de la materia
  (`SearchPage.tsx:124-127`); "matematica" sin tilde o "profe de mate" no encuentra nada. Mejor
  matching = más contactos sobre el mismo catálogo.
- **Stack:** Postgres puro, sin Elasticsearch: columna generada `search_vector`, índice GIN,
  `websearch_to_tsquery`, `similarity()` con `pg_trgm`, y orden por `ts_rank` + señales de negocio.
  Encaja con `ADR-006` (búsqueda en PostgreSQL). Costo: medio.

### 5.3 Opiniones verificadas

- **Descripción:** opinión habilitada solo para un estudiante con conversación real con ese profesor
  (y confirmación de la clase), una por par, respuesta del profesor, moderación y recálculo
  transaccional de `ratingAverage`/`ratingCount`.
- **Valor:** el PRD §8 lo define y la UI ya muestra "Sin opiniones" en cada tarjeta. Es el
  ingrediente de confianza que falta para convertir; además habilita `AggregateRating` en el SEO.
- **Stack:** tablas `reviews` (nueva migración), regla de elegibilidad en un `ReviewEligibilityService`,
  moderación en el panel admin existente, recálculo por `UPDATE ... FROM` agregado dentro de la
  misma transacción, tests de "una por par" y de elegibilidad. Costo: medio.

### 5.4 Notificación de contactos por email (outbox + deep link firmado)

- **Descripción:** cuando entra un contacto, el profesor recibe un correo del sistema con un enlace
  firmado de un solo uso que abre la conversación; reintentos, supresión de duplicados y preferencia
  de frecuencia.
- **Valor:** **es el hueco más grave del flujo núcleo.** Hoy el profesor solo se entera si entra a la
  app: el contacto puede quedar sin respuesta y el estudiante concluye que la plataforma no funciona.
  El PRD lo exige explícitamente (`product-requirements.md:85-88`).
- **Stack:** patrón outbox sobre la tabla de mensajes/eventos, worker con `FOR UPDATE SKIP LOCKED`,
  proveedor transaccional (Resend/SES/Postmark), tokens de deep link con TTL corto y un solo uso,
  plantillas versionadas, métricas de entrega/rebote. Reutiliza `ActivationMailSender` como molde.
  Costo: medio; depende de §4.4.

### 5.5 Contacto seguro activo (detección de PII + reportes)

- **Descripción:** análisis de cada mensaje para detectar emails, teléfonos, handles y direcciones;
  advertencia o bloqueo configurable antes de enviar, cola de reportes para moderación y política de
  strikes. Incluye reporte de profesores y de mensajes.
- **Valor:** la promesa del producto es "nunca se comparten datos de contacto" y hay menores
  involucrados (`product-requirements.md:98-99`). Hoy nada impide que el primer mensaje sea
  "escribime a mi WhatsApp". Sin esto, el contacto "seguro" es solo una intención.
- **Stack:** normalizador + regex de PII (teléfonos AR/E.164, emails, URLs, @handles) en el
  `MessageService` antes de persistir, resultado `ALLOW/WARN/BLOCK` con métricas; tablas `reports`
  (el diseño legacy sirve de base) y cola admin; tests con corpus de casos reales. Costo: bajo-medio.

**Descartadas a propósito:** reservas/agenda, pagos, videollamada y chat en tiempo real (WebSockets)
siguen fuera de alcance por PRD §10 y `AGENTS.md`.

---

## 6. Plan priorizado

### Ahora (antes de crecer el catálogo)

1. **DOC-01** — corregir las contradicciones: es barato y evita que el próximo agente implemente D1
   mal.
2. **CFG-01** — guard de arranque para CAPTCHA y antivirus.
3. **RACE-01** — spec + `V11` + test de concurrencia.
4. **DATA-01** — paginar/acotar materias y deduplicar el fetch (contrato: aprobar antes).
5. **TX-01** — sacar SMTP del registro (mínimo: timeouts + envío post-commit).

### Siguiente (deuda estructural)

6. **ARCH-01** — romper ciclos + D1 única + ArchUnit.
7. **DB-01** — índices con medición.
8. **FE-01/FE-02** — resiliencia del cliente HTTP y del catálogo.
9. **OPS-01** — cobertura, escaneo de dependencias e imagen.

### Después (producto)

10. **5.1 SEO/prerender** y **5.2 búsqueda difusa** (crecimiento).
11. **5.4 notificación de contactos** (cierra el loop núcleo).
12. **5.3 opiniones** y **5.5 contacto seguro activo** (confianza).

---

## 7. Verificación realizada

| Comprobación | Resultado |
|---|---|
| `mvn -B -q -DskipTests compile` | ✅ OK |
| `npx tsc --noEmit` (frontend) | ✅ OK |
| `mvn -B clean verify` (post-remediación) | ✅ 333 tests, 0 failures; JaCoCo 88,7 % líneas / 73,9 % ramas |
| `npm test` (Vitest) | ✅ 32 tests |
| `npm run lint` / `npm run build` | ✅ OK (CSP check incluido) |
| Playwright E2E | ✅ 40/40 (una corrida previa tuvo 1 flake en `messages`, verde al repetir) |
| Análisis estático de dependencias entre módulos | ✅ Realizado (ver ARCH-01) |
| Conteo de tests | 333 métodos backend · 40 E2E · 32 Vitest |

### Fortalezas a preservar

- **Identidad y ownership:** `CurrentUser` en todos los flujos; ningún DTO de request acepta
  `userId`/`studentId`/`senderId`; conversaciones y documentos ajenos responden 404.
- **Seguridad de sesión:** cookie `HttpOnly` + CSRF de doble submit, claims mínimos (`sub`, `jti`),
  `JwtSecretValidator`, CORS con allowlist normalizada, headers y CSP con verificación en build.
- **Contratos:** DTOs record, Bean Validation, `ApiError` uniforme, sin stack traces, magic bytes +
  SHA-256 + nombre saneado + `attachment` en descargas.
- **Base de datos:** Flyway como única fuente, `ddl-auto=validate`, exclusion constraint para
  solapamiento de disponibilidad, FK compuesta que impide que un no-participante firme un mensaje.
- **Operación:** health público sin detalles, métricas ADMIN-only, `X-Request-Id` validado, imagen
  no-root con `HEALTHCHECK`, CI con tres jobs y dependency review.
- **Disciplina documental:** PRD, specs, ADRs y guías de arquitectura muy por encima de la media;
  el problema es la desincronización puntual, no la falta de documentación.

---

## 8. Estado de remediación (29/10/2026)

| ID | Estado | Evidencia |
|---|---|---|
| DOC-01 | ✅ Corregido | `a3af429` — visibilidad D1, modelo `Review`, conteos y docs de fase |
| CFG-01 | ✅ Corregido | `ab6fead` — `ProductionConfigValidator` + 7 tests; tests bajo perfil `test` |
| ARCH-01 | ✅ Corregido | `bce1153` — favoritos usa `TeacherSpecifications`; **ADR-027 implementado (2026-11-02)**: puertos `AvailabilityPresencePort`/`EducationEvidencePort` y `ArchitectureTest` sin ciclos |
| DB-01 | ✅ Corregido | `ded275e` — V11 con 2 índices justificados (se descartaron 2 por no justificarse) + test de esquema |
| TX-01 | ✅ Corregido | `36d212c` timeouts; **REVIEW2-002 implementado**: outbox `V13`/`V14`, worker con lease/backoff/métricas, activación por outbox (`ADR-029`), ClamAV y S3 fuera de la transacción con re-chequeo de cupo bajo lock. 343 tests backend |
| RACE-01 | ✅ Corregido | `4b04a92` — V12 `conversation_pairs`, `INSERT ... ON CONFLICT`, test de concurrencia |
| FE-01 / FE-02 | ✅ Corregido | `useCatalog`, error con reintento, timeout, CSRF cacheado, retry en 403, `Retry-After` |
| UX-02 / A11Y-01 | ✅ Corregido | copy de la landing; `ErrorBoundary`; `TeacherCard` con link estirable focusable (mouse y teclado) sin romper favoritos ni “Ver perfil” |
| OPS-01 | ✅ Corregido | JaCoCo 85/70, CodeQL, dependency submission y Trivy |
| DATA-01 | ✅ Corregido | **REVIEW2-001 implementada**: `GET /api/subjects` paginado (+`/popular`, `Cache-Control`), landing con 12 materias y autocompletado server-side; 8 tests nuevos |
| PERF-01 | ✅ Corregido | **ADR-028 opción A**: `AuthService.me` usa el principal, sin caché; `UserLookupBudgetIntegrationTest` mide 100 requests → 100 cargas de `User` y 100 statements (una por request, sin re-consultas de service) |
| RATE-01 | ✅ Corregido | evicción aproximada por lote bajo lock, sin copia ni sort del mapa; cubierto por `InMemoryRateLimitStoreTest` |
| PERF-02 | ✅ Corregido | **REVIEW2-003 implementada**: `GET /conversations/{id}/thread` con keyset `before`/`hasMore`/`nextBefore`, `MessagesPage` con “Cargar mensajes anteriores” y 6 tests de integración |
| SEC-02 | ✅ Corregido | `EmailVerificationTokenRepository.consume` (`UPDATE ... WHERE used_at IS NULL`) + `ActivationConcurrencyIntegrationTest` |
| PERF-03 | ✅ Corregido | `@EntityGraph` en career/teacher/favorite; `TeacherSummaryAssembler` ya no re-consulta los perfiles que recibe |
| TEST-01 | 🟡 Parcial | `ApiContractIntegrationTest` congela los DTOs principales; E2E 44/44 estable; quedan pendientes los tests de carga (k6) |

> **03/11/2026:** `REVIEW2-001`, `REVIEW2-002` y `REVIEW2-003` están implementadas. `ARCH-01`,
> `RATE-01`, `SEC-02`, `PERF-01`, `PERF-02` y `PERF-03` quedaron cerrados en código; `TEST-01`
> ya tiene tests de contrato y mantiene pendientes solo los de carga (k6). Verificación de esta
> pasada: backend **432 tests** (`mvn -B clean test`), Vitest **56/56**, `tsc`, ESLint y build con
> CSP check en verde. Playwright **44/44**: se aislaron los datos demo, se corrigió el label de
> subida (PDF) y se deduplicó el fetch de CSRF para que dos escrituras concurrentes no se pisen.

