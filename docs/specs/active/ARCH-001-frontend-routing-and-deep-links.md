# ARCH-001 — Routing real, deep links y metadatos por página

**Estado:** Aprobada e implementada (35/35 E2E verdes; pendiente de commit)
**Nivel de cambio:** 2 (decisión de arquitectura + dependencia nueva de frontend)
**Origen:** hallazgo `ARCH-01` de `review.md`
**ADR asociado:** `ADR-019-frontend-routing.md`

---

## 1. ID

`ARCH-001` — Navegación con URLs reales para la SPA de ClaseYa.

---

## 2. Objetivo

Que cada pantalla del frontend tenga una URL estable, compartible y restaurable al recargar, con
navegación de historial real y metadatos por página, sin migrar todavía a SSR.

---

## 3. Contexto

- `frontend/src/App.tsx` mantiene la vista en un estado interno (`type View`) y usa
  `history.pushState` solo para que los botones atrás/adelante no salgan de la app. La URL nunca
  cambia.
- Al refrescar se vuelve a la landing; no existen enlaces profundos a `/profesores/:id`,
  `/buscar?...`, `/favoritos`, `/mensajes` ni `/mi-anuncio`.
- Un directorio depende de búsqueda orgánica y de enlaces compartibles: sin URLs no se pueden
  indexar perfiles, medir funnels por ruta ni enviar deep links desde emails o campañas.
- `docs/frontend/DESIGN.md` §12 y §17 documentan explícitamente “sin router” como estado actual.
- El backend ya expone perfiles públicos y responde 404 para profesores no visibles
  (`TeacherSpecifications.visible()`); esa regla no cambia en esta spec.
- Los crawlers de Google ejecutan JavaScript, pero los previews sociales (WhatsApp, Facebook, X)
  no. Por eso los metadatos dinámicos de esta fase **no garantizan previews sociales**; eso
  requiere renderizado en servidor y se difiere a una spec posterior.

---

## 4. Actores

| Actor | Rol |
|---|---|
| Visitante | Llega por un enlace profundo o busca desde la landing. |
| Estudiante | Accede a favoritos y mensajes por URL; comparte perfiles. |
| Profesor | Accede a “Mi anuncio” por URL. |
| Buscador | Indexa rutas y lee `title`/`description` renderizados con JS (Google). |
| Operaciones | Publica el frontend con fallback SPA configurado. |

---

## 5. Requisitos funcionales

- **RF-1 — Tabla de rutas:** la app define exactamente estas rutas:
  | Ruta | Pantalla | Acceso |
  |---|---|---|
  | `/` | Landing | Público |
  | `/buscar` | Búsqueda y resultados | Público |
  | `/profesores/:teacherId` | Perfil público del profesor | Público |
  | `/favoritos` | Mis favoritos | Solo STUDENT autenticado |
  | `/mensajes` | Lista de conversaciones | Cualquier usuario autenticado |
  | `/mensajes/:conversationId` | Conversación abierta | Cualquier usuario autenticado |
  | `/mi-anuncio` | Mi anuncio (perfil de profesor) | Solo TEACHER autenticado |
  | `*` | No encontrada | Público |
- **RF-2 — URL estable:** toda navegación interna actualiza `location.pathname`/`search` sin
  recargar el documento. Refrescar la URL reproduce la misma pantalla.
- **RF-3 — Historial:** atrás/adelante del navegador recorren las pantallas visitadas y restauran
  sus parámetros. Volver desde un perfil a la búsqueda conserva término, filtros y página.
- **RF-4 — Deep links:** entrar directo a cualquier ruta pública o privada funciona. Un
  `teacherId` inexistente, inválido o no visible muestra el estado “Perfil no disponible” dentro
  de `/profesores/...`, sin redirigir ni filtrar datos.
- **RF-5 — Estado de búsqueda en la URL:** los parámetros son `materia` (texto), `modalidad`
  (`ONLINE` \| `IN_PERSON`), `rating` (`4` \| `4.5`), `precioMin`, `precioMax` (enteros ≥ 0),
  `verificados` (`true`) y `pagina` (entero ≥ 1). Los parámetros se validan y coercionan; valores
  inválidos o desconocidos se ignoran y no se reenvían a la API.
