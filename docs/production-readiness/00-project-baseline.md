# 00 — Project baseline y auditoría de arquitectura — ClaseYa

- **Fecha:** 2026-11-17
- **Rama inspeccionada:** `main` (`52cac65`). `main` está 38 commits por delante de `dev` (`875f278`); `dev` no tiene commits que `main` no tenga (`git rev-list --left-right --count main...dev` → `38 0`).
- **Modo:** solo lectura. No se modificó código, configuración ni dependencias. No se ejecutaron migraciones, despliegues ni la suite de tests.
- **Método:** lectura de código y documentos, índice CodeGraph, `grep`, `npm outdated` (consulta al registro), `git`. Cada hallazgo indica su evidencia y una clasificación:
  - **VERIFIED**: comprobado en el código o en un comando ejecutado.
  - **PROBABLE**: hay indicios suficientes, falta confirmación.
  - **PENDING**: requiere acceso o información que no está en el repositorio.

El contexto del encargo se contrastó con el código: sigue vigente (Spring Boot + React/Vite + PostgreSQL, JWT + Google OAuth, monolito modular, sin reservas ni pagos). Dos matices: el JWT viaja en una cookie `HttpOnly`, no en JavaScript, y existen módulos que el encargo no menciona (`review`, `safety`, `notification`).

---

## 1. Resumen ejecutivo

ClaseYa es un producto coherente y con disciplina de ingeniería superior a la media de un MVP: identidad siempre desde `SecurityContext`, DTOs en lugar de entidades, Flyway como único dueño del schema, rate limiting, CSRF por cookie, validadores de arranque que impiden desplegar con configuración insegura, y una suite amplia de tests (528 `@Test` en el backend, 30 archivos Vitest, 16 specs Playwright, cobertura JaCoCo exigida en `verify` al 85 % de líneas y 70 % de ramas). No se encontró ningún bypass de autorización, inyección SQL ni exposición de secretos en archivos versionados.

Los riesgos reales no están en el "OWASP clásico" sino en tres zonas:

1. **Operación y entrega de correo.** El registro por email depende de Brevo y de un remitente verificado. Un fallo permanente deja la fila de outbox en `DEAD` sin reintento, y el usuario queda `PENDING` sin una señal visible para él (ver R-02).
2. **Brechas entre el código y la realidad desplegada.** Varios controles de producción dependen de variables de entorno que no se pueden verificar desde el repositorio (ClamAV, proxy confiable para el rate limit, HTTPS, CAPTCHA).
3. **Gobierno del repositorio y de la documentación.** `main` va por delante de `dev` contra la regla de `AGENTS.md`, y hay deriva documental (workflow `codeql.yml` citado pero inexistente, versiones de migraciones desactualizadas, una guía que no existe).

**Veredicto:** apto para un lanzamiento controlado (beta cerrada) una vez resueltos los puntos P1. No es apto para tráfico abierto hasta confirmar los puntos PENDING de seguridad y operación (§9).

---

## 2. Arquitectura real

### 2.1 Forma

- Monolito modular Java 17 / Spring Boot 3.5.16 (`pom.xml`, parent), un solo artefacto (`clase-ya-backend-0.0.1-SNAPSHOT.jar`), un solo `ApplicationContext`, una sola base de datos. **VERIFIED** (`pom.xml`, `Dockerfile:25`).
- Capas por módulo: `controller` → `service` → `repository` → PostgreSQL; `dto` con records. **VERIFIED** (estructura `src/main/java/com/claseya/*/{controller,service,repository,dto}`).
- SPA React independiente (`frontend/`), desplegada en Vercel con proxy `/api` hacia el backend en Render (`frontend/vercel.json`). Mismo origen para el navegador (ADR-022). **VERIFIED**.
- Base de datos PostgreSQL (Neon según `docs/audits/…`, **PENDING** de confirmar en el entorno real).

### 2.2 Módulos (`src/main/java/com/claseya`)

