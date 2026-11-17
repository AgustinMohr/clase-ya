# 03 — Auditoría de Frontend y Seguridad del Cliente

- Fecha: 2026-11-17
- Alcance: `frontend/` (React 18.3, TypeScript 5.6 strict, Vite 8, Tailwind 4, react-router 7), `frontend/api/seo.ts`, `frontend/middleware.ts`, `frontend/vercel.json`, `frontend/public/_headers`.
- Método: lectura estática. No se modificó código. Se ejecutó solo `npm audit --omit=dev` (lectura, sin cambios). No se ejecutaron tests, `tsc`, lint ni build; no se midió Lighthouse ni contraste real.
- Clasificación: **CONFIRMADO** (evidencia directa en código/config), **PROBABLE** (deducido, falta prueba en entorno real), **PENDIENTE** (requiere ver Vercel/producción).
- Prioridades: P0 bloquea salida a producción; P1 corregir antes de abrir tráfico; P2 corto plazo; P3 mejora.

## 1. Resumen ejecutivo

El frontend es de buena calidad para un proyecto de este tamaño. Puntos fuertes verificados:

- Sin `dangerouslySetInnerHTML`, `innerHTML`, `eval`, `document.cookie` ni `any` explícito en código de producción (grep sobre `src`).
- JWT nunca accesible a JavaScript: cookie HttpOnly, CSRF double-submit con reintento, `credentials: 'include'`, sin `localStorage` de credenciales (`api.ts:398-515`).
- Timeout global de 15 s, errores de red normalizados a `ApiError(0)`.
- CSP estricta de producción sin `unsafe-inline`/`unsafe-eval` en `script-src`, verificada en build por `scripts/check-csp.mjs`.
- Lazy loading por ruta (`App.tsx:19-35`), build sin source maps (0 `.map` en `dist/assets`), bundle principal 190 KB sin comprimir.
- `npm audit --omit=dev`: 0 vulnerabilidades. CI ejecuta lint, test, build y `npm audit --audit-level=high` (`ci.yml:37-55`).
- 30 archivos de test unitario y 16 specs Playwright.

Resultado: **0 P0, 2 P1, 7 P2, 8 P3**. Ninguna vulnerabilidad explotable remotamente por sí sola. Los P1 son: cabeceras de seguridad que probablemente no se aplican en Vercel, y la URL de foto de perfil controlada por el profesor que carga recursos de terceros en el navegador de cada visitante.

Veredicto: apto para beta controlada. Antes de tráfico real: F-01 y F-02.

## 2. Arquitectura

| Capa | Implementación | Evaluación |
|---|---|---|
| Entrada | `main.tsx`: `BrowserRouter > ThemeProvider > HumanGate > AuthProvider > ToastProvider > ErrorBoundary > App` | Orden correcto. `ErrorBoundary` queda dentro de `AuthProvider`: un error en el propio provider no se captura. |
| Estado de sesión | `AuthContext.tsx` (Context + `/api/auth/me`) | Simple y suficiente. `value` se recrea con `useMemo` pero `login`/`loginWithGoogle` son funciones nuevas en cada cambio de `user`. |
| Servicios HTTP | `api.ts` (889 líneas): tipos + cliente + ~80 métodos en un solo archivo | Funciona; es el archivo más grande y mezcla contratos, transporte y endpoints. |
| Rutas | `App.tsx` (431 líneas) + `ProtectedRoute` | `App` concentra favoritos, perfil de estudiante, login y contacto: 12 `useState`. Es el acoplamiento más fuerte. |
| Hooks | `useCatalog`, `useTeacherListing` (337 líneas), `useAcademicCascade`, `usePageMeta`, `useBack` | Buena separación en `useTeacherListing`. |
| Páginas grandes | `MessagesPage` 670, `AdminVerificationsPage` 613, `SearchPage` 432 | Candidatas a dividir (ver F-14). |
| Design system | `components/ui/*` (Button, Field, Modal, primitives…) | Consistente. `primitives.tsx` (297 líneas) agrupa muchos componentes. |
| SEO | `api/seo.ts` (función Vercel) + `middleware.ts` + `seo/metadata.ts` | Escapa HTML (verificado en auditoría 01). |

