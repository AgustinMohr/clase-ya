# 07 — Remediation Tracker

- Fecha de creación: 2026-11-17
- Fuente: informes `00` a `06` y `08` de `docs/production-readiness/`. Este documento no modifica código ni configuración; solo planifica.
- Regla de estado: **todos los hallazgos comienzan como OPEN.** Ninguno se marca resuelto aquí. Un hallazgo solo pasará a CLOSED con corrección inspeccionada, test de regresión y comportamiento validado (criterio de `08`). Los dos casos que ya tienen evidencia parcial (B-01, F-01) figuran igualmente como OPEN con una nota; ver sección 8.
- Severidad: la de los informes, aplicando la más alta cuando hay contradicción (ver `08`, sección 6). Única diferencia: F-02 quedó como P2 en `08`.
- Esfuerzo (orientativo, no un compromiso): **S** ≈ hasta medio día; **M** ≈ 1–3 días; **L** ≈ más de 3 días o con varias personas/servicios. Son órdenes de magnitud, sin certeza estadística; dependen del acceso a paneles y de decisiones aún no tomadas.
- Nivel de cambio según `AGENTS.md`: **N0** documentación/tooling; **N1** cambio acotado con presupuesto de 3 líneas y test de regresión; **N2** requiere spec aprobada (comportamiento de producto, seguridad, esquema o contrato). **Manual** = acción en un panel externo; **Decisión** = requiere decisión humana previa.

---

## 1. Resumen ejecutivo

El veredicto de `08` es **NO-GO**. El trabajo para llegar a GO es mayormente **verificación y operación**, no reescritura de código:

- **Intervención manual / acceso a servicios (no la puede hacer el agente):** Render, GitHub, Neon, Brevo, R2, Vercel. 11 ítems (RT-01 a RT-07, RT-12, RT-14 parcial, RT-17 parcial, RT-52).
- **Decisiones previas:** almacenamiento y escaneo de evidencia (RT-24), reglas de verificación (RT-28), política de borrado/retención (RT-21, RT-22), idempotencia de escrituras (RT-39), si el ADMIN puede ser contactable (RT-30). Sin estas decisiones no se debe escribir código.
- **Cambios de código pequeños y acotados (N1):** override de `picomatch`, pins de acciones, timeouts, sondas de salud, índices; la mayoría son S/M.
- **Cambios de comportamiento (N2) que necesitan spec:** B-03/B-04, límites por destinatario, evidencia, idempotencia, borrado de cuenta.

Cantidad de ítems: **10 P1 (agrupados en 9 entradas de tracker), 18 P2, 25 P3**, organizados en 11 paquetes de trabajo (WP). Orden recomendado: WP-0 (verificación) → WP-1 (CI/gate) → WP-2 (correo) → WP-3 (recuperación y observabilidad) → WP-4 (seguridad de acceso) → resto.

Lo que **no** se propone: desactivar controles (CSRF, rate limit, validadores, CAPTCHA, escaneo), marcar riesgos como aceptados sin decisión escrita, ni cambios masivos. Cada entrada es independiente y desplegable por separado.

---

## 2. Tabla de hallazgos

Columnas: ID de tracker · origen (ID en informes) · severidad · estado · tipo de intervención · esfuerzo · paquete.

### P1

| ID | Origen | Hallazgo | Sev. | Estado | Intervención | Esf. | WP |
|---|---|---|---|---|---|---|---|
| RT-01 | 08 G-01, 06 I-02, 01 S-10 | Contradicción: enlace de activación en logs de Render vs validadores de producción; estado real de Render sin verificar | P1 | OPEN | Manual (Render) | S | WP-0 |
| RT-02 | 08 G-02, 06 I-01, 00 R-06 | Sin evidencia de protección de `main`, revisiones y checks obligatorios; sin `CODEOWNERS` | P1 | OPEN | Manual (GitHub) + N0 | S | WP-0 |
| RT-03 | 08 G-03 | `npm audit --audit-level=high` falla (12 high, `picomatch` ≤ 4.0.7); job `Frontend` de CI en rojo | P1 | OPEN | N1 | S | WP-1 |
| RT-04 | 08 G-04, 04 DB-01, 06 I-04 | Recuperación no demostrada: sin restauración probada, RPO/RTO, runbook ni rollback de esquema; PITR 6 h | P1 | OPEN | Manual (Neon) + N0 | M | WP-0/3 |
| RT-05 | 08 G-05, 06 I-03 | Sin uptime externo, error tracking ni alertas; métricas inaccesibles desde fuera | P1 | OPEN | Manual + N1/N2 | M | WP-3 |
| RT-06 | 08 G-06, 02 B-01, 00 R-01 | Entrega de correo no demostrada; B-01 mitigado parcialmente; filas `DEAD`; remitente sin verificar | P1 | OPEN | Manual (Brevo) + N1 | M | WP-2 |
| RT-07 | 08 G-07, 04 DB-03, 01 S-04, 00 R-03 | Evidencia sin escanear; almacenamiento (`bytea`/R2) sin decidir ni verificar; sin retención | P1 | OPEN | Decisión + Manual + N2 | L | WP-5 |
| RT-08 | 08 G-08, 00 R-04, 01 S-01 | Rate limiting por IP detrás de Vercel→Render sin configuración verificada | P1 | OPEN | Manual + N1 | M | WP-4 |
| RT-09 | 08 G-09, 04 DB-02, 02 B-08 | Sin Hikari/timeouts/`sslmode`/`statement_timeout` para Neon | P1 | OPEN | Manual (Neon) + N1 | M | WP-3 |
| RT-10 | 08 G-10, 00 R-02 | Cuenta ADMIN con anuncio público contactable e ilegible | P1 | CLOSED (verificado por el propietario, 2026-11-17; ver nota) | Manual (datos) + Decisión | S | WP-0 |

