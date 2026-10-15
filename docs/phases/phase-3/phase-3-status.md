# Phase 3 status

Status: complete (all Phase 3 API stages exited; full `Exam*IT` suite passes on PostgreSQL).

The binding spec is [phase-3-scope.md](phase-3-scope.md). This file is the glanceable step list. It does not repeat the spec. A stage plan adds order and test checkpoints. It does not drop an exit check, reorder these stages, or pull in a later-phase domain.

Phase 3 is API and automated tests only. There is no mobile stage and no web stage.

Phase 2's phone exit and browser exit do not block this phase. Start `api-exam-module` once Phase 2's API stages have exited.

## Stages

```text
api-exam-module → api-exam-questions → api-exam-components → api-exam-access → api-exam-purchase → api-exam-mcq → api-exam-written → api-exam-results → api-question-bank
```

| Stage | App | Plan | Tests | Rule | Progress |
|---|---|---|---|---|---|
| `api-exam-module` | `api/` | [plan](../../../api/.cursor/plans/api-exam-module.md) | [tests](../../../api/.cursor/tests/api-exam-module.md) | [rule](../../../api/.cursor/rules/api-exam-module.mdc) | [progress](../../../api/.cursor/progress/api-exam-module/) |
| `api-exam-questions` | `api/` | [plan](../../../api/.cursor/plans/api-exam-questions.md) | [tests](../../../api/.cursor/tests/api-exam-questions.md) | [rule](../../../api/.cursor/rules/api-exam-questions.mdc) | [progress](../../../api/.cursor/progress/api-exam-questions/) |
| `api-exam-components` | `api/` | [plan](../../../api/.cursor/plans/api-exam-components.md) | [tests](../../../api/.cursor/tests/api-exam-components.md) | [rule](../../../api/.cursor/rules/api-exam-components.mdc) | [progress](../../../api/.cursor/progress/api-exam-components/) |
| `api-exam-access` | `api/` | [plan](../../../api/.cursor/plans/api-exam-access.md) | [tests](../../../api/.cursor/tests/api-exam-access.md) | [rule](../../../api/.cursor/rules/api-exam-access.mdc) | [progress](../../../api/.cursor/progress/api-exam-access/) |
| `api-exam-purchase` | `api/` | [plan](../../../api/.cursor/plans/api-exam-purchase.md) | [tests](../../../api/.cursor/tests/api-exam-purchase.md) | [rule](../../../api/.cursor/rules/api-exam-purchase.mdc) | [progress](../../../api/.cursor/progress/api-exam-purchase/) |
| `api-exam-mcq` | `api/` | [plan](../../../api/.cursor/plans/api-exam-mcq.md) | [tests](../../../api/.cursor/tests/api-exam-mcq.md) | [rule](../../../api/.cursor/rules/api-exam-mcq.mdc) | [progress](../../../api/.cursor/progress/api-exam-mcq/) |
| `api-exam-written` | `api/` | [plan](../../../api/.cursor/plans/api-exam-written.md) | [tests](../../../api/.cursor/tests/api-exam-written.md) | [rule](../../../api/.cursor/rules/api-exam-written.mdc) | [progress](../../../api/.cursor/progress/api-exam-written/) |
| `api-exam-results` | `api/` | [plan](../../../api/.cursor/plans/api-exam-results.md) | [tests](../../../api/.cursor/tests/api-exam-results.md) | [rule](../../../api/.cursor/rules/api-exam-results.mdc) | [progress](../../../api/.cursor/progress/api-exam-results/) |
| `api-question-bank` | `api/` | [plan](../../../api/.cursor/plans/api-question-bank.md) | [tests](../../../api/.cursor/tests/api-question-bank.md) | [rule](../../../api/.cursor/rules/api-question-bank.mdc) | [progress](../../../api/.cursor/progress/api-question-bank/) |

Do not start a stage until the previous stage's exit passes. Do not start a later slice until that slice's progress note exists.

## How a step is marked done

1. The cases for that slice in the stage's test file pass.
2. **Automated checks (agent).** Run the matching `*IT` against real PostgreSQL. Do not use H2. Do not add a mobile or web CI workflow. The existing API workflow already runs the `*IT` suite.
3. **Manual QA.** None. This phase has no phone exit and no browser exit.
4. Write `progress/<stage>/slice-<number>-<name>.md` before the next slice. Record what was done, what was tested and the result, any failure and why, and what is next.

## Exits

