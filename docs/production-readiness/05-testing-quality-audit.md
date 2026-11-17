# 05 — Auditoría de Testing y Calidad

- Fecha: 2026-11-17
- Alcance: tests backend (JUnit 5, Spring Boot Test, Testcontainers, ArchUnit), tests frontend (Vitest + Testing Library), E2E (Playwright), CI (GitHub Actions).
- Método: lectura estática del repositorio + una ejecución segura: `npx vitest run` en `frontend/` (jsdom, sin red, sin datos externos). **No se ejecutó la suite backend**: requiere Docker/Testcontainers y el daemon de Docker no estaba activo (`failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine`). **No se ejecutó Playwright**: arranca backend (`mvn spring-boot:run` con perfil `local`) contra PostgreSQL local con siembra demo, lo que escribe en una base y deja procesos en segundo plano (prohibido por `AGENTS.md`). No se modificó código.
- Clasificación: **CONFIRMADO** (evidencia directa o ejecución), **PROBABLE** (deducido), **PENDIENTE** (requiere ejecución/entorno).
- No se inventan porcentajes de cobertura: se reportan solo los del informe JaCoCo existente, con su fecha.

## 1. Resumen ejecutivo

La suite es amplia y orientada a riesgo para un proyecto de este tamaño: **88 archivos de test Java con 528 métodos `@Test`**, 30 archivos Vitest con **126 tests (ejecutados: 126/126 en verde, 11,4 s)** y 16 specs Playwright (~43 casos). CI ejecuta backend (`mvn -B clean verify` con umbral JaCoCo), frontend (lint, test, build, `npm audit`), E2E contra PostgreSQL real, revisión de dependencias en PR y escaneo de contenedor con Trivy.

Fortalezas confirmadas:

- Integración real contra PostgreSQL 16 (Testcontainers, un contenedor por JVM, perfil `test`); migraciones completas ejecutadas en cada corrida.
- Cobertura de seguridad backend sólida: JWT expirado/manipulado (`JwtServiceTest`, `AuthIntegrationTest:251`), revocación de sesión (`SessionRevocationIntegrationTest`), CSRF, cabeceras, rate limit (unidad e integración), CAPTCHA, validadores de configuración de producción, IDOR en favoritos (`FavoriteIntegrationTest:307`), Google ID token expirado/manipulado (`GoogleIdTokenVerifierTest`).
- Concurrencia probada con tests dedicados: activación, conversaciones, verificación (`*ConcurrencyIntegrationTest`).
- Regla de arquitectura automatizada (`ArchitectureTest`), contrato de errores y de API (`ErrorContractIntegrationTest`, `ApiContractIntegrationTest`), contrato de índices (`SchemaIndexIntegrationTest`).
- Sin tests deshabilitados ni omitidos (`@Disabled`, `skip`, `fixme`: 0 coincidencias); `forbidOnly` activo en CI de Playwright.
- Umbral de cobertura que rompe el build: LINE ≥ 85 % y BRANCH ≥ 70 % (`pom.xml:~255-270`).

Debilidades principales:

- **Frontend: casi nada de las pantallas de autenticación tiene test unitario** (`LoginModal`, `RegisterPage`, `ActivateAccountPage`, `ForgotPasswordPage`, `GoogleSignInButton`, `AuthContext`, `ProtectedRoute`: 0 archivos de test) y no hay medición de cobertura frontend.
- **Huecos específicos que los informes 02 y 03 identificaron como defectos no tienen test**: Brevo 401 → `DEAD` sin retry, subida de documentos con estado `UNDER_REVIEW`/`VERIFIED`, timeouts de Turnstile, timeout de subida multipart, reintento de 403.
- **Cobertura backend**: regenerada el 2026-11-17 tras `mvn -B clean verify` (ver sección 3). El resto de la frase original sobre informes de 2026-11-13 queda como histórico.
- E2E no ejecutable de forma aislada y sin job que pruebe despliegue (staging); no hay prueba de integración real contra Brevo/R2/Google (aceptable) pero tampoco contratos con dobles (WireMock) para Brevo, R2 ni Turnstile en el flujo completo.
- No hay pruebas de carga, accesibilidad automatizada (axe) ni de fuzzing.

