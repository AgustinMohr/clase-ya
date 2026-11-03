# OPINION-001 — Opiniones y rating de profesores

**ID:** OPINION-001
**Estado:** Aprobada e implementada v1 (2026-11-02) — pendiente de verificación integral con Docker/E2E
**Nivel de cambio:** 2 (feature de producto + contrato de API + migración)
**Origen:** hallazgo PRODUCT-01 / PRD §8
**Relacionado:** `ADR-024`, `ADR-032`, `CONTACT-001`, `REVIEW2-002`, `docs/verification.md`

---

## 1. Objetivo

Permitir que un estudiante que tuvo un contacto real con un profesor publique **una opinión**
(1..5 estrellas + comentario opcional), que el profesor pueda **responder** y que el promedio se
refleje en las tarjetas y en el perfil público. La opinión solo se habilita cuando existe una
**relación de contacto aceptada**; no hay reservas ni pagos.

Éxito de producto: un estudiante puede calificar a un profesor con el que ya conversó, el profesor
puede responder una vez, y el `ratingAverage`/`ratingCount` de la tarjeta y del perfil cambia de
forma consistente sin exponer datos personales.

## 2. Contexto

Lo que ya existe:

- `reviews` existe en el schema desde `V1`, sin entidad JPA ni servicio. `V17` le quitó
  `booking_id`; hoy solo tiene `student_id`, `teacher_id`, `rating`, `comment`, `created_at` y
  `updated_at`.
- `teacher_profiles.rating_average`/`rating_count` existen y ya se exponen en
  `TeacherSummaryResponse` y `TeacherPublicDetailResponse`, pero quedan en 0 porque no hay flujo.
- `TeacherProfilePage` muestra la sección “Opiniones” con un texto de “próxima fase”.
- Mensajería/contacto ya existe: un `STUDENT` inicia una conversación con un `TEACHER` y ambos
  intercambian mensajes (`CONTACT-001`). No hay reservas ni agenda.
- `reports` y `notifications` se conservan para futuro (`ADR-032`), pero no tienen flujo.
- `ADR-024` retiró las entidades legacy; esta spec **reintroduce únicamente `Review`**.

Lo que falta:

- Entidad/repositorio/servicio de reviews.
- Regla de elegibilidad “contacto aceptado”.
- Contrato REST, moderación y actualización de agregados.
- UI de opiniones en el perfil, respuesta del profesor y moderación admin.

## 3. Supuestos a validar

Estos puntos fueron aprobados en REVIEW (2026-11-02) y se implementaron como se describe:

| # | Supuesto | Recomendación |
|---|---|---|
| S1 | “Contacto aceptado” = existe una conversación entre el estudiante y el profesor **y el profesor envió al menos un mensaje**. | Aprobar; no agregar un estado nuevo a `conversations` en esta spec. |
| S2 | Una opinión por par estudiante↔profesor, para siempre; si vuelve a contactar, edita la existente. | Aprobar. |
| S3 | El estudiante puede **editar** su opinión (rating/comentario); no puede eliminarla. | Aprobar; conserva historial y evita borrados. |
| S4 | El profesor puede publicar **una respuesta** por opinión y editarla. | Aprobar. |
| S5 | La opinión se publica inmediatamente (`PUBLISHED`); el ADMIN puede ocultarla/restaurarla con motivo. | Aprobar moderación posterior, no pre-moderación. |
| S6 | El nombre público del autor es `users.name` o “Estudiante” si falta; nunca email. | Aprobar. |
| S7 | Reportes de opiniones y notificaciones por email de reviews quedan fuera de esta spec. | Aprobar; `reports`/`notifications` se implementan en specs separadas. |

## 4. Actores

| Actor | Qué hace |
|---|---|
| `STUDENT` | Crea/edita su opinión sobre un profesor con el que tuvo contacto aceptado. |
| `TEACHER` | Ve las opiniones de su perfil y responde una vez por opinión. |
| `ADMIN` | Lista opiniones publicadas/ocultas, oculta con motivo y restaura. |
| Visitante | Lee opiniones publicadas en el perfil; no escribe. |
| Sistema | Valida elegibilidad, unicidad, ownership, agrega rating y audita la moderación. |

