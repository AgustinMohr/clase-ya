# 08 — Informe Final de Preparación para Producción

- Fecha: 2026-11-17
- Rol: revisión final independiente (release gate).
- Entradas: informes `00` a `06` de `docs/production-readiness/` (no existe `07`; el prompt 07 produce este documento). Los informes anteriores no se tomaron como verdad: se reverificó en el código actual lo que condiciona el veredicto, y se detectaron contradicciones (sección 6).
- Alcance de la verificación: repositorio local (rama `main`, HEAD `52cac65`) más el único cambio posterior a las auditorías (B-01, sin commit). No se accedió a GitHub, Render, Vercel, Neon, Brevo, R2 ni Google. No se desplegó, ni se modificó código de producto, ni configuración externa, ni secretos.
- Convenciones: **VERIFIED** (evidencia en código/ejecución), **PENDING** (sin evidencia o requiere acción manual), **CLOSED** solo con corrección inspeccionada + test de regresión + comportamiento validado. Severidades: P0 bloqueante absoluto; P1 impide el lanzamiento; P2 mejora importante con mitigación; P3 futura. No se rebajó ninguna severidad para facilitar la aprobación (única reclasificación y su justificación: sección 5, F-02).

---

## 1. Resumen ejecutivo

**Veredicto: NO-GO para lanzamiento a producción abierta.**

El producto es técnicamente sólido: identidad siempre desde el `SecurityContext`, DTOs sin entidades, restricciones de integridad en la base, concurrencia cubierta con tests, validadores de arranque que impiden configuraciones inseguras, 528 tests backend en verde (ejecutados hoy), cobertura del 91,2 % de líneas, CSP estricta y cabeceras de seguridad activas en producción (verificado por el propietario). No se encontró ningún P0 de código, ninguna inyección ni XSS y ningún secreto en archivos versionados.

Sin embargo, el criterio GO exige P0/P1 cerrados, configuración de producción verificada, mecanismos de recuperación **demostrados** y flujos críticos probados. Hoy:

- **9 hallazgos P1 siguen abiertos** (sección 4). Siete son de verificación/operación (no de código): no hay evidencia de protección de `main`, de la configuración real de Render, de una restauración probada, de monitorización ni de la entrega real de correo.
- **Hallazgo nuevo de esta auditoría (P1):** `npm audit --audit-level=high` falla hoy con **12 vulnerabilidades altas** (todas por `picomatch` ≤ 4.0.7, GHSA-fc5q-39xc-vjpf, ReDoS, en herramientas de build/test), y `.github/workflows/ci.yml` ejecuta exactamente ese comando. El job `Frontend` de CI estaría fallando con el `package-lock.json` actual. Los informes 03 y 05 daban la CI frontend por correcta basándose en `npm audit --omit=dev` (0 vulnerabilidades en dependencias de producción); esa afirmación era cierta pero insuficiente.
- Hay una contradicción no resuelta entre la evidencia (el enlace de activación apareció en los logs de Render) y los validadores de arranque (rechazan esa bandera fuera de `local`/`test`): o Render corre con perfil `local`/`test`, o con una imagen anterior. Mientras no se descarte, no se puede dar por verificada la configuración de producción.
- Backups: el propietario confirmó 6 h de ventana de restauración y un snapshot manual, pero **no hay prueba de restauración** ni procedimiento. "Recuperación demostrada" es un requisito GO.
- E2E (Playwright) no se ejecutó; no hay staging.

Qué sí se podría hacer: una **beta cerrada** (usuarios invitados, sin tráfico público ni datos reales sensibles) solo por decisión explícita y escrita del propietario, con las condiciones de la sección 8 (bloque "mínimo para beta"). Eso no es un GO de producción.

Tiempo estimado para llegar a GO: bajo si las verificaciones manuales resultan limpias (1–3 días de trabajo operativo más un cambio pequeño de CI); mayor si se descubre perfil `local` en Render o si se decide escanear la evidencia con ClamAV.

---

## 2. Matriz de riesgos

Probabilidad (P): A alta, M media, B baja. Impacto (I): A alto, M medio, B bajo.