### P2

| ID | Origen | Hallazgo | Sev. | Estado | Intervención | Esf. | WP |
|---|---|---|---|---|---|---|---|
| RT-11 | 06 I-05 | Acciones de GitHub sin fijar por SHA | P2 | OPEN | N1 | S | WP-1 |
| RT-12 | 06 I-06 | Sin SAST/CodeQL | P2 | OPEN | N1 + Manual (activar code scanning) | S | WP-1 |
| RT-13 | 06 I-07, I-08 | Trivy no bloquea ni corre en PR; imágenes base flotantes; sin Dependabot Docker | P2 | OPEN | N1 | M | WP-1 |
| RT-14 | 06 I-10 | Sin sondas liveness/readiness separadas; ruta de health de `comandos.txt` inexistente; DB en health | P2 | OPEN | N1 + Manual (Render) | S | WP-3 |
| RT-15 | 06 I-12 | Logs en texto plano, sin agregador; email del destinatario en logs SMTP | P2 | OPEN | N1 + Manual | S | WP-3 |
| RT-16 | 06 I-11 | Sin entorno de staging | P2 | OPEN | Manual + N0 | L | WP-9 |
| RT-17 | 04 DB-04 | Email único sensible a mayúsculas en base | P2 | OPEN | N2 (migración) | S | WP-6 |
| RT-18 | 04 DB-05 | `scan_status` legado `CLEAN` sin escaneo | P2 | OPEN | Manual (consulta) + N2 | S | WP-6 |
| RT-19 | 02 B-05, 04 DB-16 | Sin retención/purga de outbox, tokens, mensajes | P2 | OPEN | Decisión + N2 | M | WP-6 |
| RT-20 | 04 DB-06, 00 R-10 | Sin política de borrado/anonimización de cuenta | P2 | OPEN | Decisión + N2 | L | WP-6 |
| RT-21 | 04 DB-12..15 | Búsqueda de materias/ciudad/admin sin índice utilizable; sin `EXPLAIN` | P2 | OPEN | Manual (medición) + N2 (migración) | M | WP-6 |
| RT-22 | 02 B-07 | Sitemap hasta 10.000 perfiles por petición sin caché; GET públicos sin límite | P2 | OPEN | N1 | M | WP-7 |
| RT-23 | 01 S-02 | Bloqueo de cuenta por intentos fallidos permite denegar acceso a una víctima | P2 | OPEN | N2 | M | WP-4 |
| RT-24 | 01 S-03 | Recuperación/reenvío/activación comparten bucket `REGISTER_IP` sin límite por destinatario | P2 | OPEN | N2 | M | WP-4 |
| RT-25 | 01 S-05, 03 F-08 | "Human check" no exigido en backend; `HumanGate` bloquea toda la SPA si el backend cae | P2 | OPEN | Decisión + N2 | M | WP-4 |
| RT-26 | 02 B-03, B-04 | Subida de documentos y edición de credencial en `UNDER_REVIEW`/`VERIFIED` | P2 | OPEN | Decisión + N2 | M | WP-7 |
| RT-27 | 02 B-02 | Orden de bloqueos inconsistente subida vs decisión (posible deadlock) | P2 | OPEN | N1 | M | WP-7 |
| RT-28 | 02 B-06 | Turnstile sin timeout dentro de transacción; fallo de JWKS devuelve 400 | P2 | OPEN | N1 | S | WP-7 |
| RT-29 | 01 S-06, 03 F-02 | `photoUrl` https de host arbitrario: seguimiento de visitantes; CSP `img-src https:` | P2 | OPEN | Decisión + N2 | M | WP-8 |
| RT-30 | 03 F-03, F-04, F-07 | Cliente HTTP: timeout 15 s en subidas, reintento de todo 403, respuestas no JSON no controladas | P2 | OPEN | N1 | M | WP-8 |
| RT-31 | 03 F-05 | Timeout sin idempotencia en POST (registro, mensaje, reseña, reporte) | P2 | OPEN | Decisión + N2 | M | WP-8 |
| RT-32 | 03 F-06, F-09 | Sin cancelación de peticiones; sin manejo de offline; errores silenciados con `.catch(() => undefined)` | P2 | OPEN | N1 | M | WP-8 |
| RT-33 | 05 T-02, T-03, T-01 | Sin matriz de roles, sin test de acceso cruzado a documentos, sin test de integración del reintento de correo | P2 | OPEN | N1 | M | WP-10 |
| RT-34 | 05 T-07, T-08 | Sin tests de autenticación en el frontend ni de `api.ts` para los casos nuevos | P2 | OPEN | N1 | M | WP-10 |
| RT-35 | 05 Q-02, Q-03, T-10 | E2E con estado compartido y configuración distinta de producción; sin sesión expirada ni subida lenta | P2 | OPEN | N1 | L | WP-10 |

### P3 (agrupados)