- `api-exam-module`: a module with a multi-level tree of sections and papers can be created, edited, and reordered. Every module title, module description, section name, and paper name is stored and returned in both Bangla and English.
- `api-exam-questions`: questions can be created and attached to a paper's MCQ component or written component. Question text, options, the correct answer, and explanation are stored in one language. The question table has no second-language column.
- `api-exam-components`: a paper can be configured with a scheduled MCQ component, a deadline-bound written component, or both. Each component has its own Bangla and English description. The paper has a pass percent from 0 through 100, and the MCQ cannot be scheduled and the written deadline cannot be saved until that percent is set. A paper with both components returns a combined maximum that is the sum of the two components' question marks.
- `api-exam-access`: a published module is listed to a logged-in student. OPEN grants access. ENROLLED_ONLY mirrors current course access, including a full-free open course and an active enrollment for the current opening. PURCHASE_REQUIRED stays closed until a purchase exists. An inconsistent mode and course link is rejected. A student with access can read each component's rules. An MCQ component does not reveal its questions before the attempt starts.
- `api-exam-purchase`: a student can buy a `PURCHASE_REQUIRED` module through the existing manual-verification flow and gains access once a Master Admin confirms the payment. The purchase has no expiry. OPEN and ENROLLED_ONLY cannot be purchased.
- `api-exam-mcq`: a student with access can attempt an MCQ component once. The attempt freezes the questions, the correct option, the explanation, and the pass percent. It ends at the earlier of the server timer and the window end. A client clock cannot extend either. Submission stores an automatic score with no negative marking. The student receives that score, the maximum, pass or fail, the correct option, and the explanation only when the component is set to immediate results.
- `api-exam-written`: a student with access can read the written questions and upload, reorder, and remove answer images until final submit. Each stored image is a reduced JPEG. The upload cap is 15 MiB, which is under 20 MiB. Final submit locks the submission, snapshots the questions, and moves it to under-review. There is no student resubmit and no admin reopen.
- `api-exam-results`: an admin enters marks per written question, plus one feedback text. Publishing reveals that component for every student who has a result. A visible MCQ result includes the score, the maximum, the chosen option, the correct option, and the explanation. A visible written result includes the explanation. The paper result includes pass or fail from the snapshotted pass percent, and the combined score when both of that student's component scores are visible. The combined score is the MCQ score plus the sum of the written per-question marks. After every component on the paper is published, the student sees their rank and the leaderboard. An admin can read the leaderboard before publish and can read question statistics. A student who did not sit has no result row and is left off the board.
- `api-question-bank`: a Master Admin can browse questions used across papers and save a named bank that mixes MCQ and written questions. Those questions stay on their original papers. A System Admin receives `403`.

## Slices

Progress files are written when the slice finishes. Until then the slice is not started.

### api-exam-module

| Slice | Check |
|---|---|
| 1 Module record | `ExamModuleIT` |
| 2 Content tree | `ExamTreeIT` |

### api-exam-questions

| Slice | Check |
|---|---|
| 1 Question record and attach | `ExamQuestionIT` |

### api-exam-components

| Slice | Check |
|---|---|
| 1 Component configuration and combined maximum | `ExamComponentIT` |

### api-exam-access

| Slice | Check |
|---|---|
| 1 Access mode and course link | `ExamAccessModeIT` |
| 2 Student access gate | `ExamAccessGateIT` |

### api-exam-purchase

| Slice | Check |
|---|---|
| 1 Purchase and verify | `ExamPurchaseIT` |

### api-exam-mcq

| Slice | Check |
|---|---|
| 1 Attempt, timing, and scoring | `ExamMcqAttemptIT` |

### api-exam-written

| Slice | Check |
|---|---|
| 1 Submission | `ExamWrittenSubmissionIT` |

### api-exam-results

| Slice | Check |
|---|---|
| 1 Evaluation, review, and pass or fail | `ExamResultsIT` |
| 2 Rank, leaderboard, and question statistics | `ExamAnalyticsIT` |

### api-question-bank

| Slice | Check |
|---|---|
| 1 Bank curation | `QuestionBankIT` |

## Held for a later phase

LMS products often ship the items below. This phase's scope leaves them out. Do not add them to a stage plan.

| Later capability | Why it is not in this phase |
|---|---|
| Shuffling questions or options | Section 9 does not include a shuffle. Order stays the admin's order, frozen onto the attempt. |
| More than one correct option, partial credit, negative marking | Section 9: no negative marking. Section 8.1: the correct answer is singular. |
| Extra attempts, retakes, or a best-of score | Section 9: one MCQ attempt. Section 10: no resubmit and no admin reopen. |
| A weighted combined score | Section 11.2 and Section 12: the combined total is a plain sum. Rank uses that sum. |
| Drawing a random paper from the bank, importing questions, or a difficulty rating | Section 8.3: a bank is a named group. Section 8.2: questions are added onto a paper directly. |
| Extra time or a personal deadline | Section 9: one window and one timer for the component. |
| A late written submit, with or without a penalty | Section 10: after the deadline the draft stays unsubmitted. |
| Rubrics, anonymous scripts, or a second marker | Section 10: one marks value per question and one feedback text. Either admin may enter them. |
| Proctoring, a lockdown browser, or an IP restriction | Not in the scope. |
| A notice when a window opens or when results are published | Section 14: notices are out of scope. |
| A zero score for a student who never started | Section 11 covers a result the student produced. No row is invented. |
| Attendance, batches, a payment gateway, and exam screens | Section 14. |
