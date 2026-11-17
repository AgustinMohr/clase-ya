# Runbook: rollback y recuperación de ClaseYa

- Estado: **BORRADOR** hasta completar los campos marcados `COMPLETAR` con los datos reales del simulacro y obtener revisión de una segunda persona.
- Relacionado: `docs/production-readiness/07-remediation-tracker.md` (RT-04), `04-database-integrations-audit.md` (sección 8), `06-infrastructure-devsecops-audit.md` (I-04).
- Alcance: backend en Render, SPA en Vercel, base de datos en Neon, evidencia documental en PostgreSQL (`bytea`) o en un bucket S3/R2.
- Regla general: **nunca restaurar sobre la base de producción.** Se restaura a un branch nuevo, se verifica y recién entonces se reapunta la aplicación.
- No pegar connection strings, contraseñas ni claves en chats, tickets ni commits.

## 1. Objetivos acordados (RPO / RTO)

| Concepto | Valor | Fuente |
|---|---|---|
| Ventana de restauración de Neon (PITR) | 6 horas (tope de 1 GB de historial por proyecto) | Panel de Neon, plan actual |
| RPO objetivo | `24` (por ejemplo: pérdida máxima aceptable de datos) | Decisión del propietario |
| RTO objetivo | `8` | Decisión del propietario |
| Tiempo medido en el simulacro (inicio → app verificada contra el branch) | `15 minutos` | Simulacro del `17-11-2026 18:00hs` |

Límite conocido: un problema descubierto **después** de la ventana de 6 horas solo es recuperable con un snapshot manual o con un volcado propio. Ver sección 7.

## 2. Responsables y accesos

| Rol | Persona | Acceso necesario |
|---|---|---|
| Propietario / decisor | `Agustin` | Neon, Render, Vercel, GitHub |
| Suplente | `Agustin` | Los mismos, o instrucciones de contacto del propietario |

Credenciales: guardadas en un gestor de secretos, fuera del repositorio. Quien ejecute este runbook debe tener acceso a los paneles de Neon, Render y Vercel.

## 3. Cuándo usar este runbook

| Situación | Sección |
|---|---|
| Despliegue de backend o frontend con errores | 4 (rollback de aplicación) |
| Migración de Flyway fallida o que dañó datos | 5 (rollback de esquema/datos) |
| `UPDATE`/`DELETE` manual equivocado | 5 |
| Corrupción o pérdida de datos descubierta | 5 |
| Neon caído o base inaccesible | 6 |
| Credenciales o `JWT_SECRET` expuestos | 8 |
| Evidencia documental perdida o corrupta | 7 |

## 4. Rollback de aplicación (sin cambio de esquema)

Usar cuando el último despliegue introdujo un error pero la base no se modificó con una migración nueva.

**Backend (Render)**
1. Render → servicio del backend → pestaña de despliegues (Events/Deploys).
2. Identificar el último despliegue sano y usar la opción de redeploy/rollback a ese despliegue (según lo que ofrezca el plan; **verificar la opción exacta en el panel y anotarla aquí: `COMPLETAR`**).
3. Esperar a que el health check pase. El arranque en frío con Neon puede tardar hasta unos 200–300 segundos (`Dockerfile`, `start-period=300s`).
4. Comprobar `GET /actuator/health` y un login de prueba.

**Frontend (Vercel)**
1. Vercel → proyecto → Deployments.
2. Elegir el último despliegue sano de producción y promoverlo (Instant Rollback / Promote to Production; **confirmar el nombre de la opción en el panel: `COMPLETAR`**).
3. Comprobar `curl.exe -sI https://<dominio>/` (cabeceras `x-frame-options`, `content-security-policy`, `strict-transport-security`) y cargar la landing y `/buscar`.

**Compatibilidad:** si el despliegue a revertir incluyó una migración de Flyway ya aplicada, **no usar esta sección**: ir a la sección 5. Una versión anterior de la aplicación contra un esquema más nuevo solo es segura si la migración fue aditiva (columnas/tablas nuevas sin eliminar ni renombrar). Las migraciones V17 y V18 históricas no lo fueron.