Código duplicado observado:

- `validateRegistration` y `validatePasswordReset` repiten la regla de contraseña (`lib/validation.ts:19-21` y `:42-44`). Extraer `passwordError()`.
- Los métodos `adminReports`, `adminReviews`, `adminVerificationQueue` y `adminUsers` repiten la construcción de `URLSearchParams` con page/size/status (`api.ts:~560-860`).
- Tipos casi idénticos: `VerificationCredential` y `AdminCredentialView`; `Conversation` y `ConversationSummary`.
- La unión `'PENDING' | 'UNDER_REVIEW' | 'VERIFIED' | 'REJECTED' | 'MORE_INFO_REQUIRED'` está escrita en línea en `TeacherSummary.verificationStatus` (`api.ts:19`) y como `VerificationStatus` (`api.ts:~190`). El alias existe pero no se reutiliza.

## 3. Hallazgos P0–P3

### P0
Ninguno.

### P1

**F-01 (REFUTADO el 2026-11-17 por verificación del propietario) — Cabeceras de seguridad de `public/_headers` no se aplican en Vercel.**
Resultado de `curl -sI` en producción: `Content-Security-Policy: frame-ancestors 'none'`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin` y `Strict-Transport-Security: max-age=63072000; includeSubDomains; preload` están presentes. La hipótesis original (abajo) queda como histórico; no es un hallazgo vigente. `Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=()` también confirmado en una segunda salida completa. Sin pendientes.
Evidencia: `frontend/public/_headers` define `X-Frame-Options: DENY`, `Content-Security-Policy: frame-ancestors 'none'`, `X-Content-Type-Options`, `Permissions-Policy`. El formato `_headers` es de Netlify/Cloudflare Pages; Vercel lo ignora y lo sirve como archivo estático. `vercel.json` solo define `routes` y `functions`, sin `headers` (y Vercel no permite mezclar `routes` con `headers`; haría falta migrar a `rewrites`). El comentario de `vite.config.ts:8-11` asume que `_headers` aplica `frame-ancestors`, porque un `<meta>` CSP no puede imponerlo.
Impacto: la SPA puede embeberse en un iframe (clickjacking sobre login/contacto). El backend sí envía `X-Frame-Options: DENY` en sus respuestas (`SecurityConfig.java:64`), pero eso no cubre el HTML servido por Vercel. Además el CSP completo solo vive en `<meta>`, así que no hay `report-uri`, ni `frame-ancestors`, ni `Strict-Transport-Security` propio (Vercel añade HSTS por defecto en dominios `vercel.app`; no verificado para dominio propio).
Verificación pendiente: `curl -sI https://<dominio>/` y revisar si aparecen `x-frame-options` y `content-security-policy`.
Recomendación: pasar a `rewrites` + `headers` en `vercel.json`, o configurar headers desde el dashboard. Nivel 1.

**F-02 (P1, CONFIRMADO) — `photoUrl` arbitraria se renderiza como `<img>` para todos los visitantes.**
Evidencia: el profesor escribe cualquier texto en `ProfileSection.tsx:39`; `Avatar` hace `<img src={src}>` sin `referrerPolicy` ni `crossOrigin` (`primitives.tsx:161-166`); la CSP permite `img-src https:` (`vite.config.ts:41`); `TeacherProfilePage.tsx:43` la usa además como `og:image`.
Impacto: un profesor puede apuntar a un servidor propio y registrar IP, user-agent y momento de cada visitante o alumno que ve su tarjeta (píxel de seguimiento), o servir contenido inapropiado/enorme. No es XSS. Se cruza con el hallazgo S-06 de la auditoría 01.
Verificación pendiente: validación backend de `photoUrl` (esquema, dominio, longitud); no auditada en esta pasada.
Recomendación: allowlist de hosts o subida propia con almacenamiento; mínimo `referrerPolicy="no-referrer"` y `https` obligatorio. Cambia contrato/comportamiento → Nivel 2.

