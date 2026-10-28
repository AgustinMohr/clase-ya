# ADR-019 — Routing del frontend con React Router (modo librería)

**Estado:** Aceptado (ARCH-001, implementado)
**Fecha:** 2026-10-27

## Contexto

La SPA de ClaseYa navega con un estado interno en `App.tsx` (`type View`) y no cambia la URL.
Consecuencias actuales: al refrescar se pierde la pantalla, no hay deep links ni enlaces
compartibles, los filtros de búsqueda no son restaurables y los crawlers no tienen rutas que
indexar. Para un directorio cuyo crecimiento depende de búsqueda orgánica, la URL es parte del
producto, no un detalle de implementación.

El proyecto no tiene SSR ni framework de metaframework; sirve el frontend como estáticos con Vite.
Migrar a Remix/Next o montar un servidor Node de SSR es una decisión mayor que este cambio no
justifica todavía: primero hay que estabilizar rutas, estado en URL y contratos de navegación.

## Decisión

- Adoptar **`react-router` (v7, modo librería)** como router de la SPA. Es la opción estándar del
  ecosistema, mantiene el historial del navegador, resuelve params/query con APIs probadas y
  habilita una migración posterior a data routers o SSR sin reescribir las pantallas.
- **La URL es la fuente de verdad** de la navegación y del estado de búsqueda. El estado de los
  modales (login, contacto, perfil de estudiante) sigue siendo local y no genera rutas.
- **Lazy loading por página** con `React.lazy` + `Suspense`; la landing no descarga el código de
  rutas privadas.
- **Guardas de ruta de UX** (sesión y rol) que no reemplazan la autorización del backend. Ante
  falta de sesión se conserva la URL y se ofrece el login; ante rol incorrecto se muestra un estado
  de acceso denegado sin llamar a la API.
- **Metadatos por ruta con un hook propio** (`usePageMeta`) que actualiza `document.title`,
  `description` y Open Graph. No se agrega `react-helmet` mientras el alcance sea este.
- **Sin SSR en esta fase.** Se documenta que los previews sociales y el `sitemap.xml` dinámico
  requieren renderizado en servidor y quedan para una spec/ADR posterior.
- **Requisito de despliegue:** el hosting debe reescribir rutas desconocidas a `index.html`
  (fallback SPA). Sin eso, un refresh en una ruta profunda devuelve 404 del servidor.

### Alternativas consideradas

- **Router propio (~100 líneas):** sin dependencia, pero reimplementa parsing de params, encoding,
  historial y restauración de scroll; se reemplazaría igual al migrar a SSR. Rechazado por costo de
  mantenimiento y superficie de bugs.
- **Migrar ahora a Remix/Next:** resolvería SSR y SEO de una vez, pero implica reescribir el
  entrypoint, el build, el despliegue y los E2E en un solo cambio. Se difiere hasta que las rutas y
  el estado en URL estén estables.
- **Solo `history.pushState` con paths:** no aporta params, nested routes ni navegación declarativa;
  es el estado actual con otro nombre.

## Consecuencias

- **+** URLs compartibles y restaurables; atrás/adelante reales; filtros de búsqueda persistentes.
- **+** Base limpia para indexación con JS y para una futura fase de SSR/prerender.
- **+** Chunks por página: el bundle inicial no incluye mensajería ni formularios privados.
- **+** Una dependencia estándar y mantenida, con API estable y amplia documentación.
- **−** Nueva dependencia de frontend (~20 KB gzip en el chunk del router).
- **−** Requiere configurar fallback SPA en el hosting; es un cambio operativo, no de código.
- **−** Los metadatos inyectados en cliente no los ven los crawlers sociales; los previews de
  enlaces siguen pendientes hasta la fase de SSR.
- **−** Las guardas de ruta pueden dar una falsa sensación de seguridad; el backend sigue siendo la
  única autoridad (se documenta explícitamente).

## Futuro

Evaluar en una spec aparte:

1. Prerender/SSR de `/profesores/:id` y `/buscar` para indexación y Open Graph reales.
2. `sitemap.xml` generado desde el backend o en build.
3. Data routers de React Router si se necesita carga de datos por ruta.