| ID | Origen | Hallazgo | Sev. | Estado | Intervención | Esf. | WP |
|---|---|---|---|---|---|---|---|
| RT-36 | 01 S-07, S-08, S-13, S-14..S-19, 03 V-07/V-08 | Endurecimiento: `style-src 'unsafe-inline'`, CSP solo por `<meta>`, HSTS en la app, `jti` sin revocación individual, otros LOW/INFO de 01 | P3 | OPEN | N1/N2 | L | WP-11 |
| RT-37 | 01 S-11, 00 R-05, 04 DB-23, DB-29 | Credenciales de BD por defecto en `application.yml`; riesgo de operar contra Neon desde local; `sslmode` | P3 | OPEN | N1 + N0 | S | WP-11 |
| RT-38 | 00 R-07 | Nombre público de usuario Google degradado al email | P3 | OPEN | N2 | S | WP-11 |
| RT-39 | 02 B-09..B-17 | Mapeo de `DataIntegrityViolation`, duplicados al menos una vez, sin `@Version`, propiedades duplicadas, un solo perfil de Spring, outbox sin `requestId` | P3 | OPEN | N1/N2 | L | WP-11 |
| RT-40 | 03 F-10..F-17 | Token en URL de activar/restablecer, StrictMode, sesión sin aviso, descarga admin con `<a href>`, componentes grandes, `eslint-disable`, tipos `VITE_*`, `storage` sin `try/catch` | P3 | OPEN | N1 | M | WP-11 |
| RT-41 | 03 §6 | Accesibilidad: `aria-live` en mensajes, `inert` en modales, cierre por `mousedown`, contraste/Safari/Firefox sin medir | P3 | OPEN | N1 | M | WP-11 |
| RT-42 | 04 DB-07..DB-11, DB-17..DB-22 | Agregados de rating sin reconciliar, tabla `notifications` huérfana, CHECK en `verification_decisions`, objetos huérfanos en R2, índices redundantes | P3 | OPEN | N2 | M | WP-11 |
| RT-43 | 05 T-11..T-17, Q-04..Q-10 | Accesibilidad automatizada, carga, contrato front/back, migración con datos, cobertura frontend, esperas por tiempo | P3 | OPEN | N1 | L | WP-10 |
| RT-44 | 06 I-13..I-19, 00 R-09, R-12 | JVM (`ExitOnOutOfMemoryError`), `CODEOWNERS`, archivos de trabajo en la raíz, `SECURITY.md`, deriva documental (`codeql.yml`, `guia-proyecto.md`, versiones de migraciones) | P3 | OPEN | N0/N1 | S | WP-11 |
| RT-45 | 00 R-06 | `main` 38 commits por delante de `dev`: reconciliar flujo de ramas | P3 | OPEN | Manual (git) + Decisión | S | WP-0 |

---

## 3. Priorización

Orden por reducción de riesgo, no por número de ID. Primero lo que desbloquea el veredicto y es barato.

| Orden | Ítem | Por qué va ahí |
|---|---|---|
| 1 | RT-01 (Render) | Determina si los validadores de seguridad están activos en producción; puede invalidar otras conclusiones. Costo: minutos. |
| 2 | RT-03 (CI `picomatch`) | CI en rojo debilita todo gate (RT-02). Cambio mínimo. |
| 3 | RT-02 (protección de `main`) | Sin esto, cualquier corrección posterior puede llegar sin revisión ni CI. |
| 4 | RT-10 (cuenta ADMIN) | Datos de producción incorrectos; resolución inmediata. |
| 5 | RT-04 (restauración + runbook) | Requisito GO; condiciona cualquier migración futura (RT-17, RT-18, RT-21). |
| 6 | RT-06 (correo) | Flujo crítico de registro; depende de Brevo (manual) y completa B-01. |
| 7 | RT-05, RT-09 (observabilidad y conexiones) | Hacen visibles los fallos y estabilizan la base antes de abrir tráfico. |
| 8 | RT-08 (rate limit tras proxy) | Control de abuso real; depende de RT-01 (valores efectivos). |
| 9 | RT-07 (evidencia) | Mayor impacto de privacidad, pero requiere una decisión previa; no bloquear el resto esperándola. |
| 10 | RT-23..RT-25 (cuenta/recuperación/gate) | Reducen DoS y abuso; spec corta. |
| 11 | RT-26..RT-28 (verificación y concurrencia) | Reglas del modelo de confianza; B-03/B-04 requieren decisión. |
| 12 | RT-11..RT-16 (CI/infra restantes) | Higiene de supply chain y operación. |
| 13 | RT-17..RT-22 (datos) | Migraciones; después de RT-04. |
| 14 | RT-29..RT-32 (frontend) | Robustez de cliente. |
| 15 | RT-33..RT-35 (tests) | Se ejecutan junto a cada corrección (regla de `AGENTS.md`: Nivel 1 incluye test de regresión), no al final; aquí van los huecos que no acompañan a un fix. |
| 16 | RT-36..RT-45 (P3) | Backlog. |

No hay P0 abiertos.

---

## 4. Plan de implementación

Cada paquete es una unidad desplegable independiente. Una rama por paquete (o por entrada si es grande), PR a `dev`, CI en verde, sin mezclar paquetes.

### WP-0 — Verificación manual y datos (sin código)

