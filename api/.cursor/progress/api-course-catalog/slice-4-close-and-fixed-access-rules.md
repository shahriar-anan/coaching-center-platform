# api-course-catalog — slice 4 (close and fixed access rules)

## Done

- `POST /api/v1/courses/{id}/close` and `.../reopen` under `COURSE_CLOSE` (Master Admin only).
- First publish sets `opening_number` to 1 and `closed` false. Close/reopen audit and opening rules per spec.

## Tested

- `CourseCloseIT` — passed (local PostgreSQL via `TEST_DB_*`).

## Stage exit

All four slices passed: `CourseCatalogIT`, `CourseTreeIT`, `CourseContentIT`, `CourseCloseIT`.

## Next

- `api-library-catalog` slice 1 (book record).