- **RF-6 — Escritura de la URL:** “Aplicar filtros”, “Limpiar”, el submit del buscador y la
  paginación actualizan la URL con `push`. Escribir en el buscador sin enviar no toca la URL.
- **RF-7 — La URL manda:** al montar `/buscar` o al volver con atrás/adelante, la búsqueda se
  ejecuta con los parámetros de la URL, no solo con el término.
- **RF-8 — Rutas privadas sin sesión:** se renderiza un estado “Iniciá sesión” y se abre el modal
  de login, conservando la URL. Tras autenticarse, la misma pantalla carga sus datos.
- **RF-9 — Rol incorrecto:** un TEACHER en `/favoritos` o un STUDENT en `/mi-anuncio` ve un estado
  “No tenés acceso a esta sección” y no se ejecuta ninguna llamada a la API de esa pantalla.
- **RF-10 — Logout:** si la sesión termina estando en una ruta privada, se redirige a `/`.
- **RF-11 — Metadatos:** cada ruta define `document.title` y `<meta name="description">`; el
  perfil de profesor agrega `og:title`, `og:description`, `og:url` y `og:image` con datos públicos
  una vez cargado. La búsqueda incluye la materia en el título. Nunca se incluyen emails, tokens
  ni datos privados.
- **RF-12 — Lazy loading:** cada página se carga con `React.lazy` + `Suspense`, con un fallback de
  skeletons consistente con el diseño. La landing no debe descargar el código de mensajería,
  formularios o “Mi anuncio” en el bundle inicial.
- **RF-13 — Scroll:** cambiar de pathname lleva al top; cambiar solo query params (filtros o
  página) conserva la posición.
- **RF-14 — Navegación interna:** logo, navbar, footer, tarjetas y “volver” usan navegación del
  router, sin recarga completa. Las acciones que abren modales (perfil, contacto, login) siguen
  siendo estado local, no rutas.
- **RF-15 — No encontrada:** cualquier ruta desconocida muestra una página 404 de cliente con
  enlace al inicio y título propio.
- **RF-16 — `robots.txt`:** se publica un `robots.txt` estático que permite el rastreo. El
  `sitemap.xml` dinámico queda fuera de alcance (requiere datos del backend en build o servidor).
- **RF-17 — Compatibilidad:** se preservan los flujos existentes (favoritos con 409 → perfil,
  contacto, alta de perfil, tema, Google login) y los 25 escenarios E2E actuales.

---

## 6. Requisitos no funcionales

- **Rendimiento:** la navegación interna no recarga el documento; el bundle inicial no crece con
  el código de todas las páginas (chunks por página).
- **Accesibilidad:** el cambio de ruta actualiza el título y no rompe el foco; los enlaces usan
  elementos semánticos (`<a>`/`Link`) y conservan los `aria-label` actuales.
- **Determinismo:** los tests E2E no dependen de tiempos arbitrarios; usan `expect` con locators.
- **Compatibilidad de despliegue:** el hosting del frontend debe reescribir rutas desconocidas a
  `index.html` (fallback SPA). Se documenta como requisito operativo; sin él, un refresh en
  `/profesores/:id` devuelve 404 del servidor.
- **Sin cambios de backend:** no se agregan endpoints, campos ni migraciones.

---

## 7. Seguridad

- Las rutas privadas son **UX**, no autorización: la API sigue siendo la autoridad y responde
  401/403/404. Ninguna pantalla protegida debe mostrar datos antes de validar sesión y rol.
- La visibilidad de profesores sigue saliendo del backend; una URL directa a un profesor oculto
  devuelve 404 y la UI muestra “Perfil no disponible” sin datos.
- No se aceptan destinos de redirección desde query params (sin open redirect). Las redirecciones
  son solo entre rutas internas conocidas.
- Los parámetros de búsqueda están en whitelist; no se concatenan en SQL ni se reenvían crudos.
- La URL y los metadatos no contienen PII (el UUID público del profesor ya es parte del contrato
  de la API pública).
- No se persiste el JWT en `localStorage` ni en la URL.

