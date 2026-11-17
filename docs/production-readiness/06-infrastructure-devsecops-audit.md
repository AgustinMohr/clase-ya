# 06 — Auditoría de Infraestructura y DevSecOps

- Fecha: 2026-11-17
- Alcance: `Dockerfile`, `.dockerignore`, `docker-compose.yml`, `.github/` (workflows, Dependabot), `SECURITY.md`, `application.yml`, validadores de arranque, `comandos.txt`, `frontend/vercel.json`, y lo que el propietario verificó durante la sesión.
- Método: lectura estática del repositorio. No se desplegó nada, no se tocó ninguna configuración externa ni secretos, no se accedió a GitHub, Render, Vercel ni Neon. Se ejecutó `mvn -B clean verify` (528 tests en verde) y `npx vitest run` (126 en verde).
- Verificado por el propietario durante la sesión (dato aportado, no reproducido por mí): cabeceras HTTP de Vercel en producción (`curl -I`), ventana de restauración de Neon de 6 horas (tope 1 GB de historial por proyecto) y un snapshot manual de la base.
- Clasificación: **VERIFICADO** (evidencia en repositorio o aportada), **PROBABLE** (deducido), **PENDIENTE** (requiere revisar el panel de GitHub/Render/Vercel/Neon/Brevo).
- Prioridades: P0 bloquea producción; P1 antes de abrir tráfico; P2 corto plazo; P3 mejora.
- Existe una auditoría previa (`docs/audits/2026-11-13-production-infrastructure-report.md`); esta la contrasta con el estado actual y no repite lo ya correcto.

## 1. Resumen ejecutivo

El repositorio tiene bases sólidas de higiene: contenedor multi-etapa con usuario no root, `.dockerignore`, health check, apagado ordenado, validadores de arranque que impiden desplegar en producción con configuraciones inseguras (CAPTCHA, escaneo, semilla demo, banderas de log de enlaces, CSRF, rate limit, cookie segura, proxy de confianza), CI con tests, cobertura con umbral, `npm audit`, revisión de dependencias en PR, escaneo Trivy, Dependabot y `SECURITY.md`. Las cabeceras de seguridad están activas en producción (verificado).

Lo que **no se puede afirmar**: todo lo que vive fuera del repositorio. Protección de ramas, revisiones obligatorias, secret scanning, variables reales de Render y Vercel, alertas, monitorización, rollback y recuperación son **PENDIENTES**. La infraestructura (Render, Neon, Vercel) es de instancia única; hay puntos únicos de fallo y no hay observabilidad externa documentada.

Resultado: **0 P0, 4 P1, 8 P2, 7 P3**.

P1:
- I-01: protección de `main`/revisiones/checks obligatorios sin evidencia.
- I-02: contradicción entre el validador de producción y lo ocurrido en Render (enlaces de activación en logs): hay que revisar `SPRING_PROFILES_ACTIVE` y la versión desplegada.
- I-03: sin monitorización, alertas ni uptime; fallos silenciosos (p. ej. outbox `DEAD`, B-01).
- I-04: instancia única, ventana de restauración de 6 h y sin procedimiento de rollback/recuperación probado.

Veredicto: apto para beta controlada. Antes de abrir tráfico: I-01 a I-04.

## 2. Arquitectura de infraestructura

| Componente | Proveedor | Estado | Evidencia |
|---|---|---|---|
| SPA + proxy `/api` + SEO prerender | Vercel | Cabeceras de seguridad activas | `frontend/vercel.json` (rutas), salida de `curl -I` aportada |
| Backend | Render (Docker, `Dockerfile` en raíz) | Configuración del servicio PENDIENTE | `Dockerfile`, `comandos.txt` (sección 6 de despliegue) |
| Base de datos | Neon (PostgreSQL 16) | Restore window 6 h; snapshot manual hecho | Dato del propietario |
| Evidencia documental | PostgreSQL `bytea` (por defecto) o R2/S3 | PENDIENTE de confirmar cuál | `application.yml` (`STORAGE_PROVIDER`) |
| Email | Brevo API (o SMTP) | API key con 401 (B-01) | informes 00/02 |
| CAPTCHA | Cloudflare Turnstile | Requerido por validador en producción | `ProductionConfigValidator` |
| Login social | Google OAuth | PENDIENTE de origen autorizado exacto | informe 04 |
| CI/CD | GitHub Actions; despliegue por integración git de Render/Vercel (no hay workflow de deploy) | Despliegue automático por rama PENDIENTE | `.github/workflows/*` |

