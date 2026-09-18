# PLAN — AVAIL-001 · Disponibilidad semanal del profesor

**Fuente:** `docs/specs/active/AVAIL-001-Disponibilidad-publicacion-horarios.md` (v2, recurrente
semanal) · **Estado:** DEPRECATED (Fase 0 — dirección de producto descartada). AVAIL-001 queda como
historia; el código ya implementado (`AvailabilityWindow`, V5) se conserva como **disponibilidad
declarada** y no como agenda de reservas. Ver `docs/product/product-requirements.md`.

---

## 1. Objetivo

Implementar el módulo `availability` con ventanas **recurrentes semanales** por profesor
(`AvailabilityWindow`) y una nota general descriptiva, con CRUD propio del profesor, enable/disable,
consulta pública solo `AVAILABLE` de profesores elegibles, validación de duración (≥1 h),
prevención de solapamientos por `(teacher, dayOfWeek)` con **exclusion constraint + `btree_gist`**
(DB como última línea ante concurrencia). Sin reservas, Calendly ni buscador.

## 2. Estado actual del repositorio

- Schema hasta `V4`; `Booking`/`calendly_*` solo como schema FASE 1 (sin lógica). No existe código
  de disponibilidad.
- `TeacherProfile`: `verificationStatus` (VERIFIED/PENDING/REJECTED) + `user.status`
  (ACTIVE/...). **No tiene** `active`. Elegibilidad = `verificationStatus == VERIFIED` **y**
  `user.status == ACTIVE`.
- `BookingMode` y `TeachingModality` duplican ONLINE/IN_PERSON (**PRE-EXISTING ISSUE**, documentado,
  no se consolida aquí). Se reutiliza `TeachingModality` para el `mode` de la ventana.
- Convenciones: `CurrentUser`, DTOs record con `from(entity)`, `ApiError` +
  `GlobalExceptionHandler`, excepciones tipadas, `SearchResultPage` (en `teacher.dto`),
  repositories Spring Data + Specifications (patrón búsqueda FASE 4), `@Transactional`,
  tests Testcontainers con bases compartidas.
- Nota: si antes de implementar se incorporara otra migración (p. ej. BOOK-001 que hoy está en
  pausa), AVAIL-001 pasa a `V6`; se verifica al implementar.

## 3. Decisiones (adoptadas)

- Modelo **semanal recurrente** (día + franja) y no slots con fecha; fecha concreta se materializará
  en el futuro booking.
- `dayOfWeek` 1=Lun..7=Dom; franjas como `startMinutes`/`endMinutes` (hora de reloj) con
  `int4range` para la exclusión.
- `mode` opcional (null = ambas modalidades) con `TeachingModality`.
- Solapamiento por `(teacher, dayOfWeek)` con exclusion GiST (`btree_gist`); contiguos permitidos.
- `dayPart` (MORNING/AFTERNOON/NIGHT) derivado del horario de inicio (no se persiste).
- Nota general: columna `teacher_profiles.availability_note` (text, opcional), editable por el
  profesor y expuesta en detalle público.
- No hay timezone de profesor (no existe en el repo); display es del frontend.
- Migración AVAIL-001 = `V5` salvo que otra feature ocupe ese número antes.

## 4. Flujo end-to-end

```
TEACHER (VERIFIED+ACTIVE) → POST /api/availability {dayOfWeek, startTime, endTime, mode?}
  → valida perfil/elegibilidad/duración/solapamiento → AVAILABLE
GET /api/availability/me?page&size            (solo sus ventanas; dayOfWeek,startMinutes ASC)
PUT /api/availability/{id}                    (dueño; re-validaciones)
POST /api/availability/{id}/disable|enable    (dueño; enable re-valida solapamiento)
GET /api/availability?teacherId&dayOfWeek&mode (público; solo AVAILABLE de elegibles)
Público también lee availability_note en GET /api/teachers/{id} (detalle público)
```

## 5. Diseño por módulos y archivos

