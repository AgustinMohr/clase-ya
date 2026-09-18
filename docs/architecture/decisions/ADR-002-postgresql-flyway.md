# ADR-002 — PostgreSQL con schema propiedad de Flyway

**Estado:** Aceptado (implementado).

## Contexto
Se necesita una base relacional transaccional con migraciones controladas y evolución predecible
del schema.

## Decisión
Usar **PostgreSQL** como única base. El schema es propiedad de **Flyway**
(`src/main/resources/db/migration/V1..V4`); Hibernate corre con `spring.jpa.hibernate.ddl-auto=
validate` y nunca crea/actualiza el schema.

## Consecuencias
- + Migraciones versionadas y reproducibles; el modelo JPA queda validado contra el schema real.
- - Toda modificación de schema exige una migración `V{n+1}` justificada; nunca se editan las
  aplicadas.
- - PostgreSQL es un prerequisito local/CI (se usa postgres:16 en Docker/Testcontainers).
