# REVIEW2-002 — Outbox transaccional y notificación de contactos

**Estado:** Propuesta (pendiente de REVIEW humano).
**Origen:** `review2.md` TX-01 y el hueco funcional del PRD §7. **Nivel:** 2.

## 1. Objetivo

Sacar el envío de email y las llamadas a ClamAV/S3 de las transacciones de base, y usar ese mismo
mecanismo para notificar al profesor cuando recibe un contacto (hoy no se entera hasta que entra a
la app).

## 2. Contexto

- `AuthService.register` envía el email de activación dentro de la transacción; un SMTP lento retiene
  una conexión y el lock del índice único de email (`AccountActivationService:59-73`).
- `VerificationDocumentService.upload` escanea (ClamAV) y sube (S3) dentro de `@Transactional`
  (`:62-109`). Con Hikari en 10 conexiones, una ráfaga de uploads agota el pool.
- El PRD §7 exige notificar al profesor con un deep link; `ConversationService.start` no dispara
  ningún aviso. `AGENTS.md` y `ADR-024` lo dejan fuera de alcance hasta que exista spec: esta spec
  es esa puerta.
- Timeouts de SMTP/S3 ya agregados como mitigación (commit de TX-01 parcial).

## 3. Actores

STUDENT (inicia contacto), TEACHER (recibe el aviso), ADMIN (reintentos/observabilidad), sistema
(worker).

## 4. Requisitos funcionales

- **RF-1** Toda acción que deba enviar un email encola una fila en `notification_outbox` **dentro**
  de la transacción; el envío ocurre **después** del commit.
- **RF-2** Un worker toma pendientes con `FOR UPDATE SKIP LOCKED`, marca `SENDING` con
  `locked_until` (lease de 2 min), envía con el proveedor configurado y marca `SENT`/`FAILED` con
  `attempts`, `last_error`, `next_attempt_at`. Un `SENDING` con lease vencido vuelve a `PENDING`
  (worker caído).
- **RF-3** Reintentos con backoff exponencial (1 min, 5, 15, 60, 240) y tope de 5 intentos;
  agotado el tope queda `DEAD` y se registra métrica/alerta.
- **RF-4** Al crear un contacto (`POST /api/conversations`), se encola un aviso al profesor con un
  enlace firmado de un solo uso (TTL 7 días) que abre `/mensajes/:id`. La clave de deduplicación es
  `contact:<conversationId>`: un mensaje posterior en el mismo hilo **no** genera otro aviso (el PRD
  pide avisar del contacto, no de cada mensaje).
- **RF-5** El enlace no revela el email del estudiante ni datos de contacto.
- **RF-6** El outbox cubre **fallos transitorios**, no la ausencia de transporte. Sin SMTP
  configurado el registro sigue respondiendo **503 y no crea la cuenta**: con email+contraseña como
  camino de primera clase (PRD §4), crear una cuenta que nadie puede activar es peor que fallar. Con
  transporte configurado, un fallo de envío después del commit deja la fila `PENDING`/`FAILED` y el
  worker reintenta. Opcional en REVIEW: extender `ProductionConfigValidator` para exigir `MAIL_HOST`
  fuera de `local`/`test`.
- **RF-7** El upload de evidencia escanea y guarda **antes** de abrir la transacción de persistencia;
  si la persistencia falla, se borra el objeto huérfano (ya existe el cleanup). La cuota (5
  documentos / 25 MB) se valida **de nuevo** dentro de la transacción: si el escaneo ocurre antes,
  dos uploads concurrentes podrían pasarse del tope. Mitigación: reservar la cuota con un
  `SELECT ... FOR UPDATE` sobre la credencial o un contador atómico.

## 5. Requisitos no funcionales

- Ninguna transacción de base contiene I/O de red de duración variable.
- El worker es idempotente: reintentar un envío no duplica el aviso visible (clave de deduplicación
  por evento).
- Latencia objetivo del aviso: < 60 s en el 95 % (poll cada 15 s).

## 6. Seguridad

- El payload del outbox es interno: no se loguea el token del deep link ni el cuerpo completo.
- El token del enlace se guarda hasheado (SHA-256) y es de un solo uso.
- El worker corre en el backend, sin endpoint público.
- No se envía email a direcciones no verificadas para avisos de contacto.

## 7. Invariantes

- I1: un email nunca se envía antes del commit de la acción que lo origina.
- I2: un evento no genera más de un email entregado.
- I3: un fallo de email no revierte la acción de negocio (contacto/registro).

## 8. Datos afectados

Migración `V13__notification_outbox.sql`:

```sql
create table notification_outbox (
  id uuid primary key default gen_random_uuid(),
  event_type varchar(40) not null,
  recipient_user_id uuid not null references users (id) on delete cascade,
  dedupe_key varchar(120) not null unique,
  payload jsonb not null,
  status varchar(20) not null default 'PENDING'
    check (status in ('PENDING','SENDING','SENT','FAILED','DEAD')),
  attempts int not null default 0,
  next_attempt_at timestamptz not null default now(),
  last_error text,
  created_at timestamptz not null default now(),
  sent_at timestamptz
);
create index idx_notification_outbox_due on notification_outbox (next_attempt_at)
  where status in ('PENDING','FAILED');
```

Tabla de tokens de deep link: **se crea una tabla propia** `message_link_tokens` (o
`one_time_tokens` con `purpose`), **no** se reutiliza `email_verification_tokens`: distinto
propósito, TTL y audiencia. Reutilizarla acopla dos flujos de seguridad y complica la revocación.

## 9. Criterios de aceptación

- AC-1: con SMTP caído, `POST /api/conversations` responde 201 y la fila queda `PENDING`/`FAILED`;
  el contacto existe.
- AC-2: al volver SMTP, el worker entrega y marca `SENT` sin duplicar.
- AC-3: dos contactos del mismo par no generan dos conversaciones (RACE-01) ni dos avisos.
- AC-4: upload con S3 caído responde 503 y no deja fila ni objeto.
- AC-5: un test con SMTP lento (stub) demuestra que no hay conexión de base retenida.

## 10. Out of scope

- Notificaciones push, WebSockets o preferencias de frecuencia.
- Reintento de emails de marketing.
- Migrar la evidencia a URLs prefirmadas.

## 11. Definition of Done

- [ ] Spec aprobada (y ADR si se reemplaza el envío inline de AUTH-001).
- [ ] Migración `V13` justificada.
- [ ] Worker con métricas (`sent`, `failed`, `dead`) y logs con `requestId`/`outboxId`.
- [ ] Tests de worker (idempotencia, backoff, tope), de registro sin SMTP y de contacto.
- [ ] `docs/architecture/security.md` y `docs/messaging.md` actualizados.
