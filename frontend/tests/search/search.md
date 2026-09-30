### E2E Tests: Búsqueda de profesores

**Suite ID:** `SEARCH-E2E`
**Feature:** Resultados, filtros con acción explícita y paginación.

---

## Test Case: `SEARCH-E2E-001` - El catálogo completo se pagina

**Priority:** `critical`

**Tags:**
- type → @e2e
- feature → @search

**Description/Objective:** Verifica que se pueda recorrer más de una página y que el paginador sea
coherente. Antes de SEARCH-001 la UI pedía siempre la página 0 y el resto del catálogo era
inalcanzable.

**Preconditions:**
- Sesión iniciada como estudiante y seed demo cargado (más de 24 profesores públicos).

### Flow Steps:
1. Buscar un término inexistente para llegar al estado vacío y usar "Ver todos los profesores".
2. Leer el total y el indicador de página.
3. Avanzar a la página 2 y volver a la 1.

### Expected Result:
- Indicador "Página 1 de 3" → "Página 2 de 3" → "Página 1 de 3".
- La página 2 no repite profesores de la 1.

### Key verification points:
- `totalResults() > 24`.
- Sin intersección entre los nombres de la página 1 y la 2.

---

## Test Case: `SEARCH-E2E-002` - Los filtros se aplican solo con el botón

**Priority:** `critical`

**Tags:**
- type → @e2e
- feature → @search

**Description/Objective:** Ningún filtro debe consultar al backend mientras se edita; el cambio se
aplica al presionar **Aplicar filtros**.

**Preconditions:**
- Catálogo completo visible (más de 24 resultados).

### Flow Steps:
1. Anotar el total de resultados.
2. Escribir `30000` en "Desde" **sin** aplicar.
3. Presionar **Aplicar filtros**.
4. Presionar **Limpiar filtros**.

### Expected Result:
- Tras escribir, el total sigue igual (nada se aplicó en vivo).
- Tras aplicar, aparece el estado vacío (ningún profesor demo supera $30.000).
- Tras limpiar, vuelve el total original.

### Key verification points:
- El contador no cambia al escribir.
- Estado vacío visible tras aplicar.

---

## Test Case: `SEARCH-E2E-003` - Materia sin profesores muestra estado vacío

**Priority:** `medium`

**Tags:**
- type → @e2e
- feature → @search

**Description/Objective:** Una materia del catálogo sin docentes (Química General en el seed)
devuelve cero resultados sin romper la pantalla.

**Preconditions:**
- Sesión iniciada; seed demo cargado.

### Flow Steps:
1. Buscar "Química General".

### Expected Result:
- Se muestra "Sin resultados con estos filtros".
