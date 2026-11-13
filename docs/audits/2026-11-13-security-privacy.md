# Auditoría de seguridad y privacidad — ClaseYa

- **Fecha:** 2026-11-13
- **Alcance:** MVP público, con foco en menores de edad.
- **Modo:** auditoría defensiva read-only. No se modificaron archivos, ramas ni infraestructura.
- **Restricciones:** no se ejecutaron pruebas destructivas ni se accedió a datos reales de usuarios.
- **Evidencia:** código, configuración, documentación y resultados de CI.

---

## Resumen ejecutivo

ClaseYa tiene controles técnicos razonables: Spring Security con reglas por rol, JWT en cookie HttpOnly, CSRF, CORS restringido, ownership por recurso, BCrypt, tokens de activación/reset hasheados, uploads PDF con cuota y escaneo, y DTOs públicos sin email ni dirección.

Pero **no está listo para abrir tráfico público con menores**. Hay riesgos críticos de credenciales, datos demo, consentimiento, exposición de nombres de estudiantes, administración sin MFA y logging de tokens.

---

## Controles verificados

- Reglas de autorización en `SecurityConfig` y validación de ownership en services.
- Identidad desde `CurrentUser`, no desde IDs del cliente.
- Conversaciones ajenas → 404; profesores ocultos → 404.
- JWT HS256 en cookie HttpOnly/Secure/SameSite.
- CSRF para métodos unsafe autenticados por cookie.
- CORS sin wildcard y con credenciales solo para orígenes exactos.
- BCrypt y validación de contraseña.
- Tokens de activación/reset/message-link hasheados, de un solo uso y con TTL.
- Rate limiting en login, registro, contacto, mensajes, reportes, bloqueos y uploads.
- Uploads: PDF, máximo 10 MB, cuota, hash, ClamAV fail-closed y storage privado.
- DTOs públicos sin email, dirección exacta ni coordenadas.
- Errores genéricos, sin stack traces ni SQL.
- CSP, `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy` y `Permissions-Policy`.
- CI con tests de autenticación, CSRF, rate limiting, mensajería, safety, verificación, headers y E2E.

Esto **no demuestra** que el sistema sea seguro. Los riesgos abiertos son de configuración, procesos, datos y producto.

---

## Hallazgos

### F1 — CRÍTICO — Release atrasada y datos demo potencialmente activos

**Evidencia**
- `main` estaba 26 commits detrás de `dev`; el PR #51 fue cerrado sin merge.
- El seed crea cuentas `@claseya.dev` con contraseña documentada (`db/seed/demo-data.sql`).
- El guard que suspende esas cuentas (`DemoDataProductionGuard`) está en `dev`, no en `main`.
- La limpieza real en Neon no está verificada.

**Escenario e impacto**
Si producción despliega desde `main`, un atacante puede autenticarse con cuentas demo conocidas, incluida `admin@claseya.dev` si existe. Impacto: suplantación de docentes, lectura/escritura de mensajes, acceso a PII, documentos y panel admin.

**Corrección recomendada**
- Promover `dev` a `main` solo después de la cuarentena/limpieza.
- Ejecutar la cuarentena SQL y borrar filas demo.
- Rotar contraseñas de cualquier cuenta demo que quede.
- Confirmar `DEMO_SEED=false`.
- Verificar `GET /api/teachers` y el directorio admin.

**Pruebas necesarias**
- Login demo falla en producción.
- `/api/teachers` solo devuelve profesores reales.
- Integración del guard y de la cuarentena.
- Smoke test de producción.

---

### F2 — CRÍTICO — Credencial real de Neon en archivo local

**Evidencia**
- `.env.local` está ignorado por Git, pero contiene una cadena de conexión real a Neon `production`. No se reproduce el valor.
- `git check-ignore` confirma que no está versionado, pero permanece en el workspace.

**Escenario e impacto**
Cualquier proceso, agente, backup del workspace o malware con acceso al archivo obtiene control total de la base: lectura y modificación de usuarios, mensajes, reportes y evidencia.

**Corrección recomendada**
- Rotar la contraseña de Neon inmediatamente.
- Mover la credencial a un secret manager.
- Eliminar el archivo local y revisar logs/historial de herramientas que lo hayan leído.
- Usar un rol de base con privilegios mínimos; separar migraciones de runtime si es posible.

