# TEACHER-001 — Página propia del profesor y circuito de verificación documental

**ID:** TEACHER-001
**Estado:** Activa — **revisión arquitectónica aplicada** — pendiente de decisión de producto (ver §13)
**Carpeta:** `docs/specs/active/`
**Nivel de cambio:** 2 (capacidad nueva + contrato de API + migración)

> **Nota de revisión (2026-09-30):** esta versión reemplaza a la anterior tras inspeccionar el código.
> Los cambios importantes: se separan **publicación** de **verificación** (§12 del pedido de review),
> se modelan **dos niveles de estado** (credencial y perfil) con una tabla de agregación exhaustiva,
> se corrigen `upload` vs `submit`, se protege la evidencia de una credencial verificada, la
> auditoría pasa a registrar transiciones, y **D1 se amplía**: el código actual exige `VERIFIED` en
> **cinco** lugares, no solo en la búsqueda.

---

## 1. Objetivo

Dar al profesor un área propia para **publicar y mantener su anuncio**, y cerrar el **circuito de
verificación académica** para que el badge "Verificado" signifique algo real, auditable y decidido
por una persona.

**Dos conceptos que no deben mezclarse nunca:**

| Concepto | Pregunta que responde | Estados |
|---|---|---|
| **Publicación / completitud del anuncio** | ¿el anuncio está listo para que un estudiante lo vea? | `INCOMPLETE` · `PUBLISHED` |
| **Verificación académica** | ¿ClaseYa pudo validar una credencial respaldada con documentación? | `PENDING` · `UNDER_REVIEW` · `MORE_INFO_REQUIRED` · `VERIFIED` · `REJECTED` |

**Publicado ≠ verificado.** Un anuncio publicado sin credenciales es un anuncio **visible y "No
verificado"**. La verificación solo agrega confianza; no habilita la existencia.

## 2. Contexto — estado real del repositorio (inspeccionado)

**Contratos existentes que se reutilizan tal cual:**

| Capacidad | Contrato |
|---|---|
| Perfil | `POST /api/teachers/profile`, `GET /api/teachers/me`, `PUT /api/teachers/me` |
| Materias | `GET/POST /api/teachers/me/subjects`, `DELETE /{careerSubjectId}` |
| Modalidades | `GET/POST /api/teachers/me/modalities`, `DELETE /{modality}` |
| Formación | `GET/POST /api/teachers/me/education`, `PUT /{id}`, `DELETE /{id}` |
| Disponibilidad | `GET/POST /api/availability`, `GET /me`, `PUT /{id}`, `POST /{id}/disable|enable` |

**Hallazgos de la inspección que condicionan el diseño:**

1. **`verificationStatus == VERIFIED` es precondición dura en cinco lugares** (no solo en la búsqueda):

   | # | Lugar | Efecto hoy |
   |---|---|---|
   | 1 | `TeacherSpecifications.visible()` | no aparece en la búsqueda |
   | 2 | `TeacherSearchService.getPublic` | detalle → 404 |
   | 3 | `FavoriteService.requireVisibleTeacher` | no se puede guardar como favorito (404) |
   | 4 | `ConversationService.start` | no se puede contactar (404) |
   | 5 | **`AvailabilityService.requireEligibleTeacher`** | **no puede publicar disponibilidad (403)** |

   El punto 5 es el más grave para esta spec: **un profesor no verificado no puede armar su
   disponibilidad**, así que no puede completar su anuncio antes de verificarse. El circuito queda
   circular. Se resuelve conceptualmente separando "publicar" de "verificar" (§13, D9).

2. **Los endpoints de formación no tienen ninguna guarda de verificación**
   (`TeacherEducationService`): `update` sobrescribe `institution`, `degree`, `description`,
   `startYear` y `endYear` sin restricción, y `delete` **borra físicamente** la fila. Nada impide
   hoy editar o eliminar la evidencia de una credencial validada (no ocurre todavía porque nada se
   valida, pero ocurrirá). Requisitos RF-19..RF-23.

3. **`is_verified` es gestionado por el sistema, no por el profesor**: `TeacherEducationService.create`
   fuerza `setIsVerified(false)` con el comentario *"system-managed (admin), never set by the
   teacher"*. `TeacherEducationResponse` ya lo expone, y el frontend lo ignora.

4. **`TeacherProfileService`**: `create` fija `VerificationStatus.PENDING` y rechaza un segundo perfil
   con 409; `update` **nunca toca** `verificationStatus` (correcto: editar el perfil no altera la
   verificación). Dato útil: `applyEditableFields` asigna `bio`/`address`/`latitude`/`longitude`
   siempre, pero `availabilityNote`, `pricePerHour`, `city` y `photoUrl` **solo si vienen no nulos**
   → hoy el profesor **no puede limpiar** su precio, ciudad o foto desde la API (riesgo MEDIO, §E).

5. **Los tests fijan el comportamiento actual** y deberán cambiar si se aprueba D1/D9:
   `TeacherSearchIntegrationTest#search_onlyReturnsVerifiedAndActive` y `#search_excludesPendingDetail`,
   `FavoriteIntegrationTest`, `AvailabilityIntegrationTest`, `MessagingIntegrationTest`
   (contacto a un profesor PENDING → 404) y `TeacherCardIntegrationTest`.