**Nuevo módulo `com.claseya.availability`** (espejo de convenciones del repo):
- `model` (entidad nueva) y `model.enums` (enum nuevo).
- `availability/repository/AvailabilityWindowRepository`
- `availability/service/AvailabilityService`
- `availability/specification/AvailabilityWindowSpecifications` (público: status + elegibilidad +
  filtros opcionales)
- `availability/controller/AvailabilityController`
- `availability/dto/*`

### Archivo por archivo

| PATH | Tipo | Propósito | Cambios esperados |
|------|------|-----------|-------------------|
| `src/main/resources/db/migration/V5__availability_windows.sql` | CREATE | Schema AVAIL-001 | `CREATE EXTENSION IF NOT EXISTS btree_gist`; tabla `availability_windows` (PK uuid, teacher_id FK RESTRICT/CASCADE a definir, day_of_week, start_minutes, end_minutes, mode, status, timestamps, CHECKs, exclusion GiST, índices); `ALTER TABLE teacher_profiles ADD COLUMN availability_note text`. |
| `src/main/java/com/claseya/model/AvailabilityWindow.java` | CREATE | Entidad | `@Entity @Table("availability_windows")`: teacher ManyToOne, dayOfWeek Integer, startMinutes/endMinutes Integer, mode TeachingModality nullable, status enum default AVAILABLE, createdAt/updatedAt. |
| `src/main/java/com/claseya/model/enums/AvailabilityStatus.java` | CREATE | Enum | `AVAILABLE, DISABLED`. |
| `src/main/java/com/claseya/model/enums/AvailabilityDayPart.java` | CREATE | Enum derivado | `MORNING, AFTERNOON, NIGHT` (conveniencia de lectura). |
| `src/main/java/com/claseya/model/TeacherProfile.java` | MODIFY | Nota general | `@Column(name="availability_note", columnDefinition="text") String availabilityNote;` |
| `src/main/java/com/claseya/teacher/dto/CreateTeacherProfileRequest.java` / `UpdateTeacherProfileRequest.java` | MODIFY | Editar nota | Campo opcional `availabilityNote` + `@Size`. |
| `src/main/java/com/claseya/teacher/dto/TeacherPublicDetailResponse.java` | MODIFY | Exponer nota | Campo `availabilityNote` (nullable) + mapeo. |
| `src/main/java/com/claseya/teacher/service/TeacherProfileService.java` | MODIFY | Persistir nota | Aplicar campo (null conserva el valor actual en update). |
| `src/main/java/com/claseya/teacher/service/TeacherSearchService.java` | MODIFY | Incluir nota en detalle público | Incluir el valor en `getPublic`. |
| `src/main/java/com/claseya/availability/repository/AvailabilityWindowRepository.java` | CREATE | Acceso | `findByTeacher_Id(... Pageable)`; `existsOverlapping(teacherId, day, start, end)` (JPQL strict `s.startMinutes < :end AND s.endMinutes > :start AND s.dayOfWeek=:day`); `findByIdAndTeacher_Id` (ownership). |
| `src/main/java/com/claseya/availability/service/AvailabilityService.java` | CREATE | Reglas | Elegibilidad/ownership/duración/solapamiento (409 friendly), máquina AVAILABLE↔DISABLED, UTC no aplica (minutos), listados sin N+1, manejo de `DataIntegrityViolationException` → 409 (race de exclusión). |
| `src/main/java/com/claseya/availability/specification/AvailabilityWindowSpecifications.java` | CREATE | Consulta pública | `visible()` (status AVAILABLE + teacher VERIFIED+ACTIVE) + filtros opcionales `teacherId`/`dayOfWeek`/`mode`/`dayPart`. |
| `src/main/java/com/claseya/availability/controller/AvailabilityController.java` | CREATE | Endpoints | POST, GET /me, PUT /{id}, POST /{id}/disable, POST /{id}/enable, GET pública. |
| `src/main/java/com/claseya/availability/dto/CreateAvailabilityRequest.java` | CREATE | DTO request | `dayOfWeek Integer 1..7`, `startTime LocalTime`/`"HH:mm"`, `endTime`, `mode` opcional (`TeachingModality`). Sin teacherId/status. |
| `src/main/java/com/claseya/availability/dto/UpdateAvailabilityRequest.java` | CREATE | DTO request | Iguales campos editables. |
| `src/main/java/com/claseya/availability/dto/AvailabilityWindowResponse.java` | CREATE | DTO respuesta (dueño/público) | id, dayOfWeek, startTime/endTime (`HH:mm`), mode, status, dayPart (derivado), timestamps; versión pública agrega `teacherId`. |
| `src/main/java/com/claseya/security/SecurityConfig.java` | MODIFY | Rutas | `GET /api/availability` permitAll (público); `POST /api/availability`, `GET /api/availability/me`, `PUT/DELETE...` de `/{id}`/`disable`/`enable` → `ROLE_TEACHER`. Orden antes de `anyRequest`. |
| Tests | CREATE | Cobertura | `AvailabilityIntegrationTest`, `AvailabilityValidationTest` (unit), `AvailabilityConcurrencyTest` si aplica. |
| Docs | MODIFY | Documentación | domain/architecture/database + ADR (ver §12) + plan/spec (este). |

