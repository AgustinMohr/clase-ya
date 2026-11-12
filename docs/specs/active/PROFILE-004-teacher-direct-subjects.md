# PROFILE-004 — Materias del profesor sin carrera universitaria

**ID:** PROFILE-004
**Estado:** En review
**Origen:** auditoría de lanzamiento (2026-11-11): para publicar una materia el profesor debía
elegir universidad → facultad → carrera. Un profesor de apoyo escolar, primaria, secundaria o un
idioma no encajaba en el catálogo académico.
**Nivel:** 2 (producto + contrato de API + schema).

---

## 1. Objetivo

Permitir que un profesor publique una materia del catálogo (`subjects`) directamente, sin
universidad ni carrera, manteniendo compatibilidad con las materias académicas existentes.

## 2. Contexto

`teacher_subjects.career_subject_id` era `NOT NULL`; la UI solo ofrecía la cascada académica y el
buscador público filtraba únicamente por `CareerSubject`. El seed demo crea materias académicas
(“Matemática I”, “Inglés” dentro de una carrera), pero un tutor escolar no puede publicar “Apoyo
escolar” ni “Inglés básico”.

PROFILE-003 ya desacopló el onboarding del estudiante. Ahora se desacopla la oferta del profesor.

## 3. Actores

- **Profesor:** busca una materia por nombre y la agrega a su anuncio.
- **Estudiante/visitante:** busca por materia y encuentra tanto vínculos académicos como directos.
- **Administración:** mantiene el catálogo `subjects`; no cambia su flujo.
- **Sistema:** garantiza que cada vínculo apunte a exactamente un target.

## 4. Requisitos funcionales

- **RF-1** `teacher_subjects` acepta `career_subject_id` o `subject_id`, nunca ambos ni ninguno.
- **RF-2** Los vínculos existentes con `career_subject_id` siguen funcionando sin migración de datos.
- **RF-3** `POST /api/teachers/me/subjects` acepta `{ "subjectId": "<uuid>" }` además del
  `careerSubjectId` legado.
- **RF-4** Un subject directo debe existir y estar activo; si no, `404`.
- **RF-5** No se puede duplicar el mismo subject directo para el mismo profesor (`409`).
- **RF-6** `DELETE /api/teachers/me/subjects/{teacherSubjectId}` elimina el vínculo por su id,
  independientemente del target.
- **RF-7** La búsqueda pública por `subjectId` encuentra vínculos directos y académicos.
- **RF-8** Un filtro por `careerId`/`universityId` no encuentra vínculos directos (no tienen carrera).
- **RF-9** La landing de materias populares cuenta ambos tipos de vínculo.
- **RF-10** La UI de “Mi anuncio” reemplaza la cascada académica por un buscador de materias.
- **RF-11** El listado propio y las tarjetas públicas muestran la materia directa sin carrera.

## 5. Requisitos no funcionales

- Migración Flyway nueva (`V20`), sin editar históricas.
- Batch fetch con `left join fetch` para evitar N+1 en búsqueda/detalle.
- Sin dependencias nuevas.
- El buscador de materias usa el catálogo paginado existente; no descarga todo.

## 6. Seguridad

- El profesor solo agrega/elimina sus propios vínculos (`CurrentUser`).
- El catálogo de materias es público de lectura; no se exponen datos de otros profesores.
- No se aceptan ids de otra entidad ni SQL arbitrario.
- El 404/409 no revela datos de otros docentes.

## 7. Invariantes

- Cada `teacher_subjects` tiene exactamente un target: `career_subject_id` XOR `subject_id`.
- Un profesor no tiene dos vínculos directos a la misma materia.
- Un vínculo directo no aparece bajo filtros de carrera/universidad.
- Un vínculo activo con subject activo cuenta para la publicación del anuncio y para la búsqueda.
- Los vínculos académicos existentes conservan su `careerSubject` y su semántica.

## 8. API contract

### Agregar materia directa

```http
POST /api/teachers/me/subjects
{ "subjectId": "<uuid>", "description": "Apoyo primaria/secundaria" }
```

`201` con `TeacherSubjectResponse`; `careerSubjectId`/`careerId`/`careerName` nulos.

### Agregar materia académica (legado)

```json
{ "careerSubjectId": "<uuid>" }
```

### Eliminar

```http
DELETE /api/teachers/me/subjects/{teacherSubjectId}
```

`204`; `404` si el vínculo no existe o no pertenece al profesor.

### Respuesta

`TeacherSubjectResponse.subjectId` y `subjectName` están siempre presentes; los campos de carrera
son nulos para un vínculo directo. `TeacherSubjectView` idem para tarjetas/perfil público.

## 9. Datos afectados

Migración `V20__teacher_subjects_direct_subject.sql`:

- `career_subject_id` nullable.
- Nueva `subject_id` FK a `subjects`.
- CHECK “exactamente uno”.
- Índice único parcial `(teacher_id, subject_id) WHERE subject_id IS NOT NULL`.
- Índice parcial `(subject_id) WHERE subject_id IS NOT NULL`.

Sin tablas nuevas ni borrado de datos.

## 10. Criterios de aceptación

- **CA-1** Un profesor agrega `{ subjectId }` y recibe `201` con `subjectId`/`subjectName`.
- **CA-2** El listado propio incluye el vínculo directo sin `careerSubjectId`.
- **CA-3** `subjectId` duplicado responde `409`.
- **CA-4** `subjectId` inexistente responde `404`.
- **CA-5** `careerSubjectId` existente sigue respondiendo `201`.
- **CA-6** `DELETE /{teacherSubjectId}` elimina ambos tipos y `204`.
- **CA-7** `GET /api/teachers?subjectId=` encuentra al profesor directo.
- **CA-8** `?careerId=`/`?universityId=` no lo encuentra.
- **CA-9** `GET /api/subjects/popular` cuenta al profesor directo.
- **CA-10** La UI busca y agrega una materia sin pedir universidad/carrera.
- **CA-11** Las tarjetas y el perfil público muestran la materia directa.

## 11. Out of scope

- Crear materias nuevas desde el perfil del profesor (solo ADMIN mantiene el catálogo).
- Niveles/edad objetivo por materia (spec posterior).
- Búsqueda de profesores por nivel del estudiante.
- Migrar vínculos académicos existentes a vínculos directos.

## 12. Definition of Done

- [ ] Migración `V20` + entidad + repositorio + servicio + DTOs + UI.
- [ ] Tests de integración para alta directa, duplicado, búsqueda y popularidad.
- [ ] `npm run build`, `npm test`, `npm run lint` verdes.
- [ ] `mvn -B test` verde en CI con Testcontainers.
- [ ] Docs de base de datos y spec actualizados.
