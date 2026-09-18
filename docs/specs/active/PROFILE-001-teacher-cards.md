# PROFILE-001 — Perfil público del profesor y tarjetas de búsqueda

**ID:** PROFILE-001
**Estado:** Activa · para implementación (prototipo funcional UI)
**Carpeta:** `docs/specs/active/`
**Referencia:** `docs/product/product-requirements.md` (tarjetas de profesor con foto, rating,
ubicación, precio por hora, modalidad, disponibilidad).

---

## 1. Objetivo

Que la tarjeta pública de un profesor (`GET /api/teachers`) y su detalle
(`GET /api/teachers/{id}`) expongan los campos mínimos que necesita la UI: **precio por hora,
ciudad/zona, foto**, junto a los ya existentes (nombre, bio, rating, modalidades, materias,
disponibilidad declarada mañana/tarde/noche). Alcance mínimo para el prototipo funcional.

## 2. Contexto

- `TeacherProfile` hoy: bio, address, lat/long, `availabilityNote`, rating. **Sin** precio, foto,
  ni ciudad propia. `TeacherSummaryResponse`/`TeacherPublicDetailResponse` no los traen.
- `GET /api/teachers?subjectId=` y `GET /api/teachers/{id}` ya existen y están verificados.
- No hay reviews activas: el rating mostrará "sin opiniones" hasta REVIEW-001.

## 3. RF

- RF-1 El profesor puede cargar (create/update) `pricePerHour`, `city`, `photoUrl`.
- RF-2 La tarjeta (`TeacherSummaryResponse`) y el detalle público (`TeacherPublicDetailResponse`)
  incluyen esos campos (nullable) + las modalidades y la disponibilidad declarada por día
  (mañana/tarde/noche derivada de `AvailabilityWindow`, ya persistida) cuando aplique.
- RF-3 Ningún endpoint público expone datos privados (email/address/coords exactas se mantienen
  ocultos).

## 4. Seguridad / ownership

Igual que el perfil: CurrentUser + ownership; profesor VERIFIED+ACTIVE visible en público.

## 5. Datos / migración

`V7__teacher_cards.sql` (o siguiente disponible al implementar):
`ALTER TABLE teacher_profiles ADD COLUMN price_per_hour numeric(10,2), ADD COLUMN city varchar(100), ADD COLUMN photo_url varchar(500);` (nullable). No toca `V1..V6`.

## 6. Tests

- Crear/actualizar perfil con los nuevos campos; aparecen en tarjeta/detalle público; no se filtran
  datos privados; regresión de búsqueda.

## 7. Out of scope

Reviews/rating (REVIEW-001), carga de fotos propia (almacenamiento), nivel/público del catálogo
(se decide por separado), disponibilidad por franja horaria fina, UI (prototipo aparte en
`frontend/`).

## 8. DoD

Campos en entity/DDL/requests/responses; tarjeta/detalle los exponen; tests verdes
(`mvn -B clean test`); prototipo UI muestra la tarjeta.
