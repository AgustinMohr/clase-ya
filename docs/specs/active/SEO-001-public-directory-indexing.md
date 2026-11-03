# SEO-001 — Directorio indexable: HTML en servidor, sitemap y datos estructurados

**Estado:** Aprobada (2026-11-03) — implementada; verificación de preview de Vercel pendiente.
**Origen:** `review2.md` §5.1 (funcionalidades propuestas).
**Nivel de cambio:** 2 (capacidad nueva, endpoint público nuevo, capa de renderizado y cambio de
despliegue).
**ADR asociado:** `ADR-034-public-directory-seo-rendering.md`.
**Relacionado:** `ARCH-001`, `ADR-019`, `ADR-022`, `OPINION-001`, `ADR-033`.

---

## 1. ID

`SEO-001` — HTML renderizado en servidor para las rutas públicas del directorio, `sitemap.xml`
generado desde la regla de visibilidad D1 y metadatos estructurados (canonical, Open Graph,
JSON-LD).

---

## 2. Objetivo

Que un crawler que **no ejecuta JavaScript** (WhatsApp, Facebook, X, LinkedIn, Slack) y un buscador
que indexa HTML inicial reciban, para `/`, `/buscar` y `/profesores/:id`:

1. el contenido principal de la página,
2. título, descripción, canonical y Open Graph correctos,
3. JSON-LD válido (`WebSite`, `Person`, `Service`, `AggregateRating`, `ItemList`),
4. un `sitemap.xml` que solo incluya profesores visibles (D1).

Hoy todo eso se inyecta en el cliente con `usePageMeta`: Google puede ejecutarlo, pero los previews
sociales y la indexación sin JS no ven nada. El directorio no compone sin esta capa.

---

## 3. Contexto

- `ARCH-001` dejó explícitamente fuera de alcance SSR, prerender, Open Graph real y `sitemap.xml`
  dinámico (`docs/specs/active/ARCH-001-...md` §12).
- `ADR-019` documenta que la SPA usa `react-router` en modo librería y que los metadatos viven en
  `usePageMeta`; la fase de renderizado en servidor quedó como futuro.
- `ADR-022` fija el despliegue: Vercel sirve el SPA estático y proxea `/api/*` al backend Spring
  Boot (`frontend/vercel.json`). La sesión y CSRF dependen de que el SPA y el API compartan origen.
- La visibilidad pública de un profesor es D1: `user.status = ACTIVE` **y** anuncio publicado
  (nombre, bio, ≥1 materia activa, ≥1 modalidad y ≥1 disponibilidad). La regla canónica es
  `TeacherSpecifications.visible()` / `TeacherVisibilityService`. Un profesor oculto responde 404 y
  no debe aparecer en ningún artefacto público.
- Las opiniones ya existen (`OPINION-001`, `ADR-033`) y `teacher_profiles.rating_average/count` se
  recalculan en la misma transacción. Eso habilita `AggregateRating` sin tocar el modelo.
- `frontend/public/robots.txt` es estático y no puede declarar una URL absoluta de sitemap.
- `docs/frontend/DESIGN.md` §17 registra “Sin SSR” como deuda conocida.

### 3.1 Decisión de arquitectura propuesta (detalle en ADR-034)

Se agrega una **capa de prerender en Vercel**, sin migrar la SPA a un metaframework:

1. `frontend/middleware.ts` (Edge Middleware) intercepta `/`, `/buscar` y `/profesores/:id` y
   reescribe a `/api/seo?path=<ruta+query>`.
2. `frontend/api/seo.ts` (Vercel Function, Node) pide los datos públicos al backend, arma un HTML
   con el contenido principal y los metadatos, y lo inyecta en el `dist/index.html` construido.
3. El SPA sigue siendo la capa interactiva: al montar, React reemplaza el contenido del `#root`.
   No hay hidratación ni cambio de componentes.
4. El backend suma `GET /sitemap.xml` (índice) y `GET /sitemaps/**` (chunks) usando la misma regla
   D1, más la configuración `app.public-url`.
5. `robots.txt` se genera en el build con la URL absoluta del sitemap.

