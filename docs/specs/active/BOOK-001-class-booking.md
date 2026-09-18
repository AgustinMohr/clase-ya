# BOOK-001 — Agendamiento de clases

**ID:** BOOK-001
**Estado:** DEPRECATED (dirección de producto descartada) · **Carpeta:** `docs/specs/active/`
**Última actualización:** 2026-09-02 (Fase 0, gobernanza)

> **Nota del director (Fase 0 — 2026-09-02):** BOOK-001 queda **DEPRECATED como rumbo de producto**.
> ClaseYa es un **directorio + contacto seguro** (sin reservas ni agenda online, sin Calendly) según
> `docs/product/product-requirements.md`. Este documento y su plan se conservan como historia (no se
> borran); no implementar hasta que el director los reactive. La dirección de contacto seguro
> reemplaza la idea de "agendamiento" (ver future specs OAUTH-001 / CONTACT-001 / REVIEW-001).

---

## 1. Objetivo

Permitir que un estudiante agende una clase con un profesor **usando Calendly como motor de
scheduling** y que ClaseYa represente la relación `Student ↔ Teacher ↔ CareerSubject` como un
**Booking** local sincronizado con el evento externo de Calendly. Calendly controla la agenda; el
Booking es una entidad de producto de ClaseYa.

## 2. Contexto (estado real del repositorio, verificado)

- Schema FASE 1 (sin lógica): `bookings` (`student_id`, `teacher_id`, `career_subject_id` NOT
  NULL, `scheduled_at` NOT NULL, `duration_minutes` CHECK 1..480, `mode`, `status`,
  `calendly_event_id` nullable, timestamps) y `calendly_integrations` (1:1 teacher, tokens OAuth,
  sin uso). Enums `BookingMode {ONLINE, IN_PERSON}`, `BookingStatus {PENDING, CONFIRMED,
  CANCELLED, COMPLETED}`. No hay repository/service/controller/webhook/OAuth ni tests de bookings.
- Última migración: `V4`. ADRs vigentes: ADR-001..012.
- Convenciones (AGENTS.md + docs/architecture): identity por `SecurityContext` (`CurrentUser`),
  DTOs nunca entidades, `ApiError`, roles `ROLE_*`, profesor agendable solo si `VERIFIED` + `ACTIVE`,
  timestamps `timestamptz` UTC, paginación con `SearchResultPage`, migrationes nuevas `V{n+1}`.

## 3. Decisiones arquitectónicas adoptadas (cerradas)

| # | Decisión |
|---|----------|
| D1 | **Calendly es el sistema de scheduling**: disponibilidad, horarios, conflictos, selección de franja, creación del evento, agenda y zona horaria del profesor. ClaseYa **no** implementa calendario/disponibilidad propios. |
| D2 | `career_subject_id` **permanece OBLIGATORIO**. No se hace migración para bookings sin materia. Todo Booking está ligado a un `CareerSubject`. |
| D3 | **Sin motor de solapamientos propio.** Calendly evita conflictos. ClaseYa garantiza unicidad/integridad de sus Bookings (un evento externo → un Booking; sin duplicados locales). |
| D4 | **Sin aprobación manual del profesor.** No hay "aceptar booking". La transición hacia confirmado la produce la sincronización con Calendly. |
| D5 | Estados = enum existente; máquina `PENDING→CONFIRMED`, `PENDING→CANCELLED`, `CONFIRMED→CANCELLED`, `CONFIRMED→COMPLETED`; terminales `CANCELLED`, `COMPLETED`. El cliente jamás envía `status`. `COMPLETED` no es accionable por STUDENT/TEACHER (mecanismo futuro/automático, fuera de BOOK-001). |
| D6 | **OAuth NO es requisito de V1 (Alternativa A)**. `CalendlyIntegration` queda **fuera del flujo crítico de BOOK-001** y se reserva para una futura integración OAuth. V1 necesita únicamente la **URL pública de scheduling del profesor** (ver §12). |
| D7 | **Webhook autenticado por firma oficial de Calendly** (no por rol de usuario): header `Calendly-Webhook-Signature` = `t=<unix>,v1=<hmac-sha256 hex>` sobre `t + "." + raw body`, con la `webhook signing key` (secret de entorno). Tolerancia anti-replay (p. ej. 3 min). **Corrige** la hipótesis HMAC genérica anterior. |
| D8 | **Timezone**: persistencia/lógica UTC (`Instant`/`timestamptz`); Calendly maneja la zona de la agenda del profesor; el display local es del frontend. Nunca `LocalDateTime` como representación de dominio. |
| D9 | Source of truth: **Calendly** = disponibilidad/franja/evento externo; **ClaseYa** = Booking/relación/materia/historial/estado de negocio/ownership/permisos/reviews futuras. |

