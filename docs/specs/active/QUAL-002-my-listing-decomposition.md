# QUAL-002 — Descomposición de `MyListingPage`

**ID:** QUAL-002
**Estado:** Aprobada — implementada (2026-10-28)
**Origen:** hallazgo `QUAL-02` de `review.md`.

---

## 1. Objetivo

`MyListingPage` era un archivo de **698 líneas** que mezclaba carga inicial, formulario de perfil,
cascada académica, materias, modalidades, formación, disponibilidad, verificación, confirmaciones y
toasts. Se divide en hooks + secciones sin cambiar comportamiento.

## 2. Estructura

| Pieza | Responsabilidad |
|---|---|
| `pages/MyListingPage.tsx` (127 líneas) | orquesta: metadatos, header, loading y composición |
| `hooks/useTeacherListing.ts` | carga de los 7 recursos, estado, perfil y todas las mutaciones |
| `hooks/useAcademicCascade.ts` | universidad → unidad → carrera → materia |
| `components/teacher/listing/AnnouncementStatusCard.tsx` | completitud del anuncio |
| `.../ProfileSection.tsx` | formulario de perfil |
| `.../SubjectsSection.tsx` | materias + cascada |
| `.../ModalitiesSection.tsx` | modalidades |
| `.../EducationSection.tsx` | formación + confirmación de borrado |
| `.../AvailabilitySection.tsx` | disponibilidad semanal |
| `lib/teacherProfile.ts` | `toProfileInput` puro (elimina el mapeo duplicado) |

## 3. Decisiones

- **D1 — Sin cambios de comportamiento.** El JSX de cada sección conserva clases, labels,
  `data-testid` y textos; los E2E existentes siguen siendo el contrato.
- **D2 — Props explícitas.** Las secciones no leen contexto global; el page pasa estado y callbacks.
- **D3 — El diálogo de confirmación de formación vive en `EducationSection`.** Es la única acción
  irreversible de esa sección.
- **D4 — `addSubject` devuelve `boolean`.** La selección del cascade vive en `SubjectsSection`; la
  sección limpia la materia elegida solo si el alta tuvo éxito.
- **D5 — Mapeo puro testeado.** `toProfileInput` se cubre en Vitest con perfil completo, nulos y
  ausencia de perfil.

## 4. Verificación

- `npm test` → **24/24** (5 archivos; incluye el mapeo nuevo).
- `npm run lint` y `tsc --noEmit` → limpios.
- `npm run build` → OK.
- E2E → **40/40** en base limpia, incluidos los dos escenarios de acciones destructivas de
  `my-listing`.

## 5. Fuera de alcance

Dividir `useTeacherListing` por dominio (perfil/materias/formación/disponibilidad), tests de
componentes de las secciones, cambios de API o de diseño visual.

## 6. Change budget

- **Scope:** descomposición del page en hooks, secciones y un mapper puro; docs.
- **Archivos:** `pages/MyListingPage.tsx`, `hooks/useTeacherListing.ts`,
  `hooks/useAcademicCascade.ts`, `components/teacher/listing/*`, `lib/teacherProfile.ts` (+ test),
  `review.md`.
- **Migraciones:** ninguna.
- **Dependencias:** ninguna.

## 7. Definition of Done

- [x] `MyListingPage` queda como orquestador (127 líneas).
- [x] Carga y mutaciones en un hook; cascada en otro; secciones presentacionales.
- [x] Mapeo de perfil puro y testeado.
- [x] Lint, tsc, tests, build y E2E verdes.
- [x] `review.md` actualizado.
