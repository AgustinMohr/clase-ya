# OPS-003 — Seguridad de datos demo en producción

**ID:** OPS-003
**Estado:** En review
**Origen:** auditoría de lanzamiento (2026-11-11): producción pública servía cuentas y perfiles del
seed demo, incluida `ana.profe@claseya.dev` con la contraseña documentada `Password123`.
**Nivel:** 2 (seguridad + datos + operación).

---

## 1. Objetivo

Impedir que una instalación fuera de `local`/`test` acepte el dataset demo como datos reales:
cuentas con contraseña conocida, perfiles ficticios y ratings de demostración. La aplicación debe
neutralizar esas cuentas sola al arrancar y dejar evidencia operativa de lo ocurrido.

## 2. Contexto

`db/seed/demo-data.sql` crea usuarios `@claseya.dev` con `Password123` y perfiles visibles. El seed
es opt-in (`DEMO_SEED=false` por defecto), pero una base sembrada antes de un deploy conserva esas
cuentas aunque el flag quede apagado. `ProductionConfigValidator` solo rechaza el flag activo, no
los datos ya existentes.

La verificación de la auditoría confirmó login real en producción con
`ana.profe@claseya.dev` / `Password123` (rol `TEACHER`). El dominio `@claseya.dev` es exclusivo del
dataset demo.

## 3. Actores

- **Sistema:** guard de arranque fuera de `local`/`test`.
- **Operador de despliegue:** revisa el warning y elimina los datos demo de forma definitiva.
- **Cuenta demo `@claseya.dev`:** no puede autenticarse ni aparecer en búsquedas públicas.

## 4. Requisitos funcionales

- **RF-1** Con perfiles activos distintos de `local`/`test`, al arrancar, toda cuenta cuyo email
  termine en `@claseya.dev` y no esté ya `SUSPENDED` pasa a `SUSPENDED`.
- **RF-2** La contraseña de esas cuentas se reemplaza por un hash BCrypt aleatorio e inutilizable.
- **RF-3** Las cuentas reales (cualquier otro dominio) no se modifican.
- **RF-4** La operación es idempotente: un segundo arranque no vuelve a modificar cuentas ya
  `SUSPENDED` ni cambia hashes.
- **RF-5** Si se modificó al menos una cuenta, el sistema escribe un `WARN` con la cantidad y la
  instrucción de eliminar los datos demo.
- **RF-6** En `local` y `test` el guard no ejecuta ninguna escritura.

## 5. Requisitos no funcionales

- Una sola sentencia SQL de actualización por arranque; sin N+1 ni carga de entidades.
- Sin dependencias nuevas.
- El guard corre después de Flyway y de la inicialización de JPA, cuando la base ya está lista.

## 6. Seguridad

- `status=SUSPENDED` hace que `AppUserDetails.isEnabled()` sea falso: cualquier JWT demo vigente
  deja de autenticar en la siguiente request.
- La visibilidad pública de profesores exige `user.status=ACTIVE`; un perfil demo suspendido deja
  de aparecer en búsqueda, favoritos, sitemap y detalle público (404).
- La contraseña conocida deja de ser válida.
- No se exponen emails, hashes ni tokens en logs; solo la cantidad de cuentas neutralizadas.

## 7. Invariantes

- Ninguna cuenta `@claseya.dev` puede quedar `ACTIVE`, `PENDING` o `INACTIVE` fuera de
  `local`/`test` después del arranque.
- Ninguna cuenta ajena al dominio demo cambia de estado, hash o nombre.
- El guard no borra filas: la eliminación definitiva es una decisión operativa separada.

## 8. API contract

No cambia ningún endpoint. Efecto observable: login demo responde `401`; perfil público demo
responde `404`; búsqueda pública no devuelve perfiles demo.

## 9. Datos afectados

`users.status`, `users.password_hash`, `users.updated_at` para emails `%@claseya.dev`.
Sin migración ni índice nuevo. Sin cambio de schema.

## 10. Criterios de aceptación

- **CA-1** Con una cuenta demo `ACTIVE` y contraseña `Password123`, tras el guard el login responde
  `401` y `password_hash` ya no valida `Password123`.
- **CA-2** Un usuario real `ACTIVE` conserva estado y contraseña.
- **CA-3** Un segundo arranque no modifica la cuenta demo ya suspendida.
- **CA-4** Perfiles `local` y `test` no ejecutan la actualización.
- **CA-5** El perfil público de un docente demo deja de ser visible.
- **CA-6** El log contiene un `WARN` con la cantidad neutralizada, sin datos personales.

## 11. Out of scope

- Borrado físico de conversaciones, mensajes, reviews o documentos demo.
- Limpieza automática de la base desde la aplicación.
- Cambios al seed, al catálogo académico o a la UI.
- Rotación de secretos de infraestructura.

## 12. Rollout

1. Mergear el cambio.
2. Desplegar: el guard neutraliza las cuentas demo existentes al arrancar.
3. Revisar logs por el `WARN` de OPS-003.
4. Programar el borrado definitivo de filas `@claseya.dev` con el operador de base.

## 13. Definition of Done

- [ ] Guard implementado y testeado (unitario + integración).
- [ ] Spec y README de despliegue actualizados.
- [ ] `mvn -B test` verde en CI con Docker.
- [ ] Verificado que un login demo de producción deja de funcionar después del deploy.