Flujo: navegador → Vercel (estático + `rewrite` a `${BACKEND_URL}/api/*`) → Render (Spring Boot, una instancia) → Neon. Puntos únicos de fallo: la instancia de Render (límites de plan gratuito: suspensión por inactividad y arranque en frío; el `HEALTHCHECK` admite hasta 300 s de `start-period`), la base única de Neon (autosuspend), Brevo y Vercel. El rate limit es en memoria por instancia (ADR-017), por lo que no escala horizontalmente sin cambio de diseño.

Entornos (sección 1 del prompt):

| Entorno | Existe | Evidencia |
|---|---|---|
| Desarrollo local | Sí | perfil `local` (`application-local.yml`, ignorado por Git, con `application-local.example.yml` versionado), `docker-compose.yml` con PostgreSQL 16 |
| Testing | Sí | perfil `test`, Testcontainers, E2E con PostgreSQL de servicio en CI |
| Staging | **No** | no hay perfil, workflow ni documentación de un entorno intermedio |
| Producción | Sí | perfil por defecto (sin perfil activo); los validadores tratan "sin perfil `local`/`test`" como producción |

La configuración se separa por variables de entorno (`application.yml` con valores por defecto de desarrollo). Los previews de Vercel tienen orígenes distintos, por lo que CORS (`FRONTEND_URL` exacto) y Google OAuth no funcionan en previews salvo configuración adicional (PENDIENTE).

## 3. Hallazgos

### P1

**I-01 (P1, PENDIENTE) — Protección de ramas y revisiones sin evidencia.**
`AGENTS.md` declara `main` protegida y `dev` como integración, pero el repositorio no puede demostrarlo. No existe `CODEOWNERS`; no hay evidencia de revisiones obligatorias ni de que los jobs `backend`, `frontend` y `e2e` sean checks requeridos. Consecuencia: un push directo a `main` o un merge con CI en rojo dispara el despliegue por integración git. Contexto: la rama activa local es `main`, `origin/HEAD` apunta a `dev`, y el informe 00 anotó que `main` iba 38 commits por delante de `dev` (deriva de flujo). Verificar en GitHub → Settings → Branches/Rulesets: pull request obligatorio, 1 aprobación, checks `Backend`, `Frontend`, `E2E` requeridos, rama actualizada, sin force-push, administradores incluidos.

**I-02 (P1, PENDIENTE) — El estado real de Render contradice a los validadores.**
`ProductionConfigValidator.validateSecurityToggles` (commit `2e83ebc`) rechaza el arranque con `AUTH_LOG_ACTIVATION_LINKS=true`/`MESSAGE_LINK_LOG=true` fuera de `local`/`test`; también exige `CAPTCHA_ENABLED=true`, escaneo (o `ALLOW_UNSCANNED_EVIDENCE=true`), `DEMO_SEED=false`, CSRF y rate limit. Sin embargo, el propietario vio el enlace de activación en los logs de Render. Hipótesis (sin evidencia aún): (a) Render corre con `SPRING_PROFILES_ACTIVE=local` o `test`, lo que **desactiva todos los validadores** (`isNonProduction` hace `return`) y habilita la semilla demo; (b) la imagen desplegada es anterior a esos commits. Hay que descartar (a): sería una configuración incorrecta grave (cuentas demo con contraseña conocida `Password123`, documentada en `comandos.txt`, CAPTCHA/CSRF/cookie segura sin exigir). Corregí el informe 01 (S-10) que afirmaba lo contrario. Verificación: Render → Environment: `SPRING_PROFILES_ACTIVE`, `DEMO_SEED`, `AUTH_LOG_ACTIVATION_LINKS`, `MESSAGE_LINK_LOG`, `CAPTCHA_ENABLED`; y comprobar el commit desplegado. Si hubo demo seed en producción, revisar cuentas `@demo`/de prueba existentes.

**I-03 (P1, CONFIRMADO) — Sin observabilidad externa.**
Evidencia: logs de texto plano con `requestId` (`application.yml` `logging.pattern.level`), cabecera `X-Request-Id`, contadores Micrometer del outbox (`claseya.notification.outbox.delivered/failed/dead`), `OutboxHealthIndicator`; `/actuator/metrics` solo para ADMIN y con la cookie de sesión, por lo que ningún sistema externo puede leerlas; no hay endpoint Prometheus, ni Sentry/error tracking (backend ni frontend), ni alertas, ni monitor de uptime documentado. Consecuencia: fallos como Brevo 401, outbox `DEAD`, 5xx, agotamiento de conexiones o caída de la instancia solo se descubren por quejas de usuarios (así se descubrió B-01). La retención de logs depende del plan de Render (PENDIENTE).

