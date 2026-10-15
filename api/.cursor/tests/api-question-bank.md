# api-question-bank tests

Required cases for Phase 3 Stage 9. Java `*IT` classes are written during implementation. PostgreSQL only. No manual check.

Shared setup: a Master Admin, a System Admin, an MCQ question attached to paper A, and a written question attached to paper B.

## QuestionBankIT

Slice 1.

- A System Admin `GET /question-bank/questions` and `POST /question-banks` return `403`. That System Admin can still create a paper question with `EXAM_MANAGE`.
- The Master Admin browse list includes both questions. Filtering by `paperId` returns only the question on that paper. Filtering by `questionType` `WRITTEN` returns the written question. The row lists current paper ids and does not remove the attachment.
- Creating a bank with both question ids stores one `name` and both items. The `question_banks` table has no `name_en` or `name_bn` column. An empty `questionIds` list is rejected.
- After create, both papers still return their original questions.
- Removing one question from the bank leaves it on its paper and leaves the other bank item in place.
- Bank detail returns the saved order. A student call returns `403`.
- The generated OpenAPI document has no route that starts an attempt from a bank id.