Resultado: **0 P0, 2 P1, 8 P2, 7 P3**. Veredicto: la calidad de pruebas acompaña una beta controlada; antes de producción hay que cerrar T-01 y T-02.

## 2. Tests existentes

### 2.1 Backend (`src/test/java/com/claseya`) — 88 archivos, 528 `@Test`

| Tipo | Archivos representativos | Notas |
|---|---|---|
| Integración con PostgreSQL (`AbstractPostgresTest`/`AbstractWebIntegrationTest`) | `AuthIntegrationTest` (27 tests), `MessagingIntegrationTest` (30), `VerificationApiIntegrationTest` (15), `GoogleAuthIntegrationTest` (8), `ReviewIntegrationTest` (8), `SafetyIntegrationTest` (5), `TeacherSearchIntegrationTest`, `TeacherProfileIntegrationTest`, `AdminDirectoryIntegrationTest`, `PasswordResetIntegrationTest`, `NotificationOutboxIntegrationTest`, `SitemapIntegrationTest` … | MockMvc sobre contexto Spring completo. |
| Seguridad | `CsrfIntegrationTest`, `SecurityHeadersIntegrationTest`, `SessionRevocationIntegrationTest`, `RateLimitIntegrationTest`, `JwtServiceTest`, `ProductionConfigValidator*Test` (4), `DemoDataProductionGuardTest`, `JwtSecret*Test`, `SecurityConfigOriginsTest` | Cubre configuración de arranque segura. |
| Rate limiting (unidad) | `security/ratelimit/*` (9 clases) | Con reloj controlado (`MutableTimeMeter`): determinista. |
| Concurrencia | `ActivationConcurrencyIntegrationTest`, `ConversationConcurrencyIntegrationTest`, `VerificationConcurrencyIntegrationTest` | Hilos reales contra PostgreSQL. |
| Verificación/documentos | `VerificationWorkflowIntegrationTest`, `AdminVerificationReviewIntegrationTest`, `VerificationAggregatorTest`, `DocumentContentValidatorTest`, `S3DocumentStorageTest`, `ClamAvDocumentScannerTest`, `VerificationDocumentService*Test` | Tamaño excedido, contenido no PDF, escáner desactivado. |
| Notificaciones | `BrevoEmailSenderTest`, `SmtpEmailSenderTest`, `EmailTransportSelectionTest`, `EmailTemplatesTest`, `NotificationOutboxIntegrationTest`, `OutboxHealthIndicatorTest` | Sender con doble (`CapturingEmailSender`). |
| Contratos y arquitectura | `ApiContractIntegrationTest`, `ErrorContractIntegrationTest`, `ArchitectureTest`, `SchemaIndexIntegrationTest` | |
| Demo/seed | `DemoSeedIntegrationTest`, `DemoDataQuarantine*Test` | |

No existen tests de repositorio aislados (`@DataJpaTest`); los repositorios se prueban a través de integración completa (aceptable, más lento). Los controllers se prueban vía MockMvc completo, no con `@WebMvcTest`.

### 2.2 Frontend unitario (`frontend/src/**/*.test.*`) — 30 archivos, 126 tests (ejecutados: todos pasan)

Cubren: `api.ts` (timeouts, errores, 429 con `Retry-After`, fallo de red → `ApiError` 0), `ErrorBoundary`, `HumanGate`, `ContactModal`, `ReportModal`, `ReviewModal`, `SubjectCombobox`, `VerificationPanel`, `Field`, `Navbar`, `Pagination`, `usePageMeta`, `useTeacherListing`, utilidades (`favorites`, `format`, `messaging`, `teacherProfile`, `validation`, `routes`), SEO (`handler`, `metadata`, `robots`), `ThemeProvider`, páginas `MessagesPage`, `SearchPage`, `TeacherProfilePage`, `ResetPasswordPage`, `AdminPage`, `AdminUsersPage`, `AdminVerificationsPage`.

