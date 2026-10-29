# ADR-027 — Romper los ciclos de paquetes con puertos

**Estado:** Propuesto (requiere aprobación del arquitecto).
**Origen:** `review2.md` ARCH-01. **Relacionado:** `docs/architecture/guardrails.md`, `ADR-001`.

## Contexto

El grafo real de dependencias entre módulos tiene dos ciclos:

```
teacher -> availability   y   availability -> teacher
teacher -> verification   y   verification -> teacher
```

Concretamente:

- `AnnouncementCompletenessService` (teacher) consulta `AvailabilityWindowRepository`.
- `AvailabilityService` (availability) usa `TeacherVisibilityService`.
- `TeacherEducationService` (teacher) consulta `VerificationDecisionRepository` y
  `VerificationDocumentRepository`.
- `VerificationDocumentService` y `VerificationQueryService` (verification) usan
  `TeacherProfileService` y repositorios de teacher.

Consecuencias: un cambio de visibilidad o de completitud obliga a tocar dos módulos; ArchUnit no se
puede aplicar (`guardrails.md` ya registra la deuda); y la regla D1 llegó a estar duplicada, que es
cómo aparecieron PRIV-01 y BUG-03.

## Decisión propuesta

Invertir las dependencias con **puertos pequeños en el módulo que posee el concepto**:

1. `availability` expone `AvailabilityPresencePort` (`boolean existsByTeacher(UUID)`).
   `AnnouncementCompletenessService` depende del puerto, no del repositorio. Alternativa más simple:
   mover la completitud a `availability` (es una vista del anuncio cuyas piezas ya conoce).
2. `teacher` expone `EducationEvidencePort` (`boolean hasEvidence(UUID)`,
   `boolean hasDecisions(UUID)`). `TeacherEducationService` lo usa para RF-20/RF-21;
   `verification` lo implementa.
3. `verification` deja de depender de `teacher.service`: recibe `TeacherProfile` por id desde su
   propio repositorio de lectura o desde un `TeacherLookupPort` definido en `verification` e
   implementado en `teacher`.

Regla permanente: **un módulo no importa el paquete `repository` de otro**; solo `dto`/puertos.

## Consecuencias

- + `teacher` y `availability` dejan de depender mutuamente; `verification` depende de `teacher`
  en un solo sentido.
- + Habilita ArchUnit con reglas de "sin ciclos" y "sin repositorios cruzados".
- + La regla D1 queda con una única definición (`TeacherSpecifications`), ya usada por favoritos.
- - Más interfaces (puertos) y un adaptador por cada una.
- - Refactor de varios archivos sin cambio de comportamiento: riesgo de regresión bajo pero real.

## Plan de implementación

1. Extraer los puertos y sus adaptadores con tests existentes en verde (sin cambios de contrato).
2. Verificar el grafo con un test ArchUnit (`noClasses().should().dependOnClassesThat()...` y
   `slices().matching("com.claseya.(*)..").should().beFreeOfCycles()`).
3. Actualizar `guardrails.md` con las excepciones aprobadas (`favorite → teacher.dto`,
   `messaging → teacher.dto/student/teacher`).

## Out of scope

- Partir el monolito en servicios.
- Cambiar el esquema o los contratos HTTP.
- Mover `TeacherEducation` de módulo (se evalúa en REVIEW si el puerto no alcanza).
