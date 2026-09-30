# SEARCH-001 — Búsqueda de profesores: paginación y filtro de precio

**ID:** SEARCH-001
**Estado:** Activa — **aprobada en REVIEW (2026-09-30)**, en implementación
**Carpeta:** `docs/specs/active/`
**Nivel de cambio:** 2 (modifica el contrato de la API pública de búsqueda)

---

## 1. Objetivo

Que el buscador de profesores sea **navegable** (todo el catálogo, no solo la primera página) y
**filtrable por precio en el servidor**, de forma que el directorio sea explorable completo y los
filtros no devuelvan resultados engañosos. 

## 2. Contexto

Lo que existe hoy (verificado en código):

- `GET /api/teachers` ya soporta `page`, `size` y `sort` con whitelist (`TeacherSort`: `rating`,
  `ratingDesc`, `ratingAsc`, `name`, `distance`), valida `size` entre 1 y 50 y `page >= 0`
  (`TeacherSearchService:127-162`), y devuelve `SearchResultPage{content,page,size,totalElements,totalPages}`.
- El orden es estable: termina en `id` como desempate (`TeacherSort.toSort():31-44`), así que la
  paginación es determinista.
- El `count` es exacto: los filtros sobre relaciones se escriben como `EXISTS`, sin `DISTINCT`
  (`TeacherSpecifications:20-26`).
- Visibilidad pública: `verification_status = VERIFIED` **y** `users.status = ACTIVE`
  (`TeacherSpecifications.visible():33-37`).
- `teacher_profiles.price_per_hour` es `numeric(10,2)` **nullable** (migración `V7`).

Lo que falta:

- El frontend **no pagina**: `SearchPage.tsx:60-64` pide siempre `page 0`, `size 24`, y
  `SearchPage.tsx:218-219` imprime "Mostrando X de Y" donde X es el tamaño de la página.
- El filtro de precio es **client-side** sobre esos 24 resultados (`SearchPage.tsx:98-101`) y lo
  declara en pantalla (`SearchPage.tsx:176`). El backend no tiene parámetro de precio.
- Con datos de desarrollo hay **54 profesores públicos** → hoy 30 son inalcanzables.

## 3. Actores

| Actor | Qué hace |
|---|---|
| Visitante / Estudiante / Padre-tutor (puede no tener cuenta) | Busca por materia, filtra por modalidad, calificación y precio, y recorre páginas. |
| Profesor | Publica (o no) su precio por hora. |
| Sistema | Aplica visibilidad, filtros, orden y paginación; nunca expone profesores ocultos. |

## 4. Requisitos funcionales

- **RF-1** — `GET /api/teachers` acepta `minPrice` y `maxPrice` (decimales, ARS por hora),
  opcionales e independientes entre sí.
- **RF-2** — Con al menos un límite de precio presente, solo se devuelven profesores con
  `price_per_hour` **no nulo** y dentro del rango (ver D1 en §12).
- **RF-3** — `minPrice > maxPrice` (ambos presentes) → **400**; cualquier valor negativo → **400**;
  cualquier valor mayor a **30.000** → **400**.
- **RF-4** — El orden por defecto sigue siendo `rating` (ratingAverage desc, ratingCount desc, id
  asc) y es **estable entre páginas**.
- **RF-5** — Una `page` fuera de rango devuelve `content: []` con `totalElements`/`totalPages`
  correctos; nunca 404 ni 500.
- **RF-6** — `totalPages = ceil(totalElements / size)`, donde `size` es el tamaño de página solicitado.
- **RF-7** — El frontend muestra navegación real: cantidad total de resultados (`totalElements`) y
  controles de página anterior/siguiente con "Página X de Y".
- **RF-8** — Cambiar el término de búsqueda o **cualquier** filtro resetea `page` a 0.
- **RF-9** — El filtro de precio del frontend se envía al servidor y **se elimina el filtrado local**.
- **RF-10** — El control de precio del frontend es una **barra arrastrable de dos manijas** (mínimo y
  máximo) acompañada por **dos campos de texto editables** y sincronizados (sincronización
  **solo visual**: nunca dispara consultas):
  - arrastrar una manija actualiza su campo de texto **en tiempo real**;
  - escribir un número en un campo mueve la manija correspondiente (valor acotado al rango);
  - el extremo superior de la barra significa **"sin tope"** y en ese estado **no se envía `maxPrice`**.
