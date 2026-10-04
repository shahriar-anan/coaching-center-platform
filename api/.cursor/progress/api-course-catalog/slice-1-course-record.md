# api-course-catalog — slice 1 (course record)

## Done

- Flyway `V2__courses.sql` and `Course` entity with bilingual titles, content language, access type, status, close/opening fields.
- Admin routes: `POST/PATCH/GET/DELETE /api/v1/courses`, `POST .../archive`.
- Permissions via `CoursePermissionContributor` (`COURSE_READ`, `COURSE_MANAGE`, `COURSE_PRICE`, `COURSE_CLOSE`, `COURSE_DELETE`).
- Price rules, `PRICE_REQUIRED` on freemium publish, `ACCESS_TYPE_FIXED`, soft delete and archive audit rows.

## Tested

- `CourseCatalogIT` — passed (local PostgreSQL via `TEST_DB_*`).

## Next

- Slice 2: content tree (`CourseTreeIT`).