Salida de la ejecución: 30/30 archivos, 126/126 tests, duración 11,4 s. El log imprime trazas esperadas del test de `ErrorBoundary` (error `boom` deliberado) y una recomendación de rendimiento de vitest (jsdom se crea 30 veces, 73 s acumulados en paralelo; `pool: 'vmThreads'` o `isolate: false` reducirían tiempo).

### 2.3 E2E Playwright (`frontend/tests/**`) — 16 specs, ~43 casos

`admin` (3 specs), `favorites`, `messages` (6), `mobile`, `my-listing`, `profile`, `registration` (5), `routing` (10), `search` (4), `session` (2), `teacher-listing`, `teacher-profile` (3), `theme`, `transparency`. Configuración (`playwright.config.ts`): un worker, sin paralelismo, `retries: 0`, arranca su propio backend (`mvn spring-boot:run -Dspring-boot.run.profiles=local`, rate limit desactivado, cookie no segura, enlaces de activación en log) y Vite; espera a que la siembra demo alcance ≥ 45 profesores (`global-setup.ts`).

### 2.4 CI (`.github/workflows`)

| Workflow | Contenido | ¿Bloqueante? |
|---|---|---|
| `ci.yml` `backend` | `mvn -B clean verify` (tests + JaCoCo `check`), sube `surefire-reports` si falla | Sí, si el job está marcado como check obligatorio (PENDIENTE: la configuración de branch protection no es visible; `00` ya la marcó como no verificada) |
| `ci.yml` `frontend` | `npm ci`, `npm run lint`, `npm test`, `npm run build` (incluye `tsc` y `check-csp.mjs`), `npm audit --audit-level=high` | Idem |
| `ci.yml` `e2e` | PostgreSQL 16 como servicio, `npx playwright test` (Chromium) | Idem |
| `ci.yml` `dependency-review` | Solo en PR, falla en severidad alta | Idem |
| `container-scan.yml` | Trivy sobre la imagen del backend en `main` y semanal | Informativo (no corre en PR) |
| `dependabot.yml` | Actualización automática de dependencias | — |

Observaciones: ejecuta en `push` a `main`/`dev` y en cada `pull_request`; hay `concurrency` con cancelación; `timeout-minutes` en cada job; permisos mínimos (`contents: read`). Falta `CodeQL`/SAST (la documentación lo cita pero `codeql.yml` no existe, R-12 en 00).

## 3. Cobertura comprobada

**Actualización 2026-11-17 (ejecución real, `mvn -B clean verify`, Docker activo):** BUILD SUCCESS; 528 tests, 0 fallos, 0 errores, 0 omitidos; `jacoco:check` cumplido (≥85 % líneas, ≥70 % ramas). Cifras desde `target/site/jacoco/jacoco.csv` regenerado ese día: líneas 91,2 % (3.836 cubiertas / 371 no), instrucciones 91,0 % (17.783 / 1.757), ramas 76,0 % (1.000 / 315). Prácticamente igual al informe del 2026-11-13 que se detalla abajo (por paquete).

**Backend (JaCoCo, informe existente del 2026-11-13, no regenerado):** instrucciones 91,0 % (17.762 cubiertas / 1.758 no cubiertas) y ramas 76,0 % (999 / 316), calculado desde `target/site/jacoco/jacoco.csv`. El comentario de `pom.xml` declara una línea base de 88,7 % de líneas y 73,9 % de ramas, y el umbral CI es 85 % / 70 %. Paquetes con menor cobertura de instrucciones en ese informe: `review.dto` 41,7 %, `academic.service` 61,4 %, `academic.controller` 66,2 %, `verification.controller` 68,7 %, `common.util` 73,4 %, `review.controller` 83,5 %, `common.exception` 83,7 %. (`model` aparece 0 % por tratarse de entidades; el `pom.xml` excluye Lombok.) El resto supera 85 %. Importante: cobertura de líneas no prueba corrección; el paquete `verification.controller` (68,7 %) es justo el que sirve la descarga de evidencia.

**Frontend:** **no existe medición de cobertura** (`vitest.config.ts` sin `coverage`; sin `@vitest/coverage-*` en `package.json`). No hay cifra comprobable. Por inventario: 7 de 17 páginas tienen test unitario y de los componentes críticos de autenticación ninguno (sección 4).