## 5. Requisitos funcionales

**Elegibilidad y creación**

- **RF-1** Un `STUDENT` autenticado puede crear una opinión solo si:
  1. el `teacherId` corresponde a un profesor público (D1: usuario `ACTIVE` + anuncio publicado);
  2. tiene `StudentProfile`;
  3. existe una conversación entre ambos usuarios;
  4. el profesor envió al menos un mensaje en esa conversación.
- **RF-2** La opinión tiene `rating` entero 1..5 y `comment` opcional (máx. 1000 caracteres,
  trim). El comentario se guarda como texto y se renderiza como texto, nunca HTML.
- **RF-3** Solo puede existir **una** opinión por par `student_profile` ↔ `teacher_profile`.
  Un segundo `POST` responde `409`.
- **RF-4** Si el estudiante no tiene perfil, no hay conversación o el profesor nunca respondió,
  la creación responde `403` con un mensaje de producto; no se crea fila.
- **RF-5** Si el profesor no es público, la creación responde `404` (no se revela su existencia).

**Lectura**

- **RF-6** `GET /api/teachers/{teacherId}/reviews` es público, paginado y devuelve solo opiniones
  `PUBLISHED` del profesor visible, ordenadas por `createdAt DESC, id ASC`. Un profesor no visible
  responde `404`.
- **RF-7** La respuesta pública incluye `id`, `rating`, `comment`, `authorDisplayName`,
  `createdAt`, `teacherResponse` y `teacherRespondedAt`. **No** incluye email, IDs de usuario,
  `student_id`, `teacher_id` ni datos de moderación.
- **RF-8** `GET /api/teachers/{teacherId}/reviews/me` (STUDENT) devuelve la opinión propia, incluso
  si está `HIDDEN` (con su estado), o `404` si no existe. Permite que la UI muestre “editar” en
  lugar de “opinar”.

**Edición y respuesta**

- **RF-9** `PUT /api/reviews/{reviewId}` permite al autor `STUDENT` editar `rating` y `comment` de
  su opinión `PUBLISHED`; actualiza `updatedAt`. Un no-autor recibe `404`. Una opinión `HIDDEN`
  responde `409` hasta que el ADMIN la restaure.
- **RF-10** `POST /api/reviews/{reviewId}/response` permite al `TEACHER` dueño del perfil crear o
  reemplazar su respuesta (máx. 1000 caracteres, trim) y actualiza `teacherRespondedAt`.
  Un no-dueño recibe `404`; una opinión `HIDDEN` responde `409`.
- **RF-11** El estudiante no puede responder su propia opinión; el profesor no puede editar la
  opinión del estudiante.

**Moderación**

- **RF-12** `GET /api/admin/reviews?status=PUBLISHED|HIDDEN&page=&size=` lista opiniones para
  ADMIN, con nombre del profesor, nombre del autor, rating, comentario, estado y fechas.
- **RF-13** `POST /api/admin/reviews/{reviewId}/hide` con `{reason}` oculta una opinión
  `PUBLISHED`; `POST /api/admin/reviews/{reviewId}/restore` la vuelve a `PUBLISHED`. Ambas son
  idempotentes: repetir `hide` sobre `HIDDEN` o `restore` sobre `PUBLISHED` responde `204`; ocultar
  exige motivo. Una review inexistente responde `404`.
- **RF-14** Toda acción de moderación se registra en `review_moderation_events` (append-only):
  `review_id`, `admin_user_id`, `action`, `reason`, `created_at`.

**Agregados**

- **RF-15** `rating_average` y `rating_count` del profesor se recalculan en la misma transacción
  cuando se crea, edita, oculta o restaura una opinión. Solo cuentan las `PUBLISHED`.
  Sin opiniones: `0.00` y `0`.
