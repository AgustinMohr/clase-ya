# ADR-018 — Actualización de Spring Boot 3.2.6 a 3.5.16

**Estado:** Aceptado (implementado; verificación de integración pendiente de Docker)  
**Fecha:** 2026-10-27  
**Origen:** hallazgo `DEP-01` de `review.md`

## Contexto

El backend estaba fijado a **Spring Boot 3.2.6**, una línea que ya no recibe parches OSS. El
repositorio no tenía actualizaciones automáticas para Maven: Dependabot solo cubría `npm` en
`/frontend`. La suite de tests es la red de seguridad del proyecto, pero requiere Docker
(Testcontainers con PostgreSQL 16).

Al momento de la decisión, Maven Central publica:

- **3.5.16** como última 3.5.x (octubre 2026).
- **4.1.2** como última 4.x estable (4.2.0 aún en RC).

Spring Boot 4.x implica Spring Framework 7, Spring Security 7, Hibernate 7 y Jakarta EE 11: es una
migración mayor que merece su propio ADR, su propio presupuesto y una ventana de pruebas dedicada.

## Decisión

- Subir el parent a **Spring Boot 3.5.16**, manteniendo **Java 17**.
- Agregar `org.flywaydb:flyway-database-postgresql` (versión gestionada por el parent), porque
  Flyway 10+ separó el soporte de PostgreSQL de `flyway-core`. Sin ese módulo, el arranque falla al
  detectar PostgreSQL.
- Mantener el pin de **Testcontainers 1.20.4** y el workaround de `DOCKER_API_VERSION=1.43` que ya
  usa el proyecto; actualizar Testcontainers queda fuera de este change budget.
- Agregar el ecosistema **`maven`** a Dependabot (directorio `/`), agrupando Spring y Testcontainers,
  para que las dependencias del backend dejen de quedar congeladas.
- **No** saltar a 4.x en este cambio. La migración a 4.x se decide en un ADR posterior, con su
  propia verificación y, idealmente, después de estabilizar 3.5.16 en producción.
- No se agregan endpoints, campos de contrato, migraciones ni cambios de comportamiento de producto.

## Consecuencias

- **+** El backend vuelve a una línea con parches (Spring Framework 6.2.19, Spring Security 6.5.11,
  Hibernate 6.6.53, Tomcat 10.1.55, Jackson 2.21.x, Flyway 11.7.2).
- **+** Dependabot cubre Maven y podrá proponer actualizaciones de Spring, Testcontainers y plugins.
- **+** El esquema sigue siendo propiedad de Flyway; las migraciones `V1..V8` no se tocan.
- **−** Flyway 11 exige `flyway-database-postgresql`; ya se agregó.
- **−** Hibernate 6.6 y Spring Security 6.5 pueden cambiar validaciones o defaults; la suite de
  integración completa debe correr antes de mergear.
- **−** Testcontainers queda en 1.20.4 por el workaround de Docker; una actualización futura debe
  revisar si el pin de `api.version` sigue siendo necesario.
- **−** La verificación de integración no se pudo ejecutar en el entorno del agente porque Docker
  Desktop estaba apagado; `compile`, `test-compile`, los 75 tests unitarios y `package` sí pasaron.

## Verificación requerida antes de merge

1. `mvn -B clean test` con Docker Desktop activo (Testcontainers PostgreSQL 16).
2. Confirmar que Flyway 11 aplica/valida `V1..V8` sobre una base limpia y sobre una base existente.
3. Confirmar que Hibernate 6.6 pasa `ddl-auto=validate` con todas las entidades.
4. Repetir la suite E2E con el backend en 3.5.16.
5. Revisar el PR de Dependabot y no mezclar la migración a 4.x con este cambio.

## Futuro

- ADR específico para Spring Boot 4.x (Spring Framework 7 / Security 7 / Hibernate 7).
- Evaluar actualización de Testcontainers y retiro del workaround `api.version` cuando el entorno
  de Docker lo permita.
- Incorporar escaneo de dependencias (`dependency-check`/SBOM) al pipeline cuando exista CI
  (`CI-01`).