**Por qué no React Router 7 framework mode:** resuelve SSR de verdad, pero exige reescribir
entrypoint, build, despliegue, auth y E2E en un solo cambio. La capa de prerender entrega el
beneficio de SEO con un blast radius acotado y deja la migración completa como opción futura.

---

## 4. Actores

| Actor | Rol |
|---|---|
| Visitante | Llega por búsqueda orgánica o un enlace compartido; ve la misma página que en la SPA. |
| Crawler de buscador | Indexa HTML inicial, canonical, JSON-LD y sitemap. |
| Crawler social | Genera previews con Open Graph/Twitter Card sin ejecutar JS. |
| Profesor | Su perfil se publica solo si cumple D1; al ocultarse deja de aparecer en el sitemap. |
| Estudiante | No cambia su flujo: contacto, favoritos y mensajes siguen igual. |
| Operaciones | Configura `BACKEND_URL`, `PUBLIC_SITE_URL` y verifica el deploy de Vercel. |

---

## 5. Requisitos funcionales

### 5.1 Rutas públicas y prerender

- **RF-1 — Rutas indexables:** solo `/`, `/buscar` y `/profesores/:id` se renderizan en servidor y
  se indexan. El resto de las rutas (`/favoritos`, `/mensajes`, `/mi-anuncio`, `/admin*`,
  `/registro`, `/activar`, 404) llevan `robots: noindex` (las privadas, además, `nofollow`).
- **RF-2 — HTML inicial del perfil:** `GET /profesores/:id` devuelve, sin ejecutar JS:
  - `<title>` con el nombre y la ciudad;
  - `<meta name="description">` derivada de la bio (máx. 160 caracteres);
  - `<link rel="canonical">` absoluto;
  - Open Graph (`og:type=profile`, `og:title`, `og:description`, `og:url`, `og:image` si hay foto)
    y Twitter Card;
  - JSON-LD `Person` + `Service` + `AggregateRating` (solo si `ratingCount > 0`) +
    `BreadcrumbList`;
  - contenido principal semántico: nombre, bio, ciudad, precio por hora, modalidades, materias,
    disponibilidad declarada y formación pública;
  - un aviso `noscript` que invita a activar JavaScript para contactar o guardar favoritos.
- **RF-3 — HTML inicial de búsqueda:** `GET /buscar?materia=<término>` devuelve título,
  descripción, canonical, Open Graph y un `ItemList` con hasta 20 resultados de la primera página
  (nombre, URL del perfil, rating si existe, precio y ciudad). Si el término no matchea ninguna
  materia, devuelve la página sin resultados y `noindex,follow`.
- **RF-4 — HTML inicial de la landing:** `GET /` devuelve título, descripción, canonical, Open
  Graph, JSON-LD `WebSite` con `SearchAction` y un resumen de materias populares con enlaces a
  `/buscar?materia=...`.
- **RF-5 — Profesor oculto o inexistente:** la respuesta es **404** con el shell del SPA,
  `noindex,nofollow`, sin nombre, bio ni ningún dato del perfil. No se sirve un 200 con datos
  vacíos (soft 404).

### 5.2 Reglas de indexación

- **RF-6 — Búsqueda indexable:** solo la primera página y solo el parámetro `materia` son
  indexables. Cualquier combinación de `modalidad`, `rating`, `precioMin`, `precioMax`,
  `verificados` o `pagina > 1` devuelve `noindex,follow` y canonical a la URL limpia de la página 1
  (`/buscar?materia=<término>` o `/buscar`).
- **RF-7 — Canonical único:** cada respuesta pública tiene exactamente un
  `<link rel="canonical">` absoluto, sin parámetros de tracking (`utm_*`, `fbclid`, `gclid`) y con
  `materia` normalizada (trim + `encodeURIComponent`). Las páginas `noindex` de búsqueda también
  llevan canonical a la página 1 para consolidar señales.
- **RF-8 — Sin cloaking:** el HTML que recibe un crawler es el mismo que recibe un navegador. No se
  hace sniffing de `User-Agent`; la única diferencia es que el navegador ejecuta el SPA después.
- **RF-9 — Contenido equivalente:** el HTML del servidor contiene el contenido principal que la SPA
  muestra (nombre, bio, materias, modalidades, precio, ciudad, disponibilidad, formación, rating).
  La SPA agrega lo interactivo (contacto, favoritos, lista completa de opiniones); no se oculta
  información al crawler.