6. **No hay módulo de administración**: el único uso de `ROLE_ADMIN` es el catálogo académico. Los
   endpoints `/api/teachers/me/**` aceptan `hasAnyRole("TEACHER","ADMIN")`, así que un admin **puede**
   tener perfil de profesor → la regla "un admin no decide sobre su propia cuenta" es necesaria (§7).

7. **Migraciones aplicadas: `V1`..`V7`.** No existe `V8`: el número propuesto está libre.
   `teacher_profiles.verification_status` tiene `CHECK (PENDING | VERIFIED | REJECTED)` — le faltan
   dos estados — y `teacher_education.is_verified boolean NOT NULL DEFAULT false`.

8. **No existe ningún almacenamiento de archivos** ni dependencia de multipart más allá de Spring Web.

## 3. Actores

| Actor | Qué hace |
|---|---|
| Profesor (TEACHER) | Publica su anuncio (perfil, materias, modalidades, formación, disponibilidad), presenta credenciales, ve el estado y responde requerimientos. |
| Administrador (ADMIN) | Revisa credenciales, decide con método y motivo, deja traza y puede revocar. |
| Estudiante / visitante | Ve anuncios publicados y distingue **publicado** de **verificado**. |
| Sistema | Calcula el estado agregado del perfil, guarda documentos de forma privada y audita. |

## 4. Requisitos funcionales

### A. Publicación (independiente de la verificación)

- **RF-1** — El profesor tiene un área **"Mi anuncio"** que reutiliza los endpoints existentes para
  editar perfil, materias, modalidades, formación y disponibilidad.
- **RF-2** — El sistema informa la **completitud del anuncio** (`INCOMPLETE` / `PUBLISHED`) según un
  conjunto mínimo: perfil con `name` y `bio`, al menos 1 materia, al menos 1 modalidad y al menos 1
  franja de disponibilidad. Es **informativo**: no bloquea la edición ni la verificación.
- **RF-3** — Un anuncio `PUBLISHED` es visible y contactable **con independencia de su estado de
  verificación** (depende de D1/D9).
- **RF-4** — El profesor puede **previsualizar** su anuncio como lo ve un estudiante.
- **RF-5** — El navbar de un `TEACHER` ofrece **Mi anuncio** y no ofrece acciones de estudiante
  (Contactar / Favoritos).

### B. Credenciales y documentos

- **RF-6** — Se llama **credencial** a cada fila de `teacher_education`. Es la **unidad verificable**:
  el estado de verificación se lleva por credencial, no por materia ni por el anuncio entero.
- **RF-7** — Tipos de documento admitidos: **título/diploma** (con código o QR verificable si lo
  tuviera), **constancia de título en trámite**, **certificado analítico**, **certificación de
  posgrado**, **matrícula profesional** cuando corresponda, **título del exterior con
  legalización/apostilla**.
- **RF-8** — Formatos y límites: PDF, JPG o PNG; **hasta 10 MB** por archivo; hasta 5 documentos por
  credencial; el total por credencial no supera 25 MB.
- **RF-9** — **Subir (`upload`) NO cambia el estado de verificación.** Un documento queda asociado a la
  credencial como material presentado, sin pasar nada a revisión.
- **RF-10** — **Presentar (`submit`) es la acción explícita** que pone una credencial en
  `UNDER_REVIEW`. Aplica a credenciales en `PENDING`, `MORE_INFO_REQUIRED` y `REJECTED`
  (reenvío). Detalle y semántica exacta en §8.
- **RF-11** — El profesor puede declarar por credencial si pidió **verificación en la institución** y
  adjuntar el dato o código de verificación (texto libre acotado, ≤ 500 caracteres).

### C. Dos niveles de estado (credencial y perfil)

- **RF-12** — **Estado de cada credencial**: `PENDING`, `UNDER_REVIEW`, `MORE_INFO_REQUIRED`,
  `VERIFIED`, `REJECTED`.
- **RF-13** — **Estado del perfil**: mismo vocabulario, pero con **semántica agregada** sobre las
  credenciales. La regla fundamental es:

  > **Perfil `VERIFIED` ⇔ existe al menos una credencial `VERIFIED`.**

