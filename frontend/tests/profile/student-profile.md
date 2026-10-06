### E2E Tests: Perfil de estudiante

**Suite ID:** `PROFILE-E2E`
**Feature:** Formulario "Mi perfil" (universidad → facultad → carrera → año).

---

## Test Case: `PROFILE-E2E-001` - Guarda la cascada y la conserva al reabrir

**Priority:** `critical`

**Tags:**
- type → @e2e
- feature → @profile

**Description/Objective:** Cubre la regresión reportada: al guardar una carrera y una facultad,
reabrir el formulario las mostraba vacías. La causa era que los cargadores de catálogo dependían
solo del valor seleccionado: al reabrir con la misma universidad nunca volvían a ejecutarse.

**Preconditions:**
- Sesión iniciada como `student@claseya.dev`, que ya tiene perfil (la línea "Guardado:" es visible).

### Flow Steps:
1. Abrir **Mi perfil** desde el navbar y leer los valores actuales (`currentValues`).
2. Cambiar de universidad; elegir la primera facultad y la primera carrera de la nueva universidad.
3. Guardar y reabrir.
4. Restaurar los valores originales y guardar.

### Expected Result:
- Al reabrir, universidad, facultad y carrera muestran lo guardado.
- La base demo queda como estaba.

### Key verification points:
- `faculty` y `career` no vacíos en la primera apertura (prellenado).
- Tras reabrir: los tres `<select>` tienen el valor guardado.

### Notes:
- El cambio abarca las tres selects a propósito: en el catálogo demo la UTN tiene una sola
  facultad y una sola carrera, así que un cambio real tiene que empezar por la universidad.

---

## Test Case: `PROFILE-E2E-002` - El campo "Sobre vos" respeta el límite del backend

**Priority:** `high`

**Tags:**
- type → @e2e
- feature → @profile

**Description/Objective:** El textarea "Sobre vos" debe limitar la entrada al máximo que acepta la
API (1000 caracteres, `Create/UpdateStudentProfileRequest`). Sin el `maxLength`, un texto más largo
volvía como 400 al guardar.

**Preconditions:**
- Sesión iniciada como `student@claseya.dev` (con perfil).

### Flow Steps:
1. Abrir **Mi perfil** desde el navbar.

### Expected Result:
- El textarea "Sobre vos" tiene `maxlength="1000"`.

### Key verification points:
- `dialog.bio` expone el atributo `maxlength` = `1000`.