**Pruebas necesarias**
- La credencial anterior deja de funcionar.
- La aplicación arranca con la nueva desde el gestor de secretos.
- Escaneo de secretos en repo e historial.

---

### F3 — ALTO — Logging de tokens de activación y contacto si se habilitan flags de desarrollo

**Evidencia**
- `ConfigurableActivationLinkSender.java` registra email + enlace si `AUTH_LOG_ACTIVATION_LINKS=true`.
- `ConfigurableContactNotificationSender.java` registra email + enlace si `MESSAGE_LINK_LOG=true`.
- `ProductionConfigValidator` no bloquea esos flags fuera de `local`/`test`.

**Escenario e impacto**
Un operador habilita el flag en producción o lo hereda de un entorno de prueba. Los logs de Render exponen tokens de activación o links de contacto que abren una sesión como el profesor. Impacto: takeover de cuentas y lectura de conversaciones.

**Corrección recomendada**
- Fail-fast en producción si `app.auth.activation.log-links=true` o `app.notification.log-links=true`.
- Nunca loguear tokens ni links completos.
- Redactar email y token en logs.

**Pruebas necesarias**
- Startup falla con esos flags fuera de `local`/`test`.
- Captura de logs: no aparece email ni token.
- Tests de senders con transporte configurado.

---

### F4 — ALTO — Token de reset de contraseña persiste en el outbox después de enviado

**Evidencia**
- `NotificationOutboxRepository.markSent` y `markDead` eliminan `token` solo para `ACTIVATION`.
- `PASSWORD_RESET` conserva el token crudo en `notification_outbox.payload`.

**Escenario e impacto**
Un acceso de lectura a la DB o a un backup permite usar un token de reset aún no expirado para tomar una cuenta. Además, extiende innecesariamente la vida de un secreto.

**Corrección recomendada**
- Eliminar `token` del payload también para `PASSWORD_RESET` al pasar a `SENT`/`DEAD`.
- Agregar limpieza/retensión de payloads.

**Pruebas necesarias**
- Tras `SENT`/`DEAD`, el payload de `PASSWORD_RESET` no contiene `token`.
- Test de integración del outbox.

---

### F5 — ALTO — Consentimiento parental solo en UI y ausente en Google

**Evidencia**
- `RegisterPage.tsx` guarda `accepted` solo en estado local; `RegisterRequest` no tiene campo de consentimiento.
- `AuthService.register` no persiste aceptación.
- `GoogleAuthService.createFromGoogle` crea una cuenta `ACTIVE` sin ningún consentimiento.
- El texto declara mayoría de edad/tutor, pero no hay verificación ni registro.

**Escenario e impacto**
Un menor puede registrarse por email o Google sin consentimiento parental verificable. Si hay un incidente o reclamo, no existe evidencia de aceptación. Riesgo legal y de confianza.

**Corrección recomendada**
- Persistir versión y timestamp de términos/consentimiento.
- Exigir consentimiento en email y Google.
- Evaluar age gate y flujo de tutor.
- Revisión legal de términos, privacidad y política de menores.

**Pruebas necesarias**
- Registro email sin consentimiento → rechazo.
- Registro Google nuevo sin consentimiento → rechazo.
- Consentimiento persistido y versionado.
- Test E2E del flujo de tutor.

---

### F6 — ALTO — Nombre completo de estudiantes en opiniones públicas

**Evidencia**
- `ReviewResponse.authorName` devuelve el nombre completo del estudiante.
- `GET /api/teachers/{id}/reviews` es público.

**Escenario e impacto**
El nombre completo de un menor queda indexable y visible para cualquier visitante. Facilita doxxing, acoso o contacto fuera de la plataforma.

**Corrección recomendada**
- Mostrar solo nombre de pila, inicial o alias.
- Permitir un nombre público separado del nombre real.
- Moderar comentarios y datos personales.
- Evaluar consentimiento parental para publicar opiniones.

**Pruebas necesarias**
- DTO público de review no expone nombre completo.
- Test de moderación de PII en comentarios.
- E2E de opinión pública anonimizada.

---

### F7 — ALTO — Mensajería directa con menores sin supervisión ni verificación docente obligatoria