| Paso | Ítem | Acción |
|---|---|---|
| 0.1 | RT-01 | Render → Environment: capturar (sin valores secretos) `SPRING_PROFILES_ACTIVE`, `DEMO_SEED`, `AUTH_LOG_ACTIVATION_LINKS`, `MESSAGE_LINK_LOG`, `CAPTCHA_ENABLED`, `SCAN_ENABLED`/`ALLOW_UNSCANNED_EVIDENCE`, `STORAGE_PROVIDER`, `BEHIND_TRUSTED_PROXY`; commit desplegado; Health Check Path. Si había perfil `local`: rotar `JWT_SECRET`, auditar cuentas demo. |
| 0.2 | RT-02 | GitHub → ruleset de `main`: PR obligatorio, 1 aprobación, checks `Backend`/`Frontend`/`E2E` requeridos, sin force-push, aplicar a administradores; activar secret scanning y push protection. |
| 0.3 | RT-04 | Neon: restauración de prueba a un branch con el snapshot; verificar login, búsqueda, mensajes y `flyway_schema_history`; registrar tiempos (insumo de RPO/RTO); borrar el branch. |
| 0.4 | RT-10 | Resolver la cuenta ADMIN: o bien quitar su anuncio público, o usar una cuenta TEACHER separada; decidir si ADMIN puede ser contactable (afecta RT-30). Procedimiento con SQL revisado (`SELECT` previo, snapshot previo). |
| 0.5 | RT-45 | Decidir y reconciliar `main`/`dev` (merge o `reset` solo con aprobación del propietario). |

### WP-1 — Gate de CI y cadena de suministro

| Paso | Ítem | Acción |
|---|---|---|
| 1.1 | RT-03 | `frontend/package.json` `overrides` para `picomatch` a la versión corregida; regenerar `package-lock.json`; `npm audit --audit-level=high`, `npm run build`, `npm test`. No usar `audit fix --force` (baja `vite`/`vitest` de mayor). |
| 1.2 | RT-11 | Fijar por SHA todos los `uses:`; mantener la etiqueta como comentario; Dependabot ya actualiza Actions. |
| 1.3 | RT-12 | Añadir `codeql.yml` (Java y JavaScript/TypeScript; PR, `main`, semanal). |
| 1.4 | RT-13 | Trivy: `exit-code: 1` para CRITICAL en `main`; ejecutar también en PR que toque `Dockerfile`/`pom.xml`; versión exacta de imágenes base; ecosistema `docker` en Dependabot. |

### WP-2 — Entrega de correo

| Paso | Ítem | Acción |
|---|---|---|
| 2.1 | RT-06 | Manual: API key de Brevo de tipo API (sin espacios; IPs autorizadas desactivadas o las de Render); remitente/dominio verificado con SPF/DKIM/DMARC. |
| 2.2 | RT-06 | Revisar filas `DEAD` en `notification_outbox` (solo lectura) y decidir si se reencolan o se piden reenvíos. |
| 2.3 | RT-06 | Completar B-01: test de integración del reintento con el outbox; decidir política tras agotar reintentos (conservar el token en el payload o requeue manual asistido) y alerta por `DEAD` (depende de RT-05). |

### WP-3 — Recuperación, observabilidad y conexiones

| Paso | Ítem | Acción |
|---|---|---|
| 3.1 | RT-04 | Redactar `docs/runbooks/rollback-and-recovery.md`; política de snapshot antes de cada migración. |
| 3.2 | RT-09 | Hikari (`maximum-pool-size`, `connection-timeout`, `max-lifetime` < inactividad de Neon, `keepalive-time`), `sslmode=require`, `statement_timeout`/`lock_timeout`; endpoint directo para Flyway y decisión pooler/directo para la aplicación. |
| 3.3 | RT-05 | Uptime externo sobre `/actuator/health`; error tracking en backend y SPA; alerta por `DEAD` y 5xx; métricas expuestas a un colector con autenticación adecuada (decisión de diseño). |
| 3.4 | RT-14 | `management.endpoint.health.probes.enabled=true`; liveness sin base de datos, readiness con ella; corregir Health Check Path y `comandos.txt`. |
| 3.5 | RT-15 | `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`; agregador; redactar emails en logs de `SmtpEmailSender`. |

### WP-4 — Acceso y abuso

| Paso | Ítem | Acción |
|---|---|---|
| 4.1 | RT-08 | Con los valores de RT-01: confirmar `BEHIND_TRUSTED_PROXY`/`RATE_LIMIT_TRUST_FORWARDED_HEADERS`; test de integración con `X-Forwarded-For`. |
| 4.2 | RT-23 | Spec: separar bloqueo por cuenta del acceso legítimo (por ejemplo, bloqueo por par cuenta+IP/dispositivo, o desafío CAPTCHA en vez de 429). |
| 4.3 | RT-24 | Spec: bucket propio por endpoint y límite por destinatario (hash de email) para forgot/resend. |
| 4.4 | RT-25 | Decisión: exigir la cookie humana en el backend para rutas sensibles o aceptar el diseño actual; en cualquier caso degradar `HumanGate` ante fallo del backend. |

### WP-5 — Evidencia documental

| Paso | Ítem | Acción |
|---|---|---|
| 5.1 | RT-07 | Decisión escrita: ClamAV activo, o R2 privado con riesgo aceptado formalmente; retención definida. |
| 5.2 | RT-07 | Manual: bucket privado, cifrado, versioning y lifecycle; acceso mínimo para la clave; plan para migrar `bytea` legados. |
| 5.3 | RT-07 | Código (spec): endurecer validación PDF (`/JavaScript`, `/Launch`, estructura) y reconciliación de objetos huérfanos. |

### WP-6 — Datos y migraciones (después de WP-0.3)

Todas las migraciones `V26+`; snapshot previo; nunca editar históricas.

