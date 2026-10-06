# DESIGN.md — Sistema visual de ClaseYa

Versión: 2.0
Estado: PROPUESTO (para revisión del arquitecto/director)
Owner: Frontend / Arquitectura de Producto
Alcance: `frontend/` (React 18 + TypeScript + Tailwind CSS 3.4 + Vite)

> Este documento formaliza el sistema visual **tal como está implementado hoy** en el código.
> No propone una identidad nueva: extrae tokens, componentes y patrones existentes para
> convertirlos en una referencia reutilizable.
>
> **Reemplaza a la v1.0 (`docs/frontend/design-system.md`)**, que describía un sistema
> aspiracional (paleta, escala tipográfica, sombras y librerías) que no coincide con el código.
> Ver §12 (discrepancias y deuda) para el detalle de lo corregido.

## Fuentes de verdad (leídas para construir este documento)

| Área | Archivo |
|------|---------|
| Tokens de color / tipografía / radios / sombras / motion | `frontend/tailwind.config.js` |
| Superficies semánticas light/dark, foco global, clases utilitarias | `frontend/src/index.css` |
| Tema (persistencia, anti-flash) | `frontend/src/theme/ThemeProvider.tsx`, `frontend/index.html` |
| Carga de fuentes | `frontend/src/main.tsx` |
| Componentes | `frontend/src/components/ui/*`, `frontend/src/components/teacher/*` |
| Helpers de presentación | `frontend/src/lib/cn.ts`, `frontend/src/lib/format.ts` |
| Composición de pantallas | `frontend/src/App.tsx`, `frontend/src/pages/*` |

---

## 1. Principios de diseño

La UI es un **directorio + contacto seguro**: el objetivo es encontrar un profesor y contactarlo.
La coordinación (día, hora, pago) ocurre fuera de la plataforma. El sistema visual no debe
prometer reservas, agenda ni pagos.

Principios operativos observados en el código:

1. **Buscar es la acción principal.** El `SearchInput` es el elemento dominante del hero y de búsqueda.
2. **Información antes que decoración.** Cards densas en datos (rating, precio, modalidad, zona).
3. **Semántica sobre hex sueltos.** El color se consume vía tokens semánticos (`bg-surface`, `text-content-muted`).
4. **Todo estado tiene forma.** Loading (skeleton), empty (`EmptyState`), error (caja + retry) y éxito (toast).
5. **Accesible por defecto.** Foco visible global, `aria-*`, `role="alert"`, `prefers-reduced-motion`.

---

## 2. Color y tokens

### 2.1 Paleta primitiva

Son las paletas **default de Tailwind** declaradas en `tailwind.config.js`. No hay hex propios.

**primary** (blue)

| Token | Hex | Token | Hex |
|-------|-----|------|-----|
| primary-50 | `#eff6ff` | primary-500 | `#3b82f6` |
| primary-100 | `#dbeafe` | primary-600 | `#2563eb` |
| primary-200 | `#bfdbfe` | primary-700 | `#1d4ed8` |
| primary-300 | `#93c5fd` | primary-800 | `#1e40af` |
| primary-400 | `#60a5fa` | primary-900 | `#1e3a8a` |

**secondary** (violet): `50 #f5f3ff · 100 #ede9fe · 200 #ddd6fe · 300 #c4b5fd · 400 #a78bfa · 500 #8b5cf6 · 600 #7c3aed · 700 #6d28d9 · 800 #5b21b6 · 900 #4c1d95`

**accent** (amber): `50 #fffbeb · 100 #fef3c7 · 200 #fde68a · 300 #fcd34d · 400 #fbbf24 · 500 #f59e0b · 600 #d97706 · 700 #b45309`

**success** (emerald): `50 #ecfdf5 · 500 #10b981 · 600 #059669 · 700 #047857`

**warning** (amber): `50 #fffbeb · 500 #f59e0b · 600 #d97706`

**error** (rose): `50 #fff1f2 · 500 #f43f5e · 600 #e11d48 · 700 #be123c`

### 2.2 Tokens semánticos (superficies)

Definidos como triples RGB en `index.css` y consumidos por Tailwind como
`rgb(var(--token) / <alpha-value>)`. El tema se elige con la clase `.dark` en `<html>`.

