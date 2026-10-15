# Slice 1 — Access mode and course link

## Done
- Flyway `V18__exam_access.sql` (`access_mode`, `course_id` on `exam_modules`).
- `PATCH /api/v1/exam-modules/{id}/access` with mode/link validation and new error codes.
- Admin `ModuleView` includes `accessMode` and `courseId`.
- `ExamAccessModeIT`.

## Tested
- `mvn test -Dtest=ExamAccessModeIT,ExamModuleIT` — **passed**.

## Next
- Slice 2: student catalog and component gate (`ExamAccessGateIT`).