## 4. Punto en decisión humana (NO cerrado)

> **BLOCKER — HUMAN ARCHITECTURE DECISION REQUIRED (correlación Student ↔ Event y ciclo de vida del Booking).**
Decisión cerrada: correlación mediante BookingIntent + UTM utm_content.

## 5. Actores

| Actor | Capacidades (BOOK-001) |
|-------|------------------------|
| **STUDENT** | Desde ClaseYa: iniciar el flujo "agendar con profesor X" (que abre/redirige a Calendly); consultar sus bookings; ver detalle. Cancelación: **pendiente de la decisión humana** (en V1 se espera que la cancelación ocurra en Calendly y se refleje por webhook). |
| **TEACHER** | Consultar sus bookings y ver el detalle de sus clases. Administra su agenda/disponibilidad **en Calendly**. Sin acción de "aceptar/rechazar" en ClaseYa. |
| **ADMIN** | Sin panel nuevo de bookings en BOOK-001. |
| **Calendly** | Scheduling, creación/cancelación/reprogramación del evento, webhooks. |

El `student` siempre deriva del `SecurityContext`; el cliente no manda `studentId` ni `status`.

## 6. Flujo objetivo (V1)

```
Student (autenticado)
 → ClaseYa: "agendar con Teacher" (careerSubject + mode)
 → ClaseYa entrega la Calendly URL del profesor (+ contexto de la materia si el flujo lo permite)
 → Student agenda en Calendly (franja, conflicto, timezone → Calendly)
 → Calendly crea el evento
 → Webhook (firmado) → ClaseYa
 → ClaseYa crea/confirma el Booking local (correlación Student ↔ Event: ver decisión humana)
```

Cancelación/reprogramación: suceden en Calendly → webhook → estado local (CONFIRMED→CANCELLED;
reprogramación → actualizar `scheduled_at` desde el evento legítimo).

## 7. Requisitos funcionales

- **RF-1** (estudiante autenticado, rol STUDENT, cuenta `ACTIVE`, con `StudentProfile`) inicia un
  flujo de booking hacia un profesor `VERIFIED`+`ACTIVE`. Si el profesor no cumple visibilidad → 404.
- **RF-2** El profesor debe tener configurada una **Calendly scheduling URL** para poder ser
  agendado (si no la tiene, el flujo informa que no está disponible para agendar).
- **RF-3** El inicio de flujo valida `careerSubject` (existente y activo) y `mode` (y la modalidad
  del profesor cuando corresponda), pero **no** recibe ni `scheduled_at` ni `duration` del
  frontend (eso lo elige Calendly). `duration_minutes` y `scheduled_at` se llenan desde el evento
  externo (Calendly), no desde el formulario.
- **RF-4** El webhook firmado correlaciona el evento externo con un Booking local y produce la
  transición de estado definida (mecanismo de correlación: ver §16 / plan).
- **RF-5** El estudiante puede listar (paginado) y ver detalle de sus bookings.
- **RF-6** El profesor puede listar (paginado) y ver detalle de los bookings donde es `teacher`.
- **RF-7** Webhooks idempotentes (duplicados no generan dobles bookings ni cambios repetidos);
  eventos desconocidos/fuera de orden se manejan de forma definida (ver §14).
- **RF-8** El estado nunca llega del cliente; lo gobierna el service/webhook.
- **RF-9** Respuestas como DTOs; nunca entidades ni datos sensibles del contraparte (ver §15).

## 8. Requisitos no funcionales

- **RNF-1** Listados sin N+1 (patrón queries batch ya usado). 
- **RNF-2** Toda fecha/hora externa se convierte a UTC antes de persistir.
- **RNF-3** Idempotencia de webhooks y ausencia de duplicados locales garantizados por service +
  constraint (ver §12).
- **RNF-4** Si Calendly no está disponible: el flujo de inicio puede fallar con error claro o
  indicar indisponibilidad; nunca se persiste un booking corrupto.
