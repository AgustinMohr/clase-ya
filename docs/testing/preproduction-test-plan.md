# Plan de pruebas de preproducción — ClaseYa

- **Fecha:** 2026-11-13
- **Alcance:** estrategia de pruebas para liberar el MVP (búsqueda, perfiles, registro/login, contacto y mensajería; sin reservas ni pagos).
- **Modo:** análisis read-only. No se implementaron cambios, no se agregaron dependencias y no se ejecutaron suites.
- **Base:** pruebas, configuración y documentación existentes en el repositorio.
- **Objetivo:** definir qué se prueba, con qué herramientas, en qué entorno, con qué prioridad y qué debe estar verde para aprobar el lanzamiento.

> Los conteos de tests de este informe son **estáticos** (anotaciones y bloques `it/test`), no resultados de ejecución. La suite real debe correr en CI antes de usar este plan como evidencia.

---

## 1. Resumen ejecutivo

ClaseYa tiene una base de pruebas fuerte para un MVP:

- Backend con tests unitarios e integración real sobre PostgreSQL 16 vía Testcontainers.
- Frontend con Vitest + Testing Library para lógica y componentes.
- Playwright para flujos E2E sobre el stack real.
- JaCoCo con piso de cobertura, ArchUnit, dependency review y escaneo de contenedor en CI.
- k6 para un smoke de rendimiento de búsqueda y bandeja.

La brecha principal no es “falta de tests” en general: es **falta de pruebas en condiciones de preproducción**. La suite E2E actual corre con rate limiting desactivado, cookies no seguras, email simulado y worker apagado. Eso es correcto para velocidad local, pero no valida el comportamiento real de producción.

Prioridades recomendadas:

1. **P0 — flujos que pueden romper el lanzamiento:** registro → activación → login, login/logout/revocación, búsqueda y perfil visible, contacto y mensajería, CSRF/cookies same-origin, migraciones y restauración de base, roles/IDOR/404, health y configuración de producción.
2. **P1 — calidad percibida y operación:** responsive 360/390/768, teclado y accesibilidad, errores de red y servicios caídos, rate limiting detrás del proxy, carga con dataset realista, uploads a través del proxy, revisión/moderación y storage/escaneo reales.
3. **P2 — mejoras:** regresión visual, cross-browser ampliado, automatización de accesibilidad con axe, pruebas de contrato más amplias, escenarios de carga sostenida.

**Recomendación de gates:**

- **Gate 1 — CI por PR:** unitarios, componentes, integración, E2E críticos, lint, typecheck, build, audit.
- **Gate 2 — Preproducción:** E2E completos sobre un entorno staging con proveedores reales o sandbox, checklist manual P0 y smoke de rendimiento.
- **Gate 3 — Producción:** smoke post-deploy, verificación de health/logs/cookies y rollback probado.

---

## 2. Inventario de pruebas existentes

### 2.1 Backend

Conteo estático actual: **535 anotaciones `@Test`/`@ParameterizedTest`** en `src/test/java`. La documentación
`docs/testing/testing-strategy.md` declara 438; el número quedó desactualizado.

| Área | Cobertura existente | Clases representativas |
|---|---|---|
| Autenticación | Registro, login, cookies, roles, tokens inválidos/expirados, usuario suspendido | `AuthIntegrationTest`, `CsrfIntegrationTest`, `SessionRevocationIntegrationTest` |
| Activación | Token válido, expirado, usado, reenvío, transporte ausente, admin | `AccountActivationIntegrationTest`, `ActivationConcurrencyIntegrationTest` |
| Google OAuth | Nuevo usuario, vinculación por email, cuenta deshabilitada, tokens inválidos | `GoogleAuthIntegrationTest`, `GoogleIdTokenVerifierTest` |
| CAPTCHA | Token reenviado, CAPTCHA inválido no crea cuenta | `CaptchaIntegrationTest`, `TurnstileCaptchaVerifierTest` |
| Rate limiting | Login por IP/cuenta, registro, reenvío, lecturas públicas | `RateLimitIntegrationTest`, tests unitarios en `security/ratelimit` |
| Catálogo y perfiles | CRUD académico, perfil de profesor/estudiante, materias, modalidades, formación | `AcademicDomainIntegrationTest`, `TeacherProfileIntegrationTest`, `StudentProfileIntegrationTest` |
| Búsqueda | Visibilidad D1, filtros, precio, rating, ciudad, geo, paginación, detalle seguro | `TeacherSearchIntegrationTest`, `TeacherCardIntegrationTest`, `DemoSeedIntegrationTest` |
| Disponibilidad | Franjas, solapamientos, exclusión en DB, ownership | `AvailabilityIntegrationTest`, `AvailabilityValidationTest` |
| Favoritos | Alta, baja, duplicados, roles, IDOR, visibilidad | `FavoriteIntegrationTest` |
| Mensajería | Conversación única, primer mensaje, participantes, keyset, no leídos, bloqueo | `MessagingIntegrationTest`, `ConversationConcurrencyIntegrationTest`, `SafetyIntegrationTest` |
| Opiniones | Creación, edición, respuesta del profesor, moderación, agregados | `ReviewIntegrationTest` |
| Verificación | Upload, PDF, cuotas, scan, revisión admin, decisiones, concurrencia | `Verification*IntegrationTest`, `VerificationDocumentService*Test`, `S3DocumentStorageTest` |
| Admin | Directorio, suspender/restaurar, verificación, reportes | `AdminDirectoryIntegrationTest`, `AdminVerificationReviewIntegrationTest` |
| Outbox | Entrega única, backoff, DEAD, links de un solo uso | `NotificationOutboxIntegrationTest` |
| Contratos y errores | Forma de DTOs, `ApiError`, headers, request id, health | `ApiContractIntegrationTest`, `ErrorContractIntegrationTest`, `SecurityHeadersIntegrationTest`, `ActuatorIntegrationTest` |
| Schema | Índices de caminos calientes | `SchemaIndexIntegrationTest` |
| Producción | Fail-fast de configuración, secreto JWT, cuarentena demo | `ProductionConfigValidator*Test`, `JwtSecret*Test`, `DemoData*Test` |
| Arquitectura | Sin ciclos; `teacher` no depende de `availability`/`verification` | `ArchitectureTest` |
| Presupuesto de consultas | Evita N+1 en lookup de usuarios | `UserLookupBudgetIntegrationTest` |