| Token semántico | Utility Tailwind | Light (`:root`) | Dark (`.dark`) |
|-----------------|------------------|-----------------|----------------|
| `--background` | `bg-background` | `248 250 252` `#F8FAFC` | `8 15 30` `#080F1E` |
| `--surface` | `bg-surface` | `255 255 255` `#FFFFFF` | `17 26 46` `#111A2E` |
| `--surface-muted` | `bg-surface-muted` | `241 245 249` `#F1F5F9` | `23 34 56` `#172238` |
| `--border` | `border-border` | `226 232 240` `#E2E8F0` | `35 48 74` `#23304A` |
| `--text` | `text-content` | `15 23 42` `#0F172A` | `226 232 240` `#E2E8F0` |
| `--text-muted` | `text-content-muted` | `71 85 105` `#475569` | `148 163 184` `#94A3B8` |
| `--ring` | `ring-ring` | `37 99 235` (= primary-600) | `96 165 250` (= primary-400) |

### 2.3 Reglas de uso del color

- **Superficies siempre por token semántico** (`bg-surface`, `bg-surface-muted`, `border-border`), nunca por color fijo, para que el dark mode funcione solo.
- **Marca por primitiva** (`bg-primary-600`, `text-primary-700`). En dark, el azul claro se pinta con `dark:text-primary-200`.
- **Azul en dark (obligatorio)**: todo texto o ícono azul lleva su contraparte dark (`text-primary-600 dark:text-primary-200`, `text-primary-700 dark:text-primary-200`). Nunca un azul estático sin `dark:`.
- **Sin alpha ad-hoc en superficies**: las sub-superficies usan `bg-surface-muted` **sólido**. No se usan `bg-surface-muted/40` ni `/50`.
- **Estados semánticos**: success/error/accent se usan en badges, toasts y cajas de feedback con pares `bg-*-50` + `text-*-700` (light) y `dark:bg-*-700/20` + `dark:text-*-500` (dark).
- **Selección de texto**: `::selection` → `bg-primary-200 text-primary-900`.
- **Excepción documentada**: el hero de la landing vive sobre un gradiente `from-primary-600 via-primary-700 to-primary-800` con texto blanco; es la única superficie oscura fija y no cambia con el tema.

---

## 3. Tipografía

### 3.1 Familias

| Rol | Familia | Fallbacks | Uso |
|-----|---------|-----------|-----|
| `font-display` | **Bungee** | `ui-rounded, system-ui, sans-serif` | Logo, hero, títulos de sección, precios destacados, iniciales de avatar |
| `font-sans` | **Open Sans** | `system-ui, -apple-system, "Segoe UI", sans-serif` | Todo el resto (nav, cards, formularios, botones, cuerpo) |

Carga vía `@fontsource` en `main.tsx` (solo subset latin):

```
@fontsource/bungee/latin-400.css
@fontsource/open-sans/latin-400.css
@fontsource/open-sans/latin-600.css
@fontsource/open-sans/latin-700.css
```

Pesos disponibles: 400 / 600 / 700 (**no se carga 500**). Regla: usar `font-semibold` (600) y `font-bold` (700), nunca `font-medium`.

### 3.2 Tamaños

**No hay escala tipográfica custom** en `tailwind.config.js`. Se usan los tamaños default de Tailwind, con estos valores en uso:

| Uso observado | Clase | px |
|---------------|-------|----|
| Hero H1 | `text-4xl sm:text-6xl` | 36 → 60 |
| Título de página | `text-3xl` | 30 |
| Título de sección | `text-2xl sm:text-3xl` | 24 → 30 |
| Título de card / panel | `text-xl` | 20 |
| Subtítulo / card heading | `text-lg` | 18 |
| Cuerpo | `text-base` / `text-[15px]` | 16 / 15 |
| Secundario | `text-sm` | 14 |
| Caption / meta | `text-xs` / `text-[11px]` | 12 / 11 |

Reglas:
- Títulos display usan `leading-tight`; el cuerpo usa el interlineado default.
- `body` fuerza `font-variant-numeric: tabular-nums` global (precios, contadores, timestamps alineados).
- Todo heading es `text-content` por defecto (`h1..h4` en `index.css`); sobre superficies oscuras hay que pasar `text-white` explícito (ver hero).

---

## 4. Espaciado

Escala default de Tailwind (base 4px). Valores efectivamente usados en el código:

| px | Clase | Dónde |
|----|-------|-------|
| 4 | `1` | gaps mínimos |
| 6 | `1.5` | `gap-1.5`, `py-1` |
| 8 | `2` | `gap-2` |
| 10 | `2.5` | `text-[15px]`/inputs (impar documentado) |
| 12 | `3` | `gap-3`, `py-3` |
| 16 | `4` | `p-4`, `gap-4` |
| 20 | `5` | `p-5`, `gap-5` (padding de card, gap de grillas) |
| 24 | `6` | `p-6`, `gap-6` (card de panel) |
| 32 | `8` | `py-8`, `gap-8` (layout de página) |
| 48 | `12` | `py-12`, `p-12` (secciones, empty state) |
| 64 | `16` | `sm:py-16` (secciones desktop) |