- **RNF-5** Nunca se loguean/devuelven tokens ni secretos.

## 9. Máquina de estados (definitiva)

```
PENDING → CONFIRMED   (evento creado/confirmado en Calendly — vía webhook)
PENDING → CANCELLED   (cancelación antes de confirmar, según decisión humana)
CONFIRMED → CANCELLED (cancelación en Calendly → webhook; u otra fuente aprobada)
CONFIRMED → COMPLETED (futuro/automático; fuera de BOOK-001)
```

Terminales: `CANCELLED`, `COMPLETED`. No hay transición inversa; desde terminal no se vuelve.
`PENDING` solo existe si la decisión humana lo mantiene (ver §16); si el Booking se crea solo tras
el evento, la creación normal cae directamente en `CONFIRMED` (decisión humana pendiente).

## 10. Invariantes

- **INV-1** Todo booking tiene un único `student` (rol STUDENT con perfil) y un único `teacher`
  visible (VERIFIED + ACTIVE) al crear.
- **INV-2** `careerSubject` es obligatorio (NOT NULL) y debe existir/estar activo.
- **INV-3** `scheduled_at` UTC; `duration_minutes` 1..480; `mode` ∈ {ONLINE, IN_PERSON}.
- **INV-4** Un evento externo (`calendly_event_id`) solo puede estar ligado a un Booking (UNIQUE).
- **INV-5** Ownership: solo student/teacher dueños acceden; terceros → 404.
- **INV-6** El estado lo cambia el service/webhook; nunca el cliente.
- **INV-7** No se implementa disponibilidad/solapamiento en ClaseYa (regla D3).

## 11. API (conceptual; se cierra en PLAN/IMPLEMENT)

| Método | Path | Actor | Propósito |
|--------|------|-------|-----------|
| `POST` | `/api/bookings` | STUDENT | Iniciar flujo de agendado (indica `teacherId`, `careerSubjectId`, `mode`). Devuelve el booking local y/o la URL de Calendly según la decisión humana de ciclo de vida. |
| `GET` | `/api/bookings?page=&size=` | dueño | Listar bookings propios. |
| `GET` | `/api/bookings/{id}` | dueño | Detalle. 404 si no es dueño. |
| `POST` | `/api/bookings/{id}/cancel` | (según decisión humana) | Cancelación (si se aprueba; en V1 puede ser solo vía Calendly). |
| `POST` | `/api/webhooks/calendly` | (sin rol; firma) | Recibir eventos; responder 2xx (idempotente) / 4xx (firma inválida). |
| `GET` | `/api/teachers/{id}` | público | Debe exponer la scheduling URL del profesor cuando la tenga (o un flag "agendable"). |

El frontend **no** envía `scheduledAt`/`durationMinutes`/`status`/`studentId`. (La forma exacta del
request de creación depende de la decisión humana de correlación/ciclo de vida.)

## 12. Datos / impacto de base de datos

- Sin cambios a `V1..V4`. Migración **V5** (una sola, justificada) propuesta:
  - `ALTER TABLE teacher_profiles ADD COLUMN calendly_scheduling_url varchar(500)` (nullable) —
    punto de entrada a la agenda del profesor (decisión D6).
  - `CREATE UNIQUE INDEX uq_bookings_calendly_event_id ON bookings (calendly_event_id)` — un evento
    externo → un Booking (permite NULLs múltiples; PostgreSQL).
- `career_subject_id` sigue NOT NULL (D2). No se agregan otros índices redundantes (ya existen
  `idx_bookings_student_id/teacher_id/scheduled_at/status`).
- `CalendlyIntegration` **no se modifica ni se elimina** (queda para OAuth futuro).

## 13. Calendly — responsabilidades y contrato

- Calendly es source of truth de disponibilidad/franja/evento. ClaseYa es source of truth del
  Booking de producto.
- Integración mínima V1 = **URL pública de scheduling del profesor + webhooks**, sin cliente OAuth.
- **Webhook (verificado contra docs oficiales)**: header `Calendly-Webhook-Signature`
  (`t=...,v1=...`); payload firmado = `t + "." + raw body`; HMAC-SHA256 con la `webhook signing
  key` (secret por entorno); tolerancia anti-replay (3 min recomendada). Eventos de interés:
  `invitee.created`, `invitee.canceled` (y, si aplica, `invitee.no_show`) — filtrables por
  `scheduled_event.event_type`. El payload expone al invitee (email/nombre) y al evento
  (`scheduled_event` uri/uuid + timestamps).
