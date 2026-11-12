# PROFILE-003 — Onboarding mínimo del estudiante

**ID:** PROFILE-003
**Estado:** En review
**Origen:** auditoría de lanzamiento (2026-11-11): el perfil de estudiante exigía
universidad/facultad/carrera/año. Un alumno de primaria, secundaria o un adulto no podía contactar
sin inventar datos académicos, y el PRD §6 pide un perfil mínimo (nombre visible; ciudad opcional).
**Nivel:** 2 (producto + contrato de API + schema).

---

## 1. Objetivo

Permitir que cualquier estudiante cree un perfil usable con **nombre + nivel** (y ciudad opcional),
sin universidad ni carrera. Los datos académicos quedan como un bloque opcional para quien curse
una carrera.

## 2. Contexto

`student_profiles.university_id`, `career_id` y `current_year` eran `NOT NULL`; la UI exigía la
cascada universidad → facultad → carrera → año. El backend respondía `409` al contactar sin perfil,
así que el bloqueo era total para el público objetivo del PRD (primaria, secundaria, terciario,
universidad y adultos).

El nombre visible ya se resolvió en PROFILE-002 (`users.name`). Falta el nivel y la ciudad, y hacer
opcional el bloque académico.

## 3. Actores

- **Estudiante:** completa nombre, nivel y ciudad; opcionalmente carrera.
- **Padre/madre/tutor:** opera la cuenta del menor; elige el nivel del estudiante.
- **Docente:** no ve datos académicos del estudiante; solo el nombre en la conversación.
- **Sistema:** valida que el bloque académico esté completo o vacío, nunca a medias.

## 4. Requisitos funcionales

- **RF-1** `student_profiles.university_id`, `career_id` y `current_year` pasan a nullable.
- **RF-2** El perfil agrega `level` (`PRIMARIA`, `SECUNDARIA`, `TERCIARIO`, `UNIVERSIDAD`,
  `ADULTO`) y `city` (opcional, máx. 100).
- **RF-3** Crear/actualizar perfil exige `name` y `level`; universidad, carrera y año son opcionales.
- **RF-4** Si se envía `universityId` o `careerId`, deben venir juntos; si no, `400`.
- **RF-5** `currentYear` solo se acepta cuando vienen universidad y carrera; si no, `400`.
- **RF-6** Un perfil mínimo (sin datos académicos) permite contactar y guardar favoritos.
- **RF-7** Editar un perfil puede limpiar el bloque académico enviándolo vacío.
- **RF-8** La SPA pide nombre y nivel; muestra el bloque académico como “opcional”.
- **RF-9** La SPA pide completar el perfil si falta nombre o nivel, aunque exista un perfil viejo.
- **RF-10** La migración conserva los datos académicos existentes y asigna `UNIVERSIDAD` a los
  perfiles que ya tenían universidad y carrera.

## 5. Requisitos no funcionales

- Migración Flyway nueva (`V19`), sin editar históricas.
- Sin N+1: el perfil se resuelve con las asociaciones ya cargadas.
- `level`/`city` son columnas simples; índice por `level` para futuras consultas administrativas.
- Sin dependencias nuevas.

## 6. Seguridad

- El perfil de estudiante sigue siendo privado: solo el dueño y ADMIN lo leen.
- `city` es opcional y no se expone en perfiles públicos de profesores.
- `level` no se expone a docentes ni en búsquedas públicas.
- La identidad se resuelve desde `CurrentUser`; nunca desde un `userId` del cliente.

## 7. Invariantes

- Un perfil no puede tener universidad sin carrera ni carrera sin universidad.
- Un perfil no puede tener `currentYear` sin el par académico.
- Un perfil usable tiene `users.name` no vacío y `level` no nulo.
- Un perfil sin datos académicos no inventa universidad/carrera.
- La migración no borra ni reescribe universidad/carrera/año existentes.

## 8. API contract

### Crear/actualizar

`POST /api/students/profile` y `PUT /api/students/me`:

```json
{
  "name": "Ana Estudiante",
  "level": "SECUNDARIA",
  "city": "Santa Fe",
  "bio": "Opcional"
}
```

Académico opcional:

```json
{
  "name": "Ana Estudiante",
  "level": "UNIVERSIDAD",
  "universityId": "<uuid>",
  "careerId": "<uuid>",
  "currentYear": 2
}
```

- `level` obligatorio; `name` opcional para clientes viejos (la SPA lo exige).
- `universityId` + `careerId` juntos o ninguno: `400` si se envía uno solo.
- `currentYear` sin par académico: `400`.
- `city` máx. 100; `bio` máx. 1000.

### Respuesta

`StudentProfileResponse` agrega `level` y `city`; `universityId`, `universityName`,
`academicUnitId`, `careerId`, `careerName` y `currentYear` pueden ser `null`.

## 9. Datos afectados

Migración `V19__minimal_student_onboarding.sql`:

- `student_profiles.university_id`, `career_id`, `current_year` → nullable.
- `student_profiles.level` (`varchar(20)` + CHECK) y `city` (`varchar(100)`).
- Backfill `level = 'UNIVERSIDAD'` para filas con universidad y carrera.
- Índice `idx_student_profiles_level`.

Sin tablas nuevas.

## 10. Criterios de aceptación

- **CA-1** Perfil con nombre + `SECUNDARIA` + ciudad, sin universidad, responde `201` y permite
  contactar.
- **CA-2** Respuesta y `GET /api/students/me` devuelven `level`/`city`; los campos académicos
  ausentes son `null`.
- **CA-3** `universityId` sin `careerId` responde `400`.
- **CA-4** `currentYear` sin par académico responde `400`.
- **CA-5** Un perfil con universidad/carrera sigue funcionando y conserva el año.
- **CA-6** Editar a un perfil mínimo limpia universidad/carrera/año.
- **CA-7** La SPA no envía el formulario sin nombre o nivel.
- **CA-8** Un perfil legacy sin `level` dispara el modal de perfil antes de contactar.
- **CA-9** `V19` aplica sobre una base con perfiles académicos sin perder datos.

## 11. Out of scope

- Desacoplar las materias del profesor del catálogo de carreras (spec siguiente).
- Búsqueda de profesores por nivel del estudiante.
- Validar edad o consentimiento parental (spec de seguridad/legal).
- Cambiar el catálogo académico.

## 12. Definition of Done

- [ ] Migración `V19` + entidad + DTOs + servicio + UI.
- [ ] Tests de integración para perfil mínimo, par académico parcial y actualización.
- [ ] `npm run build`, `npm test`, `npm run lint` verdes.
- [ ] `mvn -B test` verde en CI con Testcontainers.
- [ ] Docs de base de datos y spec actualizados.