| ID | Riesgo | P | I | Sev. | Estado |
|---|---|---|---|---|---|
| G-01 | Render con perfil `local`/`test` o imagen vieja (validadores omitidos, semilla demo) | M | A | P1 | PENDING |
| G-02 | `main` sin protección ni checks obligatorios: despliegue de código sin revisión/CI | M | A | P1 | PENDING |
| G-03 | CI frontend en rojo por `npm audit` (12 high, `picomatch`) | A | M | P1 | VERIFIED (ejecutado hoy) |
| G-04 | Sin restauración probada; PITR solo 6 h; sin RPO/RTO ni rollback documentados | M | A | P1 | PENDING |
| G-05 | Sin monitorización, alertas ni error tracking; fallos silenciosos | A | M–A | P1 | VERIFIED |
| G-06 | Entrega de correo no demostrada (Brevo 401; filas `DEAD` previas; B-01 mitigado, no cerrado) | A | A | P1 | PENDING |
| G-07 | Evidencia documental sin escanear y almacenamiento (`bytea` vs R2) sin decidir/verificar | M | A | P1 | PENDING |
| G-08 | Rate limiting por IP detrás de Vercel→Render sin evidencia de configuración (`BEHIND_TRUSTED_PROXY`) | M | M–A | P1 | PENDING |
| G-09 | Pool/timeouts de conexión para Neon sin configurar (`hikari`, `sslmode`, `statement_timeout`) | M | M–A | P1 | VERIFIED (ausencia en `application.yml`) |
| G-10 | Cuenta ADMIN con anuncio público contactable e ilegible (R-02 de 00) | A | B–M | P1 (según 00) | PENDING (limpieza de datos) |
| G-11 | Documentos subibles en `UNDER_REVIEW`/`VERIFIED`; edición de credencial en revisión (B-03/B-04) | M | M | P2 | VERIFIED (abierto en código) |
| G-12 | Deadlock por orden de bloqueos subida/decisión (B-02) | B–M | M | P2 | PENDING (sin test) |
| G-13 | Lockout de cuenta como DoS; recuperación/reenvío sin límite por email (S-02, S-03) | M | M | P2 | VERIFIED |
| G-14 | Sin SAST, acciones de CI sin SHA, Trivy no bloqueante, imágenes sin versión exacta | B–M | M–A | P2 | VERIFIED |
| G-15 | Sin staging, sin retención/purga de datos, sin política de borrado de cuenta | M | M | P2 | VERIFIED |
| G-16 | Turnstile sin timeout dentro de transacción; JWKS de Google → 400 en vez de 503 (B-06) | B–M | M | P2 | VERIFIED |
| G-17 | Sitemap y GETs públicos sin caché ni límite (B-07) | M | M | P2 | VERIFIED |
| G-18 | `photoUrl` https de host arbitrario: seguimiento de visitantes (F-02) | M | B–M | P2 | VERIFIED |

---

## 3. Estado de cada fase

| Fase | Informe | Resultado | Fiabilidad de la verificación |
|---|---|---|---|
| 00 Baseline y arquitectura | `00-project-baseline.md` | Beta controlada; 5 P1 (Brevo, ADMIN, evidencia sin escanear, rate limit tras proxy, riesgo local→Neon) | Estática; arquitectura confirmada. Algunos riesgos obsoletos (ver sección 6). |
| 01 Seguridad | `01-security-audit.md` | 0 críticos/altos, 4 medios | Estática; **S-10 corregido** (validador sí existe). Sin escaneo de historial git ni de GitHub. |
| 02 Backend/API | `02-backend-audit.md` | 0 P0, 1 P1 (B-01), 6 P2 | Estática + 528 tests en verde hoy. |
| 03 Frontend | `03-frontend-audit.md` | 0 P0, 2 P1, 7 P2 | Estática + 126 tests, `tsc` y `eslint` en verde hoy. **F-01 refutado.** |
| 04 BD e integraciones | `04-database-integrations-audit.md` | 0 P0, 3 P1, 7 P2 | Estática; sin acceso a Neon; sin `EXPLAIN`. |
| 05 Testing | `05-testing-quality-audit.md` | 0 P0, 2 P1, 8 P2 | Backend y Vitest ejecutados; E2E no. |
| 06 Infra/DevSecOps | `06-infrastructure-devsecops-audit.md` | 0 P0, 4 P1, 8 P2 | Estática; paneles no auditados. |
| 07 | (no existe) | — | — |
| 08 Final | este documento | **NO-GO** | Reverificación selectiva en código actual. |

