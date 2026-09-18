# Design System — ClaseYa

Versión: 1.0
Estado: ACTIVO
Owner: Arquitectura de Producto

> Este documento define la identidad visual, componentes y reglas de UX/UI de ClaseYa. Es la fuente de verdad para cualquier implementación frontend.

---

# 1. Filosofía de diseño

ClaseYa es un marketplace educativo argentino que conecta estudiantes y profesores particulares.

La interfaz debe transmitir cinco valores:

- Educación.
- Confanía.
- Cercanía.
- Profesionalismo.
- Tecnología moderna.

## Principios de UX

1. Buscar profesor debe ser la acción principal.
2. Reducir la cantidad de clics para encontrar un profesor.
3. Mostrar información importante antes que decoración.
4. Priorizar claridad sobre creatividad.
5. Todo elemento visual debe tener un propósito.

---

# 2. Identidad visual

## Inspiración

ClaseYa toma inspiración en:

- Superprof → Marketplace educativo.
- Airbnb → Espaciado y tarjetas.
- Linear → Consistencia visual.
- Notion → Tipografía y jerarquía.

Nunca copiar interfaces.

---

# 3. Paleta oficial

## Primary (ClaseYa Blue)

| Token | Color |
|-------|--------|
| primary-50 | #EFF4FF |
| primary-100 | #DCE8FF |
| primary-200 | #B7CEFF |
| primary-300 | #8FB2FF |
| primary-400 | #5E89FF |
| primary-500 | #2457FF |
| primary-600 | #1C46D8 |
| primary-700 | #1739B2 |
| primary-800 | #102A84 |
| primary-900 | #091A55 |

## Accent

| Token | Color |
|-------|--------|
| accent-50 | #FFF7E6 |
| accent-100 | #FFEBC2 |
| accent-200 | #FFD78A |
| accent-300 | #FFC14D |
| accent-400 | #FFB347 |
| accent-500 | #FF9800 |

## Success

#22C55E

## Warning

#F59E0B

## Error

#EF4444

## Info

#3B82F6

---

# 4. Superficies

## Light

Background: #F8FAFC

Surface: #FFFFFF

Surface Secondary: #F1F5F9

Border: #E2E8F0

Muted: #64748B

Heading: #0F172A

Text: #334155

## Dark

Background: #0B1120

Surface: #111827

Surface Secondary: #1F2937

Border: #334155

Heading: #F8FAFC

Text: #CBD5E1

Muted: #94A3B8

---

# 5. Tipografía

## Bungee

Uso exclusivo:

- Logo.
- Hero.
- Grandes títulos de marketing.

Nunca usar Coiny para párrafos.

## Open Sans

Uso obligatorio en:

- Navegación.
- Cards.
- Formularios.
- Botones.
- Tablas.
- Perfil de profesor.
- Dashboard.

## Escala tipográfica

| Uso | Tamaño |
|------|---------|
| Hero | 56 |
| H1 | 42 |
| H2 | 34 |
| H3 | 28 |
| H4 | 22 |
| Body Large | 18 |
| Body | 16 |
| Small | 14 |
| Caption | 12 |

Line-height:

120% títulos.

150% texto.

---

# 6. Grid y Layout

## Container

Máximo ancho: 1280px.

Padding horizontal:

Mobile: 20px

Tablet: 32px

Desktop: 48px

## Espaciado

Escala oficial:

4

8

12

16

20

24

32

40

48

64

80

96

Nunca usar valores arbitrarios si existe un token.

---

# 7. Border Radius

| Token | Valor |
|--------|-------|
| xs | 6 |
| sm | 8 |
| md | 12 |
| lg | 16 |
| xl | 20 |
| 2xl | 24 |
| full | 9999 |

Cards usan xl.

Inputs usan lg.

Botones usan lg.

---

# 8. Sombras

Shadow-sm

Inputs.

Shadow-md

Cards.

Shadow-lg

Navbar sticky.

Nunca usar sombras negras intensas.

---

# 9. Iconografía

