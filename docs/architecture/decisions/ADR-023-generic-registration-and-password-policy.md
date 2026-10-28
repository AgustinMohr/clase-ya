# ADR-023 — Registro genérico y política de contraseñas

**Estado:** Aceptado (AUTH-002, implementado)
**Fecha:** 2026-10-28
**Relacionado:** `AUTH-001` (activación), `RATE-001`, `SEC-004` de `review.md`.

## Contexto

`SEC-04` detectó dos problemas en el alta por contraseña:

1. `RegisterRequest` solo exigía `min = 8`: sin máximo, sin complejidad y sin límite de bytes.
   BCrypt usa solo los primeros 72 **bytes**, así que una contraseña más larga se truncaba en
   silencio.
2. `POST /api/auth/register` respondía `409` para un email existente, a diferencia del login (que es
   genérico). Eso permitía enumerar cuentas registradas sin costo, y el rate limiting por IP no
   eliminaba la señal.

## Decisión

1. **Política de contraseñas:** mínimo 10 caracteres, al menos una letra y un dígito, máximo 72
   bytes UTF-8. Se implementa en `@ValidPassword`, no con `@Size`, porque el límite que importa es el
   de BCrypt.
2. **Registro genérico:** `POST /api/auth/register` responde **`202` sin body** siempre. Un email
   nuevo crea la cuenta `PENDING`; uno `PENDING` recibe un enlace nuevo; uno `ACTIVE` no recibe
   nada. La contraseña de una cuenta existente **nunca** se modifica desde este endpoint: el dueño
   del email podría no ser quien llama.
3. **La carrera de duplicados no filtra:** la UNIQUE sigue siendo la última defensa y la respuesta
   también es `202`.
4. **HIBP y CAPTCHA quedan pendientes:** agregarlos implica una dependencia externa, timeouts y una
   política de fail-open/fail-closed que merece su propio cambio. El rate limiting ya existe y la
   política de longitud/complejidad cubre el mínimo.

## Consecuencias

- **Positivas:** el registro deja de enumerar cuentas; el límite de BCrypt se respeta; una cuenta
  pendiente puede recuperar el enlace sin cambiar su contraseña.
- **Costos:** cambio de contrato (`201` + body → `202` sin body); los clientes deben tratar el
  `202` como éxito genérico. Queda un canal de **timing** residual: un email nuevo envía correo y un
  `ACTIVE` no, así que los tiempos difieren. Se acepta por ahora; la mitigación real sería un envío
  asíncrono uniforme.
- **Deuda:** contraseñas filtradas (HIBP) y CAPTCHA. Hasta entonces, la defensa es longitud +
  composición + rate limiting.

## Alternativas consideradas

- **Mantener `409` + CAPTCHA:** requiere un proveedor externo y no elimina la señal del status sin
  CAPTCHA.
- **zxcvbn en el frontend:** se puede saltear llamando la API directo; no es una defensa del
  backend.
- **HIBP ahora:** valioso, pero suma una llamada HTTP en el registro y una decisión de falla
  (bloquear altas si HIBP está caído vs. permitirlas). Se difiere.