**E2E:** sin medición de cobertura de flujos; el inventario está en la sección 4.

## 4. Flujos sin cobertura (o con cobertura parcial)

Leyenda: B = backend integración, F = frontend unitario, E = E2E.

| Flujo | B | F | E | Hueco |
|---|---|---|---|---|
| Registro | ✔ `AuthIntegrationTest`, `AccountActivationIntegrationTest` | ✘ `RegisterPage` sin test (solo `validation.test.ts`) | ✔ `registration.spec` | Errores de campo 400, 503 y pantalla "Revisá tu email" sin test unitario; envío real del correo no probado extremo a extremo. |
| Activación | ✔ (token válido/expirado/concurrencia) | ✘ `ActivateAccountPage` | Parcial (enlace en log) | Reenvío con 429 mostrado como éxito (03: UX) sin test. |
| Login/Logout | ✔ | ✘ `LoginModal`, `AuthContext` | ✔ `session.spec` | Mensaje por 401/429/5xx, cuenta pendiente, expiración y `UNAUTHORIZED_EVENT` sin test unitario. |
| Google OAuth | ✔ `GoogleAuthIntegrationTest` (8), `GoogleIdTokenVerifierTest` | ✘ `GoogleSignInButton`, flujo de elección de rol | ✘ (Google no es automatizable sin doble) | Selección de rol por regex sobre el mensaje (`/role/i`) sin test; fallo de JWKS → hoy 400 (B-06). |
| Recuperación de contraseña | ✔ `PasswordResetIntegrationTest` | Parcial: solo `ResetPasswordPage`; `ForgotPasswordPage` sin test | Sin spec dedicado identificado | |
| Búsqueda | ✔ `TeacherSearchIntegrationTest`, `SubjectCatalogIntegrationTest` | ✔ `SearchPage`, `SubjectCombobox`; `SearchInput` sin test propio | ✔ `search.spec` | Carga con red lenta, respuestas 5xx y cancelación de peticiones sin test. |
| Perfil público | ✔ | ✔ | ✔ | — |
| Contacto / mensajería | ✔ `MessagingIntegrationTest` (30), concurrencia | ✔ `ContactModal`, `MessagesPage` | ✔ `messages.spec` (6) | Mensajes con ADMIN (403, caso real reportado) sin test de UI; reintento/duplicado tras timeout. |
| Gestión de profesor ("Mi anuncio") | ✔ | ✔ `useTeacherListing`, `VerificationPanel` | ✔ (`my-listing`, `teacher-listing`) | `MyListingPage` sin test unitario. |
| Carga de documentos | ✔ tamaño, no-PDF, sin archivo/tipo | ✔ `VerificationPanel` | Parcial | Subida en `UNDER_REVIEW`/`VERIFIED` (B-03) sin test; timeout multipart 15 s (F-03) sin test; límite 5 docs/25 MB por credencial: verificar (existe `assertQuotaAvailable`, test no confirmado en esta revisión). |
| Verificación de profesores (admin) | ✔ `AdminVerificationReviewIntegrationTest`, concurrencia de decisiones | ✔ `AdminVerificationsPage` | ✔ `admin-verification.spec` | Concurrencia subida vs decisión (B-02) sin test. |
| Moderación (reseñas/reportes/usuarios) | ✔ `ReviewIntegrationTest`, `SafetyIntegrationTest`, `AdminDirectoryIntegrationTest` | Parcial: `AdminReviewsPage`, `AdminReportsPage` sin test | ✔ `admin.spec`, `admin-users.spec` | |
| Favoritos | ✔ (incluye IDOR) | ✘ `FavoritesPage` | ✔ `favorites.spec` | |
| Notificaciones por correo | ✔ outbox, templates, `BrevoEmailSenderTest` | n/a | ✘ | 401/403 → `DEAD` y borrado de token sin test de recuperación (B-01). |
| Sitemap/SEO | ✔ `SitemapIntegrationTest` | ✔ `seo/*` | ✘ | 10.000 perfiles por petición sin test de carga (B-07). |

