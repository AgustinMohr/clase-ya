# AUTH-004 — Sesión extendida y revocación en logout

**Estado:** Aprobada por el director e implementada (2026-11-11).
**Nivel:** 2 (seguridad, esquema y contrato de sesión).
**Origen:** `hallazgos-2026-11-11.md` 1.11.
**Relacionado:** `AUTH-001`, `AUTH-003`, `SEC-002`, `ADR-020`.

## 1. Objetivo

Evitar el re-login cada hora y hacer que el logout invalide la sesión en el servidor, no solo la
cookie del navegador.

## 2. Contexto

- `jwt.expiration` vale 3600 s; `LoginResponse.expiresIn` refleja ese valor.
- La cookie `CLASEYA_SESSION` no tiene `Max-Age` (vive lo que el navegador), pero el JWT expira a la
  hora y no hay refresh.
- `logout` solo borra la cookie; un token copiado sigue siendo válido hasta expirar.

## 3. Actores

- STUDENT / TEACHER / ADMIN con sesión iniciada.
- Sistema (filtro JWT y servicio de sesión).

## 4. Requisitos funcionales

- **RF-1** `jwt.expiration` default pasa a **43200 s (12 h)**; `JWT_EXPIRATION` sigue siendo la
  variable de entorno.
- **RF-2** El JWT incluye la versión de sesión del usuario (`ver`). El filtro la compara con
  `users.session_version`; si no coincide, el token no autentica.
- **RF-3** `POST /api/auth/logout` incrementa `session_version`, invalida todos los tokens emitidos
  antes y borra la cookie. Responde `204` aunque no haya sesión.
- **RF-4** Un reset de contraseña exitoso también incrementa `session_version`, cerrando sesiones
  viejas (AUTH-003).
- **RF-5** Login, Google login y link de contacto emiten tokens con la versión vigente.

## 5. Requisitos no funcionales

- La revocación no agrega consultas: `session_version` viaja en la fila `users` que el filtro ya
  carga.
- Migración aditiva con default 0, sin backfill.

## 6. Seguridad

- No se agrega PII al JWT; solo `sub`, `jti`, `iat`, `exp` y `ver`.
- El logout invalida **todas** las sesiones de la cuenta (decisión aceptada para este producto).
- Un token con `ver` viejo recibe `401` genérico, sin revelar el motivo.

## 7. Invariantes

- I1: un token emitido antes del último logout/reset nunca autentica.
- I2: un token emitido después del logout/reset autentica si la cuenta sigue ACTIVE.
- I3: `session_version` solo crece.
- I4: el logout no falla ni filtra si no hay sesión.

## 8. Datos afectados

Migración `V23__session_version.sql`: `alter table users add column session_version int not null
default 0;`. Sin tablas nuevas.

## 9. Criterios de aceptación

- **AC-1** Login devuelve `expiresIn: 43200` con la configuración por defecto.
- **AC-2** Tras logout, reutilizar el token anterior en `/api/auth/me` devuelve `401`.
- **AC-3** Un login posterior al logout funciona con el token nuevo.
- **AC-4** Reset de contraseña invalida sesiones anteriores.
- **AC-5** `JwtServiceTest` verifica que el claim `ver` existe y no hay email/rol.

## 10. Out of scope

- Refresh tokens con rotación y detección de reuso.
- Revocación por dispositivo/sesión individual.
- Lista de revocación por `jti`.
- Persistencia de la sesión más allá del cierre del navegador.

## 11. Definition of Done

- [x] Spec aprobada.
- [x] Migración `V23` y campo en `User`.
- [x] Claim `ver`, filtro y logout/reset implementados.
- [x] Tests unitarios/integración escritos.
- [x] Docs de seguridad y README actualizados.
- [ ] `mvn -B clean test` completo (Docker/CI).