- **RF-14** — Agregación **exhaustiva** (no queda a interpretación del implementador). Precedencia
  de arriba hacia abajo:

  | # | Situación de las credenciales | Estado del perfil |
  |---|---|---|
  | 1 | Existe **≥1 `VERIFIED`** | **`VERIFIED`** (aunque haya otras `REJECTED` o `MORE_INFO_REQUIRED`) |
  | 2 | Ninguna `VERIFIED`, pero existe **≥1 `UNDER_REVIEW`** | `UNDER_REVIEW` |
  | 3 | Ninguna `VERIFIED` ni `UNDER_REVIEW`, pero existe **≥1 `MORE_INFO_REQUIRED`** | `MORE_INFO_REQUIRED` |
  | 4 | Ninguna `VERIFIED`, todas las presentadas **`REJECTED`** (y ≥1 presentada) | `REJECTED` |
  | 5 | Nunca se presentó ninguna credencial | `PENDING` |

  Notas obligatorias de la tabla:
  - El caso **"una `VERIFIED` + otra `REJECTED`"** → perfil `VERIFIED` (fila 1). Rechazar una
    credencial **no** rechaza el perfil.
  - El caso **"una `VERIFIED` + otra `MORE_INFO_REQUIRED`"** → perfil `VERIFIED`, **pero** el
    profesor debe ver que esa credencial necesita información (RF-16).
  - **Revocación de la única credencial `VERIFIED`**: el perfil **recalcula** con la tabla (si no
    queda ninguna `VERIFIED` → pasa a `UNDER_REVIEW`/`MORE_INFO_REQUIRED`/`REJECTED`/`PENDING` según
    corresponda). El perfil **nunca** queda en un estado que no surja de la tabla.
  - Los estados de las credenciales `PENDING` (creadas sin documentos) son irrelevantes para la
    agregación: una credencial sin `submit` no cuenta para ninguna fila salvo la 5.

- **RF-15** — El perfil **no se edita a mano**: su estado se **recalcula** en cada transición de
  credencial dentro de la misma transacción. Persistirlo es una decisión de implementación (D10),
  pero si se persiste debe ser consistente con la tabla en todo momento.

### D. Revisión y decisiones

- **RF-16** — `MORE_INFO_REQUIRED` **existe a nivel de credencial**: el admin pide información
  adicional con un **motivo obligatorio** que el profesor ve en texto claro junto a esa credencial.
  Quién lo produce: solo un `ADMIN` (§5). El profesor responde subiendo/reemplazando documentos
  (RF-9) y volviendo a presentar (`submit`, RF-10), lo que devuelve la credencial a `UNDER_REVIEW`.
  Si no responde, la credencial queda en `MORE_INFO_REQUIRED` indefinidamente y el perfil sigue en
  el estado que dicta la tabla (no expira en el MVP). La auditoría registra quién, cuándo, por qué y
  qué transición (§7, §9).
- **RF-17** — El admin decide **por credencial** (`VERIFIED` / `REJECTED` / `MORE_INFO_REQUIRED`).
  El **estado del perfil se deriva** (RF-14): el admin **no** decide sobre el perfil directamente.
- **RF-18** — **La evidencia es inmutable**: ninguna decisión posterior puede modificar el binario,
  ni los datos materiales, ni el revisor original de lo ya decidido. RF-19..RF-23 detallan las reglas.

  - **RF-19** — Una credencial **`VERIFIED`** no puede **editarse** en sus campos **materiales**
    (`institution`, `degree`, `startYear`, `endYear`): cualquier intento → **409**. Sí puede editarse
    `description` (narrativo, no material).
  - **RF-20** — Una credencial **`VERIFIED`** no puede **eliminarse** → **409**.
  - **RF-21** — Tampoco puede eliminarse ninguna credencial que tenga **documentos** o **decisiones**
    asociadas: la evidencia histórica se preserva (FK `RESTRICT`, coherente con la regla del repo de
    no borrar datos históricos). El profesor solo puede eliminar credenciales **nunca presentadas y
    sin documentos**.
  - **RF-22** — Para cambiar algo material de una credencial verificada, el camino es **presentar una
    credencial nueva** (otra fila), dejando la anterior como historial verificado.
  - **RF-23** — **Reemplazar un documento nunca sobrescribe**: se agrega un documento **nuevo** con
    identidad propia; el anterior permanece inmutable y la auditoría conserva la referencia. Ningún
    binario que sustentó una decisión se modifica.
- **RF-24** — Un `ADMIN` **no puede decidir sobre su propia cuenta** → 409.
- **RF-25** — **Concurrencia**: si dos admins deciden a la vez sobre la misma credencial, la decisión
  se aplica de forma **atómica** sobre el estado leído: la segunda, si parte de un estado que ya no
  es el vigente (p. ej. `UNDER_REVIEW → VERIFIED` cuando ya se resolvió `REJECTED`), recibe **409**.
  Nunca "last write wins".

### E. Transparencia hacia el estudiante

- **RF-26** — El anuncio muestra el estado del perfil: badge **"Verificado"** solo para `VERIFIED`;
  para el resto, etiqueta neutra **"No verificado"**.
- **RF-27** — La formación distingue **"Credencial verificada"** de **"Título declarado"** por
  entrada, derivado de `verificationStatus == VERIFIED` de esa credencial (ver D2).
- **RF-28** — El anuncio **nunca** expone documentos, motivos internos, datos del revisor, email,
  dirección ni coordenadas exactas.

## 5. Requisitos no funcionales

- **Privacidad por diseño:** los documentos se guardan fuera del alcance público. No existe ni
  existirá URL pública, listable o adivinable.
- **MVP sin integraciones:** el circuito funciona con revisión **100% humana**. No se agregan OCR,
  IA, firma digital ni integraciones con registros oficiales. Si algún día entran, son auxiliares y
  **nunca** decisorias.
- **Paginación obligatoria** en la cola del admin y en el historial del profesor: se reutiliza
  `SearchResultPage`; nada se carga completo en memoria.
