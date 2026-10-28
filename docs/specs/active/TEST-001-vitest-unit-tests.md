# TEST-001 — Tests unitarios y de componentes del frontend

**ID:** TEST-001
**Estado:** Aprobada — en implementación (2026-10-28)
**Origen:** hallazgo `TEST-01` de `review.md`.

---

## 1. Objetivo

Toda la verificación de UI dependía de Playwright contra el stack completo. Este cambio agrega un
runner rápido para lógica pura y componentes, y deja Playwright para los flujos críticos.

## 2. Decisiones

- **D1 — Vitest + jsdom.** `vitest` con `environment: 'jsdom'`, `setupFiles` y `include` acotado a
  `src/**/*.test.{ts,tsx}`. No levanta backend ni browser.
- **D2 — Testing Library.** `@testing-library/react` + `@testing-library/jest-dom` para componentes;
  `cleanup` explícito en el setup.
- **D3 — Lógica pura en `lib/`.** La validación del registro se extrae a `lib/validation.ts` y se
  testea sin DOM. También se cubren `lib/format.ts` y `routes.ts`.
- **D4 — Playwright no se toca.** Sigue cubriendo login, contacto, favoritos, verificación,
  registro/activación y las rutas. No se duplican esos flujos en Vitest.
- **D5 — Gate en CI.** `npm test` corre en el job de frontend junto a `npm run lint` y
  `npm run build`.

## 3. Tests agregados

| Archivo | Cubre |
|---|---|
| `src/lib/format.test.ts` | precios, rating, iniciales, modalidad, días y franjas |
| `src/lib/validation.test.ts` | email, mínimo 10 + letras/números, confirmación |
| `src/routes.test.ts` | `searchPath` (trim/encoding) e `isPrivatePath` |
| `src/components/ui/Field.test.tsx` | label, error accesible y hint (Testing Library) |

## 4. Fuera de alcance

Umbral de cobertura, tests de `MyListingPage`/páginas completas, MSW, reducir la suite Playwright,
snapshot tests y E2E con datos productivos.

## 5. Change budget

- **Scope:** runner de tests, setup, extracción de validación pura, tests y CI.
- **Archivos:** `frontend/vitest.config.ts`, `frontend/src/test/setup.ts`,
  `frontend/src/lib/validation.ts`, `frontend/src/pages/RegisterPage.tsx`, tests nuevos,
  `frontend/package.json`/lockfile, `.github/workflows/ci.yml`, `README.md`, `review.md`.
- **Migraciones:** ninguna.
- **Dependencias (dev):** `vitest`, `jsdom`, `@testing-library/react`, `@testing-library/dom`,
  `@testing-library/jest-dom`. Motivo: tests rápidos sin stack completo.

## 6. Definition of Done

- [x] `npm test` corre 21 tests en 4 archivos y queda en CI.
- [x] `npm run lint`, `tsc --noEmit` y `npm run build` verdes.
- [x] E2E siguen verdes.
- [x] Docs y `review.md` actualizados.