### 5.3 Sitemap y robots

- **RF-10 — Índice de sitemaps:** `GET /sitemap.xml` devuelve un `<sitemapindex>` con:
  - `${PUBLIC_SITE_URL}/sitemaps/static.xml`;
  - `${PUBLIC_SITE_URL}/sitemaps/teachers-{n}.xml`, uno por cada bloque de hasta **10.000**
    profesores visibles.
  Si no hay profesores visibles, el índice solo lista el sitemap estático.
- **RF-11 — Sitemap estático:** `GET /sitemaps/static.xml` incluye `/`, `/buscar` y hasta 50
  búsquedas por materia popular (`/buscar?materia=<nombre>`), todas con URL absoluta.
- **RF-12 — Sitemap de profesores:** `GET /sitemaps/teachers-{n}.xml` incluye solo profesores que
  cumplen D1, con `<loc>` absoluto, `<lastmod>` = `teacher_profiles.updated_at` en formato W3C
  (`2026-11-03T12:00:00Z`), `<changefreq>weekly</changefreq>` y `<priority>0.7</priority>`.
  Nunca incluye emails, direcciones, coordenadas ni IDs de usuario.
- **RF-13 — Chunk inválido:** `GET /sitemaps/teachers-0.xml`, un número no numérico o fuera de
  rango devuelve 404 con `ApiError`; no se inventa un chunk vacío.
- **RF-14 — Cabeceras del sitemap:** `Content-Type: application/xml;charset=UTF-8` y
  `Cache-Control: public, max-age=3600, s-maxage=86400`.
- **RF-15 — robots.txt:** se genera en el build con:
  - `User-agent: *`, `Allow: /`;
  - `Disallow: /admin`, `/favoritos`, `/mensajes`, `/mi-anuncio`, `/activar`;
  - `Sitemap: <PUBLIC_SITE_URL>/sitemap.xml` absoluto.
  En desarrollo, el fallback estático no declara sitemap.

### 5.4 Metadatos del cliente

- **RF-16 — Canonical y robots en la SPA:** `usePageMeta` acepta `canonical` y `robots`; actualiza
  `<link rel="canonical">`, `<meta name="robots">`, `og:type`, `og:site_name`, `og:locale` y
  Twitter Card. Las rutas no indexables se marcan automáticamente `noindex,nofollow`.
- **RF-17 — JSON-LD:** el JSON-LD se renderiza **solo en el HTML del servidor**. La SPA no inyecta
  JSON-LD (evita debilitar CSP). Si el usuario navega a otra ruta, `usePageMeta` elimina el
  `script[data-seo-jsonld]` cuyo `data-seo-path` no coincide con la ruta actual, para no dejar datos
  estructurados obsoletos en el DOM.
- **RF-18 — Compatibilidad:** `usePageMeta` sigue exportando `DEFAULT_DESCRIPTION` y
  `metaDescription` para no romper imports existentes; los tests actuales de routing siguen verdes.

### 5.5 Resiliencia

- **RF-19 — Fallback:** si el backend no responde en 2 s o devuelve 5xx, la función devuelve el
  shell del SPA con los metadatos por defecto y estado 200 (fail open). Si el `dist/index.html` no se
  puede leer ni por `includeFiles` ni por self-fetch, devuelve un documento mínimo con los metadatos
  y el contenido público. El usuario nunca ve una pantalla de error de Vercel por un fallo de SEO.
- **RF-20 — Caché:** perfiles `Cache-Control: public, s-maxage=60, stale-while-revalidate=300`;
  búsqueda `s-maxage=300`; landing `s-maxage=3600`. La ventana de 60 s en perfiles es el costo
  aceptado por cachear; un profesor oculto puede seguir visible en HTML cacheado hasta 60 s, pero la
  API y el sitemap ya lo excluyen.
- **RF-21 — Sin cookies:** las respuestas prerenderizadas no leen ni escriben cookies, no
  comparten sesión y no varían por usuario. `Vary` no es necesario.

---

## 6. Requisitos no funcionales

- **Rendimiento:** p95 de la función < 500 ms con backend caliente; timeout de 2 s por fetch;
  máximo 3 fetches por request (perfil: 1; búsqueda: 2; landing: 1).
