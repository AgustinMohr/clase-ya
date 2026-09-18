# ClaseYa — Favorites (Phase 5)

Lets a student save interesting teachers ("Mis favoritos") and list them later with their public
teacher cards, without extra per-teacher requests. Built on the Phase 1 `Favorite` entity and the
Phase 4 public teacher presentation.

## Purpose & model

`Favorite` (already modeled in Phase 1) joins `StudentProfile` and `TeacherProfile`:

```
Favorite: id, student_id -> student_profiles, teacher_id -> teacher_profiles, created_at
UNIQUE(student_id, teacher_id)   // DB is the last line of defense against duplicates
```

No migration was needed in this phase: the table, the unique constraint and its supporting index
(leading `student_id` column serves `GET /api/favorites`) already existed.

## Endpoints (all require a STUDENT bearer token)

| Method | Path | Description |
|--------|------|-------------|
| `POST`   | `/api/favorites/{teacherId}` | Save a teacher → 201 `{id, teacherId, createdAt}` |
| `DELETE` | `/api/favorites/{teacherId}` | Remove a saved teacher → 204 (404 if not saved) |
| `GET`    | `/api/favorites?page=&size=` | Student's saved teachers, newest first |
| `GET`    | `/api/favorites/{teacherId}` | Status `{teacherId, favorite, favoriteId?}` for the UI |

Auth is stateless JWT (Phase 2). Only `ROLE_STUDENT` can use favorites (`/api/favorites/**`);
teachers/admins get 403.

## Ownership

The student is always derived from the `SecurityContext` (`CurrentUser` → `User` → `StudentProfile`).
The client never sends a `studentId`. Deletes are scoped by the authenticated student + teacher —
there is no favorite-id surface to abuse, so cross-user manipulation (IDOR) is structurally
impossible and covered by tests.

## Business rules

- **Profile required**: a STUDENT without a `StudentProfile` gets **409** (`Student profile must be
  completed before adding favorites`). The profile is never auto-created. Listing/status without a
  profile simply return an empty/false answer.
- **Visible teacher only**: adding favorites is restricted to teachers that are publicly visible
  (Phase 4 rule): `TeacherProfile.verificationStatus = VERIFIED` **and** `User.status = ACTIVE`.
  PENDING/REJECTED/INACTIVE/SUSPENDED → **404** (privacy: hidden profiles are not revealed).
- **Duplicate** → **409** (pre-check + DB `UNIQUE` as final guard).
- **Delete of a non-existent favorite** → **404** (no silent idempotency).

## Pagination & order

Same limits as teacher search: `page >= 0`, `1 <= size <= 50` (else 400; defaults 0/20). Order is
`createdAt DESC, id ASC` (stable). Response reuses the generic `SearchResultPage` shape
(`content / page / size / totalElements / totalPages`).

## Response example (`GET /api/favorites`)

```json
{
  "content": [{
    "favoriteId": "uuid",
    "createdAt": "2026-09-02T15:00:00Z",
    "teacher": {
      "id": "uuid",
      "displayName": "Ana Prof",
      "bio": "Profesora de matematica",
      "ratingAverage": 4.8,
      "ratingCount": 32,
      "verificationStatus": "VERIFIED",
      "modalities": ["ONLINE", "IN_PERSON"],
      "subjects": [{
        "careerSubjectId": "uuid", "subjectId": "uuid", "subjectName": "Matematica I",
        "careerId": "uuid", "careerName": "Ingenieria en Informatica",
        "universityId": "uuid", "universityName": "Universidad Nacional del Litoral"
      }]
    }
  }],
  "page": 0, "size": 20, "totalElements": 3, "totalPages": 1
}
```

The teacher card reuses the Phase 4 `TeacherSummaryResponse` (no email, address, exact coordinates,
hashes or tokens). `distanceKm` is always null here (no geo context).

## Performance / N+1

Listing is: one page query for favorites + a **constant** number of batched queries (user names,
subject graph, modalities) via the shared `TeacherSummaryAssembler` (extracted from Phase 4 search
so both features reuse the same presentation logic). No `EAGER` relationships were introduced.

## Errors

Reuses `ApiError` + `GlobalExceptionHandler`: 401 (no auth), 403 (wrong role), 404 (teacher not
visible / favorite missing), 409 (no student profile / duplicate), 400 (page/size out of range).

## Decisions

- `GET /api/favorites/{teacherId}` **was implemented**: a cheap existence check that lets the UI
  render the heart state without scanning the whole list.
- Delete semantics: 404 when the favorite does not exist (explicit, documented).
- No profile on list/status → empty/false (no error noise); on add → 409.

## Future (documented, not implemented)

Notifications when a favorited teacher changes availability, favorites-based recommendations,
and the React UI. Phase 6+ keeps favorites untouched.
