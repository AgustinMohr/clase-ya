# AUTH-002 — Política de contraseñas y registro genérico

**ID:** AUTH-002
**Estado:** Aprobada — en implementación (2026-10-28)
**Origen:** hallazgo `SEC-04` de `review.md`.
**Depende de:** `AUTH-001` (activación), `RATE-001` (rate limiting), `ADR-020`.

---

## 1. Objetivo

Cerrar las dos partes accionables de `SEC-04`:

1. La política de contraseñas era `min = 8` sin máximo, sin complejidad y sin límite de bytes (BCrypt
   trunca a 72 bytes).
2. `POST /api/auth/register` respondía `409` cuando el email existía, lo que permitía enumerar
   cuentas sin costo.

## 2. Decisiones

- **D1 — Política de contraseñas.** Mínimo 10 caracteres, al menos una letra y un dígito, y máximo
  **72 bytes UTF-8** (no 72 caracteres). Se implementa con `@ValidPassword`
  (`PasswordPolicyValidator`), no con `@Size` + `@Pattern`, porque el límite real de BCrypt es en
  bytes.
- **D2 — Registro genérico.** `POST /api/auth/register` responde **`202 Accepted` sin body** en
  todos los casos: email nuevo, email `PENDING` (se reenvía el enlace) o email ya `ACTIVE`
  (no se envía nada). Nunca se revela si la cuenta existe.
- **D3 — Nunca se cambia la contraseña de una cuenta existente.** Si el email ya está registrado, el
  request no modifica la contraseña: el dueño del email podría no ser quien llama. Una cuenta
  `PENDING` solo recibe un enlace nuevo.
- **D4 — La carrera de registro no filtra.** Si dos requests concurrentes compiten por el mismo
  email, la constraint UNIQUE gana y la respuesta sigue siendo `202` (no `409`).
- **D5 — HIBP/CAPTCHA quedan fuera de este cambio.** La verificación contra contraseñas filtradas
  (HaveIBeenPwned k-anonymity) y un CAPTCHA agregan una dependencia externa y un modo de falla que
  merecen su propia decisión. El rate limiting existente y la respuesta genérica cubren la
  enumeración; la política de longitud/complejidad cubre el mínimo. Se registra en `ADR-023` como
  pendiente explícito.

## 3. API

```
POST /api/auth/register
{ "email": "student@example.com", "password": "Password123", "role": "STUDENT" }

202 Accepted
(sin body)
```

| Caso | Respuesta |
|---|---|
| Email nuevo y válido | `202`, cuenta `PENDING`, enlace enviado |
| Email ya `PENDING` | `202`, se invalida el token anterior y se envía uno nuevo |
| Email ya `ACTIVE` | `202`, no se envía nada ni se toca la contraseña |
| Contraseña débil | `400` con `fieldErrors.password` |
| Rol `ADMIN` | `400` |
| Sin transporte de email | `503` y rollback (AUTH-001) |

## 4. Backend

- `auth/validation/ValidPassword` + `PasswordPolicyValidator`.
- `RegisterRequest.password` usa `@ValidPassword`.
- `AuthService.register` devuelve `void`, busca por email y aplica D2/D3/D4; `RegistrationConflictException`
  se traduce a `202` en `GlobalExceptionHandler`.
- Se eliminan `RegisterResponse` y `EmailAlreadyExistsException` (sin uso).

## 5. Frontend

- `api.register` devuelve `void`; se elimina `RegisterResponse`.
- `/registro` valida mínimo 10 + letras/números en cliente, muestra el aviso genérico y ya no
  distingue `409`.

## 6. Tests

1. Registro válido → `202`, usuario `PENDING`, BCrypt correcto.
2. Email `ACTIVE` → `202` y **sin** email de activación.
3. Email `PENDING` → `202`, token nuevo, token viejo invalidado, contraseña original intacta.
4. Contraseñas débiles (`9` caracteres, sin dígito, sin letra, >72 bytes) → `400`.
5. Unit del validador (límites de longitud/bytes/composición).
6. Rate limiting del registro sigue devolviendo `429` al superar el cupo.
7. E2E de registro sigue verde (el body ya no se usa).

## 7. Fuera de alcance

HIBP/zxcvbn, CAPTCHA, recuperación de contraseña, cambio de email, MFA, política para cuentas
creadas por Google.

## 8. Change budget

- **Scope:** validación de contraseña, contrato de registro genérico, frontend de registro, tests y
  documentación.
- **Archivos:** `RegisterRequest`, `AuthService`, `AuthController`, `GlobalExceptionHandler`,
  `RegisterResponse` (delete), `EmailAlreadyExistsException` (delete),
  `RegistrationConflictException` (new), `ValidPassword`/`PasswordPolicyValidator` (new), tests,
  `frontend/src/{api.ts,auth/AuthContext.tsx,pages/RegisterPage.tsx}`, `docs/auth.md`, `README.md`,
  `review.md`, `ADR-023`.
- **Migraciones:** ninguna.
- **Dependencias:** ninguna.

## 9. Definition of Done

- [x] Política aplicada y testeada (incluye el límite de 72 bytes).
- [x] Registro siempre `202` sin filtrar existencia; sin cambio de contraseña.
- [x] `RegisterResponse` y `EmailAlreadyExistsException` eliminados.
- [x] Frontend actualizado y build verde.
- [x] Suite backend y E2E verdes.
- [x] `ADR-023`, `docs/auth.md`, README y `review.md` actualizados.