| Paso | Ítem | Acción |
|---|---|---|
| 6.1 | RT-17 | Consulta previa de duplicados (`lower(email)`); migración con CHECK o índice único; test de duplicado. |
| 6.2 | RT-18 | Consulta de `scan_status`; decisión y migración de reclasificación. |
| 6.3 | RT-21 | `EXPLAIN` en un branch de Neon con datos sintéticos; índices solo con evidencia. |
| 6.4 | RT-19, RT-20 | Decisión de retención y de borrado/anonimización; spec y jobs de purga. |

### WP-7 — Correctitud del backend

| Paso | Ítem | Acción |
|---|---|---|
| 7.1 | RT-26 | Decisión de negocio sobre subida/edición por estado; spec; guardas y tests. |
| 7.2 | RT-27 | Unificar el orden de bloqueo (perfil primero, luego credencial) en subida y decisiones; test de concurrencia cruzada. |
| 7.3 | RT-28 | Timeouts en `RestClient` de Turnstile; extraer la llamada de la transacción; 503 en fallo de JWKS. |
| 7.4 | RT-22 | Caché con TTL para el sitemap; límite o caché para GET públicos. |

### WP-8 — Frontend

| Paso | Ítem | Acción |
|---|---|---|
| 8.1 | RT-30 | Timeout específico para multipart; convertir parseo en `ApiError`; reintentar solo fallos CSRF reales. |
| 8.2 | RT-32 | `signal` opcional en `api.*` y uso en búsqueda/mensajes; estados de error visibles en vez de `.catch(() => undefined)`. |
| 8.3 | RT-29 | Decisión (allowlist de hosts o subida propia); mínimo `referrerPolicy="no-referrer"` y ajuste de `img-src`. |
| 8.4 | RT-31 | Decisión sobre clave de idempotencia (contrato de API → N2). |

### WP-9 — Entornos

RT-16: segundo servicio de Render + branch de Neon alimentado desde `dev`; variables propias; sin datos reales.

### WP-10 — Pruebas (acompañan a los paquetes)

RT-33: matriz de roles parametrizada sobre `SecurityConfig`; test de acceso cruzado a documentos; test de integración del reintento. RT-34: tests de `LoginModal`, `RegisterPage`, `ActivateAccountPage`, `ForgotPasswordPage`, `AuthContext`, `ProtectedRoute`, casos nuevos de `api.ts`. RT-35: E2E con sesión expirada y subida lenta, y un job de humo con configuración tipo producción (rate limit y cookie segura activos). RT-43: resto.

### WP-11 — Backlog P3

RT-36..RT-42, RT-44: se toman por oportunidad, sin ventana propia.

---

## 5. Detalle de los hallazgos P1

Formato: causa raíz · componentes · dependencias · solución · archivos probables · riesgos · tests · criterios de aceptación.

### RT-01 — Configuración real de Render (OPEN, P1, S)
- **Causa raíz:** los validadores omiten toda comprobación cuando hay perfil `local`/`test` (`ProductionConfigValidator.isNonProduction`); el enlace de activación apareció en logs de Render, lo que solo es posible con esas condiciones o con una imagen anterior a `2e83ebc`. Sin evidencia del panel.
- **Componentes:** Render (servicio backend), `ProductionConfigValidator`.
- **Dependencias:** bloquea la confirmación de RT-08 y RT-07; habilita cerrar la contradicción de `08`.
- **Solución:** revisión manual (WP-0.1); si hay perfil `local`, retirar y redeployar, rotar `JWT_SECRET`, auditar cuentas demo y `Password123`.
- **Archivos:** ninguno (si procede, documentar en `docs/`).
- **Riesgos:** rotar `JWT_SECRET` invalida todas las sesiones; comunicar.
- **Tests:** no aplica; opcional: `ProductionConfigValidator` fallando si el perfil `local` está activo con `DB_URL` no local (decisión).
- **Aceptación:** capturas de variables sin secretos; arranque sin perfil; ausencia de enlaces en logs tras registrar una cuenta de prueba; sin cuentas demo.

### RT-02 — Protección de `main` (OPEN, P1, S)
- **Causa raíz:** el flujo de ramas está solo documentado (`AGENTS.md`), no aplicado; `main` desplegable por integración git.
- **Componentes:** GitHub, `.github/`.
- **Dependencias:** RT-03 (checks verdes para poder requerirlos).
- **Solución:** ruleset con PR obligatorio, 1 aprobación, checks requeridos, sin force-push; `CODEOWNERS`; secret scanning.
- **Archivos:** `.github/CODEOWNERS` (nuevo).
- **Riesgos:** si un solo mantenedor, la aprobación obligatoria lo bloquea a sí mismo: decidir excepción de administrador documentada.
- **Tests:** intento de push directo rechazado (verificación manual).
- **Aceptación:** captura del ruleset; PR de prueba que no se puede fusionar con CI roja.

### RT-03 — CI frontend en rojo (OPEN, P1, S)
- **Causa raíz:** `picomatch` ≤ 4.0.7 (GHSA-fc5q-39xc-vjpf) en la cadena de `vite@8.3.3`/`vitest@5.0.5`/`typescript-eslint`; `ci.yml:55` audita todas las dependencias con `--audit-level=high`.
- **Componentes:** `frontend/package.json`, `package-lock.json`, `.github/workflows/ci.yml`.
- **Dependencias:** RT-02.
- **Solución:** `overrides` de `picomatch` a la versión corregida (como el existente para `minimatch`) o actualizar `vite`/`vitest` a versiones que la incorporen; regenerar lock.
- **Archivos:** `frontend/package.json`, `frontend/package-lock.json`.
- **Riesgos:** un override puede romper `fdir`/`tinyglobby` si cambia la API (probar build y tests). **No** aplicar la sugerencia `--force` (bajaría `vite` a 6).
- **Tests:** `npm audit --audit-level=high`, `npm test`, `npm run build`, `npm run lint`, E2E.
- **Aceptación:** `npm audit --audit-level=high` sin hallazgos; job `Frontend` verde en CI; sin cambios de comportamiento en la SPA.

