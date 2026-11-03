# ADR-034 — Rendering SEO de rutas públicas sin migrar la SPA

**Estado:** Aceptado (2026-11-03). Implementado; verificación de preview de Vercel pendiente.
**Origen:** `SEO-001` / `review2.md` §5.1.
**Relacionado:** `ADR-019` (router en modo librería), `ADR-022` (deploy same-origin en Vercel),
`ARCH-001`, `OPINION-001`, `ADR-033`.

## Contexto

ClaseYa es un directorio: su crecimiento depende de que los perfiles y las búsquedas se indexen y de
que los enlaces compartidos tengan preview. Hoy el frontend es una SPA de Vite con
`react-router` en modo librería. Los metadatos los inyecta `usePageMeta` en el cliente. Google
puede ejecutar JavaScript, pero los crawlers sociales (WhatsApp, Facebook, X, LinkedIn, Slack) no, y
la indexación sin JS queda a merced del render de cada crawler.

`ARCH-001` y `ADR-019` dejaron SSR/prerender y `sitemap.xml` explícitamente fuera de alcance. El
PRD no los exige, pero `review2.md` §5.1 los identifica como el canal natural de crecimiento del
directorio. La pregunta es cómo entregarlos sin reescribir la SPA ni romper el deploy same-origin
de `ADR-022` (cookie `HttpOnly` + CSRF, API proxeada bajo `/api`).

## Decisión

1. **Capa de prerender en Vercel, no migración a metaframework.**
   - `frontend/middleware.ts` (Edge Middleware) intercepta solo `/`, `/buscar` y
     `/profesores/:path*` y reescribe a `/api/seo?path=<ruta+query>`.
   - `frontend/api/seo.ts` es una Vercel Function (Node) que pide los datos públicos al backend,
     arma el HTML con metadatos, JSON-LD y contenido principal, y lo inyecta en el `dist/index.html`
     del deploy.
   - El SPA sigue siendo la aplicación interactiva. React monta sobre `#root` y reemplaza el HTML
     del servidor; no hay hidratación ni se reescriben las pantallas.
2. **Misma respuesta para todos.** No se hace sniffing de `User-Agent`. El crawler y el navegador
   reciben el mismo HTML; la diferencia es que el navegador ejecuta el SPA. Esto evita cloaking.
3. **D1 es la única fuente de visibilidad.** La función confía en `GET /api/teachers/{id}` (404 para
   ocultos) y el sitemap usa `TeacherSpecifications.visible()` / `TeacherVisibilityService`. No se
   duplica la regla.
4. **El sitemap lo sirve el backend.** `GET /sitemap.xml` devuelve un `<sitemapindex>` y
   `/sitemaps/static.xml` + `/sitemaps/teachers-{n}.xml` devuelven `<urlset>` de hasta 10.000 URLs.
   Vercel reescribe esos paths al backend (igual que `/api/*`).
5. **`robots.txt` se genera en el build.** El build del frontend escribe `dist/robots.txt` con
   `Sitemap: <PUBLIC_SITE_URL>/sitemap.xml` y `Disallow` de rutas privadas. No se agrega un endpoint
   de robots.
6. **JSON-LD con hash CSP.** El JSON-LD inline se autoriza con `'sha256-<hash>'` en el `script-src`
   del meta CSP existente; no se usa `'unsafe-inline'` ni un nonce que se reutilice desde una
   respuesta cacheada. La SPA no inyecta JSON-LD; solo elimina el del servidor si la ruta cambió.
7. **Caché CDN con TTL corto.** Perfiles `s-maxage=60`, búsqueda `s-maxage=300`, landing
   `s-maxage=3600`, con `stale-while-revalidate`. No hay purga al publicar/despublicar en esta
   versión.

## Consecuencias

### Positivas

- Los crawlers sociales y los buscadores reciben HTML real, canonical, Open Graph y JSON-LD sin
  migrar a Next/Remix.
- El `sitemap.xml` se genera desde la misma regla D1 que la búsqueda y el detalle; un profesor oculto
  no aparece.
- El deploy same-origin de `ADR-022` no cambia: `/api/*` sigue proxeado y las cookies siguen siendo
  first-party. La capa de SEO no toca auth.
- El blast radius es acotado: no se reescriben componentes, no se cambia el router, no se agregan
  dependencias de UI ni de metaframework.
- El HTML del servidor es un fallback legible si el JS falla.

### Costos y riesgos

- **Duplicación de presentación.** El HTML del servidor se arma con una plantilla propia, no con los
  componentes React. Puede desincronizarse del diseño. Mitigación: solo se renderiza el contenido
  principal y los tests de contrato cubren los campos; el diseño interactivo sigue en React.
