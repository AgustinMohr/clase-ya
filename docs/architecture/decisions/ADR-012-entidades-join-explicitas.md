# ADR-012 — N:M con entidades join explícitas (sin @ManyToMany)

**Estado:** Aceptado (implementado en `model`).

## Contexto
Relaciones muchos-a-muchos que además cargan atributos propios.

## Decisión
Todas las N:M se modelan como **entidades join explícitas** (nunca `@ManyToMany`): `CareerSubject`
(career-subject con year/semester/nameOverride), `TeacherSubject`, `Favorite`, `TeacherModality`,
`ConversationParticipant`. Cada una tiene su propia tabla, id y atributos, y se mapea del lado
owning con `@ManyToOne`.

## Consecuencias
- + Atributos de la relación, constraints y consultas por la propia relación.
- + Sin colecciones bidireccionales mágicas; la carga queda bajo control de queries batch.
- - Más entidades/tablas que con un `@ManyToMany` simple (aceptado).