---

## 4. Hallazgos abiertos

Solo los que condicionan el veredicto o requieren decisión. El inventario completo y la evidencia están en cada informe (se indica el ID origen).

### P1 abiertos (impiden el lanzamiento)

| ID | Origen | Hallazgo | Evidencia | Qué cierra el hallazgo |
|---|---|---|---|---|
| G-01 | 06 I-02, 01 S-10 | Contradicción: link de activación visto en logs de Render vs validadores (`ProductionConfigValidator.validateSecurityToggles`, commit `2e83ebc`) que lo prohíben fuera de `local`/`test`. | Código verificado; estado de Render PENDING | Captura de variables de Render sin valores: `SPRING_PROFILES_ACTIVE` vacío, `DEMO_SEED=false`, `AUTH_LOG_ACTIVATION_LINKS`/`MESSAGE_LINK_LOG` false, `CAPTCHA_ENABLED=true`; commit desplegado ≥ `e959e8b`. Si hubo perfil `local`: rotar `JWT_SECRET`, revisar cuentas demo (contraseña `Password123` documentada en `comandos.txt`). |
| G-02 | 06 I-01 | Sin evidencia de protección de `main`, revisiones y checks obligatorios; sin `CODEOWNERS`. | `.github/` solo contiene `workflows/` y `dependabot.yml` | Captura/ruleset: PR obligatorio, 1 aprobación, checks `Backend`/`Frontend`/`E2E` requeridos, sin force-push. |
| G-03 | **Nuevo** | `npm audit --audit-level=high` falla: 12 vulnerabilidades altas (`picomatch` ≤ 4.0.7 → `fdir`, `tinyglobby`, `vite@8.3.3`, `vitest@5.0.5`, `@vitejs/plugin-react`, `typescript-eslint`). Es ReDoS de herramientas de desarrollo (CVSS 7.5 en abstracto), sin presencia en el bundle de producción (`npm audit --omit=dev`: 0). | `npm audit --audit-level=high` ejecutado hoy en `frontend/`; `ci.yml:55` ejecuta el mismo comando; `package-lock.json` sin cambios desde 2026-11-03 | `package.json` con `overrides` de `picomatch` a la versión corregida (ya hay un override de `minimatch`) o actualización de `vite`/`vitest` a versiones con la dependencia corregida; `npm audit --audit-level=high` en verde; job de CI en verde. Los arreglos que sugiere `npm audit fix --force` (bajar `vite` a 6, `vitest` a 3) son regresiones y **no** deben aplicarse. Cambio Nivel 1 (dependencias). Se mantiene P1 porque rompe el gate de CI sobre el que se apoyan G-02 y las demás garantías; si CI no es un check requerido, el riesgo se vuelve G-02. |
| G-04 | 04 DB-01, 06 I-04 | Recuperación no demostrada: restore window de 6 h y snapshot manual (aportados por el propietario), sin prueba de restauración, sin RPO/RTO, sin runbook de rollback, sin backup propio; esquema sin rollback (V17/V18 destructivas). | Informe 04 §8; dato del propietario | Restauración real a un branch de Neon con login/búsqueda/mensajes verificados y tiempos registrados; runbook escrito; política de snapshot antes de cada migración. |
| G-05 | 06 I-03 | Sin uptime externo, error tracking, alertas ni agregador; `/actuator/metrics` solo accesible a ADMIN con cookie. | `application.yml` (`management.endpoints.web.exposure.include: health,metrics`), `SecurityConfig.java:75-76` | Monitor de uptime activo sobre `/actuator/health`, captura de errores en backend y SPA, alerta por filas `DEAD` del outbox y 5xx; prueba de disparo de alerta. |
| G-06 | 02 B-01, 00 R-01 | Entrega de correo no demostrada. Corrección aplicada hoy: `BrevoEmailSender` reintenta 401/403 (backoff 1/5/15/60 min) en lugar de `DEAD` inmediato. **No se cierra**: (a) test solo unitario, falta test de integración con el outbox; (b) tras 5 intentos (~80 min) la fila sigue pasando a `DEAD` y borra el token; (c) la API key de Brevo no está comprobada como válida; (d) filas `DEAD` previas sin revisar; (e) remitente `no-reply@claseya.app` sin dominio verificado (SPF/DKIM pendientes). | `BrevoEmailSender.java:82-92`, `BrevoEmailSenderTest` (2 tests nuevos), `NotificationOutboxWorker.java:119-125` | Correo de activación recibido de extremo a extremo en producción con la API key vigente; consulta de filas `DEAD` vacía o resuelta; test de integración del reintento; verificación de dominio remitente. |
| G-07 | 04 DB-03, 01 S-04, 00 R-03 | Evidencia de identidad/títulos: escáner `Noop` por defecto; el validador permite arrancar en producción con `ALLOW_UNSCANNED_EVIDENCE=true`; modo de almacenamiento (`bytea` en Neon vs R2) y configuración del bucket (privado, cifrado, versioning) sin verificar; sin política de retención. | `application.yml:176-184`, `DocumentContentValidator.java:35` (solo `%PDF-`), `ProductionConfigValidator.validate` | Decisión escrita: ClamAV activo, o aceptación formal del riesgo con evidencia en R2 privado; bucket verificado; política de retención. |
| G-08 | 00 R-04, 01 S-01 | Rate limiting por IP: con `trust-forwarded-headers=false` (valor por defecto) `ClientIpResolver` usa `getRemoteAddr()`, que detrás de Vercel→Render sería la IP del proxy, colapsando todos los usuarios en un solo bucket (o, si se activa, confiando en una cabecera que Vercel debe sanear). | `ClientIpResolver.java:19-30`, `application.yml:85-86,152-153` | Valores efectivos de `BEHIND_TRUSTED_PROXY`/`RATE_LIMIT_TRUST_FORWARDED_HEADERS` en Render y prueba de que dos clientes distintos tienen buckets distintos. **Contradicción de severidad** entre 00 (P1) y 01 (MEDIUM): se mantiene P1 hasta verificarlo. |
| G-09 | 04 DB-02 | Sin `spring.datasource.hikari.*`, `sslmode`, `statement_timeout`/`lock_timeout` documentados para Neon (autosuspend, límite de conexiones, pooler vs directo). | `application.yml:13-16` | Configuración explícita y prueba con el plan real de Neon; confirmar `sslmode=require` en `DB_URL`. |
| G-10 | 00 R-02 | Cuenta con rol `ADMIN` y anuncio público contactable cuyos mensajes no puede leer (403 por diseño en `/api/conversations/**`). | `SecurityConfig.java:94-95`; observado por el propietario | Limpieza de datos de esa cuenta (o cuenta separada) y decisión de producto/UX para que ADMIN no sea contactable. Mitigable por el propietario en minutos; se mantiene P1 por heredar la severidad de 00 y porque afecta un flujo de contacto. |

