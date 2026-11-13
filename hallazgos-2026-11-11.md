# Hallazgos de auditoría — ClaseYa

**Fecha:** 2026-11-11
**Alcance:** lanzamiento a producción y adquisición de primeros clientes.
**Entorno público auditado:** `https://clase-ya.vercel.app` + `https://clase-ya-api.onrender.com`.
**Stack real:** Spring Boot 3.5 + React 18/Vite + PostgreSQL (Render + Vercel). No hay Next.js,
FastAPI ni SQLite en el repositorio. El flujo crítico es búsqueda → contacto → mensajería; no hay
reserva ni pago por diseño.

---

## 1. Bloqueadores de lanzamiento (Must-Have)

### 1.1 Demo en producción y credenciales conocidas — CRÍTICO

**Estado: RESUELTO en código (2026-11-11).** `DemoDataProductionGuard` + `DemoDataQuarantineService`
suspenden y rotan el hash de toda cuenta `@claseya.dev` fuera de `local`/`test`; los profesores
suspendidos quedan ocultos porque la búsqueda exige `user.status = ACTIVE`. Verificado con
`DemoDataProductionGuardTest`. La contención manual en Neon y el deploy siguen siendo operativos
(humanos).

El entorno público servía el dataset demo: 4 docentes con nombres, fotos `pravatar`, rating
`4.70/12` y `verificationStatus=VERIFIED`, exactamente los de `db/seed/demo-data.sql` §8.

La cuenta `ana.profe@claseya.dev` autenticó con la contraseña del seed documentada en el
repositorio (rol `TEACHER`). El seed crea además otras cuentas demo con la misma clave. Es
suplantación de identidad, lectura/escritura de mensajes y oferta ficticia.

- Evidencia: `GET /api/teachers` devolvía `totalElements=4`; el login demo respondía `200`.
- **Resuelto en código:** PR #44 (`DemoDataProductionGuard`) suspende cuentas `@claseya.dev` y rota
  su hash fuera de `local`/`test`.
- **Pendiente operativo:** deployar #44 o ejecutar la cuarentena manual en Neon. Hasta entonces,
  producción sigue vulnerable.

SQL de contención inmediata (ejecutar en Neon antes del deploy):

```sql
UPDATE users
SET status = 'SUSPENDED',
    password_hash = crypt(gen_random_uuid()::text, gen_salt('bf')),
    updated_at = now()
WHERE lower(email) LIKE '%@claseya.dev'
  AND status <> 'SUSPENDED';
```

### 1.2 Cero oferta real — CRÍTICO (negocio)

**Estado: ACCIÓN HUMANA.** No es implementable desde el repositorio: requiere reclutar y dar de alta
10–20 profesores reales y decidir el borrado definitivo de las filas demo. El agente no crea oferta
ni ejecuta acciones comerciales.

Los 4 docentes públicos son demo. No hay inventario real para convertir primeros clientes.
**Acción humana:** alta asistida de 10–20 profesores reales antes de abrir tráfico, y limpieza
definitiva de filas demo.

### 1.3 Onboarding de estudiante fuera del PRD — CRÍTICO

El perfil exigía universidad, carrera y año. Un alumno de primaria/secundaria, un padre o un adulto
no podía contactar sin inventar datos académicos; el PRD §6 pide perfil mínimo.

- **Resuelto en código:** PR #48. `V19` hace opcionales universidad/carrera/año y agrega `level`
  (`PRIMARIA`/`SECUNDARIA`/`TERCIARIO`/`UNIVERSIDAD`/`ADULTO`) y `city`. La UI pide nombre + nivel,
  y deja el bloque académico como opcional.

### 1.4 Oferta docente atada a carreras universitarias — CRÍTICO

Para publicar una materia había que elegir universidad → facultad → carrera. Un profesor de apoyo
escolar, primaria o inglés básico no encajaba.

- **Resuelto en código:** PR #49. `V20` permite `teacher_subjects.subject_id` directo (sin carrera),
  la búsqueda por `subjectId` matchea ambos caminos, y “Mi anuncio” reemplaza la cascada por un
  buscador de materias.

### 1.5 Estudiantes sin nombre visible — ALTO

El registro por email no pedía nombre y `student_profiles` no lo guardaba. El docente veía
“Sin nombre”/“Un estudiante” en la conversación y en el email de contacto.

- **Resuelto en código:** PR #46. Registro y perfil piden nombre; `StudentProfileResponse` lo
  devuelve; contactar sin nombre visible responde `409`.

### 1.6 Sin recuperación de contraseña — ALTO

No había endpoint ni token. Un usuario que olvidaba la clave quedaba bloqueado.

