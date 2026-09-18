# ADR-006 — Búsqueda en PostgreSQL en vez de Elasticsearch

**Estado:** Aceptado (implementado en el módulo `teacher`).

## Contexto
Descubrimiento de profesores con filtros dinámicos, paginación y orden, sin infraestructura de
búsqueda dedicada.

## Decisión
Resolver la búsqueda **en PostgreSQL** con Spring Data JPA **Specifications**. Los filtros que
multiplicarían filas (materia/carrera/universidad, modalidad) se escriben como **subqueries
EXISTS** para evitar duplicados y mantener count/página exactos; el mapeo a tarjetas públicas usa
queries batch (sin N+1). No se introdujo Elasticsearch.

## Consecuencias
- + Cero infraestructura extra; filtros/orden/paginación correctos dentro del monolito.
- - El ranking es simple (rating DESC, count DESC + `id` como desempate) — suficiente para V1.
- - Si el catálogo crece o se necesita texto completo/ranking avanzado, evaluar Elasticsearch
  (documentado como futuro; requiere spec/ADR).
