# ADR-004 — Autenticación stateless con JWT + BCrypt + roles

**Estado:** Aceptado (implementado en `com.claseya.security`).

## Contexto
Se necesita autenticación sin sesiones para una API consumida por un frontend SPA.

## Decisión
Autenticación **stateless con JWT** (HMAC firmado; claims `sub`, `email`, `role`, `iat`, `exp`).
Contraseñas con **BCrypt**. `SessionCreationPolicy.STATELESS`; CSRF deshabilitado (API sin
cookies). Autorización por roles con authorities `ROLE_STUDENT/ROLE_TEACHER/ROLE_ADMIN` (mapeo
único en `AppUserDetails`). El filtro JWT recarga el usuario por id en cada request y exige
`status=ACTIVE`.

## Consecuencias
- + Escalable y simple para SPA; sin estado de sesión en servidor.
- - Los tokens no son revocables salvo por expiración/cuenta deshabilitada (mitigado recargando el
  usuario en cada request).
- - Secretos (`JWT_SECRET`) deben rotarse/protegerse; hoy hay defaults solo de desarrollo.
- - FUTURO: refresh tokens, activación de cuentas, MFA (documentado, no implementado).