Infraestructura:

- `AbstractPostgresTest`: un contenedor `postgres:16` por JVM, con shutdown hook.
- `AbstractWebIntegrationTest`: `@SpringBootTest` + MockMvc, helpers Bearer, usuarios y JSON.
- `application-test.yml`: worker del outbox apagado; los tests lo ejecutan explícitamente.
- Los tests de integración mockean email, CAPTCHA y links de activación; no tocan proveedores reales.
- JaCoCo exige **85 % de líneas y 70 % de ramas** en `verify` (`pom.xml`).

### 2.2 Frontend

Conteo estático actual: **113 bloques `it/test` de Vitest** en 24 archivos y **45 tests de Playwright** en 16 specs.
La documentación declara 90 y 44; también quedó desactualizada.

| Área | Cobertura existente |
|---|---|
| Transporte HTTP | CSRF, reintento en 403, 429 con `Retry-After`, error de red, links de mensaje |
| Validación | Registro, reset, política de contraseña, confirmación |
| Búsqueda | Error de catálogo, materia inexistente, filtros inválidos, reintento |
| Perfil de profesor | Error persistente, reintento, limpieza al cambiar de profesor |
| Contacto | Bloqueo y rate limit explicados |
| Mensajería | Merge de mensajes, polling, fallo de mark-read, composer bloqueado |
| Admin | Shell, directorio, verificación, estados vacíos |
| SEO | Canonical, indexabilidad, JSON-LD, escaping, CSP hash, robots, handler de Vercel |
| UI | Field accesible, Pagination, Navbar, ErrorBoundary, modales |
| Formato | Precio, rating, iniciales, etiquetas |

Limitaciones:

- Vitest corre en `jsdom`; no es un navegador real.
- Playwright usa **solo Chromium desktop** y un único smoke mobile de 390 px.
- No hay `axe`/Lighthouse automatizado.
- No hay regresión visual.
- No hay proyecto WebKit/Safari ni dispositivos reales.

### 2.3 PostgreSQL y Flyway

- Cada suite de integración arranca PostgreSQL 16 limpio y Flyway aplica `V1..V24`.
- `SchemaIndexIntegrationTest` verifica `idx_messages_unread`, `idx_subjects_active_name` e
  `idx_messages_conversation_created_id`.
- `DemoSeedIntegrationTest` verifica seed idempotente y datos públicos.
- `AvailabilityIntegrationTest` verifica la constraint GiST de solapamiento.
- No hay un test explícito de **upgrade** desde la versión anterior del schema.
- No hay test de **restauración** de backup/PITR.
- No hay test específico de Neon: SSL, `btree_gist`, límite de conexiones o pooler.

### 2.4 CI, calidad y rendimiento

`.github/workflows/ci.yml` ejecuta:

- Backend: `mvn -B clean verify` (unitarios + integración + JaCoCo).
- Frontend: `npm run lint`, `npm test`, `npm run build`, `npm audit --audit-level=high`.
- E2E: Playwright con PostgreSQL service y demo seed.
- Dependency review en PRs.

También existen `container-scan.yml` (Trivy) y `dependency-submission.yml`.
**No hay workflow de deploy**; el despliegue se administra fuera del repositorio.

Rendimiento:

- `scripts/load.ps1` ejecuta k6 (`grafana/k6:2.3.0`) contra `GET /api/teachers` y
  `GET /api/conversations`.
- Presupuesto inicial: `p95 < 800 ms` y `http_req_failed < 1 %`.
- No corre en cada PR; requiere target con dataset (~5.000 profesores).

### 2.5 Observaciones que condicionan la estrategia

1. **La E2E actual no representa producción.** `playwright.config.ts` desactiva rate limit, usa
   `auth.cookie.secure=false`, activa `AUTH_LOG_ACTIVATION_LINKS=true` y apaga el worker del outbox.
   No valida cookies `Secure`, CSRF real, rate limiting ni entrega de emails.
2. **Los tests de integración mockean servicios externos.** Brevo/SMTP, Turnstile, Google y S3/ClamAV
   no se prueban de punta a punta en CI.
3. **Testcontainers no es Neon.** No cubre SSL, extensiones, pooler, latencia ni cuotas del plan.
4. **E2E muta estado compartido.** `fullyParallel=false`, `workers=1`, base demo. Sirve localmente,
   pero en preproducción necesita una rama/base desechable.
5. **Solo Chromium.** El público inicial probablemente use móvil; Safari iOS no está cubierto.
6. **No hay accesibilidad automatizada.** Hay buenas prácticas en componentes, pero no una auditoría
   WCAG repetible.
7. **No hay prueba de upgrade de Flyway.** Se prueba base vacía, no una base existente con datos.
8. **No hay smoke de producción ni de rollback.**

---

## 3. Estrategia propuesta

### 3.1 Pirámide y niveles

