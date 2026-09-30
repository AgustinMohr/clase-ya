# ClaseYa — Verificación documental (TEACHER-001)

Circuito de **verificación académica** del profesor: el profesor presenta credenciales con
documentación, un administrador decide, y el sistema deja traza. Implementa el slice B del spec
`docs/specs/active/TEACHER-001-teacher-page-and-verification.md`.

## Dos conceptos que no se mezclan

| Concepto | Pregunta | Estados |
|---|---|---|
| **Publicación del anuncio** | ¿el anuncio está listo para verse? | `INCOMPLETE` · `PUBLISHED` |
| **Verificación académica** | ¿validamos una credencial con documentación? | `PENDING` · `UNDER_REVIEW` · `MORE_INFO_REQUIRED` · `VERIFIED` · `REJECTED` |

**Publicado ≠ verificado.** La verificación solo agrega confianza; no habilita la existencia.

## Modelo de datos (migración `V8`, nueva)

- `teacher_profiles.verification_status`: el `CHECK` pasa a los 5 estados. Es el **estado agregado**
  del perfil, persistido pero **recalculado** en la misma transacción de cada transición (D10).
- `teacher_education` (la **credencial**, unidad verificable): gana `verification_status` y
  `submitted_at`; pierde `is_verified` (D2: una sola fuente de verdad).
- `verification_documents`: evidencia privada e inmutable. `content bytea` (D5), `sha256`, metadatos.
- `verification_decisions`: auditoría **append-only** con `previous_status`, `new_status`, `decision`,
  `method`, `reason`, `admin_user_id`, `decided_at`.

## Agregación del perfil (RF-14)

Precedencia sobre los estados de las credenciales:
`VERIFIED > UNDER_REVIEW > MORE_INFO_REQUIRED > REJECTED > PENDING`.
Una credencial `PENDING` (nunca presentada) no cuenta. En código: `VerificationAggregator`.

## Endpoints — profesor (`/api/teachers/me/**`, rol TEACHER o ADMIN)

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/verification` | Estado del perfil + estado de cada credencial + conteo de documentos |
| `POST` | `/verification/submit` | Presenta **todas** las credenciales elegibles |
| `POST` | `/education/{educationId}/documents` | `upload` (multipart: `file`, `type`) → 201 |
| `GET` | `/education/{educationId}/documents` | Metadatos de la evidencia |
| `POST` | `/education/{educationId}/submit` | Presenta **esa** credencial |
| `GET` | `/documents/{documentId}/content` | Descarga (dueño) |
| `DELETE` | `/documents/{documentId}` | Borra un documento no usado como evidencia |

### `upload` vs `submit`

- **`upload` no cambia estados** (RF-9/I3): solo adjunta evidencia.
- **`submit`** es la acción explícita que pone la credencial en `UNDER_REVIEW` (RF-10):
  - Elegible = ≥1 documento y estado ≠ `VERIFIED`.
  - `PENDING` / `MORE_INFO_REQUIRED` / `REJECTED` → `UNDER_REVIEW` (setea `submitted_at`).
  - `UNDER_REVIEW` → **200 idempotente**, no reinicia la antigüedad en la cola.
  - `VERIFIED` → **200 idempotente**, no vuelve a revisión.
  - Sin documentos → **400**.

### Validación de archivos (backend, nunca se confía en el cliente)

PDF / JPG / PNG por **magic bytes** (no por extensión ni `Content-Type`); máximo **10 MB** (413);
hasta 5 documentos y 25 MB por credencial; **SHA-256** calculado; nombre original **saneado**.
`spring.servlet.multipart.max-file-size` se subió a 10 MB (el default de 1 MB rechazaría evidencia
válida).

## Endpoints — administración (`/api/admin/**`, rol ADMIN únicamente)

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/verifications?status=&page=&size=` | Cola (default `UNDER_REVIEW`), **más antiguos primero** por `submitted_at`, paginada |
| `GET` | `/verifications/{teacherId}` | Detalle: credenciales, documentos e historial completo |
| `GET` | `/documents/{documentId}/content` | Descarga (auditada en el log de la aplicación) |
| `POST` | `/verifications/{teacherId}/credentials/{educationId}/decision` | `{"decision":"VERIFIED"\|"REJECTED"\|"MORE_INFO_REQUIRED","method":"...","reason":"..."}` |
| `POST` | `/verifications/{teacherId}/revoke` | Revoca la última credencial verificada: `{"method":"...","reason":"..."}` |

`MORE_INFO_REQUIRED` **exige motivo**; la revocación **exige motivo y método**.

## Inmutabilidad de la evidencia (RF-19..RF-23)

- Una credencial `VERIFIED` **no** puede editar campos materiales (`institution`, `degree`,
  `startYear`, `endYear`) ni eliminarse → **409**. `description` sí es editable.
- Ninguna credencial con documentos o decisiones se borra → **409**.
- Reemplazar un documento **crea uno nuevo**; el binario anterior nunca se sobrescribe.

## Concurrencia (RF-25)

Toda transición toma un **lock de escritura pesimista sobre la fila `teacher_profiles`**
(`findByIdForUpdate`). La decisión se aplica **sobre el estado leído**: si ya no es `UNDER_REVIEW`,
recibe **409**. Dos administradores decidiendo a la vez → una transición aplicada y otra 409,
**nunca last-write-wins**; el estado agregado del perfil nunca queda inconsistente (I12).

## Seguridad

- La identidad sale siempre del `SecurityContext` (`CurrentUser`), nunca del cuerpo.
- Un documento de otro profesor → **404** (no se revela que existe).
- Un `ADMIN` no puede decidir sobre su propia cuenta → **409**.
- Los binarios nunca viajan en JSON; la descarga es siempre `attachment` con un `Content-Type` de la
  lista permitida. Nada del contenido va a logs.
- `/api/admin/**` es el primer uso de `ADMIN` fuera del catálogo académico y está explícito en
  `SecurityConfig`.

## Códigos HTTP

| Código | Cuándo |
|---|---|
| **400** | Entrada/formato inválido (tipo de documento, sin documentos al presentar, motivo faltante) |
| **403** | Rol insuficiente (`/api/admin/**` sin ADMIN) |
| **404** | Recurso inexistente o ajeno |
| **409** | Transición inválida, evidencia protegida, conflicto de concurrencia, admin sobre su cuenta |
| **413** | Archivo mayor a 10 MB |

## Código

- `verification/service`: `VerificationAggregator`, `DocumentContentValidator`, `DocumentStorage` +
  `PostgresDocumentStorage`, `TeacherVerificationService`, `VerificationDocumentService`,
  `VerificationSubmissionService`, `VerificationDecisionService`, `VerificationQueryService`.
- `verification/controller`: `TeacherVerificationController`, `AdminVerificationController`.

## Fuera del MVP (documentado)

Purga/retención automática de documentos (D6), integraciones con registros oficiales/OCR/IA (D3),
notificaciones por email, límite de reenvíos (D11), moderación previa (D8) y "materias verificadas"
(D7).