- **Resuelto en código:** PR #50. `V21` agrega `password_reset_tokens`; endpoints
  `forgot-password`/`reset-password`; token de un solo uso, SHA-256, TTL 30 min, anti-enumeración,
  outbox `PASSWORD_RESET` y pantallas `/recuperar` + `/restablecer`.
- **Estado:** PR abierto; CI encolado al cierre de este documento.

### 1.7 Mensajería sin aviso de respuestas — ALTO

**Estado: RESUELTO en código (2026-11-11).** `MessagesPage` hace polling cada 20 s solo con la
pestaña visible: refresca la lista (badge de no leídos y último mensaje) y fusiona los mensajes
nuevos del hilo abierto sin perder el historial cargado ni saltar el scroll si el usuario está
leyendo hacia arriba. Un mensaje nuevo estando al final marca la conversación como leída.
No se agregó email por mensaje: `REVIEW2-002` RF-4 lo excluye explícitamente (el aviso de contacto
es único) y el PRD lo deja como post-MVP.

Solo se encolaba el email del primer contacto. `MessageService.send` no notificaba y `MessagesPage`
no hacía polling ni SSE. El docente respondía y el estudiante no se enteraba hasta refrescar.

- **Pendiente.** No implementado en esta tanda.

### 1.8 Sin reportes/bloqueo ni páginas legales/parentales — ALTO

**Estado: RESUELTO en código (2026-11-11).** `REPORT-001` implementa reportes de usuarios con cola
admin (`/admin/reportes`), bloqueo bidireccional que corta contacto y mensajes, botones de reportar
y bloquear en la conversación, páginas `/terminos`/`/privacidad`/`/ayuda`, y consentimiento
adulto/tutor obligatorio en el registro. Incluye `V22`, tests unitarios de backend, tests de
integración (para CI) y tests de frontend. La suite de integración local queda pendiente porque
Docker está apagado.

La tabla `reports` y `notifications` existen sin flujo. `Footer` muestra “Términos · Privacidad ·
Ayuda” como botones que navegan al inicio; no hay rutas legales ni consentimiento parental. Para
un producto con menores es bloqueante de confianza y legal.

- **Pendiente.** No implementado en esta tanda.

### 1.9 Sin búsqueda por zona — ALTO

El PRD §3/§5 exige búsqueda por materia y zona; `TeacherSearchCriteria` no aceptaba ciudad y la UI
no tenía filtro.

- **Resuelto en código:** PR #47. `GET /api/teachers?city=` case-insensitive + trim; UI “Zona” con
  parámetro `ciudad`.

### 1.10 Fail-fast de configuración incompleto — ALTO

El backend podía arrancar “UP” con `APP_BASE_URL`/`FRONTEND_URL` de desarrollo, cookies inseguras o
sin transporte de email. El registro podía quedar roto o el outbox en `DEAD` sin alerta.

- **Resuelto en código:** PR #45. `ProductionConfigValidator` valida al arrancar URLs HTTPS
  públicas, CORS, `COOKIE_SECURE`, `COOKIE_SAME_SITE` y transporte SMTP/Brevo. Todos los problemas
  se reportan juntos.

### 1.11 Sesión de 1 hora sin refresh — MEDIO

**Estado: RESUELTO en código (2026-11-11).** `AUTH-004`: TTL por defecto 12 h (`JWT_EXPIRATION`),
claim `ver` ligado a `users.session_version`; logout y reset de contraseña incrementan la versión y
revocan en el servidor todos los JWT anteriores sin consultas extra. Tests unitarios de JWT y test
de integración de logout/reset (para CI). No se agregaron refresh tokens: quedan documentados como
futuro.

Login real devolvía `expiresIn: 3600`; logout solo borra la cookie. El usuario debe re-loguear cada
hora; no hay revocación server-side.

- **Pendiente.** Evaluar TTL mayor o refresh tokens con rotación.

### 1.12 Verificación de release — MEDIO

**Estado: VERIFICADO (2026-11-11).** Frontend `npm test` 99/99, `npm run lint`, `npm run build` +
CSP y `npm audit` (0 vulnerabilidades) PASS. Backend `mvn -B -DskipTests test-compile`,
`JwtServiceTest` 5/5 y `ArchitectureTest` 2/2 PASS. La suite completa de integración/E2E sigue en
CI porque Docker Desktop local está apagado.

`npm run build` PASS; `npm test` 94/94 PASS; `npm run lint` PASS; `npm audit` 0 vulnerabilidades;
`mvn -B clean test-compile` PASS; tests unitarios de OPS-003/OPS-004 PASS. La suite completa de
integración/E2E corre en CI; localmente Docker Desktop estaba apagado.

---

## 2. Deuda técnica y rendimiento prioritario