**I-04 (P1, VERIFICADO parcial) — Disponibilidad y recuperación.**
- Backups: restore window de 6 h (verificado por el propietario) y snapshot manual. Una corrupción descubierta después de 6 h no es recuperable por PITR; el snapshot manual no es periódico. No hay procedimiento escrito ni prueba de restauración realizada (PENDIENTE).
- Rollback de aplicación: no hay documentación. Render permite redeploy de un build anterior y Vercel permite revertir a un deployment anterior (capacidades de la plataforma, no verificadas aquí); el rollback de **esquema** no existe (Flyway sin migraciones de reversión; V17 y V18 fueron destructivas): solo restaurar.
- Instancia única de backend y base única; sin réplicas ni failover.
- Ver informe 04, sección 8.

### P2

| ID | Estado | Hallazgo |
|---|---|---|
| I-05 | VERIFICADO | **Acciones de terceros sin fijar por SHA.** Todos los `uses:` están por etiqueta mutable: `actions/checkout@v4`, `setup-java@v4`, `setup-node@v4`, `upload-artifact@v4`, `dependency-review-action@v4`, `docker/setup-buildx-action@v3`, `docker/build-push-action@v6`, `aquasecurity/trivy-action@v0.35.0`, `github/codeql-action/upload-sarif@v3`, `advanced-security/maven-dependency-submission-action@v4`. Una etiqueta movida en una acción de terceros ejecuta código arbitrario con acceso al `GITHUB_TOKEN`. Esto importa más en `dependency-submission.yml`, que tiene `contents: write`. Dependabot (ecosistema `github-actions` configurado) puede mantener pins por SHA. |
| I-06 | VERIFICADO | **Sin análisis estático (SAST).** No existe `codeql.yml` (la documentación lo cita, R-12 en 00); `upload-sarif` solo recibe resultados de Trivy. Sin Semgrep/SpotBugs/ESLint-security. |
| I-07 | VERIFICADO | **Trivy no bloquea ni corre en PR.** `container-scan.yml` se ejecuta en push a `main`, semanal y manual; usa `severity: CRITICAL,HIGH`, `ignore-unfixed: true` y no define `exit-code`, por lo que solo sube SARIF; no impide un despliegue. Además `main` es la rama que despliega: el escaneo corre después de integrar. |
| I-08 | VERIFICADO | **Imágenes base sin versión exacta ni digest.** `maven:3.9-eclipse-temurin-17` y `eclipse-temurin:17-jre` son etiquetas flotantes; builds no reproducibles. Dependabot no incluye el ecosistema `docker`. La imagen final es JRE completo sobre Ubuntu (más superficie de CVE que una imagen mínima; tamaño no medido). |
| I-09 | VERIFICADO | **Build de imagen sin tests y sin verificar el artefacto.** `mvn -B -q clean package -DskipTests` es correcto si CI ya probó, pero depende de I-01 (que ese commit haya pasado CI). Nombre del JAR fijo (`clase-ya-backend-0.0.1-SNAPSHOT.jar`): cualquier cambio de versión rompe el `COPY`. |
| I-10 | VERIFICADO | **Sondas de salud.** `Dockerfile` define `HEALTHCHECK` con `curl` contra `/actuator/health` (público, `show-details: never`), pero Render usa su propia "Health Check Path" y no el `HEALTHCHECK` de Docker (PROBABLE). `comandos.txt` documenta la ruta `/api/test/public`, que **no existe** en el código (grep sin resultados): deriva de documentación; si Render la tiene configurada así, devolvería 404/401. No hay grupos `liveness`/`readiness` configurados (`management.endpoint.health.probes` ausente) y el health incluye base de datos: una caída de Neon marca la app como DOWN y puede provocar reinicios en bucle (el comentario de `application.yml` lo evita solo para el correo). |
| I-11 | VERIFICADO | **Sin entorno de staging**: las migraciones y los cambios de configuración llegan a producción sin ensayo; no hay forma de probar la restauración ni un deploy de forma aislada. |
| I-12 | VERIFICADO | **Logs en texto plano, sin formato estructurado ni envío a un agregador**; `SmtpEmailSender` registra el email del destinatario; los senders de enlaces escriben el enlace con las banderas de desarrollo (bloqueadas en producción por el validador, ver I-02). |

