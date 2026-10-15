# Slice 1 — Question record and attach

## Done
- Flyway `V16__exam_questions.sql` (components, questions, options, links).
- `ExamComponentService` / `ExamComponentController` for create, attach, patch, get, reorder, detach.
- Paper delete cascades soft-delete on components and links via `ExamNodeService`.
- `ExamQuestionIT` covers MCQ/written create, type mismatch, attach, reorder, detach, paper delete.

## Tested
- `mvn test -Dtest=ExamQuestionIT` with local PostgreSQL (`TEST_DB_*`) — **passed**.

## Next
- `api-exam-components` (configuration, pass percent, combined max).
