# api-course-catalog — slice 11 (course instructors)

## Done

- Flyway `V13__course_instructors.sql`: a course profile with name, description, optional photo, and position. It is not a user and has no login.
- Create, update, photo upload, list, and Master Admin delete. Delete is audited as `COURSE_INSTRUCTOR_DELETE`.
- A photo accepts JPEG, PNG, and WebP, is stored like a cover, and is left out of course, section, and lecture material lists. Students can download it while the course is published and open. A closed course does not serve it. An admin with `COURSE_READ` still can.
- Student course detail includes `instructors`.

## Tested

- `CourseInstructorIT` — passed (local PostgreSQL via `TEST_DB_*`).

## Next

- Course-depth follow-on slices 6–11 are in place on the API.
