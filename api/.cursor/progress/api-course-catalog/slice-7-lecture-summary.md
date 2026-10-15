# api-course-catalog — slice 7 (lecture summary)

## Done

- Flyway `V10__lecture_summary.sql`: lecture `description` and `duration_minutes`.
- PATCH on a lecture stores and clears both. A section rejects them. `durationMinutes` below 1 is `400` and leaves the previous value.
- Student course detail includes them on the outline. An allowed lecture returns them. A refusal does not.

## Tested

- `LectureSummaryIT` — passed (local PostgreSQL via `TEST_DB_*`).
- `CourseContentIT`, `CourseTreeIT`, `CatalogCourseReadIT`, `ContentAccessIT` — passed on the same database.

## Next

- Slice 8: lecture progress (`LectureProgressIT`).