**Container** (`.container-page`): `mx-auto w-full max-w-6xl px-4 sm:px-6 lg:px-8`
→ ancho máx 1152px, padding horizontal 16 / 24 / 32px.

Regla: usar tokens de la escala; no inventar valores arbitrarios (`[Npx]`) salvo `text-[15px]` / `text-[11px]`, que ya existen y son decisiones de densidad.

---

## 5. Bordes y radios

Overrides en `tailwind.config.js`: `xl: 0.875rem` (14px), `2xl: 1.25rem` (20px), `3xl: 1.75rem` (28px).

| Elemento | Radio | Valor |
|----------|-------|-------|
| Botones, inputs, selects, textareas, toasts, tiles de icono | `rounded-xl` | 14px |
| Cards, modales, paneles, dropdown de búsqueda, shell de conversación | `rounded-2xl` | 20px |
| Badges, chips, avatares, pills, contador de no leídos | `rounded-full` | 9999px |
| Botón de cierre de modal, filas de documento | `rounded-lg` | 8px |
| Sub-card (sub-panel inset) | `rounded-xl` | 14px |
| Sub-card (fila compacta inset) | `rounded-lg` | 8px |
| Cajas de error/aviso inline | `rounded-xl` | 14px |

`rounded-3xl` está definido pero **no se usa**. Bordes siempre con `border-border`; los empty states usan `border-dashed`.

**Sub-card / fila inset** (`SubCard`): superficie anidada dentro de una `Card` para agrupar ítems hijos (credenciales, formación, documentos, horarios). Fondo `bg-surface-muted` **sólido**, radio `rounded-xl` (sub-panel, `p-4`) o `rounded-lg` (fila compacta, `px-3 py-2`). Reemplaza los estilos ad-hoc anteriores.

---

## 6. Sombras

Definidas en `tailwind.config.js` (tinte azulado, nunca negro puro):

| Token | Valor |
|-------|-------|
| `shadow-card` | `0 1px 2px rgb(15 23 42 / 0.06), 0 8px 24px -12px rgb(15 23 42 / 0.18)` |
| `shadow-card-hover` | `0 2px 4px rgb(15 23 42 / 0.06), 0 18px 40px -18px rgb(15 23 42 / 0.28)` |
| `shadow-focus` | `0 0 0 3px rgb(var(--ring) / 0.35)` (definido; en la práctica se usa el ring de Tailwind) |

Uso: `Card` base → `shadow-card`; card interactiva en hover → `shadow-card-hover`; dropdown de búsqueda y modal → `shadow-card-hover`; toast y thumb del price range → `shadow-card`.
**El Navbar no usa sombra**: se separa con `border-b` + `backdrop-blur`.

---

## 7. Motion

Sin librería de animación (no hay `framer-motion`). Todo es CSS/Tailwind.

### 7.1 Keyframes y animaciones

| Animación | Keyframe | Duración / easing | Uso |
|-----------|----------|-------------------|-----|
| `animate-fade-up` | `translateY(8px) → 0`, opacity 0→1 | `.35s ease-out both` | Toasts |
| `animate-fade-in` | opacity 0→1 | `.25s ease-out both` | Overlay de modal, dropdown de búsqueda |
| `animate-scale-in` | `scale(.97) → 1`, opacity 0→1 | `.18s ease-out both` | Panel de modal |
| `animate-shimmer` | `translateX(100%)` | `1.4s infinite` | `Skeleton` |
| `animate-spin` | (Tailwind) | — | Spinner de `Button` en loading |

### 7.2 Transiciones

- Easing custom `ease-smooth = cubic-bezier(.22,.61,.36,1)` para botones y cards.
- Duración estándar **200ms** (`transition-colors duration-200`, `duration-200`).
- **Card interactiva**: `transition-[transform,box-shadow] duration-200 ease-smooth` + `hover:-translate-y-0.5` (2px) + `hover:shadow-card-hover`.
- **Button**: `transition-[transform,background-color,color,box-shadow] duration-200 ease-smooth` + `active:translate-y-[1px]`.
- **Chip / inputs**: `transition-colors duration-200`.
- **Cambio de tema**: clase `.theme-transition` en `<html>` durante 200ms (color, background, border, box-shadow); se aplica solo al cambiar el tema para no afectar hovers.

### 7.3 Accesibilidad del motion

`@media (prefers-reduced-motion: reduce)` desactiva animaciones, transiciones y `scroll-behavior` a nivel global. Es obligatorio respetarlo en cualquier componente nuevo.

