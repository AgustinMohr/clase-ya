### E2E Tests: Perfil del profesor y contacto

**Suite ID:** `CONTACT-E2E`
**Feature:** Perfil público, pedido de contacto y gating por rol.

---

## Test Case: `CONTACT-E2E-001` - Contactar crea la conversación con el primer mensaje

**Priority:** `critical`

**Tags:**
- type → @e2e
- feature → @contact

**Description/Objective:** El flujo núcleo del producto: contactar desde el perfil debe crear la
conversación **con** el mensaje inicial y llevar al hilo. Es justo lo que fallaba cuando el backend
en ejecución era anterior al cambio (ignoraba el campo `message` y creaba la conversación vacía).

**Preconditions:**
- Sesión iniciada como `student@claseya.dev` (tiene perfil de estudiante).

### Flow Steps:
1. Desde la landing, abrir la primera tarjeta de profesor.
2. Presionar **Contactar**, escribir un mensaje único y enviarlo.
3. Verificar que se abre **Mensajes** con la conversación y el mensaje.

### Expected Result:
- El mensaje enviado aparece como burbuja del hilo.

### Key verification points:
- Heading "Mensajes" visible.
- Existe un bubble que contiene el texto único enviado.

---

## Test Case: `CONTACT-E2E-002` - Un profesor no puede contactar ni guardar favoritos

**Priority:** `high`

**Tags:**
- type → @e2e
- feature → @contact

**Description/Objective:** El rol TEACHER no debe ver acciones que la API rechaza (403) y que
confundirían al usuario.

**Preconditions:**
- Sesión iniciada como `ana.profe@claseya.dev`.

### Flow Steps:
1. Abrir el perfil público de un profesor.

### Expected Result:
- No existe botón **Contactar**; se explica que los contactos los inician los estudiantes.
- No existe botón de favoritos.

---

## Test Case: `CONTACT-E2E-003` - La materia se elige entre las del profesor

**Priority:** `medium`

**Tags:**
- type → @e2e
- feature → @contact

**Description/Objective:** El modal ofrece las materias reales del profesor (no texto libre).

**Preconditions:**
- Sesión iniciada como estudiante; perfil de un profesor abierto.

### Flow Steps:
1. Presionar **Contactar**.
2. Leer las opciones del select **Materia**.

### Expected Result:
- El select tiene opciones cargadas del catálogo.

### Key verification points:
- `options.length > 0` y los textos no son valores de prueba generados por la suite.
