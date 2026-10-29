# REVIEW2-001 — Catálogo de materias: contrato y paginación

**Estado:** Propuesta (pendiente de REVIEW humano).
**Origen:** `review2.md` DATA-01. **Nivel:** 2 (cambio de contrato de API).

## 1. Objetivo

Dejar de servir el catálogo completo de materias en cada visita y dar a cada consumidor el contrato
que realmente necesita: un puñado de materias para la landing/autocompletado y una lista paginada
para los selectores de "Mi anuncio".

## 2. Contexto

- `GET /api/subjects` ignora `size` y devuelve **todas** las materias activas ordenadas por nombre
  (`SubjectController:33-35`, `SubjectService:28-33`).
- La landing lo pedía **dos veces** por visita (`App` y `LandingPage`). El fetch duplicado ya se
  eliminó con `useCatalog`, pero el payload sigue siendo el catálogo entero.
- El frontend pide `?size=50` (`api.ts:401`) y no recibe 50: recibe todo.
- Índice parcial `subjects(name) WHERE active` agregado en V11.

## 3. Actores

Visitante (landing/autocompletado), TEACHER (selector de materias), ADMIN (catálogo).

## 4. Requisitos funcionales

- **RF-1** `GET /api/subjects/popular?limit=12` devuelve hasta `limit` materias activas **con al
  menos un docente activo**, ordenadas por uso (cantidad de `teacher_subjects` activos agrupados por
  `career_subjects.subject_id`) y, a igualdad, por nombre. `limit` 1..50.
- **RF-2** `GET /api/subjects` acepta `page` (>=0) y `size` (1..100) y devuelve
  `SearchResultPage<SubjectResponse>`; el default es `size=50`.
- **RF-3** `GET /api/subjects?query=` mantiene la búsqueda por nombre, ahora paginada.
- **RF-4** La landing usa `popular`. El autocompletado consulta
  `GET /api/subjects?query=&size=10` (con debounce) y los selectores usan `page/size`; ninguno
  precarga el catálogo completo.
- **RF-5** Un `limit`/`size` fuera de rango devuelve 400 con `ApiError`.

## 5. Requisitos no funcionales

- La landing no debe pedir más de 12 materias ni volver a descargar el catálogo completo.
- `popular` se resuelve en una consulta agregada (`group by subject`, `order by count desc`),
  sin N+1.
- Ambas respuestas llevan `Cache-Control: public, max-age=60`. No se promete `ETag`: la
  invalidación por TTL de 60 s es suficiente y más simple que versionar el catálogo.

## 6. Seguridad

- Endpoints públicos de solo lectura (ya permitidos en `SecurityConfig`).
- No se exponen datos internos: solo `id`, `name`, `slug`/`description` si ya estaban en el DTO.

## 7. Invariantes

- I1: ningún consumidor recibe más de `size`/`limit` elementos.
- I2: `popular` nunca incluye materias inactivas.
- I3: el orden de `GET /api/subjects` sigue siendo estable (`name`, luego `id`).

## 8. API contract

```
GET /api/subjects/popular?limit=12
200 { "content": [ { "id": "...", "name": "Matemática" } ], "page": 0, "size": 12, "totalElements": 12, "totalPages": 1 }

GET /api/subjects?page=0&size=50&query=mate
200 SearchResultPage<SubjectResponse>
400 ApiError si page<0 o size fuera de 1..100
```

> **Cambio incompatible:** `GET /api/subjects` pasa de `Subject[]` a `SearchResultPage`. Hay que
> actualizar `useCatalog` y el autocompletado en el mismo cambio; no se mantienen ambas formas.

## 9. Datos afectados

Sin migración nueva: V11 ya agregó el índice parcial. `popular` usa `teacher_subjects` (índice
existente por `career_subject_id`) y `subjects.active`.

## 10. Criterios de aceptación

- AC-1: `GET /api/subjects` con 3.000 materias y `size=50` devuelve 50 y `totalElements=3000`.
- AC-2: la landing hace una sola request y recibe <= 12 materias.
- AC-3: `popular` ordena por cantidad de profesores y desempata por nombre.
- AC-4: `size=0` y `size=101` → 400.
- AC-5: con 3.000 materias, escribir "álgebra" en el buscador encuentra la materia aunque no esté
  entre las 12 populares (consulta server-side, sin precargar el catálogo).

## 11. Out of scope

- Búsqueda full-text/trigram (REVIEW2-004, propuesta de features).
- Cache en CDN o Redis (infraestructura).
- Cambiar el modelo `Subject`/`CareerSubject`.

## 12. Definition of Done

- [ ] Spec aprobada por el director/arquitecto.
- [ ] Contrato nuevo documentado en `docs/teacher-search.md` o `docs/academic-profiles.md`.
- [ ] Tests de integración de paginación, `popular` y validaciones.
- [ ] Frontend actualizado (landing/popular + selectores paginados) con tests.
- [ ] `mvn clean verify` y `npm test` verdes.