## 6. Base de datos (V5) — detalle

- `CREATE EXTENSION IF NOT EXISTS btree_gist;`
- Tabla `availability_windows`:
  - `id uuid PK DEFAULT gen_random_uuid()`
  - `teacher_id uuid NOT NULL REFERENCES teacher_profiles(id)` (ON DELETE: **RESTRICT** para
    preservar historial — no se borran perfiles; definirlo al implementar según patrón del repo)
  - `day_of_week int NOT NULL CHECK (day_of_week BETWEEN 1 AND 7)`
  - `start_minutes int NOT NULL CHECK (start_minutes BETWEEN 0 AND 1439)`
  - `end_minutes int NOT NULL CHECK (end_minutes BETWEEN 1 AND 1440)`
  - `mode varchar(20) NULL CHECK (mode IN ('ONLINE','IN_PERSON'))`
  - `status varchar(20) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE','DISABLED'))`
  - `created_at timestamptz NOT NULL DEFAULT now()`, `updated_at timestamptz NOT NULL DEFAULT now()`
  - `CONSTRAINT chk_end_gt_start CHECK (end_minutes > start_minutes)`
  - `CONSTRAINT chk_min_duration CHECK (end_minutes - start_minutes >= 60)`
  - `CONSTRAINT ex_availability_no_overlap EXCLUDE USING gist (teacher_id WITH =, day_of_week WITH =, int4range(start_minutes, end_minutes, '[)') WITH &&)`
- Índices btree mínimos: `(teacher_id, day_of_week, start_minutes)` (consulta de dueño/orden) y
  `(status, day_of_week)` si la consulta pública los usa; sin redundancia con la exclusión.
- `ALTER TABLE teacher_profiles ADD COLUMN availability_note text;`

## 7. API (contratos)

### POST /api/availability (TEACHER)
- Request `{ "dayOfWeek": 1, "startTime": "18:00", "endTime": "20:00", "mode": "ONLINE" }`
- Validaciones (spec §13). Respuesta 201 `AvailabilityWindowResponse` status AVAILABLE.
- Errores: 400 (rango/datos), 401, 403 (rol/no elegible), 404 (perfil/otro recurso), 409
  (solapamiento o violación de exclusión).

### GET /api/availability/me (TEACHER)
Paginado 1..50, `dayOfWeek ASC, startMinutes ASC`, solo propios (ownership por `CurrentUser`).

### PUT /api/availability/{id} (TEACHER dueño)
Reemplaza `dayOfWeek/start/end/mode`; re-validaciones; no-dueño → 404.

### POST /api/availability/{id}/disable · /enable (TEACHER dueño)
Transición AVAILABLE↔DISABLED; `enable` re-valida solapamiento; no-dueño → 404. Respuesta 204.

