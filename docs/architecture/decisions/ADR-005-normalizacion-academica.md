# ADR-005 — Normalización del dominio académico

**Estado:** Aceptado (implementado en el módulo `academic`).

## Contexto
No guardar strings de universidad/carrera/materia directamente en perfiles; una materia puede tener
nombre distinto según la carrera.

## Decisión
Modelo académico normalizado (~3NF):

```
University 1:N AcademicUnit 1:N Career 1:N CareerSubject N:1 Subject
```

Student/Teacher referencian por FK; `CareerSubject.nameOverride` cubre el nombre propio de la
carrera. `Subject` es conceptual e independiente de universidad. No hay nombres duplicados ni
hardcodeo (los valores se cargan por ADMIN; no hay enums ni seed).

## Consecuencias
- + Coherencia y sin duplicación; búsqueda académica navegable por FK.
- + Un mismo `Subject` puede convivir con `nameOverride` distintos por carrera.
- - El catálogo debe poblarse antes de usar perfiles (gestión ADMIN).
- - Consultas más enlazadas que con strings (mitigado con Specifications/EXISTS en búsqueda).
