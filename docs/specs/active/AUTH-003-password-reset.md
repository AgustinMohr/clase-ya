# AUTH-003 — Recuperación de contraseña

**ID:** AUTH-003
**Estado:** En review
**Origen:** auditoría de lanzamiento (2026-11-11): no existía recuperación de contraseña. Un usuario
que la olvidaba quedaba bloqueado.
**Nivel:** 2 (autenticación + contrato de API + schema).

---

## 1. Objetivo

Permitir que un usuario activo restablezca su contraseña con un enlace de un solo uso enviado por
email, sin revelar si el email está registrado y sin romper el flujo de activación existente.

## 2. Contexto

El registro por email exige activación; el login usa BCrypt y JWT. No había endpoint ni token de
reset. `email_verification_tokens` es para activación y `message_link_tokens` para contacto: mezclar
propósitos habría acoplado TTL y revocación. Se agrega una tabla propia y un evento de outbox.

## 3. Actores

- **Usuario activo:** pide el enlace y elige una nueva contraseña.
- **Sistema:** emite, invalida y consume tokens; envía el email por el outbox.
- **Operador:** monitorea outbox `DEAD` y la entrega del proveedor.

## 4. Requisitos funcionales

- **RF-1** `POST /api/auth/forgot-password` acepta `{ email }` y responde siempre `202`.
- **RF-2** Solo una cuenta `ACTIVE` recibe un enlace; PENDING/INACTIVE/SUSPENDED no se modifican.
- **RF-3** El token es de 32 bytes aleatorios; la base guarda solo SHA-256.
- **RF-4** TTL por defecto 30 minutos (`AUTH_PASSWORD_RESET_TTL`); un solo uso.
- **RF-5** Un nuevo pedido invalida los tokens pendientes del mismo usuario.
- **RF-6** `POST /api/auth/reset-password` acepta `{ token, newPassword }` y responde `204`.
- **RF-7** La nueva contraseña cumple la política existente (10+, letra y número, ≤72 bytes BCrypt).
- **RF-8** Un token inválido, vencido, usado o de cuenta no activa responde `400` genérico.
- **RF-9** El email se envía por el outbox (`PASSWORD_RESET`), con el link
  `${APP_BASE_URL}/restablecer?token=...`.
- **RF-10** Si no hay transporte de email, la solicitud responde `503` antes de buscar la cuenta.
- **RF-11** La SPA agrega “¿Olvidaste tu contraseña?” en el login, una pantalla de solicitud y una de
  nueva contraseña.

## 5. Requisitos no funcionales

- El consumo del token es atómico (`UPDATE ... WHERE used_at IS NULL`).
- Sin N+1; una fila de token y una de usuario.
- Rate limit: `forgot-password` y `reset-password` comparten el bucket de registro por IP.
- Migración Flyway nueva (`V21`), sin editar históricas.

## 6. Seguridad

- Anti-enumeración: misma respuesta `202` para email existente, inexistente o no activo.
- No se revela el estado de la cuenta en el reset; todos los fallos comparten mensaje.
- El token crudo solo vive en memoria, el email y el payload del outbox; el worker lo limpia al
  marcar `SENT`/`DEAD` (ADR-029).
- La nueva contraseña se hashea con BCrypt; nunca se loguea.
- El link de reset no se envía a terceros ni se expone en DTOs.
- Rate limit por IP reutiliza `REGISTER_IP` para no agregar configuración en esta fase.

## 7. Invariantes

- Un token usado no puede volver a usarse.
- Un token vencido no puede usarse.
- Un pedido nuevo invalida los pendientes anteriores.
- Un usuario no activo nunca recibe un token.
- La contraseña anterior deja de funcionar después del reset.
- La activación y el contacto conservan sus tokens y TTL independientes.

## 8. API contract

### Solicitar

```http
POST /api/auth/forgot-password
{ "email": "student@example.com" }

202 Accepted (no body)
```

Siempre `202` con transporte configurado; `503` si no hay transporte; `400` si el email es inválido.

### Restablecer

```http
POST /api/auth/reset-password
{ "token": "<raw-token>", "newPassword": "NewPassword123" }

204 No Content
```

`400` para token inválido/vencido/usado o contraseña inválida.

## 9. Datos afectados

Migración `V21__password_reset.sql`:

- Tabla `password_reset_tokens` (`user_id`, `token_hash` UNIQUE, `expires_at`, `used_at`,
  `created_at`), índice por `user_id`.
- Amplía el CHECK de `notification_outbox.event_type` con `PASSWORD_RESET`.
- Sin cambios en `users`.

## 10. Criterios de aceptación

- **CA-1** Un usuario activo recibe un token en el outbox y puede cambiar su contraseña.
- **CA-2** La contraseña anterior deja de validar; la nueva sí.
- **CA-3** El mismo token no se puede usar dos veces (`400`).
- **CA-4** Un email desconocido responde `202` y no encola nada.
- **CA-5** PENDING/SUSPENDED no reciben token.
- **CA-6** Un token inválido responde `400` genérico.
- **CA-7** Una contraseña débil responde `400` sin consumir el token.
- **CA-8** La SPA completa solicitud → email → nueva contraseña → login.
- **CA-9** `V21` aplica sobre una base existente sin perder datos.

## 11. Out of scope

- Cambio de email, MFA, refresh tokens.
- Invalidación global de sesiones JWT existentes (el JWT actual expira solo).
- Personalización del email o proveedor distinto de SMTP/Brevo.
- Rate limit por cuenta (se reutiliza el de IP de registro).

## 12. Definition of Done

- [ ] Migración `V21` + entidad + repositorio + servicio + outbox + worker.
- [ ] Endpoints y pantallas de solicitud/reset.
- [ ] Tests de integración para token, un solo uso, anti-enumeración y política.
- [ ] `npm run build`, `npm test`, `npm run lint` verdes.
- [ ] `mvn -B test` verde en CI con Testcontainers.
- [ ] Docs de API, base de datos y README actualizados.