- **Escalabilidad del sitemap:** consulta paginada de 10.000 filas por chunk con la `Specification`
  D1 canónica; sin N+1. El índice hace un `count` y no carga entidades.
- **Memoria:** la función lee `dist/index.html` una vez por instancia (cache en módulo) y no
  persiste datos de usuarios.
- **Accesibilidad:** el HTML del servidor usa `main`, `article`, `h1`, `h2`, listas y enlaces
  semánticos; es legible sin CSS y sin JS.
- **Observabilidad:** log estructurado con `path`, `status`, `durationMs` y `error` (sin PII).
- **Determinismo:** los tests no dependen de la red; el renderer puro se testea con fixtures.

---

## 7. Seguridad

- **D1 manda:** el prerender y el sitemap usan la misma regla de visibilidad. Un profesor con
  `user.status != ACTIVE` o anuncio incompleto no se renderiza ni aparece en el sitemap.
- **404 real:** un perfil oculto no filtra nombre, bio, materias ni coordenadas; la respuesta 404 no
  incluye datos.
- **Sin PII:** el HTML y el JSON-LD solo usan campos del DTO público (`displayName`, `bio`,
  `city`, `photoUrl`, `pricePerHour`, `ratingAverage`, `ratingCount`, `modalities`, `subjects`,
  `education`, `availabilityNote`). Nunca `email`, `address`, `latitude`, `longitude`, `userId`
  ni datos de estudiantes o conversaciones.
- **CSP:** el JSON-LD inline se autoriza con `'sha256-<hash>'` calculado sobre el texto exacto y
  agregado a `script-src` del meta CSP existente. No se usa `'unsafe-inline'`; el check de CSP del
  build sigue pasando. La función agrega los headers de seguridad que `_headers` no aplica a
  funciones (`X-Content-Type-Options`, `X-Frame-Options: DENY`,
  `Content-Security-Policy: frame-ancestors 'none'`, `Referrer-Policy` y `Permissions-Policy`).
- **Escapado:** todo texto se escapa para HTML y atributos; el JSON-LD se serializa con
  `JSON.stringify` y se escapan `<`, `>` y `&` para que no cierre el `<script>`.
- **Sin open redirect:** el middleware solo reescribe rutas conocidas; la función valida que
  `path` sea `/`, `/buscar` o `/profesores/<uuid>` y no acepta URLs absolutas.
- **Backend de confianza:** la función solo hace fetch a `${BACKEND_URL}` configurado; no acepta
  un host desde el request.
- **Canonical sin host injection:** en producción se usa `PUBLIC_SITE_URL`; si falta, se usa el
  origen del request solo en previews. El canonical no se construye con un `Host` arbitrario.

---

## 8. Invariantes

- **I1:** todo profesor visible en el sitemap o en el HTML prerenderizado cumple D1.
- **I2:** todo profesor oculto responde 404 y no aparece en ningún artefacto público.
- **I3:** cada página pública tiene como máximo un canonical y un `robots`.
- **I4:** el HTML del servidor y el de la SPA representan la misma entidad (mismo `teacherId`,
  mismo canonical).
- **I5:** el sitemap es XML bien formado y no supera 50.000 URLs por archivo.
- **I6:** ninguna respuesta de SEO incluye cookies, sesión, email, dirección, coordenadas ni IDs
  internos.
- **I7:** el SPA sigue funcionando si la capa de SEO falla; el fallback es el shell estático.

---

## 9. API contract

### 9.1 `GET /sitemap.xml` (público)

```
200 Content-Type: application/xml;charset=UTF-8
Cache-Control: public, max-age=3600, s-maxage=86400

<?xml version="1.0" encoding="UTF-8"?>
<sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <sitemap>
    <loc>https://claseya.example/sitemaps/static.xml</loc>
  </sitemap>
  <sitemap>
    <loc>https://claseya.example/sitemaps/teachers-1.xml</loc>
    <lastmod>2026-11-03T12:00:00Z</lastmod>
  </sitemap>
</sitemapindex>
```

- Sin autenticación ni CSRF (GET público).
- `PUBLIC_SITE_URL` se toma de `app.public-url` (`${APP_BASE_URL:http://localhost:5173}`).

