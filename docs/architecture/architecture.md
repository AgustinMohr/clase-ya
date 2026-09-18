# ClaseYa — Arquitectura (tal como está implementada)

Backend monolítico (modular) para conectar estudiantes universitarios con profesores particulares.
Este documento describe la arquitectura **actual y real**. Todo cambio de arquitectura futuro
requiere un ADR.

## Forma

- **Java 17 / Spring Boot 3.2.6 / Maven**, un único artefacto desplegable bajo `com.claseya`.
- **Monolito modular**: el código se agrupa por paquetes de dominio; un solo contexto de Spring,
  una sola base de datos.
- Capas dentro de cada módulo: `controller` (solo HTTP) → `service` (reglas de negocio,
  `@Transactional`) → `repository` (Spring Data JPA) → PostgreSQL. `dto` contiene records.
- Los controllers nunca contienen reglas de negocio ni consultas JPA; las entidades nunca se
  devuelven directamente.

## Módulos

| Paquete | Responsabilidad | Usa |
|---------|-----------------|-----|
| `model` | Entidades JPA + `model.enums` | — (hoja) |
| `common` | `exception` (`ApiError`, `GlobalExceptionHandler`, excepciones tipadas), `util` (`SlugUtils`) | model |
| `user` | `UserRepository` | model |
| `security` | Cadena de filtros, `JwtService`, `JwtAuthenticationFilter`, `AppUserDetails(Service)`, `CurrentUser`, handlers JSON 401/403 | model, common, user |
| `auth` | Registro/login (BCrypt vía `AuthenticationManager`), DTOs de auth | user, security, common |
| `academic` | Catálogo: CRUD de University, AcademicUnit, Career, Subject, CareerSubject | model, common |
| `student` | `StudentProfile` + repositories usados en otros módulos | model, common, academic(repos), user, security |
| `teacher` | Perfil/educación/materias/modalidades del profesor, búsqueda y detalle públicos, `TeacherSummaryAssembler`, `SearchResultPage` | model, common, academic(repos), security, user |
| `favorite` | Favoritos de estudiantes | model, common, student(repo), teacher(repos/assembler/dto) |
| `messaging` | Conversaciones, participantes, mensajes, estado de lectura | model, common, user, student(repo), teacher(repo + `SearchResultPage`) |
| `web` | **Temporal** `/api/test/*` para validar seguridad | security |

Notas sobre reuso real entre módulos: `favorite` y `messaging` reutilizan
`teacher.dto.SearchResultPage` (forma genérica de página) y `favorite` reutiliza
`teacher.service.TeacherSummaryAssembler` para las tarjetas públicas de profesor. Es intencional y
se mantiene a nivel service/DTO; los repositories no se alcanzan cruzando controllers.

## Flujo de request (autenticado)

```
HTTP request
  → Cadena de filtros de Spring Security (JWT sin estado)
      JwtAuthenticationFilter: lee "Authorization: Bearer", valida firma+expiración,
      recarga el usuario por id, exige status=ACTIVE, setea el SecurityContext
  → Controller (mapea HTTP + @Valid)
  → Service (@Transactional; valida ownership/invariantes)
  → Repository (Spring Data / JPQL) → PostgreSQL
  → Respuesta: records DTO
```

Las peticiones no autenticadas/prohibidas no llegan a controllers: la cadena de filtros o el
`AuthorizationFilter` producen un `ApiError` JSON vía `RestAuthenticationEntryPoint` (401) y
`RestAccessDeniedHandler` (403).

## Identidad

El usuario que actúa se resuelve desde el `SecurityContext` con `security.CurrentUser`
(`id()`, `principal()`). `AppUserDetails` envuelve `User`; las authorities son `ROLE_<ROLE>`; solo
las cuentas `ACTIVE` están "enabled". Controllers/services nunca aceptan identidad provista por el
cliente para decidir *quién* actúa.

## Persistencia

- El schema lo define Flyway (`src/main/resources/db/migration/V1..V4`); Hibernate corre con
  `spring.jpa.hibernate.ddl-auto=validate`.
- Las entidades viven en `model`; cada asociación se mapea del lado owning (`@ManyToOne`/
  `@OneToOne` + `@JoinColumn`), las relaciones N:M son entidades join explícitas (sin
  `@ManyToMany`).
- Timestamps `timestamptz` (UTC) ↔ `Instant`; PK UUID.
- Ver `docs/architecture/database.md`.

## Manejo de errores

`common.exception.GlobalExceptionHandler` (@RestControllerAdvice) mapea excepciones tipadas a un
`ApiError {timestamp, status, error, message, path, fieldErrors?}` uniforme:
400 validación/`BadRequest`/`InvalidAssociation`/`InvalidRole`, 401 credenciales inválidas /
autenticación, 403 `AccessDenied`, 404 `ResourceNotFound`, 405 método no permitido, 409
`Conflict`/email duplicado/`DataIntegrityViolation`. `HttpMessageNotReadable` → 400. Nunca llegan
stack traces ni SQL al cliente.

## Notas del estado actual (no corregidas aquí)

- `web/SecurityTestController` (`/api/test/*`) es andamiaje temporal de validación.
- Existen algunos helpers sin uso (`TeacherSearchService.defaultSize()`,
  `FavoriteService.defaultSize()`).
- `SearchResultPage`/`TeacherSummaryResponse` viven en `teacher.dto` aunque se reutilicen entre
  módulos — aceptable hoy; moverlos sería un refactor que requiere spec/ADR.
- `application.yml` trae defaults **solo de desarrollo** para DB y `jwt.secret` (documentado;
  producción debe sobreescribirlos vía entorno).
