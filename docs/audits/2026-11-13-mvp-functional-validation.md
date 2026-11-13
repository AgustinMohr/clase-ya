# Validación funcional del MVP — ClaseYa

- **Fecha:** 2026-11-13
- **Alcance:** flujos funcionales del MVP: búsqueda, perfiles, autenticación, roles, contacto, estados de UI y responsive.
- **Modo:** análisis funcional sin cambios de código en el momento de la auditoría.
- **Evidencia:** código, tests existentes y CI de GitHub Actions.
- **Estado posterior:** el plan T1–T7 fue aprobado e implementado en el PR #52 (`fix/mvp-functional-ux → dev`). T8 (release y producción) queda pendiente.

---

## Evidencia usada

- `dev` = `875f278` al momento de la validación.
- `main` = `e764dac`, 26 commits atrás.
- PR #51 cerrado sin merge.
- CI de `dev`: Backend, Frontend, E2E, CodeQL, Dependency review y Vercel en verde.
- No se ejecutaron builds locales nuevos; se usó la evidencia de GitHub Actions.

---

## 1. Búsqueda y filtros de profesores

**Recorrido**
Landing o `/buscar` → término → sugerencias → filtros (zona, modalidad, rating, precio, verificados) → aplicar → tarjetas → paginación → perfil.

**Frontend**
`LandingPage`, `SearchPage`, `SearchInput`, `PriceRange`, `Pagination`, `TeacherCard`, `useCatalog`.

**Backend**
`GET /api/subjects`, `GET /api/subjects/popular`, `GET /api/subjects?query=`, `GET /api/teachers` con `subjectId`, `careerId`, `universityId`, `city`, `modality`, `minRating`, `minPrice`, `maxPrice`, `page`, `size`, `sort`, `onlyVerified`.

**Reglas**
- Solo profesores `ACTIVE` con anuncio publicado.
- Materia directa o vía carrera.
- Ciudad exacta, case-insensitive y trim.
- Precio 0..30000; página 0..N; tamaño 1..50; sort whitelist.
- Perfil oculto → 404.
- La URL es la fuente de verdad; los filtros solo se aplican con el botón.

**Pruebas existentes**
`TeacherSearchIntegrationTest`, `TeacherCardIntegrationTest`, `SubjectCatalogIntegrationTest`, `search.spec.ts`, `transparency.spec.ts`.

**Faltantes**
- Test de componente para `SearchPage`.
- E2E mobile de filtros.
- Test que distinga “materia inexistente” de “fallo del catálogo”.
- Test de URL inválida (`pagina`, precio, rating).

**Defectos**
- Si falla `api.subjectSearch`, la UI mostraba “No encontramos ‘X’” como si la materia no existiera. Confundía un error de red con ausencia de resultados.
- Un `400` del backend por filtros fuera de rango mostraba “No pudimos buscar profesores. Revisá tu conexión”, incorrecto.
- No hay normalización de variantes de ciudad (“Sta. Fe” vs “Santa Fe”).
- No hay búsqueda “cerca de mí” en UI, aunque la API soporta lat/lng.

**Criterios de aceptación**
- Término inexistente, error de catálogo y error de red muestran mensajes distintos.
- Filtros inválidos explican el campo concreto.
- Paginación y back/forward conservan filtros.
- Flujo completo funciona en 360, 768 y desktop.

---

## 2. Visualización de perfiles públicos

**Recorrido**
Tarjeta → `/profesores/:id` → detalle, materias, modalidades, disponibilidad, formación, opiniones → Contactar o favorito.

**Frontend**
`TeacherCard`, `TeacherProfilePage`, `ReviewsSection`, `Avatar`, `Badge`.

**Backend**
`GET /api/teachers/{id}`, `GET /api/availability?teacherId=`, `GET /api/teachers/{id}/reviews`, `GET /api/teachers/{id}/reviews/me`.

**Reglas**
- Perfil visible = `ACTIVE` + anuncio publicado; oculto → 404.
- DTO público sin email, dirección exacta ni coordenadas.
- Disponibilidad y opiniones solo de profesores visibles.
- Profesor/admin no puede iniciar contacto; ve aviso para usar Mensajes.

**Pruebas existentes**
`TeacherProfileIntegrationTest`, `ReviewIntegrationTest`, `TeacherCardIntegrationTest`, `teacher-profile.spec.ts`, `routing.spec.ts`, `transparency.spec.ts`.

**Faltantes**
- Test de componente para `TeacherProfilePage`.
- Test de transición error 404 → perfil válido.
- Test de reintento ante error transitorio.
- E2E mobile.

**Defectos**
- `TeacherProfilePage` no limpiaba `error` al cambiar `teacherId`. Si se navegaba de un perfil inexistente a uno válido, el estado de error podía persistir.
- Ante error de red solo había “Volver”, no “Reintentar”.
- Si falla la disponibilidad, se muestra vacío silenciosamente; aceptable, pero puede parecer “sin horarios”.

