# QUAL-001 — TypeScript estricto y lint del frontend

**ID:** QUAL-001
**Estado:** Aprobada — en implementación (2026-10-28)
**Origen:** hallazgo `QUAL-01` de `review.md`.

---

## 1. Objetivo

El frontend usaba TypeScript con `strict: false`, `noUnusedLocals: false` y
`noUnusedParameters: false`, y no tenía lint. Eso anulaba las garantías que justifican el
compilador y dejaba pasar hooks mal usados, imports muertos y `any` explícitos.

## 2. Decisiones

- **D1 — `strict: true`.** El código ya compilaba limpio con `--strict`; se activa completo (no
  incremental) junto con `noUnusedLocals` y `noUnusedParameters`.
- **D2 — `any` tipado.** `api.ts` deja de usar `let parsed: any`; se define `ApiErrorBody` con la
  forma real de `ApiError` y se usa en el cliente y en `RegisterPage`.
- **D3 — ESLint 10 flat config.** `eslint.config.js` con `@eslint/js`, `typescript-eslint`,
  `eslint-plugin-react-hooks` y `globals`. Reglas clásicas de hooks (`rules-of-hooks`,
  `exhaustive-deps`) activas.
- **D4 — Reglas nuevas del plugin de hooks desactivadas con motivo.** `react-hooks/refs` y
  `react-hooks/set-state-in-effect` (v7, alineadas al React Compiler) marcan el patrón actual de
  carga en efectos y refs de inicialización. Migrar a ese modelo es un cambio aparte; se dejan
  apagadas y documentadas.
- **D5 — Sin Prettier en este cambio.** Un formateador sobre 4.7k LOC produce un diff masivo y no
  aporta al hallazgo; queda como decisión separada.
- **D6 — Gate en CI.** `npm run lint` se suma al job de frontend, junto a `npm ci`, `npm run build`
  y `npm audit`.

## 3. Verificación

- `npx tsc --noEmit` con `strict` → 0 errores.
- `npm run lint` → 0 errores.
- `npm run build` → OK (incluye el chequeo de CSP).
- E2E 40/40 (sin cambios de comportamiento).

## 4. Fuera de alcance

Prettier, reglas type-aware de `typescript-eslint`, migración al React Compiler, tests unitarios de
componentes (`TEST-01`), división de `MyListingPage` (`QUAL-02`).

## 5. Change budget

- **Scope:** `tsconfig`, `api.ts`/`RegisterPage` (tipos), configuración de ESLint, scripts,
  `package.json`/lockfile, CI y docs.
- **Archivos:** `frontend/tsconfig.json`, `frontend/eslint.config.js`, `frontend/package.json`,
  `frontend/package-lock.json`, `frontend/src/api.ts`,
  `frontend/src/pages/RegisterPage.tsx`, `frontend/src/pages/FavoritesPage.tsx`,
  `frontend/src/App.tsx`, `frontend/src/components/StudentProfileModal.tsx`,
  `.github/workflows/ci.yml`, `README.md`, `review.md`.
- **Migraciones:** ninguna.
- **Dependencias:** `eslint`, `@eslint/js`, `typescript-eslint`, `eslint-plugin-react-hooks`,
  `globals` (dev). Motivo: lint real en el gate de CI.

## 6. Definition of Done

- [x] `strict`, `noUnusedLocals` y `noUnusedParameters` activos.
- [x] `ApiErrorBody` reemplaza el `any`.
- [x] ESLint configurado; `npm run lint` verde y en CI.
- [x] Build y E2E verdes.
- [x] Docs y `review.md` actualizados.
