# Auditoría de preparación para producción — ClaseYa

- **Fecha:** 2026-11-13
- **Alcance:** lanzamiento público del MVP (búsqueda, perfiles, registro/login, contacto y mensajería; sin reservas ni pagos).
- **Modo:** auditoría read-only. No se modificaron archivos, ramas ni infraestructura.
- **Evidencia:** código del repositorio, documentación y resultados reales de GitHub Actions.
- **Nota:** documento histórico. El estado de ramas y PRs puede haber cambiado después de la auditoría.

---

## 1. Arquitectura verificada

- Monolito modular Spring Boot 3.5.16 / Java 17, Maven.
- Spring Web, Security, JPA/Hibernate, Validation y Actuator.
- PostgreSQL 16 + Flyway (`ddl-auto=validate`, `open-in-view=false`).
- Frontend React 18.3 + Vite 8 + TypeScript + Tailwind 4.
- Autenticación por JWT HS256 en cookie `HttpOnly` + CSRF por cookie.
- Roles `STUDENT`, `TEACHER`, `ADMIN`.
- Módulos: `auth`, `oauth`, `user`, `academic`, `student`, `teacher`, `availability`, `favorite`, `messaging`, `review`, `safety`, `notification`, `verification`, `security`, `common`, `model`, `bootstrap`.
- Frontend y API comparten origen mediante el proxy `/api` de `frontend/vercel.json`.
- Proveedores previstos: Render (backend Docker), Vercel (SPA/SEO), Neon (PostgreSQL), Brevo/SMTP (email), Google OAuth, Cloudflare Turnstile, S3/R2 opcional y ClamAV opcional.

## 2. Estado de Git

- Rama de trabajo usada para la auditoría: `dev`.
- `main`: `e764dac`; `dev`: `875f278` al momento de la auditoría.
- `main` estaba **26 commits detrás** de `dev`.
- El PR **#51 `dev → main` estaba cerrado y no mergeado**.
- No había PRs abiertos.
- `main` protegida: 1 review requerida, sin force-push ni borrado.
- `dev` sin protección de rama.
- Rama por defecto: `dev`.
- Árbol de trabajo local con archivos no versionados: `hallazgos-2026-11-11.md`, `.atl/*`, `docs/agent-*.md`, `.opencode/`, `TODO.md`, `docs/git-workflow.md`.

**Conclusión:** el trabajo técnico estaba en `dev`, pero producción no lo había recibido. Si Render/Vercel despliegan desde `main`, producción sigue con el código viejo.

## 3. Estado del backend

Verificado en código:

- Flyway con migraciones `V1` a `V24`.
- Últimas migraciones:
  - `V22`: reportes y bloqueo.
  - `V23`: `users.session_version`.
  - `V24`: índice keyset del hilo.
- Validadores de arranque:
  - `JwtSecretValidator`: rechaza el secreto de desarrollo o uno menor a 32 bytes fuera de `local`.
  - `ProductionConfigValidator`: exige URLs HTTPS, CORS, cookies seguras, CAPTCHA, email y configuración de escaneo.
  - `DemoDataProductionGuard`: suspende y rota el hash de cuentas `@claseya.dev` fuera de `local`/`test`.
- Seguridad:
  - Contraseñas BCrypt.
  - JWT sin datos personales; claims `sub`, `jti`, `iat`, `exp`, `ver`.
  - Logout/reset revocan sesiones con `session_version`.
  - CSRF para métodos unsafe autenticados por cookie.
  - CORS sin wildcard.
  - `/api/admin/**` solo ADMIN.
  - Conversaciones ajenas devuelven 404.
  - Profesores no visibles devuelven 404.
- Validaciones y errores:
  - `ApiError` centralizado.
  - `GET /error` corregido para no devolver `status:999`.
  - Sin stack traces ni SQL en respuestas.
- Uploads:
  - Solo PDF.
  - Máximo 10 MB por archivo.
  - Máximo 5 documentos / 25 MB por credencial.
  - ClamAV obligatorio si el escaneo está activo; modo unscanned requiere acknowledge explícito.
  - Storage PostgreSQL o S3/R2.
- Observabilidad:
  - Actuator health público sin detalles.
  - Métricas de outbox.
  - `OutboxHealthIndicator` reporta `DEGRADED` si hay filas `DEAD`.