### P2 abiertos (mitigación documentada, no bloquean por sí solos)

| ID | Origen | Hallazgo | Verificado hoy |
|---|---|---|---|
| G-11 | 02 B-03/B-04 | `VerificationDocumentService.upload` no comprueba el estado de la credencial (solo `delete` lo hace, línea 202); edición de campos congelados solo en `VERIFIED` | Sí, inspección del código actual |
| G-12 | 02 B-02 | Orden de bloqueos inconsistente subida vs decisión; sin test de concurrencia cruzada | Código no cambiado; sin test |
| G-13 | 01 S-02, S-03 | Bloqueo de cuenta por intentos fallidos (DoS) en `AuthService.login`; `forgot-password`, `reset-password`, `resend-activation`, `activate` comparten el bucket `REGISTER_IP` (`RateLimitFilter.java:105-110`) sin límite por destinatario | Sí |
| G-14 | 06 I-05..I-08 | Acciones sin SHA, sin CodeQL, Trivy no bloqueante, imágenes base flotantes | Sí (workflows y `Dockerfile`) |
| G-15 | 04, 06 | Sin staging, retención/purga ni política de borrado/anonimización de cuenta; email único sin normalización en base | Sí |
| G-16 | 02 B-06 | Turnstile sin timeout dentro de transacción; fallo de JWKS devuelve 400 | Sí (informe 02; no reinspeccionado línea a línea) |
| G-17 | 02 B-07 | Sitemap hasta 10.000 perfiles por petición sin caché | Sí (informe 02) |
| G-18 | 03 F-02 | `photoUrl`: el backend impone `https://` y máx. 500 caracteres (`CreateTeacherProfileRequest.java:28-31`), pero admite cualquier host. Visitantes cargan imágenes de terceros sin `referrerPolicy` | Sí. **Reclasificado de P1 a P2**: 03 lo calificó P1 sin haber verificado la validación backend (pendiente en su texto); con https obligatorio queda solo el seguimiento por host arbitrario. No afecta al veredicto. |
| Otros | 01, 03, 04 | Frontend: timeout de 15 s en subidas, reintento de 403, respuestas no JSON, sin cancelación; 04: índices (`unaccent`, ciudad), `scan_status` legado `CLEAN`; 05: sin tests de autenticación frontend, matriz de roles, acceso cruzado a documentos | Según informes |

