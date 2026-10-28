# A4: staged edits, change list, publish with revision (agent A)

Committed together with A3 (paper versions): one code path and one test class, `ExamRevisionIT`. See [a-a3.md](a-a3.md).

## What changed

- **Staged edits.** A published exam's edits (settings, questions, question PDF) go into `live_exam_revisions.payload` as the full proposed state (`ExamState`). Draft exams still write rows directly. Editing a published exam never changes its status (E27).
- **Working copy.** Staff `GET /live-exams/{id}` returns the working copy with `pendingChanges: true`. Students and the sitting read the published rows.
- **Change list.** `GET /live-exams/{id}/pending-changes` returns `ChangeList` from `ExamChangeListBuilder`: `fields`, `questions`, `paper`, `impact`. The update notice fires when `liveAt`, `endAt`, `durationMinutes`, the paper version or MCQ question content (stem, option text, correct key, marks, multipleAnswer, explanation) changes, per the orchestrator's ruling. `DELETE /pending-changes` discards.
- **Publish.** `POST /live-exams/{id}/publish` takes an optional `{revisionId}`. The exam row is locked first (`lockById`). A stale id gives `PENDING_CHANGES_STALE`. Without a revision, a published exam is published as before (compatibility rule 4, logged at warn). With a revision: validate, apply, re-score when scoring changed (`McqRescorer`), write `live_exam_change_log`, audit `LIVE_EXAM_REVISION_PUBLISH`, delete the revision, and after commit call `StudentNoticeWriter.examUpdated` when the notice rule holds. No mail.
- **Question CRUD on a published exam** is staged. Option ids are kept when the count is unchanged. A count change after a start is refused (`QUESTION_STRUCTURE_LOCKED`).
- **Copy** takes the working copy (orchestrator answer 2): all active memberships, no dates, price, discount or `popular`.
- **Pending list.** `LiveExamRevisionService.pending()` and its repository queries are in place; the route and tests come with A5.

## Tests

1. First invocation, exit 1: `ExamRevisionIT,ExamQuestionMultiAnswerIT,ExamWindowAuthoringIT,LiveExamIT,LiveExamArchiveIT,LiveSittingIT,LiveEvaluationIT,AuditLogIT,ExamMailIT`.
   - Green: `ExamRevisionIT` 10/0, `ExamQuestionMultiAnswerIT` 4/0, `ExamWindowAuthoringIT` 12/0, `LiveExamArchiveIT` 2/0, `LiveEvaluationIT` 63/0, `ExamMailIT` 13/0.
   - Fixed and rerun: `LiveExamIT` (one staged-delete assertion), `AuditLogIT` (two new routes), `LiveSittingIT` (one `DRAFT` assertion and one routine count, both E27).
2. Rerun of `LiveExamIT,AuditLogIT,LiveSittingIT` before the merge: `LiveExamIT` 11/0, `AuditLogIT` 3/0. `LiveSittingIT` 34 with 2 failures: the archived-package test (fixed on integration) and the routine count (fixed in this slice).
3. Final `LiveSittingIT` run after merging `nr01/integration` (`e925cf2`): **34/0**. This includes `aRemovedRosterOrArchivedModuleRejectsAnotherSubmission`, passing with the orchestrator's `LiveExamAccess` fix. Not weakened.

## Changes to existing tests (decided behaviour)

- `LiveExamIT.deletingAQuestionClosesTheGap...`: the question delete is staged (E31), so the rows still hold the question and no `LIVE_EXAM_QUESTION_DELETE` audit row is written until publish. The assertions now check that.
- `LiveSittingIT.anUndatedExamSitsLastOnTheRoutine...`: E27 keeps a published exam published, so after the edit the undated exam still appears on the routine, last. The `DRAFT` expectation becomes `PUBLISHED`, and the routine expectation becomes two items.
- `AuditLogIT`: the A block lists `GET` and `DELETE /live-exams/{id}/pending-changes`.
- `ExamQuestionMultiAnswerIT` (A2): the row-level assertions after a published-exam edit are replaced by checks on the staged response, because the rows keep their wording until publish.

## Deviations

- A3 and A4 are one commit and one test invocation, because the paper path and the revision share one code path and one test class.
- `LiveExamService.publish(id)` (one argument) is kept as an overload for `ExamMailIT`, which is orchestrator-owned.
- The notice rule follows the orchestrator's ruling (time, paper, MCQ content), not the A-file's wording.

## Pending

- `LiveSittingIT.aRemovedRosterOrArchivedModuleRejectsAnotherSubmission`: passes now with the orchestrator's `LiveExamAccess` fix. Listed here as pending the orchestrator's access fix, as instructed; it is not weakened.