**Riesgo backend principal:** el código está bien, pero la configuración de producción no se puede verificar desde el repositorio.

## 4. Estado del frontend

- TypeScript estricto, ESLint, Vitest, Playwright.
- AuthContext resuelve la sesión con `/api/auth/me`; el JWT no se expone a JavaScript.
- No hay tokens en `localStorage`.
- Rutas privadas con guard de rol.
- SEO con prerender de landing, búsqueda y perfiles.
- Páginas legales `/terminos`, `/privacidad`, `/ayuda`.
- Consentimiento adulto/tutor en registro.
- Mensajería con polling cada 20 s, paginación y bloqueo/reporte.
- Accesibilidad: `Modal` con focus trap/ESC, `aria-live` en toasts, labels, `sr-only`, `prefers-reduced-motion`.
- E2E actual: solo Chromium desktop; no hay proyectos mobile.
- No hay auditoría automática de accesibilidad tipo axe/Lighthouse.
- No hay Sentry ni reporte de errores frontend; `ErrorBoundary` solo muestra fallback.

**Riesgo frontend principal:** el público inicial probablemente use móvil, y la cobertura E2E no incluye viewports móviles ni auditoría visual/accesible formal.

## 5. Base de datos y despliegue

- Schema propiedad de Flyway; Hibernate solo valida.
- PostgreSQL 16; Neon en producción.
- `V22`–`V24` deben aplicarse en el release.
- `users.session_version` es aditiva y compatible.
- `V24` crea índice; en una tabla chica no debería ser problema.
- `.env.local` local (ignorado por Git) contiene una cadena de conexión real a Neon `production`. **No se reproduce el valor.**
- No hay `render.yaml` ni configuración de proyecto Vercel en el repo; se administran fuera.
- `Dockerfile` corre como no-root, usa `PORT`, y tiene `HEALTHCHECK` con `curl`. No hay evidencia en el repo de que la imagen base incluya `curl`; verificar en el entorno real.

## 6. Pruebas y evidencia

- No se ejecutaron builds locales nuevos durante la auditoría read-only.
- Evidencia real de CI en `dev` (`875f278`):
  - Backend: PASS.
  - Frontend: PASS.
  - E2E: PASS.
  - CodeQL: PASS.
  - Dependency review: PASS.
  - Vercel: PASS.
- PR #51: todos los checks verdes, pero cerrado sin merge.
- Docker local estaba apagado en las verificaciones previas; la suite de integración se apoya en CI.

## 7. Variables de entorno requeridas (solo nombres)

**Backend**
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `JWT_SECRET`, `JWT_EXPIRATION`
- `APP_BASE_URL`, `FRONTEND_URL`
- `COOKIE_SECURE`, `COOKIE_SAME_SITE`
- `EMAIL_TRANSPORT`, `EMAIL_API_KEY` o `MAIL_HOST`/`MAIL_*`, `MAIL_FROM`
- `CAPTCHA_ENABLED`, `TURNSTILE_SECRET`
- `RATE_LIMIT_TRUST_FORWARDED_HEADERS`
- `DEMO_SEED=false`
- `STORAGE_PROVIDER`, `SCAN_ENABLED`, `ALLOW_UNSCANNED_EVIDENCE`
- Si S3/R2: `STORAGE_ENDPOINT`, `STORAGE_REGION`, `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`
- `GOOGLE_CLIENT_ID`
- `PORT`

**Frontend / Vercel**
- `VITE_GOOGLE_CLIENT_ID`
- `VITE_TURNSTILE_SITE_KEY`
- `VITE_SITE_URL`
- `VITE_API_URL` (debe quedar vacío con el proxy de Vercel)
- `BACKEND_URL`
- `PUBLIC_SITE_URL`

## 8. Hallazgos

### BLOQUEANTE

1. **Release no promovida a `main`.**
   - `main` estaba 26 commits atrás y el PR #51 cerrado.
   - Si producción despliega desde `main`, no tiene cuarentena demo, password reset, sesión revocable, reportes/bloqueo, legales ni onboarding mínimo.

2. **Datos demo y credenciales conocidas en producción.**
   - El seed crea cuentas `@claseya.dev` con contraseña documentada.
   - El guard existe en `dev`, pero la limpieza real en Neon no está verificada.
   - Hasta limpiar, hay riesgo de suplantación y oferta ficticia.