- **RF-12** — **Nada se aplica en tiempo real**: mientras se editan los filtros no se dispara ninguna
  consulta. Se consulta solo con una acción explícita del usuario: enviar el buscador, pulsar
  **Aplicar filtros**, cambiar de página o limpiar los filtros. En todos esos casos `page` vuelve a 0 (RF-8).
- **RF-13** — El panel de filtros tiene un botón **Aplicar filtros**, debajo de los controles, que envía
  de una sola vez todos los filtros seleccionados e indica cuántos hay seleccionados.
- **RF-11** — La forma de la respuesta **no cambia**: mismo `SearchResultPage` y mismos campos de
  `TeacherSummaryResponse`.

## 5. Requisitos no funcionales

- **Sin N+1:** se conserva el ensamblado batcheado de `TeacherSummaryAssembler` (3 consultas por página).
- **Count exacto:** el filtro de precio es una comparación de columna sobre `teacher_profiles`, no una
  subconsulta; no se introduce `DISTINCT` ni duplicación por joins.
- **Sin migración y sin índices nuevos.** No hay evidencia de necesidad con el tamaño actual del
  catálogo; el baseline `visible()` ya acota por estado. Se reevalúa con datos de producción (§10).
- **Paginación por `page`/`size`**, con `size = 24` en el frontend (≤ `MAX_SIZE` 50). Se descarta el
  scroll infinito.
- **Compatibilidad hacia atrás:** los parámetros nuevos son opcionales; ningún cliente existente se rompe.

## 6. Seguridad

- El endpoint sigue siendo público (`permitAll`) y solo filtra por un dato ya público (precio).
- `TeacherSpecifications.visible()` se aplica **siempre** y se combina con `AND`: ningún filtro puede
  exponer profesores ocultos (no `VERIFIED` o usuario no `ACTIVE`).
- Los parámetros nuevos se validan (rango numérico) y se aplican vía Criteria: sin SQL concatenado.
- No se agregan campos a la respuesta, no se toca autenticación, autorización ni ownership.

## 7. Invariantes

- **I1** — Todo resultado cumple `verification_status = VERIFIED` y `users.status = ACTIVE`.
- **I2** — `totalPages = ceil(totalElements / size)`; si `page >= totalPages`, `content` es vacío.
- **I3** — El orden total es determinista (termina en `id`): recorrer todas las páginas no repite ni
  omite filas.
- **I4** — Con `minPrice`/`maxPrice` no aparece ningún profesor con `price_per_hour` nulo ni fuera del rango.
- **I5** — `totalElements` es la cantidad de filas que cumplen **todos** los filtros, sin duplicados.

## 8. API contract

`GET /api/teachers` (público). Parámetros nuevos:

| Parámetro | Tipo | Default | Validación |
|---|---|---|---|
| `minPrice` | decimal ≥ 0 | — | 400 si < 0 o > 1.000.000 |
| `maxPrice` | decimal ≥ 0 | — | 400 si < 0 o > **30.000**; 400 si `minPrice > maxPrice` |

Ejemplo:

```
GET /api/teachers?subjectId=<uuid>&modality=ONLINE&minRating=4&minPrice=4000&maxPrice=9000&page=1&size=24
```

Respuesta 200 (forma sin cambios):

```json
{ "content": [ /* TeacherSummaryResponse */ ], "page": 1, "size": 24, "totalElements": 54, "totalPages": 3 }
```

Errores: **400** con `ApiError` y mensaje explícito, siguiendo el estilo actual
(`"size must be between 1 and 50"`):

- `"minPrice must be 0 or greater"`
- `"maxPrice must be greater than or equal to minPrice"`
- `"maxPrice must be at most 30000"`

## 9. Datos afectados

- Lectura de `teacher_profiles` (`price_per_hour`, `verification_status`) y de las relaciones ya
  usadas por la búsqueda.
- **Sin migración** (no cambia el schema) y **sin índices nuevos** (justificado en §5).

## 10. Criterios de aceptación

