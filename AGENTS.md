# AGENTS.md — ClaseYa

Reglas permanentes de desarrollo de este repositorio. El repositorio (código, specs, ADRs y docs)
es la **única fuente de verdad**: nunca dependas de prompts históricos de chat para recordar
decisiones.

## Propósito

ClaseYa es un **directorio + contacto seguro**: conecta a estudiantes de todas las edades (y a sus
padres/madres/tutores) con profesores particulares de la materia y la zona que necesitan. El alumno
encuentra al profesor (búsqueda por materia, tarjetas con foto/rating/precio/modalidad/
disponibilidad) y lo **contacta**; la coordinación de fecha/hora/pago la acuerdan las partes fuera
de la plataforma. **No hay reservas, agenda online, pagos ni Calendly** (esa dirección quedó
descartada; ver nota abajo).

> **Dirección de producto:** la referencia funcional es `docs/product/product-requirements.md`.
> `AVAIL-001` y `BOOK-001` están **DEPRECATED** como rumbo (agenda/reservas/Calendly); su código y
> documentos se conservan como historia. `AvailabilityWindow` se reutiliza solo como
> **disponibilidad declarada** (mañana/tarde/noche por día), nunca como agenda de reservas.

Se construye incrementalmente con **spec-driven development**; toda nueva capacidad comienza como
**spec** en `docs/specs/active/` (ver `docs/development-workflow.md`).

## Documentos normativos del proyecto (Fuente de verdad)

Antes de implementar cualquier cambio, el agente DEBE leer y respetar estos documentos.

Orden de prioridad:

1. `docs/product/product-requirements.md` → requisitos funcionales.
2. `docs/frontend/design-system.md` → identidad visual, UX/UI y componentes.
3. `docs/architecture/guardrails.md` → reglas de arquitectura.
4. `docs/development-workflow.md` → flujo de trabajo.
5. Specs activas en `docs/specs/active/`.

Estos documentos tienen prioridad sobre cualquier sugerencia del chat.

Si una implementación contradice alguno de estos documentos, el agente debe detenerse y reportarlo.

## Stack (implementado)

- Java 17, Maven, Spring Boot 3.2.6
- Spring Web + Spring Security (JWT sin estado) + Spring Data JPA (Hibernate 6.4) + Bean Validation
- PostgreSQL, Flyway (schema propiedad de las migraciones, `ddl-auto=validate`)
- Testcontainers (PostgreSQL 16) para tests de integración
- Frontend: React 18.3 + TypeScript 5.6 + Vite 5.4 + Tailwind CSS.
- Context API para sesión/autenticación JWT.
- El frontend vive en `frontend/` y evoluciona mediante specs igual que el backend.

## Arquitectura (implementada)

Monolito modular bajo `com.claseya`. Módulos:

| Paquete | Responsabilidad |
|---------|-----------------|
| `model` | Entidades JPA + `model.enums`. No depende de nada más. |
| `common` | Transversal: `exception` (errores + `GlobalExceptionHandler`), `util`. |
| `auth` | Registro/login, DTOs y servicio de auth. |
| `user` | `UserRepository` (lookup compartido de identidad). |
| `academic` | CRUD del catálogo (University, AcademicUnit, Career, Subject, CareerSubject). |
| `student` | `StudentProfile`. |
| `teacher` | Perfil de profesor, formación, materias, modalidades + búsqueda/detalle + `TeacherSummaryAssembler`. |
| `availability` | Disponibilidad **declarada** del profesor (`AvailabilityWindow`, franjas por día mañana/tarde/noche + nota). Sin agenda de reservas. |
| `favorite` | Favoritos. |
| `messaging` | Conversaciones/mensajes/estado de lectura. |
| `security` | Configuración de Spring Security, JWT, `CurrentUser`, principal, handlers. |
| `web` | **Temporal**: endpoints de prueba de seguridad `/api/test/*` (a eliminar cuando existan pruebas reales de UI). |