| Nivel | Qué prueba | Herramienta existente | Frecuencia |
|---|---|---|---|
| Estático | Tipos, lint, arquitectura, dependencias, imagen | `tsc`, ESLint, ArchUnit, npm audit, Trivy | Cada PR |
| Unitario | Reglas puras, validadores, cálculos, seguridad aislada | JUnit 5, Mockito, AssertJ, Vitest | Cada PR |
| Componente | UI aislada, estados, accesibilidad básica | Vitest + Testing Library + jsdom | Cada PR |
| Integración | API + PostgreSQL + Flyway + seguridad | Spring Boot Test + MockMvc + Testcontainers | Cada PR |
| Contrato | Forma de DTOs y errores entre capas | `ApiContractIntegrationTest` + TypeScript | Cada PR |
| E2E | Flujos reales en navegador | Playwright | Cada PR (críticos) y preproducción (completos) |
| Manual | Exploratorio, accesibilidad, responsive, proveedores reales | Checklist + navegador/dispositivo | Preproducción |
| Carga | Búsqueda y bandeja con dataset realista | k6 | Nightly/pre-release |
| Post-deploy | Salud, login, búsqueda, contacto | Smoke manual/script | Cada deploy |

### 3.2 Entornos

| Entorno | Uso | Datos | Proveedores |
|---|---|---|---|
| Local | Desarrollo y E2E rápidos | Docker PostgreSQL + demo seed | Mock/log |
| CI | Gate por PR | PostgreSQL 16 service/Testcontainers + demo seed | Mock |
| Preproducción | Validación de release | Rama Neon `staging` o base desechable + fixture | Brevo sandbox, Turnstile test keys, Google test client, R2 staging |
| Producción | Smoke post-deploy | Cuenta smoke dedicada, sin datos fake masivos | Reales |

Reglas:

- **Nunca** ejecutar E2E que mutan datos contra producción.
- Preproducción debe usar una rama/base descartable y restaurarse antes de cada corrida completa.
- Los secretos se cargan por variables de entorno; no se escriben en el repo ni en el informe.
- La cuenta smoke de producción se crea una vez, se identifica y se suspende/elimina al terminar.

### 3.3 Datos de prueba

- Demo seed local/CI: suficiente para búsqueda, perfiles y mensajería.
- Fixture de carga: ~5.000 profesores para k6 y `EXPLAIN`.
- Emails únicos por corrida: `e2e-<flujo>-<timestamp>@example.com`.
- PDF sintético válido para uploads; EICAR solo en un entorno controlado para ClamAV.
- Para activación/reset: buzón de prueba dedicado o captura controlada en preproducción.
- No usar cuentas demo documentadas en producción.

### 3.4 Herramientas: qué se usa y qué no

**Se usa lo existente:**

- JUnit 5, MockMvc, Testcontainers, AssertJ, Mockito, ArchUnit, JaCoCo.
- Vitest, jsdom, Testing Library.
- Playwright (Chromium; se puede agregar viewport mobile sin dependencia nueva).
- k6 vía Docker.
- GitHub Actions, npm audit, Trivy.

**No se agrega sin aprobación:**

- Cypress, Selenium, Jest, Storybook, Pact, Artillery, Lighthouse CI.
  Motivo: duplican capacidades ya cubiertas por Playwright/Vitest/k6 o agregan mantenimiento sin un
  beneficio demostrado para el MVP.
- `@axe-core/playwright`: es la única incorporación que recomiendo evaluar. Beneficio: auditoría
  automática de accesibilidad dentro de Playwright, sin runtime de producción. Costo: una dependencia
  de desarrollo y posibles falsos positivos. **No se instala en este plan.**

### 3.5 Prioridades

- **P0:** si falla, no se libera. Afecta autenticación, datos, contacto, visibilidad, migraciones,
  seguridad o disponibilidad.
- **P1:** debe estar verde antes del tráfico público o durante la primera semana; puede tener workaround.
- **P2:** mejora continua; no bloquea el lanzamiento.

### 3.6 Criterios de entrada y salida

**Entrada:**

- Build compila y CI base está verde.
- Docker disponible para Testcontainers.
- Preproducción configurada con variables y proveedores de prueba.
- Datos de prueba cargados y rama/base descartable disponible.

**Salida:**

- Todos los P0 automáticos verdes.
- Checklist manual P0 firmado.
- Sin defectos P0/P1 abiertos.
- Cobertura backend ≥ 85 % líneas / 70 % ramas.
- Carga: `p95 < 800 ms` y fallos < 1 % en search/inbox, o desvío documentado y aceptado.
- Restauración de backup probada.
- Smoke de producción y rollback probados.
- Evidencia archivada: reportes JUnit, JaCoCo, Playwright, k6, capturas y SQL.

---

## 4. Cobertura faltante priorizada