- Duplicados → idempotencia por `calendly_event_id`; fuera de orden → comparar una marca temporal
  del evento (a fijar contra payload real en el spike); desconocido/sin booking local → registro
  controlado (ver §16) o descarte con log; retry de Calendly → mismo tratamiento idempotente.
- ClaseYa **no** cancela eventos en Calendly en V1 sin OAuth; si se aprueba cancelación por API del
  estudiante, requiere decisión (ver §16). Las cancelaciones vía Calendly se reflejan por webhook.

## 14. Idempotencia / sincronización

- Un evento externo → un Booking (UNIQUE V5 + service).
- Webhook duplicado → no-op.
- Fuera de orden → aplicar solo si la marca del evento es más nueva que la última aplicada al
  Booking; descartar las más viejas.
- Evento desconocido localmente (sin booking) → comportamiento definido en la decisión humana
  (p. ej., si se crea el Booking al recibir el webhook, no aplica; si se crea antes, es el caso de
  correlación). Sin estados inconsistentes: transaccional (`@Transactional`).

## 15. Seguridad y privacidad

- Identity desde `SecurityContext`; sin `studentId` del cliente. Ownership por dueño; no-dueños →
  404. Roles según §5.
- Webhooks: autenticidad por firma (sin rol); secretos solo por entorno; no loguear tokens ni
  payloads sensibles completos.
- DTOs sin: emails ajenos innecesarios, coordenadas exactas, `passwordHash`, tokens, secretos.
  El estudiante ve lo público del profesor (incl. scheduling URL); el profesor ve la identidad
  mínima del estudiante (sin email salvo decisión explícita).
- Replay/forged webhook → rechazo por firma + tolerancia de timestamp.

## 16. Decisión humana pendiente (BLOCKER)

1. **¿Cuándo se crea el Booking local y cómo se correlaciona con el evento?** El `scheduled_at` es
   NOT NULL y Calendly elige la franja, por lo que pre-crear un `PENDING` sin evento no tiene
   franja (choca con el schema). Alternativas:
   - **A (recomendada)**: el Booking se crea al recibir el webhook (evento ya tiene franja) y la
     creación normal cae en `CONFIRMED`; `PENDING` se reserva para bordes/async. Correlación por
     `invitee.email` (normalizado) contra el User ACTIVE; ClaseYa abre Calendly con el email del
     estudiante autenticado (prefill si el contrato lo permite) y valida el match; si no hay match,
     el evento queda en cuarentena controlada (log/flag admin) sin crear Booking.
   - **B**: pre-crear Booking `PENDING` exige franja previa → obligaría a pedir franja en ClaseYa
     o a relajar `scheduled_at` (cambio de schema NO autorizado). No recomendada.
2. **¿Cancelación por API del estudiante en V1?** Sin OAuth, ClaseYa no puede cancelar el evento en
   Calendly. Opciones: (a) cancelación solo vía Calendly + webhook (recomendada V1); (b) incluir
   cliente OAuth de Calendly (amplía alcance/dependencias). 
3. Confirmar **prefill de email** en la Calendly URL (capacidad a validar en spike) para reforzar el
   match por email.

Ver `docs/plans/BOOK-001-plan.md`.

## 17. Threat model (resumen)

| Amenaza | Riesgo | Mitigación | BOOK-001 |
|---------|--------|------------|----------|
| IDOR (ver/cancelar booking ajeno) | Alto | Ownership + `CurrentUser`; 404 a no-dueños; tests | Sí |
| Forged webhook | Alto | Firma `Calendly-Webhook-Signature` + tolerancia timestamp | Sí |
| Replay webhook | Medio | Idempotencia + timestamp tolerance | Sí |
| Duplicate webhook | Medio | Idempotencia (no-op) | Sí |
| Webhook fuera de orden | Medio | Comparar marca del evento (definida en spike) | Sí |
| Cancelación no autorizada | Alto | Máquina de estados + dueño; fuentes de cancelación aprobadas | Sí (según decisión) |
| Compromiso proveedor externo | Medio | Validar firma; estado local gobernado; reconciliación manual futura | Parcial |
| Fuga de secretos (signing key, tokens) | Alto | Secretos por entorno; no loguear | Sí (cifrado de tokens: futuro, OAuth no es V1) |