**Evidencia**
- Cualquier STUDENT puede iniciar conversación con un TEACHER.
- `verificationStatus` es un plus, no requisito de publicación.
- Reporte/bloqueo existen, pero el reporte es a nivel usuario y no referencia conversación/mensaje.
- No hay moderación previa de mensajes ni visibilidad parental.

**Escenario e impacto**
Un adulto no verificado puede publicar un anuncio y comunicarse con menores. El reporte posterior no permite al admin revisar el mensaje exacto. Riesgo de grooming, acoso y responsabilidad legal.

**Corrección recomendada**
- Exigir verificación de identidad para publicar, o mostrar advertencia clara.
- Agregar reporte a nivel mensaje/conversación con evidencia visible al admin.
- Definir política de contacto con menores y supervisión parental.
- Evaluar verificación de antecedentes para docentes.
- Definir SLA de moderación y protocolo de escalamiento.

**Pruebas necesarias**
- Reporte de mensaje crea referencia y admin puede revisar.
- Profesor no verificado oculto o marcado según decisión.
- E2E de reporte/bloqueo y flujo parental.
- Test de acceso admin a evidencia con auditoría.

---

### F8 — ALTO — Panel admin sin MFA

**Evidencia**
- No hay TOTP/WebAuthn ni segundo factor.
- ADMIN puede listar emails, ver y descargar evidencia, moderar y suspender cuentas.

**Escenario e impacto**
Una contraseña de admin comprometida da acceso a PII de menores, documentos de identidad y conversaciones. Es el activo más sensible.

**Corrección recomendada**
- MFA obligatorio para ADMIN.
- Step-up authentication para descargar evidencia.
- Alertas de login admin y revisión periódica de cuentas admin.
- Evitar un único admin; proteger contra lockout.

**Pruebas necesarias**
- Admin sin MFA no accede.
- Enrollment y recuperación.
- Step-up para documentos.
- Auditoría de accesos.

---

### F9 — ALTO — Toggles de seguridad no validados en producción

**Evidencia**
- `security.csrf.enabled` se lee con default `true`, pero `ProductionConfigValidator` no lo valida.
- `RATE_LIMIT_ENABLED` puede desactivarse sin fail-fast.
- `RATE_LIMIT_TRUST_FORWARDED_HEADERS` default `false`; detrás de Vercel todos comparten bucket si no se setea.
- El README exige `RATE_LIMIT_TRUST_FORWARDED_HEADERS=true` detrás del proxy, pero no hay validación de arranque.

**Escenario e impacto**
Un deploy puede quedar sin CSRF o sin rate limiting, o con todos los usuarios compartiendo el mismo bucket IP. Impacto: CSRF, credential stuffing, spam y bloqueo de usuarios legítimos.

**Corrección recomendada**
- Fail-fast en producción si CSRF o rate limiting están deshabilitados.
- Validar la configuración de proxy confiable.
- Tests de arranque y de headers/rate limit.

**Pruebas necesarias**
- Startup falla con CSRF/rate limit apagados.
- Rate limit por IP real detrás de proxy.
- CSRF integration.

---

### F10 — ALTO — Reportes sin evidencia de conversación ni auditoría del resolutor

**Evidencia**
- `Report` no tiene `conversation_id`, `message_id` ni `resolved_by`.
- `ReportService.resolve` no recibe al admin; solo setea `status` y `resolved_at`.
- `AdminReportResponse` muestra nombres y descripción, pero no el contenido reportado.

**Escenario e impacto**
Administración no puede investigar el contexto real de un reporte de acoso; no queda registro de quién lo cerró. Impacto: moderación inefectiva y falta de trazabilidad legal.

**Corrección recomendada**
- Reportar mensaje/conversación con referencia.
- Registrar admin resolutor y timestamp.
- Permitir al admin ver el contexto con control de acceso y auditoría.
- Definir retención de reportes y evidencia.

**Pruebas necesarias**
- Reporte de mensaje incluye conversación/mensaje.
- Admin ve evidencia y queda auditado.
- `resolved_by` persistido.
- IDOR: no participante no accede.

---

### F11 — MEDIO — PII y tokens en logs y URLs