---

## 8. Invariantes

- La URL visible siempre representa la pantalla actual (salvo modales transitorios).
- Ninguna ruta privada renderiza datos sin sesión y rol válidos.
- Un profesor no visible nunca se vuelve accesible por conocer su URL.
- Atrás/adelante nunca expulsa de la SPA ni deja una pantalla en estado inconsistente.
- Los parámetros inválidos nunca producen un error 500 del cliente ni una consulta con valores
  arbitrarios.

---

## 9. API contract

Sin cambios. Se reutilizan `GET /api/teachers`, `GET /api/teachers/{id}`,
`GET /api/availability?teacherId=`, `GET /api/favorites`, `GET /api/conversations` y
`GET /api/conversations/{id}`.

---

## 10. Datos afectados

Ninguno. Sin migraciones ni cambios de esquema.

---

## 11. Criterios de aceptación

- **AC-1 (RF-1/2):** abrir `/`, `/buscar`, `/profesores/:id`, `/favoritos`, `/mensajes` y
  `/mi-anuncio` (con el rol correspondiente) renderiza la pantalla correcta; un refresh la
  mantiene.
- **AC-2 (RF-3):** buscar, filtrar, paginar, entrar a un perfil y volver con el botón atrás
  restaura término, filtros y página.
- **AC-3 (RF-4):** entrar directo a `/profesores/{uuid}` muestra el perfil; a un UUID inexistente
  muestra “Perfil no disponible” conservando la URL.
- **AC-4 (RF-5/6/7):** `/buscar?materia=matematica&modalidad=ONLINE&rating=4&precioMin=1000&precioMax=5000&verificados=true&pagina=2`
  ejecuta esa búsqueda; `rating=abc`, `pagina=-3` y `modalidad=FOO` se ignoran sin romper.
- **AC-5 (RF-4/RF-17):** un profesor oculto responde 404 en la API y la UI no muestra su tarjeta ni
  datos por URL directa.
- **AC-6 (RF-8):** visitar `/favoritos` sin sesión abre el login y conserva la URL; tras login se
  ven los favoritos.
- **AC-7 (RF-9):** TEACHER en `/favoritos` y STUDENT en `/mi-anuncio` ven el estado de acceso
  denegado sin requests a esas APIs.
- **AC-8 (RF-10):** cerrar sesión desde `/mensajes` lleva a `/`.
- **AC-9 (RF-11):** el título de `/` es el actual; el de un perfil incluye el nombre del profesor;
  el de `/buscar?materia=...` incluye la materia; `/favoritos` no expone emails.
- **AC-10 (RF-12):** `npm run build` genera chunks separados por página.
- **AC-11 (RF-15):** `/ruta-inexistente` muestra la 404 de cliente con enlace al inicio.
- **AC-12 (RF-17):** los 25 escenarios Playwright existentes siguen verdes, más los nuevos de
  routing.

---

## 12. Out of scope

- SSR, prerender o SSG (Remix/Next o servidor Node propio). Requiere ADR y decisión de despliegue.
- Previews sociales con Open Graph reales (dependen de HTML renderizado en servidor).
- `sitemap.xml` dinámico con perfiles de profesores.
- Analítica, experimentos o tracking por ruta.
- Cambios en la API de búsqueda (fuzzy search, filtros nuevos).
- Persistencia de sesión en `localStorage` o cookies.
- Rediseño visual de cualquier pantalla.

---

## 13. Definition of Done

- [x] Spec y ADR-019 aprobados por el humano.
- [x] Rutas implementadas con `react-router` (modo librería) y lazy loading por página.
- [x] Búsqueda serializada en query params con whitelist y validación.
- [x] Guardas de sesión/rol en rutas privadas sin filtrar datos.
- [x] Metadatos por ruta y por perfil cargado.
- [x] Tests E2E nuevos para deep links, historial, estado en URL, guardas y 404.
- [x] `npm run build` y `npx tsc --noEmit` en verde; suite Playwright completa en verde (35/35).
- [x] `docs/frontend/DESIGN.md` (§12 y §17) y `README.md` actualizados.
- [x] Sin cambios de backend, API ni base de datos.
