# SEARCH-002 — Filtro por ciudad/zona

**ID:** SEARCH-002
**Estado:** En review
**Origen:** auditoría de lanzamiento (2026-11-11): el PRD §3/§5 define búsqueda por materia **y
zona**, pero `GET /api/teachers` no aceptaba ciudad y la UI no ofrecía el filtro.
**Nivel:** 2 (contrato de API + producto).

---

## 1. Objetivo

Permitir que el estudiante filtre profesores por ciudad/zona desde la búsqueda pública, usando el
`city` que el profesor ya publica en su anuncio.

## 2. Contexto

`teacher_profiles.city` existe y se muestra en tarjetas/perfiles. La búsqueda ya filtra por
materia, modalidad, rating, precio y verificados; también soporta geolocalización por lat/lng, pero
no hay UI. La zona es una de las promesas centrales del PRD y el dato ya está disponible.

## 3. Actores

- **Visitante/estudiante:** escribe una ciudad y aplica el filtro.
- **Profesor:** el valor de `city` de su anuncio es el que se compara.
- **Sistema:** normaliza y valida el parámetro.

## 4. Requisitos funcionales

- **RF-1** `GET /api/teachers` acepta `city` (texto, opcional).
- **RF-2** La comparación es exacta, case-insensitive y con `trim` en ambos lados.
- **RF-3** `city` vacío o solo espacios se ignora (no filtra).
- **RF-4** `city` de más de 100 caracteres responde `400`.
- **RF-5** La UI de búsqueda agrega un campo “Zona” y lo serializa en la URL como `ciudad`.
- **RF-6** El filtro se combina con materia, modalidad, rating, precio y verificados.
- **RF-7** Limpiar filtros borra también la zona.
- **RF-8** La paginación conserva el filtro de zona.

## 5. Requisitos no funcionales

- Sin N+1: la comparación vive en la misma `Specification` de la búsqueda.
- Sin migración: `teacher_profiles.city` ya existe.
- Sin índice nuevo en esta fase; el catálogo actual es chico. Medir con `EXPLAIN` si crece.

## 6. Seguridad

- El filtro es público y de solo lectura; no revela coordenadas ni dirección.
- No acepta columnas ni SQL del cliente; es un valor de texto parametrizado.
- `city` no se refleja en logs con datos personales.

## 7. Invariantes

- Un profesor sin `city` no aparece cuando se aplica una zona no vacía.
- La comparación no distingue mayúsculas/minúsculas ni espacios externos.
- El filtro no altera la regla de visibilidad (`ACTIVE` + anuncio publicado).

## 8. API contract

`GET /api/teachers?city=Santa%20Fe&page=0&size=24`

- `city` opcional; se ignora si está vacío.
- `400` si supera 100 caracteres.
- La respuesta mantiene el shape `SearchResultPage<TeacherSummaryResponse>`.

## 9. Datos afectados

`teacher_profiles.city` (lectura). Sin migración, sin tabla ni índice nuevos.

## 10. Criterios de aceptación

- **CA-1** Dos profesores en ciudades distintas: `?city=Santa%20Fe` devuelve solo el de Santa Fe.
- **CA-2** `?city=%20santa%20fe%20` devuelve el mismo resultado (trim + case-insensitive).
- **CA-3** `?city=` no filtra y devuelve todos los visibles.
- **CA-4** Un profesor sin ciudad no aparece al filtrar por una ciudad.
- **CA-5** `?city=` + 101 caracteres responde `400`.
- **CA-6** La UI escribe `ciudad` en la URL, lo conserva al paginar y lo borra con “Limpiar”.

## 11. Out of scope

- Normalización de variantes (“Sta. Fe”, “SF”) o autocompletado de ciudades.
- Búsqueda por barrio separada de `city`.
- Radio/geolocalización en la UI (la API ya tiene lat/lng; se tratará en otra spec).
- Cambiar el perfil del profesor para elegir ciudad desde un catálogo.

## 12. Definition of Done

- [ ] Backend + frontend + tests.
- [ ] `mvn -B test` verde en CI.
- [ ] `npm run build`, `npm test` y `npm run lint` verdes.
- [ ] Flujo manual: buscar materia + zona → paginar → limpiar.