### GET /api/availability (público)
Filtros `teacherId?`, `dayOfWeek?`, `mode?`, `dayPart?`, `page`, `size`. Solo `AVAILABLE` de
profesores VERIFIED+ACTIVE. Paginado; sin datos privados.

## 8. Seguridad / privacidad

Identity por `SecurityContext`/`CurrentUser`; ownership en service (404 a no-dueños); rutas de
escritura `ROLE_TEACHER` + elegibilidad en service (VERIFIED+ACTIVE). Público solo
`AvailabilityWindowResponse` + `availabilityNote`; sin email/coords/address/secretos. Logging sin
datos sensibles.

## 9. Tests (mapeo spec §25/AC)

- **Unit**: `AvailabilityValidationTest` (rango dayOfWeek, hora → minutos, duración ≥60,
  end>start, dayPart), `AvailabilityStateTest` (enable/disable).
- **Integración** `AvailabilityIntegrationTest` (Testcontainers): AC-001..AC-013, 401/403/
  ownership/paginación/solapamientos/contiguos/duplicados (409)/timezone (minutos + instantes en
  nota)/público sin datos privados.
- **Concurrencia** (AC-012): dos escritores simultáneos sobre el mismo `(teacher, día)` → solo uno
  persiste (exclusión en DB; el otro recibe 409).

## 10. Observabilidad y errores

- Log INFO de operaciones con `teacherId`/`windowId`/estado; sin datos personales.
- Errores de dominio → `ApiError`/`GlobalExceptionHandler`: 400 `BadRequest`/`InvalidAssociation`,
  401, 403 `AccessDenied`, 404 `ResourceNotFound`, 409 `Conflict`/`DataIntegrityViolation`.

## 11. Change budget

- **CREATE**: migración `V5`; módulo `availability` (~9 clases) + entidad/enums en `model`; tests.
- **MODIFY**: `TeacherProfile`, 3 DTOs de `teacher` + 2 services de `teacher`, `SecurityConfig`,
  docs.
- **DELETE**: ninguno. **DEPENDENCIAS**: ninguna (solo `CREATE EXTENSION btree_gist`).
- **CONFIG**: ninguna nueva.
- **DOCS**: spec/plan + ADR + domain/architecture/database.

## 12. Rollback

- Código: retirar módulo `availability` y revertir cambios en `teacher`/`security`.
- DB: nunca editar `V5`; revertir con `V6__revert_avail_001` (DROP tabla/columna/índice; dejar la
  extensión `btree_gist` documentada o eliminarla si no la usa otra feature) — rollback explícito.
- Sin procesos parciales: operaciones transaccionales; estado coherente en todo momento.

## 13. Riesgos / dependencias

- `CREATE EXTENSION btree_gist` requiere privilegio/permiso en el entorno (validar en deploy);
  alternativa documentada si no estuviera disponible (advisory lock) — decisión ya tomada: exclusión.
- Concurrencia AC-012 cubierta por la exclusión.
- Coexistencia con BOOK-001 (en pausa) y Calendly (fuera) → si BOOK se reactivara, la franja
  concreta se materializaría desde la ventana; sin cambios de AVAIL-001.
- `V5` vs `V6`: si otra feature incorpora migración antes, AVAIL pasa a `V6`.

## 14. Definition of Done (verificable)

- [ ] Spec aprobada (v2 semanal) y plan aprobado.
- [ ] ADR-014 (o siguiente libre) creado: modelo semanal recurrente, exclusion constraint +
  `btree_gist`, excepción "hora de reloj ≠ instante", nota general.
- [ ] Migración `V{n+1}` aplicada sin modificar `V1..V4`; `career_subject`/Calendly intactos.
- [ ] `AvailabilityWindow` + `availability_note` implementados; CRUD propio, enable/disable,
  consulta pública, ownership, duración ≥1h, solapamiento y concurrencia (exclusión), `HH:mm`/
  minutos, sin N+1.
- [ ] Tests (unit + integración + concurrencia) en verde con `mvn -B clean test`.
- [ ] Sin lógica de BOOK-001/SEARCH-001/Calendly; change budget respetado; reportado al director.