### P3

- I-13 (VERIFICADO): `JAVA_TOOL_OPTIONS` solo fija la zona horaria; `-XX:MaxRAMPercentage=75` está en `ENTRYPOINT`, sin `-XX:+ExitOnOutOfMemoryError` ni límites de metaspace; con 512 MB de Render Free el margen es estrecho (no medido).
- I-14 (VERIFICADO): sin `CODEOWNERS`, sin plantilla de PR/issue ni política de revisión documentada (ver I-01).
- I-15 (VERIFICADO): `dependency-review` solo en PR; `dependency-submission` solo en `main`; Dependabot apunta a `dev`, y `main` recibe los cambios por release, con demora.
- I-16 (VERIFICADO): archivos de trabajo versionados en la raíz (`comandos.txt`, `review.md`, `review2.md`, `hallazgos-2026-11-11.md`). `comandos.txt` contiene solo marcadores (`<host-neon>`, `<password de Neon>`) y contraseñas de demo; ningún secreto real detectado por inspección, pero documenta cómo configurar Neon y conviene moverlo a `docs/` o ignorarlo. Sin escaneo de secretos del historial (PENDIENTE).
- I-17 (VERIFICADO): `.gitignore` excluye `.env`, `application-local.yml`, `node_modules`, `dist`; `frontend/.env.example` versionado (sin secretos).
- I-18 (VERIFICADO): `SECURITY.md` define canal de reporte privado, pero no plazos de respuesta ni versiones soportadas más allá de "latest main".
- I-19 (PROBABLE): Node 22 en CI, `@types/node` 26 en el frontend y Vite 8; verificar la versión de Node de Vercel (PENDIENTE).

## 4. Riesgos

| Riesgo | Probabilidad | Impacto | Relacionado |
|---|---|---|---|
| Merge a `main` sin revisión ni CI verde despliega código roto o inseguro | Media | Alto | I-01 |
| Render corre con perfil `local`/`test` y validadores desactivados | Baja–media (hay evidencia circunstancial) | Alto | I-02 |
| Fallos de correo, 5xx o caída sin detección | Alta | Medio–alto | I-03, B-01 |
| Corrupción de datos descubierta fuera de las 6 h de PITR | Media | Alto | I-04 |
| Compromiso de una acción de terceros mueve su etiqueta | Baja | Alto | I-05 |
| Vulnerabilidad en dependencia/imagen sin bloqueo | Media | Medio | I-06, I-07, I-08 |
| Reinicios en bucle de Render si Neon se suspende o cae | Media | Medio | I-10 |
| Cambio de esquema destructivo sin ensayo | Media | Alto | I-11, informe 04 |
| Instancia única: caída o arranque en frío de ~200–300 s para el usuario | Alta (plan gratuito) | Medio | arquitectura |

## 5. Configuraciones incorrectas (verificadas en el repositorio)

1. `comandos.txt`: ruta de health check `/api/test/public` inexistente.
2. Acciones y base de contenedores por etiqueta flotante (I-05, I-08).
3. Trivy sin `exit-code` y fuera del flujo de PR (I-07).
4. Documentación que cita `codeql.yml` inexistente (I-06).
5. Raíz del repositorio con archivos de trabajo (I-16).
6. Informe 01 (S-10) afirmaba que el validador no bloqueaba las banderas de log de enlaces; **corregido** en esta auditoría.

Configuraciones correctas verificadas: usuario no root en la imagen, `.dockerignore` que excluye `frontend/`, `docs/`, `.git/`, `target/`; `permissions: contents: read` por defecto en `ci.yml`; `concurrency` con cancelación; `timeout-minutes` en todos los jobs; servicio PostgreSQL del E2E con credenciales exclusivas de CI; `server.shutdown: graceful` con 30 s; `management.endpoint.health.show-details: never`; `/actuator/**` salvo health restringido a ADMIN; Dependabot para Maven, npm y Actions con grupos para dependencias acopladas; `npm audit --audit-level=high` y dependency-review en CI; `npm audit --omit=dev` sin vulnerabilidades (ejecutado); cabeceras de seguridad en producción (aportadas por el propietario).

## 6. Configuraciones pendientes de verificar

Requieren acceso a los paneles. Nada de esto se modificó.