- **Ventana de caché y privacidad.** Un profesor oculto puede seguir visible en HTML cacheado hasta
  60 s. La API y el sitemap *generado* lo excluyen de inmediato; la copia CDN del sitemap puede durar
  hasta 24 h, pero solo contiene la URL (sin nombre ni datos) y el perfil responde 404. Se acepta por
  el TTL corto del HTML; si el producto exige revocación instantánea, habrá que purgar el CDN al
  despublicar.
- **Acoplamiento a Vercel.** `middleware.ts` y `api/seo.ts` son específicos de Vercel. El resto del
  frontend sigue siendo portable; migrar de host exigiría reimplementar la capa.
- **Dependencia del backend en el render.** Si `BACKEND_URL` no responde, la función devuelve el
  shell del SPA con metadatos por defecto (200). El SEO degrada, la aplicación no.
- **Acceso al `dist/index.html`.** La función necesita leer el HTML construido. Se resuelve con
  `includeFiles` en `vercel.json` y, si el preview muestra que no está disponible, con un self-fetch
  a `${origin}/index.html` cacheado en módulo. Es un punto a validar en el preview antes de DONE.
- **Dos fuentes de verdad para metadatos.** El servidor (`api/seo.ts`) y `usePageMeta` deben
  coincidir en título, canonical y robots. La lógica del servidor vive en la función autocontenida y
  la del cliente en `frontend/src/seo/`; los tests cubren ambos lados.
- **Costo operativo:** una función y un middleware más. Para tráfico bajo es despreciable; el
  middleware solo matchea tres rutas.

## Alternativas descartadas

- **React Router 7 framework mode / SSR completo.** Es la solución “correcta” a largo plazo, pero
  exige reescribir entrypoint, build, despliegue, providers de auth y E2E en un solo cambio. Se deja
  como migración futura si el producto necesita SSR de rutas privadas o datos por loader.
- **Prerender estático con `react-snap`/`vite-plugin-ssr` en build.** Los perfiles son dinámicos y
  el build de Vercel no tiene acceso garantizado a la base; además, el catálogo cambia sin deploy.
  Generaría HTML obsoleto y tiempos de build crecientes.
- **SSR en Spring Boot con Thymeleaf.** Pondría el render en el backend y obligaría a que el backend
  conozca los assets hasheados del frontend (o a un manifest y un fetch cruzado). Agrega una
  dependencia Java y una dependencia de disponibilidad backend→frontend para la landing.
- **Solo mejorar `usePageMeta` (sin servidor).** No resuelve previews sociales ni indexación sin JS;
  es el estado actual.
- **Sitemap generado en el frontend en build.** No tiene datos; requeriría acceso a la base o una
  API durante el build y quedaría obsoleto entre deploys.

## Implementación (a validar en el preview)

- `frontend/vercel.json`: `functions` con `includeFiles` para `dist/index.html`; rutas
  `/sitemap.xml` y `/sitemaps/(.*)` al backend antes del fallback SPA.
- `frontend/middleware.ts`: `rewrite()` de `@vercel/functions` solo para las tres rutas públicas.
- `frontend/api/seo.ts`: handler Node con timeout de 2 s, `Cache-Control`, headers de seguridad y
  fallback al shell.
- `frontend/api/seo.ts`: handler y renderer autocontenidos (Vercel no bundlea imports fuera de
  `api/`; el primer preview falló con `ERR_MODULE_NOT_FOUND`).
- `frontend/src/seo/`: helpers de cliente (canonical, indexabilidad, robots) y `robots.ts`.
- Backend: `SitemapController` + `SitemapService` en `teacher`, `SecurityConfig` con permitAll para
  los paths públicos y `app.public-url`.
- `robots.txt`: plugin de Vite que escribe `dist/robots.txt`.

## Futuro

Si el producto necesita SEO de rutas privadas, datos por loader o HTML hidratado, evaluar la
migración a React Router framework mode con un ADR que reemplace este. Hasta entonces, esta capa es
un prerender de contenido público, no un segundo frontend.

## Implementación (2026-11-03)

- `frontend/middleware.ts` reescribe `/`, `/buscar` y `/profesores/:path*` a `/api/seo`.
- `frontend/api/seo.ts` renderiza perfiles, búsqueda y home; cachea perfiles 60 s, búsqueda 300 s y
  landing 3600 s; devuelve el shell con `no-store` si el backend falla.
- `frontend/api/seo.ts` centraliza canonical, JSON-LD, escaping, contenido y la inyección con hash
  CSP. `frontend/src/seo/metadata.ts` mantiene los helpers de cliente y `frontend/src/seo/robots.ts`
  genera `robots.txt`.
- Backend: `GET /sitemap.xml`, `/sitemaps/static.xml`, `/sitemaps/teachers-{n}.xml` con
  `TeacherSpecifications.visible()`, `app.public-url` y `SitemapIntegrationTest`.
- Verificación local: backend 438 tests, Vitest 90, Playwright 44/44, `tsc`, ESLint y build con CSP
  en verde. La prueba con `curl`/UA de crawler en un preview de Vercel queda pendiente de deploy.