**Criterios de aceptación**
- Un perfil 404 muestra estado claro y permite volver.
- Un perfil válido después de uno inválido carga correctamente.
- Error transitorio permite reintentar sin salir.
- No se expone ningún dato privado en la respuesta pública.

---

## 3. Registro, login, logout y persistencia de sesión

**Recorrido**
Registro → consentimiento → email de activación → activación → login → `/me` → navegación → logout. Recuperación: forgot → email → reset → login.

**Frontend**
`RegisterPage`, `LoginModal`, `ActivateAccountPage`, `ForgotPasswordPage`, `ResetPasswordPage`, `AuthContext`, `ProtectedRoute`.

**Backend**
`POST /api/auth/register`, `/activate`, `/resend-activation`, `/login`, `/logout`, `/forgot-password`, `/reset-password`, `/google`, `/message-link`; `GET /api/auth/me`, `/csrf`.

**Reglas**
- Registro `202` anti-enumeración; cuenta `PENDING`.
- Activación por token de un solo uso.
- BCrypt; CAPTCHA si está habilitado.
- JWT en cookie HttpOnly; CSRF en métodos unsafe.
- Sesión 12 h; logout y reset revocan tokens con `session_version`.
- Errores de login genéricos.

**Pruebas existentes**
`AuthIntegrationTest`, `AccountActivationIntegrationTest`, `ActivationConcurrencyIntegrationTest`, `PasswordResetIntegrationTest`, `SessionRevocationIntegrationTest`, `CsrfIntegrationTest`, `CaptchaIntegrationTest`, `JwtServiceTest`, `JwtSecretValidatorTest`, `registration.spec.ts`, `session.spec.ts`.

**Faltantes**
- Test unitario de `LoginModal`.
- Test de mensajes en español para activación/reset.
- Test de persistencia de sesión en refresh real (parcialmente cubierto por E2E).
- Test de login con CAPTCHA en E2E real.

**Defectos**
- `ActivateAccountPage` y `ResetPasswordPage` mostraban el `message` crudo del backend; en varios casos era inglés (“The reset link is invalid or has expired”).
- Después de activar hay que iniciar sesión manualmente; no es bloqueante, pero es fricción.
- Registro con email ya activo responde igual que uno nuevo (anti-enumeración), lo que puede confundir a un usuario que se equivocó de formulario.
- El consentimiento parental es solo UI; no queda registro server-side.

**Criterios de aceptación**
- Activación inválida, expirada y usada muestran mensaje claro en español.
- Reset inválido/expirado también.
- Cookie persiste durante refresh y se elimina en logout.
- Logout invalida el token anterior.
- Registro, activación y login funcionan en mobile.

---

## 4. Roles, permisos y protección de operaciones

**Recorrido**
Usuario anónimo → login → rol → rutas privadas y acciones permitidas.

**Frontend**
`ProtectedRoute`, `Navbar`, rutas de admin, acciones condicionadas por rol.

**Backend**
`SecurityConfig`, `CurrentUser`, `AppUserDetails`, servicios con ownership.

**Reglas**
- STUDENT: favoritos, iniciar conversaciones, opinar.
- TEACHER: perfil/anuncio, responder opiniones, disponibilidad, verificación.
- ADMIN: catálogo, usuarios, opiniones, verificación, reportes.
- Rutas admin `ADMIN`-only.
- Conversación ajena → 404.
- Perfil oculto → 404.
- Profesor no responde opiniones ajenas.

**Pruebas existentes**
`SecurityConfigOriginsTest`, `SecurityHeadersIntegrationTest`, `AdminDirectoryIntegrationTest`, `AdminVerificationReviewIntegrationTest`, `SafetyIntegrationTest`, `ReviewIntegrationTest`, `admin.spec.ts`, `admin-users.spec.ts`, `admin-verification.spec.ts`, `routing.spec.ts`, `session.spec.ts`.

**Faltantes**
- Test unitario de `ProtectedRoute`.
- Matriz completa rol × endpoint.
- Test de acceso cruzado a conversaciones/opiniones/reportes en frontend.

**Defectos**
- En mobile, los links del `Navbar` con texto `hidden sm:inline` y el icono `aria-hidden` podían quedar sin nombre accesible.
- `dev` no está protegida; el proceso permite pushes directos sin PR.
- No hay skip link global.

**Criterios de aceptación**
- Cada rol solo ve y ejecuta lo permitido, incluso por URL directa.
- El backend rechaza la operación aunque el frontend la muestre.
- Los links/acciones mobile tienen nombre accesible.

---

## 5. Flujo de contacto estudiante → profesor