---

## 8. Componentes

Todos viven en `frontend/src/components/ui/` (genéricos) y `frontend/src/components/teacher/` (dominio).
Variantes con `class-variance-authority`; merge de clases con `cn()` (`clsx` + `tailwind-merge`).

### 8.1 Button

```
base   inline-flex items-center justify-center gap-2 rounded-xl font-semibold
       transition-[transform,background-color,color,box-shadow] duration-200 ease-smooth
       focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring
       focus-visible:ring-offset-2 focus-visible:ring-offset-background
       disabled:pointer-events-none disabled:opacity-50 active:translate-y-[1px]
```

| Variante | Estilo | Uso |
|----------|--------|-----|
| `primary` | `bg-primary-600 text-white shadow-card hover:bg-primary-700` | Acción principal (default) |
| `secondary` | `bg-secondary-600 text-white hover:bg-secondary-700` | Acción alterna sólida (violeta) |
| `accent` | `bg-accent-400 text-primary-900 hover:bg-accent-500` | Énfasis cálido |
| `outline` | `border border-border bg-surface text-content hover:bg-surface-muted` | Acción secundaria neutra |
| `ghost` | `text-content hover:bg-surface-muted` | Acciones de nav, íconos |
| `danger` | `bg-error-600 text-white hover:bg-error-700` | Solo destructivo |
| `link` | `text-primary-600 underline-offset-4 hover:underline px-0` | Acción terciaria |

| Tamaño | Clase | Alto |
|--------|-------|------|
| `sm` | `h-9 px-3 text-sm` | 36px |
| `md` (default) | `h-11 px-5 text-[15px]` | 44px |
| `lg` | `h-12 px-6 text-base` | 48px |
| `icon` | `h-10 w-10` | 40px |

`block` → `w-full`. `loading` → muestra `Loader2` con `animate-spin`, setea `disabled` y `aria-busy`.

> Nota de accesibilidad: `sm` (36px) queda por debajo del objetivo táctil de 44px. Se acepta en barras densas (nav, tablas), no en acciones primarias.

### 8.2 Card

`rounded-2xl border border-border bg-surface shadow-card`.
Prop `interactive` agrega `cursor-pointer` + hover (lift + sombra). Es la base de `TeacherCard`, `CategoryCard` y los paneles de perfil.

`TeacherCard` (dominio): card interactiva `p-5` en columna; avatar `lg`, nombre `text-lg font-bold truncate`, botón de favorito (`heart`, `rounded-full p-1.5`, activo `text-error-600` + `fill`), `Badge` de verificación, rating, bio `line-clamp-2`, footer con `border-t` (zona + badges de modalidad + precio `font-display text-lg text-primary-700 dark:text-primary-200`) y `Button size="sm" block` "Ver perfil".
Variante `featured`: `border-primary-200 ring-1 ring-primary-100 dark:ring-primary-900/40`.

`CategoryCard` (dominio): card interactiva `flex items-center gap-3 p-4`; tile de ícono `h-11 w-11 rounded-xl bg-primary-50 text-primary-700 dark:bg-primary-900/40 dark:text-primary-200`; título `font-semibold`; subtítulo `text-xs text-content-muted`. Es `role="button"` con `tabIndex={0}` y activación por Enter/Espacio.

### 8.3 Badge

`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold`.

| Tono | Light | Dark |
|------|-------|------|
| `neutral` | `bg-surface-muted text-content-muted` | (mismo, vía tokens) |
| `primary` | `bg-primary-50 text-primary-700` | `dark:bg-primary-900/40 dark:text-primary-200` |
| `success` | `bg-success-50 text-success-700` | `dark:bg-success-700/20 dark:text-success-500` |
| `accent` | `bg-accent-100 text-accent-700` | `dark:bg-accent-700/25 dark:text-accent-300` |
| `secondary` | `bg-secondary-100 text-secondary-700` | `dark:bg-secondary-700/25 dark:text-secondary-200` |

`VerifiedBadge` = `Badge tone="success"` + ícono `BadgeCheck`. Puede renderizar un `icon` a la izquierda.

**`Tag` (valor editable):** mismo lenguaje visual que `Badge`, para valores que el usuario puede quitar (p. ej. materias, formación). Con `onRemove` renderiza un `<button>` de quitar (`X` de lucide, `h-3.5 w-3.5`, `aria-label="Quitar {etiqueta}"`, color del tono + `hover:text-error-600`). Es un `<span>` — no `Chip`, que es un `<button>` toggle y no admite un botón anidado. `Badge` sigue siendo presentacional; `Tag` es la variante editable.

