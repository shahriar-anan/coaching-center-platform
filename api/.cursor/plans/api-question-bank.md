# api-question-bank

Stage plan for Phase 3 Stage 9. The binding spec is [phase-3-scope.md](../../../docs/phases/phase-3/phase-3-scope.md) Section 8.3 and Stage 9. This plan adds order only. It does not drop an exit check.

Required cases are in [api-question-bank.md](../tests/api-question-bank.md). The stage rule is [api-question-bank.mdc](../rules/api-question-bank.mdc).

Start only after `api-exam-results` exits. This is the last Phase 3 stage.

Do not turn a bank into an attempt, a paper, or a purchasable module. Do not draw a random paper from it. Adding a question to a paper stays the Stage 2 attach route (Section 8.2). The bank is only the named group in Section 8.3. Do not grant this permission to `SYSTEM_ADMIN`.

When the slice's checks finish, write `api/.cursor/progress/api-question-bank/slice-1-bank.md`.

## Permissions

| Permission | Master Admin | System Admin | Student |
|---|---|---|---|
| `QUESTION_BANK_MANAGE` | yes | no | no |

The bank name is one string. Section 2's bilingual list does not include it. Do not add `name_en` and `name_bn`.

## Slice 1 — Bank curation

Flyway `V18__question_banks.sql`.

`question_banks`: `name`, and the base-entity columns. No language pair.

`question_bank_items`: bank id, question id. Unique pair. No question-type check. Position is the order in the create list.

- `GET /api/v1/question-bank/questions` under `QUESTION_BANK_MANAGE`. Paged. Optional filters: `subject`, `questionType` (`MCQ` or `WRITTEN`), `paperId`. The list includes questions attached to any paper, including papers the caller did not create. Each row includes the question id, type, subject, and the paper ids it is currently attached to. It does not detach anything.
- `POST /api/v1/question-banks` with `name` and `questionIds`. The name is required. At least one question id is required. Unknown ids return `404`. A mix of MCQ and written ids is valid. Creating the bank does not delete `component_questions` rows.
- `GET /api/v1/question-banks/{id}` returns the name and the questions in saved order.
- `DELETE /api/v1/question-banks/{id}/questions/{questionId}` removes the bank item only, under `QUESTION_BANK_MANAGE`. The question and its paper attachments remain. This is not `EXAM_DELETE`.
- A System Admin call to any of these routes returns `403`. A System Admin can still author paper questions through `EXAM_MANAGE`.

## Stage exit

A Master Admin can create a named bank from questions on more than one paper, those papers still contain the questions, and a System Admin cannot use the bank routes.