**GitHub:** branch protection/rulesets de `main` y `dev`; checks requeridos; revisiones obligatorias; force-push y borrado de ramas; secret scanning y push protection; alertas Dependabot y su estado; code scanning; permisos por defecto del `GITHUB_TOKEN` (Settings → Actions → "Read repository permissions"); acciones permitidas; secretos del repositorio (no hay secretos propios usados en los workflows revisados: `GITHUB_TOKEN` implícito).

**Render:** `SPRING_PROFILES_ACTIVE` (debe estar vacío), `DEMO_SEED`, banderas `AUTH_LOG_ACTIVATION_LINKS`/`MESSAGE_LINK_LOG`, `CAPTCHA_ENABLED`, `SCAN_ENABLED`/`ALLOW_UNSCANNED_EVIDENCE`, `STORAGE_PROVIDER`, `BEHIND_TRUSTED_PROXY`, `EMAIL_API_KEY`; Health Check Path; auto-deploy y rama; plan (RAM, suspensión); retención de logs; notificaciones de deploy fallido; historial de deploys para rollback.

**Vercel:** rama de producción y despliegues de preview (variables `BACKEND_URL`, `VITE_*` separadas por entorno), protección de previews, dominio y certificado, versión de Node, límite de tamaño de cuerpo para subidas de 10 MB, rollback.

**Neon:** plan, límite de conexiones, endpoint directo vs pooler, roles y acceso de miembros, retención (6 h confirmada), prueba de restauración (ver informe 04, sección 11).

**Brevo / Cloudflare / Google:** según informe 04, sección 11.

## 7. Recomendaciones