### P2

**F-03 (P2, CONFIRMADO) — Subida de documentos con timeout de 15 s.**
`requestForm` usa `send` → `fetchWithTimeout` con `REQUEST_TIMEOUT_MS = 15_000` (`api.ts:~418-445`). Un PDF de hasta 10 MB por una red móvil lenta supera 15 s con facilidad; el usuario ve "El servidor tardó demasiado en responder" aunque el servidor pueda terminar de guardarlo. Resultado: reintento → documento duplicado (el límite de 5 docs/25 MB por credencial se consume). Recomendación: timeout específico para multipart (60-120 s), progreso visible y comprobación previa de tamaño en cliente (`VerificationPanel.tsx` no valida tamaño ni tipo antes de enviar; confirmar leyendo `:80-110`).

**F-04 (P2, CONFIRMADO) — Reintento automático de cualquier 403 en escrituras.**
`request` (`api.ts:~490-497`) reintenta toda escritura que devuelve 403, no solo las fallas CSRF. Un 403 legítimo de autorización (p. ej. ADMIN contra `/api/conversations`) duplica la petición y un segundo round-trip a `/csrf`. Es idempotente en la práctica (el primer intento fue rechazado) pero ensucia logs y rate limits (cuenta dos veces contra el bucket POST). Recomendación: reintentar solo si el cuerpo/cabecera identifica fallo CSRF, o limitar a un reintento por token.

**F-05 (P2, CONFIRMADO) — Timeout de escritura sin idempotencia.**
Si un POST (registro, mensaje, reseña, reporte) excede 15 s, el cliente lanza `ApiError(0)` pero el servidor puede haberlo procesado. El usuario reintenta y duplica. `sendMessage` y `createReview` son los casos más visibles. Sin `Idempotency-Key`. Recomendación: documentar o añadir clave de idempotencia (Nivel 2 por contrato de API) y, mientras tanto, deshabilitar el botón hasta resolver (ya hay `loading` en la mayoría de formularios).

**F-06 (P2, CONFIRMADO) — Sin cancelación de solicitudes (`AbortController` solo para el timeout).**
`api.*` no acepta `signal` (`api.ts:441` es el único `AbortController`). Las pantallas evitan efectos obsoletos con banderas `active`/`cancelled`/`threadSeq` (bien resuelto en `SearchInput.tsx:77-92`, `TeacherProfilePage.tsx:48-63`, `MessagesPage.tsx:~75`), pero la petición HTTP sigue viva: cambiar rápido de filtros o de conversación deja solicitudes en vuelo contra el rate limit y el pool de Neon. Recomendación: propagar `signal` opcional.

**F-07 (P2, CONFIRMADO) — Respuestas HTTP no validadas en runtime.**
`request` hace `JSON.parse(text) as T` (`api.ts:~505`) y `res.json()) as T`. Si un proxy devuelve HTML con 200 (error de Vercel/Render durante un deploy), `JSON.parse` lanza `SyntaxError`, no `ApiError`; los `catch` que comprueban `instanceof ApiError` caen en la rama genérica o, donde no hay `catch`, quedan como promesa rechazada sin manejar. Además la mayoría de los campos de los tipos son opcionales (`?`), lo que traslada la incertidumbre a cada componente. Recomendación: envolver el parseo y convertirlo en `ApiError`; considerar validación con un esquema ligero en las respuestas críticas (`/auth/me`, login).

**F-08 (P2, CONFIRMADO) — `HumanGate` bloquea toda la aplicación, incluida la landing pública, ante un fallo de `/api/human/status`.**
`HumanGate.tsx:35-39` muestra pantalla de error con "Reintentar" si el backend no responde (por ejemplo durante un arranque en frío de Render plan gratuito, 30-60 s, o con el timeout de 15 s). Sin el backend el sitio estático ni siquiera muestra el contenido SEO/landing. El gate además hace una petición bloqueante antes de pintar cualquier cosa (peor LCP). Relacionado con S-05 (el backend no exige la verificación humana). Recomendación: degradar a "ready" con aviso, o mostrar la landing mientras el estado carga.

