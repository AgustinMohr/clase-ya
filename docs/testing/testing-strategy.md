# ClaseYa — Estrategia de testing (tal como se ejecuta)

Documenta cómo se ejecutan realmente los tests del proyecto.

## Tipos de tests

- **Unitarios** (sin Spring ni DB): hoy `JwtServiceTest` (generación/validación de tokens:
  claims, expirado, manipulado, clave incorrecta).
- **Integración** (`*IntegrationTest`, con `@SpringBootTest` + MockMvc + Testcontainers):
  prueban el stack completo sobre PostgreSQL 16 real (auth, dominio académico, perfiles, búsqueda,
  favoritos, mensajería). Se requieren ~104 tests verdes (ver "Verificación").

## Infraestructura compartida

- `AbstractPostgresTest`: arranca **un contenedor** `postgres:16` por JVM (singleton + shutdown
  hook; se evita el ciclo por-clase de Testcontainers que colgaba en Windows) y registra
  datasource + `jwt.secret`/`jwt.expiration` de test vía `@DynamicPropertySource`.
- `AbstractWebIntegrationTest`: `@SpringBootTest` + `@AutoConfigureMockMvc`, helpers HTTP
  (`postJson`, `putJson`, `getJson`, `deleteJson` con charset UTF-8 explícito), creación de
  usuarios/tokens (`createUser`, `bearer`, `adminBearer`) y parseo JSON.

## Convenciones

- Clases de integración anotadas `@Transactional` → cada test hace rollback y no necesita limpieza
  manual; los datos se crean vía repositorios en el propio test (semillas locales por clase).
- Naming: `verbo_caso_resultado` (p. ej. `student_canCreateOwnProfile_thenGetMe`,
  `duplicateFavorite_returns409`).
- Verificación de respuestas con AssertJ + `jsonPath` sobre el body DTO (no se devuelven entidades).
- `application.yml` de la app usa `spring.jackson.default-property-inclusion: non_null`: los
  asserts sobre campos nulos deben usar `.has("campo")`, no `.get(...).isNull()`.
- El charset y la zona horaria están forzados en `pom.xml` para el JVM de tests
  (`-Duser.timezone=UTC`, `-Dfile.encoding=UTF-8`) y Docker API (`-Dapi.version=1.43`).

## Comandos

```powershell
mvn -B clean test          # suite completa (requiere Docker Desktop activo)
mvn -B test -Dtest=XxxIntegrationTest    # una clase
mvn -B test "-Dtest=XxxIntegrationTest#metodo"  # un método
scripts/test.ps1           # preflight de docker + mvn -B clean test
scripts/verify.ps1         # verificación reproducible, reporta PASS/FAIL
```

Docker Desktop debe estar corriendo para los tests de integración (Testcontainers). No se dejan
procesos en segundo plano ni se usa `spring-boot:run` para verificar.

## Reglas

- Tests obligatorios para todo cambio de comportamiento.
- El patrón de verificación de una feature incluye: casos felices, errores esperados, ownership/
  IDOR, roles, visibilidad y "sin datos privados en la respuesta".
- Los asserts de seguridad (401/403/404 de no-revelación) van en la suite de integración.
