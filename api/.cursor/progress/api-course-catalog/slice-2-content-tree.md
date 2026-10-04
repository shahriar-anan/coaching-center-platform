# api-course-catalog — slice 2 (content tree)

## Done

- Flyway `V3__content_nodes.sql` with `SECTION` and `LECTURE` only.
- Node CRUD routes: `POST/PATCH/DELETE /api/v1/courses/{id}/nodes`, `POST .../nodes/reorder`.
- Reorder, cycle, and lecture-parent guards (`NODE_TYPE_REJECTED`). Freemium free-flag rules (`FREE_CONTENT_FIXED`).

## Tested

- `CourseTreeIT` — passed (local PostgreSQL via `TEST_DB_*`).

## Next

- Slice 3: files and lecture content (`CourseContentIT`).
