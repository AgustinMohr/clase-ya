# ADR-032 — Limpieza de tablas legacy: bookings y Calendly fuera; reviews/reports/notifications se conservan

**Estado:** Aceptado (2026-11-02).
**Origen:** decisión del director sobre `LEGACY-01`. **Relacionado:** `ADR-024`, `V17`.

## Contexto

`V1` creó tablas para funcionalidades que después quedaron fuera del rumbo: `bookings`,
`reviews`, `reports`, `notifications` y `calendly_integrations`. `ADR-024` eliminó sus entidades JPA
y decidió conservar todas las tablas como historia, sin construir nada nuevo sobre ellas.

El director definió una dirección distinta para el cleanup:

- **Conservar** `reviews`, `reports`, `notifications` y los agregados
  `teacher_profiles.rating_average`/`rating_count` para implementarlos en el futuro.
- **Eliminar** `bookings` y `calendly_integrations`, que ya no tienen lugar en el producto.

## Decisión

1. Nueva migración `V17__drop_bookings_and_calendly.sql`:
   - elimina `calendly_integrations`;
   - elimina `bookings`;
   - elimina `reviews.booking_id`, su FK y su UNIQUE, porque `bookings` deja de existir.
2. Se conservan:
   - `reviews` con `student_id`, `teacher_id`, `rating`, `comment`, `created_at`, `updated_at`;
   - `reports`;
   - `notifications`;
   - `teacher_profiles.rating_average`/`rating_count`.
3. No se crean entidades JPA ni endpoints para las tablas conservadas. Su implementación futura
   requiere spec propia.
4. El seed demo deja de insertar bookings/reviews: no hay flujo de reviews todavía y no se inventa
   un vínculo nuevo. Los ratings demo quedan en 0.
5. Una futura spec de reviews definirá el vínculo (por ejemplo conversación/contacto) y la regla de
   “una opinión por relación”, en lugar de reutilizar `booking_id`.

## Consecuencias

- **Positivas:** el schema deja de contener las dos tablas que contradecían el rumbo; las tablas que
  el director quiere conservar siguen disponibles; no se agregan entidades ni código muerto.
- **Negativas:** la migración es destructiva para `bookings` y para el vínculo booking→review. En un
  entorno con datos reales hay que respaldar esas tablas antes de aplicarla.
- **Neutras:** el seed demo ya no muestra ratings; la UI de opiniones sigue en “próxima fase”.

## Alternativas descartadas

- **Conservar bookings/Calendly “por las dudas”:** contradice la decisión del director y mantiene
  tablas que no son rumbo.
- **Conservar `reviews.booking_id` con una FK a una tabla eliminada:** imposible.
- **Crear ya las entidades de reviews/reports/notifications:** sería una feature sin spec; queda
  fuera de este cleanup.

## Actualización (2026-11-02)

`V18__reviews_enabled.sql` implementó la parte de reviews/rating de esta decisión: la tabla
`reviews` está activa con entidad JPA, API, respuesta del profesor, moderación y agregados
(`OPINION-001`). `reports` y `notifications` siguen conservadas para futuras specs.
