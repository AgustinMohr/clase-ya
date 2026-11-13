# Plan de implementación — endurecimiento para producción

- **Fecha:** 2026-11-13
- **Estado:** en ejecución
- **Rama de trabajo:** `fix/mvp-functional-ux`
- **Destino de producción:** `main`
- **Fuente:** consolidación de `docs/audits/2026-11-13-production-readiness.md`, `docs/audits/2026-11-13-security-privacy.md`, `docs/audits/2026-11-13-mvp-functional-validation.md`, `docs/audits/2026-11-13-production-infrastructure-report.md` y `docs/testing/preproduction-test-plan.md`.
- **Alcance:** bloqueantes P0/P1 para abrir tráfico público, con foco en seguridad, release, base de datos, storage, observabilidad y pruebas.

---

## 1. Estado actual

### 1.1 Git y release

- HEAD local: `main`
- `main` está pusheado a `origin/main` en `c1cc4c0`.
- Render está desplegando `main` y el backend arrancó correctamente.
- La rama `fix/mvp-functional-ux` quedó como referencia histórica de los primeros 7 commits.
- La rama `fix/production-hardening` aportó los commits `ff3bced` y `c1cc4c0`, ya integrados en `main`.

### 1.2 Commits realizados

| Commit | Cambio | Estado |
|---|---|---|
| `2e83ebc` | Fail-fast para `AUTH_LOG_ACTIVATION_LINKS`, `MESSAGE_LINK_LOG`, CSRF y rate limit | Hecho |
| `b7eb71b` | Elimina el token de `PASSWORD_RESET` del outbox al pasar a `SENT`/`DEAD` | Hecho |
| `e959e8b` | Valida coherencia `BEHIND_TRUSTED_PROXY` ↔ `RATE_LIMIT_TRUST_FORWARDED_HEADERS` | Hecho |
| `65e3cdc` | Redacta el email destinatario de los logs de fallo SMTP | Hecho |
| `b45c06e` | Agrega `Cache-Control: no-store` a respuestas `/api/auth/**` | Hecho |
| `9170cb6` | Selecciona explícitamente el constructor de `S3DocumentStorage` | Hecho |
| `53d3ec8` | Desactiva `chunkedEncodingEnabled` para uploads a R2 | Hecho |
| `ff3bced` | Opiniones públicas muestran solo el nombre de pila; admin conserva el nombre completo | Hecho |
| `c1cc4c0` | `server.shutdown=graceful` y timeout de 30 s | Hecho |

### 1.3 Validación ejecutada

- `ProductionConfigValidatorTest`, `ProductionConfigValidatorDeploymentTest`, `ProductionConfigValidatorSecurityTogglesTest` y `ProductionConfigValidatorTrustedProxyTest`: **36 tests en verde**.
- `SmtpEmailSenderTest`: **6/6**.
- `AuthCacheControlFilterTest`: **2/2**.
- `S3DocumentStorageStartupTest`: **1/1**.
- `S3DocumentStorageTest`: **5/5**.
- `ReviewResponseTest`: **4/4**.
- `NotificationOutboxIntegrationTest`: **no ejecutado**. Testcontainers no pudo conectarse a Docker (`tcp://127.0.0.1:2375` rechazó la conexión). Hay que levantar Docker y correrlo antes de cerrar SEC-02.
- No se ejecutó la suite completa.
- Render arrancó el backend con la configuración actual.

### 1.4 Cambios operativos ya hechos

- Credencial de Neon rotada por el usuario.
- `.env.local` raíz eliminado.
- Render arranca con `STORAGE_PROVIDER=s3`; falta probar upload/download contra R2.
- Render desplegando `main`.
- Persisten archivos locales no relacionados en el working tree; no se commitearon.

---

## 2. Estado del merge a `main`

**Resuelto.** `main` fue actualizado y pusheado a `origin/main`; Render redeployó y el backend arrancó. La regla sigue siendo: Render construye `main`, así que todo cambio futuro debe integrarse ahí, no solo a una rama de trabajo.

Antes del deploy se cargó `BEHIND_TRUSTED_PROXY=true`; sin eso el fail-fast bloquea el arranque cuando `RATE_LIMIT_TRUST_FORWARDED_HEADERS=true`.

---

## 3. Plan por fases

### Fase 0 — Contención y release (P0)