| ID | Brecha | Riesgo | Prioridad | Propuesta |
|---|---|---|---|---|
| G-01 | Flujo completo registro → email → activación → login no está E2E | Un cambio en token/email rompe el alta | P0 | E2E con buzón de prueba en preproducción; mantener integración backend como respaldo |
| G-02 | E2E no prueba cookies `Secure`, CSRF ni rate limit reales | Login/CSRF puede fallar solo en producción | P0 | Smoke same-origin en preproducción con configuración real |
| G-03 | No hay upgrade test de Flyway sobre base existente | Migración puede fallar en el release | P0 | Restaurar rama del release anterior y desplegar la nueva |
| G-04 | No hay prueba de restauración Neon | Sin recuperación verificada | P0 | Restaurar branch a un timestamp y validar login/datos |
| G-05 | No hay E2E de password reset | El usuario no puede recuperar cuenta | P1 | E2E con email de prueba + test backend existente |
| G-06 | No hay E2E de Google real ni CAPTCHA real | Login social/registro puede fallar por configuración | P1 | Manual en preproducción con test keys |
| G-07 | No hay E2E de reporte/bloqueo/opinión/moderación | Flujos de seguridad y confianza sin validar en UI | P1 | E2E de reporte, bloqueo, review y cola admin |
| G-08 | Responsive solo 390 px y dos páginas | Roturas en registro/perfil/mensajes/admin | P1 | Playwright 360/390/768 en flujos críticos |
| G-09 | Sin auditoría de accesibilidad | Exclusión de usuarios y riesgo legal | P1 | Checklist WCAG 2.2 AA + teclado; evaluar axe |
| G-10 | Solo Chromium | Fallos en Safari iOS/Android | P1 | Smoke manual en dispositivo real; opcional WebKit |
| G-11 | Sin pruebas de servicios caídos | 503/errores confusos o pérdida de datos | P1 | Fault injection en preproducción: SMTP, S3, ClamAV, DB |
| G-12 | Sin upload de 10 MB a través de Vercel | 413 oculto en producción | P1 | Prueba manual por el proxy |
| G-13 | Rate limit detrás de proxy no probado | Todos comparten bucket o se saltea | P1 | Preproducción con `X-Forwarded-For` y `TRUST_FORWARDED_HEADERS=true` |
| G-14 | k6 no corre en CI ni con 5k fijo | Regresión de performance | P1 | Nightly contra staging con fixture |
| G-15 | Contratos solo cubren 4 endpoints | Drift FE/BE en otros DTOs | P2 | Extender `ApiContractIntegrationTest` a endpoints críticos |
| G-16 | Sin regresión visual | Cambios de layout no detectados | P2 | Screenshots Playwright en páginas críticas |
| G-17 | Sin E2E de sesión expirada/revocada | Usuario con token viejo | P2 | E2E con token revocado o espera corta |
| G-18 | Sin prueba de imagen externa rota | Perfil con imagen rota | P2 | Mock de URL inválida y fallback de Avatar |

---

## 5. Matriz de pruebas

**Leyenda:**

- **A:** automatizada.
- **M:** manual.
- **H:** híbrida (automatización del núcleo + verificación manual).
- **P0/P1/P2:** prioridad.

### 5.1 Registro y activación

| ID | Caso | Precondiciones | Pasos | Resultado esperado | Tipo | Prioridad |
|---|---|---|---|---|---|---|
| REG-01 | Registro válido de estudiante | Backend con transporte de email configurado o mock | POST `/api/auth/register` con nombre, email nuevo y contraseña válida | `202`, usuario `PENDING`, contraseña BCrypt, evento de activación encolado, sin token en la respuesta | A | P0 |
| REG-02 | Registro de email ya activo | Usuario `ACTIVE` existente | Repetir registro con el mismo email | `202` genérico, sin crear duplicado, sin encolar email | A | P0 |
| REG-03 | Contraseña débil o confirmación distinta | Formulario abierto | Ingresar contraseña corta, sin número o confirmación distinta | Error de validación en español; no se llama a la API o responde `400` | A | P0 |
| REG-04 | Rol no permitido | Backend activo | POST con `role=ADMIN` o rol inválido | `400`; no se crea cuenta | A | P0 |
| REG-05 | CAPTCHA inválido | `CAPTCHA_ENABLED=true` con verificador mock | POST con token inválido o ausente | `400`/`403`; no se crea cuenta | A | P0 |
| REG-06 | Activación con token válido | Cuenta `PENDING` con token vigente | Abrir enlace de activación; luego login | Cuenta `ACTIVE`; login funciona; token de un solo uso | A + M | P0 |
| REG-07 | Token expirado, usado o inválido | Cuenta `PENDING` | Abrir enlace vencido/reutilizado/alterado | `400` genérico; cuenta sigue `PENDING` | A | P0 |
| REG-08 | Reenvío de activación | Cuenta `PENDING` | Solicitar reenvío; usar token anterior y nuevo | Token anterior inválido; nuevo válido; respuesta genérica para email desconocido | A | P1 |
| REG-09 | Registro sin transporte de email | Sin SMTP/Brevo configurado | POST de registro | `503`; no se crea cuenta ni queda estado parcial | A | P0 |
| REG-10 | Flujo completo en navegador | Preproducción con buzón de prueba | Registro en `/registro`; abrir email; activar; login; verificar `/api/auth/me`; logout | Alta completa, sesión válida, logout limpia la vista privada | H | P0 |
| REG-11 | Cuarentena de datos demo | Base con cuentas `@claseya.dev` | Arrancar backend fuera de `local`/`test`; consultar cuentas | Cuentas demo `SUSPENDED`, hash rotado, no visibles públicamente | A + M | P0 |

### 5.2 Login y sesión

