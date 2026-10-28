# Web impact: exam authoring (agent A)

What the later UI plan must change because of stage 2 slice A. Nothing here is done in this plan; the current website keeps working under the compatibility rules in [../contracts.md](../contracts.md#7-compatibility-rules-the-current-website-keeps-working).

## Exam editor fields

The staff exam editor needs inputs for the new fields (all on `POST /live-exam-modules/{id}/exams`, `POST /live-exams` and `PATCH /live-exams/{id}`):

- `mode` (`LIVE` or `PRACTICE`). A practice exam has no dates. The mode can change only on a draft nobody has started; on anything else the API answers `400` with `fieldErrors[0].field = "mode"`.
- `liveAt` and `endAt` (the window) and `durationMinutes`, separately. The window must be at least as long as the duration (`EXAM_WINDOW_INVALID`). Today the web sends only `liveAt`; the API then sets `endAt = liveAt + duration`. A duration-only change on a window that was exactly one duration long moves the end with it. The new editor should send both dates.
- `negativeMarks` (decimal, default empty = none; the UI suggests 0.25 when switched on), `answersChangeable` (default on), `previewEnabled`.
- Commercial fields, Master Admin only (the API answers `403` to a System Admin): `price`, `free`, `discountPercent` (1 to 99), `discountEndsAt`, `accessDays`. Errors come back as `fieldErrors` with the field names.
- A question has `multipleAnswer`. With it off, exactly one option is correct; with it on, at least one. The paper says "select all that apply" (UI).
- Remove the locked state of the editor (`exam-editor.tsx` `locked`): the API has no schedule or content lock. The only structure lock is `QUESTION_STRUCTURE_LOCKED` (409) when a student has started: no question or option can be added or removed; wording, key, marks and explanation can still change.

## Standalone exam create

`POST /live-exams` with `categoryId` creates an exam that is in no package (R2). The category page needs a "New exam" action. `ExamView.moduleId` is now null for such an exam; `packages` is the list of packages it is in.

## Staged edits

Editing a published exam never changes its status and never hides it from students. The edit is kept as a revision and the staff `GET /live-exams/{id}` returns that working copy with `pendingChanges: true`.

- Banner on the exam when `pendingChanges` is true: "Changes waiting to be published".
- Change-list dialog: `GET /live-exams/{id}/pending-changes` returns `{revisionId, fields[{field, before, after}], questions[{questionId, position, change, before, after}], paper{beforeVersion, afterVersion, afterFileName}, packageExams, impact{papersToRescore, notifiesStudents, studentsToNotify}}`. Show every field, both languages, the question changes, the paper versions, and the impact lines ("N submitted papers will be re-scored", "N students will get a notice").
- Discard: `DELETE /live-exams/{id}/pending-changes` (204).
- Publish: `POST /live-exams/{id}/publish` with `{ "revisionId": "<from the change list>" }`. A stale id answers `409 PENDING_CHANGES_STALE`: re-read the change list. `409 NO_PENDING_CHANGES` when nothing is staged. `409 QUESTION_STRUCTURE_LOCKED` when a student started after the edit was staged: discard and edit again.
- **Compatibility rule 4 (remove later):** the current publish button sends no body and the API publishes the current revision, logging a warning. Once the new dialog sends `revisionId`, drop that rule from the API.
- The System Admin can stage and discard but not publish (`403` on publish).

## Pending badge

`GET /live-exam-pending-changes` returns `{exams, packages, items[{kind: EXAM|PACKAGE, id, titleEn, titleBn, updatedAt, updatedBy}]}`, newest first, for the Master Admin and System Admin sidebar badge. Package items appear once agent B's package revision exists.

## Preview page (student)

`GET /live-exams/{id}/preview` and `GET /live-exams/{id}/preview/question-pdf`. `404` unless the exam is published, `previewEnabled`, and its window has ended or it is a practice exam. An MCQ preview lists stems, options and `multipleAnswer` with no key and no explanation; a written one returns `hasPaper` and the PDF. It needs no purchase and no access.

## Question PDF versions

Every upload is a new paper version. On a published exam the new version is part of the staged edit; students keep the old one until the publish. `ExamView.questionPdf` for staff reflects the working copy.

## Answers released switch

`PUT /live-exams/{id}/answers-released` is kept and stores its value, but nothing reads it any more once D removes the reads (answers show at submit, P-d). The control can be removed from the UI.

## New read-only fields on `ExamView`

`mode`, `phase` (`UNANNOUNCED`, `UPCOMING`, `LIVE`, `ENDED`, `PRACTICE`; computed from the published exam), `categoryId`, `packages[{id,titleEn,titleBn}]`, `instructor{id,name}` (staff), `negativeMarks`, `answersChangeable`, `previewEnabled`, `popular`, `accessDays`, `pricing` (`PriceView`), `pendingChanges` (staff), and for students `access{granted,source,endsAt,expired}`, `attempts`, `myStatusDetail`. Questions carry `multipleAnswer`.