### RT-04 — Recuperación no demostrada (OPEN, P1, M)
- **Progreso (2026-11-17, sin cierre):** el propietario realizó un simulacro de restauración a un branch de Neon (punto restaurado 13:09:52, inicio 17:55, fin 18:10, 15 min, resultado correcto) y redactó el runbook `docs/runbooks/rollback-and-recovery.md`. Evidencia aportada por el propietario, no reproducida por el agente. **Pendiente para cerrar:** (1) campos `COMPLETAR` restantes del runbook (nombre de las opciones de rollback de Render y Vercel, estado del `pg_dump` propio y del versioning de R2); (2) RPO objetivo de 24 h sin mecanismo que lo respalde (PITR de 6 h; sin volcado periódico); (3) unidades de RPO/RTO sin explicitar; (4) revisión por una segunda persona (el suplente figura igual que el propietario); (5) ensayo del rollback de aplicación en Render/Vercel.
- **Causa raíz:** dependencia de la ventana de 6 h de Neon sin procedimiento ni prueba; V17/V18 destructivas; sin migraciones de reversión; sin backup propio.
- **Componentes:** Neon, R2 (evidencia), documentación.
- **Dependencias:** previa a RT-17, RT-18, RT-21 y a cualquier migración.
- **Solución:** restauración real a un branch; runbook (Render redeploy, Vercel rollback, restauración de Neon, compatibilidad de versión); snapshot antes de migrar; evaluar `pg_dump` periódico externo y versioning de R2.
- **Archivos:** `docs/runbooks/rollback-and-recovery.md` (nuevo).
- **Riesgos:** restaurar sobre producción sin ventana destruye datos recientes: el runbook debe indicar restaurar a un branch nuevo y reapuntar, no sobrescribir.
- **Tests:** simulacro cronometrado.
- **Aceptación:** simulacro documentado con RPO/RTO medidos y acordados; runbook revisado por una segunda persona.

### RT-05 — Observabilidad (OPEN, P1, M)
- **Causa raíz:** solo logs de texto y contadores internos; `/actuator/metrics` restringido a ADMIN con cookie.
- **Componentes:** backend, SPA, proveedores externos.
- **Dependencias:** RT-14, RT-15; RT-06 (alerta `DEAD`).
- **Solución:** uptime externo; error tracking (backend y SPA, sin PII en eventos); alertas por 5xx, `DEAD` y salud; decidir cómo exponer métricas (autenticación de scraper).
- **Archivos:** `pom.xml`/`application.yml` (según herramienta), `frontend/src/main.tsx` o `ErrorBoundary`, posible `SecurityConfig.java`.
- **Riesgos:** nueva dependencia (justificar según `AGENTS.md`); fuga de PII a un tercero (scrubbing); exponer métricas públicamente (restringir).
- **Tests:** provocar un error controlado y ver la alerta; test de que los eventos no contienen emails/tokens.
- **Aceptación:** alerta recibida ante caída simulada y ante una fila `DEAD`; SPA reporta una excepción de prueba.

### RT-06 — Entrega de correo (OPEN, P1, M)
- **Causa raíz:** 401 de Brevo (API key/IPs) → antes `DEAD` inmediato con borrado de token (`BrevoEmailSender`, `NotificationOutboxWorker`). Cambio aplicado y sin commit: 401/403 reintentables con backoff 1/5/15/60 min.
- **Componentes:** `notification` (sender, worker, outbox), Brevo.
- **Dependencias:** RT-05 (alerta), WP-0 (acceso a Brevo).
- **Solución:** completar: test de integración; política tras agotar reintentos; verificación real; revisar filas `DEAD`; dominio remitente.
- **Archivos:** `BrevoEmailSender.java`, `NotificationOutboxWorker.java`, `NotificationOutboxRepository.java` (si cambia `markDead`), `NotificationOutboxIntegrationTest`.
- **Riesgos:** reintentar 401 durante mucho tiempo con una key revocada genera ruido y cuota; conservar el token más tiempo amplía su exposición en la base (considerar expirar con el TTL del token).
- **Tests:** integración 401→reintento→éxito tras corrección; 400 sigue permanente; token conservado mientras haya intentos.
- **Aceptación:** correo de activación real recibido; sin filas `DEAD` pendientes sin explicar; test de integración en verde.