| ID | Caso | Precondiciones | Pasos | Resultado esperado | Tipo | Prioridad |
|---|---|---|---|---|---|---|
| LOG-01 | Login con email y contraseña | Usuario `ACTIVE` | POST `/api/auth/login`; inspeccionar respuesta y cookies | `200`; cookie `CLASEYA_SESSION` `HttpOnly`, `Secure`, `SameSite=Lax`; `lastLogin` actualizado | A + M | P0 |
| LOG-02 | Credenciales incorrectas o cuenta no activa | Usuarios `PENDING`/`SUSPENDED`/`INACTIVE` y email inexistente | Intentar login en cada caso | `401` con mensaje genérico; sin enumeración | A | P0 |
| LOG-03 | Logout revoca la sesión | Sesión activa | POST logout; reutilizar cookie/token anterior | Token anterior deja de autenticar; nuevo login funciona | A | P0 |
| LOG-04 | Reset de contraseña revoca sesiones | Sesión activa y token de reset | Resetear contraseña; usar sesión anterior | Sesión anterior inválida; login con nueva contraseña funciona | A | P0 |
| LOG-05 | Token inválido, alterado o expirado | Token JWT vencido/alterado | Acceder a `/api/auth/me` y ruta privada | `401`; no se filtra información | A | P0 |
| LOG-06 | Login con Google | Cliente OAuth de prueba | Login nuevo, vinculación por email, cuenta deshabilitada, token inválido | Comportamiento esperado por caso; sin duplicados; sin tokens persistidos | A + M | P1 |
| LOG-07 | CSRF en métodos unsafe | Cookie de sesión y token CSRF | POST sin `X-XSRF-TOKEN`; POST con token; request Bearer | Sin token: `403`; con token: éxito; Bearer exento | A | P0 |
| LOG-08 | Rate limit de login | Límites configurados | Superar intentos por IP y por cuenta | `429` con `Retry-After`; cuenta bloqueada temporalmente; login exitoso resetea contador | A + M | P0 |
| LOG-09 | Sesión sobrevive reload y protege rutas | Sesión iniciada | Recargar; abrir ruta privada; cerrar sesión; volver atrás | Sesión persiste; ruta privada no queda visible tras logout; redirección correcta | A | P0 |
| LOG-10 | Navegación por rol | Sesiones STUDENT/TEACHER/ADMIN | Revisar navbar y rutas | Cada rol ve solo sus accesos; Admin no aparece para estudiante | A | P1 |

### 5.3 Búsqueda y perfil

| ID | Caso | Precondiciones | Pasos | Resultado esperado | Tipo | Prioridad |
|---|---|---|---|---|---|---|
| SRCH-01 | Solo profesores visibles | Profesores activos/publicados, ocultos e inactivos | GET `/api/teachers` y detalle | Solo aparecen `ACTIVE` con anuncio publicado; oculto = `404` | A | P0 |
| SRCH-02 | Filtros combinados | Dataset con materias, modalidades, ciudad, precio y rating | Aplicar materia + modalidad + ciudad + precio + rating | Intersección correcta, sin duplicados | A | P0 |
| SRCH-03 | Paginación estable | Más de una página | Navegar páginas 1, 2 y volver | Sin repetidos; orden estable; controles deshabilitados en bordes | A | P0 |
| SRCH-04 | Validación de filtros | Backend activo | Enviar precio invertido, rating inválido, página fuera de rango | `400` con mensaje claro; no `500` | A | P1 |
| SRCH-05 | Búsqueda sin resultados y error de catálogo | Red simulada/backend mock | Buscar materia inexistente; simular fallo de catálogo | Estado vacío distinto de error; reintento disponible | A | P1 |
| SRCH-06 | Sugerencias y deep link | Catálogo con materias | Tipear prefijo; elegir sugerencia; recargar URL | Se busca la materia real, no el texto parcial; filtros sobreviven al reload | A | P1 |
| PROF-01 | Perfil público seguro | Profesor visible | GET detalle y revisar DTO | Bio, materias, modalidad, disponibilidad, rating y verificación; sin email/dirección/hash | A | P0 |
| PROF-02 | Perfil oculto | Profesor suspendido o sin anuncio | Abrir URL directa | `404` real; no se renderiza contenido ni JSON-LD con datos | A | P0 |
| PROF-03 | Edición de perfil del profesor | Sesión TEACHER | Editar bio, precio, ciudad, foto HTTPS; limpiar campos | Guarda cambios; URL no HTTPS rechazada; campos se pueden limpiar | A | P1 |
| PROF-04 | Perfil de estudiante requerido para contacto | Estudiante sin perfil o sin nombre visible | Intentar contactar/favorito | `409`; UI abre el formulario de perfil | A | P1 |
| PROF-05 | Favoritos | Sesión STUDENT | Guardar, listar, quitar, repetir | Alta `201`, duplicado `409`, lista propia, sin datos privados | A | P1 |

### 5.4 Contacto y mensajería

| ID | Caso | Precondiciones | Pasos | Resultado esperado | Tipo | Prioridad |
|---|---|---|---|---|---|---|
| CONT-01 | Contacto crea conversación con primer mensaje | Estudiante con perfil y profesor visible | Abrir perfil; Contactar; elegir materia; escribir mensaje; enviar | `201`; conversación creada; mensaje visible; UI abre el hilo | A | P0 |
| CONT-02 | Contacto repetido | Conversación existente | Contactar al mismo profesor otra vez | Misma conversación; mensaje nuevo; sin duplicado | A | P0 |
| CONT-03 | Restricciones de contacto | Profesor, estudiante sin perfil, profesor oculto o bloqueado | Intentar en cada caso | `403`/`409`/`404` según corresponda; sin crear datos | A | P0 |
| CONT-04 | Validación de mensaje | Conversación abierta | Enviar vacío, solo espacios, 257 caracteres, texto con `<script>` | `400` o límite de UI; se preserva texto seguro; no XSS | A | P0 |
| CONT-05 | Hilo y no leídos | Conversación con mensajes de ambos | Abrir hilo; paginar; marcar leído; recargar | Orden cronológico, sin duplicados, contador se limpia | A | P0 |
| CONT-06 | Acceso de no participante | Conversación de otros usuarios | Intentar leer/enviar con otra sesión | `404`; no se revela existencia | A | P0 |
| CONT-07 | Link de contacto por email | Outbox y transporte mock | Crear contacto; capturar link; canjearlo; reutilizarlo | Link de un solo uso; canje crea sesión; reutilización falla | A + M | P1 |
| CONT-08 | UI de contacto y mensajes | Navegador | Probar 403, 429, bloqueo, mensaje largo, Enter/Shift+Enter | Mensajes accionables; sin scroll horizontal; envío correcto | A | P1 |
| CONT-09 | Reporte, bloqueo y opinión | Dos usuarios con conversación | Reportar; bloquear; responder; crear review; moderar | Bloqueo corta contacto/mensajes; reporte entra a cola; review respeta reglas | A + M | P1 |

