# api-course-catalog — slice 9 (course routine)

## Done

- Flyway `V12__course_routine.sql`: weekly slots for a course, with weekday, start, end, place, and position.
- `PUT /api/v1/courses/{id}/routine` replaces the routine. An empty list clears it. `GET` returns the slots in position order.
- A non-empty routine is only stored for `OFFLINE` and `HYBRID`. `ONLINE`, overlapping slots, and a bad time return the planned errors and leave the previous routine in place.
- Switching `deliveryMode` to `ONLINE` while slots exist is rejected.
- Student course detail includes `routine`. An online course returns an empty list. A closed course stays `404`.

## Tested

- `CourseRoutineIT` — passed (local PostgreSQL via `TEST_DB_*`).

## Next

- Slice 10: opening roster (`CourseRosterIT`).