**F-09 (P2, CONFIRMADO) — Sin manejo explícito de offline ni de reintento automático.**
No hay `navigator.onLine`, ni eventos `online/offline`, ni reintentos con backoff (grep sin resultados). Los errores muestran mensajes fijos y la mayoría requiere recargar o pulsar "Reintentar" (existe en catálogo y `HumanGate`; no en todas las pantallas). Los `.catch(() => undefined)` silencian fallos de favoritos y de bloqueados (`App.tsx:~91`, `MessagesPage.tsx:~137`): el usuario ve una lista vacía sin saber que falló.

### P3

**F-10 (P3, CONFIRMADO) — Token de un solo uso en la URL de activación/reset y manejo asimétrico.**
`/activar?token=` y `/restablecer?token=` mantienen el token en la barra de direcciones y el historial (`ActivateAccountPage.tsx:20`), mientras que el flujo de mensajes sí lo elimina con `history.replaceState` (`AuthContext.tsx:37-48`). El riesgo se atenúa por `Referrer-Policy: strict-origin-when-cross-origin` (meta, `index.html:6`) y por tokens de un solo uso. Recomendación: borrar el parámetro tras leerlo, igual que el flujo de mensajes.

**F-11 (P3, CONFIRMADO) — Intercambio de `message-link` y StrictMode.**
En desarrollo `useEffect` corre dos veces: la primera ejecución consume y borra el token de la URL; la segunda ejecuta `api.me()` y deja `active=false` a la primera. El resultado es una sesión correcta por la cookie, pero el token ya fue consumido y la respuesta del intercambio se descarta. Solo afecta a dev (StrictMode no duplica en producción). Sin riesgo funcional en producción.

**F-12 (P3, CONFIRMADO) — Sin expiración proactiva de sesión.**
`LoginResponse.expiresIn` se ignora; la sesión caduca solo cuando una petición con `auth=true` recibe 401 y dispara `UNAUTHORIZED_EVENT` (`api.ts:~498`). Peticiones sin `auth` (p. ej. `api.teacher`, `api.teachers`) nunca disparan el evento, y `me()` con 401 se trata como "sin sesión" sin mensaje. Coherente con el backend (ver S-02 sobre revocación por `session_version`), pero el usuario con la pestaña abierta pierde la sesión sin aviso hasta la siguiente acción. Mejora: aviso "Tu sesión venció" al recibir el evento.

**F-13 (P3, CONFIRMADO) — Admin: descarga de documentos con `<a href>` relativo.**
`AdminVerificationsPage.tsx:168` apunta a `/api/admin/documents/{id}/content`. Funciona porque Vercel proxea `/api/*`, pero depende de ese proxy (con `VITE_API_URL` distinto, el enlace iría al dominio equivocado) y no pasa por `fetchWithTimeout` ni gestiona 401/403/404 (el navegador muestra la respuesta JSON cruda). Si la sesión caducó, el admin ve un error del backend en una pestaña. Recomendación: usar `API_BASE` y manejar errores, o abrir en pestaña con `rel="noopener"`.

**F-14 (P3, CONFIRMADO) — Componentes y hooks grandes.**
`MessagesPage` (670 líneas, ~25 `useState` y 8 refs), `AdminVerificationsPage` (613), `App` (431). Aumentan el riesgo de regresiones. Dividir en subcomponentes (lista, hilo, composer) y mover favoritos/perfil de estudiante a hooks propios.

**F-15 (P3, CONFIRMADO) — `eslint-disable react-hooks/exhaustive-deps` en `useTeacherListing.ts:~104` y reglas `set-state-in-effect`/`refs` desactivadas globalmente (`eslint.config.js`).** Decisión consciente y documentada, pero oculta defectos de dependencias. Revisar caso por caso.

