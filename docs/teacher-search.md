# ClaseYa — Teacher Discovery & Search (Phase 4)

Public, backend-enforced discovery of verified teachers with academic, modality, rating and
proximity filters, stable ordering and pagination. No auth required to read; visibility rules
(VERIFIED + ACTIVE) are applied server-side. Migration `V3__add_display_name_and_search_indexes.sql`
adds `users.name` (public display name) and search-support indexes.

## Endpoints

```
GET /api/teachers          public teacher search (paginated)
GET /api/teachers/{id}     public teacher detail (404 unless verified + active)
```

`POST/PUT /api/teachers/...` (profile management) remain `TEACHER`/`ADMIN`; `GET /api/teachers/me`
is still protected. Security matchers are ordered so the literal `/me` route is protected before
the public `GET /api/teachers/{id}` wildcard applies.

## Query parameters (`GET /api/teachers`)

| Param | Type | Notes |
|-------|------|-------|
| `subjectId` | UUID | teachers teaching this Subject (via any CareerSubject) |
| `careerId` | UUID | teachers teaching a CareerSubject of this Career |
| `universityId` | UUID | teachers teaching subjects of careers under this University |
| `modality` | `ONLINE`/`IN_PERSON` | teachers offering this modality |
| `minRating` | 0..5 | `ratingAverage >= minRating` |
| `latitude` / `longitude` | double | geo search; must come together |
| `radius` | km 0<r<=100 | required when geo |
| `page` | int >= 0 | default 0 |
| `size` | 1..50 | default 20; >50 or <1 → 400 |
| `sort` | see below | default `rating` |

Academic filters combine with **AND/intersection semantics**: a teacher must teach at least one
CareerSubject matching all provided academic filters at once (verified by tests).

### Sort whitelist (never arbitrary columns)

`rating` (default: ratingAverage DESC, ratingCount DESC, id ASC) · `ratingDesc` · `ratingAsc`
(ratingAverage ASC, ratingCount ASC, id ASC) · `name` (user.name ASC, id ASC) · `distance`
(requires latitude+longitude; nearest first). Invalid values → 400. `id` is the final tie-breaker
so pagination is stable.

## Response shape

```json
{
  "content": [{
    "id": "uuid",
    "displayName": "Ana Prof",
    "bio": "...",
    "ratingAverage": 4.8,
    "ratingCount": 32,
    "verificationStatus": "VERIFIED",
    "modalities": ["ONLINE", "IN_PERSON"],
    "distanceKm": 2.4,
    "subjects": [{
      "careerSubjectId": "uuid",
      "subjectId": "uuid",
      "subjectName": "Matematica I",
      "careerId": "uuid",
      "careerName": "Ingenieria en Informatica",
      "universityId": "uuid",
      "universityName": "Universidad Nacional del Litoral"
    }]
  }],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

`displayName` comes from `users.name` (optional). `distanceKm` is only present on geo queries.

## Visibility & privacy

- Only `TeacherProfile.verificationStatus = VERIFIED` **and** `User.status = ACTIVE` are returned
  (PENDING/REJECTED teachers and INACTIVE/SUSPENDED users are invisible, detail included → 404).
- Public responses never include email, password hash, address or exact coordinates. Coordinates
  are used only for the internal distance computation; a coarse location could be added later
  (e.g., from the University) when the product needs a "city" display.
- Display-name decision: Phase 1/3 had no name field on profiles, so `V3` adds an optional
  `users.name`; teachers set it through `POST/PUT /api/teachers/profile` (`name`). Nothing in the
  model is denormalized.

## Query strategy

- **Dynamic filters**: Spring Data JPA **Specifications**. Filters that would otherwise multiply
  teacher rows (subject/career/university, modality) are `EXISTS` subqueries, so there is **no
  DISTINCT** and count/page stay exact even with combined filters.
- **Geo**: a coarse **bounding box** (`latitude/longitude BETWEEN`, uses the coordinates index)
  pre-filters, then the exact radius is enforced in SQL with a **squared planar approximation**
  (`kmLat² + kmLon² <= radius²`) using only arithmetic (no per-row trig). Distance ordering is a
  Criteria `ORDER BY` on that expression applied only to the content query (never the count).
  `distanceKm` in the response is the accurate Haversine distance computed for the returned page.
  Documented approximation for Santa Fe scale; PostGIS is a future option if it becomes a real need.
- **No N+1**: the page is loaded once; then a constant number of batched queries fetch names
  (`JOIN FETCH user`), the whole subject graph (teacher_subjects → career_subjects → subject →
  career → academic unit → university) and modalities `WHERE teacher_id IN (page ids)`.
- Default ranking is deliberately simple (rating DESC, count DESC) and documented as V1.

## Indexes (V3)

- `users.name` column.
- `teacher_profiles(verification_status, rating_average DESC, rating_count DESC)` — default
  verified listing sorted by rating.
- `teacher_modalities(modality)` — modality filter.
- Reused existing indexes: teacher_subjects(teacher_id / career_subject_id),
  career_subjects(career_id / subject_id), careers/academic_units parents, teacher coordinates.

## Error handling

Reuses `GlobalExceptionHandler` + `ApiError`. 400 for: size/page out of range, invalid sort,
`minRating` outside 0..5, incomplete/invalid coordinates, invalid radius, `sort=distance` without
location. 404 for a non-public teacher detail. No stack traces or SQL leak.

## Future (documented, not implemented)

- Favorites, bookings, Calendly, reviews (will feed rating fields), notifications, messaging.
- Elasticsearch / full-text when the catalog grows or ranking gets complex; Redis cache;
  rate limiting. Current V1 uses PostgreSQL only.