### 8.4 Chip (filtros / atajos)

`rounded-full border px-3.5 py-1.5 text-sm font-semibold transition-colors duration-200`.
Activo: `border-primary-600 bg-primary-600 text-white`. Inactivo: `border-border bg-surface text-content hover:border-primary-300 hover:bg-surface-muted`.
Es un `<button>` con `aria-pressed`.

### 8.5 Avatar

`rounded-full object-cover` con imagen (`loading="lazy"`); sin imagen, iniciales sobre `bg-primary-100 font-display text-primary-700 dark:bg-primary-900/50 dark:text-primary-200`.
Tamaños: `sm` 36px · `md` 48px · `lg` 64px · `xl` 96px.

### 8.6 Rating

`inline-flex items-center gap-1.5 text-sm`; estrella `h-4 w-4` (`fill-accent-400 text-accent-400` si hay rating, `text-content-muted/50` si no). Texto `font-semibold`; sin datos → "Sin opiniones".

### 8.7 Skeleton

Clase `.skeleton`: `relative overflow-hidden bg-surface-muted` + `::after` con gradiente y `animate-shimmer`. Base `rounded-xl`. Se usa con alturas fijas (`h-64`, `h-40`, `h-20`…) para reservar el layout.

### 8.8 EmptyState

`rounded-2xl border border-dashed border-border bg-surface px-6 py-12 text-center`.
Ícono opcional en círculo `h-12 w-12 bg-primary-50 text-primary-600 dark:bg-primary-900/40 dark:text-primary-200`, título `text-lg font-bold`, descripción `max-w-md text-sm text-content-muted` y una acción (`Button`).

**Variante `compact`** (`size="compact"`): para vacíos dentro de una sub-sección. `rounded-xl px-4 py-6`; ícono `text-content-muted` sin círculo; título `text-sm font-semibold`; descripción `text-xs`. La variante de página (default) no cambia.

### 8.9 Campo de formulario (Field / Input / Textarea / Select)

`Field` cablea label + control + hint/error con `useId` y `aria-describedby` / `aria-invalid`; label `text-sm font-semibold`; hint `text-xs text-content-muted`; error `text-xs font-semibold text-error-600` con `role="alert"`.

Control compartido:
```
w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-[15px] text-content
placeholder:text-content-muted/70 transition-colors duration-200
focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-ring/40
disabled:opacity-50
```
`Textarea` es `resize-none` (decisión explícita). `Select` agrega `pr-8`.

### 8.10 Modal

Overlay `fixed inset-0 z-50 grid place-items-center bg-slate-950/60 p-4 animate-fade-in`; panel `w-full rounded-2xl border border-border bg-surface p-6 shadow-card-hover animate-scale-in`.
Tamaños: `sm max-w-md` · `md max-w-lg` · `lg max-w-3xl`.
Comportamiento accesible: `role="dialog"`, `aria-modal`, `aria-labelledby`; cierre con `Escape` y click en backdrop (`onMouseDown` sobre el overlay); focus trap circular; restauración de foco; `body overflow: hidden`.
Botón de cierre: `rounded-lg p-1.5 text-content-muted hover:bg-surface-muted`.

### 8.11 Toast

Contenedor `fixed inset-x-0 bottom-4 z-[60]` centrado, `aria-live="polite"`.
Item `max-w-sm rounded-xl border px-4 py-3 text-sm font-semibold shadow-card animate-fade-up`, auto-descarte a **4200ms**, botón de cierre. Tonos: `success` (`CheckCircle2`), `error` (`AlertTriangle`), `info` (`Info`).
Uso: `const { notify } = useToast(); notify('Guardado en favoritos'); notify('...', 'error')`.

**Acción (undo)**: `notify('...', { action: { label: 'Deshacer', onClick } })` agrega un botón de acción dentro del toast. Los toasts con acción duran **8000 ms** (los informativos siguen en 4200 ms) para dar tiempo a leer y actuar.

### 8.12 Layout: container-page, Section, Navbar, Footer

- **`.container-page`**: ancho/padding estándar de página (ver §4).
- **`Section`**: `py-12 sm:py-16`; header `mb-8` con título `font-display text-2xl sm:text-3xl` y subtítulo `text-content-muted`.
- **`Navbar`**: `sticky top-0 z-40 border-b border-border bg-surface/85 backdrop-blur supports-[backdrop-filter]:bg-surface/70`, alto `h-16`. Logo = tile `h-9 w-9 rounded-xl bg-primary-600 text-white` (ícono `GraduationCap`) + wordmark `font-display text-2xl text-primary-700 dark:text-primary-200`. Acciones con `Button variant="ghost" size="sm"`; labels ocultos bajo `sm` (`hidden sm:inline`); toggle de tema `size="icon"`.
- **`Footer`**: `border-t border-border bg-surface`; grilla `gap-10 py-12 sm:grid-cols-2 lg:grid-cols-4`; barra inferior `border-t py-5 text-xs text-content-muted`.