### 5.5 Admin y verificación

| ID | Caso | Precondiciones | Pasos | Resultado esperado | Tipo | Prioridad |
|---|---|---|---|---|---|---|
| ADM-01 | Directorio de cuentas | Admin y usuarios variados | Filtrar por rol/estado; suspender; restaurar; intentar auto-suspensión | Filtros correctos; sin PII; auto-suspensión bloqueada; suspensión corta sesión | A | P1 |
| ADM-02 | Upload y revisión de evidencia | Profesor con credencial | Subir PDF válido, archivo no PDF, >10 MB, cuota excedida; revisar; decidir | Solo PDF; `400`/`413`; cuota respetada; decisión solo con documentos revisados | A | P1 |
| ADM-03 | Escaneo real | ClamAV en preproducción | Subir PDF limpio, EICAR y detener ClamAV | Limpio guarda `CLEAN`; infectado `400`; caído `503` sin guardar | M | P1 |
| ADM-04 | Storage real | R2/S3 staging | Subir, descargar como dueño/admin, intentar como otro, borrar | Binario privado; descarga autorizada; otro usuario `404`; borrado correcto | M | P1 |
| ADM-05 | Moderación de reportes y opiniones | Admin y casos creados | Listar, resolver, ocultar/restaurar review | Estado y auditoría correctos; review oculta sale de lista y agregado | A + M | P1 |

### 5.6 PostgreSQL y Flyway

| ID | Caso | Precondiciones | Pasos | Resultado esperado | Tipo | Prioridad |
|---|---|---|---|---|---|---|
| DB-01 | Base nueva | PostgreSQL 16 vacío | Arrancar backend | Flyway aplica `V1..V24`; `ddl-auto=validate` pasa; health `UP` | A | P0 |
| DB-02 | Índices de caminos calientes | Migraciones aplicadas | Consultar `pg_indexes` | Existen los índices esperados | A | P0 |
| DB-03 | Constraints críticas | Datos de prueba | Forzar duplicados, sender no participante, ventanas solapadas, review duplicada | La DB rechaza con constraint; el servicio responde `409`/`400` | A | P0 |
| DB-04 | Upgrade desde release anterior | Rama Neon del release previo con datos | Desplegar versión nueva; observar Flyway | Migraciones pendientes aplican; datos preservados; health `UP` | M | P0 |
| DB-05 | Reinicio idempotente | Base ya migrada | Reiniciar backend dos veces | No reaplica migraciones; no hay errores; health `UP` | A + M | P0 |
| DB-06 | Neon real | Rama staging en Neon | Verificar SSL, `btree_gist`, conexiones, latencia | Migraciones aplican; sin error de extensión/SSL; pool estable | M | P0 |
| DB-07 | Restauración | Backup/PITR disponible | Restaurar branch a un timestamp previo; levantar app compatible | Datos y login funcionan; `flyway_schema_history` coherente | M | P0 |
| DB-08 | Cuarentena demo | Base con seed | Arrancar en modo producción | Cuentas demo suspendidas; no aparecen en búsqueda | A + M | P0 |

### 5.7 Integración frontend-backend

| ID | Caso | Precondiciones | Pasos | Resultado esperado | Tipo | Prioridad |
|---|---|---|---|---|---|---|
| INT-01 | Contratos de DTOs críticos | Backend y frontend actuales | Ejecutar tests de contrato de sesión, tarjetas, búsqueda e hilo | Campos y tipos esperados; sin campos privados | A | P1 |
| INT-02 | Mapeo de errores | Backend activo | Provocar 400, 401, 403, 404, 409, 413, 429 y 500 | UI muestra mensaje en español y acción útil; 500 genérico | A | P1 |
| INT-03 | Same-origin y cookies | Preview de Vercel + backend staging | Login, POST con CSRF, reload, logout | Sin CORS; cookie first-party; CSRF funciona | M | P0 |
| INT-04 | Rewrite `/api` y SEO | Preview de Vercel | Abrir `/api/teachers`, `/sitemap.xml`, perfil prerenderizado | Rewrite correcto; HTML con metadata; 404 real para oculto | M | P1 |
| INT-05 | Upload de 10 MB por proxy | Preview + backend | Subir PDF cercano a 10 MB | Sin 413; si falla, registrar y aplicar plan de `ADR-022` | M | P1 |
| INT-06 | CORS y `Origin` | Origen exacto configurado | POST con `Origin` permitido y no permitido; opcional preflight cross-origin directo | Permitido responde; no permitido no recibe CORS; sin wildcard | A + M | P0 |

### 5.8 Responsive y accesibilidad

| ID | Caso | Precondiciones | Pasos | Resultado esperado | Tipo | Prioridad |
|---|---|---|---|---|---|---|
| A11Y-01 | Flujos críticos en 360/390/768 | Playwright o dispositivo | Registro, búsqueda, perfil, contacto, mensajes, legales en tres anchos | Sin scroll horizontal; acciones principales visibles y usables | A + M | P1 |
| A11Y-02 | Teclado completo | Navegador | Tab desde inicio; skip link; modal; combobox; mensajes | Foco visible y orden lógico; Escape cierra; foco vuelve; sin trampas | A + M | P1 |
| A11Y-03 | Nombres accesibles | Navegador/lector | Revisar navbar, iconos, campos, errores y toasts | Todo control tiene nombre; errores anunciados; `aria-live` funciona | A + M | P1 |
| A11Y-04 | Contraste y foco | Herramienta manual/Lighthouse | Revisar texto normal, grande, botones y foco | WCAG AA: 4.5:1 normal, 3:1 grande; foco perceptible | M | P1 |
| A11Y-05 | Zoom y movimiento reducido | Navegador | Zoom 200 %; activar `prefers-reduced-motion` | Sin pérdida de contenido; animaciones reducidas | M | P1 |
| A11Y-06 | Lector de pantalla | NVDA o VoiceOver | Registro, búsqueda, perfil, contacto y mensajes | Landmarks, headings, labels y errores comprensibles | M | P1 |