- **Orden de la cola:** "más antiguos primero" por el **momento de presentación**, no por la creación
  de la credencial. Se requiere un campo explícito **`submitted_at`** (por credencial), actualizado
  en cada `submit`; ordenar por `created_at` daría un orden engañoso para una credencial creada hace
  meses y presentada ayer.
- **Sin N+1:** listar la cola con sus credenciales y documentos se resuelve con un número constante
  de consultas (patrón ya usado por `TeacherSummaryAssembler`).
- **Atomicidad:** cada decisión se aplica en **una** transacción que incluye: el cambio de estado de
  la credencial, el recálculo del estado del perfil (RF-14) y la escritura de la auditoría. O se
  aplica todo, o no se aplica nada.
- **Auditoría append-only:** los registros de decisión son de solo inserción; no se editan ni se
  borran.
- **Límites de archivo aplicados en el backend** (no confiar en el cliente) y también en la
  configuración del contenedor.
- **Sin notificaciones por email** (coherente con CONTACT-001 D3): el estado y los requerimientos se
  ven en la propia área del profesor.
- **Sin purga automática de documentos en el MVP** (ver D6).

## 6. Seguridad

**Documentos**

- Acceso **solo** al profesor dueño y a un `ADMIN` autenticado, vía endpoint protegido con chequeo de
  ownership/rol. Un tercero (incluido otro profesor) recibe **404**, indistinguible de "no existe".
- Se valida el **contenido real** del archivo (magic bytes → PDF/JPG/PNG), no la extensión ni el
  `Content-Type` declarado por el cliente.
- El nombre original se **sanea** (sin rutas, sin caracteres de control, longitud acotada).
- **Nunca** se sirve HTML ni SVG; se fuerza `Content-Disposition: attachment` y un `Content-Type`
  propio de la lista permitida.
- Los binarios **nunca** viajan en respuestas JSON ni se expone el `storage_key`/path.
- Nada del contenido de los documentos ni datos personales (DNI, legajo, firma) va a **logs**; los
  errores devuelven mensajes genéricos.

**Autorización**

- Endpoints del profesor: rol `TEACHER` (y `ADMIN` donde ya lo permite la configuración actual de
  `/api/teachers/me/**`).
- Endpoints de revisión: rol `ADMIN` **únicamente**. Es el primer uso de `ADMIN` fuera del catálogo
  académico, así que `/api/admin/**` debe quedar explícitamente fuera de `permitAll` con
  `.hasRole("ADMIN")`, y ninguna ruta de verificación puede entrar en la lista pública.
- El frontend oculta la vista de revisión por rol, pero **la autorización real es del backend**.

**Decisiones**

- Toda decisión registra actor, momento, objeto, método y motivo (§9), y es rechazada si el admin
  actúa sobre su propia cuenta (RF-24).
- La decisión se aplica sobre el estado leído y falla con **409** si el estado ya cambió (RF-25).

**Códigos HTTP (semántica unificada, sin ambigüedades)**

| Código | Cuándo |
|---|---|
| **400** | Entrada o formato inválido (tipo/campos/estado de entrada mal formado) |
| **403** | Rol insuficiente (p. ej. no `ADMIN` en `/api/admin/**`, o publicar disponibilidad sin cumplir el requisito vigente) |
| **404** | Recurso inexistente **o ajeno** cuando corresponde ocultar existencia (documentos de otro profesor) |
| **409** | Transición inválida, recurso protegido (evidencia inmutable), conflicto de concurrencia, admin sobre su propia cuenta, perfil duplicado |
| **413** | Archivo mayor a 10 MB |

## 7. Invariantes

- **I1** — `Perfil VERIFIED ⇔ ∃ al menos una credencial VERIFIED` (la tabla de RF-14 es la única
  fuente de verdad de la agregación).
- **I2** — Solo una acción humana de `ADMIN` produce `VERIFIED`/`REJECTED`/`MORE_INFO_REQUIRED`.
  Ningún proceso automático cambia estados de verificación.
- **I3** — `upload` no cambia estados; solo `submit` (PENDING/MORE_INFO_REQUIRED/REJECTED →
  `UNDER_REVIEW`).
- **I4** — Todo cambio de estado queda auditado con estado anterior y nuevo, actor, momento, objeto,
  método y motivo.
- **I5** — Una credencial `VERIFIED` no se edita en campos materiales ni se elimina (RF-19/RF-20), y
  ningún registro con documentos o decisiones se borra físicamente (RF-21).
- **I6** — Ningún binario ya usado como evidencia se modifica o se sobrescribe (RF-23).
- **I7** — Ningún documento es recuperable sin autenticación + autorización, ni aparece en respuestas
  públicas.
- **I8** — El badge "Verificado" se muestra **solo** con `verificationStatus = VERIFIED`; cualquier
  otro estado se muestra "No verificado" (RF-26).
- **I9** — Ningún **título declarado** se presenta como credencial verificada (RF-27).
- **I10** — Las transiciones válidas son solo las de RF-12/RF-14/RF-25; cualquier otra → 409.
- **I11** — Editar el perfil, las materias, las modalidades, la disponibilidad o el `description` de
  una credencial **no** altera ningún estado de verificación.