| ID | Tarea | Estado | Tipo | Dependencia |
|---|---|---|---|---|
| SEC-01 | Rotar credencial de Neon, guardarla en secret manager y borrar `.env.local` | Hecho por el usuario | Manual | Acceso Neon |
| CFG-01 | Fail-fast de log-links, CSRF, rate limit y proxy confiable | Hecho y en `main` | Código | — |
| SEC-02 | Quitar token de `PASSWORD_RESET` del outbox terminal | Hecho; falta correr test de integración | Código | Docker |
| SEC-04 | Redactar PII en logs SMTP y `no-store` en auth | Parcial: quedan tokens en query strings y logs de acceso | Código/infra | Decisión de exchange de tokens |
| INFRA-03 | Constructor S3 y compatibilidad R2 | Código corregido; falta probar upload/download real | Código + manual | R2 |
| REL-01 | Merge de la release a `main` y deploy | Hecho; Render arrancó | Manual/Git | — |
| REL-02 | Verificar que no queden cuentas `@claseya.dev` activas y `DEMO_SEED=false` | Pendiente | Manual | Acceso Neon |
| SAFE-01 | Persistir consentimiento parental y exigirlo en email/Google | Pendiente | Código + legal | Decisión de versión/alcance |
| SAFE-02 | Anonimizar el nombre público en opiniones | Hecho y en `main`; admin conserva nombre completo | Código | — |
| INFRA-01 | Verificar SSL de Neon, `btree_gist`, upgrade de Flyway y versión PostgreSQL | Parcial: SSL y Flyway V24 verificados en Render; PostgreSQL 18.7 excede el soporte declarado de Flyway 17 | Manual | Acceso Neon |
| INFRA-02 | Verificar backups/PITR y hacer una restauración de prueba | Pendiente | Manual | Plan Neon |
| OPS-01 | Completar variables, proveedores y dominio de producción | Parcial: Render arranca; falta verificar dominio, email, cookies y R2 | Manual | Paneles Render/Vercel/Neon/DNS |
| PROD-01 | Cargar 10–20 profesores reales y borrar datos demo | Pendiente | Manual/negocio | Trabajo comercial |
| LEGAL-01 | Revisión legal de términos, privacidad, menores, retención e incident response | Pendiente | Manual/legal | SAFE-01/02, PRIV-02 |

### Fase 1 — Seguridad y operación antes de abrir tráfico (P1)

| ID | Tarea | Estado | Tipo | Dependencia |
|---|---|---|---|---|
| SEC-03 | MFA obligatorio para ADMIN y step-up para evidencia | Pendiente | Código + dependencia | Spec/ADR y aprobación |
| SAFE-03 | Reporte a nivel mensaje/conversación y `resolved_by` | Pendiente | Código + migración | Spec de moderación |
| PRIV-01 | Fotos de profesor en storage propio o allowlist | Pendiente | Código + producto | Spec de storage |
| PRIV-02 | Retención, borrado y exportación de datos | Pendiente | Código + legal | Política de retención |
| RATE-01 | Límites de lectura pública, por destinatario y proxy confiable | Pendiente | Código/spec | ADR de abuso |
| INFRA-03b | Configurar R2 y decidir ClamAV vs `ALLOW_UNSCANNED_EVIDENCE` | Pendiente | Manual | Bucket/credenciales |
| OPS-02 | Verificar `curl` en la imagen, health, cold start y graceful shutdown | Parcial: shutdown graceful hecho; health/cold start pendientes | Código/infra | Render |
| OPS-03 | Uptime, alertas 5xx/logs y outbox `DEAD` | Pendiente | Manual | Proveedor de monitoreo |
| COST-01 | Probar upload de 10 MB por Vercel y validar plan/costo | Pendiente | Manual | Vercel |
| TEST-01 | Ejecutar plan de preproducción: activación completa, upgrade/restore, servicios reales | Pendiente | Pruebas | Staging y Docker |
| QA-01 | Responsive 360/390/768, teclado, WCAG y Safari iOS | Pendiente | Pruebas | TEST-01 |

### Fase 2 — Mejoras (P2/P3)

| ID | Tarea | Prioridad |
|---|---|---|
| SUPPLY-01 | SCA backend, scan de imagen en PR, SBOM y DAST | P2 |
| HARD-01 | HSTS, CSP más estricta y cookies `__Host-` | P2 |
| DOC-01 | Actualizar conteos de tests, `VITE_API_URL` y runbooks | P2 |
| PERF-01 | k6 nightly con dataset de ~5.000 profesores | P2 |
| P3 | Refresh tokens, Redis, email por mensaje, normalización de ciudades, “cerca de mí”, auto-login, foto propia, Sentry, regresión visual, cross-browser, IaC | P3 |

---

## 4. Tareas manuales fuera del código