## 18. Out of scope (confirmado)

Pagos, facturación/comisiones, marketplace, chat/videollamadas, reviews/rating (requiere
COMPLETED), notificaciones/email, WebSockets, panel admin de bookings, calendario/disponibilidad
propios, SDK/cliente OAuth de Calendly en V1 (solo URL + webhooks), implementación del mecanismo
COMPLETED, frontend.

## 19. Criterios de aceptación (Given/When/Then)

- **AC-001** (auth) Sin token → 401, sin crear nada.
- **AC-002** (rol) TEACHER intenta iniciar un booking → 403.
- **AC-003** (perfil) STUDENT sin `StudentProfile` → 409, no se crea perfil.
- **AC-004** (visibilidad) Profesor PENDING/REJECTED/INACTIVE/SUSPENDED → 404 al iniciar booking.
- **AC-005** (teacher sin scheduling URL) Iniciar booking con un profesor VERIFIED+ACTIVE sin
  `calendly_scheduling_url` → respuesta que indica "no disponible para agendar" (4xx/flag) y no
  crea booking.
- **AC-006** (careerSubject) `careerSubjectId` inexistente/inactivo → 400/404; nunca se crea
  booking sin materia.
- **AC-007** (sin campos del frontend) Request con `scheduledAt`/`durationMinutes`/`status` →
  campos rechazados/ignorados (no se aceptan del cliente).
- **AC-008** (flujo iniciado) Estudiante válido + teacher agendable + careerSubject válido →
  obtiene la Calendly URL/flujo correcto y se registra la intención si la decisión humana lo
  define.
- **AC-009** (webhook firma) Webhook con firma inválida → 4xx y sin efectos.
- **AC-010** (webhook crea booking — si se aprueba opción A) Evento válido con invitee que
  coincide con un User ACTIVE → Booking `CONFIRMED` con `scheduled_at`/`duration` del evento y
  `calendly_event_id` correcto.
- **AC-011** (webhook duplicado) Mismo evento dos veces → sin cambios la segunda.
- **AC-012** (fuera de orden) Evento más viejo que el último aplicado → descartado sin retroceder.
- **AC-013** (evento desconocido / sin match) Invitee sin User o evento sin booking local →
  comportamiento definido (cuarentena/descarte controlado), sin Booking fantasma.
- **AC-014** (cancelación) Cancelación en Calendly → webhook → Booking `CANCELLED` (si aplica la
  fuente aprobada). Transiciones inválidas → 409/400 y sin cambio.
- **AC-015** (un evento = un booking) Mismo `calendly_event_id` en dos bookings → constraint UNIQUE
  lo impide (409).
- **AC-016** (ownership) C consulta booking de A → 404; A lista solo los suyos (paginado, estable,
  sin duplicados).
- **AC-017** (timezone) Franja del evento persiste y devuelve el mismo instante UTC (sin
  corrimiento).
- **AC-018** (privacidad) Respuestas sin email/coordenadas/hashes/tokens del contraparte.
- **AC-019** (listado N+1) Listado de bookings del dueño con paginación no dispara N+1
  (query page + número constante de batch queries).

> AC-008..AC-014 que dependan de la decisión humana (momento de creación/correlación/cancelación)
> se concretarán cuando se resuelva el BLOCKER.

## 20. Definition of Done (BOOK-001)

- [ ] Spec aprobada (incl. resolución del BLOCKER) y ADR-013 creado (integración Calendly +
  Booking de producto + source of truth + scheduling URL del profesor).
- [ ] Módulo `booking` con máquina de estados gobernada; sin estados del cliente; sin
  disponibilidad/solapamiento propios; sin confirmación manual del profesor.
- [ ] `career_subject_id` sigue obligatorio; migración V5 (scheduling URL + UNIQUE evento) si el
  PLAN la confirma tras la decisión.
- [ ] Webhook firmado (`Calendly-Webhook-Signature`) e idempotente; correlación Student↔Event
  resuelta y cubierta por tests.
- [ ] Ownership/roles/IDOR y privacidad cubiertos por tests.
- [ ] Timezone UTC sin ambigüedad; sin secretos en logs.
- [ ] Docs actualizadas (spec/plan/ADR/domain/API según corresponda).
- [ ] `mvn -B clean test` en verde con los tests de BOOK-001.
- [ ] Change budget respetado; reportado al arquitecto/director.
