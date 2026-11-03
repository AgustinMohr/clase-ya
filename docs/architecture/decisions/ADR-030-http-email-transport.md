# ADR-030 — Transporte de email por API HTTPS además de SMTP

**Estado:** Aceptado (2026-10-30)
**Relacionado:** `NOTIF-001`, `REVIEW2-002`, `ADR-029`, `ADR-021`.

## Contexto

El backend envía el email de activación y el aviso de contacto con `JavaMailSender` (SMTP, puerto
587 por defecto) desde `ADR-021` y `ADR-029`. El deploy actual corre en **Render Free**, que bloquea
el tráfico saliente en los puertos `25`, `465` y `587` (el `25` está bloqueado en todos los planes).

El problema no es solo que el envío falle: `isConfigured()` mira si `spring.mail.host` está
definido, no si el proveedor es alcanzable. Con el host configurado, el registro responde `202`, la
cuenta queda `PENDING`, el worker reintenta cinco veces y la fila termina `DEAD`. El usuario nunca
recibe el link y la UI no muestra ningún error.

Las salidas evaluadas fueron: pagar un plan de Render (habilita 587, pero agrega costo y no resuelve
el escaneo de evidencia), cambiar de host (traslada el problema, sin garantía de que el nuevo
permita SMTP) o cambiar el transporte a HTTPS, que sí pasa en el plan gratuito.

## Decisión

1. **Un puerto `EmailSender`** (`isConfigured()` + `send(to, subject, text)`) como única salida. La
   activación y el aviso de contacto solo componen el mensaje y delegan; el worker del outbox no
   conoce el transporte.
2. **Dos implementaciones:** `SmtpEmailSender` (comportamiento previo, seleccionado por defecto) y
   `BrevoEmailSender` (`POST` HTTPS con header `api-key` y payload JSON
   `sender`/`to`/`subject`/`textContent`). La selección es
   `app.notification.email.transport` (`smtp` | `brevo`).
3. **Timeouts acotados.** El `RestClient` del transporte HTTP se construye una vez con timeout de
   conexión y lectura (`EMAIL_API_CALL_TIMEOUT`, default `PT10S`). El envío sigue ocurriendo en el
   worker, fuera de cualquier transacción de base.
4. **Clasificación de errores.** `400`/`401`/`403` del proveedor → `PermanentNotificationException`
   (fila `DEAD`, sin quemar reintentos en una API key inválida o un remitente no verificado).
   `429`, `5xx`, timeout y error de I/O → `NotificationDeliveryException` (backoff existente).
5. **El fallback `log-links` se queda en cada sender.** Las variables son distintas por flujo
   (`AUTH_LOG_ACTIVATION_LINKS` y `MESSAGE_LINK_LOG`) y loguear un link es una decisión del flujo,
   no del transporte.
6. **El guard de arranque no cambia.** `ProductionConfigValidator` sigue exigiendo
   `CAPTCHA_ENABLED=true` y `SCAN_ENABLED=true` fuera de `local`/`test`.

## Consecuencias

- **Positivas:**
  - El deploy gratuito puede entregar emails sin tocar el contrato de API, el esquema ni el frontend.
  - SMTP sigue disponible donde funcione (local, hosts pagos), sin ramas en el código de negocio.
  - Los tests de integración no cambian: mockean `ActivationLinkSender` y `ContactNotificationSender`,
    que conservan su firma.
- **Costos / riesgos:**
  - Dependencia de un proveedor y de su cuota (Brevo Free: ~300 emails/día). Un `429` se reintenta,
    pero un exceso sostenido termina en `DEAD`.
  - El remitente debe estar verificado (o el dominio autenticado con SPF/DKIM) o el proveedor
    responde `4xx` permanente.
  - No hay deduplicación: si el proveedor acepta el mail y la respuesta se pierde, el reintento
    puede duplicarlo. Riesgo aceptado: el link es de un solo uso y el duplicado no habilita nada
    nuevo.
  - Cada proveedor nuevo requiere su adaptador; las APIs no son compatibles.
  - **No** resuelve ClamAV: sin un escáner alcanzable, la subida de evidencia docente sigue
    respondiendo `503`.
- **Alternativas descartadas:** plan pago de Render; relajar `ProductionConfigValidator` o activar
  el perfil `local` en producción (desactivaría controles de seguridad); migrar de host.