- **RF-16** El redondeo de `rating_average` es a 2 decimales (`HALF_UP`), compatible con
  `numeric(3,2)`.
- **RF-17** La tarjeta de búsqueda y el perfil público ya consumen esos agregados; no cambia el
  contrato de `GET /api/teachers` ni `GET /api/teachers/{id}`.

## 6. Requisitos no funcionales

- **Sin N+1:** el listado público carga reviews + autor + respuesta en una consulta paginada;
  el agregado se resuelve con `AVG`/`COUNT` en SQL, no en Java.
- **Concurrencia:** la UNIQUE `(student_id, teacher_id)` es la última defensa; dos `POST`
  concurrentes → una fila y un `409` para el perdedor. La actualización del agregado toma un lock
  pesimista sobre `teacher_profiles` para que dos reviews concurrentes no dejen un promedio
  inconsistente.
- **Paginación:** `page >= 0`, `size` 1..50, default 10/20; fuera de rango → `400`.
- **Rendimiento:** índice `(teacher_id, status, created_at DESC)` para el listado público; el
  agregado usa el mismo índice y `status`.
- **Privacidad:** el DTO público no expone email, IDs internos ni la razón de moderación.
- **Sin WebSockets ni email:** la lista se actualiza al abrir/refrescar. Las notificaciones de
  reviews quedan para una spec posterior.

## 7. Seguridad

- La identidad sale siempre de `CurrentUser`; nunca se acepta `studentId`, `teacherId` de autor,
  `userId` ni `adminUserId` desde el body.
- Crear/editar opinión: solo `ROLE_STUDENT`; responder: solo `ROLE_TEACHER` y dueño del perfil;
  moderar: solo `ROLE_ADMIN`.
- Un recurso ajeno se comporta como **404**, no 403: un estudiante no puede editar la opinión de
  otro; un profesor no puede responder la opinión de otro profesor.
- El profesor visible se valida con `TeacherVisibilityService` (D1); un profesor oculto → 404.
- El texto se valida y se renderiza como texto; no se acepta HTML.
- No se registran tokens, emails ni contenido de la opinión en logs de moderación.

## 8. Invariantes

- **I1** Una sola review por `(student_id, teacher_id)`; la DB lo garantiza con UNIQUE.
- **I2** `rating` siempre 1..5.
- **I3** `rating_average` y `rating_count` siempre reflejan las reviews `PUBLISHED` del profesor.
- **I4** Una review `HIDDEN` no aparece en el listado público ni suma al agregado.
- **I5** La respuesta del profesor solo puede escribirla el dueño del perfil reseñado.
- **I6** La moderación es append-only en `review_moderation_events`; no se borra auditoría.
- **I7** Los DTOs públicos no exponen PII ni IDs internos de usuario.

## 9. API contract

### 9.1 Listado público

```http
GET /api/teachers/{teacherId}/reviews?page=0&size=10
200 SearchResultPage<ReviewResponse>
404 si el profesor no es visible
```

```json
{
  "content": [{
    "id": "uuid",
    "rating": 5,
    "comment": "Explica con ejemplos claros.",
    "authorDisplayName": "Ana",
    "createdAt": "2026-11-02T12:00:00Z",
    "teacherResponse": "Gracias, Ana.",
    "teacherRespondedAt": "2026-11-02T13:00:00Z"
  }],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

### 9.2 Opinión propia

```http
GET /api/teachers/{teacherId}/reviews/me
200 ReviewResponse (incluye status si es HIDDEN)
404 si el estudiante no dejó opinión
```

### 9.3 Crear / editar

```http
POST /api/teachers/{teacherId}/reviews
{ "rating": 5, "comment": "Muy clara." }
→ 201 ReviewResponse
400 rating fuera de rango / comment > 1000
403 sin contacto aceptado o sin perfil
404 profesor no visible
409 ya existe una opinión para este par