- **I12** — El estado del perfil siempre coincide con la tabla de RF-14 aplicada a sus credenciales
  (verificable con una consulta de integridad).

## 8. API contract

### Reutilizado (sin cambios de contrato)

Perfil, materias, modalidades y formación (`/api/teachers/me/...`) y disponibilidad
(`/api/availability...`), con las guardas nuevas de RF-19..RF-23 en formación.

### Nuevo — profesor

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/teachers/me/verification` | Estado del **perfil** + estado de **cada credencial** + requerimiento vigente + historial **paginado** (vista TEACHER, §9) |
| `POST` | `/api/teachers/me/education/{educationId}/documents` | `upload` (multipart: `file`, `type`) → 201 con metadatos. **No cambia estados** |
| `GET` | `/api/teachers/me/education/{educationId}/documents` | Metadatos de los documentos de la credencial |
| `GET` | `/api/teachers/me/documents/{documentId}/content` | Descarga (dueño) |
| `DELETE` | `/api/teachers/me/documents/{documentId}` | Elimina un documento **no** usado como evidencia (si lo sustenta → 409) |
| `POST` | `/api/teachers/me/education/{educationId}/submit` | Presenta **esa** credencial a revisión |
| `POST` | `/api/teachers/me/verification/submit` | Presenta **todas** las credenciales elegibles (sin `VERIFIED`) en una sola acción |

**Semántica de `submit` (resuelve la ambigüedad de la versión anterior):**

- **Elegible** = credencial con al menos 1 documento y estado ≠ `VERIFIED`.
- `submit` de una credencial elegible → `UNDER_REVIEW` y actualiza `submitted_at`.
- **Idempotencia:** si la credencial ya está `UNDER_REVIEW`, `submit` responde **200** sin cambios
  (no reinicia la antigüedad en la cola, para no perder el lugar de espera). Si está `VERIFIED`,
  **no** la devuelve a revisión: responde **200** con el estado sin cambios.
- `submit` sobre una credencial **sin documentos** → **400** (no hay nada que revisar).
- Estados de origen permitidos: `PENDING`, `MORE_INFO_REQUIRED`, `REJECTED`. Cualquier otro → 409
  (salvo los 200 de idempotencia recién descritos).
- Tras un `submit`, el estado del **perfil** se recalcula (RF-14).

### Nuevo — administración

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/admin/verifications?status=&page=&size=` | Cola (por defecto `UNDER_REVIEW`), **más antiguos primero por `submitted_at`**, paginada |
| `GET` | `/api/admin/verifications/{teacherId}` | Detalle: anuncio declarado, credenciales, documentos, historial completo (vista ADMIN) |
| `GET` | `/api/admin/documents/{documentId}/content` | Descarga (admin), auditada |
| `POST` | `/api/admin/verifications/{teacherId}/credentials/{educationId}/decision` | `{"decision":"VERIFIED"\|"REJECTED"\|"MORE_INFO_REQUIRED","method":"...","reason":"..."}` |
| `POST` | `/api/admin/verifications/{teacherId}/revoke` | Revoca la última credencial verificada: `{"method":"...","reason":"..."}` (obligatorio) |

### Cambios en lo público

- `GET /api/teachers/{id}` y `GET /api/teachers` **ya** exponen `verificationStatus` y, por credencial,
  `isVerified` (derivado, D2). El cambio real depende de D1/D9 (§13) y del filtro "solo verificados".

## 9. Datos afectados

**Migración `V8` (nueva; `V1..V7` no se tocan):**

1. `teacher_profiles.verification_status`: reemplazar el `CHECK` por los 5 estados de RF-12.
2. `teacher_education`: agregar **`verification_status`** (mismo enum, default `PENDING`) y
   **`submitted_at timestamptz`** (nullable; se setea en cada `submit`), con índice
   `(verification_status, submitted_at)` para la cola. Según D2, `is_verified` se **elimina**
   (migrando su valor al nuevo estado) o se conserva como columna derivada — **nunca** como segunda
   fuente de verdad.
3. Tabla **`verification_documents`**: `id`, `teacher_id`, `teacher_education_id`, `type`,
   `original_filename`, `content_type`, `size_bytes`, `sha256`, `storage_key`, `uploaded_at`,
   `uploaded_by`. **Nada de `content` en JSON.** FK a `teacher_education` con
   `ON DELETE RESTRICT` (soporta RF-21).
4. Tabla **`verification_decisions`** (append-only = auditoría): `id`, `teacher_id`,
   `teacher_education_id` (**nullable**), `document_id` (nullable), `admin_user_id`,
   **`previous_status`**, **`new_status`**, `decision`, `method`, `reason`, `decided_at`.
   - Decisión **sobre credencial** → `teacher_education_id ≠ NULL`.
   - Decisión **sobre el perfil** (revocación / recálculo relevante) → `teacher_education_id = NULL`.
5. Índices: `verification_documents(teacher_id)`, `verification_documents(teacher_education_id)`,
   `verification_decisions(teacher_id, decided_at)`, `verification_decisions(teacher_education_id)`.