**Evidencia**
- `SmtpEmailSender.java` registra el email destinatario en errores.
- Los links de activación/reset/message-link viajan en query params; `AuthContext` los borra del historial, pero los logs de acceso del hosting pueden conservar la URL inicial.
- `Referrer-Policy` ayuda, pero no elimina logs de query string.

**Escenario e impacto**
Emails y tokens en logs de Render/Vercel pueden ser leídos por soporte o por un atacante con acceso a logs. Impacto: enumeración de usuarios y account takeover.

**Corrección recomendada**
- Redactar emails en logs.
- No loguear query strings de rutas con tokens.
- Evaluar exchange de código de un solo uso en POST, o fragmento de URL.
- `Cache-Control: no-store` en respuestas de autenticación.

**Pruebas necesarias**
- Captura de logs sin email/token.
- Headers `no-store` en `/api/auth/**`.
- Test de URL con token sin filtración a logs.

---

### F12 — MEDIO — Fotos externas permiten tracking de menores

**Evidencia**
- `photoUrl` acepta cualquier `https://` en `CreateTeacherProfileRequest`.
- `Avatar` renderiza la URL externa.
- CSP `img-src https:` permite cualquier host.

**Escenario e impacto**
Un docente puede insertar un píxel de tracking y registrar IP/User-Agent de estudiantes que ven su perfil. Impacto: privacidad de menores y vigilancia fuera de la plataforma.

**Corrección recomendada**
- Subir/proxy de imágenes a un bucket propio.
- Restringir dominios permitidos en CSP.
- `referrerPolicy="no-referrer"` en avatares.
- Moderar fotos.

**Pruebas necesarias**
- Rechazo de URLs fuera de allowlist.
- Avatar servido desde origen propio.
- CSP sin hosts arbitrarios.

---

### F13 — MEDIO — Sin borrado/exportación de datos ni retención definida

**Evidencia**
- No hay endpoint de eliminación/exportación.
- `docs/data-model.md` indica que el hard-delete de usuarios es imposible por FKs `RESTRICT`.
- Mensajes, reportes, evidencia y tokens pueden permanecer indefinidamente.

**Escenario e impacto**
Un menor o tutor no puede ejercer borrado/acceso; los datos se acumulan. Impacto legal y de privacidad.

**Corrección recomendada**
- Definir política de retención por tipo de dato.
- Implementar anonimización/baja lógica y exportación.
- Definir proceso manual mientras no exista endpoint.
- Revisión legal.

**Pruebas necesarias**
- Flujo de baja/anonimización.
- Exportación de datos propios.
- Job de retención y verificación de borrado.

---

### F14 — MEDIO — Rate limiting en memoria y endpoints públicos sin límite

**Evidencia**
- Bucket4j en memoria por instancia; no distribuido.
- `GET /api/teachers` y `GET /api/subjects` no tienen rate limit.
- No hay CAPTCHA en contacto/mensajes; solo en registro.
- No hay límite por profesor/destinatario.

**Escenario e impacto**
Con varias instancias el límite se multiplica. Un atacante puede hacer scraping/DoS de búsqueda o crear varias cuentas y spamear contactos.

**Corrección recomendada**
- Redis o rate limit distribuido al escalar.
- Límite de búsqueda por IP.
- Límite por docente/destinatario y CAPTCHA adaptativo en contacto.
- Monitoreo de abuso.

**Pruebas necesarias**
- Carga sobre búsqueda.
- Rate limit por IP/proxy.
- Límite de contacto por destinatario.

---

### F15 — MEDIO — Cobertura de supply chain y escaneo de imagen incompleta

**Evidencia**
- CI corre `npm audit` y dependency review; no hay OWASP dependency-check para Maven.
- `container-scan.yml` corre en `main`, no en cada PR/dev.
- No hay DAST/pen-test automatizado.

**Escenario e impacto**
Una dependencia vulnerable o una imagen con CVE puede llegar a producción sin bloqueo.

**Corrección recomendada**
- Agregar SCA backend (OWASP Dependency-Check o equivalente).
- Escanear imagen en PR/dev.
- SBOM y política de actualización.
- DAST pre-release.

**Pruebas necesarias**
- Job de CI falla con CVE alto.
- Scan de imagen en PR.
- Auditoría de dependencias.