### 9.2 `GET /sitemaps/static.xml` (público)

```
200 application/xml
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <url><loc>https://claseya.example/</loc><changefreq>daily</changefreq><priority>1.0</priority></url>
  <url><loc>https://claseya.example/buscar</loc><changefreq>weekly</changefreq><priority>0.8</priority></url>
  <url><loc>https://claseya.example/buscar?materia=Matem%C3%A1tica</loc><changefreq>weekly</changefreq><priority>0.6</priority></url>
</urlset>
```

### 9.3 `GET /sitemaps/teachers-{page}.xml` (público)

```
200 application/xml
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <url>
    <loc>https://claseya.example/profesores/{uuid}</loc>
    <lastmod>2026-11-03T12:00:00Z</lastmod>
    <changefreq>weekly</changefreq>
    <priority>0.7</priority>
  </url>
</urlset>
```

- `page` empieza en 1; cada chunk tiene hasta 10.000 URLs.
- 404 si `page` es inválida o supera el total.

### 9.4 Sin cambios en la API existente

`GET /api/teachers`, `GET /api/teachers/{id}`, `GET /api/subjects`, `GET /api/subjects/popular` y
los DTOs públicos se reutilizan tal cual. La capa de SEO no agrega endpoints bajo `/api`.

---

## 10. Datos afectados

- **Sin migración.** No se agregan tablas, columnas ni índices.
- Se reutilizan `teacher_profiles.updated_at`, `teacher_profiles.rating_average/count` y la
  `Specification` D1.
- Se agrega configuración `app.public-url` (env `APP_BASE_URL`, default local) y `PUBLIC_SITE_URL`
  en Vercel para el canonical y el sitemap.

---

## 11. Criterios de aceptación

- **AC-1 (RF-2):** `curl -A "Twitterbot" /profesores/{id}` devuelve 200, `<title>`, description,
  canonical, `og:*`, JSON-LD `Person`/`Service` y el contenido principal sin ejecutar JS.
- **AC-2 (RF-2, RF-5):** un profesor suspendido o con anuncio despublicado devuelve 404, sin nombre
  ni bio, con `noindex`; no aparece en `/sitemap.xml` ni en sus chunks.
- **AC-3 (RF-3, RF-6):** `/buscar?materia=matematica` devuelve `ItemList` y canonical limpio;
  `/buscar?materia=matematica&modalidad=ONLINE` devuelve `noindex,follow` y canonical a la URL
  limpia.
- **AC-4 (RF-4):** `/` devuelve `WebSite` + `SearchAction` y enlaces de materias populares.
- **AC-5 (RF-10/11/12):** el sitemap índice lista el sitemap estático y los chunks necesarios; el
  chunk de profesores solo contiene UUIDs visibles y `lastmod` correcto; el XML parsea.
- **AC-6 (RF-13/14):** `/sitemaps/teachers-0.xml` y `/sitemaps/teachers-999.xml` devuelven 404;
  todos los sitemaps devuelven `application/xml` y el `Cache-Control` esperado.
- **AC-7 (RF-15):** `dist/robots.txt` contiene el sitemap absoluto y los `Disallow` de rutas
  privadas.
- **AC-8 (RF-16/18):** navegar de un perfil a otro actualiza canonical y `robots`; los tests
  existentes de `usePageMeta`/routing siguen verdes.
- **AC-9 (RF-17):** el HTML prerenderizado tiene JSON-LD con hash CSP válido; el build no contiene
  scripts inline ni `unsafe-inline`; `scripts/check-csp.mjs` pasa.
- **AC-10 (RF-19):** con `BACKEND_URL` inalcanzable, la función devuelve el shell del SPA (o el
  documento mínimo si el template no está disponible) con 200 y metadatos por defecto; no hay 500.
- **AC-11 (RF-20):** las respuestas llevan el `Cache-Control` definido; no incluyen `Set-Cookie`
  ni `Vary` por usuario.
- **AC-12 (seguridad):** un test escanea el HTML y el sitemap y no encuentra emails, `address`,
  `latitude`, `longitude` ni `userId`.
- **AC-13 (regresión):** Playwright completo en verde; Vitest completo en verde; `tsc --noEmit`,
  ESLint y `npm run build` en verde; `mvn -B clean test` en verde.