P3 abiertos: ver cada informe (≈ 30 ítems; ninguno condiciona el lanzamiento).

---

## 5. Hallazgos cerrados

Aplicando el criterio estricto (corrección + inspección + test + comportamiento validado):

| ID | Hallazgo | Cierre | Evidencia |
|---|---|---|---|
| F-01 | Cabeceras de seguridad de Vercel no aplicadas | **CLOSED (refutado)** | `curl -I` de producción aportado: `Content-Security-Policy: frame-ancestors 'none'`, `X-Frame-Options: DENY`, `Strict-Transport-Security: max-age=63072000; includeSubDomains; preload`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy` (dos salidas, 2026-11-17). Matiz: es evidencia del propietario, no reproducida por mí. |
| S-10 (recomendación) | "Impedir con el validador que las banderas de log de enlaces queden activas" | **Ya implementado** en el código (no era un hallazgo de código) | `ProductionConfigValidator.validateSecurityToggles`; tests `ProductionConfigValidatorSecurityTogglesTest`. La **causa** (enlace visto en logs de Render) sigue abierta como G-01. |
| Q-01 (05) | Suite backend no ejecutada | **CLOSED** | `mvn -B clean verify` hoy: 528 tests, 0 fallos/errores/omitidos, `jacoco:check` cumplido, BUILD SUCCESS |
| B-01 | Brevo 401/403 → `DEAD` permanente | **PARCIAL — NO cerrado** (ver G-06) | Cambio aplicado y 2 tests unitarios; falta test de integración, verificación de entrega real y revisión de filas `DEAD` |

No hay otros hallazgos cerrables con el criterio estricto: ningún otro tiene corrección posterior + test + validación.

---

## 6. Contradicciones e inconsistencias entre informes y con el código

| # | Contradicción | Resolución |
|---|---|---|
| 1 | 01/S-10 afirmaba que el validador no bloqueaba las banderas de log; el código sí las bloquea. | Corregido en 01. Pasa a ser la contradicción G-01 (evidencia de logs vs validador). |
| 2 | 03/F-01 (P1 probable) vs verificación de producción. | Refutado; corregido en 03. |
| 3 | 03/F-02 calificado P1 con la validación backend "pendiente"; el backend exige `https://`. | Reclasificado a P2 (sección 4, G-18) con justificación. |
| 4 | S-01 (rate limit tras proxy): P1 en 00, MEDIUM en 01. | Se aplica la más alta (P1) hasta verificar la configuración. |
| 5 | 03 y 05 daban por correcta la CI frontend (`npm audit --omit=dev` = 0) mientras `ci.yml:55` ejecuta `npm audit --audit-level=high` sobre todas las dependencias, que hoy falla. | Nuevo G-03; 03/05 deben leerse con esta corrección. |
| 6 | 00 R-05 (riesgo de operar contra producción desde local) y 04 DB-23: mismo hallazgo con distinta numeración; `comandos.txt` contiene solo marcadores (no secretos), y advierte contra setear `DB_*` en local. | Consolidado: riesgo procedimental, no de código (P2 salvo evidencia de credenciales expuestas). |
| 7 | 00 (docs) cita `codeql.yml` y `guia-proyecto.md` inexistentes; 06 confirma ausencia de CodeQL. | Consistente; deriva documental (P3). |
| 8 | 00 menciona la ruta `/api/test/public` para health check (en `comandos.txt`); no existe en el código. | Deriva documental; verificar la ruta real configurada en Render (parte de G-01). |
| 9 | 02 clasifica B-03/B-04 como P2 pero el flujo de verificación es el modelo de confianza del producto. | Se mantiene P2 por ausencia de explotación demostrada; requiere decisión de negocio (Nivel 2). |

