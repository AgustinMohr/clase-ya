# AVAIL-001 — Disponibilidad y publicación de horarios (recurrente semanal)

**ID:** AVAIL-001
**Estado:** DEPRECATED (dirección de producto descartada) · **Carpeta:** `docs/specs/active/`
**Revisión:** v2 (2026-09-02) · última revisión de estado 2026-09-02 (Fase 0, gobernanza).

> **Nota del director (Fase 0 — 2026-09-02):** este documento queda **DEPRECATED como rumbo de
> producto**. ClaseYa pasa a ser un **directorio + contacto seguro** (sin agenda ni reservas) según
> `docs/product/product-requirements.md`. La infraestructura `AvailabilityWindow` (código/migración
> V5) se conserva como **disponibilidad declarada** (franjas mañana/tarde/noche por día) para el
> perfil del profesor y no como agenda de reservas. No implementar nuevas capacidades sobre esta
> spec sin reactivarla por el director. Se conserva como historia (no se borra).

---

## 1. Objetivo

Permitir que profesores elegibles publiquen su **disponibilidad recurrente semanal** (días de la
semana + franjas horarias, agrupables en mañana/tarde/noche), de forma que los estudiantes puedan
consultarla públicamente y, en el futuro, materializar horarios concretos para solicitar clases.
Este modelo se inspira en cómo publican su disponibilidad plataformas de clases particulares
(tusclasesparticulares, Superprof): **patrón semanal por día y franja**, no fechas sueltas.

ClaseYa gestiona la disponibilidad publicada; no delega en Calendly (decisión del director).

## 2. Conceptos

### 2.1 `AvailabilityWindow` (patrón semanal recurrente)

Un bloque horario **recurrente semanal** que el profesor publica:

```
Profesor: Juan Pérez
Día: Lunes (1)
Inicio: 18:00
Fin: 20:00
Modalidad: ONLINE (o null = ambas)
Estado: AVAILABLE
```

Ejemplos válidos de duración: 1 h, 1 h 30 m, 2 h, 3 h. **Nunca menos de 1 hora.**

### 2.2 Disponibilidad general (nota descriptiva)

Texto opcional visible públicamente (p. ej. "Disponible de lunes a miércoles de 16:00 a 20:00").
Es **informativo**; no bloquea horarios ni participa del mecanismo de ventana/futura reserva. No se
parsea texto libre para construir ventanas.

## 3. Contexto del proyecto (verificado)

- Java / Spring Boot 3.2.6 · PostgreSQL · Flyway (schema hasta `V4`) · Spring Data JPA ·
  Spring Security (JWT) · roles `STUDENT/TEACHER/ADMIN`.
- Convenciones: identity por `SecurityContext`/`CurrentUser`; DTOs nunca entidades; `ApiError`;
  ownership server-side; timestamps `timestamptz`/`Instant` para **instantes**; migraciones nuevas
  `V{n+1}` sin editar `V1..V4`; evitar N+1; AGENTS.md y `docs/architecture/*`.
- **Elegibilidad real** (el spec previo hablaba de `TeacherProfile.active/status`, que no existen):
  el profesor debe cumplir `TeacherProfile.verificationStatus = VERIFIED` **y**
  `TeacherProfile.user.status = ACTIVE` (rol TEACHER garantizado por autorización).
- No existe código de disponibilidad en el repo. `BookingMode` y `TeachingModality` duplican
  ONLINE/IN_PERSON (deuda preexistente documentada, no se consolida aquí). Para `AvailabilityWindow`
  se reutiliza `TeachingModality` (`ONLINE`/`IN_PERSON`).

## 4. Actores

| Actor | Capacidades |
|---|---|
| TEACHER | Crear, consultar, modificar, habilitar/deshabilitar **sus** ventanas; cargar/editar su nota de disponibilidad. |
| STUDENT | Consultar públicamente disponibilidad publicada. |
| ADMIN | Sin funcionalidades nuevas en AVAIL-001. |
| Público | Consultar ventanas `AVAILABLE` de profesores elegibles y la nota general. |

## 5. Reglas de elegibilidad del profesor