---

## 12. Out of scope

- Migración completa a React Router 7 framework mode o SSR con hidratación.
- Renderizado en servidor de rutas privadas o personalizadas.
- Páginas de opiniones individuales, perfiles de estudiante o listados de conversaciones.
- Páginas programáticas por ciudad/materia (`/profesores/matematica/santa-fe`) o `hreflang`.
- Purga de CDN al publicar/despublicar un profesor (la ventana es el TTL de 60 s).
- Generación de imágenes Open Graph; se usa `photoUrl` si existe y se omite si no.
- Cambios en el ranking o búsqueda difusa (`review2.md` §5.2).
- Envío de sitemaps a Search Console, analítica o experimentos.
- Nuevos índices o migraciones; si el sitemap necesita optimización, va en otra spec.

---

## 13. Definition of Done

- [x] Spec y `ADR-034` aprobados por el director/arquitecto (2026-11-03).
- [x] `GET /sitemap.xml`, `/sitemaps/static.xml` y `/sitemaps/teachers-{n}.xml` implementados con
      tests de integración (visibilidad D1, XML, 404, cache headers).
- [x] `SecurityConfig` permite los sitemaps sin autenticación.
- [x] Renderer del servidor en `frontend/api/seo.ts` (autocontenido) + helpers de cliente en
      `frontend/src/seo/` con tests Vitest (JSON-LD, escaping, canonical, indexabilidad, sin PII).
- [ ] `frontend/api/seo.ts` + `frontend/middleware.ts` + configuración de Vercel probados en un
      preview con `curl`/UA de crawler. **Pendiente: no hay preview deployment en esta sesión.**
- [x] `usePageMeta` con canonical/robots y tests; JSON-LD obsoleto eliminado en navegación.
- [x] `robots.txt` generado en el build con URL absoluta.
- [x] `scripts/check-csp.mjs` en verde; JSON-LD con hash y sin `unsafe-inline`.
- [x] Documentación actualizada: `README.md` (deploy), `docs/frontend/DESIGN.md` (§17),
      `ADR-019` (futuro), `docs/architecture/security.md` y `docs/testing/testing-strategy.md`.
- [x] Verificación local: `mvn -B clean test` (438), `npm test` (90), `npx tsc --noEmit`,
      `npm run lint`, `npm run build` y Playwright **44/44**. La prueba con `curl`/UA de crawler en
      un preview de Vercel sigue pendiente de deploy.
- [x] Sin migración, sin cambios de contrato en endpoints existentes y sin modificar la SPA más
      allá de metadatos.

## 14. Implementación (2026-11-03)

- Backend: `SitemapController` + `SitemapService` en `teacher`, `SecurityConfig` con permitAll para
  `/sitemap.xml` y `/sitemaps/**`, `app.public-url` (`APP_BASE_URL`). `SitemapIntegrationTest`
  (6 tests) cubre D1, chunking, 404, XML y cache.
- Renderer del servidor: `api/seo.ts` autocontenido (handler + HTML/JSON-LD, hash CSP);
  helpers de cliente en `src/seo/metadata.ts`; `src/seo/robots.ts`; tests en `src/seo/*.test.ts`.
- `api/seo.ts` no importa módulos fuera de `api/`: Vercel transpila la función sin bundlear imports
  externos, y hacerlo produjo `ERR_MODULE_NOT_FOUND` en el primer preview.
- Vercel: `middleware.ts` (rewrite de las tres rutas públicas), `api/seo.ts` (fetch al backend,
  render, fallback fail-open), `vercel.json` (includeFiles + rutas de sitemap).
- SPA: `usePageMeta` actualiza canonical/robots/OG/Twitter y elimina JSON-LD obsoleto;
  `SearchPage` y `TeacherProfilePage` ajustan `robots` para resultados filtrados/404.
- `robots.txt`: plugin de Vite escribe `dist/robots.txt` con `VITE_SITE_URL` o
  `VERCEL_PROJECT_PRODUCTION_URL`.
- Dependencias nuevas: `@vercel/functions` (runtime del middleware) y `@types/node` (tipos del
  handler, dev). `@vercel/node` se descartó: el handler usa una vista estructural de
  request/response y no necesita ese paquete.