**F-16 (P3, CONFIRMADO) — `VITE_TURNSTILE_SITE_KEY` no declarada en `vite-env.d.ts`** (solo `VITE_GOOGLE_CLIENT_ID` y `VITE_API_URL`); se usa con un `as` en `TurnstileWidget.tsx:22`. Además `VITE_SITE_URL` se lee en `vite.config.ts`. Declarar las tres. Todas las variables `VITE_*` son públicas por diseño y ninguna contiene secretos (CONFIRMADO).

**F-17 (P3, CONFIRMADO) — `sessionStorage` y `localStorage` sin `try/catch` en escritura.** `SearchInput.tsx:119` y `ThemeProvider.tsx:30` escriben sin protección; en modo privado/cuota llena puede lanzar. La lectura sí está protegida (`SearchInput.tsx:33-37`). Además `JSON.parse(...)` de `sessionStorage` se devuelve sin validar que sea `string[]`.

## 4. Vulnerabilidades

| ID | Tipo | Estado | Severidad | Nota |
|---|---|---|---|---|
| V-01 | Clickjacking (cabeceras no aplicadas) | PROBABLE | Media | F-01 |
| V-02 | Tracking/contenido de terceros vía `photoUrl` | CONFIRMADO | Media | F-02 |
| V-03 | XSS almacenado/reflejado | No encontrado | — | React escapa; sin `dangerouslySetInnerHTML`; SEO escapa con `escapeHtml` |
| V-04 | Fuga de secretos en bundle | No encontrado | — | solo `VITE_GOOGLE_CLIENT_ID` (público), `VITE_TURNSTILE_SITE_KEY` (público), `VITE_API_URL` |
| V-05 | Tokens en `localStorage`/`sessionStorage` | No encontrado | — | solo tema e historial de búsqueda |
| V-06 | Dependencias | 0 vulnerabilidades (`npm audit --omit=dev`) | — | `devDependencies` no auditadas aquí; CI usa `--audit-level=high` sobre todo |
| V-07 | `style-src 'unsafe-inline'` | CONFIRMADO | Baja | `vite.config.ts:40`; ya en S-07 |
| V-08 | CSP solo por `<meta>` | CONFIRMADO | Baja | sin `frame-ancestors`, `report-to`; ver F-01 |
| V-09 | Source maps | No presentes | — | 0 archivos `.map` en `dist/assets` |

Protección de formularios: la protección CSRF cubre todos los métodos de escritura. Turnstile solo se envía en registro y en `HumanGate`; ni login, ni recuperación, ni reenvío de activación llevan captcha (el backend los limita por IP; ver S-01 y S-03).

## 5. Problemas de tipado

| Hallazgo | Evidencia | Impacto |
|---|---|---|
| `strict: true`, sin `any` explícito en producción | grep `\bany\b` solo coincide en comentarios/`toast.action!`/`profile!.id` | Positivo |
| Non-null assertions | `Toast.tsx:89` (`toast.action!`), `MyListingPage.tsx:62` (`listing.profile!`), `main.tsx` (`getElementById('root')!`) | Bajo; guardar con comprobación |
| Aserciones sobre JSON | `as T` en `api.ts:~505`, `as CsrfResponse` (`api.ts:~470`), `error.body as ApiErrorBody` (`RegisterPage.tsx:25`), `(parsed as {message?: string})` | Sin validación runtime (F-07) |
| Casi todos los campos de respuesta son opcionales | `TeacherSummary`, `TeacherDetail`, `AvailabilityWindow`… | Evita crashes pero oculta contratos rotos; no hay tipos generados desde el backend (sin OpenAPI) |
| Unión de estados repetida | `api.ts:19` vs `VerificationStatus` | Duplicación (sección 2) |
| `string` donde hay conjunto cerrado | `modalities?: string[]`, `AvailabilityWindow.mode?: string`, `addModality(modality: string)`, `ConversationParticipant.role: string`, `VerificationDecisionView.decision: string` | Pierde exhaustividad |
| Tipos duplicados | `Conversation` ≈ `ConversationSummary` sin `lastMessage`; `VerificationCredential` ≈ `AdminCredentialView`; `TeacherProfile` vs `TeacherDetail` | Riesgo de divergencia |
| Variables de entorno | `import.meta.env.VITE_TURNSTILE_SITE_KEY as string \| undefined` | F-16 |
| `JSON.parse(sessionStorage…)` retorna `any` implícito | `SearchInput.tsx:34` | F-17 |

