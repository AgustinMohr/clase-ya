# NOTIF-001 — Transporte HTTP de email para el outbox

**ID:** NOTIF-001
**Estado:** Aprobada — implementada (2026-10-30). Smoke en Render pendiente.
**Nivel:** 2 (integración externa nueva + decisión de arquitectura)
**Origen:** el backend corre en Render Free, que bloquea el tráfico saliente en los puertos
`25`, `465` y `587`. Ni el email de activación ni el aviso de contacto pueden salir por SMTP.
**Relacionado:** `REVIEW2-002` (outbox), `ADR-029` (email de activación por outbox), `ADR-021`,
`ADR-030` (nuevo, ver §14).

---

## 1. Objetivo

Permitir que el backend entregue los emails de activación y de aviso de contacto a través de la
**API HTTP** de un proveedor (Brevo, sobre 443) además del SMTP actual, sin cambiar el
comportamiento observable del registro ni el contrato de API, para que el deploy gratuito en
Render funcione.

## 2. Contexto

- `ConfigurableActivationLinkSender` y `ConfigurableContactNotificationSender` envían con
  `JavaMailSender` (SMTP, puerto 587 por defecto).
- Render Free bloquea 25/465/587. Como el chequeo de transporte solo mira si `spring.mail.host`
  está definido, el registro responde **202** y el email nunca llega: el worker reintenta 5 veces y
  la fila queda `DEAD`. Es una falla silenciosa desde el punto de vista del usuario.
- `REVIEW2-002` ya sacó el envío de la transacción: el cambio queda contenido en la capa de envío,
  sin tocar el outbox, el worker, el scheduler ni los servicios de negocio.
- `RestClient` ya se usa en `TurnstileCaptchaVerifier`; no hace falta ninguna dependencia nueva.
- Las pruebas de integración mockean las interfaces `ActivationLinkSender` y
  `ContactNotificationSender` (`AbstractWebIntegrationTest`, `NotificationOutboxIntegrationTest`,
  `RateLimitIntegrationTest`), así que **esas interfaces no cambian**.

## 3. Actores

- **Sistema (worker del outbox):** entrega los avisos.
- **STUDENT:** se registra y recibe el email de activación.
- **TEACHER:** recibe el aviso de contacto.
- **Operador:** configura el proveedor (API key, remitente verificado).

## 4. Decisiones

- **D1 — Un puerto `EmailSender`.** Interfaz nueva: `boolean isConfigured()` y
  `void send(String to, String subject, String text)`. Los dos senders existentes pasan a
  **componer el mensaje** (asunto/cuerpo actuales) y **delegar** en el puerto. El fallback
  `log-links` de cada flujo se queda en su sender, porque las variables son distintas
  (`AUTH_LOG_ACTIVATION_LINKS` vs `MESSAGE_LINK_LOG`) y porque loguear un link es una decisión del
  flujo, no del transporte.
- **D2 — Dos implementaciones del puerto:** `SmtpEmailSender` (comportamiento actual, sin cambios de
  texto) y `BrevoEmailSender` (HTTP). Selección con `app.notification.email.transport`
  (`smtp` por defecto, `brevo` alternativo).
- **D3 — Cero cambio de comportamiento.** Mismos asuntos y cuerpos, mismo `202` genérico, mismo
  `503` cuando no hay transporte, mismo outbox, mismos reintentos y backoff.
- **D4 — Clasificación de errores del proveedor.** `400`, `401` y `403` →
  `PermanentNotificationException` (clave inválida o remitente no verificado no se arregla
  reintentando: la fila va a `DEAD`). `429`, `5xx`, timeout y error de I/O →
  `NotificationDeliveryException` (reintento con el backoff existente).
- **D5 — Timeouts acotados** de conexión y lectura, configurables. El envío ocurre en el worker,
  nunca en el hilo del request.
- **D6 — El guard de arranque no cambia.** `ProductionConfigValidator` sigue exigiendo
  `CAPTCHA_ENABLED=true` y `SCAN_ENABLED=true` fuera de `local`/`test`. Este cambio **no** relaja
  ninguna validación de seguridad.
- **D7 — Brevo como primer proveedor.** Es el único adaptador HTTP de esta spec. La forma de agregar
  otro (Resend, Mailgun) queda documentada, no implementada.

## 5. Requisitos funcionales

- **RF-1** Con `EMAIL_TRANSPORT=smtp` (default) el comportamiento es idéntico al actual: SMTP si
  `spring.mail.host` está definido y `JavaMailSender` disponible.
- **RF-2** Con `EMAIL_TRANSPORT=brevo` los dos flujos (activación y aviso de contacto) se entregan
  vía `POST` a la API de Brevo con la API key configurada.
- **RF-3** `isConfigured()` es `true` cuando el transporte elegido tiene lo que necesita: SMTP →
  host no vacío y `JavaMailSender` presente; Brevo → API key no vacía. Si es `false`, el registro
  responde **503 y no crea la cuenta** (comportamiento actual, RF-6 de `REVIEW2-002`).
- **RF-4** Los textos de los emails no cambian (asunto y cuerpo actuales, incluidos los links
  `${APP_BASE_URL}/activar?token=...` y `${APP_BASE_URL}/mensajes/{id}?token=...`).
- **RF-5** Un error 4xx permanente del proveedor deja la fila `DEAD` sin reintentos; un 429, 5xx o
  timeout se reintenta con el backoff existente.
- **RF-6** Ningún log incluye la API key, el token de activación ni el link completo. Se loguea el
  destinatario y el código de estado, nada más.
- **RF-7** El arranque falla con un mensaje claro si `EMAIL_TRANSPORT=brevo` y `EMAIL_API_KEY` está
  vacía, en lugar de aceptar registros que después no pueden entregar el email.