## 5. Tests faltantes

Prioridad según el riesgo registrado en los informes 01–04.

### P1

**T-01 (P1, CONFIRMADO) — Sin test para el fallo permanente del proveedor de correo.**
El caso real vivido (Brevo `401`) lo trata `BrevoEmailSender.java:82-85` + `NotificationOutboxWorker.java:119-125` como `DEAD` y borra el token. `BrevoEmailSenderTest` y `NotificationOutboxIntegrationTest` existen, pero no hay un test que fije el comportamiento esperado de recuperación (reencolado, alerta o reintento manual). Añadir test que demuestre que, tras un 401, una nueva petición de reenvío de activación entrega el correo y que `OutboxHealthIndicator` reporta filas `DEAD`.

**T-02 (P1, CONFIRMADO) — Sin pruebas de autorización por rol sobre todos los endpoints (matriz).**
Hay 8 aserciones `isForbidden` en 15 archivos que mencionan 403, pero no existe un test parametrizado que recorra `SecurityConfig` (rol × endpoint × método) y detecte un cambio accidental de orden de matchers (el orden es crítico: `SecurityConfig.java:69-119`). Casos concretos: ADMIN en `/api/conversations/**` (403 por diseño), TEACHER en `/api/favorites`, STUDENT en `/api/availability`, anónimo en `/api/admin/**`, método no GET sobre `/api/teachers/*` anónimo. Un test de matriz es barato y bloquea regresiones de seguridad.

### P2

| ID | Estado | Falta |
|---|---|---|
| T-03 | CONFIRMADO | Acceso a **documentos privados**: descarga de evidencia por otro profesor o por estudiante (404/403), por anónimo (401) y por admin (200). Hay `anUnknownDocumentAnswers404` y `downloadIsAnAttachmentAndIsNotCached`, pero no se encontró un test de acceso cruzado explícito entre profesores (verificar con grep dirigido en la implementación de la mejora). |
| T-04 | CONFIRMADO | B-03/B-04: subida y edición de credenciales en `UNDER_REVIEW`/`VERIFIED` (documentar regla y fijarla con test tras decidir). |
| T-05 | CONFIRMADO | B-02: concurrencia subida de documento vs decisión de verificación (hoy solo decisiones concurrentes entre sí). |
| T-06 | CONFIRMADO | Fallos de servicios externos: Turnstile caído/lento (B-06), JWKS de Google inaccesible (debe ser 503, hoy 400), R2 no disponible durante subida/descarga/borrado (DB-17), ClamAV caído (debe ser 503). Existen tests unitarios de cada cliente, pero no de su efecto en el flujo HTTP completo. |
| T-07 | CONFIRMADO | Frontend de autenticación sin tests: `LoginModal`, `RegisterPage`, `ActivateAccountPage`, `ForgotPasswordPage`, `AuthContext`, `ProtectedRoute`, `GoogleSignInButton`. Mínimo: estados de error (401/429/5xx/red), doble envío, expiración (`UNAUTHORIZED_EVENT`), restauración de sesión, intercambio de `message-link`. |
| T-08 | CONFIRMADO | `api.ts`: 200 con cuerpo no JSON (F-07), reintento de 403 solo CSRF (F-04), timeout de multipart (F-03), 204/202 sin cuerpo, `Retry-After` en 503. Existen tests de 429 y red. |
| T-09 | CONFIRMADO | Límites de entrada: longitudes máximas de bio/mensaje/nombre/email, caracteres Unicode, emojis, cadenas de 10 000 caracteres, HTML en campos (XSS almacenado → verificar escape en respuesta/plantillas), SQL en búsqueda (parametrizado, pero sin test explícito de entradas maliciosas en `TeacherSearchIntegrationTest`). |
| T-10 | CONFIRMADO | Sesión expirada en E2E: JWT vencido a mitad de un flujo (mensajes, subida) y comportamiento de la UI (aviso, redirección, no perder el borrador). |

### P3