**Recorrido**
Perfil → Contactar → si falta perfil, completar nombre/nivel → modal de contacto → primer mensaje → conversación → profesor responde → estudiante ve respuesta.

**Frontend**
`TeacherProfilePage`, `ContactModal`, `StudentProfileModal`, `App`, `MessagesPage`, `ReportModal`.

**Backend**
`POST /api/conversations`, `GET /api/conversations`, `GET /api/conversations/{id}/thread`, `POST /api/conversations/{id}/messages`, `PATCH /api/conversations/{id}/read`, bloqueos y reportes.

**Reglas**
- Solo STUDENT inicia contacto.
- Profesor visible; si está oculto → 404.
- Perfil de estudiante con nombre visible; la UI exige además nivel.
- Una conversación por par estudiante/profesor.
- Primer mensaje opcional.
- Email de contacto solo en el primer contacto.
- Bloqueo en cualquier dirección → 403 al contactar/enviar.
- Rate limit de contacto y mensajes.
- Sin exponer email/teléfono.

**Pruebas existentes**
`MessagingIntegrationTest`, `ConversationConcurrencyIntegrationTest`, `SafetyIntegrationTest`, `ReviewIntegrationTest`, `NotificationOutboxIntegrationTest`, `teacher-profile.spec.ts`, `messages.spec.ts`.

**Faltantes**
- Test de `ContactModal`.
- E2E del flujo “sin perfil → completar → volver al contacto”.
- Test de 403 por bloqueo y 429 por rate limit en UI.
- Test de respuesta del profesor visible por polling.

**Defectos**
- `ContactModal` mostraba un mensaje genérico para 403 (bloqueado) y 429 (rate limit); el usuario no sabía qué hacer.
- Si falla `markConversationRead`, `MessagesPage` mostraba “No pudimos cargar la conversación” aunque el hilo sí cargó.
- Si el otro usuario bloqueó al estudiante, el composer seguía visible y el error aparecía recién al enviar.
- No hay email por cada respuesta; el profesor/estudiante depende de entrar a Mensajes o del polling cada 20 s.

**Criterios de aceptación**
- Contacto exitoso crea o reutiliza una conversación y abre el hilo.
- Sin perfil, el usuario completa nombre/nivel y vuelve al contacto sin perder el profesor.
- Bloqueo y rate limit se explican en español.
- El profesor ve el contacto; el estudiante ve respuestas sin refrescar manualmente.
- No se filtran datos personales.

---

## 6. Estados de carga, errores, validaciones y vacíos

**Frontend**
`Skeleton`, `EmptyState`, `Toast`, `ErrorBoundary`, `Modal`, `Field`, páginas de auth, búsqueda, perfiles, mensajes y admin.

**Backend**
`GlobalExceptionHandler`, `ApiError`, `BadRequestException`, `ConflictException`, `ResourceNotFoundException`, `AccessDeniedException`.

**Bien resuelto**
- Skeletons en búsqueda, perfil, mensajes y admin.
- Empty states en búsqueda sin resultados, sin conversaciones, sin opiniones y admin sin cola.
- Errores de API con forma `ApiError`.
- Validaciones de formularios y límites.
- `ErrorBoundary` global.
- `aria-live` en toasts y búsqueda.

**Faltantes**
- Tests de componente para `SearchPage`, `TeacherProfilePage`, `MessagesPage`, `ContactModal`, `StudentProfileModal`.
- Tests de estados de error/vacío por página.
- Test de reintento en perfil.
- Test de fallo de `markRead`.
- Test de mensajes de error en español.
- Axe/Lighthouse automatizado.

**Defectos**
- Errores crudos en inglés en activación/reset.
- `StudentProfileModal` mostraba `e.message` del backend, que podía ser un mensaje técnico en inglés.
- `SearchPage` confundía fallo de catálogo con materia inexistente.
- `TeacherProfilePage` no permitía reintentar tras error.
- `MessagesPage` mezclaba error de “marcar leído” con error de carga del hilo.
- No hay reporte de errores frontend en producción.

**Criterios de aceptación**
- Todos los errores de usuario están en español y son accionables.
- Cada página tiene loading, error, vacío y reintento cuando aplica.
- Un fallo secundario no oculta contenido ya cargado.
- Los formularios muestran errores por campo.

---

## 7. Experiencia responsive

**Frontend**
Layouts con Tailwind (`sm`, `lg`), `Navbar`, `SearchPage`, `TeacherProfilePage`, `MessagesPage`, `Modal`.

**Bien resuelto**
- Mensajes: lista/thread separados en mobile con “Volver”.
- Perfil: CTA sticky solo en `lg`.
- Búsqueda: filtros apilados en mobile.
- Modales con scroll y foco.
- `prefers-reduced-motion` respetado.