Seguridad de nulos: los componentes usan `?.`, `??` y valores por defecto con consistencia (`availability.content ?? []`). Se detecta `if (!w.dayOfWeek) return;` en `TeacherProfilePage.tsx` que descarta `dayOfWeek === 0`; correcto solo si el backend usa 1–7 (el formulario inicia en 1; no verificado contra la API).

## 6. Problemas de UX técnica

Autenticación y rutas:

- `ProtectedRoute` resuelve loading → login → rol (`ProtectedRoute.tsx:21-26`) y el cierre de sesión redirige solo desde rutas privadas (`App.tsx:~113-120`). Correcto. Es solo UX: el backend decide.
- `SignInGate` abre el modal de login automáticamente desde un `useEffect`; tras cerrar el modal el usuario queda en una página con botón "Ingresar" (aceptable).
- Un ADMIN que abre `/mensajes` (ruta sin restricción de rol) recibe 403 del backend y el mensaje genérico "No pudimos cargar tus conversaciones." (`MessagesPage.tsx:~106`): es el síntoma que reportaste. Falta un estado específico para ese rol (y ocultar el enlace en el `Navbar`; confirmar en `Navbar.tsx`).
- Google OAuth: si el backend responde 400 con "role" se pide elegir rol (`LoginModal.tsx:~84`). La detección es por regex sobre el mensaje del servidor (`/role/i.test(e.message)`), frágil ante cambios de texto. Mejor un código de error estable.
- Login: cualquier 401 muestra la ayuda de reenvío de activación (`LoginModal.tsx:~48-51`); decisión documentada por anti-enumeración.

Formularios:

- Validación cliente de registro: nombre, email no vacío, contraseña ≥10 con letra y número, confirmación (`validation.ts`). No valida formato de email en cliente (el `type="email"` con `noValidate` no bloquea) ni longitud máxima de nombre/email. Los errores de servidor se mapean con `fieldErrors` (`RegisterPage.tsx:24-27,60`).
- Doble envío: botones con `loading` en login, registro, reenvío y `HumanGate` (ref `verifyingRef`). Aceptable. Los formularios de `MyListingPage` (educación, disponibilidad) usan banderas `adding*`.
- Registro: en 503 muestra mensaje claro. El estado "cuenta creada" siempre indica "Si el email no tenía una cuenta…" (anti-enumeración, correcto). Con el problema B-01 del backend (Brevo 401 → DEAD) el usuario nunca recibe el correo y la pantalla no lo puede detectar.
- Reenvío: `resend()` en `ActivateAccountPage` ignora errores y siempre muestra "enviado" (`finally { setResendState('sent') }`): si hay un 429 el usuario lo ve como éxito.

Accesibilidad:

- Positivo: enlace "Saltar al contenido", `role="alert"`/`status`, `aria-busy`, `aria-pressed`, `aria-label`, modal con `role="dialog"`, `aria-modal`, foco inicial, trampa de foco, Escape y restauración de foco (`Modal.tsx`).
- Mejoras: la trampa de foco calcula la lista en cada `Tab` y no incluye `input[type=hidden]`/`disabled` (puede intentar enfocar un control deshabilitado); `Modal` no marca el resto de la página como `inert`/`aria-hidden`; `Modal` cierra con `onMouseDown` en el fondo, por lo que un arrastre de selección de texto que termina fuera cierra el diálogo con datos sin guardar (en `StudentProfileModal`, `ContactModal`).
- `MessagesPage`: la lista de mensajes no usa `aria-live`/`role="log"` (grep sin resultados), así que los lectores de pantalla no anuncian respuestas nuevas del sondeo de 20 s. PENDIENTE de comprobación manual.
- Contraste, tamaños táctiles y comportamiento responsive: hay tests Playwright móviles (`tests/mobile/mobile.spec.ts`); contraste real PENDIENTE (requiere herramienta).
- Compatibilidad: `target: ES2020`; `\p{L}` con flag `u` en validación (soportado en navegadores modernos). Sin configuración `browserslist` explícita (Vite 8 usa su baseline). Safari/Firefox PENDIENTE.

