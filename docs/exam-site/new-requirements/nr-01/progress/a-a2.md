# A2: questions, multi-answer and the structure lock (agent A)

## What changed

- `multipleAnswer` is read on create and update (absent keeps the current value; a non-boolean is `VALIDATION_FAILED`).
- Key rules: a single-answer question has exactly one correct option; a multi-answer one has at least one; every question has at least two options; every option needs text. Turning `multipleAnswer` off re-checks the key.
- Structure lock (R16): once any participant row exists for the exam (any status), adding or deleting a question, deleting a question's option set, or changing the option count in a PATCH returns `409 QUESTION_STRUCTURE_LOCKED`. Wording, correct flags, marks, explanation and `multipleAnswer` stay editable.
- Options keep their ids when the count is unchanged: rows are updated in place, so an answer already given still points at its option.

## Tests

One invocation, exit 0 (`ExamQuestionMultiAnswerIT,ExamQuestionIT,LiveExamIT,ExamWindowAuthoringIT`):

| Class | Tests | Failures | Errors |
|---|---|---|---|
| `ExamQuestionMultiAnswerIT` (new) | 4 | 0 | 0 |
| `ExamQuestionIT` (legacy course engine, owned by A by settlement) | 1 | 0 | 0 |
| `LiveExamIT` | 11 | 0 | 0 |
| `ExamWindowAuthoringIT` | 12 | 0 | 0 |

## Deviations

- The submit step in the locked-exam test uses the existing `POST /submit` route; the re-score it implies is asserted in A4.