**Faltantes**
- Playwright solo probaba Desktop Chrome.
- No había tests de 360/390/768 px.
- No había auditoría automática de accesibilidad.
- No hay verificación en Safari iOS/Android real.

**Defectos**
- Links/iconos del `Navbar` sin nombre accesible en mobile.
- No hay skip link.
- Filtros de búsqueda ocupan mucho espacio antes de los resultados en mobile.
- El header de Mensajes con Reportar/Bloquear/Volver puede quedar cargado en pantallas pequeñas.

**Criterios de aceptación**
- Registro, búsqueda, perfil, contacto, mensajes, reporte y legales funcionan en 360/390/768 px.
- Sin scroll horizontal.
- Navegación completa con teclado.
- Contraste y foco visibles.
- Links e iconos con nombre accesible.

---

## Defectos del MVP vs mejoras futuras

### Defectos a corregir antes de usuarios reales
1. Perfil público: error persistente al cambiar de profesor y falta reintento.
2. Mensajería: fallo de “marcar leído” se muestra como fallo de carga.
3. Activación/reset/perfil: mensajes crudos en inglés.
4. Búsqueda: fallo de catálogo se muestra como “materia inexistente”.
5. Contacto: 403/429 sin explicación accionable.
6. Navbar mobile sin nombres accesibles y sin skip link.
7. Cobertura mobile/a11y inexistente en E2E.
8. Release/config/datos demo: bloqueantes de producto, no de código funcional.

### Mejoras futuras (no bloquean MVP)
- Email por cada mensaje.
- Refresh tokens con rotación.
- Normalización de ciudades y “cerca de mí”.
- Subida de foto en vez de URL.
- Auto-login después de activar.
- Sentry/error tracking frontend.
- Redis para rate limiting distribuido.
- Índices de búsqueda por rating/precio con volumen real.
- MFA.

---

## Plan de implementación propuesto

### T1 — Errores de búsqueda distinguibles
- Cambio: `SearchPage`/`resolveSubject` distingue error de red, error de catálogo y materia inexistente.
- AC: tres estados visuales distintos; reintento funciona.
- Verificación: test de componente + E2E.

### T2 — Ciclo de vida del perfil público
- Cambio: limpiar `error` al cambiar `teacherId`; agregar “Reintentar”.
- AC: 404 → válido funciona; error transitorio se recupera.
- Verificación: test de componente `TeacherProfilePage`.

### T3 — Aislar fallo de marcar leído
- Cambio: en `MessagesPage`, `markConversationRead` no debe convertir un hilo cargado en error.
- AC: si falla mark-read, el hilo sigue visible y solo se informa aparte.
- Verificación: test de componente con mock de fallo.

### T4 — Errores de usuario en español
- Cambio: mapear errores de activación, reset, perfil y contacto a mensajes claros.
- AC: sin mensajes técnicos en inglés; 400/403/409/429 traducidos.
- Verificación: tests de componentes/páginas.

### T5 — Accesibilidad del Navbar y navegación
- Cambio: `aria-label` en links/botones con texto oculto; skip link.
- AC: navegación por teclado y lector de pantalla en mobile.
- Verificación: test de `Navbar` + revisión manual.

### T6 — Cobertura mobile y accesibilidad
- Cambio: proyecto Playwright mobile para flujos críticos; smoke axe/Lighthouse.
- AC: registro, búsqueda, perfil, contacto y mensajes en 360/390 px.
- Verificación: E2E mobile en CI.

### T7 — UX de contacto con bloqueo/rate limit
- Cambio: mensajes específicos para 403 y 429; estado de bloqueo en el hilo.
- AC: el usuario entiende por qué no puede contactar y qué hacer.
- Verificación: tests de `ContactModal` y `MessagesPage`.

### T8 — Cierre de release y datos
- Cambio: promover `dev → main`, configurar producción y limpiar demo.
- AC: `main` = `dev`, migraciones aplicadas, `/api/teachers` solo reales, smoke test real.
- Verificación: CI + smoke manual.

**Orden:** T1–T4 primero (defectos de flujo), T5–T7 después (UX/a11y), T8 al final (release). T8 no debe esperar a que todo esté perfecto, pero sí a que los flujos esenciales estén verdes.

---

## Estado de implementación posterior

- **T1–T7 implementados** en la rama `fix/mvp-functional-ux` y el PR #52 hacia `dev`.
  - Búsqueda con errores distinguibles.
  - Perfil con reintento y limpieza de error.
  - Mensajería con mark-read aislado y composer bloqueado.
  - Errores en español.
  - Navbar accesible y skip link.
  - Smoke E2E mobile.
  - Corrección real detectada por el smoke: navbar desbordaba en mobile con sesión iniciada; se oculta el wordmark en pantallas chicas.
- **T8 pendiente:** merge a `dev`, release `dev → main`, configuración de producción y limpieza demo.