3. **Configuración de producción no verificada.**
   - Mismo origen SPA/API, `VITE_API_URL` vacío, `BACKEND_URL`, `FRONTEND_URL`, cookies, CAPTCHA y email deben coincidir.
   - Una mala combinación rompe login o registro.

4. **Cero oferta real.**
   - El producto no puede lanzarse con docentes demo.
   - Faltan 10–20 profesores reales y datos reales.

5. **Legal/privacidad para menores sin revisión profesional.**
   - Términos, privacidad y consentimiento existen como textos del producto, pero no hay evidencia de revisión legal.
   - No hay proceso verificado de baja/eliminación de datos ni responsable de soporte.

6. **Credencial real de Neon en archivo local.**
   - `.env.local` está ignorado por Git, pero contiene una cadena de conexión real a la rama `production`.
   - Rotar la contraseña y mover el secreto a un gestor seguro. No asumir que el archivo no se compartió.

### ALTO

1. **`RATE_LIMIT_TRUST_FORWARDED_HEADERS` no está en el fail-fast.**
   - Detrás de Vercel, si queda en `false`, todos los usuarios comparten un bucket de IP.
   - El README lo exige, pero el validador no lo fuerza.

2. **Evidencia en PostgreSQL por defecto.**
   - `STORAGE_PROVIDER=postgres` guarda PDFs en `bytea`.
   - En Neon Free el espacio y los backups se degradan rápido.
   - Si se usa verificación documental, configurar S3/R2 y decidir `SCAN_ENABLED`/`ALLOW_UNSCANNED_EVIDENCE`.

3. **Sin observabilidad de errores frontend ni uptime externo documentado.**
   - Ya existe health `DEGRADED` para outbox, pero no alertas de uptime, errores JS ni trazas.
   - Sin esto, los fallos de producción se detectan por usuarios.

4. **Cobertura mobile/accesibilidad insuficiente para un MVP público.**
   - Playwright solo desktop Chrome.
   - No hay axe/Lighthouse automatizado.
   - Verificar 360/390/768 px, teclado, contraste y lectores de pantalla.

5. **Healthcheck del contenedor no validado en CI.**
   - `Dockerfile` usa `curl`; CI no construye la imagen en cada PR/dev.
   - Verificar que la imagen de Render tenga `curl` o cambiar la sonda.

6. **Backups/PITR de Neon no verificables desde el repositorio.**
   - Confirmar retención y restauración antes de abrir tráfico.

### MEDIO

- Rate limiting en memoria por instancia; con más de una instancia el límite se multiplica.
- JWT de 12 h sin refresh tokens; logout/reset revocan, pero un token robado puede durar hasta 12 h.
- Sin email por cada mensaje; solo polling in-app. Aceptado por `REVIEW2-002`.
- `SmtpEmailSender` registra el email del destinatario en errores de envío; evaluar redacción de PII en logs.
- `dev` sin protección de rama; se puede pushear sin PR/CI.
- README con instrucciones contradictorias sobre `VITE_API_URL`.
- `comandos.txt` versionado con ejemplos/comentarios de variables y credenciales de desarrollo; no es secreto de producción, pero conviene limpiar.
- Sin índices de búsqueda por rating/precio; no agregar sin `EXPLAIN` con volumen real.

### BAJO

- Ausencia de tests automatizados de accesibilidad.
- CSP con `style-src 'unsafe-inline'` y `img-src https:` amplio.
- Documentación de ramas nueva (`docs/git-workflow.md`) sin commitear.
- `hallazgos-2026-11-11.md` y artefactos `.atl`/`.opencode` sin versionar.

## 9. Riesgos de seguridad y privacidad

**Bien resuelto**
- Cookie HttpOnly, Secure y SameSite configurable.
- CSRF con token y cookie HttpOnly.
- BCrypt.
- Validación de secreto JWT al arrancar.
- CORS restringido.
- Roles y ownership en endpoints.
- DTOs públicos sin email, dirección ni coordenadas.
- Uploads PDF con tamaño, cuota, hash y escaneo.
- Errores genéricos sin enumeración.
- Rate limiting en login, registro, contacto, mensajes y uploads.

**Riesgos abiertos**
- Credenciales demo conocidas hasta limpiar producción.
- Conexión Neon `production` en `.env.local`.
- Rate limit IP detrás de proxy mal configurado.
- Evidencia sensible en `bytea` y backups de Neon.
- Emails en logs de error SMTP.
- Token de 12 h sin refresh.
- Sin MFA.
- Sin reporte de errores frontend.
- Legal/parental sin revisión externa.