| Módulo | Responsabilidad | Archivos clave |
|---|---|---|
| `model` (+`enums`) | 27 entidades JPA, 17 enums. Hoja de dependencias. | `User`, `TeacherProfile`, `Conversation`, `Review`, `VerificationDocument` |
| `common` | `ApiError`, `GlobalExceptionHandler`, `SearchResultPage`, utilidades. Hoja. | `common/exception/*` |
| `security` | Cadena de filtros, JWT, CSRF, rate limiting, validadores de arranque. | `SecurityConfig`, `JwtService`, `JwtAuthenticationFilter`, `ProductionConfigValidator`, `DemoDataProductionGuard` |
| `auth` | Registro, login, activación, reset de contraseña, CAPTCHA, `human-check`, directorio admin. | `AuthService`, `AccountActivationService`, `HumanCheckService` |
| `oauth` | Login con Google (ID token). | `GoogleAuthService` |
| `user` | `UserRepository`. | — |
| `academic` | CRUD del catálogo (University, AcademicUnit, Career, Subject, CareerSubject). | `*Controller` |
| `student` / `teacher` | Perfiles, formación, materias, modalidades, anuncio, búsqueda y visibilidad D1. | `TeacherSearchService`, `TeacherVisibilityService`, `TeacherSpecifications` |
| `availability` | Disponibilidad declarada (no es agenda). | `AvailabilityController` |
| `favorite`, `messaging`, `review`, `safety` | Favoritos, conversaciones y mensajes, opiniones, reportes y bloqueos. | `ConversationService` |
| `notification` | Outbox transaccional, worker, SMTP/Brevo. | `NotificationOutboxWorker`, `BrevoEmailSender` |
| `verification` | Evidencia PDF, escaneo, almacenamiento, decisiones admin. | `VerificationDocumentService`, `DocumentContentValidator`, `ClamAvDocumentScanner` |
| `bootstrap` | `DemoDataSeeder`. | — |

### 2.3 Límites y dirección de dependencias

- `model` y `common` son hojas. **VERIFIED** (`docs/architecture/architecture.md`).
- `ArchitectureTest` solo vigila ciclos de paquetes y que `teacher` no dependa de `availability` ni `verification`. Las reglas 3 a 7 de `docs/architecture/guardrails.md` son propuestas, no se aplican. **VERIFIED** (`docs/architecture/guardrails.md`, `src/test/java/com/claseya/ArchitectureTest.java`).
- Cruces entre módulos a nivel de repositorio (por ejemplo `availability`, `verification`, `student` y `teacher` consultan repositorios de otros módulos). Es deuda reconocida en la documentación. **VERIFIED**.

### 2.4 Frontend

- Rutas con `react-router`, páginas cargadas con `lazy` (`frontend/src/App.tsx:17-33`). Sesión con Context API sobre cookie y `/api/auth/me`. Cliente HTTP único `frontend/src/api.ts` (889 líneas).
- Prerender SEO solo para `/`, `/buscar` y `/profesores/:id` con `frontend/middleware.ts` + `frontend/api/seo.ts`. No hay SSR con hidratación (ADR-034). **VERIFIED**.

---

## 3. Tecnologías y versiones

| Capa | Tecnología | Versión | Evidencia |
|---|---|---|---|
| Lenguaje | Java | 17 | `pom.xml:21` |
| Framework | Spring Boot | 3.5.16 | `pom.xml` (parent) |
| JWT | jjwt | 0.12.6 | `pom.xml:22` |
| Rate limit | bucket4j | 8.21.0 | `pom.xml:23` |
| Tests | Testcontainers | 1.20.4 | `pom.xml:28` |
| Tests | ArchUnit | 1.3.0 | `pom.xml` |
| Cobertura | JaCoCo | 0.8.12 (piso 0,85 / 0,70) | `pom.xml:235-273` |
| Storage | AWS SDK S3 | 2.31.78 | `pom.xml` |
| BD | PostgreSQL / Flyway | 16 / V1..V25 | `docker-compose.yml`, `db/migration` |
| Frontend | React / TypeScript / Vite | 18.3.1 / 5.9.3 (instalado) / 8.3.3 | `frontend/package.json`, `npm outdated` |
| Frontend | Tailwind | 4.3.3 | `npm outdated` |
| Frontend | Vitest / Playwright | 5.0.5 / 1.63.0 | `npm outdated` |
| Contenedor | `eclipse-temurin:17-jre`, usuario no root, `HEALTHCHECK` | — | `Dockerfile:15-45` |

---

## 4. Estructura de módulos y del repositorio