Librería oficial:

lucide-react.

Tamaño:

16

20

24

32

Nunca mezclar librerías.

---

# 10. Botones

## Primary

Azul.

Texto blanco.

Altura 48px.

Hover oscurece.

Focus ring azul.

## Secondary

Borde azul.

Texto azul.

Hover fondo primary-50.

## Ghost

Sin borde.

Hover gris.

## Danger

Rojo.

Solo acciones destructivas.

Estados obligatorios:

Default

Hover

Active

Focus

Disabled

Loading

---

# 11. Inputs

Altura 48px.

Icono opcional.

Label arriba.

Helper text abajo.

Error debajo.

Focus ring primary-500.

Nunca usar placeholder como label.

---

# 12. SearchInput

Elemento principal del producto.

Incluye:

Icono.

Placeholder descriptivo.

Botón Buscar.

Loading.

Autocomplete preparado.

Chips debajo con materias populares.

---

# 13. Cards

## Teacher Card

Debe incluir:

Foto.

Nombre.

Rating.

Precio.

Ubicación.

Modalidad.

Materias.

Badge Verificado.

Tiempo de respuesta.

CTA.

Hover eleva ligeramente.

## Category Card

Icono.

Materia.

Cantidad de profesores.

Hover cambia borde.

---

# 14. Navbar

Desktop:

Logo.

Buscador pequeño.

Iniciar sesión.

Avatar.

Mobile:

Logo.

Hamburguesa.

Nunca esconder navegación esencial.

---

# 15. Hero

Es la sección más importante.

Debe ocupar aproximadamente 70% de la pantalla desktop.

Contiene:

Título.

Subtítulo.

SearchInput.

Categorías.

Prueba social.

Ilustración.

---

# 16. Empty States

Nunca mostrar texto rojo aislado.

Todo estado vacío incluye:

Icono.

Título.

Descripción.

Acción sugerida.

Ejemplo:

"No encontramos profesores de Álgebra."

Botón:

Explorar Matemática.

---

# 17. Loading

Skeleton obligatorio.

Nunca spinner solo.

Teacher cards tienen skeleton propio.

---

# 18. Animaciones

Librería recomendada:

framer-motion.

Duración:

150–250ms.

Permitidas:

Fade.

Slide.

Scale 1.02 hover.

No rebotes exagerados.

Respetar prefers-reduced-motion.

---

# 19. Responsive

## Mobile

320+

Navbar simplificada.

Cards verticales.

Botones ancho completo.

## Tablet

768+

Dos columnas cuando corresponda.

## Desktop

1280+

Tres o cuatro columnas.

Sidebar de filtros.

Nunca scroll horizontal.

---

# 20. Accesibilidad

Cumplir WCAG AA.

Contraste mínimo.

Focus visible.

ARIA labels.

Navegación teclado.

Mensajes de error asociados al input.

---

# 21. Dark Mode

ThemeProvider obligatorio.

Persistencia del tema.

No invertir imágenes.

Todos los componentes deben tener versión dark.

---

# 22. Componentes obligatorios

components/ui/

Button

Input

Badge

Avatar

Card

Chip

Tag

Skeleton

EmptyState

ThemeToggle

Container

Section

components/layout/

Navbar

Footer

PageHeader

components/search/

SearchInput

SearchFilters

SearchSuggestions

components/teacher/

TeacherCard

TeacherGrid

TeacherProfileHeader

TeacherRating

TeacherAvailability

---

# 23. Librerías aprobadas

Permitidas:

- lucide-react
- clsx
- tailwind-merge
- class-variance-authority
- framer-motion

Toda dependencia nueva requiere justificación en AGENTS.md.

---

# 24. Definition of Done del Frontend

Una pantalla no está terminada hasta que:

- Respeta este Design System.
- Tiene estados loading, empty y error.
- Funciona en mobile, tablet y desktop.
- Tiene Dark Mode.
- Usa componentes reutilizables.
- No tiene colores ni espaciados hardcodeados repetidos.
- Compila con `npm run build`.