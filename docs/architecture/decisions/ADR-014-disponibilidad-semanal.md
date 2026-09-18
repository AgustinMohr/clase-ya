# ADR-014 — Disponibilidad semanal del profesor (AVAIL-001)

**Estado:** Aceptado (implementado en AVAIL-001).

## Contexto
Los profesores publican su disponibilidad para que los estudiantes la consulten y, en el futuro,
soliciten clases. Se descartó Calendly como motor para esta etapa (BOOK-001 en pausa) y se eligió
un modelo recurrente semanal similar al de plataformas de clases particulares (día + franja,
mañana/tarde/noche), en vez de slots con fecha.

## Decisión
- Nueva entidad **`AvailabilityWindow`**: `(teacher, dayOfWeek 1..7, startMinutes, endMinutes,
  mode nullable, status AVAILABLE/DISABLED)`. La franja se guarda como **hora de reloj en minutos**
  de día; un patrón recurrente **no es un instante**, por lo que no aplican las reglas UTC/`Instant`
  (que sí rigen para instantes). `dayPart` (MORNING/AFTERNOON/NIGHT) se deriva, no se persiste.
- `mode` nullable: `null` = ambas modalidades (reusa `TeachingModality`).
- **Integridad por solapamiento en DB**: `CREATE EXTENSION btree_gist` + exclusion constraint
  `EXCLUDE USING gist (teacher_id =, day_of_week =, int4range(start_minutes,end_minutes,'[)') &&)`.
  La DB es la última línea de defensa ante concurrencia (el service da un 409 amigable con
  pre-check JPA). Los bloques contiguos quedan permitidos por el rango medio-abierto.
- Nota general descriptiva y pública: `teacher_profiles.availability_note` (text); no se parsea.
- Duración mínima 60 minutos (`CHECK end_minutes - start_minutes >= 60`).
- Elegibilidad real: `TeacherProfile.verificationStatus = VERIFIED` y `user.status = ACTIVE`
  (el modelo no tiene `TeacherProfile.active`).

## Consecuencias
- + Publicación sencilla y recurrente; el solapamiento/concurrencia queda garantizado en la DB.
- + Preparado para que BOOK/SEARCH materialicen fechas concretas desde la ventana sin remodelar.
- - Requiere la extensión `btree_gist` en el entorno (privilegio para `CREATE EXTENSION`).
- - Un patrón de hora de reloj no es un instante: la zona del usuario queda como responsabilidad
  del frontend; sin modelo de timezone de profesor (no existe) en AVAIL-001.
- - Los FKs de reserva futura sobre `availability_windows` exigirán no borrar físicamente
  (status `DISABLED` conserva historial).