Solo un profesor que cumpla **VERIFIED + ACTIVE** (ver §3) puede publicar disponibilidad. Profesores
PENDING/REJECTED, o con cuenta INACTIVE/SUSPENDED, no pueden publicar. La autorización es
server-side. Un profesor no puede modificar/habilitar/deshabilitar ventanas de otro (404 a
no-dueños).

## 6. Modelo `AvailabilityWindow`

```
id            uuid PK
teacher       TeacherProfile (FK)
dayOfWeek     smallint 1..7   (1 = lunes … 7 = domingo)
startMinutes  int 0..1439     (minutos desde medianoche, hora de reloj del patrón)
endMinutes    int 1..1440
mode          varchar(20) NULL CHECK (mode IN ('ONLINE','IN_PERSON'))  -- NULL = ambas
status        varchar(20) DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE','DISABLED'))
createdAt     timestamptz (UTC)
updatedAt     timestamptz (UTC)
```

- La disponibilidad pertenece al **profesor**, no a una materia (no se duplica por `CareerSubject`);
  la materia se decidirá en el futuro `BookingRequest`.
- `dayPart` (**derivado**, no se persiste): MORNING (inicio < 12:00) / AFTERNOON (12:00 ≤ inicio <
  19:00) / NIGHT (inicio ≥ 19:00). Cubre el concepto "mañana/tarde/noche".
- Reglas: `endMinutes > startMinutes`; duración `endMinutes - startMinutes ≥ 60`; sin límite máximo
  en AVAIL-001.

## 7. Tiempo y timezone

- Los **instantes** persisten como UTC (`timestamptz`/`Instant`).
- Un patrón semanal recurrente **no es un instante**: se almacena como hora de reloj en minutos de
  día (`startMinutes`/`endMinutes`). El display en la zona del profesor es responsabilidad del
  frontend; no se introduce un modelo de timezone de profesor en AVAIL-001 (no existe en el repo; no
  se inventa una solución paralela).
- Las **fechas concretas** se materializarán en el futuro mecanismo de reserva (fuera de alcance).

## 8. Duración

`endMinutes - startMinutes ≥ 60`. `endMinutes` estrictamente mayor que `startMinutes`. Ejemplos
válidos: 60, 90, 120, 180. Inválidos: 30, 45, o `end == start`.

## 9. Solapamientos y concurrencia

- Un profesor **no** puede tener ventanas solapadas el **mismo día**. Contiguas permitidas
  (`endMinutes == next.startMinutes` no es solapamiento).
- La validación es server-side y **la DB es la última línea de defensa ante concurrencia**:
  exclusion constraint PostgreSQL
  `EXCLUDE USING gist (teacher_id WITH =, day_of_week WITH =, int4range(start_minutes,end_minutes,'[)') WITH &&)`
  con la extensión `btree_gist`. El service ofrece además un pre-check con 409 amigable.
- El solapamiento se evalúa por `(teacher, dayOfWeek)` independientemente de `mode`: si el profesor
  quiere ofrecer la misma franja en ambas modalidades, publica `mode = NULL` (ambas).

## 10. Estados

`AVAILABLE` (publicada/descubrible) y `DISABLED` (deshabilitada por el profesor; no aparece en
público, conserva historial). Transiciones: `AVAILABLE ↔ DISABLED` por su dueño. No se eliminan
físicamente ventanas que puedan ser referenciadas por reservas futuras. Si en el futuro una ventana
tuviera una reserva activa, `disable` debería rechazarse (regla de BOOK; hoy sin efecto).

## 11. Relación con reservas futuras

AVAIL-001 prepara el dominio para BOOK-001 (BookingRequest sobre una ventana → franja concreta).
No se implementa aquí reserva, estados de reserva, materialización de fechas ni aceptación/rechazo.
El diseño deja referenciable cada `AvailabilityWindow` por id.

## 12. Publicación (contrato de escritura)

El profesor crea ventanas con: `dayOfWeek`, `startTime`, `endTime`, `mode` (opcional). No se aceptan
del cliente `teacherId`, `status`, `createdAt`, `updatedAt`. El estado inicial es `AVAILABLE`.
Los horarios se envían como hora de reloj (`"HH:mm"`).