**Reutilizado:** `teacher_education` como credencial, `teacher_profiles.verification_status` como
estado del perfil, `users`/roles para autorización. `reports` y `notifications` **no** se tocan.

## 10. Criterios de aceptación

### Estados y agregación (el corazón de la revisión)

| # | Escenario | Verifica |
|---|---|---|
| CA-1 | Perfil recién creado: sin credenciales presentadas → perfil `PENDING` | RF-14/5 |
| CA-2 | Una credencial `UNDER_REVIEW` → perfil `UNDER_REVIEW` | RF-14/2 |
| CA-3 | Una credencial `MORE_INFO_REQUIRED` (sin otras) → perfil `MORE_INFO_REQUIRED` | RF-14/3 |
| CA-4 | Todas las presentadas `REJECTED` → perfil `REJECTED` | RF-14/4 |
| CA-5 | **`VERIFIED` + `REJECTED` → perfil `VERIFIED`**, y el rechazo se ve en su credencial | RF-14/1, RF-17 |
| CA-6 | **`VERIFIED` + `MORE_INFO_REQUIRED` → perfil `VERIFIED`**, con el requerimiento visible para el profesor | RF-14/1, RF-16 |
| CA-7 | Validar una credencial → perfil `VERIFIED`, badge público, fila de auditoría | RF-14/1, RF-26, I4 |
| CA-8 | **Revocar la última credencial `VERIFIED`** → el perfil recalcula según sus otras credenciales y deja de mostrar el badge | RF-14, RF-26 |
| CA-9 | Integridad: el estado del perfil siempre coincide con la tabla de RF-14 aplicada a sus credenciales | I1, I12 |

### `upload` vs `submit`

| # | Escenario | Verifica |
|---|---|---|
| CA-10 | `upload` de un documento **no** cambia el estado de la credencial ni del perfil | RF-9, I3 |
| CA-11 | `submit` con documentos → `UNDER_REVIEW` y `submitted_at` actualizado | RF-10 |
| CA-12 | `submit` sin documentos → **400** | §8 |
| CA-13 | `submit` repetido sobre `UNDER_REVIEW` → **200**, mismo estado y **sin perder el lugar en la cola** | §8 (idempotencia) |
| CA-14 | `submit` sobre una credencial `VERIFIED` → **200**, sin volver a revisión | §8 |
| CA-15 | Reenviar una credencial `REJECTED` y una `MORE_INFO_REQUIRED` → `UNDER_REVIEW` | RF-10 |
| CA-16 | Transición no permitida (p. ej. `VERIFIED → UNDER_REVIEW` por `submit`) → 409 salvo los 200 definidos | I10 |

### Evidencia e inmutabilidad

| # | Escenario | Verifica |
|---|---|---|
| CA-17 | Editar campos **materiales** de una credencial `VERIFIED` → **409**; editar `description` → 200 | RF-19 |
| CA-18 | Eliminar una credencial `VERIFIED` → **409** | RF-20 |
| CA-19 | Eliminar una credencial con documentos o decisiones → **409** | RF-21 |
| CA-20 | Reemplazar un documento crea uno **nuevo**: el anterior sigue descargable con su hash y la auditoría lo referencia | RF-23, I6 |
| CA-21 | Cambiar algo material de una credencial verificada se hace con una credencial **nueva**, dejando la anterior como historial | RF-22 |
| CA-22 | Editar perfil, materias, modalidades, disponibilidad o `description` **no** altera ningún estado | I11 |

### Seguridad y errores

| # | Escenario | Verifica |
|---|---|---|
| CA-23 | Archivo **> 10 MB** → **413** | RF-8, §6 |
| CA-24 | Documento de **otro profesor** → **404** (no 403, no se revela que existe) | §6, I7 |
| CA-25 | Usuario no `ADMIN` en `/api/admin/**` → **403** | §6 |
| CA-26 | Ejecutable renombrado a `.pdf` → **400** (validación por contenido real) | §6 |
| CA-27 | Un `ADMIN` decide sobre su propia cuenta → **409** | RF-24 |
| CA-28 | Ningún endpoint público devuelve documentos, motivos internos ni datos del revisor | RF-28, I7 |

### Concurrencia y auditoría

| # | Escenario | Verifica |
|---|---|---|
| CA-29 | Dos admins deciden a la vez sobre la misma credencial: una operación aplica y la otra recibe **409** (sin "last write wins") | RF-25 |
| CA-30 | El historial del **profesor** muestra fecha, credencial, estado y motivo, **sin** datos internos del revisor, y está **paginado** | RF-16, §9 |
| CA-31 | El historial del **admin** muestra actor, método, documento y **estado anterior/nuevo** | §9 |
| CA-32 | Toda decisión es append-only: no se edita ni se borra | I4, §5 |

### Publicación y transparencia

| # | Escenario | Verifica |
|---|---|---|
| CA-33 | El anuncio informa completitud (`INCOMPLETE`/`PUBLISHED`) sin bloquear edición ni verificación | RF-2 |
| CA-34 | Un anuncio `PUBLISHED` sin verificar es visible y contactable con etiqueta **"No verificado"** | RF-3, RF-26 (sujeto a D1/D9) |
| CA-35 | La formación distingue "Credencial verificada" de "Título declarado" por entrada | RF-27, I9 |
| CA-36 | El navbar de un profesor muestra **Mi anuncio** y no acciones de estudiante | RF-5 |