Dirección de dependencias: **`model` y `common` son módulos hoja.** Los módulos pueden usar
`model`, `common`, `security`, `user`. Se permite reutilizar helpers de presentación entre módulos
(p. ej. `favorite` y `messaging` reutilizan `teacher.dto.SearchResultPage`/
`TeacherSummaryAssembler`); mantenlo a nivel de servicio, nunca dejes que un controller toque el
repository de otro módulo. Ver `docs/architecture/guardrails.md` para las reglas ArchUnit
propuestas (aún no aplicadas).

## Reglas de seguridad (implementadas — no las rompas)

- La identidad siempre sale del `SecurityContext` vía `security.CurrentUser`. Nunca aceptes
  `userId`/`studentId`/`senderId`/`participantIds` del cliente para decidir *quién* actúa.
- Contraseñas: solo BCrypt; nunca guardes ni loguees texto plano. Nunca expongas `passwordHash`.
- JWT sin estado (`jwt.secret`, `jwt.expiration`); las cuentas **PENDING/INACTIVE/SUSPENDED no
  pueden autenticarse** (solo `ACTIVE`). La visibilidad pública de un profesor exige
  `verificationStatus = VERIFIED` **y** `user.status = ACTIVE`; profesores ocultos → 404.
- Roles: `ROLE_STUDENT/ROLE_TEACHER/ROLE_ADMIN` (un único punto de mapeo en `AppUserDetails`).
  ADMIN no puede auto-registrarse. Solo STUDENT gestiona favoritos e inicia conversaciones.
- Nunca expongas emails, direcciones, coordenadas exactas, JWT/tokens ni hashes en DTOs públicos o
  personales.
- CORS restringido a `cors.allowed-origins` (`FRONTEND_URL`); sin wildcard.
- Los secretos vienen del entorno (`DB_*`, `JWT_SECRET`, `JWT_EXPIRATION`, `FRONTEND_URL`).
  `application.yml` contiene defaults **solo de desarrollo** — nunca los trates como secretos de
  producción.
- Los fallos de login devuelven un único mensaje genérico (sin enumeración de cuentas).
- Quien no participa en una conversación recibe **404** (no se revela que la conversación existe).

## Reglas de base de datos

- El schema es propiedad de Flyway. **Nunca edites una migración aplicada/histórica** (`V1..V{n}`).
  Los cambios nuevos van en `V{n+1}__descripcion_corta.sql` con motivo justificado.
- `ddl-auto=validate` — nunca dejes que Hibernate cree/actualice el schema.
- Claves primarias UUID (`gen_random_uuid()`); timestamps `timestamptz` UTC (`Instant`).
- Enums como `varchar` + CHECK, mapeados con `@Enumerated(EnumType.STRING)`.
- Prefiere desactivación lógica (`active=false`, `status` de usuario) sobre borrado físico; usa FKs
  `RESTRICT` para datos históricos.
- Las constraints de DB (UNIQUEs, CHECKs, FKs compuestas) son la última línea de defensa; la capa de
  servicios igual valida con mensajes amigables.
- **SOLO DEV**: `app.demo-seed.enabled` (`DEMO_SEED`, default `true`) siembra catálogo demo +
  profesores verificados + un estudiante al arrancar vía `DemoDataSeeder` (upserts idempotentes).
  DEBE ser `false` en entornos reales; los tests de integración lo desactivan.

## Reglas de API

- REST bajo `/api`. Las respuestas son **DTOs (records)**, nunca entidades JPA.
- Errores con la forma compartida `ApiError` vía `common.exception.GlobalExceptionHandler`
  (400/401/403/404/405/409 según corresponda). Nunca filtres stack traces/SQL.
- La paginación reutiliza la forma genérica (`teacher.dto.SearchResultPage`); las opciones de orden
  deben estar en whitelist (nunca nombres de columna arbitrarios del cliente).
- Solo los controllers mapean HTTP; las reglas de negocio viven en services; el acceso a datos vive
  en repositories. Sin lógica de negocio en controllers/entidades.

