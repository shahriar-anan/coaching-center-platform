# api-exam-questions

Stage plan for Phase 3 Stage 2. The binding spec is [phase-3-scope.md](../../../docs/phases/phase-3/phase-3-scope.md) Sections 2, 8.1, and 8.2, and Stage 2. This plan adds order only. It does not drop an exit check.

Required cases are in [api-exam-questions.md](../tests/api-exam-questions.md). The stage rule is [api-exam-questions.mdc](../rules/api-exam-questions.mdc).

Start only after `api-exam-module` exits. Do not start `api-exam-components` until this stage exits.

This stage creates a component shell so a question has a place to attach. Schedule, duration, deadline, rules text, and the combined total are the next stage. Do not build access checks, purchases, attempts, uploads, results, or the question bank.

Package stays `com.coachingcenter.api.exams`.

When the slice's checks finish, write `api/.cursor/progress/api-exam-questions/slice-1-question-record.md`.

## Slice 1 — Question record and attach

Flyway `V11__exam_questions.sql`.

`paper_components`: paper id, `component_type` (`MCQ` or `WRITTEN`). At most one row of each type per paper. No rules text and no schedule columns in this migration.

`questions`: `question_type` (`MCQ` or `WRITTEN`), `stem`, `explanation` nullable, `marks` numeric, `subject`. Base-entity columns. No language column and no `*_en` / `*_bn` column.

`question_options`: question id, `text`, `correct` boolean, `position`. Used only by MCQ.

`component_questions`: component id, question id, `position`. Unique pair. The same question id may appear on more than one component.

- `POST /api/v1/exam-papers/{paperId}/components` with `type` under `EXAM_MANAGE`. A second component of the same type on that paper returns `409`, code `COMPONENT_EXISTS`. A section id returns `400`, code `NODE_TYPE_REJECTED`.
- `POST /api/v1/exam-components/{componentId}/questions` creates a question and attaches it. `POST /api/v1/exam-components/{componentId}/questions/{questionId}` attaches a question that already exists.
- An MCQ question requires at least two options and exactly one `correct: true`. A written question has a stem, marks, and subject, and no options. The other shape returns `400`, code `VALIDATION_FAILED`.
- Attaching an MCQ question to a written component, or a written question to an MCQ component, returns `400`, code `QUESTION_TYPE_MISMATCH`.
- `marks` must be greater than zero. `subject` is one required string. It is not a course-category id. An MCQ question has exactly one correct option. Do not add a multi-correct mode.
- `PATCH /api/v1/questions/{questionId}` edits the shared row under `EXAM_MANAGE`. The options array is the full replacement, in display order. Papers that have not yet frozen the question onto an attempt or a written submission see the edit. Do not copy the question on attach.
- `POST /api/v1/exam-components/{componentId}/questions/reorder` under `EXAM_MANAGE` sets sibling positions. Section 8.2 is the admin building that paper's order. The same order is what a later attempt freezes.
- `DELETE /api/v1/exam-components/{componentId}/questions/{questionId}` detaches only that link under `EXAM_MANAGE`. The question row remains.
- There is no question delete route in this stage.
- Admin `GET` of a component returns its questions in position order. MCQ options include `correct`. The JSON keys are `stem`, `explanation`, `text`, and `subject`. There is no second-language key.
- Soft-deleting a paper soft-deletes its component rows and attachment rows. Question rows remain.

## Stage exit

Questions can be created and attached to a specific paper's MCQ or written component through the API. Each question is stored in one language only.
