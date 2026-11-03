# ADR-033 — Reviews: contacto aceptado, una por par, respuesta y moderación

**Estado:** Aceptado (2026-11-02).
**Origen:** `OPINION-001`. **Relacionado:** `ADR-024`, `ADR-032`, `CONTACT-001`, `V18`.

## Contexto

`reviews` quedó en el schema desde `V1` y se conservó en `ADR-032` para implementarla en el futuro.
El producto define opiniones/rating, pero descartó reservas y pagos; por lo tanto no hay
`bookings` que sirvan como vínculo. El contacto real ocurre en la conversación interna.

## Decisión

1. **Relación válida:** un estudiante puede opinar si existe una conversación con el profesor **y
   el profesor envió al menos un mensaje**. Es la evidencia disponible de “contacto aceptado” sin
   agregar un estado nuevo a `conversations`.
2. **Una opinión por par** `student_profile` ↔ `teacher_profile`, garantizada por UNIQUE. El autor
   puede editarla; no puede eliminarla.
3. **Una respuesta del profesor** por opinión, editable, solo por el dueño del perfil reseñado.
4. **Moderación posterior:** la opinión se publica (`PUBLISHED`); ADMIN puede ocultarla
   (`HIDDEN`) con motivo y restaurarla. Toda acción crea una fila append-only en
   `review_moderation_events`.
5. **Agregados:** `teacher_profiles.rating_average/count` se recalculan solo con reviews
   `PUBLISHED`, en la misma transacción y bajo un lock pesimista del perfil docente.
6. **Privacidad:** el DTO público muestra `users.name` o “Estudiante”, rating, comentario, fecha y
   respuesta; nunca email, IDs internos ni datos de moderación.
7. **Fuera de alcance:** reportes de reviews y notificaciones de nuevas opiniones; quedan para
   specs separadas.

## Consecuencias

- **Positivas:** no se reintroduce la agenda; la opinión se apoya en una conversación real; la
  unicidad y los agregados quedan garantizados por DB + transacción; la moderación es auditable.
- **Costos:** un profesor que nunca responde no puede ser calificado por ese estudiante; el dueño
  del perfil debe responder para habilitar la opinión. El seed demo ya no inventa reviews.
- **Riesgo aceptado:** la definición “profesor respondió” es una aproximación de “relación
  aceptada”; si el producto necesita un estado explícito, será otra spec/ADR.

## Alternativas descartadas

- Mantener `booking_id`: bookings se eliminó como rumbo.
- Permitir opinar sin contacto: facilita reseñas falsas.
- Pre-moderación: agrega fricción y cola de revisión para una v1.
- Múltiples reviews por par: complica el promedio y la UI sin un requisito de producto.
