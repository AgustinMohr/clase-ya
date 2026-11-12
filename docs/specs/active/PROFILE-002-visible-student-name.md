# PROFILE-002 — Nombre visible del estudiante

**ID:** PROFILE-002
**Estado:** En review
**Origen:** auditoría de lanzamiento (2026-11-11): el registro por email no pedía nombre y
`student_profiles` no lo guardaba. El docente veía “Sin nombre”/“Un estudiante” en la conversación y
en el email de contacto.
**Nivel:** 2 (contrato de API + producto).

---

## 1. Objetivo

Que toda cuenta creada por email tenga un nombre visible y que el estudiante pueda corregirlo desde
su perfil, sin exponer email ni otros datos personales.

## 2. Contexto

`users.name` existe desde `V3` y alimenta `displayName` en búsqueda, favoritos, mensajería y
reviews. Google OAuth y el perfil de profesor ya lo completan. El registro email+contraseña no lo
pedía y `StudentProfile` no tenía campo de nombre. El PRD §6 pide “datos mínimos (nombre visible;
ciudad opcional)”.

La auditoría confirmó que un estudiante registrado por email podía contactar con `users.name = NULL`;
el email de contacto decía “Un estudiante” y la conversación no mostraba identidad.

## 3. Actores

- **Estudiante:** completa su nombre al registrarse o al completar/editar su perfil.
- **Docente:** ve el nombre en la conversación, el email de contacto y las opiniones.
- **Sistema:** exige un nombre no vacío antes de iniciar una conversación.

## 4. Requisitos funcionales

- **RF-1** El registro email+contraseña acepta `name` y lo persiste en `users.name` (trim, sin
  espacios externos).
- **RF-2** La creación y actualización del perfil de estudiante acepta `name` y actualiza
  `users.name` cuando viene no vacío.
- **RF-3** `StudentProfileResponse` incluye `name`.
- **RF-4** La SPA pide “Nombre y apellido” en el registro y en el perfil de estudiante; no permite
  guardar vacío.
- **RF-5** Si un estudiante tiene perfil pero `users.name` está vacío, la SPA pide completar el
  perfil antes de contactar/guardar favoritos.
- **RF-6** El backend rechaza iniciar una conversación si el estudiante no tiene nombre visible
  (`409`), aunque el perfil académico exista.
- **RF-7** El nombre se usa en `ConversationParticipantResponse`, el email de contacto y
  `ReviewResponse.authorDisplayName`; nunca se expone el email.

## 5. Requisitos no funcionales

- Sin migración: `users.name` ya existe.
- Sin N+1: el nombre sale de la entidad `User` ya cargada por el flujo.
- Validación `@Size(max=255)` en los DTOs.

## 6. Seguridad

- El nombre es el único dato personal nuevo expuesto; email, dirección y coordenadas siguen fuera
  de DTOs públicos.
- El nombre se resuelve desde `CurrentUser`/`StudentProfile.user`, nunca desde un `userId` del
  cliente.
- El nombre no se loguea en el email de contacto: el link no incluye datos del estudiante.

## 7. Invariantes

- Un estudiante que inicia una conversación tiene `users.name` no vacío.
- Un perfil de estudiante no puede borrar el nombre enviando `name` vacío; conserva el guardado.
- El nombre mostrado a terceros es `users.name`, no el email.

## 8. API contract

- `POST /api/auth/register` request agrega `name` (opcional para clientes viejos, obligatorio en la
  SPA). Respuesta sigue siendo `202` sin body.
- `POST /api/students/profile` y `PUT /api/students/me` agregan `name` (opcional; si viene, se
  actualiza).
- `StudentProfileResponse` agrega `name`.
- `POST /api/conversations` responde `409` si el perfil existe pero el nombre está vacío.

## 9. Datos afectados

`users.name` (ya existente). Sin migración, sin índice nuevo, sin tabla nueva.

## 10. Criterios de aceptación

- **CA-1** Registro con `name: "  Ana Estudiante  "` → `users.name = "Ana Estudiante"`.
- **CA-2** `POST /api/students/profile` con nombre → respuesta y `GET /me` devuelven el nombre.
- **CA-3** `PUT /api/students/me` con nombre nuevo → actualiza `users.name`.
- **CA-4** Estudiante con perfil sin nombre → `POST /api/conversations` responde `409`.
- **CA-5** Estudiante con nombre → la conversación devuelve `displayName` no vacío y el email de
  contacto usa ese nombre.
- **CA-6** La SPA no envía el formulario de registro/perfil con nombre vacío.
- **CA-7** `local`/`test` y los flujos de Google siguen funcionando.

## 11. Out of scope

- Cambiar el modelo de perfil académico (universidad/carrera) — se trata en otra spec.
- Editar el nombre desde administración.
- Validar que el nombre sea real o único.
- Cambiar el email de contacto o la plantilla visual.

## 12. Definition of Done

- [ ] Backend, frontend y tests actualizados.
- [ ] `mvn -B test` verde en CI (integración con Docker).
- [ ] `npm run build`, `npm test` y `npm run lint` verdes.
- [ ] Verificado en un flujo real: registro → perfil → contacto → conversación con nombre.
