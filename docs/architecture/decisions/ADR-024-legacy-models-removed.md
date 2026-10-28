# ADR-024 — Modelos legacy descartados

**Estado:** Aceptado (QUAL-03, implementado)
**Fecha:** 2026-10-28
**Relacionado:** dirección de producto (`AGENTS.md`, PRD), `ADR-009`, `ADR-014`.

## Contexto

El rumbo actual es **directorio + contacto**: el alumno encuentra al profesor y lo contacta; la
coordinación de fecha, hora y pago ocurre fuera de la plataforma. La dirección anterior (reservas,
Calendly, reviews, reportes y notificaciones) quedó descartada, pero el modelo seguía conteniendo
`Booking`, `CalendlyIntegration`, `Review`, `Report` y `Notification`, y `V1` creaba sus tablas.

Ninguno de esos tipos tiene repositorio, servicio, controller ni test: solo se referencian entre
ellos. `rg` sobre `src/main` y `src/test` no encontró usos de negocio. Mantenerlos sugiere que la
funcionalidad existe y empuja a alguien a “completarla”.

## Decisión

1. Se confirma el **descarte** de bookings, Calendly, reviews, reportes y notificaciones como rumbo
   de producto.
2. Se **eliminan las entidades y enums JPA** (`Booking`, `CalendlyIntegration`, `Review`, `Report`,
   `Notification`, `BookingMode`, `BookingStatus`, `NotificationType`, `ReportStatus`). Al no estar
   mapeadas, Hibernate deja de exponerlas y el modelo refleja el producto real.
3. Las **tablas de `V1` se conservan** como legacy. No se agrega una migración destructiva: no hay
   ganancia funcional y sí riesgo de pérdida de datos. Si en el futuro se quieren eliminar, será una
   migración dedicada con backup y ventana de mantenimiento.
4. No se construye nada nuevo sobre esas tablas ni sobre `teacher_profiles.rating_average/count`
   (agregados que hoy no tienen módulo que los mantenga).

## Consecuencias

- **Positivas:** el modelo y el esquema dejan de prometer features descartadas; menos superficie
  para que un agente “complete” algo fuera de rumbo; el `ddl-auto=validate` sigue verde porque las
  tablas extra no se validan.
- **Costos:** el schema conserva tablas y columnas sin uso (documentadas en
  `docs/architecture/database.md`); una limpieza futura requiere su propia migración.
- **Sin cambios** de API, seguridad, tests ni comportamiento.

## Alternativas consideradas

- **Eliminar las tablas en `V10`:** rechazado por ahora; es destructivo y no aporta al producto.
- **Dejar las entidades y solo documentar:** rechazado; es exactamente la confusión que el hallazgo
  señaló.