| Área | Tarea | Resultado esperado |
|---|---|---|
| Git/Render | PR `fix/mvp-functional-ux → dev`, luego `dev → main`; CI verde | Render deploya las correcciones |
| Render | Agregar `BEHIND_TRUSTED_PROXY=true` | Coherencia con rate limit detrás de Vercel |
| Render | Verificar `curl` en la imagen y health `/actuator/health` | Contenedor healthy |
| Render | Configurar `STORAGE_PROVIDER`, R2 y `SCAN_ENABLED`/`ALLOW_UNSCANNED_EVIDENCE` | Evidencia persistente y startup válido |
| Neon | Verificar SSL, `btree_gist`, versión PostgreSQL y límite de conexiones | Migraciones y conexión estables |
| Neon | Confirmar PITR/retención y restaurar un branch de prueba | Backup validado |
| Neon | Limpiar cuentas `@claseya.dev` y confirmar `DEMO_SEED=false` | Sin datos demo |
| Vercel | Asegurar `VITE_API_URL` ausente, `BACKEND_URL`, `VITE_SITE_URL`, `PUBLIC_SITE_URL` | Same-origin y SEO correctos |
| Vercel | Probar upload de 10 MB por el proxy | Sin 413 inesperado |
| R2 | Crear bucket privado, token S3 y configurar variables | Upload/download/delete funcionan |
| Brevo | Verificar remitente/dominio, SPF/DKIM y API key | Activación/reset/contacto llegan |
| Google | Agregar dominio a orígenes autorizados | Login Google funciona |
| Turnstile | Configurar hostnames, site key y secret | Registro con CAPTCHA funciona |
| Monitoreo | Uptime sobre health, alertas 5xx/logs y outbox `DEAD` | Fallos detectados |
| Admin | Promover ADMIN y enrolar MFA | Panel operable |
| Legal | Revisar términos, privacidad, menores, retención y borrado | Políticas aprobadas |
| Negocio | Cargar 10–20 profesores reales | Oferta real |

---

## 5. Decisiones pendientes

1. **SAFE-02:** nombre público en opiniones: nombre de pila + inicial, solo nombre de pila o alias separado.
2. **SAFE-01:** age gate, consentimiento parental y flujo de tutor; requiere revisión legal.
3. **SEC-03:** proveedor/dependencia para MFA y step-up.
4. **SAFE-03:** alcance del reporte por mensaje/conversación y política de verificación docente.
5. **PRIV-02:** política de retención, anonimización y exportación.
6. **INFRA-03:** R2 + ClamAV o evidencia `UNSCANNED` aceptada explícitamente.
7. **COST-01:** Vercel Hobby vs Pro y límite real de upload.
8. **QA-01:** Safari iOS como requisito P0/P1.
9. **LEGAL-01:** revisión legal y responsable de soporte/incidentes.
10. **PROD-01:** reclutamiento y validación de oferta real.

---

## 6. Criterios de aceptación

- Todos los P0 cerrados.
- `main` contiene los commits de esta rama y Render deploya ese commit.
- `/actuator/health` responde `200` con `status=UP`.
- Registro, activación, login, búsqueda, perfil, contacto y mensajería funcionan en producción.
- Cookies `Secure`/`HttpOnly`/`SameSite=Lax`; CSRF y rate limit activos.
- `DEMO_SEED=false` y cero cuentas demo activas.
- Flyway `V1..V24` validado; upgrade y restauración probados.
- R2 privado funciona; upload/download/delete autorizado.
- Backups/PITR verificados con restauración real.
- Logs sin tokens, emails completos ni secretos.
- Monitor de uptime y alerta de outbox `DEAD` activos.
- Costo mensual dentro del presupuesto acordado.
- Revisión legal completada y oferta real cargada.

---

## 7. Notas y riesgos

- `NotificationOutboxIntegrationTest` corre con Docker y pasó **9/9**.
- PostgreSQL en Neon reporta **18.7**; Flyway soporta hasta 17. Es una desviación a corregir (fijar PostgreSQL 16/17 o actualizar Flyway).
- El constructor S3 y `chunkedEncodingEnabled(false)` están corregidos; R2 real se valida con upload/download en producción.
- `main` ya fue actualizado y Render arrancó con las correcciones.
- No se commiteó ningún archivo ajeno del working tree.

---

## 8. Mejoras de producto de `TODO.md` (implementadas)

| Item | Estado | Commit |
|---|---|---|
| Modalidades sin delay: actualización optimista y estado pendiente | Hecho; test de hook | `88c1c5e` |
| Favoritos ocultos para docentes y mensaje específico | Hecho; test de política y E2E | `f383258` |
| Auto-upload de evidencia al elegir el PDF | Hecho; test de componente y E2E | `a78d1c7` |
| Transición de tema con View Transitions y fallback | Hecho; tests de tema | `46af2eb`, `d8d7a60` |
| Autocompletado de materias en “Mi anuncio” y mejoras al buscador | Hecho; tests de combobox | `8aa2e62` |
| Correos HTML de activación, reset y contacto | Hecho; tests de plantillas, Brevo, SMTP y outbox | `859aa79` |
| Verificador humano en la primera visita con cookie firmada | Hecho; tests de servicio, integración y gate | `b0bfbd5`, `c99289c` |

**Validación de esta tanda**

- Backend: **526 tests, 0 fallos** (`mvn -B test` con Docker).
- Frontend: **126 tests, 0 fallos**; ESLint y build con CSP en verde.
- El gate humano se apoya en `CAPTCHA_ENABLED`; en local/test queda desactivado.
- La cookie `CLASEYA_HUMAN` es `HttpOnly`, `Secure`, `SameSite=Lax` y dura 30 días por defecto (`HUMAN_CHECK_TTL`).