### 5.9 Errores, resiliencia y rendimiento

| ID | Caso | Precondiciones | Pasos | Resultado esperado | Tipo | Prioridad |
|---|---|---|---|---|---|---|
| ERR-01 | Error 500 seguro | Backend activo | Forzar excepción no controlada | `ApiError` genérico; sin stack/SQL; request id en logs y respuesta | A | P0 |
| ERR-02 | Red caída o timeout | Navegador con red simulada | Cortar red; recargar búsqueda/perfil/mensajes | Estado de error claro; reintento; contenido previo no se pierde | A | P1 |
| ERR-03 | Servicios externos caídos | Preproducción | Detener SMTP/Brevo, S3 y ClamAV por separado | Registro/upload responden `503`; no se guarda estado parcial; health no entra en loop | M | P1 |
| ERR-04 | Rate limit en UI | Límite configurado | Superar contacto/login | `429` con `Retry-After`; mensaje accionable; no se rompe la vista | A + M | P1 |
| ERR-05 | ErrorBoundary | Frontend | Forzar error de render | Pantalla de recuperación; no queda página en blanco | A | P1 |
| ERR-06 | Outbox DEAD | Fila DEAD en preproducción | Consultar health y logs; disparar alerta | Health `DEGRADED`; monitor alerta; no reinicia la app | A + M | P1 |
| PERF-01 | Search/inbox con 5k | Fixture ~5.000 profesores | Ejecutar k6 30–60 s | `p95 < 800 ms`; `http_req_failed < 1 %` | M | P1 |
| PERF-02 | Cold start | Render Free/Starter | Dejar inactivo; medir primer request | Tiempo medido y aceptado; health responde tras arranque | M | P1 |
| PERF-03 | Plan de consulta | Dataset realista | `EXPLAIN (ANALYZE, BUFFERS)` de búsqueda e inbox | Uso de índices esperado; sin sequential scan evitable | M | P1 |
| PERF-04 | Build y bundle | Frontend | `npm run build`; revisar tamaño | Build sin errores; tamaño registrado; CSP válida | A | P2 |

### 5.10 Smoke de producción

| ID | Caso | Precondiciones | Pasos | Resultado esperado | Tipo | Prioridad |
|---|---|---|---|---|---|---|
| SMOKE-01 | Health público | Deploy terminado | GET `/actuator/health` | `200` y `status=UP`; sin detalles | M | P0 |
| SMOKE-02 | Búsqueda y perfil | Datos reales publicados | Buscar materia; abrir perfil; recargar URL | Resultados visibles; perfil correcto; sin 404 inesperado | M | P0 |
| SMOKE-03 | Registro y login | Buzón de prueba | Registro, email, activación, login, logout | Flujo completo; cookie `Secure`; sin errores | M | P0 |
| SMOKE-04 | Contacto y mensaje | Cuenta estudiante y profesor reales de prueba | Contactar; enviar mensaje; responder | Conversación y mensajes funcionan; email llega | M | P0 |
| SMOKE-05 | Upload de evidencia | Profesor de prueba | Subir PDF pequeño y uno cercano al límite | Upload/descarga funcionan; sin 413 ni 500 | M | P1 |
| SMOKE-06 | SEO y rewrite | Dominio productivo | GET `/sitemap.xml`, `/robots.txt`, perfil prerenderizado | Respuestas correctas; canonical y metadata coherentes | M | P1 |
| SMOKE-07 | Logs y secretos | Deploy con tráfico | Revisar logs de Render/Vercel | Sin tokens, contraseñas, emails completos ni links de activación | M | P0 |
| SMOKE-08 | Rollback | Release anterior identificada | Simular rollback de app y DB en staging | App vuelve; DB compatible; health y login funcionan | M | P0 |

---

## 6. Flujos completos exigidos

### 6.1 Registro completo

1. Abrir `/registro`.
2. Completar nombre, email único y contraseña válida; aceptar consentimiento.
3. Enviar formulario.
4. Verificar pantalla “Revisá tu email” y que la cuenta quede `PENDING`.
5. Abrir el email de activación en un buzón de prueba.
6. Abrir el enlace; verificar activación exitosa.
7. Iniciar sesión con las credenciales creadas.
8. Verificar `/api/auth/me` y la vista de sesión.
9. Cerrar sesión y confirmar que la vista privada desaparece.

**Cobertura:** REG-06, REG-10, LOG-01, LOG-03.

### 6.2 Inicio de sesión completo

1. Abrir el modal de ingreso desde la landing.
2. Ingresar email y contraseña.
3. Verificar cookie `HttpOnly`, `Secure`, `SameSite=Lax` y `XSRF-TOKEN`.
4. Recargar la página y confirmar que la sesión persiste.
5. Abrir una ruta privada.
6. Cerrar sesión.
7. Usar “atrás” del navegador.
8. Confirmar que la ruta privada no se muestra.

**Cobertura:** LOG-01, LOG-03, LOG-09.

### 6.3 Búsqueda completa

