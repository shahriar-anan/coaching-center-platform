# Phase 3 status

Status: complete. Slice 2 of `api-live-sitting` and `api-live-evaluation` passed. An archived category hides its whole branch from students, including an exam opened by id, and a posted result stays readable.

The binding spec is [phase-3-scope.md](phase-3-scope.md). This file is the step list. A stage plan adds order and tests. It does not drop an exit, reorder these stages, or pull screens into an API stage.

Phase 3 stays `api/` and its `*IT` classes. There is no phone exit and no browser exit.

## Stages

```text
api-live-catalog → api-live-exam → api-live-roster → api-live-sitting → api-live-evaluation
```

| Stage | Plan | Tests | Rule |
|---|---|---|---|
| `api-live-catalog` | [plan](../../../api/.cursor/plans/api-live-catalog.md) | [tests](../../../api/.cursor/tests/api-live-catalog.md) | [rule](../../../api/.cursor/rules/api-live-catalog.mdc) |
| `api-live-exam` | [plan](../../../api/.cursor/plans/api-live-exam.md) | [tests](../../../api/.cursor/tests/api-live-exam.md) | [rule](../../../api/.cursor/rules/api-live-exam.mdc) |
| `api-live-roster` | [plan](../../../api/.cursor/plans/api-live-roster.md) | [tests](../../../api/.cursor/tests/api-live-roster.md) | [rule](../../../api/.cursor/rules/api-live-roster.mdc) |
| `api-live-sitting` | [plan](../../../api/.cursor/plans/api-live-sitting.md) | [tests](../../../api/.cursor/tests/api-live-sitting.md) | [rule](../../../api/.cursor/rules/api-live-sitting.mdc) |
| `api-live-evaluation` | [plan](../../../api/.cursor/plans/api-live-evaluation.md) | [tests](../../../api/.cursor/tests/api-live-evaluation.md) | [rule](../../../api/.cursor/rules/api-live-evaluation.mdc) |

Do not start a stage until the previous stage's exit passes. Write `api/.cursor/progress/<stage>/slice-1.md` when that slice's tests pass, before the next stage.

## Exits

- `api-live-catalog`: a category tree of unlimited depth, a module placed under one category, more than one module in the same category, draft on each change, final save, then publish. Students do not see a draft.
- `api-live-exam`: an MCQ exam and a written exam on the same module, pass percent required before publish, MCQ maximum equal to the sum of question marks, written maximum stored on the exam, one question PDF, a five-minute gap inside the module, and the exam still published after the live window ends.
- `api-live-roster`: only a Master Admin marks students, by email, phone, name, or a paste of email and phone. A marked student sees the published exams. A System Admin receives `403`.
- `api-live-sitting` slice 1: Take exam creates the participant. MCQ answers can change until the deadline and are posted then, including a blank paper as zero. The score is immediate. The answer review waits until the sitting ends. A written PDF replaces the previous file, rejects over 80 MB, and a missing PDF is failed to submit. One retake exists only for a student who never tapped during the live window, and it stays off the live leaderboard.
- `api-live-sitting` slice 2: an early submit does not freeze MCQ answers. The deadline posts the paper without a student submit call. The participant can download the written question PDF only during that sitting. Removing the roster row after Take exam leaves the result readable.
- `api-live-evaluation` slice 1: one instructor per module, created like a System Admin. That instructor or the Master Admin saves a written total, an optional comment, and an evaluated PDF. The student sees them on save and never sees the evaluator. A later save recalculates pass or fail and rank. The MCQ live leaderboard opens five minutes after the window.
- `api-live-evaluation` slice 2: the instructor browses assigned modules only and can download the answer PDF. A System Admin can read the evaluator name and cannot save a mark. A comment is visible to the student when one was entered. A written retake is marked and stays off the live board. The submission list shows the deadline post without a student submit call.

## Previous build

The stages `api-exam-module` through `api-question-bank` exited against the replaced model. Their progress notes under `api/.cursor/progress/` are history. Do not extend those stages. Their plan, rule, and test files now point here.

## How a step is marked done

1. The cases in the stage's test file pass.
2. Run the matching `*IT` against real PostgreSQL. Do not use H2.
3. There is no manual QA in this phase.
4. Write the progress note before the next stage.