## 13. Crear ventana

```http
POST /api/availability
```
TEACHER. Request:

```json
{ "dayOfWeek": 1, "startTime": "18:00", "endTime": "20:00", "mode": "ONLINE" }
```

Validaciones: autenticación → ROLE_TEACHER → perfil existente → profesor VERIFIED+ACTIVE →
`dayOfWeek` 1..7 → `startTime`/`endTime` válidos → `endMinutes > startMinutes` → duración ≥ 1 h →
sin solapamiento del mismo día → ignorar campos controlados por servidor. Respuesta DTO.

## 14. Consultar mis ventanas

```http
GET /api/availability/me?page=&size=
```
TEACHER. Solo sus ventanas, paginado (1..50), orden `dayOfWeek ASC, startMinutes ASC`. No se acepta
`teacherId` para ownership.

## 15. Modificar ventana

```http
PUT /api/availability/{id}
```
TEACHER (dueño). Se permite modificar mientras no esté comprometida por una reserva activa (hoy no
existen; regla de BOOK futura). No-dueño → 404. Re-validaciones de duración/solapamiento.

## 16. Habilitar / deshabilitar

```http
POST /api/availability/{id}/disable
POST /api/availability/{id}/enable
```
TEACHER (dueño). `AVAILABLE → DISABLED` / `DISABLED → AVAILABLE`. `enable` re-valida solapamiento.
No-dueño → 404.

## 17. Disponibilidad pública

Solo ventanas `status = AVAILABLE` de profesores elegibles (VERIFIED + ACTIVE). Las `DISABLED` y las
de profesores no elegibles nunca aparecen. No se exponen datos privados del profesor.

## 18. API pública

```http
GET /api/availability
```
Público. Filtros previstos (implementar como mínimo `teacherId`, `dayOfWeek`, `mode`):
`teacherId`, `dayOfWeek`, `mode` (y `dayPart` como conveniencia). Filtros por fecha concreta
(`date`/`from`/`to`) se difieren a SEARCH/BOOK cuando exista la materialización de instancias; no se
inventa una relación artificial. Paginado, orden `dayOfWeek ASC, startMinutes ASC`.

## 19. Información general (nota)

Columna `teacher_profiles.availability_note` (text, opcional), editable por el profesor, visible en
el detalle público. No reemplaza `AvailabilityWindow`; no se parsea.

## 20. Integridad (invariantes)

- INV-1 Solo VERIFIED + ACTIVE publican.
- INV-2 El dueño de una ventana es el profesor autenticado que la creó.
- INV-3 `endMinutes > startMinutes`.
- INV-4 Duración ≥ 60 minutos.
- INV-5 Sin ventanas solapadas el mismo día (mismo profesor).
- INV-6 Contiguas permitidas.
- INV-7 `DISABLED` no aparece en público.
- INV-8 Ventanas de profesores no elegibles no aparecen en público.
- INV-9 El cliente no controla `teacher`, `status`, `createdAt`, `updatedAt`.
- INV-10 No se borran físicamente ventanas referenciables por reservas futuras.
- INV-11 Instantes (timestamps) en UTC; el patrón se guarda como hora de reloj en minutos.

## 21. Seguridad

`CurrentUser`; ownership server-side; `ROLE_TEACHER`; 404 para recursos ajenos; `ApiError`; DTOs;
nunca confiar en IDs del cliente para identidad. Un STUDENT no puede usar POST/PUT/enable/disable de
`/api/availability`. Las consultas públicas no exponen email/coordenadas/address/secretos.

## 22. Idempotencia

Sin idempotency key obligatoria (el repo no tiene infraestructura). Se evita la duplicación
accidental por la exclusión de solapamiento (el mismo bloque en el mismo día vuelve a estar
"solapado" y se rechaza). No se crea un UNIQUE sobre `(teacher, day, start, end, mode)` (evaluado:
impediría casos legítimos como el re-uso futuro de franjas); documentar.

## 23. Base de datos

