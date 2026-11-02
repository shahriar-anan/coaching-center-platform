# Agent B brief: exam rules, withdraw, texts and written rules (API)

Read first: [README.md](README.md), sections 1 (D1 to D8, D10 to D12, D16 to D18), 3, 4 and 6.

Goal: withdrawn exams can be edited and their staged content published, withdrawing is refused during open sittings, the structure lock is gone, descriptions and rules are optional, MCQ rescoring is removed, and written maximums are checked against the marks already given.

Owned files: see README section 3.

## Tasks

**B1. Edit routing (`D5`).** In `LiveExamService`, change `requireEditable` so an archived exam is no longer refused. Add one helper, for example `takesStagedEdits(LiveExam exam)`, which returns true when the status is `PUBLISHED`, or when the status is `ARCHIVED` and `publishedAt` is set. Use it everywhere the code now checks `LiveExam.PUBLISHED` to decide between staging and direct edits:
- `update`;
- `addQuestion`, `updateQuestion`, `deleteQuestion`, `reorderQuestions`;
- `uploadQuestionPdf` (it is in `LiveExamService`);
- `publish` (see B2).

**B2. Publishing a withdrawn exam (`D6`).** In `LiveExamService.publish(id, revisionId, actorId)`:
- If the exam is `ARCHIVED` and has a staged revision, publish the revision with `staging.publish`, keep the status `ARCHIVED`, and return.
- If the exam is `ARCHIVED` with no revision, refuse with `EXAM_ARCHIVED`. The message says the exam must be restored before it can be published (wording in B9).
- Keep the rest of the path unchanged for non-archived exams.
- Note the current order: the archived check runs first and throws. Change the order so the revision check comes before the refusal.

**B3. Withdraw refused during an open sitting (`D7`).** In `LiveExamService.archive(id)`:
- Before the status changes, check for an open sitting: a participant of this exam that is `IN_PROGRESS`, has no `submittedAt`, and whose `deadlineAt` is after now.
- If one exists, refuse with `EXAM_SITTING_OPEN` (409). Message in README section 4.
- Add the repository query in `LiveExamParticipantRepository` (B owns it).
- Restore (`D8`) is unchanged.

**B4. Remove the structure lock (`D11`).**
- Remove the `requireStructureOpen` calls in `addQuestion`, `updateQuestion`, `deleteQuestion` and `reorderQuestions`, and in the staged variants.
- Remove the structure check in `LiveExamRevisionService.publish` (`structureChanged` and `participants.existsByExamId`).
- Remove `requireStructureOpen`, `optionCountChanges` and `structureChanged` if nothing else uses them.
- In the staged update path, the option count check (`requireStructureOpen`) is also removed.

**B5. Soft-delete rows (`D12`).** Replace every hard delete of a question or option with a soft delete (set `deletedAt`):
- `LiveExamRevisionService.syncOptions`: `options.deleteAll(rows.values())` becomes soft deletes.
- `LiveExamService.replaceOptions`: `options.deleteAll(current)` becomes soft deletes, before the new rows are saved.
- Check that every read of options and questions for current content filters on `deletedAt IS NULL`. Pinned attempts do not read these rows through the current-content queries.

**B6. Remove MCQ rescoring (`D10`).**
- In `LiveExamRevisionService.publish`: remove the `rescorer.rescore` call, the `questions.flush()` and `options.flush()` calls around it, and the `rescored` assignment. Set `rescored` to 0.
- Remove the `McqRescorer` field and constructor parameter from `LiveExamRevisionService`.
- Delete `McqRescorer.java`. First search the codebase, including tests, for other references, and remove them.
- In `ExamChangeListBuilder`: `Impact` must report 0 submitted papers for MCQ. Remove `scoringChanged` if it is now unused, but keep the `Impact` record fields.

**B7. Optional texts (`D18`).**
- Remove `requireTexts` and its two calls (`LiveExamRevisionService.requirePublishable` and `LiveExamService.publish`). Delete `requireTexts` if nothing else uses it.
- In `LiveExamService.applyText`, on edits, rich-text fields (`descriptionEn`, `descriptionBn`, `rulesEn`, `rulesBn`) use `textOrBlank` so a blank value is stored as `""`. Titles keep `requiredText`.
- In `LiveExamModuleService`: `create` and the edit path store blank descriptions as `""`. Use the same rule as the exam.
- Remove `EXAM_TEXT_REQUIRED` from `ErrorCode.java`.

**B8. Written maximum (`D16`).**
- Add a repository query in `LiveExamParticipantRepository` for the highest mark already given to an evaluated written participant of an exam. Count the saved score and the pending score (`pendingScore`) when a revision is pending.
- In `requirePublishable` (`LiveExamRevisionService`) and in the written branch of `applyEdit` (`LiveExamService`), refuse a maximum below that mark with `WRITTEN_MAXIMUM_BELOW_MARK`, as a field error on `writtenMaximum`.
- Check the evaluation fields in `LiveExamEvaluationService` for the exact names before writing the query.

**B9. Withdraw wording in API messages (`D1`).** Every API message for an exam that says archived or archive must say withdrawn:
- `EXAM_ARCHIVED` thrown in `publish`, `copy` and the `requireEditable` path (if it remains anywhere), and any other `EXAM_ARCHIVED` site you find;
- the `NOT_ARCHIVED` message for the exam restore path ("Only a withdrawn exam can be restored."). Leave the package message unchanged, because `NOT_ARCHIVED` is shared with packages. If it is shared, add an exam-only variant, or report the choice.
- Do not rename any error code or route.

**B10. Tests (write, do not run).**
- Update `ExamAuthoringStep1IT.publishRefusesBlankTextAndNamesEachField`. The new behaviour is that publish succeeds with blank texts.
- Withdrawn exam: edits are staged when it was published; direct when it was never published.
- A staged revision of a withdrawn exam publishes and stays withdrawn.
- Withdraw is refused with `EXAM_SITTING_OPEN` while a sitting is open, and allowed after it ends.
- Blank descriptions and rules on an exam and on a package.
- Adding and removing a question after a student started: allowed.
- Soft-deleted options: the row still exists, and a pinned answer still resolves.
- Written: a maximum below the highest mark is refused, both at edit and at publish.
- Publishing a revision on an MCQ exam does not call the rescorer.

## Acceptance (what your report must show)

- Every item in B1 to B9 is done, or is listed as a deviation.
- `McqRescorer` is deleted and has no references left.
- `QUESTION_STRUCTURE_LOCKED` and `EXAM_TEXT_REQUIRED` have no references left (or the report says where one remains and why).
- No hard delete of a question or option row remains in the exam code.
- No file outside your list was edited.
