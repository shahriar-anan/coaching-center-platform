# api-course-catalog — slice 10 (opening roster)

## Done

- `GET /api/v1/courses/{id}/enrollments` under `COURSE_READ` returns the page envelope for one opening.
- The default opening is the course's current opening. `opening` selects another.
- Each row has the enrollment, the student name and phone, source, status, delivery mode, opening, and start time. Awaiting payment is included. No password is returned.

## Tested

- `CourseRosterIT` — passed (local PostgreSQL via `TEST_DB_*`).

## Next

- Slice 11: course instructors (`CourseInstructorIT`).