- T-11: accesibilidad automatizada (axe-core con Playwright) en login, registro, búsqueda, modales; navegación por teclado.
- T-12: pruebas de carga/humo (k6 o similar) en `/api/teachers`, sitemap y subida; ninguna existe.
- T-13: contrato front/back (tipos generados o tests de contrato) — los tipos TS no están validados contra el backend (03).
- T-14: test de migración con datos previos (V18 con duplicados; ver informe 04).
- T-15: pruebas de recuperación/retención del outbox y purga (cuando se implemente B-05).
- T-16: pruebas de cabeceras de seguridad en el entorno desplegado (Vercel), no solo en `SecurityHeadersIntegrationTest` (F-01).
- T-17: Playwright en navegadores adicionales (solo Chromium hoy) y viewports/redes lentas con throttling.

## 6. Problemas de calidad

| ID | Prioridad | Estado | Hallazgo |
|---|---|---|---|
| Q-01 | P2 | CERRADO | **Backend ejecutado el 2026-11-17** (después del informe inicial): `mvn -B clean verify` → BUILD SUCCESS, 528 tests, 0 fallos/errores/omitidos, umbrales JaCoCo cumplidos. Esa corrida incluye el cambio de B-01 (Brevo 401/403 reintentables). |
| Q-02 | P2 | CONFIRMADO | **E2E contra estado compartido**: un solo worker, base demo mutable ("Tests mutate shared state", `playwright.config.ts:13`); el orden y la siembra condicionan los resultados. Dependencia temporal: `global-setup.ts` hace *polling* hasta 60 s esperando 45 perfiles. Riesgo de flakiness ante siembra lenta. `retries: 0` y `workers: 1` lo hacen determinista pero lento (~25 min de timeout de job). |
| Q-03 | P2 | CONFIRMADO | **E2E arranca backend con configuración distinta de producción**: rate limit desactivado, cookie sin `Secure`, enlaces de activación en log, worker de notificaciones desactivado. Por tanto E2E no valida cabeceras seguras, límites ni entrega de correo; esos aspectos recaen solo en tests de integración. Falsa sensación de cobertura de seguridad extremo a extremo. |
| Q-04 | P3 | CONFIRMADO | **Cobertura frontend sin medir** y sin umbral; la cobertura backend tiene umbral de bundle (85/70) pero no por paquete: un paquete crítico puede bajar mucho sin romper el build (p. ej. `verification.controller` 68,7 %). |
| Q-05 | P3 | CONFIRMADO | **Esperas por tiempo en tests**: 6 usos de `sleep`/`waitForTimeout`/`Thread.sleep` en tests y 4 de Awaitility/`await()`; revisar cada uno por potencial de flakiness (no inspeccionados uno a uno en esta pasada). |
| Q-06 | P3 | CONFIRMADO | **Aislamiento backend**: un contenedor PostgreSQL por JVM compartido por todas las clases (`AbstractPostgresTest`), por lo que el estado persiste entre clases salvo limpieza propia; el orden de ejecución no debería importar pero depende de que cada clase use datos únicos. No se verificó la limpieza de cada clase. |
| Q-07 | P3 | CONFIRMADO | **Configuración específica del entorno local en el `pom.xml`** (`api.version=1.43`, `DOCKER_API_VERSION`, UTC): documentada, pero indica una dependencia del daemon Docker local y fragilidad si cambia la versión. |
| Q-08 | P3 | CONFIRMADO | Tests de `ErrorBoundary` imprimen trazas de error en la salida (esperado): conviene silenciar `console.error` en esos tests para no ocultar errores reales en el log de CI. |
| Q-09 | P3 | CONFIRMADO | Mocks: `fetch` mockeado en `api.test.ts` (aislado y determinista); backend usa dobles `CapturingEmailSender` y clientes con servidor simulado. No se detectaron mocks que reproduzcan la implementación (falsos positivos) en el muestreo realizado, pero no se revisaron los 88 archivos uno a uno. |
| Q-10 | P3 | PENDIENTE | **Branch protection** y checks obligatorios no verificables desde el repositorio; sin ellos "tests obligatorios" no está garantizado. |

