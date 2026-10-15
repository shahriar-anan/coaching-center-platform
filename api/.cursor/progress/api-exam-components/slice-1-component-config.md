# Slice 1 — Component configuration and combined maximum

## Done
- Flyway `V17__exam_component_config.sql` (`pass_percent` on papers; component descriptions, MCQ window, written deadline).
- PATCH component, POST schedule, GET/PATCH paper with `combinedMaxMarks` when both components exist.
- `PASS_MARK_REQUIRED` for schedule and written deadline without pass percent.
- Derived MCQ lifecycle (`DRAFT` / `SCHEDULED` / `ACTIVE` / `ENDED`).
- `ExamComponentIT`.

## Tested
- `mvn test -Dtest=ExamComponentIT,ExamQuestionIT` — **passed**.

## Next
- `api-exam-access`.