1. Desde la landing, buscar una materia.
2. Usar una sugerencia del catálogo.
3. Verificar resultados y contador.
4. Aplicar modalidad, ciudad, precio y rating.
5. Navegar a la página 2 y volver.
6. Recargar la URL y compartirla.
7. Probar una materia sin resultados.
8. Simular fallo de catálogo y usar “Reintentar”.

**Cobertura:** SRCH-01 a SRCH-06.

### 6.4 Perfil completo

1. Abrir una tarjeta de profesor.
2. Verificar nombre, foto, bio, materias, modalidades, precio, ciudad, disponibilidad, formación y verificación.
3. Verificar que no se expongan email, dirección, coordenadas ni datos internos.
4. Guardar/quitar favorito.
5. Abrir el modal de contacto.
6. Si el estudiante no tiene perfil, completar el formulario y volver al contacto.
7. Abrir una URL de profesor oculto y confirmar `404`.

**Cobertura:** PROF-01 a PROF-05, CONT-03.

### 6.5 Contacto completo

1. Estudiante con perfil abre un profesor visible.
2. Presiona “Contactar”.
3. Elige una materia real del profesor.
4. Escribe el primer mensaje y envía.
5. Verifica que se cree una conversación y el mensaje aparezca.
6. Contacta de nuevo al mismo profesor y verifica que se reutilice la conversación.
7. Profesor recibe email con link de un solo uso.
8. Profesor abre el link y responde.
9. Estudiante ve la respuesta por polling.
10. Marca como leído.
11. Reporta/bloquea y verifica que el contacto se corte.

**Cobertura:** CONT-01 a CONT-09.

---

## 7. Ejecución y gates

### 7.1 Por pull request

1. `npm run lint`
2. `npm test`
3. `npm run build`
4. `npm audit --audit-level=high`
5. `mvn -B clean verify`
6. Playwright crítico (`@critical`) en Chromium
7. ArchUnit, JaCoCo y dependency review

**Bloquea merge:** cualquier P0 automatizado, lint, typecheck, build o auditoría de severidad alta.

### 7.2 Nightly / pre-release

1. Suite E2E completa.
2. k6 contra staging con fixture de ~5.000 profesores.
3. Tests de integración completos.
4. Smoke de accesibilidad manual y responsive.
5. Backup/restore de Neon en un branch de prueba.
6. Revisión de logs y outbox.

### 7.3 Preproducción antes de liberar

1. Desplegar el release candidato en staging.
2. Ejecutar todos los P0 automáticos y manuales.
3. Ejecutar flujos completos de registro, login, búsqueda, perfil y contacto.
4. Validar proveedores reales: Brevo, Google, Turnstile, R2, ClamAV.
5. Validar rate limit, cookies, CSRF y CORS con configuración de producción.
6. Validar upload de 10 MB por el proxy.
7. Validar restauración de backup.
8. Firmar checklist y registrar evidencia.

### 7.4 Post-deploy

1. Health.
2. Búsqueda y perfil.
3. Registro/activación/login.
4. Contacto/mensaje.
5. Cookies y logs.
6. Rollback disponible.
7. Monitoreo durante 60 minutos.

---

## 8. Evidencia y artefactos

Guardar como artefactos de CI o en el registro de release:

- Reportes JUnit XML y surefire.
- Reporte JaCoCo HTML/XML.
- Reporte Playwright, trazas y capturas de fallos.
- Resumen k6 con `p95`, `p99` y `http_req_failed`.
- Capturas de 360/390/768 px.
- Checklist manual firmado.
- Resultado de restauración de Neon.
- Consultas SQL de verificación: `flyway_schema_history`, cuentas demo, outbox, documentos.
- Logs con `requestId` de los smoke tests, sin secretos.
- Resultado del rollback.

---

## 9. Decisiones que requieren aprobación

1. **Entorno de preproducción:** crear una rama Neon `staging`, un servicio Render staging y un preview de Vercel, o reutilizar un entorno existente. Sin esto, los P0 de integración real quedan manuales sobre producción, lo cual no se recomienda.
2. **Buzón de prueba:** definir el email dedicado para activación/reset/contacto. No usar cuentas reales de usuarios.
3. **Accesibilidad automatizada:** aprobar o rechazar `@axe-core/playwright`. Si se rechaza, el plan queda con checklist manual WCAG 2.2 AA.
4. **Cross-browser:** definir si Safari iOS es requisito P0/P1 para el lanzamiento en Santa Fe. Si sí, agregar smoke manual en dispositivo real o proyecto WebKit.
5. **Umbral de performance:** confirmar `p95 < 800 ms` y fallos < 1 % para el dataset inicial, o ajustar el presupuesto.
6. **Datos de carga:** confirmar si se usará el fixture de ~5.000 profesores y dónde se aloja.
7. **Alcance de verificación documental:** si el MVP incluye uploads, ClamAV y R2 pasan a P0; si no, se puede diferir con aceptación explícita.
8. **Cuenta smoke de producción:** definir si se crea una cuenta dedicada y cómo se limpia después.

---

## 10. Recomendación final

No hace falta reemplazar la estrategia de testing: la base es sólida y las herramientas ya están elegidas. Hace falta **cerrar la brecha entre “tests verdes en local/CI” y “comportamiento real en preproducción”**.

Orden recomendado:

1. Completar P0 de autenticación, búsqueda, perfil y contacto en preproducción.
2. Validar Flyway sobre una base existente y probar restauración de Neon.
3. Validar same-origin, cookies, CSRF, CORS y rate limit con configuración real.
4. Agregar responsive 360/390/768 y teclado para los flujos críticos.
5. Ejecutar k6 con el dataset realista y registrar resultados.
6. Probar servicios externos reales y uploads por el proxy.
7. Recién entonces abrir tráfico público.

**No se implementó ningún cambio. El plan queda pendiente de aprobación.**