## 6. Requisitos no funcionales

- Ninguna transacción de base contiene I/O de red (se mantiene la separación de `REVIEW2-002`).
- Timeout total por envío acotado (default 10 s); una API lenta no bloquea el lote del worker.
- Sin dependencias nuevas: `RestClient` + Jackson ya están en el proyecto.
- Sin cambio de esquema ni de índice: el volumen de envíos no cambia.

## 7. Seguridad

- La API key se lee solo de entorno (`EMAIL_API_KEY`), nunca del repo ni de la base, y nunca se
  expone en DTOs ni en logs.
- Se envía únicamente por HTTPS; la URL es configurable solo para pruebas con servidor mock.
- Los enlaces siguen sin exponer el email del estudiante ni datos de contacto.
- No se agrega ningún endpoint público: el worker sigue siendo el único emisor.

## 8. Invariantes

- **I1** Un email nunca se envía antes del commit de la acción que lo origina (heredado de
  `REVIEW2-002`).
- **I2** El registro sigue siendo genérico: el código de respuesta no revela si el email existía.
- **I3** Un fallo de email no revierte la acción de negocio ya commiteada.
- **I4** Sin transporte configurado no se crea ninguna cuenta.

## 9. API contract

Sin cambios. `POST /api/auth/register` sigue respondiendo `202` (o `503` sin transporte) y
`POST /api/conversations` sigue igual. El frontend no se toca.

## 10. Datos afectados

Ninguno. No hay migración: la tabla `notification_outbox` y sus estados no cambian.

## 11. Configuración

| Variable | Default | Uso |
|---|---|---|
| `EMAIL_TRANSPORT` | `smtp` | `smtp` \| `brevo` |
| `EMAIL_API_URL` | `https://api.brevo.com/v3/smtp/email` | Endpoint del proveedor (mockeable en tests) |
| `EMAIL_API_KEY` | *(vacío)* | API key de Brevo; obligatoria si `EMAIL_TRANSPORT=brevo` |
| `EMAIL_API_CALL_TIMEOUT` | `PT10S` | Timeout de conexión/lectura |
| `MAIL_FROM` | `no-reply@claseya.app` | Remitente; en Brevo debe estar **verificado** |
| `MAIL_*` | — | Sin cambios; se siguen usando con `EMAIL_TRANSPORT=smtp` |

Precondición operativa (fuera del código): en Brevo hay que verificar el remitente o autenticar el
dominio con SPF/DKIM. Sin eso, el proveedor rechaza el envío con 4xx permanente.

## 12. Criterios de aceptación

- **CA-1** Unit `BrevoEmailSenderTest` con `MockRestServiceServer`: 201 → no lanza; 401 → permanente;
  429 y timeout → reintentable; el payload envía remitente, destinatario, asunto y `textContent`.
- **CA-2** Unit de `SmtpEmailSender`: `isConfigured()` según host y disponibilidad de
  `JavaMailSender`; el envío usa el remitente configurado.
- **CA-3** Test de selección: `EMAIL_TRANSPORT=brevo` registra el bean HTTP; `smtp` (y ausente)
  registra el SMTP.
- **CA-4** Arranque con `brevo` y sin API key falla con mensaje explícito.
- **CA-5** `AccountActivationIntegrationTest` y `NotificationOutboxIntegrationTest` siguen verdes
  sin cambios (las interfaces se conservan).
- **CA-6** Smoke manual en Render Free: registro real → llega el email de activación → el link
  activa la cuenta; contacto a un profesor → llega el aviso.
- **CA-7** Con `EMAIL_TRANSPORT=brevo` y una key inválida, la fila queda `DEAD` en el primer intento
  (no consume los 5 reintentos).

## 13. Out of scope

- ClamAV: `SCAN_ENABLED=true` sigue siendo obligatorio para arrancar, pero sin un ClamAV alcanzable
  la **subida de evidencia docente responde 503**. Es una limitación conocida del deploy gratuito y
  se resuelve en otra spec (`STOR-001`/infra), no acá.
- Emails HTML, plantillas, adjuntos, rebotes/webhooks del proveedor, supresión de direcciones.
- Segundo proveedor HTTP, fallback automático entre transportes, migración de los emails ya `DEAD`.
- Retirar SMTP o tocar `ProductionConfigValidator`.
- Cambios en el frontend o en el `vercel.json`.

## 14. Change budget

- **Scope:** puerto `EmailSender` + dos transportes (SMTP y Brevo) + delegación de los dos senders
  existentes + `application.yml` + tests + docs + `ADR-030`.
- **Archivos esperados:**
  - nuevos: `notification/service/EmailSender.java`, `SmtpEmailSender.java`, `BrevoEmailSender.java`
  - modificados: `ConfigurableActivationLinkSender.java`, `ConfigurableContactNotificationSender.java`,
    `src/main/resources/application.yml`
  - tests nuevos: `BrevoEmailSenderTest`, `SmtpEmailSenderTest`, selección de transporte
  - docs: `README.md` (tabla de variables), `docs/auth.md`,
    `docs/architecture/decisions/ADR-030-http-email-transport.md`
- **Migraciones esperadas:** ninguna.
- **Dependencias nuevas esperadas:** ninguna (se usa `RestClient` de Spring).

## 15. Definition of Done

- [x] RF-1..RF-7 implementados y trazables a tests.
- [x] `mvn -B clean test` en verde (377 tests, unit + integración con Testcontainers).
- [x] Ningún log expone API key, tokens ni links.
- [x] El contrato de API y el frontend quedan intactos.
- [x] `README.md`, `docs/auth.md` y `ADR-030` actualizados.
- [ ] Smoke en Render Free con Brevo documentado con evidencia (captura o log del envío).