## 11. Out of scope

- **Purga o retención automática** de documentos (D6): el MVP no borra nada por tiempo.
- Integración con registros oficiales, OCR, IA, firma digital o lectura de QR: auxiliares futuros,
  **nunca** decisorios (RF-14, D3).
- Notificaciones por email/push (coherente con CONTACT-001 D3).
- Gestión del trámite de legalización/apostilla (se acepta el documento, no se tramita).
- Verificación de identidad (KYC) más allá del nombre declarado.
- Apelaciones formales, mediación y consecuencias legales de un rechazo.
- Moderación previa del anuncio (D8) y "materias verificadas" (D7).
- Límites anti-abuso de reenvíos (D11) y múltiples anuncios por profesor.
- **Bug existente de `applyEditableFields`** (no permite limpiar `pricePerHour`, `city` ni `photoUrl`
  porque solo aplica valores no nulos, `TeacherProfileService:96-116`): afecta a "Mi anuncio" pero se
  corrige en su propia tarea, no acá.

## 12. Definition of Done

- [ ] Spec aprobada por el director, con las decisiones de §13 resueltas (como mínimo D1, D2, D9).
- [ ] Change budget declarado antes de cada slice.
- [ ] Migración `V8` nueva y justificada; `V1..V7` intactas.
- [ ] Máquina de estados de credencial y agregación de perfil implementadas y cubiertas por tests
      (CA-1..CA-9), incluidas TODAS las filas de RF-14.
- [ ] `upload`/`submit` con la semántica de §8 (CA-10..CA-16).
- [ ] Inmutabilidad de evidencia (CA-17..CA-22).
- [ ] Seguridad y códigos HTTP (CA-23..CA-28).
- [ ] Concurrencia atómica con 409 (CA-29) y auditoría append-only (CA-30..CA-32).
- [ ] Publicación separada de verificación (CA-33..CA-36).
- [ ] `mvn -B clean test` y `npm run build` en verde por slice; specs E2E por slice.
- [ ] Sin dependencias nuevas ni servicios externos.
- [ ] Docs actualizados: este spec, la doc de la API de profesores y, si D1/D9 cambian la
      visibilidad, también `docs/teacher-search.md` y `docs/messaging.md`.

## 13. DECISIONES (RESUELTAS 2026-09-30)

| # | Resolución |
|---|---|
| D1 | **(b)** — Publicar todo anuncio `PUBLISHED` con etiqueta; agregar filtro "Solo verificados" a la búsqueda. |
| D2 | **(a)** — `verification_status` como única fuente de verdad; se elimina `is_verified` y se expone `isVerified` derivado (`status == VERIFIED`) en el DTO. |
| D3 | **(a)** — Documentar la guía interna de fuentes admisibles (sin integraciones). |
| D4 | Verificación **solo académica**; la completitud es informativa y separada. |
| D5 | PostgreSQL **`bytea`** detrás de una interfaz `DocumentStorage`. |
| D6 | **Sin purga** automática ni retención por tiempo en el MVP. |
| D7 | Fuera del MVP (materias verificadas). |
| D8 | Fuera del MVP (moderación previa). |
| D9 | **(a)** — Publicar disponibilidad no exige `VERIFIED`; se separa publicación de verificación. |
| D10 | Estado del perfil **persistido**, recalculado en la misma transacción; con I12 verificable. |
| D11 | Sin límite de reenvíos en el MVP. |

### Detalle del análisis (por qué se eligió)

Cada una con: problema · opciones · recomendación · impacto · qué debe decidir el director.

- **D1 — Visibilidad y contacto de profesores no verificados.**
  *Problema:* la decisión de producto dice que un no verificado "puede existir y usar ClaseYa"
  mostrándose como "No verificado", pero hoy **cinco** lugares exigen `VERIFIED` (§2.1) y los tests lo
  fijan. *Opciones:* (a) mantener todo solo-`VERIFIED` y que "No verificado" se vea únicamente por
  link directo; (b) publicar todo anuncio `PUBLISHED` con su etiqueta y agregar un filtro
  "Solo verificados" a la búsqueda. *Recomiendo (b)*, con la barrera pública pasando a ser
  `user.status = ACTIVE` + anuncio `PUBLISHED`. *Impacto:* búsqueda, detalle, favoritos, contacto y
  **publicación de disponibilidad**; hay que actualizar los tests de §2.5 y los docs de búsqueda y
  mensajería. *Debe decidir:* (b) y el nombre del filtro.
- **D2 — `verification_status` como única fuente de verdad por credencial.**
  *Problema:* hoy `teacher_education.is_verified` (boolean, `V1`) convive con el estado del perfil, y
  el spec agregaría un tercer estado. *Opciones:* (a) eliminar `is_verified` y exponer `isVerified`
  **derivado** (`status == VERIFIED`) en el DTO; (b) conservar ambos. *Recomiendo (a)*: una sola
  fuente persistida. *Impacto:* `V8` migra el valor; el DTO no cambia para el frontend.
  *Debe decidir:* confirmar (a).
