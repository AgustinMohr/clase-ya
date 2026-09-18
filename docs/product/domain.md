# ClaseYa — Dominio (tal como está implementado)

Modelo conceptual real del producto. Las entidades viven en `com.claseya.model`; para el detalle
físico ver `docs/architecture/database.md`.

## Actores e identidad

- **User**: identidad única (email único normalizado en minúsculas, `passwordHash` BCrypt, `role`,
  `status`, timestamps, `lastLoginAt`, `name` público opcional). `role` es de un solo valor:
  STUDENT, TEACHER o ADMIN. `status`: PENDING / ACTIVE / INACTIVE / SUSPENDED.
- **StudentProfile** (1:1 con User): `university`, `career`, `currentYear` (1-12), `bio`. Un
  usuario STUDENT crea su perfil con `POST /api/students/profile`.
- **TeacherProfile** (1:1 con User): `bio`, `verificationStatus` (PENDING/VERIFIED/REJECTED),
  ubicación (`address`, `latitude`, `longitude`), `ratingAverage`/`ratingCount` (agregados),
  timestamps.

## Catálogo académico (normalizado)

```
University 1:N AcademicUnit 1:N Career 1:N CareerSubject N:1 Subject
```

- **University / AcademicUnit / Career**: catálogo con `active`, `slug`/`code`, localización de la
  universidad. Se cargan por ADMIN (no hay enums hardcodeados ni seed).
- **Subject**: materia conceptual (no depende de una universidad), con `name`, `normalizedName`
  (para dedupe/búsqueda) y `slug`.
- **CareerSubject**: qué materia se dicta en qué carrera y con qué particularidades
  (`nameOverride`, `code`, `year`, `semester`, `mandatory`, `active`). Permite que la misma materia
  tenga nombre distinto según la carrera. Regla: no se crea un Subject nuevo al asociar; el
  `(career, subject)` es único.

## Oferta del profesor

- **TeacherEducation** (1:N con TeacherProfile): formación declarada (`institution`, `degree`,
  `startYear`/`endYear`, `isVerified`). El profesor no puede marcarla como verified; eso es del
  sistema/admin (futuro).
- **TeacherSubject** (N:N TeacherProfile ↔ CareerSubject): qué materias enseña
  (`description`, `yearsExperience`, `active`); el par `(teacher, careerSubject)` es único.
- **TeacherModality** (V2): modalidades de clase, N:N con TeacherProfile via join table, valores
  `ONLINE`/`IN_PERSON` (`(teacher, modality)` único). Un profesor puede ofrecer ambas.

## Interacción

- **Favorite** (N:N StudentProfile ↔ TeacherProfile vía join): un estudiante guarda un profesor
  `(student, teacher)` único, `createdAt`. Solo se puede favoritear un profesor VERIFIED+ACTIVE.
- **Conversation / ConversationParticipant / Message**: mensajería entre User (genérica). Toda
  conversación se crea con exactamente 2 participantes (estudiante + profesor); V1 mantiene una
  única conversación por par. `Message`: sender debe ser participante (FK compuesta en SQL),
  contenido trim no vacío (1..5000), `readAt` por mensaje.
- **Booking / Review** (schema listo, sin lógica todavía): reserva futura + review 1:0..1 por
  booking, que alimentará `ratingAverage/ratingCount`.
- **Report / Notification / CalendlyIntegration** (schema listo, sin lógica todavía).

## Reglas de dominio vigentes

- Visibilidad pública de profesor = `VERIFIED` + `user.status = ACTIVE`; todo lo demás es 404 en
  búsqueda/detalle/favoritos/contacto.
- Las cuentas deben estar `ACTIVE` para autenticarse.
- Ownership: los recursos pertenecen al usuario autenticado (perfil `/me`, favoritos, conversaciones).
- `currentYear` 1-12; la carrera del perfil del estudiante debe pertenecer a la universidad elegida.

## Disponibilidad (AVAIL-001)

- **AvailabilityWindow** (recurrente semanal): `teacher`, `dayOfWeek` (1=Lun..7=Dom),
  `startMinutes`/`endMinutes` (hora de reloj), `mode` opcional (`TeachingModality`; null = ambas),
  `status` AVAILABLE/DISABLED. La disponibilidad pertenece al profesor, no a una materia.
- `dayPart` (MORNING/AFTERNOON/NIGHT) derivado del horario de inicio (no se persiste).
- La duración mínima es 1 hora; no hay ventanas solapadas por `(teacher, día)` (exclusión en DB);
  las contiguas son válidas. Un patrón semanal no es un instante (no aplica UTC/`Instant`); las
  fechas concretas se materializarán en el futuro booking.
- Nota general descriptiva y pública: `TeacherProfile.availabilityNote` (texto libre, no se parsea).

