# CONTACT-001 — Contacto seguro de punta a punta (v1)

**ID:** CONTACT-001
**Estado:** Activa — **pendiente de REVIEW del director**
**Carpeta:** `docs/specs/active/`
**Nivel de cambio:** 2 (flujo nuevo + contrato de API)

---

## 1. Objetivo

Cerrar el flujo que define el producto: que un estudiante pueda **contactar** a un profesor desde su
perfil, que se cree la conversación interna con su primer mensaje, y que ambos puedan **leer y
responder** dentro de ClaseYa — sin exponer datos personales y sin reservas ni pagos.

Hoy el botón "Contactar" del perfil es un **prototipo**: guarda la solicitud en `sessionStorage` y
muestra "Solicitud registrada (simulada)" (`ContactModal.tsx:14-17,37`). El backend de mensajería ya
existe y está probado (Phase 6), pero **el frontend no lo usa**: `api.ts` no tiene ningún método de
conversaciones ni existe una vista de mensajes.

## 2. Contexto

Lo que ya existe y **no se toca**:

- `POST /api/conversations {teacherId}` — solo `STUDENT`, reutiliza la conversación del par
  (200 si existe, 201 si es nueva), exige `StudentProfile` (409) y profesor `VERIFIED`+`ACTIVE` (404)
  (`ConversationService.start:63-89`).
- `GET /api/conversations` (propias, ordenadas por actividad), `GET /{id}` (+`unreadCount`),
  `GET /{id}/messages` (paginado, más antiguos primero), `POST /{id}/messages`, `PATCH /{id}/read`.
- Sin N+1 (3 consultas batcheadas), orden estable, 404 para no participantes, contenido 1..5000
  caracteres (ver `docs/messaging.md`).

Lo que falta (verificado en código):

- `POST /api/conversations` **no acepta un mensaje inicial** → contactar crea una conversación vacía.
- **No existe UI** de mensajes: `App.tsx:17-21` solo tiene `landing`, `search`, `profile`, `favorites`.
- **No existe UI para completar el perfil de estudiante.** `POST /api/students/profile`,
  `GET/PUT /api/students/me` existen, pero no hay ningún formulario. Consecuencia real: un estudiante
  nuevo **no puede contactar** (409) **ni guardar favoritos** (409, y `App.tsx:80` ya muestra
  "Completá tu perfil de estudiante..." sin ofrecer cómo hacerlo).
- El `ContactModal` pide la materia como texto libre y no usa las materias reales del profesor.

## 3. Actores

| Actor | Qué hace |
|---|---|
| Estudiante (o padre/madre/tutor con cuenta propia) | Completa su perfil, contacta a un profesor con un primer mensaje y conversa. |
| Profesor | Recibe el contacto y responde dentro de la plataforma. |
| Visitante sin cuenta | Puede llegar al botón Contactar; se le pide iniciar sesión (comportamiento actual). |
| Sistema | Crea la conversación, valida visibilidad, aplica ownership y no expone datos personales. |

## 4. Requisitos funcionales

**Perfil de estudiante (desbloqueante)**

- **RF-1** — Si el usuario autenticado es `STUDENT` y no tiene perfil, la UI le ofrece **completarlo**
  (universidad, carrera, año actual — campos que exige el modelo: `university_id`, `career_id`,
  `current_year` 1..12, con `bio` opcional).
- **RF-2** — El perfil se crea con `POST /api/students/profile` y se puede consultar/editar con
  `GET/PUT /api/students/me`. La UI llega a ese formulario desde el error de contacto/favorito y
  desde el menú de usuario.
- **RF-3** — Universidad y carrera se eligen de los catálogos públicos existentes
  (`GET /api/universities`, `GET /api/academic-units/{id}/careers` o el endpoint equivalente ya
  disponible); nunca se escribe texto libre para entidades del catálogo.

**Contacto**

- **RF-4** — `POST /api/conversations` acepta un campo **opcional** `message` (el primer mensaje). Si
  viene, se persiste en la misma transacción que la conversación. La respuesta mantiene su forma
  actual (`201` nueva / `200` existente).
- **RF-5** — Si ya existía la conversación del par, el mensaje **se agrega igual** (no se descarta) y
  la respuesta sigue siendo `200`.