Verificación posterior: login de una cuenta de prueba, búsqueda de profesores, envío de un mensaje, y comprobación de que no hay filas nuevas en `notification_outbox` con `status = 'DEAD'`.

## 5. Recuperación de datos o esquema con Neon (restaurar a un branch)

Usar ante migración defectuosa, borrado/modificación accidental o corrupción detectada **dentro de la ventana de 6 horas**.

### 5.1 Contención (primeros minutos)
1. Registrar la hora UTC de detección y la hora estimada del último estado bueno.
2. Reducir el daño: poner la aplicación en modo seguro. Opciones, de menor a mayor impacto: detener el auto-deploy en Render; suspender el servicio de Render para que no siga escribiendo (el frontend mostrará errores, pero los datos no se alteran más). Decisión del propietario.
3. No ejecutar más `UPDATE`/`DELETE` manuales hasta tener un branch de restauración.

### 5.2 Crear el branch de restauración
1. Neon → proyecto → Branches → crear un branch **desde la rama de producción**, eligiendo un instante anterior al incidente (dentro de la ventana) o el snapshot disponible. Nombre sugerido: `restore-YYYY-MM-DD-hhmm`.
2. Copiar la connection string **del branch** (endpoint directo para Flyway y verificación).
3. Anotar hora de creación y punto en el tiempo elegido.

### 5.3 Verificar el branch (en local, sin tocar producción)
Con `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` definidas solo en tu terminal (no pegarlas en chats):

```sql
select count(*) from users;
select max(installed_rank), max(version) from flyway_schema_history;
select status, count(*) from notification_outbox group by status;
select scan_status, count(*) from verification_documents group by scan_status;
```

Comprobar además, con el backend de la versión compatible (ver 5.4) arrancado en local contra el branch: login de una cuenta de prueba, búsqueda de profesores, conversaciones y mensajes existentes. Flyway no debe reportar migraciones pendientes ni fallidas (`ddl-auto=validate` hace fallar el arranque si el código espera columnas que el branch no tiene).

### 5.4 Versión de aplicación compatible
`ddl-auto=validate` exige que la versión de la aplicación coincida con el esquema restaurado. Antes de reapuntar:
- Identificar la última migración aplicada en el branch (`max(version)` arriba).
- Desplegar la versión de la aplicación correspondiente a ese estado (commit/tag cuyo `src/main/resources/db/migration` termine en esa versión).

### 5.5 Reapuntar producción al branch restaurado
1. Render → Environment: cambiar `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` por los del branch restaurado (si cambia el usuario/clave). Guardar los valores anteriores en el gestor de secretos.
2. Redeploy del backend. Esperar el health check.
3. Verificación completa (sección 9).
4. Mantener la base anterior (rama dañada) **sin borrar** hasta confirmar la restauración y haber extraído los datos que se quieran rescatar.

### 5.6 Datos creados después del punto restaurado
Los registros creados entre el punto restaurado y el incidente se pierden en el branch. Si importan, exportarlos de la rama dañada **antes** de descartarla (consultas de solo lectura) y reinsertarlos manualmente con revisión, o comunicar a los usuarios afectados (por ejemplo, cuentas registradas en ese intervalo).

### 5.7 Cierre
Registrar el incidente: causa, hora de detección, hora de restauración, datos perdidos, acciones preventivas. Actualizar este runbook con los tiempos reales.

## 6. Neon caído o base inaccesible

1. Revisar el estado del proveedor (página de estado de Neon) y el panel del proyecto.
2. Si el cómputo está suspendido por inactividad (autosuspend), la primera conexión lo reactiva; esperar el arranque y reintentar. El backend puede tardar en arrancar mientras la base despierta.
3. Si el problema es de la aplicación (agotamiento de conexiones): revisar logs de Render por errores de pool y el límite de conexiones del plan; reiniciar el servicio.
4. Si Neon sufre una caída prolongada: no hay réplica ni failover (limitación conocida, ver `06` I-04). Comunicar la indisponibilidad. Opción de contingencia: restaurar el último volcado propio (sección 7) en otro proveedor de PostgreSQL 16 y reapuntar `DB_URL`; requiere que el volcado exista y haya sido probado (**estado actual: `COMPLETAR`**).