Migración nueva `V5__availability_windows.sql` (o el siguiente número real al implementar). Contiene:
`CREATE EXTENSION IF NOT EXISTS btree_gist`; tabla `availability_windows`; exclusion constraint
GiST; CHECKs; `ALTER TABLE teacher_profiles ADD COLUMN availability_note text`; índices btree
mínimos (`(teacher_id)`, `(status, day_of_week)` si aportan a las consultas previstas; sin
redundancia con la exclusión). No toca `V1..V4`.

## 24. Arquitectura del módulo

`com.claseya.availability` con `repository`, `service`, `controller`, `dto`, `specification`
(patrón del repo: entidad en `model`, excepciones en `common`). Cambios mínimos en `teacher`
(nota + DTO/service de perfil) y `security` (rutas). Sin arquitectura paralela.

## 25. Tests obligatorios

Seguridad: sin auth → 401; STUDENT crea → 403; TEACHER no elegible crea → rechazo; ownership
(cruzado) → 404.
Creación: 60/90/120 min OK; <60 → rechazo; `end <= start` → rechazo; `teacherId`/`status` del
cliente ignorados.
Solapamiento: mismo día solapado/contenido/parcial → rechazo; contiguos → OK; dos escritores
concurrentes → uno persiste (exclusión).
Estados: AVAILABLE→DISABLED→AVAILABLE; DISABLED no aparece en público; enable re-valida.
Público: solo AVAILABLE de elegibles; sin datos privados.
Ownership: `/me` solo propios; A no modifica ventana de B.
Tiempo: patrón persiste como minutos y responde `HH:mm` sin corrimientos; timestamps UTC.
Performance: consultas públicas sin N+1.

## 26. Criterios de aceptación

- AC-001 Profesor VERIFIED+ACTIVE crea ventana válida → 201 AVAILABLE.
- AC-002 Profesor no VERIFIED o cuenta INACTIVE intenta publicar → rechazo.
- AC-003 Ventana de 59 min → rechazo.
- AC-004 Ventana de 90 min → OK.
- AC-005 Mismo día 18–20 y 19–21 → rechazo (solapamiento).
- AC-006 18–19 y 19–20 → ambos válidos (contiguos).
- AC-007 A intenta modificar ventana de B → 404.
- AC-008 AVAILABLE sin reservas → disable → DISABLED y no visible en público.
- AC-009 DISABLED válida → enable → AVAILABLE.
- AC-010 Ventana AVAILABLE de profesor elegible aparece en consulta pública.
- AC-011 Consulta pública sin datos privados innecesarios.
- AC-012 Dos solicitudes concurrentes solapadas → estado final sin solapamiento.
- AC-013 Hora de reloj enviada y recuperada sin corrimiento (patrón semanal); instantes UTC.

## 27. Out of scope

Reservas, `BookingRequest`, `Booking`, aprobación/rechazo, pagos, notificaciones, Calendly/OAuth,
videollamadas, calendario externo, recurrencias complejas/excepciones por fecha, sincronización con
Google Calendar, frontend, buscador completo (SEARCH), reviews/ratings, materialización de fechas.

## 28. Relación con futuras features

AVAIL-001 alimenta SEARCH-001 (descubrir ventanas) y BOOK-001 (crear `BookingRequest` sobre una
ventana → franja concreta → aceptación). El modelo no bloquea esas features.

## 29. Definition of Done

- [ ] Spec aprobada.
- [ ] Plan de implementación creado.
- [ ] ADR creado (modelo semanal + exclusion constraint + nota de excepción de hora-de-reloj).
- [ ] Migración nueva sin modificar `V1..V4`.
- [ ] `AvailabilityWindow` + nota general implementados.
- [ ] CRUD propio, enable/disable, consulta pública, ownership, duración mínima, solapamiento,
      concurrencia (exclusión), `HH:mm`/minutos, sin N+1.
- [ ] Tests unitarios/integración según patrones existentes; `mvn -B clean test` en verde.
- [ ] No se implementó lógica de BOOK-001/SEARCH-001/Calendly.
- [ ] Change budget respetado; reportado al arquitecto/director.
