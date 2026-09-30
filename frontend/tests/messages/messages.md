### E2E Tests: Mensajes

**Suite ID:** `MSG-E2E`
**Feature:** Hilo de conversación, envío de mensajes y layout del hilo.

---

## Test Case: `MSG-E2E-001` - Un mensaje largo sin espacios no genera scroll horizontal

**Priority:** `critical`

**Tags:**
- type → @e2e
- feature → @messages

**Description/Objective:** Verifica que un texto sin espacios (ej. "aaaa…") se parta dentro del
bubble en lugar de estirar el hilo y producir scroll horizontal.

**Preconditions:**
- Sesión iniciada como `student@claseya.dev` (token por API).
- Conversación existente con el primer profesor público, con al menos un mensaje.

### Flow Steps:
1. Abrir **Mensajes** desde el navbar.
2. Abrir la conversación con el profesor.
3. Enviar un mensaje de 220 caracteres sin espacios.
4. Medir `scrollWidth - clientWidth` del contenedor del hilo y del último bubble.

### Expected Result:
- El mensaje se envía y se ve en el hilo.
- Ningún contenedor desborda horizontalmente.

### Key verification points:
- `overflow <= 1` en `[data-testid="thread-body"]`.
- `overflow <= 1` en el bubble.

---

## Test Case: `MSG-E2E-002` - Enter envía y Shift+Enter agrega una línea

**Priority:** `critical`

**Tags:**
- type → @e2e
- feature → @messages

**Description/Objective:** El composer envía con Enter y conserva Shift+Enter como salto de línea,
sin partir el mensaje en dos.

**Preconditions:**
- Sesión iniciada como estudiante, con una conversación abierta.

### Flow Steps:
1. Escribir una línea, presionar `Shift+Enter` y escribir la segunda.
2. Presionar `Enter`.
3. Verificar el bubble resultante y el estado del composer.

### Expected Result:
- Se envía **un** mensaje que contiene ambas líneas.
- El composer queda vacío.

### Key verification points:
- El bubble contiene la primera y la segunda línea.
- `composer` con valor vacío tras enviar.

---

## Test Case: `MSG-E2E-003` - Volver a una conversación ya vista no parpadea

**Priority:** `high`

**Tags:**
- type → @e2e
- feature → @messages

**Description/Objective:** Al reabrir una conversación ya visitada, el hilo se renderiza desde
caché y **no** aparecen skeletons (el parpadeo reportado por el usuario).

**Preconditions:**
- Sesión iniciada como estudiante, con **dos** conversaciones con mensajes.

### Flow Steps:
1. Abrir la conversación A y esperar sus mensajes.
2. Abrir la conversación B.
3. Volver a la conversación A.

### Expected Result:
- La conversación A se muestra al instante.

### Key verification points:
- Hay bubbles visibles inmediatamente.
- `document.querySelectorAll('.skeleton').length === 0` **sin** reintentos (un flash no se tolera).
