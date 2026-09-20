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
| `PATCH /api/v1/students/{id}` | Partial update (`application/merge-patch+json`); requires `If-Match` |
| `DELETE /api/v1/students/{id}` | Remove |
| `GET /api/v1/students/{id}/courses` | Relationship view: this student's courses |
| `GET /api/v2/students/{id}` | Versioning demo — see below |
| `POST /api/v1/courses` | Create (starts `DRAFT`) |
| `GET /api/v1/courses` | Paginated, filterable (`status`), sortable list |
| `GET /api/v1/courses/{id}` | Fetch one; sets `ETag` |
| `PUT /api/v1/courses/{id}` | Full replace (title/description/capacity only — see below); requires `If-Match` |
| `PATCH /api/v1/courses/{id}` | Partial update; requires `If-Match` |
| `DELETE /api/v1/courses/{id}` | Remove (blocked if any `ACTIVE` enrollment exists) |
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
- A course with any `ACTIVE` enrollment can't be deleted.

## PATCH is real JSON Merge Patch (RFC 7396)

Not an ad-hoc "send only the changed fields" DTO — the actual IETF-standardized
mechanism. Request `Content-Type: application/merge-patch+json` (a different
`Content-Type` gets a clean `415`). The merge rule: a field absent from the patch body
is left unchanged; present with a value replaces it; present as explicit `null` clears
it — but only for `Student.phoneNumber` and `Course.description`, the two fields where
"no value" is a legitimate state. Sending explicit `null` for a required field (like
`fullName`) is a `400`, not a silent no-op.

Records have no setters, so this is implemented as an explicit field-by-field merge
against a raw `JsonNode`, not Jackson's `readerForUpdating` (which is built for mutable
beans).

## Optimistic concurrency (ETag / If-Match)

`GET` on Student/Course sets a strong `ETag: "<version>"`. `PUT`/`PATCH` require
`If-Match`:

- Missing → `428 Precondition Required`.
- Present but stale → `412 Precondition Failed`.
- Present and current → succeeds, `version` increments, new `ETag` comes back.

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