Cambios posteriores a las auditorías (`git status`/`git diff` verificados): solo `BrevoEmailSender.java` y `BrevoEmailSenderTest.java` modificados (+21/−3), sin commit, más los propios informes (sin versionar: `docs/production-readiness/` aparece sin seguimiento). Ningún otro código cambió desde la auditoría 00; las referencias de línea de los informes siguen siendo válidas salvo `BrevoEmailSender.java` (ahora 82-92).

---

## 7. Pruebas ejecutadas

| Prueba | Resultado | Fecha |
|---|---|---|
| `mvn -B clean verify` (Docker activo, Testcontainers postgres:16) | BUILD SUCCESS; 528 tests, 0 fallos/errores/omitidos; `jacoco:check` cumplido; líneas 91,2 %, instrucciones 91,0 %, ramas 76,0 % | 2026-11-17 |
| `mvn -B -q test -Dtest=BrevoEmailSenderTest` | Verde (incluye los 2 tests nuevos de 401/403) | 2026-11-17 |
| `npx vitest run` (`frontend/`) | 30 archivos, 126 tests, todos verdes | 2026-11-17 |
| `npx tsc --noEmit` (`frontend/`) | Sin errores | 2026-11-17 |
| `npm run lint` (`frontend/`) | Sin errores (salida vacía) | 2026-11-17 |
| `npm audit --omit=dev` | 0 vulnerabilidades | 2026-11-17 |
| `npm audit --audit-level=high` (todas las dependencias) | **FALLA: 12 high** (`picomatch`, GHSA-fc5q-39xc-vjpf) | 2026-11-17 |
| `curl -I` a producción (ejecutado por el propietario) | Cabeceras de seguridad presentes | 2026-11-17 |

**No ejecutado:** Playwright/E2E, `npm run build` (incluye `check-csp.mjs`), Trivy, CodeQL, `EXPLAIN`, pruebas de carga, restauración de Neon, prueba real de correo, escaneo de secretos del historial git, revisión de paneles externos. La **fiabilidad de cada afirmación** que depende de estos puntos es PENDING.

---

## 8. Requisitos pendientes

### Mínimo para considerar una **beta cerrada** (decisión del propietario, no es GO)
1. G-01: confirmar variables de Render (sin perfil `local`/`test`, `DEMO_SEED=false`, banderas de log en `false`).
2. G-06: recibir un correo de activación real con la API key vigente; revisar/limpiar filas `DEAD`.
3. G-04 (parcial): restauración de prueba a un branch con el snapshot; snapshot manual antes de cada cambio.
4. G-10: limpiar la cuenta ADMIN con anuncio público.
5. G-03: restablecer el job de CI frontend (override de `picomatch`).
6. Usuarios invitados únicamente; no almacenar evidencia real de terceros hasta resolver G-07.

