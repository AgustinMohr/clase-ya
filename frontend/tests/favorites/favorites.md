### E2E Tests: Favoritos

**Suite ID:** `FAV-E2E`
**Feature:** Guardar y quitar profesores como favoritos.

---

## Test Case: `FAV-E2E-001` - Guardar y quitar un favorito se refleja en la lista

**Priority:** `high`

**Tags:**
- type → @e2e
- feature → @favorites

**Description/Objective:** El estado del corazón de una tarjeta y la página "Mis favoritos" deben
mantenerse coherentes en ambos sentidos.

**Preconditions:**
- Sesión iniciada como `student@claseya.dev` (tiene perfil de estudiante: sin él la API responde 409).
- Al menos un profesor de la landing sin guardar.

### Flow Steps:
1. Elegir la primera tarjeta cuyo botón diga "Guardar a … en favoritos".
2. Guardarlo y verificar que el botón pasa a "Quitar a … de favoritos".
3. Abrir **Favoritos** desde el navbar y verificar que la tarjeta está ahí.
4. Quitarlo desde esa página.

### Expected Result:
- La tarjeta aparece en favoritos y desaparece al quitarla.
- La base demo queda como estaba (se agrega y se quita).

### Key verification points:
- `aria-label` del botón cambia de Guardar a Quitar.
- Conteo de tarjetas de ese profesor: 1 → 0.
