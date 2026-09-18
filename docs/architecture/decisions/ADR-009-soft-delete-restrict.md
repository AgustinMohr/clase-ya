# ADR-009 — Soft delete y RESTRICT en vez de borrado físico

**Estado:** Aceptado (implementado en el schema).

## Contexto
Preservar datos históricos y evitar pérdidas accidentales por borrados en cascada.

## Decisión
- Catálogo y relaciones de oferta usan desactivación lógica (`active = false`) en vez de DELETE
  físico; las cuentas usan `status`.
- FKs con `ON DELETE RESTRICT` para datos históricos/transaccionales (bookings, reviews, mensajes,
  participantes, reportes); `CASCADE` solo en datos de propiedad efímera (education,
  teacher_subjects, modalities, notifications, calendly_integration).
- La eliminación de un usuario físicamente no está soportada (RESTRICT lo impide).

## Consecuencias
- + No se pierde historia por accidente; los recursos ocultos devuelven 404 pero siguen en DB.
- - Requiere queries que filtren por `active`/`status` y reglas claras de reactivación (futuro).
- - La "purga" real de cuentas, si se necesita, debe diseñarse explícitamente (futuro).
