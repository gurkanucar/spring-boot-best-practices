<title>REST API Design Best Practices</title>

# REST API Design Best Practices

A small Student/Course/Enrollment API that exists to show REST design conventions, not
to be a real backend. Controllers and DTOs are the point; storage is an in-memory
`ConcurrentHashMap` (no database, no service layer), so restarting the app resets
everything.

Spring Boot 4.1.1 · Java 25 · no persistence

## Running it

```bash
./mvnw spring-boot:run
```

Swagger UI (live docs generated from the code): http://localhost:8080/swagger-ui.html
Raw OpenAPI spec: http://localhost:8080/v3/api-docs

## The domain

**Student** ↔ **Course** is many-to-many, modeled through **Enrollment** — a resource
in its own right, not a hidden join table. Enrollment has no `PUT`/`PATCH`/`DELETE`:
once created it only moves through `complete`/`drop` actions. Not every relationship
needs full CRUD; one with its own lifecycle deserves resource status instead.

| Resource | Fields |
|---|---|
| Student | `fullName`, `email` (unique), `phoneNumber` (optional), `status` |
| Course | `title`, `description` (optional), `capacity`, `status`: `DRAFT` → `PUBLISHED` → `ARCHIVED` |
| Enrollment | `studentId`, `courseId`, `status`: `ACTIVE` → `COMPLETED` / `DROPPED` |

## Endpoints

| Method & path | Purpose |
|---|---|
| `POST /api/v1/students` | Create |
| `GET /api/v1/students` | Paginated, sortable list |
| `GET /api/v1/students/{id}` | Fetch one; sets `ETag` |
| `PUT /api/v1/students/{id}` | Full replace; requires `If-Match` |
| `PATCH /api/v1/students/{id}` | Partial update (`application/json`); requires `If-Match` |
| `DELETE /api/v1/students/{id}` | Remove (blocked by any enrollment history) |
| `GET /api/v1/students/{id}/courses` | Relationship view: this student's courses |
| `GET /api/v2/students/{id}` | Versioning demo — see below |
| `POST /api/v1/courses` | Create (starts `DRAFT`) |
| `GET /api/v1/courses` | Paginated, filterable (`status`), sortable list |
| `GET /api/v1/courses/{id}` | Fetch one; sets `ETag` |
| `PUT /api/v1/courses/{id}` | Full replace (title/description/capacity only — see below); requires `If-Match` |
| `PATCH /api/v1/courses/{id}` | Partial update; requires `If-Match` |
| `DELETE /api/v1/courses/{id}` | Remove (blocked by any enrollment history) |
| `POST /api/v1/courses/{id}/publish` | Action: `DRAFT` → `PUBLISHED` |
| `POST /api/v1/courses/{id}/archive` | Action: `PUBLISHED` → `ARCHIVED` |
| `GET /api/v1/courses/{id}/students` | Relationship view: students enrolled in this course |
| `POST /api/v1/enrollments` | Create = enroll (body: `studentId`, `courseId`) |
| `GET /api/v1/enrollments` | Paginated, filterable (`studentId`, `courseId`, `status`) |
| `GET /api/v1/enrollments/{id}` | Fetch one |
| `POST /api/v1/enrollments/{id}/complete` | Action: `ACTIVE` → `COMPLETED` |
| `POST /api/v1/enrollments/{id}/drop` | Action: `ACTIVE` → `DROPPED` |

## Error format

Every error is one RFC 9457 `ProblemDetail` with an `errors` array:

```json
{
  "title": "Conflict",
  "status": 409,
  "detail": "Course is not accepting enrollments: status is DRAFT, must be PUBLISHED",
  "instance": "/api/v1/enrollments",
  "errors": []
}
```

`errors[]` is populated for field-level Bean Validation failures (`400`) and empty for
business-rule conflicts and not-found errors, where `detail` alone carries the message.

## Business rules → 409

- Student `email` must be unique.
- Enrolling requires the course to be `PUBLISHED`, not already at `capacity`, and the
  student not already `ACTIVE`-enrolled in it.
- `complete`/`drop` require the enrollment to currently be `ACTIVE`.
- `publish` only works from `DRAFT`; `archive` only works from `PUBLISHED`.
- Students and courses with any enrollment history (active, completed, or dropped) cannot be deleted; relationship views and history keep valid references.

## Partial updates with PATCH

Use `Content-Type: application/json` and send only the fields to update:

```http
PATCH /api/v1/courses/1
Content-Type: application/json
If-Match: "0"

{ "title": "New title" }
```

The controller receives a `PatchCourseRequest` or `PatchStudentRequest` DTO. Only
non-null fields are applied; omitted and explicit null fields keep their current
value. To clear an optional field such as description or phoneNumber, use PUT with
that field set to null. The resulting values are validated with the same rules as
PUT, so a blank title, invalid email, or non-positive capacity returns 400.

Student status can be patched; Course status still changes via publish/archive.

## Optimistic concurrency (ETag / If-Match)

`GET` on Student/Course sets a strong `ETag: "<version>"`. `PUT`/`PATCH` require
`If-Match`:

- Missing → `428 Precondition Required`.
- Present but stale → `412 Precondition Failed`.
- Present and current → succeeds, `version` increments, new `ETag` comes back.

The stores recheck the expected version while holding the same lock used for the
write. Two updates using the same version cannot both succeed. Student email
uniqueness is also checked inside that lock. This is an in-process guarantee for
the demo; persistent storage should enforce it with database concurrency controls.

Enrollment creation and parent deletion share an in-process lock so a new enrollment
cannot appear between the deletion guard and removal of its student or course.

`DELETE` and every Enrollment action skip this on purpose — overwriting someone's
concurrent edit is the problem this solves; deleting a resource you have a stale
reference to is a smaller one, and requiring `If-Match` everywhere would bury the
lesson under repetition.

Also deliberate: `Course`'s `PUT` body has no `status` field. State-machine transitions
go through `/publish` and `/archive` only — not every attribute should be freely
replaceable through a full update.

## Versioning

URI-based (`/api/v1/...`). `GET /api/v2/students/{id}` shows a breaking response-shape
change — `fullName` split into `firstName`/`lastName` (a naive split on the first
space, fine for a demo) — served alongside v1 against the exact same stored data.
Versioning is a contract decision, not a duplicated backend.

## Pagination and sorting

`?page=0&size=20&sortBy=title&sortDir=asc`, plus `status` filtering on Course/Enrollment
and `studentId`/`courseId` on Enrollment. Response shape:
`{ content, page, size, totalElements, totalPages }`.

`page` must be non-negative and `size` must be between 1 and 100. Out-of-range pages
return empty content; large offsets are calculated without integer overflow.

Deliberately **not** `?sort=field,dir` as a single value: Spring's default `List<String>`
binding splits a request parameter on commas, so a single `sort=title,asc` silently
becomes two list elements (`"title"`, `"asc"`) instead of one — a real bug this project
hit and fixed by using two separate params instead of fighting the converter. Only
single-field sort is supported; multi-field sort would need either repeated `sortBy`
params or a custom binder, and wasn't worth the complexity here.

## What's deliberately out of scope

No database (in-memory `ConcurrentHashMap` only — restart clears everything), no
service layer (business rules live in controllers/stores directly), no
authentication, no HATEOAS, no i18n (plain English messages, unlike the sibling
`validation/` project).
