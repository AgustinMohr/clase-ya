### E2E Tests: Tema

**Suite ID:** `THEME-E2E`
**Feature:** Modo claro/oscuro y elementos nativos (scrollbar, controles de formulario).

---

## Test Case: `THEME-E2E-001` - El esquema de color sigue al modo

**Priority:** `high`

**Tags:**
- type → @e2e
- feature → @theme

**Description/Objective:** El `color-scheme` del documento debe reflejar el modo activo, para que
los elementos nativos (la scrollbar, los controles del navegador) se rendericen oscuros en modo
oscuro y claros en modo claro. Sin `color-scheme`, la scrollbar quedaba clara aún en modo oscuro.

**Preconditions:**
- Home cargado; preferencia del sistema fijada en claro para que el estado inicial sea determinista.

### Flow Steps:
1. Abrir la home.
2. Leer `getComputedStyle(document.documentElement).colorScheme`.
3. Alternar a modo oscuro.
4. Alternar a modo claro.

### Expected Result:
- `color-scheme` es `light` → `dark` → `light`.

### Key verification points:
- El valor computado del `<html>` cambia con el tema.
- Los botones de alternancia ("Activar modo oscuro" / "Activar modo claro") existen.
