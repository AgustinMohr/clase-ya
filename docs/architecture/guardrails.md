# ClaseYa — Guardrails de arquitectura (aplicados parcialmente)

Estado: **APLICADO PARCIALMENTE (2026-11-02).** `ArchitectureTest` usa ArchUnit (scope `test`) para
verificar que los módulos no tengan ciclos y que `teacher` no dependa de `availability` ni
`verification`. El resto de las reglas sigue siendo una propuesta.

## Evaluación (PASO 13)

ArchUnit quedó incorporado con dos reglas; el resto se revisa por convención:

- La separación es **por convención** (controller/service/repository/dto dentro de cada módulo) y
  se revisa manualmente en reviews.
- Los ciclos `teacher ↔ availability`, `teacher ↔ verification` y `academic ↔ teacher` se
  eliminaron con puertos y moviendo `SearchResultPage` a `common.dto`.
- Quedan dependencias cruzadas **unidireccionales** legítimas: favoritos/mensajería reutilizan
  `common.dto.SearchResultPage` y `teacher.service.TeacherSummaryAssembler`; availability,
  verification, student y teacher consultan repositories de otros módulos. Una regla estricta de
  “sin repositorios cruzados” requeriría más refactor y una decisión explícita.

## Reglas que controlamos / queremos controlar

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

## Dependencias cruzadas actuales (unidireccionales)

- `favorite` → `teacher.dto` y `teacher.service` (assembler) — reuso intencional.
- `messaging` → `common.dto.SearchResultPage`, `student.repository`, `teacher.repository`,
  `safety.service` (bloqueo bidireccional de contacto/mensajes).
- `safety` → `user` y `common`; no depende de `messaging` (relación unidireccional).
- `availability` → `teacher.service`/`teacher.repository` (visibilidad y perfil).
- `verification` → `teacher.service`/`teacher.repository` (perfil y educación).
- `review` → `teacher`, `student` y `messaging` (perfil, elegibilidad y respuesta), todo unidireccional.
- `security` → `user` (`UserRepository`); `auth` → `user`/`security`.
- `student`/`teacher` → repositories de `academic`.
- `model`/`common` son hojas (sin dependencias de dominio).

## Qué queda por agregar

- Regla ArchUnit de “sin repositorios cruzados” (hoy availability/verification/student/teacher
  consultan repos de otros módulos); requiere decidir excepciones o refactorizar.
- Reglas 3-5 (controllers sin repositorios/`EntityManager`, services que no devuelven entidades).
- Formalizar la excepción `security → user` o invertirla.
- Documentar como ADR cualquier cambio de dirección de dependencias.

`ArchitectureTest` ya se ejecuta en la suite (`mvn test`).