### Necesarios para **GO**
- Todos los P1 de la sección 4 cerrados con la evidencia indicada.
- E2E ejecutado en verde (Playwright), `npm run build` y CI completa en verde en `main`.
- Matriz de roles y test de acceso cruzado a documentos (05 T-02/T-03), y test de integración del reintento de correo (T-01).
- Runbook de rollback/recuperación probado; RPO/RTO acordados.
- Verificación independiente de GitHub (protección de ramas, secret scanning), Vercel (variables por entorno, previews) y Neon (conexiones, roles, pooler).
- Decisión explícita y registrada sobre los riesgos aceptados (P2 abiertos de la sección 4).

---

## 9. Checklist de lanzamiento

✅ verificado · ⬜ pendiente · ❌ incumple

**Código y pruebas**
- ✅ 528 tests backend verdes; cobertura 91,2 % líneas / 76,0 % ramas.
- ✅ 126 tests frontend, `tsc` y `eslint` verdes.
- ⬜ E2E Playwright ejecutado; `npm run build` ejecutado.
- ❌ `npm audit --audit-level=high` (G-03).
- ⬜ Tests T-01/T-02/T-03 (correo, roles, documentos ajenos).

**Autenticación y autorización**
- ✅ Identidad desde `SecurityContext`; JWT HttpOnly + CSRF; revocación por `session_version`; matriz de roles revisada en `SecurityConfig` (ADMIN sin acceso a conversaciones por diseño).
- ⚠️ Lockout de cuenta explotable (G-13), sin límite por email en recuperación.
- ⬜ Rate limit por IP real tras proxy (G-08).

**Privacidad e integridad**
- ✅ Integridad referencial y constraints fuertes (informe 04).
- ⬜ Evidencia: escáner/almacenamiento decididos (G-07); política de retención y de borrado de cuenta.
- ⬜ Email único normalizado en base; `scan_status` legado.

**Estabilidad y disponibilidad**
- ⬜ Pool/timeouts Neon (G-09); instancia única y arranque en frío conocidos.
- ⬜ Monitorización y alertas (G-05).
- ⬜ Staging.

**Configuración de producción**
- ✅ Cabeceras HTTP de Vercel (propietario).
- ⬜ Variables de Render/Vercel/Neon revisadas (G-01, G-08, G-09).
- ⬜ Secretos: API key de Brevo válida; rotación si hubo exposición; escaneo del historial git.
- ✅ Sin secretos en archivos versionados por inspección (no equivale a escaneo del historial).

**Recuperación**
- ✅ Restore window 6 h + snapshot manual (propietario).
- ❌ Restauración probada, RPO/RTO, runbook, rollback de esquema (G-04).

**Proceso**
- ⬜ Protección de `main`, revisiones, checks obligatorios (G-02).
- ⬜ CodeQL, acciones por SHA, Trivy bloqueante (G-14).

---

## 10. Veredicto

# NO-GO

Razones, en orden de peso:

1. **P1 abiertos:** nueve (G-01…G-09, y G-10 como P1 heredado). El criterio GO exige cero.
2. **Configuración de producción no verificada**, con una contradicción activa (G-01) que podría implicar validadores omitidos en Render.
3. **Recuperación no demostrada** (G-04): solo existen snapshot y ventana de 6 h.
4. **CI en rojo en su gate de dependencias** (G-03), dato nuevo y verificable.
5. **Entrega de correo no demostrada** (G-06), flujo crítico de registro.
6. **Flujos críticos no probados extremo a extremo** en este ciclo (E2E y producción real).

El veredicto pasa a **PENDING** (no a GO) cuando G-01, G-03, G-04 y G-06 estén resueltos con evidencia, y a **GO** solo cuando todos los P1 estén cerrados y se haya registrado la decisión sobre los P2. Una beta cerrada es admisible solo con la autorización explícita del propietario y las condiciones de la sección 8.

## Aspectos no verificables en esta auditoría

Estado real de GitHub, Render, Vercel, Neon, R2, Brevo, Google y Cloudflare; contenido de la base de producción (filas `DEAD`, cuentas demo, `scan_status`); historial de git en busca de secretos; resultados de Playwright, Trivy y CodeQL; comportamiento real del proxy respecto de `X-Forwarded-For`.