---

### F16 — BAJO — Hardening de headers/cookies

**Evidencia**
- Frontend `_headers` no declara HSTS explícito; Vercel puede agregarlo.
- CSP usa `style-src 'unsafe-inline'` e `img-src https:` amplio.
- Cookies no usan prefijo `__Host-`.
- No hay `Cache-Control: no-store` explícito en respuestas de auth.

**Escenario e impacto**
Riesgo residual de tracking, caching de sesión o downgrade; no es bloqueante por sí solo.

**Corrección recomendada**
- HSTS explícito en el host.
- Evaluar CSP más estricta y nonces.
- Cookies `__Host-` si la arquitectura lo permite.
- `Cache-Control: no-store` en auth.

**Pruebas necesarias**
- Header tests.
- CSP check.
- Cache tests.

---

## Requisitos legales y de producto que necesitan revisión humana

No son decisiones técnicas que el agente deba tomar solo:

1. **Menores y consentimiento parental:** edad mínima, tutor responsable, registro de consentimiento, visibilidad parental.
2. **Privacidad:** política de datos, retención, borrado, exportación, subencargados (Neon, Render, Vercel, Brevo, Google, S3/R2).
3. **Seguridad infantil:** política de contacto adulto-menor, moderación, reporte de mensajes, protocolo de escalamiento.
4. **Verificación docente:** identidad obligatoria, antecedentes, verificación opcional vs. requisito de publicación.
5. **Términos y privacidad:** revisión legal profesional, no solo texto generado.
6. **Email transaccional:** SPF, DKIM, DMARC, remitente verificado y política antiphishing.
7. **Incident response:** notificación de brechas, contacto de seguridad, plazos.
8. **Datos en el exterior:** residencia de datos y transferencias internacionales.
9. **Cumplimiento local:** Argentina (Ley 25.326 y normas de menores) y cualquier jurisdicción aplicable.
10. **Moderación:** SLA, criterios, evidencia conservada y auditoría de decisiones.

---

## Pruebas necesarias para validar las correcciones

- Startup fail-fast para `log-links`, CSRF y rate limiting.
- Payload del outbox sin token de `PASSWORD_RESET` tras `SENT`/`DEAD`.
- Logs sin email ni tokens.
- Registro email/Google sin consentimiento → rechazo.
- Opinión pública sin nombre completo.
- Reporte de mensaje con evidencia visible al admin y `resolved_by`.
- Admin sin MFA → bloqueo; step-up para documentos.
- Matriz IDOR/BOLA por endpoint con IDs de otro usuario.
- Rate limit por IP real detrás de proxy y por destinatario.
- Upload: PDF inválido, tamaño, cuota, scan caído, acceso cruzado.
- Headers y `Cache-Control` en auth.
- SCA backend, scan de imagen y DAST.
- E2E de menor/tutor, reporte/bloqueo y flujo de contacto.

---

## Orden recomendado

1. **Rotar credencial Neon** y sacar `.env.local` del workspace.
2. **Promover la release** y limpiar datos demo; verificar producción.
3. **Fail-fast de log-links/CSRF/rate limit**; limpiar tokens del outbox; redactar logs.
4. **MFA admin** y auditoría de reportes/decisiones.
5. **Consentimiento parental, age gate y Google**; anonimizar reviews.
6. **Moderación de mensajes/reportes y verificación docente**.
7. **Retención, borrado, exportación e imágenes propias**.
8. **SCA/container scan/DAST y hardening de headers**.

---

## Recomendación

**No está listo para abrir tráfico público con menores.**

El código tiene una base de seguridad mejor que la media para un MVP, pero quedan bloqueantes reales:

- credenciales production en un archivo local,
- release sin promover y datos demo posiblemente activos,
- tokens que pueden terminar en logs,
- token de reset persistido en el outbox,
- consentimiento parental no verificable y ausente en Google,
- nombres de menores en opiniones públicas,
- mensajería adulto-menor sin supervisión ni verificación obligatoria,
- administración sin MFA.

**Se puede iniciar la preparación de producción, pero no el lanzamiento público**, hasta resolver al menos F1–F9 y las revisiones legales de menores.

No se implementaron correcciones durante esta auditoría.