Estados:

- Loading con `Skeleton`, `EmptyState` y reintentos en catálogo; errores por pantalla. Fallos silenciados con `.catch(() => undefined)` (F-09).
- `ErrorBoundary` global presente (con test).
- Sondeo de mensajes cada 20 s solo con pestaña visible y sin solapar (`MessagesPage.tsx:228,265`): correcto; no hay backoff ante errores consecutivos ni pausa en 401 más allá del evento.

## 7. Problemas de rendimiento

- Bundle principal 190 KB (sin gzip) + `jsx-runtime` 47 KB + `primitives` 29 KB; páginas en chunks separados (todas con `lazy`). Aceptable (**medido** sobre el `dist/` local existente, no sobre un build nuevo).
- Fuentes autoalojadas en `latin` vía `@fontsource` (Bungee y Open Sans 400/600/700): 4 archivos de fuente en la ruta crítica; no se usa `font-display` propio (el paquete lo trae como `swap`).
- Imágenes: `loading="lazy"` en avatares; sin `width/height` en el `<img>` (clases Tailwind fijan tamaño, así que el layout no salta); sin `srcset`; las fotos provienen de URL externa arbitraria (F-02), sin control de peso.
- `HumanGate` añade una petición bloqueante antes del primer render (F-08).
- Cada visita a la home/buscador/perfil pasa por `middleware.ts` → función `api/seo` (prerender SEO), lo que añade latencia de función serverless a las rutas públicas más visitadas. Medición PENDIENTE.
- Re-renders: `App` mantiene estado de favoritos/perfil/modales en un solo componente; cualquier cambio re-renderiza el árbol de rutas (los elementos de ruta se recrean). Sin `React.memo` en `TeacherCard`. Impacto bajo con 24 tarjetas por página; medir con React Profiler (PENDIENTE).
- Sin cancelación de solicitudes (F-06) y sin caché de resultados entre navegaciones (cada vuelta a `/buscar` repite la consulta).
- Sondeo de mensajes: una petición cada 20 s por pestaña visible; con muchas sesiones simultáneas suma carga sobre el backend (ver B-07 en auditoría 02).

## 8. Evidencia

| Tema | Archivo:línea |
|---|---|
| Cliente HTTP, timeout, CSRF, 401 | `frontend/src/api.ts:398-515` |
| Sesión y token de enlace | `frontend/src/auth/AuthContext.tsx:37-100` |
| Guardas de ruta | `frontend/src/components/routing/ProtectedRoute.tsx:21-26` |
| Redirección al cerrar sesión | `frontend/src/App.tsx:113-120` |
| CSP | `frontend/vite.config.ts:14-60`, `scripts/check-csp.mjs` |
| Cabeceras (formato Netlify) | `frontend/public/_headers` |
| Rutas Vercel | `frontend/vercel.json` |
| `photoUrl` → `<img>` | `ProfileSection.tsx:39`, `primitives.tsx:161-166`, `TeacherProfilePage.tsx:43` |
| Descarga admin | `AdminVerificationsPage.tsx:168` |
| Gate humano | `HumanGate.tsx:35-72` |
| Sondeo mensajes | `MessagesPage.tsx:30,228,265` |
| Variables de entorno | `vite-env.d.ts`, `TurnstileWidget.tsx:22`, `AuthContext.tsx:9` |
| CI | `.github/workflows/ci.yml:37-55` |

Flujos recorridos por lectura: registro (`RegisterPage` → `api.register`), activación (`ActivateAccountPage`), login por contraseña y Google (`LoginModal`, `GoogleSignInButton`), búsqueda (`SearchInput`, `SearchPage`), perfil (`TeacherProfilePage`), contacto (`App.contact` → `ContactModal`), gestión de profesor (`useTeacherListing`, `VerificationPanel`). No se ejecutaron en un navegador.

