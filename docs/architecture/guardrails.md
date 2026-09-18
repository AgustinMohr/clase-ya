# ClaseYa — Guardrails de arquitectura (propuesta, no aplicada)

Estado: **PROPUESTA documentada.** No se agregó ArchUnit al proyecto ni se escribieron tests de
arquitectura. Esta página describe qué reglas controlaríamos a futuro y por qué hoy no se aplican
automáticamente.

## Evaluación (PASO 13)

ArchUnit sería la herramienta adecuada para fijar límites entre paquetes una vez que queramos
blindar la estructura. Hoy:

- La separación es **por convención** (controller/service/repository/dto dentro de cada módulo) y
  se revisa manualmente en reviews.
- Existen dependencias cruzadas legítimas entre módulos (favoritos/mensajería reutilizan
  `teacher.dto.SearchResultPage` y `TeacherSummaryAssembler`; los services de perfiles consultan
  repositories de otros módulos como `academic`). Un test ArchUnit estricto requeriría definir con
  precisión esas excepciones o refactorizar — fuera de alcance de este harness.
- Agregar la dependencia implicaría un cambio de `pom.xml` (justificación formal) y tiempo de
  ajuste; se difiere hasta que el conjunto de reglas esté aprobado.

## Reglas que controlaríamos (propuesta para el futuro)

Paquete base: `com.claseya`.

1. `model` no depende de ningún otro paquete de `com.claseya`.
2. `common` no depende de módulos de dominio (solo de `model`, si acaso).
3. Ninguna clase fuera de `*.model` accede a atributos de entidades para lógica de negocio en
   controllers.
4. Los controllers solo declaran `@RestController`/`@Controller` y no contienen repositorios ni
   `EntityManager`.
5. Los services no devuelven entidades JPA como respuesta HTTP (los records DTO viven en `*.dto`).
6. Los repositories de un módulo no se inyectan en los controllers de otro módulo (reuso permitido
   solo a nivel service).
7. `security` es usada por los módulos de dominio, pero `security` no depende de módulos de
   dominio concretos (hoy depende de `user` vía `UserRepository`; hay que formalizar la excepción o
   invertir).
8. Nada importa clases de `web` (endpoints temporales `/api/test/*`).

## Violaciones actuales relevantes (para decidir)

- `favorite` → `teacher.dto` y `teacher.service` (assembler) — reuso intencional.
- `messaging` → `teacher.dto.SearchResultPage`, `student.repository`, `teacher.repository`.
- `security` → `user` (`UserRepository`); `auth` → `user`/`security`.
- `student`/`teacher` → repositories de `academic`.
- `model`/`common` son hojas (sin violaciones).

## Qué agregaríamos si se aprueba

- Dependencia `com.tngtech.archunit:archunit-junit5` (versión a fijar) solo en scope `test`.
- Un `ArchitectureTest` que ejecute las reglas 1-8 con las excepciones aprobadas de forma
  explícita.
- Documentar las reglas como ADR si cambia la dirección de dependencias.

Nada de esto se aplica en este harness; es solo la propuesta.