### RT-07 — Evidencia documental (OPEN, P1, L)
- **Causa raíz:** `NoopDocumentScanner` por defecto, aceptable en producción solo con `ALLOW_UNSCANNED_EVIDENCE=true`; validación de contenido solo por `%PDF-`; `STORAGE_PROVIDER=postgres` por defecto; sin retención.
- **Componentes:** `verification` (servicios, storage), `ProductionConfigValidator`, R2/Neon, ClamAV.
- **Dependencias:** RT-01 (valores reales), RT-04.
- **Solución:** decisión (ClamAV como servicio disponible o riesgo aceptado por escrito con R2 privado); endurecer validación PDF; política de retención/purga; migrar `bytea` legados.
- **Archivos:** `DocumentContentValidator.java`, `ClamAvDocumentScanner.java`, `S3DocumentStorage.java`, `ProductionConfigValidator.java`, `application.yml`, posible migración.
- **Riesgos:** si ClamAV cae, las subidas devuelven 503 (decidir disponibilidad); falsos positivos; costes de hosting de ClamAV.
- **Tests:** EICAR contra ClamAV (entorno de prueba); PDF con `/JavaScript`; arranque de producción sin escáner ni reconocimiento falla.
- **Aceptación:** decisión archivada (ADR); bucket verificado; escaneo activo o excepción firmada; retención definida.

### RT-08 — Rate limiting tras proxy (OPEN, P1, M)
- **Causa raíz:** `ClientIpResolver` usa `getRemoteAddr()` salvo `trust-forwarded-headers=true`; detrás de Vercel→Render la IP vista puede ser la del proxy, agrupando usuarios.
- **Componentes:** `ClientIpResolver`, `RateLimitFilter`, configuración de Render, `vercel.json`.
- **Dependencias:** RT-01.
- **Solución:** determinar qué cabecera sanea realmente cada salto; configurar ambas banderas coherentes; test.
- **Archivos:** `ClientIpResolver.java`, `application.yml`, `ProductionConfigValidator.java`, `RateLimitIntegrationTest`.
- **Riesgos:** confiar en `X-Forwarded-For` sin saneamiento permite evadir límites; no confiar colapsa buckets y bloquea a todos.
- **Tests:** integración con cabeceras; prueba en preview real con dos clientes.
- **Aceptación:** dos IPs distintas → buckets distintos; cliente que falsea `X-Forwarded-For` no evade el límite.

### RT-09 — Conexiones a Neon (OPEN, P1, M)
- **Causa raíz:** solo valores por defecto de HikariCP y del driver; sin `statement_timeout`.
- **Componentes:** `application.yml`, `DB_URL` en Render, Neon.
- **Dependencias:** RT-04 (conocer el plan y límites).
- **Solución:** pool dimensionado al límite del plan, `max-lifetime` menor que el cierre por inactividad, `keepalive`, timeouts; `sslmode=require`; directo para Flyway, decisión sobre pooler para la aplicación (compatibilidad con `FOR UPDATE SKIP LOCKED` y prepared statements).
- **Archivos:** `application.yml`; documentación de variables.
- **Riesgos:** pool demasiado pequeño → 503 por espera; demasiado grande → agota conexiones de Neon; pooler en modo transacción rompe supuestos.
- **Tests:** prueba de carga ligera y de reconexión tras suspensión de Neon.
- **Aceptación:** sin errores de conexión tras autosuspend; configuración documentada.

### RT-10 — Cuenta ADMIN con anuncio público (CLOSED, P1, S)
- **Cierre (2026-11-17):** el propietario ocultó el anuncio con `update teacher_profiles set bio = null` sobre su cuenta ADMIN (snapshot previo) y verificó que ya no aparece en `/buscar` ni responde `GET /api/teachers/{id}`. Evidencia aportada por el propietario; el agente no la reprodujo (sin acceso a producción). Queda fuera de este cierre la decisión de producto/código (N2) de excluir a ADMIN de la visibilidad pública, que se trata junto con RT-29.
- **Causa raíz:** rol cambiado a ADMIN por SQL manteniendo un anuncio publicado; `/api/conversations/**` excluye ADMIN por diseño; visibilidad D1 ignora el rol.
- **Componentes:** datos de producción, `TeacherSpecifications.visibleTeacher`, `SecurityConfig`.
- **Dependencias:** WP-0.
- **Solución:** manual: cuenta ADMIN dedicada sin anuncio y cuenta TEACHER separada; opcionalmente (N2) excluir ADMIN de la visibilidad pública.
- **Archivos:** ninguno (manual); `TeacherSpecifications.java` si se decide excluir.
- **Riesgos:** borrar a mano una cuenta con FKs `RESTRICT` es propenso a error; hacerlo con snapshot y `SELECT` previo.
- **Tests:** si se cambia la visibilidad: test de que un perfil de ADMIN no aparece en búsqueda.
- **Aceptación:** ningún usuario ADMIN visible públicamente o contactable.

---

## 6. Dependencias

```
RT-01 ─┬─> RT-08 (valores reales del proxy)
       └─> RT-07 (config de evidencia)
RT-03 ──> RT-02 (checks verdes antes de requerirlos)
RT-02 ──> todo cambio posterior (revisión obligatoria)
RT-04 ─┬─> RT-17, RT-18, RT-21, RT-19, RT-20 (migraciones requieren restauración probada)
       └─> RT-09 (límites del plan)
RT-05 <── RT-06 (alerta de DEAD), RT-14, RT-15
RT-06 ──> necesita acceso a Brevo (manual) + RT-05 para la alerta
RT-24 (S-03) ──> RT-23 (S-02) comparten diseño de buckets (hacer en la misma spec)
RT-25 ──> RT-08 (ambos tocan RateLimitFilter/ClientIpResolver; no solapar PRs)
RT-26 (B-03/B-04) ──> RT-27 (B-02) mismo flujo de bloqueos; tests de concurrencia comunes
RT-07 ──> RT-18 (scan_status legado), RT-42 (huérfanos en R2)
RT-29 ──> RT-10 (si se decide excluir ADMIN, mismo punto de visibilidad)
RT-35 ──> RT-01/RT-08 (config tipo producción en el job de humo)
RT-30 ──> RT-31 (la idempotencia exige un cliente HTTP ya refactorizado)
```