- 712 archivos versionados. Backend: ~15,8 k líneas en `src/main`, 88 archivos de test en `src/test`. Frontend: `frontend/src` con `pages`, `components`, `hooks`, `lib`, `seo`.
- Documentación en `docs/`: 41 specs activas, 33 ADRs, PRD, `DESIGN.md`, guardrails, estrategia de tests y 4 auditorías previas (`docs/audits/`, 2026-11-13).
- CI en `.github/workflows/`: `ci.yml` (backend `mvn clean verify`, frontend lint/test/build/audit, E2E con Postgres y seed, dependency review), `container-scan.yml`, `dependency-submission.yml`. Dependabot para Maven y npm (`.github/dependabot.yml`).

---

## 5. Flujos críticos

### 5.1 Registro por email

`POST /api/auth/register` → `AuthController.register` (`auth/controller/AuthController.java:58`) → `AuthService.register` (`auth/service/AuthService.java:62-106`).

1. Rechaza `ADMIN` (`:66`).
2. Verifica el CAPTCHA antes de buscar la cuenta (`:72`).
3. Si el email existe y está `PENDING`, reenvía el enlace; si no, no cambia nada. Siempre responde `202` (anti-enumeración, `:77-82`).
4. Crea `User` `PENDING` con BCrypt (`:84-91`) y encola el correo `ACTIVATION` en la misma transacción (`:105`). **VERIFIED**.

La entrega ocurre después del commit por `NotificationOutboxWorker` (reintentos con backoff, lease, `PermanentNotificationException` → `DEAD`). Con 400/401/403 de Brevo la fila queda `DEAD` y no se reintenta (`notification/service/BrevoEmailSender.java:82-85`). **VERIFIED**.

### 5.2 Activación

`POST /api/auth/activate` → `AccountActivationService.activate`: token de un solo uso con hash SHA-256 y TTL 24 h. Reenvío con `POST /api/auth/resend-activation` (202 genérico). **VERIFIED** (`AuthController.java:72-79`; `docs/architecture/security.md`).

### 5.3 Login y sesión

`POST /api/auth/login` (`AuthController.java:64`): bloqueo por cuenta antes de BCrypt (`AuthService.java:126`), mensaje genérico (`:137`), JWT HS256 con `sub`, `jti`, `ver`, `iat`, `exp` (`security/JwtService.java:40-50`) en cookie `HttpOnly`. El filtro recarga el usuario en cada request, exige `ACTIVE` y compara `ver` con `users.session_version`. CSRF con doble submit para requests por cookie (`SecurityConfig.java:124-139`). **VERIFIED**.

### 5.4 Google OAuth

`GoogleAuthService.loginWithGoogle` (`oauth/service/GoogleAuthService.java:44-72`): valida el ID token, busca por `google_sub` y luego por email. Solo vincula cuentas `ACTIVE` (`:61`) y rechaza `ADMIN` (`:81`). Un usuario nuevo nace `ACTIVE` con rol elegido por el cliente (`STUDENT` o `TEACHER`) (`:77-90`). **VERIFIED**.

### 5.5 Perfiles y búsqueda

Perfil de profesor en `/api/teachers/me*` (roles `TEACHER` y `ADMIN`, `SecurityConfig.java:102-103`). Búsqueda pública (`GET /api/teachers`, `GET /api/teachers/{id}`) con `Specification` canónica: visible si `user.status = ACTIVE` y anuncio publicado (`teacher/specification/TeacherSpecifications.java:54`). El orden se valida contra una whitelist y el tamaño de página está acotado (`TeacherSearchService.java`). **VERIFIED**.

### 5.6 Contacto y mensajería

`POST /api/conversations` (solo `STUDENT`) → `ConversationService.start` (`messaging/service/ConversationService.java:88-142`): exige profesor visible, bloqueo bidireccional, perfil de estudiante con nombre, y crea una sola conversación por par con `conversation_pairs` (carrera resuelta por `ON CONFLICT DO NOTHING`, `ConversationPairRepository.java`). Encola el aviso por email al profesor. Los no participantes reciben 404. **VERIFIED**.

### 5.7 Carga de documentación (verificación)

`POST /api/teachers/me/education/{id}/documents` → `VerificationDocumentService.upload` (`verification/service/VerificationDocumentService.java:72-91`): valida propiedad, valida el contenido por magic bytes (solo PDF `%PDF-`, máximo 10 MB, `DocumentContentValidator.java:23-39`), cuota (5 documentos y 25 MB por credencial, `:33-34`), escaneo ClamAV fuera de la transacción (`:81`) y almacenamiento S3/R2 o `bytea`. Las descargas son siempre como adjunto (`TeacherVerificationController.java:90`). Los documentos usados como evidencia no se pueden borrar (`:198-207`). **VERIFIED**.

