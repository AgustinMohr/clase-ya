# OPS-004 — Fail-fast de configuración de despliegue

**ID:** OPS-004
**Estado:** En review
**Origen:** auditoría de lanzamiento (2026-11-11): el backend podía arrancar “UP” con
`APP_BASE_URL` o `FRONTEND_URL` de desarrollo, cookies inseguras o sin transporte de email.
**Nivel:** 2 (seguridad + operación).

---

## 1. Objetivo

Hacer que un despliegue fuera de `local`/`test` no arranque si falta una configuración que rompe
registro, activación, login, CORS o enlaces públicos. El fallo debe ser explícito al arrancar, no
un `202`/`503` silencioso para el primer cliente.

## 2. Contexto

`ProductionConfigValidator` ya validaba `JWT_SECRET` (en su validador propio), CAPTCHA, escaneo y
`DEMO_SEED`. No validaba:

- `APP_BASE_URL`: base de los enlaces de activación y del canonical/sitemap.
- `FRONTEND_URL`: origen CORS; el default son dos `localhost`.
- `COOKIE_SECURE` / `COOKIE_SAME_SITE`: cookies de sesión/CSRF.
- transporte de email (`EMAIL_TRANSPORT`, `MAIL_HOST`, `EMAIL_API_KEY`): sin él el registro
  responde `503` o el outbox queda `DEAD` después de aceptar la cuenta.

El default de `application.yml` es seguro para desarrollo, pero inseguro para producción.

## 3. Actores

- **Operador de despliegue:** define las variables y recibe el error de arranque.
- **Sistema:** valida la configuración antes de aceptar tráfico.
- **Usuario:** no llega a un flujo de registro/activación roto.

## 4. Requisitos funcionales

- **RF-1** Fuera de `local`/`test`, `APP_BASE_URL` debe ser un origen HTTPS público (no
  `localhost`, `127.0.0.1`, `0.0.0.0` ni `::1`).
- **RF-2** `FRONTEND_URL` debe contener al menos un origen y todos deben ser HTTPS públicos.
- **RF-3** `COOKIE_SECURE` debe ser `true`.
- **RF-4** `COOKIE_SAME_SITE` debe ser `Lax`, `Strict` o `None`; `None` exige `COOKIE_SECURE=true`.
- **RF-5** `EMAIL_TRANSPORT` debe ser `smtp` o `brevo`. Con `smtp`, `MAIL_HOST` no puede estar
  vacío; con `brevo`, `EMAIL_API_KEY` no puede estar vacío.
- **RF-6** Todos los problemas se reportan juntos en un único `IllegalStateException` al arrancar.
- **RF-7** En `local` y `test` no se valida nada de lo anterior.

## 5. Requisitos no funcionales

- Validación pura sobre `Environment`; sin llamadas de red ni de base.
- Sin dependencias nuevas.
- Mensajes accionables que nombran la variable de entorno.

## 6. Seguridad

- Evita cookies de sesión sobre HTTP y orígenes CORS de desarrollo en producción.
- Evita enlaces de activación apuntando a `localhost`.
- Evita un despliegue que acepte registros sin poder entregar el email de activación.
- No expone secretos: solo nombres de variables y valores de URL no sensibles.

## 7. Invariantes

- Un entorno de producción no puede quedar `UP` con `APP_BASE_URL` o `FRONTEND_URL` de desarrollo.
- Un entorno de producción no puede quedar `UP` sin un transporte de email configurado.
- `COOKIE_SAME_SITE=None` nunca convive con `COOKIE_SECURE=false`.

## 8. API contract

No cambia ningún endpoint. Cambia el resultado del arranque: configuración insegura → la aplicación
no levanta y el orquestador marca el deploy como fallido.

## 9. Datos afectados

Ninguno. No hay migración ni cambio de schema.

## 10. Criterios de aceptación

- **CA-1** Configuración completa (`APP_BASE_URL=https://…`, `FRONTEND_URL=https://…`,
  `COOKIE_SECURE=true`, `EMAIL_TRANSPORT=smtp` + `MAIL_HOST`) arranca.
- **CA-2** `APP_BASE_URL` o `FRONTEND_URL` con `localhost`/`http://` falla con mensaje que nombra
  la variable.
- **CA-3** `COOKIE_SECURE=false` falla; `SameSite=None` sin Secure falla.
- **CA-4** `smtp` sin `MAIL_HOST` y `brevo` sin `EMAIL_API_KEY` fallan.
- **CA-5** Los perfiles `local` y `test` arrancan con los defaults de desarrollo.
- **CA-6** Los problemas se listan juntos, no uno por arranque.

## 11. Out of scope

- Validar que el proveedor SMTP/Brevo acepte credenciales o que el remitente esté verificado.
- Validar DNS, TLS o disponibilidad del frontend.
- Cambiar defaults de `application.yml`.
- Chequear `RATE_LIMIT_TRUST_FORWARDED_HEADERS` (depende del proxy, no siempre es Vercel).

## 12. Definition of Done

- [ ] `ProductionConfigValidator` valida los seis grupos y los tests cubren cada fallo.
- [ ] README del guard de arranque actualizado.
- [ ] `mvn -B test` verde en CI.
- [ ] Deploy de producción revisado con las variables completas.