- **D3 — Fuentes oficiales admisibles por tipo de título.**
  *Problema:* el MVP es 100% humano; sin guía, cada admin decide distinto. *Opciones:* (a) documento
  interno de guía (categorías: consulta a la institución emisora, documentos digitales con
  código/QR verificable que emiten algunas universidades, procesos oficiales de legalización para
  títulos del exterior, matrículas profesionales), sin integraciones; (b) dejar a criterio del
  revisor. *Recomiendo (a)* como **guía**, con el método registrado por decisión (RF-15 del modelo de
  métodos). *Impacto:* ninguno técnico; sí de calidad de revisión. *Debe decidir:* aprobar la guía y
  quién la mantiene.
- **D4 — Requisitos para obtener `VERIFIED`.** *Recomendación firme:* la verificación es **solo
  académica**; no se exige bio, precio, foto, disponibilidad ni cantidad de materias. La completitud
  (RF-2) es informativa y separada. *Debe decidir:* confirmarlo (evita que un implementador mezcle
  ambas cosas).
- **D5 — Almacenamiento de binarios.** *Recomiendo:* `bytea` en PostgreSQL detrás de una interfaz
  (`DocumentStorage`) con implementación PostgreSQL, para poder migrar a object storage privado sin
  cambiar el contrato. Límite 10 MB hace que `bytea` sea viable. *Debe decidir:* confirmar (a) o
  autorizar un servicio externo (hoy no hay ninguno en el proyecto).
- **D6 — Retención y eliminación.** *Problema:* no hay política definida y esto toca datos
  personales. *Recomiendo:* **no** implementar purga ni retención automática en el MVP; conservar
  documentos mientras exista la cuenta y permitir el borrado **manual** solo de lo no-evidenciado
  (RF-21). *Debe decidir:* el director fija la política (plazo, baja de cuenta, pedido de borrado del
  profesor). Queda documentado, no implementado.
- **D7 — "Materias verificadas".** Fuera del MVP: la verificación es de la **credencial**, no de cada
  materia. *Debe decidir:* confirmar que queda para una fase posterior.
- **D8 — Moderación previa del anuncio.** Fuera del MVP: la verificación académica no modera el
  anuncio. *Debe decidir:* confirmar (el §12 del documento de producto deja "aprobación previa vs
  reportes" abierto, pero no es esta spec).
- **D9 — ¿Qué exige hoy `VERIFIED` y qué debería exigir?** *(nueva, derivada de §2.1)*
  *Problema:* `AvailabilityService` exige `VERIFIED` para **publicar disponibilidad** (403), lo que
  hace circular el circuito: no puedo armar mi anuncio si no estoy verificado, y para verificarme
  necesito armar mi anuncio. *Opciones:* (a) sacar esa guarda y exigir solo "profesor con perfil
  activo" para publicar disponibilidad; (b) dejar la guarda y permitir presentar credenciales sin
  disponibilidad. *Recomiendo (a)*, coherente con separar publicación de verificación.
  *Impacto:* `AvailabilityService`, sus tests y el flujo de alta de un profesor nuevo.
  *Debe decidir:* (a) o (b) — sin esto, el slice A no se puede completar.
- **D10 — ¿El estado del perfil se persiste o se deriva?** *Recomiendo:* persistirlo en
  `teacher_profiles.verification_status` **recalculado en la misma transacción** de cada transición de
  credencial, porque (i) la búsqueda y el filtro "solo verificados" lo consultan y (ii) la UI pública
  lo muestra. Con I12 como invariante verificable. *Alternativa:* derivarlo con una consulta agregada
  (sin segunda escritura, pero con más costo en el listado). *Debe decidir:* confirmar la persistencia
  consistente.
- **D11 — ¿Hay límite de reenvíos por credencial?** *Problema:* sin límite, un profesor puede
  reenviar indefinidamente una credencial rechazada. *Recomiendo:* **sin límite en el MVP** (el admin
  puede pedir más información o rechazar; el abuso de cuenta se maneja con `user.status`), y
  documentar el rate limiting como fase futura (coherente con producto §7). *Debe decidir:*
  confirmarlo o fijar un tope.

## 14. Slices de entrega sugeridos (chained)

| Slice | Alcance | Depende de |
|---|---|---|
| **A — Mi anuncio** | Área del profesor con perfil, materias, modalidades, formación y disponibilidad + completitud informativa + navbar + previsualización. | **D9** (si no se resuelve, la disponibilidad da 403 y el slice queda cojo) |
| **B — Circuito de verificación** | Estados de credencial + agregación del perfil + documentos privados + `upload`/`submit` + cola y decisiones del admin + auditoría + inmutabilidad de evidencia + concurrencia. | D2, D5, D10 (migración `V8`); D3/D6/D11 para los detalles |
| **C — Transparencia pública** | Badge "Verificado" vs "No verificado", "Credencial verificada" vs "Título declarado", y filtro "Solo verificados" si D1 se aprueba. | **D1** (y arrastra D9) |

Cada slice entra con tests de integración y su spec E2E, y se revisa antes de empezar el siguiente.