### 5.8 Revisión administrativa

`/api/admin/**` solo `ADMIN` (`SecurityConfig.java:117`). Cola de verificación, revisión de documentos, decisión `VERIFIED/REJECTED/MORE_INFO_REQUIRED` con bloqueo pesimista del perfil (`TeacherProfileRepository.java:40-42`) y auditoría. Moderación de opiniones y reportes. **VERIFIED**.

### 5.9 Roles y permisos

| Ruta | Regla (`SecurityConfig.java`) |
|---|---|
| `/api/auth/**`, `/api/human/**` | `permitAll` (`:72-73`) |
| catálogo académico (lectura) | pública; escritura `ADMIN` (`:80-90`) |
| `/api/students/**` | `STUDENT`, `ADMIN` (`:91`) |
| `/api/favorites/**` | `STUDENT` (`:92`) |
| `POST /api/conversations` | `STUDENT` (`:94`) |
| `/api/conversations/**` | `STUDENT`, `TEACHER` (`:95`) |
| `/api/teachers/me/**` | `TEACHER`, `ADMIN` (`:102-103`) |
| `/api/admin/**`, `/actuator/**` (salvo `health`) | `ADMIN` (`:76`, `:117`) |
| cualquier otra | autenticado (`:119`) |

---

## 6. Dependencias

### 6.1 Frontend (`npm outdated`, ejecutado 2026-11-17) — VERIFIED

- Con versión menor/parche disponible dentro del rango: `@playwright/test` 1.63 → 1.65, `vite` 8.3.3 → 8.3.8, `@vitejs/plugin-react` 6.1.2 → 6.3.0, `tailwindcss` / `@tailwindcss/postcss` 4.3.3 → 4.3.5, `eslint` 10.13 → 10.15, `vitest` 5.0.5 → 5.1.0, `postcss` 8.5.29 → 8.5.33.
- Saltos mayores disponibles y aplazados a propósito: React 18 → 19, `react-router` 7 → 8, TypeScript 5.9 → 7, `lucide-react` 0.468 → 1.74, `tailwind-merge` 2 → 3, `@react-oauth/google` 0.12 → 0.13. Dependabot ignora los mayores de React (`.github/dependabot.yml`, comentario de cabecera). No es deuda crítica.
- `npm audit` no se ejecutó en esta pasada (el CI lo corre con `--audit-level=high`, `.github/workflows/ci.yml`). **PENDING**.

### 6.2 Backend — PENDING

No se ejecutó `mvn dependency:tree` ni un escáner de vulnerabilidades. Observaciones:

- `commons-compress` 1.26.0 y `commons-lang3` 3.18.0 están fijadas en `dependencyManagement` (`pom.xml`), señal de parches de CVE manuales; conviene confirmar si siguen siendo necesarias. **PROBABLE**.
- Testcontainers 1.20.4: existe una serie 2.x más reciente. No bloquea producción. **PENDING** de comprobar.
- `spring-boot-devtools` está declarada solo para desarrollo y no entra al JAR (según `docs`); confirmar el `<optional>` en el `pom.xml`. **PENDING**.

---

## 7. Riesgos priorizados

### P0 — Ninguno confirmado

No hay hallazgos que, comprobados en el código, permitan acceso no autorizado, fuga de datos o corrupción del schema en una instalación configurada según la documentación.

### P1

**R-01. Brevo: fallos permanentes de entrega sin reintento ni aviso al usuario** — VERIFIED
- `BrevoEmailSender.java:82-85` convierte 400/401/403 en `PermanentNotificationException`, y `NotificationOutboxWorker.java:123` deja la fila `DEAD`.
- Consecuencia: si la API key o el remitente están mal, cada registro deja una cuenta `PENDING` que nunca recibirá el enlace. El registro igualmente responde `202`.
- Existe `notification/health/OutboxHealthIndicator.java`, pero `management.endpoint.health.show-details: never` (`application.yml`) y el chequeo no se ve desde fuera. **PENDING**: confirmar si hay alerta ante filas `DEAD`.
- Relacionado: el remitente por defecto `no-reply@claseya.app` (`application.yml`, `app.auth.activation.from`) no es verificable por Brevo sin controlar ese dominio. **VERIFIED** que es el valor por defecto; **PENDING** qué hay configurado en Render.