### 8.13 SearchInput

Form `flex items-center gap-2 rounded-2xl border border-border bg-surface p-2 shadow-card`; campo `h-11 text-base sm:text-lg` (size `lg`) o `h-10 text-[15px]`; botón "Buscar".
Dropdown `absolute z-30 mt-2 rounded-2xl border border-border bg-surface shadow-card-hover animate-fade-in`, con sugerencias (combobox), historial (`sessionStorage['claseya.searchHistory']`, últimos 5) y materias populares (chips `rounded-full border px-3 py-1`).
Teclado: `ArrowUp/Down` navega, `Enter` envía, `Escape` cierra; click afuera cierra. `role="combobox"` + `role="listbox"`.

### 8.14 PriceRange

Slider dual con dos `<input type="range">` superpuestos (sin dependencia). Track `h-1.5 rounded-full bg-surface-muted`; rango activo `bg-primary-600`; thumbs `h-4 w-4 rounded-full bg-primary-600 shadow-card`.
Límites `0–30000` (ARS), paso `250`. Arrastrar clampa un handle contra el otro; tipear no reescribe el valor. "Sin tope" se representa como `null` y el extremo superior muestra placeholder "Sin tope".
Es parte de los filtros **draft**: no dispara request por sí solo.

---

## 9. Estados de interfaz

| Estado | Patrón implementado |
|--------|---------------------|
| **Foco** | Global: `:focus-visible { outline-none ring-2 ring-ring ring-offset-2 ring-offset-background }`. Botones repiten el ring con `focus-visible`. Inputs: `focus:border-primary-500 focus:ring-2 focus:ring-ring/40`. |
| **Hover** | Cards se elevan 2px + sombra; botones oscurecen/clarear según variante; ghost/outline ganan `bg-surface-muted`; links `hover:underline`. |
| **Active** | Botones `active:translate-y-[1px]`; chips cambian a fondo primary-600. |
| **Disabled** | `disabled:opacity-50` + `pointer-events-none` (botones) / `opacity-50` (inputs). |
| **Loading** | `Button loading` (spinner) y `Skeleton` para estructuras. En búsqueda, resultados con `opacity-50`. Skeletons solo en la primera carga; re-filtrar no colapsa el layout. |
| **Error** | Campo: texto `text-error-600` + `role="alert"`. Formulario: caja `rounded-xl border border-error-500/30 bg-error-50 px-3 py-2 text-sm font-semibold text-error-700` (+ variantes dark). Página: `EmptyState` con acción "Reintentar". |
| **Empty** | `EmptyState` (ícono + título + descripción + acción). Nunca texto suelto. |
| **Success** | `Toast` tone success + mensajes positivos en pantalla. |

---

## 10. Dark / Light mode

- **Estrategia**: `darkMode: 'class'`; la clase `.dark` va en `<html>`.
- **Persistencia**: `localStorage['claseya.theme']`; si no hay valor, respeta `prefers-color-scheme`.
- **Anti-flash**: script inline en `index.html` aplica el tema **antes** del primer paint.
- **Transición**: `.theme-transition` en `<html>` durante 200ms al cambiar tema; no afecta hovers ni el primer paint.
- **Superficies**: siempre vía tokens semánticos; los colores de marca se ajustan con variantes `dark:` (p. ej. `text-primary-700 dark:text-primary-200`).
- **No invertir imágenes.**
- **Excepción**: el hero de la landing es oscuro en ambos temas (gradiente primario + texto blanco).

---

## 11. Responsive

Breakpoints default de Tailwind: `sm 640` · `md 768` · `lg 1024` · `xl 1280`.