- **RF-6** — El mensaje inicial respeta las reglas ya vigentes: trim, 1..5000 caracteres, y el emisor
  debe ser participante (garantizado por la FK compuesta).
- **RF-7** — El `ContactModal` deja de ser simulado: envía el contacto real y, al confirmar, lleva al
  usuario a la conversación creada.
- **RF-8** — El modal arma el primer mensaje con los campos que pide el producto (§7): **materia**
  (elegida entre las materias reales del profesor, no texto libre), **franja preferida**
  (mañana/tarde/noche) y **rol** ("soy estudiante" / "soy padre/madre de un estudiante"), más un
  texto libre opcional. Se componen en el contenido del mensaje; **no se agregan columnas**.

**Mensajería**

- **RF-9** — Vista **Mensajes** accesible desde el navbar para `STUDENT` y `TEACHER`, con la lista de
  conversaciones (nombre de la contraparte, último mensaje, fecha y **no leídos**).
- **RF-10** — Vista de hilo: mensajes ordenados del más antiguo al más nuevo, envío de mensajes,
  paginación de la historia y **marcado como leído al abrir** (`PATCH /{id}/read`).
- **RF-11** — Los estados vacíos son explícitos: sin conversaciones ("Todavía no contactaste a ningún
  profesor" / "Todavía no te contactó ningún estudiante") y conversación sin mensajes.
- **RF-12** — Los errores de la API se muestran en lenguaje de producto: `409` sin perfil → ofrece
  completarlo (RF-1); `404` (profesor no visible o conversación ajena) → "Este perfil ya no está
  disponible"; `401` → sesión expirada (comportamiento global actual).

## 5. Requisitos no funcionales

- Sin N+1: se reutilizan las consultas batcheadas existentes (`docs/messaging.md` §Anti-N+1).
- Sin migración: no cambia el schema. Sin dependencias nuevas (ni librería de componentes ni de email).
- Sin WebSockets: la lista y el hilo se actualizan al abrir/refrescar (el polling es aceptable en v1).
- El envío de un mensaje no debe recargar la lista completa: se agrega de forma optimista o se
  refresca solo el hilo.
- Mantener el patrón de filtros con acción explícita ya usado en la búsqueda: nada de requests por
  pulsación de tecla.

## 6. Seguridad

- La identidad sigue saliendo de `CurrentUser`; **nunca** se aceptan `senderId`/`studentId`/
  `participantIds` del cliente (regla ya cubierta por tests de Phase 6).
- El `message` inicial no puede crear conversaciones con profesores no visibles: se valida antes de
  persistir (404), igual que hoy.
- Un no participante sigue recibiendo **404** (no se filtra la existencia).
- El contenido del mensaje se valida (trim + longitud) y se renderiza como **texto**, nunca como HTML.
- No se exponen email, teléfono, dirección ni coordenadas en ninguna de las vistas nuevas
  (`ConversationParticipantResponse` ya expone solo `displayName` y `role`).
- **Fuera de v1, documentado:** límites de contacto y rate limiting (producto §7) y el email de aviso
  con enlace firmado de un solo uso (§7.4) — ver Out of scope.

## 7. Invariantes

- **I1** — Una conversación tiene exactamente **dos participantes**: el estudiante (o quien contacta)
  y el profesor.
- **I2** — Un par estudiante↔profesor tiene **una sola** conversación (re-POST devuelve la misma).
- **I3** — Todo mensaje tiene un emisor que es participante (garantizado por la FK compuesta).
- **I4** — Ninguna respuesta nueva expone datos personales de la contraparte más allá de
  `displayName` y `role`.
- **I5** — Marcar como leído afecta **solo** los mensajes no leídos de la contraparte; los propios
  nunca se marcan.

## 8. API contract

Único cambio: un campo opcional en un endpoint existente (compatible hacia atrás).

```http
POST /api/conversations
Authorization: Bearer <student>
{ "teacherId": "<uuid>", "message": "Hola, busco apoyo en Análisis Matemático I. Preferiría por la tarde." }
→ 201 (nueva) | 200 (existente)   // misma forma de respuesta que hoy
```

Sin `message`, el comportamiento es idéntico al actual. Validación: `message` opcional; si viene,
trim y 1..5000 caracteres (400 fuera de rango, reutilizando las reglas de `MessageService`).

Los endpoints de lectura/escritura de mensajes **no cambian**.

## 9. Datos afectados

- `conversations`, `conversation_participants`, `messages`: solo se ejercitan (ya existen).
- **Sin migración** y **sin columnas nuevas**: la materia, la franja y el rol viajan dentro del
  contenido del primer mensaje (ver RF-8 y D2 en §12).

## 10. Criterios de aceptación

| # | Escenario | Verifica |
|---|---|---|
| CA-1 | Contactar con `message` crea la conversación y **un** mensaje con ese contenido | RF-4, RF-6 |
| CA-2 | Contactar sin `message` sigue devolviendo 201/200 como hoy (regresión) | RF-4 |
| CA-3 | Re-contactar el mismo profesor con otro `message` devuelve **200** y **agrega** el mensaje | RF-5, I2 |
| CA-4 | Profesor no visible (`PENDING`/`REJECTED`/usuario no `ACTIVE`) → 404 y **no** se agrega mensaje | §6 |
| CA-5 | Estudiante sin perfil → 409 con mensaje claro; la UI ofrece completar el perfil | RF-1, RF-12 |
| CA-6 | `message` vacío o de más de 5000 caracteres → 400 | RF-6 |
| CA-7 | Un no participante recibe 404 en detalle, mensajes, envío y read | §6 |
| CA-8 | `PATCH /read` marca solo los de la contraparte (los propios quedan intactos) | I5 |
| CA-9 | La lista de conversaciones ordena por actividad y trae `unreadCount` correcto | RF-9 |
| CA-10 | El primer mensaje compuesto por el modal incluye materia, franja y rol legibles | RF-8 |

## 11. Out of scope

- **Email de aviso con enlace firmado de un solo uso** (producto §7.4): requiere proveedor de email
  (`spring-boot-starter-mail` u otro) y tokens de un solo uso. Fase siguiente.
- **Rate limiting / límites de contacto** (producto §7, anti-abuso). Fase siguiente, antes de abrir
  a usuarios reales.
- WebSockets / tiempo real, adjuntos, búsqueda dentro de mensajes, edición o borrado de mensajes.
- Notificaciones (`notifications`) y reportes (`reports`): siguen fuera de alcance.
- Reservas, agenda y pagos: descartados por dirección de producto.
- Un usuario con **ambos roles** (hoy el rol se elige al registrarse): decisión pendiente del
  producto (§12 de `product-requirements.md`), no de esta spec.

## 12. Decisiones para REVIEW

- **D1 —** ¿El primer mensaje viaja en `POST /api/conversations` (recomendado: atómico, un solo
  request) o el frontend hace create + send (dos requests, más simple en el backend pero deja
  conversaciones vacías si falla el segundo)?
- **D2 —** ¿Materia/franja/rol se **componen en el texto** del primer mensaje (recomendado: sin
  migración, legible para el profesor) o se agregan **columnas** al modelo (más estructurado, cuesta
  una migración `V8` y no lo consume ninguna pantalla)?
- **D3 —** ¿El email de aviso queda fuera de CONTACT-001 (recomendado) o entra ahora? Entrar implica
  proveedor de email, plantillas y enlaces firmados.
- **D4 —** ¿La vista Mensajes vive en el navbar como entrada propia (recomendado) o dentro de un
  futuro "Mi cuenta"?
- **D5 —** ¿Aceptás que el **formulario de perfil de estudiante** entre en esta spec como
  desbloqueante (RF-1..RF-3), o preferís una spec aparte más chica ejecutada **antes**? Sin él, el
  flujo de contacto no es alcanzable para un usuario nuevo.

## 13. Definition of Done

- [ ] Spec aprobada por el director (REVIEW).
- [ ] Change budget declarado antes de implementar.
- [ ] Backend: `message` opcional en `POST /api/conversations` + tests de CA-1..CA-10 (los que apliquen).
- [ ] Frontend: formulario de perfil de estudiante, `ContactModal` real, vista Mensajes (lista + hilo
      + envío + read) y métodos en `api.ts`.
- [ ] `mvn -B clean test` en verde y `npm run build` en verde.
- [ ] Docs actualizados: `docs/messaging.md` (campo nuevo) y el flujo de contacto en la doc de API.
- [ ] Sin migraciones, sin dependencias nuevas, sin cambios de seguridad.