Determinismo y aislamiento: rate limit con reloj inyectado; sin dependencias de red externas en tests unitarios; Vitest en jsdom con `fetch` simulado. La principal fuente de no determinismo identificada es E2E (estado compartido, siembra, procesos locales).

## 7. Matriz de pruebas recomendadas

| Riesgo (informe) | Prueba | Tipo | Dónde | Prioridad |
|---|---|---|---|---|
| B-01 Brevo 401 → DEAD | 401 reencola o alerta; reenvío recupera | Integración backend | `NotificationOutboxIntegrationTest` | P1 |
| S-01/S-03 rate limit tras proxy | `X-Forwarded-For` con/sin `BEHIND_TRUSTED_PROXY` | Integración | `RateLimitIntegrationTest` | P1 |
| Matriz de roles | Parametrizado rol × endpoint × método | Integración (MockMvc) | nueva clase de seguridad | P1 |
| B-03/B-04 reglas de verificación | Subida/edición por estado | Integración | `VerificationWorkflowIntegrationTest` | P2 |
| B-02 deadlock | Subida concurrente con decisión | Concurrencia | `VerificationConcurrencyIntegrationTest` | P2 |
| B-06 Turnstile/Google | Timeout → 503, sin conexión retenida | Integración con servidor simulado | `CaptchaIntegrationTest`, `GoogleAuthIntegrationTest` | P2 |
| DB-04 email en mayúsculas | Duplicado rechazado tras la migración | Integración | nueva | P2 |
| F-01 cabeceras Vercel | `curl -sI` del despliegue | Smoke post-deploy | script CI | P1/P2 |
| F-02 `photoUrl` | Solo https/hosts permitidos; `referrerPolicy` | Unidad + backend | `TeacherProfileIntegrationTest`, `primitives.test.tsx` | P1/P2 |
| F-03 subida 15 s | Throttling de red, PDF de 10 MB | E2E Playwright | nueva | P2 |
| F-07 respuesta no JSON | 200 HTML en `/auth/me` | Unidad | `api.test.ts` | P2 |
| F-04 reintento 403 | Solo CSRF; autorización no se duplica | Unidad | `api.test.ts` | P2 |
| T-07 auth frontend | Estados de error, expiración, restauración | Unidad (RTL) | nuevos `*.test.tsx` | P2 |
| T-10 sesión expirada | JWT vencido en medio del flujo | E2E | `session.spec.ts` | P2 |
| T-09 entradas maliciosas | HTML/SQL/Unicode/longitudes | Integración + unidad | varios | P2 |
| T-11 accesibilidad | axe en páginas clave | E2E | nueva | P3 |
| T-12 carga | k6 sobre búsqueda/sitemap/subida | Rendimiento | fuera del repo/CI nocturno | P3 |
| DB-17 R2 huérfanos | Fallo de borrado/limpieza | Integración con doble | `VerificationDocumentServiceUploadTest` | P3 |

## 8. Comandos reproducibles

Ejecutados en esta auditoría (seguros, sin escritura externa):

```bash
cd frontend && npx vitest run      # 30 archivos, 126 tests: PASS (11,4 s)
docker info                         # daemon apagado: impide Testcontainers
```

Para reproducir el resto (requieren Docker Desktop activo y, en E2E, escriben en una base local de desarrollo; no apuntar nunca a Neon):

```powershell
# Backend completo con cobertura (Testcontainers; no toca bases externas)
mvn -B clean verify              # incluye jacoco:report y jacoco:check (85% líneas / 70% ramas)
scripts/verify.ps1               # reporta PASS/FAIL (AGENTS.md)

# Informe de cobertura backend
start target/site/jacoco/index.html

# Frontend
cd frontend
npm run lint
npx tsc --noEmit
npm test
npm run build                    # tsc + vite build + check-csp
npm audit --audit-level=high

# E2E: arranca su propio backend (perfil local) y Vite; requiere PostgreSQL local de desarrollo
cd frontend; npx playwright test
```

Notas: cerrar procesos Java/Node tras ejecutar E2E (`AGENTS.md` prohíbe dejar procesos en segundo plano). Verificar que `DB_URL` no apunte a Neon antes de lanzar Playwright (riesgo R-05 de `00`: `comandos.txt`).

