# ADR-021 — Registro con email + contraseña y activación por email

**Estado:** Aceptado (AUTH-001, implementado)
**Fecha:** 2026-10-28
**Relacionado:** `OAUTH-001` (D-1 convivencia), `ADR-020` (cookie de sesión), `RATE-001`.

## Contexto

`OAUTH-001` D-1 decidió que Google OAuth y email + contraseña **convivieran**, y el PRD exige Google
sin excluir el segundo camino. Pero el alta por contraseña quedó a mitad de camino: creaba cuentas
`PENDING` y no existía ninguna forma de activarlas. Como `GoogleAuthService.requireActive` rechaza
las cuentas `PENDING`, una persona que se registraba con contraseña no podía entrar por ningún
camino. La UI, además, ofrecía un login por contraseña que nunca podía funcionar para una cuenta
nueva.

El hallazgo `AUTH-01` de `review.md` pidió cerrar esa brecha.

## Decisión

1. **El camino email + contraseña queda completo y soportado**, en segundo lugar respecto de Google
   (que sigue siendo el mecanismo principal del PRD). No se elimina ni se aísla.
2. **Activación por token de un solo uso enviado por email.** El registro crea la cuenta `PENDING`
   y emite un token de 32 bytes aleatorios. La base guarda solo su SHA-256; el token en claro vive
   únicamente en el enlace. Vence a las 24 h y se consume al activar.
3. **Reenvío con respuesta genérica.** `POST /api/auth/resend-activation` responde `202` siempre;
   solo emite un token nuevo si la cuenta existe y sigue `PENDING`, e invalida los anteriores.
4. **La activación pone `status=ACTIVE` y registra `email_verified_at`.** No se agrega un estado
   nuevo. Un ADMIN puede activar manualmente (`POST /api/admin/users/{id}/activate`) como salida
   operativa; esa vía **no** marca el email como verificado.
5. **El registro conserva `409` para un email existente.** Convertirlo en una respuesta genérica es
   parte de `SEC-04` y se mantiene fuera de este cambio para no mezclar hallazgos.
6. **Transporte SMTP.** Se agrega `spring-boot-starter-mail`. Sin `spring.mail.host` configurado, el
   registro responde `503` y la transacción se revierte: nunca queda una cuenta que su dueño no
   puede activar. En desarrollo, `app.auth.activation.log-links=true` escribe el enlace en el log
   como último recurso; está deshabilitado por defecto y prohibido fuera de desarrollo.
7. **Los orígenes de Google se configuran en Google Cloud Console.** `origin_mismatch` no es un
   defecto del código: es un origen ausente en *Authorized JavaScript origins*. La UI muestra el
   origen actual y la ruta exacta a configurar; el despliegue debe registrar el origen público.

## Consecuencias

- **Positivas:** el alta por contraseña funciona de punta a punta; la evidencia de verificación de
  email queda auditada; el flujo resiste caídas de SMTP sin dejar cuentas huérfanas; no se toca el
  login con Google.
- **Costos:** una dependencia nueva (`spring-boot-starter-mail`), una migración (`V9`), configuración
  operativa de SMTP y de `APP_BASE_URL`, y una tabla nueva que conviene purgar periódicamente.
- **Riesgos asumidos:** el envío ocurre dentro de la transacción de registro (un SMTP lento alarga el
  request); no hay recuperación de contraseña ni cambio de email; el rate limit del reenvío comparte
  el bucket de registro por IP.
- **Reversión:** quitar la dependencia y la migración no es trivial una vez aplicada; la alternativa
  segura es deshabilitar el alta por contraseña en la UI y dejar el endpoint detrás de un flag.
