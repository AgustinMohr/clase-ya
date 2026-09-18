# PLAN — BOOK-001 · Agendamiento de clases (Calendly)

**Fuente:** `docs/specs/active/BOOK-001-class-booking.md` · **Estado:** DEPRECATED (dirección de
producto descartada; no implementar) · **No implementa nada.**

> **Nota del director (Fase 0 — 2026-09-02):** BOOK-001 queda deprecado como rumbo. ClaseYa es un
> **directorio + contacto seguro** (sin reservas/agenda/Calendly), según
> `docs/product/product-requirements.md`. Se conserva como historia.

---

## 1. Objetivo

Implementar, en el monolito modular existente, el módulo `booking` que permite a un estudiante
agendar una clase con un profesor usando **Calendly como motor de scheduling** y que mantiene un
**Booking** local (entidad de producto de ClaseYa) sincronizado con el evento externo vía webhook
firmado. Calendly NO es clonado (sin disponibilidad/solapamientos propios); OAuth NO es V1.

## 2. Estado actual del repositorio

- `bookings`, `bookings` indexes y `calendly_integrations` existen (FASE 1, schema puro).
  Entidades `Booking`, `BookingMode`, `BookingStatus`, `CalendlyIntegration` en `model`.
- `Booking.careerSubject` es NOT NULL y `scheduledAt` es NOT NULL.
- No hay repository/service/controller/DTO/test/webhook/OAuth de bookings.
- Última migración `V4`; ADRs 001-012; convenciones (DTOs, `ApiError`, `CurrentUser`,
  `SearchResultPage`, `@Transactional`, migrationes `V{n+1}`).
- `TeacherProfile` no tiene ningún campo de Calendly; `CalendlyIntegration` existe (OAuth tokens)
  pero no se usa.

## 3. Decisiones arquitectónicas (cerradas, ver spec §3)

D1 Calendly scheduling; D2 `careerSubject` obligatorio; D3 sin solapamientos propios; D4 sin
confirmación manual; D5 máquina de estados + sin `status` del cliente; D6 OAuth fuera de V1
(solo URL de scheduling + webhooks); D7 firma `Calendly-Webhook-Signature` (verificado contra
docs oficiales); D8 timezone UTC; D9 source-of-truth (Calendly agenda / ClaseYa Booking).

**Dato clave para la API:** como la franja se elige en Calendly, el frontend NO envía
`scheduled_at`/`duration_minutes`. Esos valores se llenan desde el evento (webhook).

## 4. Punto en decisión humana (NO resuelto aquí)

Ver § "Riesgos y BLOCKER". El resto del plan se diseña para que cualquiera de las dos opciones del
BLOCKER sea implementable con cambios mínimos y localizados (service de creación/correlación).

## 5. Flujo end-to-end (objetivo)

```
Student (autenticado)
 → POST /api/bookings  { teacherId, careerSubjectId, mode }     [ClaseYa valida rol/visibilidad/materia/modalidad]
 → Respuesta con la Calendly scheduling URL del profesor (y el booking PENDING si se aprueba la Opción B)
 → Student agenda en Calendly (franja/conflicto/zona → Calendly)
 → Calendly crea el evento
 → Webhook firmado (invitee.*) → POST /api/webhooks/calendly
 → ClaseYa valida firma → correlaciona (ver BLOCKER) → persiste/confirma Booking
     (calendly_event_id, scheduled_at, duration_minutes desde el evento)
 → Student/Teacher consultan sus bookings (GET /api/bookings)
```

Cancelación/reprogramación: en Calendly → webhook → estado local (`CONFIRMED→CANCELLED`;
reprogramación actualiza `scheduled_at` desde el evento legítimo). En V1 recomendado: sin endpoint
de cancelación por API del estudiante (ver BLOCKER-2).

## 6. Diseño por módulos

Módulo nuevo `com.claseya.booking` (mismo estilo que `favorite`/`messaging`):

```
booking/
├── controller/  BookingController · CalendlyWebhookController
├── service/     BookingService · CalendlyWebhookService
├── repository/  BookingRepository
└── dto/         StartBookingRequest · BookingResponse · (opcional) WebhookResult/Error
```