| Área | Mobile (<640) | sm (≥640) | lg (≥1024) |
|------|---------------|-----------|------------|
| Container | `px-4` | `px-6` | `px-8` (máx 1152px) |
| Secciones | `py-12` | `py-16` | `py-16` |
| Navbar | íconos (labels `hidden sm:inline`) | labels | labels |
| Hero | `py-16`, H1 `text-4xl` | `py-24`, H1 `text-6xl` | igual |
| Grilla de profesores | 1 col | `sm:grid-cols-2` | `lg:grid-cols-3` |
| Grilla de categorías | 1 col | `sm:grid-cols-2` | `lg:grid-cols-4` |
| Página de búsqueda | filtros arriba, resultados abajo | — | `lg:grid-cols-[260px_1fr]`, filtros `lg:sticky top-24` |
| Perfil de profesor | una columna | — | `lg:grid-cols-[1fr_320px]`, CTA `lg:sticky top-24` |
| Mensajes | lista **o** hilo (`hidden`), botón "Volver" en el hilo | — | `lg:grid-cols-[320px_1fr]`, lista + hilo |
| Formularios | 1 col | `sm:grid-cols-2` | `sm:grid-cols-4` según panel |
| Modales | ancho completo con `p-4` | centrado `max-w-*` | igual |

Reglas:
- Nunca scroll horizontal.
- `top-24` (96px) es el offset de elementos sticky bajo el Navbar (`h-16`) + margen.
- Acciones primarias en mobile pueden usar `block` (ancho completo).
- Botones de acción primaria ≥44px (`md`/`lg`); en mobile evitar `sm` para acciones importantes.

---

## 12. Patrones de interacción

- **Búsqueda**: `Enter` envía; flechas navegan sugerencias; `Escape`/click afuera cierra; historial en `sessionStorage`.
- **Filtros (draft/applied)**: tocar chips/slider/escribir precio solo actualiza el **draft**; recién "Aplicar filtros" (o el submit del buscador) consulta la API. El botón muestra el contador de filtros activos.
- **Modal**: `Escape`, backdrop, focus trap y restauración de foco.
- **Mensajería**: `Enter` envía / `Shift+Enter` nueva línea; composer auto-crece hasta 200px; scroll solo dentro del hilo; hilos cacheados para no re-mostrar skeletons.
- **Favoritos**: corazón con `aria-pressed`; si el usuario no tiene perfil de estudiante (API 409) se abre el formulario de perfil.
- **Navegación**: por estado en `App.tsx` (sin router); `history.pushState` para back/forward; cada cambio de vista vuelve al top de la página.
- **Verificación**: carga de documentos + submit por credencial; estado por `Badge` (`Pendiente`/`En revisión`/`Verificado`/`Rechazado`).
- **Acciones destructivas**: si son **reversibles** (quitar materia, desactivar disponibilidad) → se ejecutan y se ofrece **`Toast` con "Deshacer"**. Si son **irreversibles** (quitar formación con documentos/verificación asociados, eliminar un documento ya subido) → **`ConfirmDialog`** (`Modal` + `Button variant="danger"`). Nunca destructivo sin una de las dos.

---

## 13. Accesibilidad

- Objetivo WCAG AA.
- Foco visible global (nunca `outline: none` sin reemplazo).
- `aria-label` en botones de ícono; `aria-hidden` en decorativos.
- `role="alert"` en errores; `aria-live="polite"` en resultados y toasts.
- Diálogos con semántica completa (`role="dialog"`, `aria-modal`, `aria-labelledby`).
- `aria-pressed` en chips y toggles; `aria-busy` en zonas en carga.
- Labels siempre visibles (nunca placeholder como label).
- `prefers-reduced-motion` respetado globalmente.
- Objetivo táctil 44px: se cumple en botones `md`/`lg`; `sm` (36px) es la excepción conocida.
- El estado **no depende solo del color**: además del color se usa forma/ícono (p. ej. check lleno vs círculo vacío) y texto alternativo (`sr-only`). Aplica a indicadores binarios como el checklist de completitud.
- Los toasts con acción deben durar lo suficiente para leer y actuar (ver §8.11).

---

## 14. Iconografía

- Librería única: **`lucide-react`**. No mezclar.
- Tamaños en uso: `h-3.5 w-3.5` (14), `h-4 w-4` (16), `h-5 w-5` (20), `h-6 w-6` (24), `h-7 w-7` (28).
- Decorativos con `aria-hidden="true"`; los que actúan, con `aria-label`.
- Sin emojis como íconos.

---

## 15. Voz y contenido (UI)

- **Idioma**: español rioplatense con voseo ("Elegí", "Contactá", "Guardá", "Probá").
- **Casing**: sentence case en títulos y botones ("Buscar profesores", "Guardar cambios").
- **Números**: `toLocaleString('es-AR')` → `$12.000`. Precio por hora: `$12.000/h`; sin precio: "Consultar"; vacío: "a convenir".
- **Rating**: `4.5 (12)` o "Sin opiniones".
- **Modalidad**: "Online" / "Presencial" / "A convenir".
- **Fechas**: `es-AR`, `dd/mm hh:mm`.
- **Errores**: genéricos y accionables ("No pudimos guardar el perfil."), nunca stack traces ni detalles internos.
- **Sin emojis** en la UI.