| # | Acción | Tipo | Prioridad |
|---|---|---|---|
| 1 | Verificar y activar protección de `main`: PR obligatorio, 1 aprobación, checks `Backend`/`Frontend`/`E2E` requeridos, sin force-push; añadir `CODEOWNERS` | Configuración GitHub (manual) | P1 |
| 2 | Revisar variables de Render (sección 6); si había perfil `local`, corregir, rotar `JWT_SECRET`, auditar cuentas demo | Operativo | P1 |
| 3 | Añadir monitor de uptime externo (p. ej. sobre `/actuator/health`), alerta de errores (Sentry o equivalente) en backend y SPA, alerta por filas `DEAD` del outbox y por 5xx; exponer métricas a un colector con autenticación adecuada | Operativo + código (Nivel 1/2) | P1 |
| 4 | Redactar runbook de rollback y recuperación (Render redeploy, Vercel rollback, restauración de Neon con prueba hecha en un branch); snapshots manuales antes de cada migración; evaluar `pg_dump` programado fuera de Neon por la ventana de 6 h | Documentación + operativo | P1 |
| 5 | Fijar acciones por SHA (Dependabot mantiene los pins), o al menos las de terceros no oficiales (`trivy-action`, `maven-dependency-submission-action`, `docker/*`) | Código CI (Nivel 1) | P2 |
| 6 | Añadir CodeQL (Java y JS/TS) en PR y semanal | Código CI (Nivel 1) | P2 |
| 7 | Trivy con `exit-code: 1` para CRITICAL en `main` y ejecutar también en PR que toque `Dockerfile`/`pom.xml`; añadir ecosistema `docker` a Dependabot | Código CI (Nivel 1) | P2 |
| 8 | Fijar la versión menor de las imágenes base (o digest) y evaluar una imagen JRE reducida | Código (Nivel 1) | P2 |
| 9 | Habilitar `management.endpoint.health.probes.enabled=true` y definir grupos: liveness sin base de datos, readiness con ella; corregir la Health Check Path de Render y la documentación | Configuración + código (Nivel 1) | P2 |
| 10 | Crear un entorno de staging (segundo servicio de Render + branch de Neon) alimentado desde `dev` | Operativo | P2 |
| 11 | Logs estructurados (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`, ya previsto en `application.yml`) y envío a un agregador; redactar emails en logs de SMTP | Configuración + código (Nivel 1) | P2 |
| 12 | Mover `comandos.txt`, `review*.md`, `hallazgos-*.md` a `docs/` o fuera del repo; escanear el historial con `gitleaks`/secret scanning de GitHub | Higiene | P3 |
| 13 | Añadir `-XX:+ExitOnOutOfMemoryError` y revisar memoria con el plan real de Render | Código (Nivel 1) | P3 |
| 14 | Ampliar `SECURITY.md` con plazos de respuesta y versiones soportadas | Documentación | P3 |

Los cambios de comportamiento o de seguridad (3 parcial, 7, 11) siguen las reglas de `AGENTS.md`: Nivel 1 con presupuesto de cambio y test de regresión; ADR o spec si alteran decisiones de arquitectura.

## 8. Checklist de producción

Estado: ✅ verificado, ⬜ pendiente, ⚠️ riesgo abierto.

**Repositorio y CI**
- ✅ CI en `push` a `main`/`dev` y en cada PR; tests, lint, build, `npm audit`, E2E, dependency review.
- ✅ Umbral de cobertura (85 % líneas, 70 % ramas) y 528 tests backend en verde.
- ⬜ Protección de `main`, revisiones obligatorias y checks requeridos (I-01).
- ⬜ Secret scanning + push protection activos; historial escaneado.
- ⚠️ Acciones sin fijar por SHA (I-05); sin CodeQL (I-06); Trivy no bloqueante (I-07).
- ✅ Dependabot (Maven, npm, Actions); ⬜ ecosistema Docker.

**Contenedor y runtime**
- ✅ Multi-etapa, usuario no root, `.dockerignore`, health check, apagado ordenado.
- ⚠️ Imágenes sin versión exacta (I-08); ⬜ tamaño y CVE de la imagen revisados con Trivy (no ejecutado aquí).
- ✅ Validadores de arranque de producción.
- ⬜ Confirmar que Render no usa perfil `local`/`test` (I-02).
- ⬜ Readiness/liveness separados (I-10).

**Configuración y secretos**
- ⬜ Variables de Render y Vercel revisadas (sección 6); `JWT_SECRET` aleatorio ≥ 32 bytes (el validador lo exige).
- ⬜ Credenciales de Neon, Brevo, R2 rotadas si se expusieron; API key de Brevo válida (B-01).
- ✅ `.env` y `application-local.yml` ignorados por Git.

**Frontend/Vercel**
- ✅ HTTPS con HSTS (`max-age=63072000; includeSubDomains; preload`), `X-Frame-Options: DENY`, `frame-ancestors 'none'`, `nosniff`, `Referrer-Policy`, `Permissions-Policy`.
- ⬜ Previews, variables por entorno, dominio, límite de cuerpo en subidas de 10 MB.

**Datos y recuperación**
- ✅ Restore window de 6 h y snapshot manual.
- ⬜ Prueba de restauración en un branch; RPO/RTO acordados; backup propio; versioning de R2.
- ⬜ Runbook de rollback.

**Observabilidad**
- ⬜ Uptime externo, error tracking, alertas, agregador de logs, métricas accesibles (I-03).

## 9. Plan de remediación

**Fase 0 — Verificación manual (1 día, sin código)**
1. Revisar Render (variables, perfil, health check path, plan) y descartar `local`/`test` (I-02).
2. Revisar GitHub: protección de ramas, secret scanning, code scanning, permisos de Actions (I-01).
3. Hacer una restauración de prueba en un branch de Neon con el snapshot y registrar RPO/RTO (I-04).

**Fase 1 — P1 (esta semana)**
4. Activar protección de `main` y añadir `CODEOWNERS`.
5. Monitor de uptime y error tracking; alerta por outbox `DEAD`.
6. Runbook de rollback y recuperación; política de snapshot previo a cada migración.
7. Corregir la API key de Brevo y comprobar la entrega real (B-01 ya reintenta 401/403).

**Fase 2 — P2 (2–4 semanas)**
8. Pins por SHA, CodeQL, Trivy bloqueante + Dependabot Docker, versiones de imagen.
9. Sondas liveness/readiness y logs estructurados.
10. Entorno de staging con branch de Neon.

**Fase 3 — P3**
11. Limpieza de la raíz del repositorio, escaneo de historial, `SECURITY.md`, opciones de JVM, revisión de Node en Vercel.

Dependencias: las fases 1–2 que cambian código CI o aplicación siguen el flujo Nivel 1 de `AGENTS.md` (declaración de cambio, test de regresión, `mvn -B clean verify` en verde); las configuraciones de paneles externos las ejecuta el propietario.

## Aspectos no verificables en esta auditoría

Estado real de GitHub, Render, Vercel, Neon, R2, Brevo y Google; variables de entorno efectivas; vulnerabilidades de la imagen final (Trivy no se ejecutó); historial de Git y de despliegues; plan contratado en cada proveedor y sus límites; comportamiento real de Render ante una caída de la base de datos.