Cambios en módulos existentes (mínimos y localizados):
- `model`: `TeacherProfile.calendlySchedulingUrl` (nuevo campo).
- `teacher`: DTO de detalle público (+ scheduling URL) y service de perfil (persistir el campo).
- `security`: matcher público para `/api/webhooks/calendly` y roles para `/api/bookings/**`.
- `common`: sin cambios (se reutilizan excepciones y `ApiError`).

No se crean abstracciones nuevas (sin mapper dedicado: factories `from(entity)` en DTO, como el
resto del repo). La máquina de estados vive en `BookingService` (métodos privados transicionar),
sin framework.

## 7. Cambios archivo por archivo

| PATH | Tipo | Propósito | Cambios esperados |
|------|------|-----------|-------------------|
| `src/main/resources/db/migration/V5__add_calendly_scheduling_and_booking_events.sql` | CREATE | Soportar scheduling URL del profesor y unicidad de eventos externos | `ALTER TABLE teacher_profiles ADD COLUMN calendly_scheduling_url varchar(500)`; `CREATE UNIQUE INDEX uq_bookings_calendly_event_id ON bookings(calendly_event_id)`; (si el BLOCKER-1 se resuelve con Opción B: `ALTER TABLE bookings ALTER COLUMN scheduled_at DROP NOT NULL`). |
| `src/main/java/com/claseya/model/TeacherProfile.java` | MODIFY | Campo para la agenda pública | `@Column(name="calendly_scheduling_url") String calendlySchedulingUrl;` |
| `src/main/java/com/claseya/teacher/dto/CreateTeacherProfileRequest.java` | MODIFY | Permitir al profesor cargar su URL | Campo opcional `calendlySchedulingUrl` + `@Size`. |
| `src/main/java/com/claseya/teacher/dto/UpdateTeacherProfileRequest.java` | MODIFY | Ídem en update | Ídem. |
| `src/main/java/com/claseya/teacher/dto/TeacherPublicDetailResponse.java` | MODIFY | Exponer "agendar" al estudiante | Campo `schedulingUrl` (nullable) + mapeo en service. |
| `src/main/java/com/claseya/teacher/service/TeacherProfileService.java` | MODIFY | Persistir URL (create/update) | Aplicar campo editable; en update, null conserva el valor actual (mismo criterio que `name`). |
| `src/main/java/com/claseya/teacher/service/TeacherSearchService.java` | MODIFY | Incluir scheduling URL en detalle público | Pasar el valor en `getPublic`. |
| `src/main/java/com/claseya/booking/repository/BookingRepository.java` | CREATE | Acceso a bookings | `findById`, `findByCalendlyEventId`, page por `student.id` y por `teacher.id` (con join batch a perfiles), `existsByCalendlyEventId`. |
| `src/main/java/com/claseya/booking/dto/StartBookingRequest.java` | CREATE | Request de inicio | `@NotNull teacherId, careerSubjectId`; `@NotNull BookingMode mode`. Sin `scheduledAt`/`duration`/`status`. |
| `src/main/java/com/claseya/booking/dto/BookingResponse.java` | CREATE | DTO de booking | id, teacher (público mínimo), student (público mínimo), careerSubject (id+nombre), mode, scheduledAt, durationMinutes, status, calendlyEventId, timestamps; + `schedulingUrl` solo en la respuesta de inicio. |
| `src/main/java/com/claseya/booking/service/BookingService.java` | CREATE | Reglas de inicio/consulta/estado | Validaciones (perfil, visibilidad VERIFIED+ACTIVE, careerSubject activo, modalidad del profesor), inicio de flujo, listado/detalle por dueño, máquina de estados, sincronización desde webhook, sin N+1 (page + batch). |
| `src/main/java/com/claseya/booking/service/CalendlyWebhookService.java` | CREATE | Validación + procesamiento webhook | Verificación `Calendly-Webhook-Signature` (`t + "." + raw body`, HMAC-SHA256, signing key de entorno, tolerancia anti-replay); dispatch por tipo (`invitee.created`/`canceled`); idempotencia y fuera-de-orden. |
| `src/main/java/com/claseya/booking/controller/BookingController.java` | CREATE | Endpoints de estudiante/profesor | POST inicio, GET list, GET `{id}` (+ cancel si se aprueba). |
| `src/main/java/com/claseya/booking/controller/CalendlyWebhookController.java` | CREATE | Endpoint del webhook | `POST /api/webhooks/calendly`: valida firma; 2xx idempotente; 4xx firma inválida. Nunca loguea payload completo ni secretos. |
| `src/main/java/com/claseya/security/SecurityConfig.java` | MODIFY | Rutas | `/api/webhooks/calendly` permitAll (sin rol; firma interna); `/api/bookings/**` `hasAnyRole(STUDENT, TEACHER)` (roles), con ownership en service; matcher antes de `anyRequest`. |
| `src/main/resources/application.yml` | MODIFY | Secretos de webhook (env) | `calendly.webhook.signing-key: ${CALENDLY_WEBHOOK_SIGNING_KEY:}` y `tolerance-seconds` (default 180). Dev-only placeholder documentado. |
| Tests | CREATE | Cobertura BOOK-001 | `BookingIntegrationTest` + `CalendlyWebhookTest`/`BookingStateMachineTest` (ver §11). |
| `docs/product/domain.md`, `docs/architecture/*` | MODIFY | Documentación | Booking con estado/sincronización; calendly scheduling URL en profesor. |