---

## 16. Convenciones de implementación

- **Merge de clases**: `cn()` (`clsx` + `tailwind-merge`). Nunca concatenar strings a mano cuando hay conflicto de variantes.
- **Variantes**: `class-variance-authority` para componentes con variantes (Button, Badge).
- **Tokens antes que hex**: usar clases semánticas/de paleta; no hardcodear colores ni espaciados repetidos.
- **Librerías aprobadas**: `lucide-react`, `clsx`, `tailwind-merge`, `class-variance-authority`, `@fontsource/*`, `@react-oauth/google`. Dependencia nueva → justificación (AGENTS.md).
- **Sin `framer-motion`**: la animación es CSS/Tailwind.

---

## 17. Discrepancias conocidas y deuda

1. **La v1.0 del design-system (borrada) no coincidía con el código.** Diferencias principales, ahora corregidas en este documento:
   - Paleta: la v1.0 declaraba hex propios (`primary-500 #2457FF`, `accent-500 #FF9800`, `success #22C55E`, `error #EF4444`); el código usa Tailwind (blue, amber, emerald `#10b981`, rose `#f43f5e`).
   - `Button secondary`: la v1.0 decía "borde azul"; el código es **sólido violeta**.
   - Alturas: la v1.0 decía botón/input 48px; el default real es **44px** (`md`).
   - Tipografía: la v1.0 definía escala Hero 56 → Caption 12; **no existe escala custom** (tamaños default de Tailwind).
   - Radios: la v1.0 decía botón/input `lg` 16px; es `rounded-xl` 14px.
   - Sombras: la v1.0 listaba sm/md/lg; son `card`/`card-hover`/`focus`.
   - Container: la v1.0 decía 1280px y padding 20/32/48; es **1152px** y 16/24/32.
   - Motion: la v1.0 recomendaba `framer-motion`; **no está instalado**.
   - Fuentes: una iteración anterior usó Coiny + Cabin Condensed; hoy es **Bungee + Open Sans**.
2. **Referencias**: `AGENTS.md` apunta (a partir de este cambio) a `docs/frontend/DESIGN.md`.
3. **Botones `sm` de 36px** por debajo del objetivo táctil de 44px (ver §13).
4. **Sin router**: la navegación es por estado; la URL no cambia por pantalla.

---

## Apéndice A — Mapa token → archivo

| Qué | Dónde se define |
|-----|-----------------|
| Paletas (primary/secondary/accent/success/warning/error) | `tailwind.config.js` → `theme.extend.colors` |
| Superficies semánticas (RGB vars) | `index.css` → `:root` y `.dark`; mapeo en `tailwind.config.js` → `colors.{background,surface,surface-muted,border,content,ring}` |
| Familias tipográficas | `tailwind.config.js` → `fontFamily`; carga en `main.tsx` |
| Radios | `tailwind.config.js` → `borderRadius` |
| Sombras | `tailwind.config.js` → `boxShadow` |
| Animaciones | `tailwind.config.js` → `keyframes` + `animation`; `ease-smooth` en `transitionTimingFunction` |
| Foco global, `::selection`, `.container-page`, `.skeleton`, `.theme-transition`, `.price-range`, `.composer`, reduced-motion | `index.css` |
| Tema (persistencia/anti-flash) | `ThemeProvider.tsx`, `index.html` |

## Apéndice B — Inventario de componentes

`components/ui/`: `Button`, `primitives` (`Card`, `SubCard`, `Badge`, `VerifiedBadge`, `Tag`, `Chip`, `Avatar`, `Rating`, `Skeleton`, `EmptyState`, `ConfirmDialog`), `Field` (`Input`, `Textarea`, `Select`), `Modal`, `Toast` (+`ToastProvider`/`useToast`), `Navbar`, `Footer`, `Section`, `SearchInput`, `PriceRange`.
`components/teacher/`: `TeacherCard`, `CategoryCard`, `VerificationPanel`.
`components/`: `LoginModal`, `ContactModal`, `StudentProfileModal`, `GoogleSignInButton`.

---

> **Estado de esta propuesta.** Documenta con fidelidad el sistema implementado. Antes de marcarlo
> canónico conviene confirmar: (a) si `styles.css` se elimina; (b) si `secondary` violeta es la
> intención de marca o un desvío a corregir; (c) si se formalizan paletas propias o se acepta
> Tailwind default como paleta oficial; (d) si se agrega una escala tipográfica custom o se mantiene
> la default de Tailwind.