**R-02. Una cuenta con rol `ADMIN` puede quedar con un anuncio público contactable que nadie puede leer** — VERIFIED (código) / observado
- `TeacherSpecifications.java:54` define la visibilidad solo por `user.status = ACTIVE` y anuncio publicado, sin mirar el rol.
- `SecurityConfig.java:95` impide que `ADMIN` use `/api/conversations/**`.
- Resultado: si un profesor se promueve a `ADMIN` por SQL, su perfil sigue visible, los alumnos pueden contactarlo (`ConversationService.start` no mira el rol del profesor) y los mensajes no se pueden leer. Ocurrió en el entorno real. Es un defecto de integridad, no de seguridad.
- No hay un procedimiento documentado para crear un `ADMIN` (solo existe la regla "no se puede auto-registrar"). **VERIFIED**.

**R-03. Evidencia sin escanear si ClamAV no está activo** — PENDING (config) / VERIFIED (código)
- Con `SCAN_ENABLED=false`, `NoopDocumentScanner` registra `UNSCANNED` y deja pasar el archivo (`verification/service/NoopDocumentScanner.java`).
- `ProductionConfigValidator` exige `SCAN_ENABLED=true` o un reconocimiento explícito (`ALLOW_UNSCANNED_EVIDENCE`). El validador de contenido solo comprueba `%PDF-` (`DocumentContentValidator.java:35`), no el contenido interno del PDF, y un administrador abre ese archivo en su navegador.
- Falta confirmar qué valor tiene en Render y si ClamAV existe en el despliegue.

**R-04. Rate limiting por IP detrás del proxy de Vercel** — PENDING
- `ClientIpResolver.resolve` (`security/ratelimit/ClientIpResolver.java:19-30`) solo confía en `Forwarded`/`X-Forwarded-For` si `trust-forwarded-headers=true`. Si el backend recibe todo el tráfico desde Vercel y no está configurado, todos los usuarios comparten un bucket de IP; si se activa sin que el proxy sanee la cabecera, un cliente puede falsificar su IP.
- `ProductionConfigValidator.validateTrustedProxy` (`:145`) obliga a que ambas banderas coincidan, pero no demuestra que el proxy real sanea. Verificar en Render/Vercel. Los buckets son por instancia y en memoria (ADR-017), por lo que no escalan horizontalmente. **VERIFIED**.

**R-05. Riesgo de operar contra producción desde un entorno local** — VERIFIED
- `comandos.txt` advierte que, si `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` están definidas en la terminal, el backend local "escribe en NEON (PRODUCCION)". No hay un guardia técnico que lo impida (el guard `DemoDataProductionGuard` actúa sobre cuentas `@claseya.dev`, no sobre el destino de la conexión). **PROBABLE** que sea un riesgo vigente.

### P2

**R-06. `main` por delante de `dev` y desvío del flujo de ramas** — VERIFIED
- `main` tiene 38 commits que `dev` no tiene. `AGENTS.md` y `docs/git-workflow.md` indican que `main` solo recibe releases desde `dev`. Además, la auditoría `2026-11-13` describía el estado opuesto (`main` detrás de `dev`). Hay ramas locales sin sincronizar (`feat/todo-improvements`, `fix/production-hardening`).

**R-07. El nombre público de un usuario de Google se puede degradar al email** — PROBABLE
- `GoogleAuthService.java:86`: `user.setName(claims.name() != null ? claims.name() : email)`. Si Google no devuelve nombre, el email se guarda como nombre. En un profesor, el nombre se publica en su anuncio (ADR-028, el nombre público vive en `users.name`), lo que expondría el email. Requiere confirmar que el anuncio no se publica sin que el usuario edite el nombre y que el DTO público usa `users.name`.

**R-08. Cabeceras de seguridad con valores distintos entre capas** — VERIFIED
- El backend responde `Referrer-Policy: no-referrer` (`SecurityConfig.java:66`); `frontend/public/_headers` usa `strict-origin-when-cross-origin`. La CSP vive en una `<meta>` de `index.html` (validada por `frontend/scripts/check-csp.mjs`), y la cabecera HTTP solo declara `frame-ancestors 'none'`. El backend no emite HSTS (sin coincidencias en `src/main`); depende de Render y Vercel. **PENDING** confirmar HSTS en producción.