- **Índice de hilo de mensajes:** `findLatestThreadPage` ordena por `(created_at desc, id desc)` por
  conversación; `V11` solo crea un índice parcial para no leídos. Agregar
  `messages(conversation_id, created_at desc, id desc)`.
  **RESUELTO:** `V24__message_thread_index.sql` crea `idx_messages_conversation_created_id`;
  `SchemaIndexIntegrationTest` lo exige y la documentación de DB quedó actualizada (PERF-001).
- **Lista de conversaciones tope 50:** `MessagesPage` pide `size=50` fijo; con más conversaciones
  las viejas desaparecen. La API ya pagina.
  **RESUELTO:** la lista carga de a 50 y muestra “Cargar más conversaciones” mientras existan
  páginas; el polling refresca la primera página y conserva las anteriores sin duplicar.
- **Índices de búsqueda:** no hay índices para `rating_average/rating_count` ni `price_per_hour`;
  medir con `EXPLAIN (ANALYZE, BUFFERS)` y agregar compuestos si crece el catálogo.
  **DECISIÓN:** no se agregan índices especulativos sin un plan real de producción. Queda como
  medición operativa cuando el catálogo crezca; el índice del hilo (PERF-001) ya se resolvió.
- **Rate limiting en memoria:** bucket4j por instancia; con varias instancias el límite efectivo se
  multiplica. Migrar a Redis cuando escale. En Vercel, `RATE_LIMIT_TRUST_FORWARDED_HEADERS=true` es
  obligatorio para no compartir un único bucket de IP.
  **BLOQUEADO:** Redis está fuera de alcance hasta que exista una spec (AGENTS). La configuración
  de proxy queda documentada; no se migra en esta tanda.
- **Outbox sin alerta operativa:** el worker reintenta y marca `DEAD`, pero no hay alerta/panel. Un
  email rechazado por Brevo deja cuentas sin activar y contactos sin avisar.
  **RESUELTO:** `OutboxHealthIndicator` reporta `DEGRADED` en `/actuator/health` cuando hay filas
  `DEAD` (sin detalles públicos ni reinicio), además de las métricas existentes.
- **Evidencia en PostgreSQL por defecto:** `STORAGE_PROVIDER=postgres` guarda PDFs como `bytea`; en
  Neon Free el espacio se agota rápido. Usar S3/R2 y definir `SCAN_ENABLED`/
  `ALLOW_UNSCANNED_EVIDENCE` explícitamente.
  **ACCIÓN OPERATIVA:** el código ya soporta `s3` y el fail-fast de escaneo; falta configurar el
  bucket y las variables en el entorno real. No es un cambio de repositorio.
- **Meta description duplicada en SEO:** `injectSeo` no elimina el `<meta name="description">`
  multilínea de `index.html`; la respuesta pública tiene dos.
  **RESUELTO:** los matchers aceptan `\s+` entre `<meta`/`<link` y sus atributos; el template
  multilínea se reemplaza. Test de regresión en `handler.test.ts`.
- **`/error` público devuelve `status:999`:** no filtra datos, pero rompe el contrato de errores.
  **RESUELTO:** `ApiErrorController` reemplaza el body de Boot; `GET /error` directo responde `404`
  con `ApiError`, y un dispatch real conserva su status. Test de integración para CI.
- **JWT sin revocación real:** `jti` se genera pero no se usa; un token robado vale hasta 1 h.
  **RESUELTO:** AUTH-004 agrega `users.session_version` al JWT; logout y reset revocan todos los
  tokens anteriores en el servidor. `jti` sigue siendo trazabilidad, no hace falta una denylist.
- **Sin observabilidad de frontend:** no hay Sentry/error reporting; el `ErrorBoundary` solo muestra
  un fallback.
  **BLOQUEADO:** requiere una dependencia nueva y una decisión de proveedor (Sentry/otro) con
  aprobación explícita; no se agrega a ciegas.

No se encontró N+1 crítico en búsqueda/inbox: `TeacherSummaryAssembler` y
`ConversationService.list` usan consultas batcheadas; la búsqueda usa `EntityGraph`.

---

## 3. Propuestas para conversión y retención

### MVP 1 — Onboarding mínimo y oferta desacoplada

Implementado en #48/#49: estudiante con nombre + nivel + ciudad; profesor publica materia directa
sin carrera. Próximo paso: medir finalización de registro y primer contacto.

### MVP 2 — Mensajería “viva”

Notificación por email en cada mensaje + badge de no leídos + polling 15–30 s (o SSE). Mostrar
“última actividad” y “respondé en un clic” desde el email. Sin esto, el contacto inicial muere.

**RESUELTO PARCIAL:** badge de no leídos + polling cada 20 s (1.7). El email por cada mensaje queda
fuera de alcance por `REVIEW2-002` RF-4 (el aviso es único por contacto).

### MVP 3 — Búsqueda por zona real