## 7. Respaldos adicionales y evidencia documental

**Snapshot manual obligatorio antes de:**
- cada migración nueva de Flyway (cada `V{n+1}`),
- cada `UPDATE`/`DELETE`/DDL manual en la consola de Neon,
- cada cambio de rol o de datos de cuentas por SQL.

**Volcado propio (`pg_dump`)**: la ventana de 6 horas no protege contra problemas descubiertos más tarde. Recomendado: volcado periódico (por ejemplo diario) a un almacenamiento fuera de Neon, con retención definida y prueba de restauración trimestral. Estado actual: `COMPLETAR` (si no existe, queda pendiente).

**Evidencia documental:**
- Si `STORAGE_PROVIDER=postgres`: los PDF están dentro del backup de Neon (y de los volcados); la restauración los incluye.
- Si `STORAGE_PROVIDER=s3` (R2/S3): el backup de Neon **no** cubre los objetos. Verificar en el bucket: versioning activo, política de lifecycle, retención y acceso privado. Estado actual: `COMPLETAR`.
- Tras restaurar la base, comprobar que las filas de `verification_documents` con `storage_key` apuntan a objetos existentes y reconciliar huérfanos o referencias rotas.

## 8. Credenciales o secretos expuestos

1. Rotar inmediatamente la credencial expuesta en su proveedor: contraseña de Neon, API key de Brevo, claves de R2, secret de Turnstile, `JWT_SECRET`.
2. Actualizar la variable en Render y redeployar.
3. **Rotar `JWT_SECRET` invalida todas las sesiones**: los usuarios deberán iniciar sesión de nuevo; avisar si corresponde.
4. Los enlaces de un solo uso emitidos y registrados en logs (activación 24 h, restablecimiento 30 min, contacto 7 días) deben considerarse expuestos hasta que venzan; si hay sospecha de uso indebido, invalidar los tokens pendientes.
5. Revisar los logs de acceso de la plataforma afectada y el historial de Git por la credencial.

## 9. Verificación posterior a cualquier recuperación

| Comprobación | Cómo | Resultado esperado |
|---|---|---|
| Salud del backend | `GET /actuator/health` | `UP` |
| Cabeceras del frontend | `curl.exe -sI https://<dominio>/` | `x-frame-options: DENY`, `content-security-policy: frame-ancestors 'none'`, HSTS, `nosniff` |
| Login | Cuenta de prueba | Sesión iniciada |
| Búsqueda pública | `/buscar` | Profesores listados |
| Mensajería | Conversación existente y envío de un mensaje | Lectura y envío correctos |
| Correo | Registro de una cuenta de prueba | Correo de activación recibido; sin filas `DEAD` nuevas |
| Evidencia | Descarga de un documento por un ADMIN | Archivo correcto |
| Flyway | `select max(version) from flyway_schema_history` | Coincide con la versión esperada |
| Perfil de producción | Variables de Render | Sin `SPRING_PROFILES_ACTIVE`; `DEMO_SEED=false` |

## 10. Simulacro (registro)

| Fecha | Participantes | Branch usado | Punto restaurado | Inicio | Fin | Duración (RTO observado) | Resultado | Observaciones |
|---|---|---|---|---|---|---|---|---|
| `17-11-2026` | `Agustin` | `restore-test-17-11-26` | `17-11-26 13:09:52` | `17:55` | `18:10` | `15 minutos` | `Se restauró al snapshot correctamente` | `Ninguna` |

Repetir el simulacro: tras cada cambio relevante de infraestructura y como mínimo cada trimestre.

## 11. Mantenimiento de este documento

- Revisar tras cualquier cambio de plan en Neon, Render o Vercel, o de proveedor de almacenamiento.
- Cada incidente real debe actualizar la sección 5 con los tiempos observados.
- Cierre de RT-04: simulacro registrado (sección 10), campos `COMPLETAR` resueltos, revisión por una segunda persona y RPO/RTO acordados.
