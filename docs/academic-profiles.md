# ClaseYa — Academic Domain & Profiles (Phase 3)

CRUD for the academic catalog (university → academic unit → career → career-subject → subject)
and the student/teacher profiles built on top of the Phase 1 model and Phase 2 authentication.
A new migration `V2__add_teacher_modalities.sql` adds teaching modalities (`ONLINE`/`IN_PERSON`)
as a join table, because a teacher may offer both.

## Quick path

1. Log in as an **ADMIN** (accounts start `PENDING`; activation is still a future phase, so set
   the status to `ACTIVE` in the DB or via seed to exercise the API).
2. Create the catalog: university → academic unit → career → subject → career-subject.
3. Log in as a **STUDENT**/**TEACHER** and `POST` their profile; the teacher then adds
   education, subjects and modalities.

## Authorization rules

| Area | Rule |
|------|------|
| Academic catalog reads (`GET /api/universities/**`, `/api/academic-units/**`, `/api/careers/**`, `/api/career-subjects/**`, `/api/subjects/**`) | public |
| Academic catalog writes (`POST`/`PUT`/`DELETE` on those) | `ADMIN` |
| `/api/students/**` | `STUDENT` or `ADMIN` |
| `/api/teachers/**` | `TEACHER` or `ADMIN` |

The authenticated user is always resolved from the `SecurityContext` (`CurrentUser`); no endpoint
accepts a client-supplied `userId`. Ownership is enforced in the service layer, not only by the
URL filter.

## Endpoints

### Academic catalog (writes = ADMIN)

```
GET  /api/universities
GET  /api/universities/{id}
POST /api/universities                         {name, shortName?, city?, province?, country?}
PUT  /api/universities/{id}
DELETE /api/universities/{id}                  (soft: active=false)

GET  /api/universities/{universityId}/academic-units
GET  /api/academic-units/{id}
POST /api/universities/{universityId}/academic-units   {name, code?}
PUT  /api/academic-units/{id}
DELETE /api/academic-units/{id}

GET  /api/academic-units/{academicUnitId}/careers
GET  /api/careers/{id}
POST /api/academic-units/{academicUnitId}/careers      {name, code?}
PUT  /api/careers/{id}
DELETE /api/careers/{id}

GET  /api/subjects?query=matem                      (optional simple contains)
GET  /api/subjects/{id}
POST /api/subjects                                   {name, description?}
PUT  /api/subjects/{id}
DELETE /api/subjects/{id}

GET  /api/careers/{careerId}/subjects
GET  /api/career-subjects/{id}
POST /api/careers/{careerId}/subjects                {subjectId, nameOverride?, code?, year?, semester?, mandatory?}
PUT  /api/career-subjects/{id}
DELETE /api/career-subjects/{id}
```

`slug` (University/Career/Subject) and `normalized_name` (Subject) are generated server-side from
`name` (ASCII-folded, unique-suffixed). `POST /careers/{id}/subjects` only links an **existing**
`subjectId`; it never creates a subject.

### Student profile

```
POST /api/students/profile    {universityId, careerId, currentYear(1-12), bio?}   → 201 / 409 if exists
GET  /api/students/me
PUT  /api/students/me
```

### Teacher profile, education, subjects, modalities

```
POST /api/teachers/profile    {bio?, address?, latitude?, longitude?}   → 201 / 409 if exists
GET  /api/teachers/me
PUT  /api/teachers/me

GET/POST /api/teachers/me/education
PUT/DELETE /api/teachers/me/education/{id}

GET/POST /api/teachers/me/subjects     POST {careerSubjectId, description?, yearsExperience?}
DELETE /api/teachers/me/subjects/{careerSubjectId}

GET/POST /api/teachers/me/modalities   POST {modality: ONLINE|IN_PERSON}
DELETE /api/teachers/me/modalities/{modality}
```

## Academic integrity rules (service layer)

- A `CareerSubject` references an existing active `Subject`; a career + subject pair cannot be
  duplicated (pre-check + DB `UNIQUE (career_id, subject_id)` as the final guard → 409).
- A `TeacherSubject` references an existing active `CareerSubject` and cannot be duplicated
  (`UNIQUE (teacher_id, career_subject_id)` → 409).
- **Student profile coherence**: the chosen `careerId` must belong to an academic unit of the
  chosen `universityId` (`existsByIdAndAcademicUnit_University_Id`). A UNL career under a UTN
  university returns 400.
- Catalog children must belong to their declared parent (unit under university, career under
  unit) — enforced by path scoping + existence checks → 404 when the parent is unknown/inactive.
- Catalog deletes are **logical** (`active=false`): historical references (profiles, bookings)
  are never broken, matching the RESTRICT FKs from Phase 1. Reads only expose active rows.

## System-managed fields (never accepted from clients)

| Field | Behavior |
|-------|----------|
| `TeacherProfile.verificationStatus` | set to `PENDING` on creation; teachers cannot self-verify |
| `TeacherProfile.ratingAverage` / `ratingCount` | start at `0`; updated later by the reviews module |
| `TeacherEducation.isVerified` | always `false`; verification is an admin/future concern |
| `slug` / `normalizedName` | generated from `name` |

Client payloads that include these fields are silently ignored (they are not part of the request DTOs).

## Error handling

Consistent `ApiError` JSON (as in Phase 2), now extended: `404` (`ResourceNotFoundException`),
`409` (`ConflictException` and any `DataIntegrityViolationException`), `400`
(`InvalidAssociationException`, validation errors). 401/403 come from the security layer.

## Decisions

- **Modalities**: modeled as a join table `teacher_modalities` (V2) so a teacher can offer both
  `ONLINE` and `IN_PERSON`. Filters by modality are a future phase.
- **PUT semantics**: full replacement of the editable fields; `slug` is intentionally stable on
  update (keeps URLs valid). `CareerSubject.mandatory` keeps its value if omitted on update.
- **Transactions**: `@Transactional` on mutating service operations; DTO mapping happens inside
  the transaction to avoid `LazyInitializationException`.
- **DTO/mapper**: plain Java records with static `from(entity)` factories. No MapStruct (not in
  the project). JPA entities are never exposed.
- **No seed data**: universities/careers/subjects are entered by ADMIN through the API; nothing
  is hardcoded. Real data (UNL, UTN FR Santa Fe, UCSF, ...) is configured in the DB.

## Error example

```json
POST /api/students/profile
{ "universityId": "<unl>", "careerId": "<utn-career>", "currentYear": 2 }

400
{ "timestamp": "...", "status": 400, "error": "Bad Request",
  "message": "Career does not belong to the specified university", "path": "/api/students/profile" }
```

## Future (documented, not implemented)

- User activation flow (registration still creates `PENDING` accounts; only `ACTIVE` can log in).
- Search/filters by subject, modality and proximity; PostGIS.
- Reviews (which will maintain `ratingAverage`/`ratingCount`), bookings, messaging, favorites.
- Full admin panel (verification workflow for teachers and education).