**No se crean** archivos especulativos. `CalendlyIntegration` NO se toca.

## 8. Base de datos

- Migración **V5** (única; número siguiente real). No edita `V1..V4`.
- `career_subject_id` permanece NOT NULL (D2). No se toca `calendly_integrations`.
- Cambios (según decisión humana): columna `calendly_scheduling_url` en `teacher_profiles`;
  UNIQUE `uq_bookings_calendly_event_id` (permite NULLs múltiples → bookings aún sin evento);
  y, solo con Opción B del BLOCKER, `scheduled_at` nullable.
- Índices: se reutilizan `idx_bookings_*`; no se agregan duplicados.

## 9. API

### POST /api/bookings (STUDENT)
- Request: `{ "teacherId": uuid, "careerSubjectId": uuid, "mode": "ONLINE" | "IN_PERSON" }`.
- Validación: estudiante ACTIVE con `StudentProfile` (sin perfil → 409); teacher
  VERIFIED+ACTIVE (otro → 404); `careerSubject` activo (400/404); modalidad ofrecida por el
  profesor (400 si no la ofrece); profesor con `calendlySchedulingUrl` (sin URL → 409 "profesor no
  disponible para agendar" o 200 con `available:false` — a fijar).
- Respuesta (Opción B): `201` booking `PENDING` + `schedulingUrl` (sin `scheduledAt`). (Opción A):
  `200` con `schedulingUrl` y sin booking local.
- Errores: 400/401/403/404/409 vía `ApiError`.
- Ownership: el `student` deriva del `SecurityContext`.

### GET /api/bookings?page=&size= (dueño)
- Solo bookings donde el usuario autenticado es `student` o `teacher`. Sin `userId`/`studentId`
  de query. Página estable (`scheduled_at DESC, id DESC` o `created_at DESC`), límites 1..50.
  Anti-N+1 (page + batch de contraparte/materia).

### GET /api/bookings/{id} (dueño)
- Detalle. No-dueño → 404 (no revelar existencia).

### POST /api/bookings/{id}/cancel (SOLO si se aprueba; ver BLOCKER-2)
- Dueño autorizado según máquina de estados; impacto en Calendly depende de la decisión.

### POST /api/webhooks/calendly (sin rol)
- Firma válida → procesa (idempotente) → 200/202. Firma inválida/vieja → 4xx sin efectos.

## 10. Calendly

- **V1 = URL pública de scheduling del profesor + webhooks firmados** (sin OAuth ni SDK; se usan
  dependencias existentes).
- Config: `calendly.webhook.signing-key` (env `CALENDLY_WEBHOOK_SIGNING_KEY`), tolerancia
  anti-replay (180 s), URL del endpoint.
- Verificación de firma (contrato oficial verificado): header `Calendly-Webhook-Signature`
  `t=<unix>,v1=<hex>`; mensaje firmado = `t + "." + raw body`; HMAC-SHA256 con la signing key;
  comparar `v1`; rechazar si `t` fuera de tolerancia.
- Eventos: `invitee.created`, `invitee.canceled` (y `invitee.no_show` si aplica); filtro por
  `scheduled_event.event_type` si hay varios.
- Correlación evento↔Booking: por `calendly_event_id` (UNIQUE). Student↔evento: ver BLOCKER-1.
- Idempotencia: `calendly_event_id` + marca del evento; duplicado → no-op.
- Fuera de orden: comparar la marca temporal del evento contra la última aplicada al Booking;
  descartar las más viejas (marca exacta a fijar en spike contra payload real).
- Desconocido/sin booking: cuarentena controlada (log + flag/columna opcional) o descarte con log
  (según BLOCKER-1/Opción A).
- Retry de Calendly: misma ruta idempotente. Fallos temporales: `@Transactional`, reintento manual/
  procesamiento diferido NO se agregan en V1 (documentar).
- Reprogramación/cancelación en Calendly → webhook → actualizar `scheduled_at`/estado local.

## 11. Tests

Mapeo AC → tests:
- Unit: `BookingStateMachineTest` (transiciones válidas/inválidas; estados terminales; sin
  `status` del cliente) · `CalendlyWebhookSignatureTest` (firma correcta/incorrecta, timestamp
  viejo = replay).
- Integración (`BookingIntegrationTest`, Testcontainers, patrón del repo):
  AC-001 auth 401 · AC-002 TEACHER 403 · AC-003 sin perfil 409 · AC-004 visibilidad 404 ·
  AC-005/006 sin scheduling URL / careerSubject inválido · AC-007 campos no aceptados ·
  AC-008 inicio OK devuelve URL · AC-009 webhook firma inválida 4xx · AC-010/011/012/013 webhook
  crea/duplica/fuera de orden/desconocido · AC-014 cancelación · AC-015 UNIQUE evento ·
  AC-016 ownership/IDOR/paginación · AC-017 timezone · AC-018 privacidad · AC-019 sin N+1.
- De los AC que dependen del BLOCKER, se completan al resolverlo (los marcados como "según
  decisión").

## 12. Observabilidad

- Log INFO: operación (`booking.start`, `webhook.received`, `booking.confirmed`,
  `booking.cancelled`), `bookingId`, `calendly_event_id`, estado anterior→nuevo, resultado.
- Log DEBUG: solo resumen; jamás payload completo ni secretos ni emails innecesarios.
- Nunca: signing key, tokens OAuth, credenciales.

## 13. Manejo de errores

| Caso | HTTP | Excepción/ApiError |
|------|------|--------------------|
| Sin autenticación | 401 | — |
| Rol incorrecto / no dueño | 403 / 404 (ownership) | `AccessDenied` / `ResourceNotFound` |
| Profesor no visible / booking ajeno / evento sin booking | 404 | `ResourceNotFound` |
| Sin StudentProfile / profesor sin URL / estado inválido / duplicado evento | 409 | `Conflict` / `DataIntegrityViolation` |
| Datos inválidos / modalidad no ofrecida / careerSubject inválido | 400 | `BadRequest` / `InvalidAssociation` |
| Webhook firma inválida / replay | 403/400 (sin exponer detalles) | — (handler propio en el webhook) |

## 14. Change budget

- **CREATE**: migración V5; `booking/**` (repository/service x2/controller x2/dto x2); tests.
- **MODIFY**: `TeacherProfile`, 3 DTOs de teacher + 2 services de teacher, `SecurityConfig`,
  `application.yml`, docs (domain/architecture/spec/plan).
- **DELETE**: ninguno.
- **DATABASE**: V5 (columna URL + UNIQUE evento; scheduled_at nullable solo si se aprueba B).
- **DEPENDENCIAS**: ninguna (webhook = HTTP entrante ya soportado; sin cliente Calendly en V1).
- **CONFIG**: `calendly.webhook.signing-key` (+ tolerancia) por entorno.
- **TESTS**: unidad (estados, firma) + integración (booking/webhook).
- **DOCS**: spec (hecho) + plan (este) + ADR-013 + domain/architecture cuando se apruebe.

## 15. Rollback

- Revertir por código: retirar módulo `booking`, revertir cambios en `TeacherProfile`/DTOs/
  `SecurityConfig`/`application.yml`.
- Base de datos: nunca editar V5; revertir con `V6__revert_book_001` (DROP UNIQUE index, DROP
  columna, y si aplica restaurar NOT NULL) — documentada como rollback explícito.
- Webhook: pausar/desactivar la suscripción en Calendly y eliminar la ruta; los bookings ya
  creados permanecen coherentes (sin procesos parciales: operaciones transaccionales).
- Criterio: el sistema queda sin bookings nuevos y sin inconsistencia entre Booking y Calendly
  (los eventos previos ya persistidos se conservan).

## 16. Riesgos y BLOCKER

**BLOCKER-1 (crítico) — Correlación Student ↔ Event y ciclo de vida del Booking.**
`bookings.scheduled_at` es NOT NULL y Calendly elige la franja ⇒ **no se puede pre-crear un
Booking `PENDING` sin evento** sin tocar el schema. Además, si el Booking se creara recién en el
webhook, no habría dónde persistir `careerSubject`/`mode` elegidos en ClaseYa antes de Calendly.
Alternativas:
- **Opción B (recomendada)** — "intent = Booking PENDING": `POST /api/bookings` crea el Booking en
  `PENDING` (student, teacher, careerSubject, mode) con `scheduled_at` nullable (requiere que V5
  quite el NOT NULL de `scheduled_at`) y devuelve la scheduling URL con el email del estudiante
  pre-cargado (prefill si Calendly lo permite; a validar). El webhook (`invitee.email` normalizado
  == email de un User ACTIVE y `calendly_event_id` aún sin booking) completa el Booking:
  `scheduled_at`, `duration_minutes`, `calendly_event_id`, estado `CONFIRMED`. Sin match →
  cuarentena controlada (no se crea Booking fantasma).
- **Opción A** — Booking creado en el webhook (`CONFIRMED` directo): exige resolver subject/mode
  sin persistencia previa (p. ej., preguntas personalizadas en Calendly leídas del payload — frágil)
  o un pedido previo en ClaseYa sin booking; se descarta por debilidad de captura de materia/modo.
Impacto: schema (`scheduled_at` nullability), API de inicio, uso de `PENDING`, tests AC-008/010.

**BLOCKER-2 — Cancelación por API del estudiante en V1.** Sin OAuth, ClaseYa no puede cancelar el
evento en Calendly. Recomendación: cancelación V1 solo vía Calendly + webhook
(`CONFIRMED→CANCELLED`). Si el producto exige cancelar desde la app, se necesita un cliente
Calendly (OAuth) → fuera de V1.

> `STOP — HUMAN ARCHITECTURE DECISION REQUIRED`: decidir BLOCKER-1 (Opción B recomendada vs A) y
> BLOCKER-2 (cancelación vía Calendly vs incluir OAuth). Antes de resolverlos no se cierra el
> detalle de creación/cancelación, ni se crea el ADR-013.

**Otros riesgos:** webhooks fuera de orden (marca exacta del payload a confirmar en spike);
disponibilidad de Calendly (degradación controlada); timezone (UTC estricto, sin DST local);
retry ilimitado de Calendly (idempotencia cubre). Documentados, sin resolver inventando.

## 17. Definition of Done (verificable)

- [ ] BLOCKER-1 y BLOCKER-2 resueltos por el arquitecto y reflejados en spec/plan.
- [ ] Módulo `booking` implementado; máquina de estados sin estados del cliente; sin
  disponibilidad/solapamiento propios; sin confirmación manual.
- [ ] `careerSubject` obligatorio; V5 aplicada con justificación (y `scheduled_at` nullable solo
  con Opción B aprobada).
- [ ] Webhook con firma `Calendly-Webhook-Signature` (signing key de entorno), idempotente, con
  manejo de duplicados/fuera de orden/desconocidos.
- [ ] Ownership/roles/IDOR/privacidad/timezone cubiertos por tests; sin N+1.
- [ ] ADR-013 creado (integración Calendly + Booking de producto + source of truth + scheduling
  URL del profesor).
- [ ] Docs actualizadas; `mvn -B clean test` verde con los tests de BOOK-001.
- [ ] Change budget respetado; reportado al arquitecto/director.

## 18. ADR requerido

`ADR-013 — Integración Calendly + Booking como entidad de producto (source of truth, scheduling
URL del profesor y sincronización por webhooks firmados)`. Se creará cuando se resuelva el
BLOCKER-1 (no antes; su contenido depende de la opción elegida). Próximo número según ADRs
existentes: 013.
