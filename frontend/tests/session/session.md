### E2E Tests: Sesión y navegación

**Suite ID:** `SESSION-E2E`
**Feature:** Cierre de sesión y posición del scroll al navegar.

---

## Test Case: `SESSION-E2E-001` - Cerrar sesión desde Mensajes vuelve a la landing

**Priority:** `high`

**Tags:**
- type → @e2e
- feature → @session

**Description/Objective:** Al cerrar sesión estando en una vista privada, la vista debe volver a la
landing (regresión reportada: las conversaciones seguían visibles tras salir).

**Preconditions:**
- Sesión iniciada como estudiante.

### Flow Steps:
1. Abrir **Mensajes**.
2. Presionar **Salir**.

### Expected Result:
- Se ve la landing y la vista de Mensajes ya no existe.

### Key verification points:
- Heading de la landing visible.
- Heading "Mensajes" con conteo 0.

---

## Test Case: `SESSION-E2E-002` - Abrir un perfil desde una lista scrolleada deja el scroll arriba

**Priority:** `medium`

**Tags:**
- type → @e2e
- feature → @session

**Description/Objective:** Cada cambio de vista arranca en el tope (regresión: se aterrizaba en el
medio del perfil al venir de una lista larga).

**Preconditions:**
- Catálogo completo listado (la lista debe permitir scroll).

### Flow Steps:
1. Bajar hasta el final de la lista con `window.scrollTo`.
2. Abrir el perfil de la última tarjeta.

### Expected Result:
- El perfil se muestra con `window.scrollY === 0`.

### Key verification points:
- `scrollY > 0` antes de abrir; `scrollY === 0` después.
