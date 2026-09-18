# ADR-007 — Geolocalización sin PostGIS en V1

**Estado:** Aceptado (implementado en la búsqueda de profesores).

## Contexto
Filtrar/ordenar profesores por cercanía manteniendo el despliegue simple, sin agregar PostGIS.

## Decisión
Geo en PostgreSQL estándar: un **bounding box** sobre `latitude`/`longitude` (usa el índice
existente) como pre-filtro, seguido de un **círculo exacto** por distancia cuadrática planar
calculada en SQL con aritmética pura. El orden por distancia usa esa expresión en la query de
contenido. `distanceKm` real en la respuesta se calcula con Haversine solo sobre la página
devuelta. Sin funciones trig por fila.

## Consecuencias
- + Sin extensión/extensiones ni dependencias geo; desplegable igual de simple.
- - Aproximación plana: correcta para la escala Santa Fe y radios ≤ 100 km.
- - Si se necesitan geodatos avanzados (formas, proyecciones, precisión global), PostGIS es una
  opción futura (requiere spec/ADR).