**R-09. Dos fuentes de verdad documental en conflicto** — VERIFIED
- `README.md` afirma que existe `codeql.yml`; no existe en `.github/workflows/` (solo `ci.yml`, `container-scan.yml`, `dependency-submission.yml`).
- `docs/architecture/architecture.md:89` dice `V1..V18`; hay V1..V25. `docs/architecture/database.md:8` dice `V1..V21`. `docs/product/domain.md` afirma que `reviews` no tiene entidad, pero existe `model/Review.java`. `architecture.md` cita `guia-proyecto.md`, que no existe en el repositorio.
- `docs/audits/2026-11-13-production-readiness.md` dice que `dev` no tiene protección; `README.md` pide activar branch protection en `main`. Estado real **PENDING** (configuración de GitHub).

**R-10. Eliminación de cuentas y datos personales** — PENDING
- Las claves foráneas son `RESTRICT` y el criterio es la desactivación lógica (`AGENTS.md`). No encontré un flujo de "eliminar mi cuenta" ni de exportación de datos. Si el lanzamiento es en Argentina, conviene revisar la Ley 25.326 de protección de datos con asesoría. **PENDING**.

### P3

- **R-11.** Archivos grandes con responsabilidades mezcladas: `frontend/src/api.ts` (889 líneas, todo el cliente y los tipos), `MessagesPage.tsx` (670), `AdminVerificationsPage.tsx` (613). **VERIFIED**.
- **R-12.** Ruido en logs: el log `A cookie header was received [g_state=…] that contained an invalid cookie` (cookie de Google Identity) se registra en INFO. Inocuo. **VERIFIED** (observado).
- **R-13.** Helpers sin uso (`TeacherSearchService.defaultSize()`, `FavoriteService.defaultSize()`), según `docs/architecture/architecture.md`. **PROBABLE**.
- **R-14.** `/api/students/**` permite `ADMIN` (`SecurityConfig.java:91`); no consta un caso de uso para ese rol. **PROBABLE**.

---

## 8. Deuda técnica

| Área | Detalle | Evidencia |
|---|---|---|
| Arquitectura | Reglas ArchUnit 3 a 7 sin aplicar; cruces entre repositorios de distintos módulos. | `docs/architecture/guardrails.md` |
| Caché de usuario | El filtro hace una consulta a `users` por request (ADR-028 opción A parcial); falta la medición de 100 requests. | `docs/architecture/architecture.md` |
| Duplicación | La regla D1 ya se unificó en `TeacherSpecifications` (el segundo informe detectó una copia en `FavoriteRepository`; el estado actual no se revalidó en esta pasada). | `review2.md` |
| Frontend | Cliente HTTP monolítico y páginas grandes; sin `TODO`/`FIXME` en el código (búsqueda sin resultados). | `frontend/src/api.ts` |
| Legacy | Tablas `reports`, `notifications` y vestigios de `AVAIL-001`/`BOOK-001` conservados "como historia". | `AGENTS.md`, `V17` |
| Documentación | Deriva descrita en R-09. Artefactos de IA y auditorías en la raíz (`review.md`, `review2.md`, `hallazgos-*.md`, `comandos.txt`, `.atl/`). | raíz del repositorio |
| Observabilidad | Sin métricas externas, trazas ni alertas (Prometheus pendiente, `docs/architecture/security.md`). | `docs/architecture/security.md` |
| Escalado | Rate limit y outbox con estado por instancia; el worker usa lease, así que es seguro con varias instancias, el rate limit no comparte estado. | ADR-017, `NotificationWorkerScheduler` |

---

## 9. Aspectos no verificables desde el repositorio

1. Variables reales de Render y Vercel (`EMAIL_TRANSPORT`, `EMAIL_API_KEY`, `MAIL_FROM`, `CAPTCHA_ENABLED`, `TURNSTILE_SECRET`, `SCAN_ENABLED`, `STORAGE_*`, `BEHIND_TRUSTED_PROXY`, `RATE_LIMIT_TRUST_FORWARDED_HEADERS`, `COOKIE_*`, `JWT_SECRET`).
2. Si existe ClamAV desplegado y alcanzable, y si el bucket R2/S3 es privado.
3. Si el dominio de envío o el remitente están verificados en Brevo y cómo se maneja un 401.
4. Estado real de la base en Neon: datos demo remanentes (cuentas `@claseya.dev`), cuentas con rol manual, filas `DEAD` en `notification_outbox`, backups y retención.
5. Protección de ramas y checks obligatorios en GitHub; secretos del repositorio.
6. Resultado vigente de `mvn -B clean verify`, de `npm audit` y de los E2E (no se ejecutaron en esta auditoría).
7. HSTS, TLS y cabeceras reales en las respuestas de producción.
8. Política legal real (términos, privacidad) y su revisión por un profesional.
9. Datos de producción: volumen, cantidad de profesores reales (la auditoría del 2026-11-11 indicó cero oferta real).