## 9. Recomendaciones

| # | Acción | Nivel (AGENTS.md) | Prioridad |
|---|---|---|---|
| 1 | Aplicar cabeceras de seguridad reales en Vercel (`headers`) y verificar con `curl -sI` | 1 | P1 |
| 2 | Restringir/validar `photoUrl` y añadir `referrerPolicy="no-referrer"`; decidir allowlist o subida propia | 2 | P1 |
| 3 | Timeout propio para multipart y validación previa de tamaño/tipo | 1 | P2 |
| 4 | Convertir errores de parseo en `ApiError`; manejar 200 no-JSON | 1 | P2 |
| 5 | Reintentar solo fallos CSRF reales | 1 | P2 |
| 6 | `signal` opcional en `api.*` y cancelar en los efectos de búsqueda/mensajes | 1 | P2 |
| 7 | `HumanGate` no bloquea la landing ante fallo del backend | 2 (afecta comportamiento de seguridad) | P2 |
| 8 | Estado explícito para ADMIN en `/mensajes` y ocultar enlace | 1 | P2 |
| 9 | Aviso de "sesión vencida" al recibir `UNAUTHORIZED_EVENT` | 1 | P3 |
| 10 | Quitar el token de la URL tras leerlo en activar/restablecer | 1 | P3 |
| 11 | Dividir `MessagesPage`, `AdminVerificationsPage`, `App`; extraer reglas de contraseña y constructor de query | 0/1 | P3 |
| 12 | Declarar todas las `VITE_*` en `vite-env.d.ts`; proteger escrituras de storage | 0 | P3 |
| 13 | `aria-live`/`role="log"` en hilo de mensajes; `inert` en el fondo del modal | 1 | P3 |
| 14 | Generar tipos desde OpenAPI o tests de contrato front/back | 2 | P3 |

## 10. Criterios de aceptación

Para dar el frontend por listo para producción:

1. `curl -sI` al dominio de producción muestra `X-Frame-Options: DENY` (o `frame-ancestors 'none'`), `X-Content-Type-Options: nosniff` y `Referrer-Policy`; un iframe de prueba no carga la app.
2. `photoUrl` solo acepta `https` y los hosts definidos; el `<img>` usa `referrerPolicy="no-referrer"`.
3. Subir un PDF de 10 MB con red limitada a 1 Mbps (Playwright con throttling) termina sin error de timeout.
4. Una respuesta 200 con cuerpo HTML en `/api/auth/me` deja la app en estado "sin sesión" sin excepción sin capturar (test unitario).
5. Un 403 de autorización en una escritura se envía una sola vez; un 403 CSRF se reintenta una vez (test en `api.test.ts`).
6. Con el backend caído, la landing se muestra y las acciones privadas informan el error; con ADMIN, `/mensajes` muestra un mensaje específico.
7. `npm run lint`, `npm test`, `npm run build` y `npm audit --audit-level=high` en verde en CI (ya existen); `npx playwright test` ejecutado en un pipeline (verificar en la auditoría 05 si CI lo corre).
8. Verificación manual: navegación por teclado completa de login, registro, contacto y modales; lector de pantalla anuncia mensajes nuevos.
9. Lighthouse en producción: Accesibilidad ≥ 90 y sin errores de CSP en consola (PENDIENTE de medición).

## Aspectos no verificables en esta auditoría

- Configuración real de Vercel (cabeceras, dominio, HSTS) y de variables `VITE_*` en producción.
- Validación backend de `photoUrl` y de longitud/formato de campos del perfil.
- Contraste, rendimiento real (Lighthouse/Web Vitals), comportamiento en Safari/Firefox y lectores de pantalla.
- Resultados de `tsc`, `eslint`, `vitest` y Playwright en esta sesión (no ejecutados).
- `npm audit` de `devDependencies` (CI sí lo ejecuta).