## Reglas de testing

- Los tests son obligatorios para cambios de comportamiento. Los patrones están en
  `docs/testing/testing-strategy.md`.
- Los tests de integración requieren Docker (Testcontainers postgres:16). Los unitarios
  (`JwtServiceTest`) no.
- Nunca dejes procesos Java/Maven/Docker corriendo en segundo plano desde un script o una
  verificación; no uses `spring-boot:run` oculto de larga duración para "verificar" un build.
  Prefiere `mvn test`.

## Comandos oficiales

```powershell
mvn -B clean test          # suite completa (unit + integración; requiere Docker Desktop activo)
scripts/test.ps1           # preflight de docker + mvn -B clean test
scripts/verify.ps1         # verificación reproducible (igual que test, reporta PASS/FAIL)
```

## Definition of Done

Un cambio está terminado solo si se cumplen todas:

- [ ] Impulsado por una spec aprobada (o una tarea explícita no funcional: docs/tooling).
- [ ] No se inventó ningún requisito que no esté en la spec.
- [ ] Arquitectura/seguridad/schema/API/contratos sin cambios más allá del change budget aprobado.
- [ ] El comportamiento nuevo tiene tests, y `mvn -B clean test` está en verde.
- [ ] Migraciones (si las hay) como `V{n+1}` nueva con justificación.
- [ ] Docs afectados actualizados (API, ADR cuando cambia una decisión).
- [ ] El cambio no excede el scope declarado (ver Change Budget).
- [ ] Reportado al humano (arquitecto/director). El agente nunca mergea ni publica por su cuenta.

## Change budget (declarar antes de implementar)

Toda implementación debe declarar desde el inicio:

- **Scope**: qué se hará y qué no.
- **Archivos esperados**: archivos a crear/modificar.
- **Migraciones esperadas**: nombre y motivo de `V{n+1}` (o ninguna).
- **Dependencias nuevas esperadas**: coordenada + motivo (o ninguna).

Si el trabajo empieza a exceder significativamente lo declarado, o requiere algo no declarado
(cambio de schema, de seguridad, dependencia nueva o cambio de contrato de API): **DETENTE Y
REPORTA** — no continúes en silencio.

## No silent fixes

Si durante una implementación descubres un problema que requiere: cambio de arquitectura,
modificación de seguridad, modificación del schema, dependencia nueva o cambio de contrato de API,
**no lo corrijas en silencio**. En su lugar:

1. Detecta y explica el problema con evidencia.
2. Evalúa el impacto.
3. Propón una solución (con trade-offs).
4. Espera la aprobación humana.

## Límites del agente / prohibiciones

- Nunca implementes una feature de producto sin spec aprobada.
- Nunca cambies la arquitectura sin un ADR.
- Nunca crees/modifiques una migración sin justificación, y nunca edites las históricas.
- Nunca agregues una dependencia sin justificación (motivo documentado).
- Nunca cambies el comportamiento de seguridad en silencio.
- Nunca expongas secretos; nunca commitees credenciales reales.
- Nunca rehagas una fase que ya funciona "para mejorarla" dentro de una tarea no relacionada.
- Fuera de alcance hasta que exista spec: bookings/Calendly, reviews, notificaciones, pagos,
  WebSockets, frontend, Elasticsearch, Redis, PostGIS, microservicios.
- Nunca modifiques el backend desde una tarea exclusivamente de frontend.
- Nunca cambies contratos de API para acomodar el frontend sin aprobación.
- Nunca implementes componentes visuales fuera del Design System si pueden ser reutilizables.
- Nunca agregues una librería de UI completa (Material UI, Ant Design, Chakra, etc.) sin aprobación explícita.

## Workflow

Ver `docs/development-workflow.md`: SPEC → REVIEW → PLAN → IMPLEMENT → TEST → VERIFY → REVIEW →
DONE. Las ambigüedades con impacto arquitectónico se reportan, no se adivinan.