---

## 10. Plan recomendado de auditorías

| # | Auditoría | Objetivo | Insumos |
|---|---|---|---|
| 01 | Seguridad de sesión y autorización | Matriz rol × endpoint probada, CSRF, enumeración, revocación | `SecurityConfig`, tests de integración |
| 02 | Entrega de correo y outbox | Brevo/remitente, `DEAD`, reintentos, alerta, deliverability | Render, Brevo, `notification_outbox` |
| 03 | Verificación documental | ClamAV, almacenamiento, abuso de PDF, acceso admin | `verification/*`, R2 |
| 04 | Privacidad y datos personales | Inventario de PII, nombre público, borrado y retención | DTOs públicos, esquema |
| 05 | Base de datos y migraciones | Índices, FKs, plan de backup/restore en Neon, V1..V25 | Neon, `db/migration` |
| 06 | Despliegue y configuración | Variables, proxy, HSTS, rate limit, health | Render, Vercel |
| 07 | Pipeline y gobierno de ramas | CI como gate, branch protection, deriva `main`/`dev` | GitHub |
| 08 | Frontend, SEO y accesibilidad | CSP, prerender, errores de red, móviles | `frontend/` |
| 09 | Rendimiento y carga | Búsqueda, hilo de mensajes, pool de conexiones | `scripts/load`, `docs/testing/load-testing.md` |
| 10 | Documentación y operación | Runbook (crear admin, rotar secretos, responder a incidentes) | `docs/` |

---

## 11. Evaluación de preparación para producción

| Dimensión | Estado | Comentario |
|---|---|---|
| Arquitectura y calidad de código | Alta | Capas consistentes, validadores de arranque, tests amplios. |
| Seguridad de aplicación | Alta | Sin bypass encontrado; controles principales verificados en código. |
| Seguridad de despliegue | Sin verificar | R-03, R-04 y HSTS dependen de configuración externa. |
| Entrega de correo | Frágil | R-01: depende de Brevo y no hay señal de fallo para el usuario. |
| Operación y observabilidad | Baja | Sin alertas ni métricas externas; sin runbook (R-02, §8). |
| Datos y privacidad | Media | PII bien contenida en DTOs; faltan borrado/exportación (R-10) y R-07. |
| Proceso y documentación | Media | Documentación abundante pero con deriva (R-06, R-09). |
| Producto | Incierto | Sin oferta real de profesores según la auditoría del 2026-11-11. |

**Conclusión objetiva:** el software está en condiciones técnicas de una beta controlada. Para abrir tráfico real faltan cerrar R-01 a R-05 y confirmar los puntos de §9.

---

## 12. Preguntas que requieren intervención humana

1. ¿Qué remitente y dominio usará Brevo en producción? ¿Se comprará un dominio propio antes de abrir tráfico?
2. ¿Hay alguien que vea las filas `DEAD` del outbox, o se acepta que un fallo de Brevo pase inadvertido?
3. ¿Cómo se crea y se rota un `ADMIN`? ¿Se escribe un procedimiento (SQL documentado) y se bloquea que un `ADMIN` tenga un anuncio visible? (R-02; sería un cambio de Nivel 2).
4. ¿ClamAV está desplegado? Si no, ¿se acepta `ALLOW_UNSCANNED_EVIDENCE` con PDFs abiertos por un administrador? (R-03).
5. ¿Qué valores tienen `BEHIND_TRUSTED_PROXY` y `RATE_LIMIT_TRUST_FORWARDED_HEADERS` en Render, y el proxy de Vercel sanea `X-Forwarded-For`? (R-04).
6. ¿Se mantiene `main` por delante de `dev` o se regulariza el flujo `dev` → `main`? (R-06).
7. ¿Se aplicó la protección de ramas y los checks obligatorios en `main`?
8. ¿Hay política de backups y de restauración en Neon, y se probó?
9. ¿Se necesita flujo de baja de cuenta y exportación de datos antes del lanzamiento? (R-10).
10. ¿Cuántos profesores reales hay cargados y se eliminaron los datos demo en producción?