Comandos recomendados para cubrir huecos (aún no existen): `npm test -- --coverage` (tras añadir `@vitest/coverage-v8`), `npx playwright test --project=chromium --grep @a11y`, y un script de humo post-despliegue con `curl -sI`.

## 9. Criterios de aceptación

Para considerar la estrategia de pruebas lista para producción:

1. `mvn -B clean verify` y `npm run lint && npm test && npm run build` en verde en CI, y los jobs `backend`, `frontend` y `e2e` configurados como checks obligatorios en `main` (comprobado en GitHub).
2. Cobertura backend regenerada en CI (artefacto JaCoCo adjunto) ≥ umbral actual; cobertura frontend medida con umbral inicial acordado (no inventado: medir primero y fijar el suelo por debajo de la base).
3. Existen y pasan: test de matriz de roles (T-02), test de recuperación de correo (T-01), test de acceso cruzado a documentos (T-03), tests de reglas de verificación (T-04) y de concurrencia subida/decisión (T-05).
4. Tests unitarios para `LoginModal`, `RegisterPage`, `ActivateAccountPage`, `ForgotPasswordPage`, `AuthContext`, `ProtectedRoute` con los estados de error, doble envío y expiración (T-07).
5. E2E cubre sesión expirada (T-10) y subida con red lenta (T-08/F-03) y se ejecuta con la configuración más cercana a producción posible (cookie segura, rate limit activo) en al menos un job de humo.
6. Cero tests omitidos (`@Disabled`, `skip`), sin `sleep` fijo no justificado y ejecución repetida (3 veces) de la suite E2E sin fallos intermitentes.
7. Smoke post-despliegue automatizado: cabeceras de seguridad, `/actuator/health`, login de cuenta de prueba, búsqueda.
8. Resultado de la ejecución de la suite backend completa registrado en este informe (hoy **PENDIENTE** por Docker apagado).

## 10. Plan de mejora

**Fase 0 — Verificación (sin código, 1 día)**
- Activar Docker Desktop y ejecutar `mvn -B clean verify`; guardar el resultado y el informe JaCoCo actualizados; comprobar que CI está verde en `main`/`dev`.
- Revisar en GitHub branch protection y checks obligatorios.

**Fase 1 — Cerrar P1 (Nivel 1, tests)**
- T-02 matriz de roles; T-01 comportamiento de correo (acordar con B-01); rate limit tras proxy (S-01).
- Test de cabeceras en entorno desplegado (smoke).

**Fase 2 — Cobertura de riesgos conocidos (P2)**
- T-03..T-10 junto con la corrección de los hallazgos correspondientes (B-02, B-03/B-04, B-06, F-03, F-04, F-07). Regla de proceso: cada defecto confirmado en 01–04 entra con su test de regresión (`AGENTS.md`: Nivel 1 exige test de regresión).
- Tests de autenticación del frontend (T-07) y medición de cobertura frontend.

**Fase 3 — Calidad de la suite (P3)**
- Reducir flakiness de E2E: datos propios por test o limpieza, sin dependencia del orden; sustituir `waitForTimeout` por esperas condicionales; job de humo con configuración tipo producción.
- Accesibilidad (axe), carga ligera (k6), navegadores adicionales, tests de migración con datos y de retención/purga.
- Opcional: `pool: 'vmThreads'` en Vitest para acortar la suite; silenciar logs esperados.

Dependencias: las mejoras de T-04 requieren decisión de negocio (Nivel 2, spec aprobada) antes de fijar el comportamiento con tests.

## Aspectos no verificables en esta auditoría

- Resultado de E2E (no ejecutado). Backend: ejecutado el 2026-11-17 (528/528 en verde, ver sección 3).
- Estado de branch protection y de los checks requeridos en GitHub; historial de ejecuciones de CI y tasa de flakiness real.
- Contenido íntegro de los 88 archivos de test (se inventarió y muestreó; no se auditó cada aserción).
- Cobertura frontend (no hay herramienta configurada).
