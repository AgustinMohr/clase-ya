# ADR-029 — El email de activación pasa por el outbox

**Estado:** Aceptado (aprobado en REVIEW del 2026-10-29, junto con `REVIEW2-002`).
**Origen:** `REVIEW2-002` RF-1/RF-6. **Reemplaza:** el envío inline de `AUTH-001`/`ADR-021`.
**Relacionado:** `V13`/`V14`, `docs/architecture/security.md`.

## Contexto

`AuthService.register` guardaba el usuario y enviaba el email de activación **dentro** de la misma
transacción (`AccountActivationService.issueAndSend`). Con un SMTP lento, la conexión del pool y el
lock del índice único de `users.email` quedaban tomados durante la llamada de red. Los timeouts de
SMTP agregados en TX-01 acotan el daño pero no lo eliminan.

`REVIEW2-002` (RF-1) exige que **todo** email se encole dentro de la transacción y se envíe después
del commit, y (RF-6) que sin transporte configurado el registro siga respondiendo **503 sin crear la
cuenta**.

## Decisión

1. `AccountActivationService.issueAndEnqueue` crea el token (solo el hash, como en `ADR-021`) y
   encola `ACTIVATION` en `notification_outbox` con `dedupe_key = activation:<tokenId>`.
2. **Precheck sincrónico de transporte**: `ActivationLinkSender.isConfigured()` se evalúa antes de
   crear la cuenta. Si no hay SMTP ni el fallback de desarrollo, lanza
   `ServiceUnavailableException` y la transacción revierte, así que no queda una cuenta PENDING que
   nadie puede activar. `resend` hace el mismo chequeo **antes** de buscar la cuenta, para no
   reintroducir enumeración de emails.
3. El worker (`NotificationOutboxWorker`) envía después del commit. Un fallo transitorio reintenta
   con el backoff de RF-3; uno permanente (sin transporte, cuenta borrada) va directo a `DEAD`.
4. **El token crudo viaja en el payload** porque no se puede reconstruir desde el hash guardado. El
   worker lo borra (`payload - 'token'`) al marcar `SENT` o `DEAD`: la credencial solo está en reposo
   mientras la fila está pendiente.
5. `V14` amplía el CHECK de `event_type` (no se edita `V13`, que puede estar aplicada).

## Consecuencias

- + Ninguna transacción de base contiene I/O de SMTP.
- + Un SMTP caído ya no revierte el registro: la cuenta queda PENDING y el worker reintenta.
- + Sin transporte, la respuesta sigue siendo 503 y la cuenta no se crea (RF-6).
- + El enlace de activación sigue guardado solo como hash en `email_verification_tokens`; el token
  crudo en `notification_outbox` es transitorio y se elimina al terminar la entrega.
- − Entre el encolado y la entrega el token crudo está en `jsonb`. Queda acotado por el poll (15 s)
  y, con SMTP caído, por la ventana de reintentos (hasta ~5,5 h). Es el precio de no inventar una
  clave de cifrado para un secreto de vida corta.
- − La entrega es *at-least-once*: si el envío tiene éxito y falla el `markSent`, se puede repetir
  el mismo email. El enlace es el mismo (idempotente para el usuario); no se emite un token nuevo.
- − Un fallo permanente después del commit deja la cuenta PENDING sin email; ADMIN puede activarla
  con `activateManually` y la fila queda `DEAD` con el motivo.

## Alternativas descartadas

- **Mantener el envío inline**: era el estado anterior; no cumple RF-1.
- **Que el worker cree el token al enviar**: evita el token crudo en reposo, pero un reintento tras
  un `markSent` perdido invalida el enlace que ya está en la bandeja del usuario. Peor experiencia y
  peor diagnóstico.
- **Cifrar el payload**: agrega gestión de claves para un secreto de 24 h; desproporcionado hoy.

## Criterios de aceptación

- AC-1: sin transporte, `POST /api/auth/register` responde 503 y no deja fila en `users`.
- AC-2: con transporte, el registro responde 202, deja el usuario PENDING y una fila `ACTIVATION`
  `PENDING`; el worker la entrega y la marca `SENT` sin el token en el payload.
- AC-3: un fallo transitorio del sender reintenta con backoff y termina `DEAD` en el 5.º intento.
- AC-4: `resend-activation` con email desconocido responde 202 y no llama al sender.