PUT /api/reviews/{reviewId}
{ "rating": 4, "comment": "Actualizo mi opinión." }
→ 200 ReviewResponse
404 no es el autor / no existe
409 la opinión está HIDDEN
```

### 9.4 Respuesta del profesor

```http
POST /api/reviews/{reviewId}/response
{ "response": "Gracias por tu comentario." }
→ 200 ReviewResponse
403 no es TEACHER
404 no es el dueño del perfil / no existe
409 la opinión está HIDDEN
```

### 9.5 Moderación ADMIN

```http
GET /api/admin/reviews?status=PUBLISHED&page=0&size=20
200 SearchResultPage<AdminReviewResponse>

POST /api/admin/reviews/{reviewId}/hide
{ "reason": "Contenido ofensivo" }
→ 204

POST /api/admin/reviews/{reviewId}/restore
→ 204
404 si la review no existe
```

`AdminReviewResponse` agrega `status`, `moderationReason`, `moderatedAt`, `moderatedByName`,
`teacherName` y `authorName`; no se usa en endpoints públicos.

## 10. Datos afectados

### Migración `V18__reviews_enabled.sql`

- `reviews`:
  - `status varchar(20) NOT NULL DEFAULT 'PUBLISHED' CHECK (status IN ('PUBLISHED','HIDDEN'))`;
  - `teacher_response text`;
  - `teacher_responded_at timestamptz`;
  - `moderated_by uuid NULL REFERENCES users(id) ON DELETE RESTRICT`;
  - `moderated_at timestamptz`;
  - `moderation_reason text`;
  - `CONSTRAINT uq_reviews_student_teacher UNIQUE (student_id, teacher_id)`;
  - índice `(teacher_id, status, created_at DESC)`.
- Nueva tabla `review_moderation_events`:
  - `id uuid PK`;
  - `review_id uuid NOT NULL REFERENCES reviews(id) ON DELETE RESTRICT`;
  - `admin_user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT`;
  - `action varchar(20) NOT NULL CHECK (action IN ('HIDDEN','RESTORED'))`;
  - `reason text`;
  - `created_at timestamptz NOT NULL DEFAULT now()`.
- No se tocan `reports` ni `notifications` en esta spec; quedan para sus propias specs.

### Código

- `model.Review` + `model.enums.ReviewStatus`.
- `review` module: controller, service, repository, DTOs.
- `TeacherProfile` ya tiene `rating_average`/`rating_count`; se actualizan desde `ReviewService`.
- `MessageRepository` gana un `existsByConversation_IdAndSender_Id` para la elegibilidad.
- No se agrega dependencia nueva.

## 11. Criterios de aceptación

| # | Escenario | Verifica |
|---|---|---|
| CA-1 | Estudiante con conversación y respuesta del profesor crea review → 201 y agregado actualizado | RF-1, RF-2, RF-15 |
| CA-2 | Estudiante sin conversación o sin respuesta del profesor → 403 y no crea fila | RF-4 |
| CA-3 | Profesor no visible → 404 en listado y creación | RF-5, RF-6 |
| CA-4 | Segundo POST del mismo par → 409; la UNIQUE lo garantiza ante concurrencia | RF-3, I1 |
| CA-5 | `rating=0`, `rating=6` o `comment` >1000 → 400 | RF-2 |
| CA-6 | Listado público excluye `HIDDEN`, incluye respuesta y no expone PII | RF-6, RF-7, I4, I7 |
| CA-7 | Autor edita su review → 200 y agregado recalculado; otro estudiante → 404 | RF-9, I1 |
| CA-8 | Profesor dueño responde; otro profesor → 404; review HIDDEN → 409 | RF-10, RF-11 |
| CA-9 | ADMIN oculta → 204, sale del listado y del agregado; restaura → vuelve | RF-12..RF-15 |
| CA-10 | Toda moderación deja fila append-only en `review_moderation_events` | RF-14, I6 |
| CA-11 | Sin reviews, `ratingAverage=0.00` y `ratingCount=0` | RF-15 |
| CA-12 | El listado paginado mantiene orden `createdAt DESC, id ASC` y no hace N+1 | §6 |

## 12. Out of scope

- **Reportes de opiniones** (`reports`): spec separada; el ADMIN puede ocultar manualmente.
- **Notificaciones por email/in-app de nuevas reviews**: spec separada; no se agrega evento al
  outbox.
- **Borrado de reviews** por el autor o el admin.
- **Pre-moderación** (revisar antes de publicar); se publica y se oculta después.
- **Múltiples reviews por par** o reviews de un profesor por alguien sin contacto.
- **Respuestas anidadas** (solo una respuesta del profesor).
- **Agregados históricos** (average por período, distribución de estrellas).
- **Fotos/adjuntos** en reviews.

## 13. Decisiones de REVIEW (resueltas 2026-11-02)

1. **S1 — Contacto aceptado:** ¿alcanza con que el profesor haya enviado al menos un mensaje, o
   querés un estado explícito `ACCEPTED` en la conversación? Recomendación: el mensaje del profesor.
2. **S2/S3 — Una review por par y sin borrado:** ¿confirmás que la única acción es editar?
3. **S5 — Moderación posterior:** ¿la review se publica de inmediato y el admin oculta, o querés
   aprobación previa?
4. **S6 — Nombre del autor:** ¿`users.name` o “Estudiante” cuando falta? ¿Querés mostrar iniciales?
5. **S7 — Reportes/notificaciones:** ¿quedan fuera de esta spec o los incluimos en la misma?
6. **Límite de comentario/respuesta:** propuesto 1000 caracteres; ¿otro valor?

## 14. Definition of Done

- [ ] Spec aprobada por el director/arquitecto.
- [ ] ADR-033 (modelo de reviews, contacto aceptado y moderación) aceptado.
- [ ] Migración `V18` aplicada en limpio y sobre una base con `V17`; `ddl-auto=validate` verde.
- [ ] Entidad/repositorio/servicio/controller con tests de integración (felices, errores,
      ownership, roles, visibilidad, concurrencia, agregados y auditoría).
- [ ] Frontend: listado en perfil, formulario de alta/edición, respuesta del profesor y moderación
      admin; tests de componentes y `api.ts`.
- [ ] E2E del flujo completo (estudiante opina → profesor responde → admin oculta/restaura).
- [ ] `mvn -B clean test` y `npm test` verdes; E2E verde con Docker.
- [ ] `README.md`, `docs/architecture/database.md`, `docs/architecture/security.md` y
      `guia-proyecto.md` actualizados.
- [ ] Sin cambios de contrato fuera de los endpoints de esta spec.

## 15. Plan de implementación propuesto (no ejecutado)

1. Aprobar esta spec y el ADR-033.
2. `V18` + entidad `Review`/`ReviewStatus` + `ReviewModerationEvent`.
3. Repositorios y `ReviewService` (elegibilidad, unicidad, agregados con lock).
4. Controllers/DTOs + reglas de `SecurityConfig`.
5. Tests de integración y unitarios de agregado/eligibilidad.
6. Frontend: tipos/API, sección de opiniones, respuesta y admin.
7. E2E y actualización documental.

## 16. Estado de implementación (2026-11-02)

Implementado:

- `V18__reviews_enabled.sql` (estado, respuesta, moderación, UNIQUE por par, índice y auditoría).
- Entidades `Review`/`ReviewModerationEvent` + enums.
- Módulo `review`: repositorios, `ReviewService`, `ReviewController` y `AdminReviewController`.
- Seguridad por rol y ownership en `SecurityConfig`.
- Frontend: sección de opiniones en el perfil, alta/edición, respuesta del profesor y
  `/admin/opiniones`.
- Spec aprobada y `ADR-033` aceptado.

Pendiente de verificación: `mvn -B clean test` completo y E2E, bloqueados en este entorno porque
Docker/PostgreSQL no está disponible. Los tests unitarios + ArchUnit y el build del frontend están
verdes.