## 10. Tareas ordenadas por dependencia

1. **Rotar la credencial de Neon y sacarla del workspace.**
   - AC: la contraseña vieja deja de funcionar; la nueva vive en gestor de secretos; `.env.local` no se comparte ni se versiona.

2. **Promover `dev` a `main`.**
   - Reabrir #51 o crear un PR nuevo `dev → main`.
   - AC: CI verde, PR aprobado, `main` igual a `dev`, deploy de backend y frontend, migraciones `V22`–`V24` aplicadas, `/actuator/health` UP.

3. **Configurar producción y verificar el fail-fast.**
   - AC: backend arranca con `JWT_SECRET`, URLs HTTPS, `COOKIE_SECURE=true`, `COOKIE_SAME_SITE=Lax`, `CAPTCHA_ENABLED=true`, email Brevo/SMTP y `RATE_LIMIT_TRUST_FORWARDED_HEADERS=true`; login y registro reales funcionan.

4. **Limpiar datos demo en Neon.**
   - AC: cero cuentas `@claseya.dev` activas; `GET /api/teachers` solo devuelve profesores reales; `DEMO_SEED=false`; guard verificado.

5. **Definir almacenamiento de evidencia.**
   - AC: `STORAGE_PROVIDER=s3` con bucket privado o decisión explícita de mantener PostgreSQL; `SCAN_ENABLED`/`ALLOW_UNSCANNED_EVIDENCE` definidos; upload/download/delete probados.

6. **Observabilidad y alertas.**
   - AC: uptime monitor contra health; alerta si `DEGRADED`; logs de Render/Vercel; decisión sobre Sentry u otro error tracking; alerta de outbox `DEAD`.

7. **Legal y soporte.**
   - AC: términos/privacidad/ayuda revisados; consentimiento parental aceptado; canal de soporte y baja de datos publicado; SLA de moderación definido.

8. **QA mobile, responsive y accesibilidad.**
   - AC: flujos críticos en 360/390/768 px; teclado completo; contraste y foco; sin scroll horizontal; registro, búsqueda, contacto, mensajería, reporte y legales verificados.

9. **Oferta real y lanzamiento suave.**
   - AC: 10–20 profesores reales cargados y revisados; 5–10 usuarios de prueba; métricas registro → perfil → contacto → respuesta; soporte preparado.

## 11. Verificación manual fuera del repositorio

- Render: branch de deploy, variables de entorno, healthcheck, Docker health, logs.
- Vercel: root `frontend`, `BACKEND_URL`, `VITE_API_URL` vacío, `VITE_SITE_URL`, `PUBLIC_SITE_URL`, dominio y rewrite `/api`.
- Neon: rama `production`, SSL, pool, backups/PITR, migraciones aplicadas.
- Google Cloud: orígenes autorizados de OAuth.
- Cloudflare Turnstile: site key y secret key.
- Brevo/SMTP: remitente verificado, API key, entrega real de activación y reset.
- S3/R2: bucket privado, credenciales, lifecycle y escaneo.
- DNS/TLS/HSTS y cookies en navegador real.
- Monitoreo: uptime, alertas, logs, outbox `DEAD`.
- Legal: revisión profesional de términos/privacidad/consentimiento.
- Datos: limpieza demo y verificación de oferta real.

## 12. Recomendación

**No está listo para abrir tráfico público todavía.**

El código en `dev` está en buen estado técnico: CI verde, arquitectura consistente, autenticación sólida, autorización verificada, migraciones controladas y buenos controles de errores y privacidad. **Sí está en condiciones de comenzar la preparación formal de producción.**

Pero hay bloqueantes reales:

1. La release no está en `main`; producción no recibió el trabajo.
2. Hay datos demo y credenciales conocidas por limpiar.
3. La configuración de producción no está verificada.
4. La oferta real es cero.
5. Falta revisión legal/privacidad para menores.
6. Hay una credencial real de Neon en un archivo local que debe rotarse.

**Orden recomendado:** rotar credencial → promover `dev` a `main` → configurar y verificar producción → limpiar demo → asegurar storage/observabilidad → revisar legal → QA mobile → cargar oferta real → lanzamiento suave.