| # | Escenario | Verifica |
|---|---|---|
| CA-1 | Con 54 profesores visibles, `size=24` → `totalPages=3` y page 0 trae 24 | RF-6 |
| CA-2 | page 2 trae el remanente y `page=3` trae `content` vacío con totales correctos (200) | RF-5, I2 |
| CA-3 | Recorrer las 3 páginas no repite ids ni omite ninguno | RF-4, I3 |
| CA-4 | `minPrice=4000&maxPrice=9000` → todos dentro del rango y ninguno sin precio | RF-1, RF-2, I4 |
| CA-5 | `minPrice=9000&maxPrice=4000` → 400 | RF-3 |
| CA-6 | `minPrice=-1` → 400 y `maxPrice=30001` → 400 | RF-3 |
| CA-7 | Combinación `subjectId + modality + minRating + minPrice` → solo los que cumplen todo, y `totalElements` coincide con el conteo directo en base | RF-1, I5 |
| CA-8 | Un profesor `REJECTED` y uno `VERIFIED` con usuario `PENDING` no aparecen ni con filtro de precio | I1, §6 |
| CA-9 | Rango de precio imposible → `totalElements=0`, 200 (no 404) | RF-5 |
| CA-10 | Regresión: sin parámetros nuevos, la búsqueda se comporta exactamente como hoy | Compatibilidad |

## 11. Out of scope

- Búsqueda por texto libre o por nombre de profesor (ítem separado del roadmap).
- Exponer `sort` en la UI, orden por precio o destacados.
- Scroll infinito, paginación de favoritos, filtro de distancia desde la UI.
- Migraciones o índices nuevos.
- Obtener los extremos de precio del catálogo desde el backend (facetas): la barra usa un rango fijo
  de 0 a 100.000.
- Cambios en `GET /api/teachers/{id}` o en `TeacherSummaryResponse`.

## 12. Decisiones resueltas (REVIEW 2026-09-30)

- **D1 — Precio nulo con filtro aplicado: EXCLUIR.** Cuando hay al menos un límite de precio, los
  profesores sin `price_per_hour` no se devuelven: el filtro debe ser verificable. Esto cambia el
  comportamiento actual del frontend (`SearchPage.tsx:99` los incluía) y la UI debe dejar de hacerlo.
- **D2 — UI de paginación: PAGER** (anterior/siguiente + "Página X de Y").
- **D3 — Control de precio: barra arrastrable de dos manijas + dos campos de texto sincronizados**
  (detalle en RF-10/RF-12). Se implementa con dos `input type="range"` nativos superpuestos y CSS,
  **sin agregar ninguna dependencia de UI** (regla de `AGENTS.md`).
- **D4 — Tope de `maxPrice`: 30.000** (bajado primero a 100.000 y luego a 30.000 para que un rango
  habitual, como 15.000-20.000, quede cómodo de arrastrar).
- **D5 — El frontend mantiene `size=24`** y el default del backend sigue en 20 (no se toca el default).

Decisión de implementación registrada (no requiere aprobación): la barra usa un rango fijo **0..30.000**
y su manija superior, en el máximo, se interpreta como **"sin tope"** (no se envía `maxPrice`), de modo que
un profesor con un precio mayor al tope siga siendo alcanzable sin filtro. Las manijas nunca se cruzan al
arrastrar, y si un par escrito a mano queda invertido (`Desde` > `Hasta`) se ordena al aplicar los filtros:
la consulta nunca sale inválida. Al tipear, el valor de un campo **nunca** es reescrito por el otro.
`PRICE_MAX` (frontend) y `MAX_PRICE` (backend) deben mantenerse sincronizados; hoy son el único punto de
duplicación de este contrato.

## 13. Definition of Done

- [ ] Spec aprobada por el director (REVIEW).
- [ ] Change budget declarado antes de implementar (scope, archivos, migraciones, dependencias).
- [ ] Backend: parámetros, Specifications de precio y validaciones, sin cambios de forma en la respuesta.
- [ ] Tests de integración que cubren CA-1..CA-10; `mvn -B clean test` en verde.
- [ ] Frontend: pager, filtros server-side, reset de `page`; `npm run build` en verde.
- [ ] Docs actualizados: `docs/teacher-search.md` (contrato de la API) y `comandos.txt` si aplica.
- [ ] Sin migraciones, sin dependencias nuevas, sin cambios de seguridad.