Implementado el filtro por ciudad en #47. Próximo paso: normalizar variantes (“Sta. Fe”/“Santa Fe”)
y evaluar “cerca de mí” usando lat/lng ya soportados por la API. **Fuera de hallazgos:** es una
propuesta de producto; requiere spec para normalización/geolocalización.

### Quick wins

- Auto-login después de activar la cuenta. **Propuesta de producto; requiere spec (cambio de
  contrato de activación y sesión).**
- Conservar el profesor objetivo al pasar por registro/login. **Propuesta de producto; requiere
  spec de flujo de registro/activación.**
- Copia honesta de verificación (“verificación opcional”, no “todos verificados”).
  **RESUELTO:** landing y subtítulo explican que la verificación es opcional; la tarjeta mantiene
  “Verificado”/“No verificado”.
- Subir foto en vez de pegar URL. **Propuesta de producto; requiere spec de upload/almacenamiento.**
- Mensajes de error en español sin `[401] Invalid credentials`.
  **RESUELTO:** el login traduce 401/429/5xx/400 a mensajes en español, sin prefijo de status ni
  detalle interno.
- Páginas reales de términos/privacidad/ayuda y consentimiento parental.
  **RESUELTO:** rutas `/terminos`, `/privacidad`, `/ayuda` y checkbox adulto/tutor en registro.
- Reporte/bloqueo de usuarios y mensajes.
  **RESUELTO:** reportes con cola admin y bloqueo bidireccional (REPORT-001).

### Adquisición

- Onboarding manual de fundador para 10–20 profesores reales.
- Testimonios verificables y CTA “Publicá tu anuncio” en landing.
- No lanzar con ratings/fotos ficticias: la primera impresión de confianza es el producto.

---

## 4. Mapa de PRs de esta tanda

| PR | Hallazgo | Estado |
|---|---|---|
| [#44](https://github.com/AgustinMohr/clase-ya/pull/44) | Demo en producción + credenciales conocidas | CI verde; pendiente merge/deploy |
| [#45](https://github.com/AgustinMohr/clase-ya/pull/45) | Fail-fast de configuración | CI verde; pendiente merge/deploy |
| [#46](https://github.com/AgustinMohr/clase-ya/pull/46) | Nombre visible del estudiante | CI verde; pendiente merge/deploy |
| [#47](https://github.com/AgustinMohr/clase-ya/pull/47) | Filtro por ciudad/zona | CI verde; pendiente merge/deploy |
| [#48](https://github.com/AgustinMohr/clase-ya/pull/48) | Onboarding mínimo del estudiante | CI verde; pendiente merge/deploy |
| [#49](https://github.com/AgustinMohr/clase-ya/pull/49) | Materias directas del profesor | CI verde; pendiente merge/deploy |
| [#50](https://github.com/AgustinMohr/clase-ya/pull/50) | Recuperación de contraseña | Abierto; CI encolado al cierre |

Los PRs son apilados: #44 → main, #45 → #44, #46 → #45, #47 → #46, #48 → #47, #49 → #48,
#50 → #49. No se mergeó nada; el agente no mergea ni publica por su cuenta.

---

## 5. Pendientes que requieren decisión o trabajo humano

1. **Deployar #44 y limpiar producción** (bloqueante de seguridad inmediato). **Humano.**
2. **Reclutar oferta real** (10–20 profesores) y borrar definitivamente datos demo. **Humano.**
3. **Notificaciones de mensajes** (nuevo evento de outbox + badge/polling). **Resuelto in-app**
   (polling/badge); el email por mensaje queda fuera por REVIEW2-002.
4. **Reportes/bloqueo + términos/privacidad + consentimiento parental.** **Resuelto (REPORT-001).**
5. **Sesión/refresh tokens** y revocación de JWT. **Resuelto (AUTH-004): TTL 12 h + revocación por
   `session_version`.** Refresh tokens con rotación quedan como futuro.
6. **Observabilidad:** alerta de outbox `DEAD`, errores frontend y uptime. **Outbox resuelto**
   (`DEGRADED` en health); errores frontend bloqueados por dependencia; uptime es operativo.
7. **Deuda de índices** de mensajes y búsqueda cuando haya volumen. **Mensajes resuelto (PERF-001);
   búsqueda queda a la espera de EXPLAIN con volumen real.**

---

## 6. Evidencia de verificación

- Frontend: `npm run build`, `npm test` (94/94), `npm run lint`.
- Backend: `mvn -B clean test-compile`; tests unitarios de OPS-003 (4/4) y OPS-004 (22/22).
- CI por PR: Backend, Frontend, E2E, Dependency review y Vercel en #44–#49.
- #50: CI pendiente al cierre.
- Live: `/actuator/health` 200; CORS permite el origen Vercel y rechaza un origen ajeno con 403;
  HSTS/CSP presentes; HSTS del backend presente.