Orden mínimo de ruta crítica: RT-01 → RT-03 → RT-02 → RT-04 → RT-06 → RT-05/RT-09 → RT-08 → RT-07.

Dependencias externas por paquete: WP-0 (Render, GitHub, Neon), WP-2 (Brevo, DNS del remitente), WP-3 (proveedores de uptime/error tracking), WP-5 (R2, ClamAV), WP-9 (Render, Neon).

---

## 7. Criterios de aceptación

Criterio común a todo cambio de código (de `AGENTS.md` y `08`): declaración de alcance, test de regresión que falla antes y pasa después, `mvn -B clean verify` y (si aplica) `npm test && npm run lint && npm run build` verdes en CI, documentación afectada actualizada, y revisión por una persona. Un ítem solo pasa de OPEN a CLOSED con: (1) corrección inspeccionada, (2) test de regresión, (3) comportamiento validado en un entorno representativo y (4) la evidencia enlazada en este tracker.

Por paquete, criterios específicos de salida:

| WP | Criterio de salida |
|---|---|
| WP-0 | Capturas/registro de Render, GitHub y Neon archivados sin secretos; simulacro de restauración cronometrado; decisión de la cuenta ADMIN ejecutada. |
| WP-1 | CI verde con auditoría de dependencias; acciones por SHA; CodeQL ejecutándose; Trivy bloqueante en `main`. |
| WP-2 | Correo de activación real recibido; test de integración del reintento; sin `DEAD` sin explicación. |
| WP-3 | Runbook probado; alerta recibida ante fallo simulado; pool configurado y verificado tras autosuspend; sondas liveness/readiness. |
| WP-4 | Buckets de rate limit correctos tras proxy; lockout y recuperación con tests de abuso; decisión sobre el gate humano. |
| WP-5 | ADR de almacenamiento/escaneo; evidencia nueva escaneada o excepción firmada; retención definida. |
| WP-6 | Migraciones `V26+` con snapshot previo, tests de migración con datos previos y `EXPLAIN` adjunto para índices. |
| WP-7 | Tests de estado de credencial, concurrencia cruzada y fallos externos (503) en verde. |
| WP-8 | Tests de `api.ts` para los casos nuevos; subida de 10 MB con red lenta sin falso timeout. |
| WP-10 | Matriz de roles, acceso cruzado a documentos y tests de autenticación frontend presentes; E2E de sesión expirada. |

---

## 8. Riesgos residuales

Incluso con todo el plan ejecutado:

1. **Instancia única** de backend y de base: caídas y arranques en frío del plan gratuito siguen existiendo; el rate limit en memoria no escala a varias instancias (requiere rediseño o almacén compartido, decisión arquitectónica no incluida aquí).
2. **Ventana de PITR de 6 h**: corrupciones descubiertas más tarde dependen de snapshots/dumps manuales o periódicos; el RPO real será el del procedimiento elegido, no el de Neon.
3. **Entrega de correo**: depende de un tercero; los reintentos no garantizan entrega si la key o el dominio fallan durante más de ~80 min (o más si se amplía la política).
4. **Evidencia de identidad**: aun con ClamAV y R2 privado persiste el riesgo de datos personales sensibles en terceros; cumplimiento legal (retención, supresión) requiere revisión humana/legal.
5. **Confianza en proveedores**: Vercel, Render, Neon, Brevo, Cloudflare, Google; sin plan de salida ni multi-proveedor.
6. **Modelo de verificación**: la confianza humana de los administradores no se automatiza; B-03/B-04 se resuelven por regla, no eliminan el riesgo de documentación falsa que pasa la revisión.
7. **Tipos de ataque no cubiertos**: DoS volumétrico (depende del proveedor), fuzzing, pruebas de penetración independientes y pruebas de carga reales no están en el plan; se recomienda una revisión externa antes de abrir tráfico masivo.
8. **Cambios en terceros** (advisories nuevos como `picomatch`, cambios de API de Vercel/Render) pueden reabrir el gate de CI en cualquier momento; Dependabot y un monitoreo semanal lo mitigan, no lo eliminan.
9. **Cobertura de pruebas** depende de lo que se añada: el E2E con estado compartido sigue siendo una fuente de flakiness hasta que se rediseñe (RT-35).
10. **Incertidumbre de estimaciones:** los esfuerzos S/M/L son órdenes de magnitud sin base estadística; los ítems con decisión o acceso externo pueden demorarse por motivos ajenos al trabajo técnico.

### Notas sobre hallazgos con evidencia parcial (siguen OPEN)

- **B-01 (RT-06):** el cambio de `BrevoEmailSender` (401/403 reintentables) está en el árbol de trabajo, sin commit, con 2 tests unitarios y la suite completa en verde (528). Sigue OPEN por los puntos de RT-06.
- **F-01:** `curl -I` de producción aportado por el propietario muestra las cabeceras de seguridad; el informe 03 lo marca refutado. Como evidencia no reproducida por el agente, queda como **candidato a cierre** a validar por una segunda persona o con una captura archivada; hasta entonces no se retira del tracker (no tiene ID de remediación porque no requiere trabajo).
- **S-10:** la recomendación de código ya existe (`validateSecurityToggles`); la causa del enlace en los logs sigue en RT-01.
