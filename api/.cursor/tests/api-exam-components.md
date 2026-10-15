# api-exam-components tests

Required cases for Phase 3 Stage 3. Java `*IT` classes are written during implementation. PostgreSQL only. No manual check.

Shared setup: a paper with both component shells and questions attached, plus a second paper with only an MCQ component.

## ExamComponentIT

Slice 1.

- Patching an MCQ component stores `description_en`, `description_bn`, `startsAt`, `endsAt`, `durationSeconds`, and `immediateResults`. A missing description is rejected. `endsAt` before `startsAt` is rejected. `durationSeconds` of 0 is rejected.
- Patching a written component stores its own `description_en`, `description_bn`, and `deadlineAt`. Sending `durationSeconds` on a written component, or `deadlineAt` on an MCQ component, returns `VALIDATION_FAILED`.
- Patching a component description does not change the module's `description_en` or `description_bn`, and does not change the other component's descriptions.
- `immediateResults` defaults to false when omitted.
- Scheduling an MCQ component whose window and both descriptions are stored sets the derived lifecycle from the server clock: `SCHEDULED` before `startsAt`, `ACTIVE` inside the window, and `ENDED` at or after `endsAt`. The test sets the window columns in the database. There is no route whose body sets lifecycle to `ACTIVE` or `ENDED`.
- `passPercent` stores a whole number from 0 through 100 on a paper. A value outside that range, and a pass percent sent for a section, returns `VALIDATION_FAILED`.
- Scheduling an MCQ, and saving a written deadline, while `passPercent` is null returns `PASS_MARK_REQUIRED`. After the percent is set, scheduling follows the window rules above.
- Scheduling a component that is missing a description returns `VALIDATION_FAILED`.
- A paper with both components returns `combinedMaxMarks` equal to the sum of the MCQ questions' marks and the written questions' marks. Adding a question changes that sum. The paper response has no weight field.
- A paper with only an MCQ component omits `combinedMaxMarks`.
