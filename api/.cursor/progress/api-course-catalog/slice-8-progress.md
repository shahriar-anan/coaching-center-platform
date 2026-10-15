# api-course-catalog — slice 8 (lecture progress)

## Done

- Flyway `V11__lecture_progress.sql`: one progress row per student and lecture, with `watched` and `last_opened_at`.
- `POST /api/v1/me/courses/{courseId}/progress` records progress only when the student may already open the lecture. Omitted `watched` is false on create and unchanged on update. `lastOpenedAt` moves forward.
- `GET /api/v1/me/courses` adds `lastLectureId`. `GET /api/v1/me/courses/{courseId}/progress` lists the rows.
- An allowed lecture names the previous and next lecture in depth-first sibling order. A locked neighbor id is still returned. Reading a lecture does not write progress.

## Tested

- `LectureProgressIT` — passed (local PostgreSQL via `TEST_DB_*`).

## Next

- Slice 9: course routine (`CourseRoutineIT`).
