# Stage 2: Exam authoring and staged edits (agent A)

**Covers** decisions C10', C11, C13, C14, D15–D18, E27–E34, R2 (create), R9 (flag), R16, R17, R20, and P-c, P-d, P-e. Read them in [../NR-01-exam-segment.md](../NR-01-exam-segment.md).

Branch `nr01/a-authoring`. Worktree `F:\cc-wt\nr01-a`. Test database `cc_test_nr01_a`. The files you own are in [README.md](README.md#ownership).

## A1: Exam fields, window and no lock

- **Create and update** accept:
  - `mode`, `liveAt`, `endAt`, `durationMinutes`;
  - `negativeMarks`, `answersChangeable`, `previewEnabled`;
  - `price`, `free`, `discountPercent`, `discountEndsAt`, `accessDays`.

  Validate with `ExamWindow.validate` and `ExamPricing.validate`.
- **Rules**
  - A `PRACTICE` exam has no dates.
  - A `LIVE` exam has both dates or neither.
  - **Compatibility:** if a request sets `liveAt` without `endAt` on a `LIVE` exam, set `endAt = liveAt + duration`. The current web sends only `liveAt`.
  - Changing `mode` is allowed only while the exam is a draft that nobody has started.
- **`POST /live-exams`** creates a standalone exam (R2). `categoryId` is required, and the category must exist and not be archived (`LiveExamCatalogVisibility`).
- **Inside a package**, create sets the category from the package, adds the membership, and inherits the package default instructor (`live_exam_instructors`).
- **Remove the locks:**
  - every `CONTENT_LOCKED` raise for schedule, scoring, paper language and text;
  - the five-minute clash check (`rejectWindowConflict`);
  - publish's `EXAM_WINDOW_ENDED`. An ended exam can be published, because archived participants can still sit it.
- **`popular` is not accepted here**; B owns it.
- **Delete** (`LiveExamRemoval`): today's guards stay (`EXAM_HAS_PARTICIPANTS`, `EXAM_HAS_PURCHASES`). Deleting also removes the exam's active memberships (`removed_at`) and any pending revision.
- **Copy** (`POST /live-exams/{id}/copy`) copies the new fields and **every** active membership (F copied only the primary one), but no dates, price or `popular`.
- **Paper author:** pass the acting user into the paper-version create, so `live_exam_question_papers.created_by` is set (F left it null).
- **Before A1's own work**, replace the `primaryModuleId` reads in your files per [contracts.md §8](contracts.md#8-several-packages-per-exam-how-each-primary-package-read-resolves).
- **`ExamView`** gains every field in [contracts.md §6](contracts.md#exam-fields-examview-gains-these-a-assembles-them-values-come-from-the-helpers-and-from-d-and-c). For the student fields (`access`, `attempts`, `sitting`, `myStatus`, `retakeAvailable`), call the D and C helpers already used today (`LiveExamStudentStatus`, `LiveExamAccess.state`). Do not reimplement their rules.

**Tests:** update `LiveExamIT`, removing the lock assertions and adding the new ones. New `ExamWindowAuthoringIT`:
- window shorter than the duration is rejected;
- `liveAt` only (compatibility) fills `endAt`;
- practice with dates is rejected;
- editing the schedule, duration, pass percent and paper language of a published exam during and after the window is accepted (it is staged, see A4);
- publish after the window succeeds;
- two exams in the same package with overlapping windows both publish;
- standalone create, and create in an archived category is rejected.

## A2: Questions (multi-answer and the structure lock)

- Questions take `multipleAnswer`.
  - With `false`, exactly one correct option.
  - With `true`, at least one correct option, and at least two options in total.
- **Structure lock (R16).** Once any participant row exists for the exam (any status), these return `409 QUESTION_STRUCTURE_LOCKED`:
  - adding or deleting a question;
  - adding or deleting an option;
  - changing the option count in a PATCH.
- Changing the stem, option text, the correct flags, marks, explanation and `multipleAnswer` stays allowed. Changing `multipleAnswer` from true to false must still leave exactly one correct option.

**Tests:** new `ExamQuestionMultiAnswerIT`, covering the validation, the lock after one take, and allowed key changes after submissions (the re-score is asserted in A4).

## A3: Question paper versions

- Each `question-pdf` upload creates a new `live_exam_question_papers` row (version n+1).
  - On a **draft** exam, `live_exams.question_paper_id` and the legacy columns are updated at once.
  - On a **published** exam, the revision records the new paper id. The published pointer moves only when the revision is published (A4).
- Never delete a paper version that a participant references.

**Tests:** in `ExamRevisionIT` (A4): replace the paper on a published exam after one student sat it. Before publish, students still get v1. After publish, a new sitter gets v2, and the first participant still has v1. D's tests assert what the evaluator sees.

## A4: Staged edits, change list and publish

**Revision storage.**
- `live_exam_revisions.payload` holds the **full proposed state**: exam fields, the question list with options (ids kept; new ones get ids when they are added), and the proposed paper id.
- The first edit to a published exam creates the revision, by copying the published state and applying the edit. Later edits update it.
- Draft exams take no revision; edits apply directly, as today.
- **Editing a published exam never changes its status** and never hides it from students. This removes today's "edit returns to draft".

**Reads.**
- Staff `GET /live-exams/{id}` returns the working copy, with `pendingChanges: true`.
- Student and public reads, the sitting, and the scoring always read the published rows.

**`GET /live-exams/{id}/pending-changes`** returns the `ChangeList` built by `ExamChangeListBuilder`:
- `fields`: one entry per changed field, as `before`/`after` values. Titles, descriptions and rules appear in both languages.
- `questions`: added or removed questions (only possible before the first sitting), and per question the stem, option text, correct key, marks, explanation and `multipleAnswer` changes.
- `paper`: the version before and after.
- `impact.papersToRescore`: the count of submitted MCQ attempts, when any of these changed: the key, marks, `multipleAnswer`, `negativeMarks` or `passPercent`.
- `impact.notifiesStudents`: true when `liveAt`, `endAt`, `durationMinutes`, the paper or the questions changed (P-e). Include the count of students with access.

**`DELETE /live-exams/{id}/pending-changes`** drops the revision. Audit `LIVE_EXAM_REVISION_DISCARD`.

**`POST /live-exams/{id}/publish`** with `{revisionId}`. Master Admin only. A stale id gets `PENDING_CHANGES_STALE`; the compatibility rule in [contracts.md §7](contracts.md#7-compatibility-rules-the-current-website-keeps-working) item 4 applies. Take the exam row lock first (`LiveExamRepository.lockById`, the same lock take and submit use), so a sitting cannot start or post halfway through a publish. In one transaction:
1. Apply the payload to the exam, question and option rows, and move `question_paper_id` (mirroring the legacy columns).
2. Run the same validations as a first publish.
3. Call `McqRescorer.rescore(examId)` when scoring changed. The rule is R17: in-progress attempts are not scored now; they score at submit against the current key.
4. Write `live_exam_change_log` with the change list and the re-score count.
5. Write the audit entry `LIVE_EXAM_REVISION_PUBLISH`.
6. Delete the revision.
7. After commit, when `notifiesStudents`, call F's `StudentNoticeWriter.examUpdated(examId, studentIds)` for the students with access (grant and roster based; [contracts.md §7](contracts.md#7-compatibility-rules-the-current-website-keeps-working) item 10). Send **no mail**, and do not re-send the "exam published" notice or mail.

Publishing an exam that has never been published works as today: final-save, then publish, with the notice and mail.

**Tests:** new `ExamRevisionIT`:
- edits to a published exam leave the student view unchanged until publish;
- the change list is correct for each change type;
- discard works;
- stale `revisionId` is rejected;
- a System Admin cannot publish but can stage;
- publish re-scores (multi-answer key change), and both the scores and the leaderboard order change;
- the notice is sent only for time or paper changes, and no mail is queued;
- the audit and change-log rows are written;
- the compatibility publish without a `revisionId` works.

## A5: Pending badge

`GET /live-exam-pending-changes` lists every exam and package with a pending revision (reading both revision tables), newest first, with the counts. It is for Master Admin and System Admin.

**Tests:** in `ExamRevisionIT`, plus one package case. B's package revision must exist on `nr01/integration` for that case; mark it `@Disabled("needs B")` until then, and tell the orchestrator.

## A6: Preview

- **`GET /live-exams/{id}/preview`** and **`/preview/question-pdf`**: signed-in student (P-b), `previewEnabled`, published, phase `ENDED` or `PRACTICE`. Anything else gets `404`.
- MCQ returns the stems, options and `multipleAnswer`, never `correct` or an explanation. Written returns the published paper.
- It needs **no purchase**. That is the point: the preview is promotion.

**Tests:** new `ExamPreviewIT`, covering:
- closed before and during the window;
- open after the window;
- practice;
- no answer leak in the JSON (assert that `correct` and `explanation` are absent);
- disabled flag;
- a signed-out request is rejected.

## A7: Retire the answers-released switch (P-d)

`PUT /live-exams/{id}/answers-released` stays, stores the value, and gates nothing. D removes the reads. Note this in `web-impact/a.md`.

## For web-impact

Write to `web-impact/a.md`:
- the editor fields;
- the staged-changes banner, change-list dialog and discard;
- `revisionId` on publish, then removing compatibility rule 4;
- the pending badge;
- the preview page;
- a standalone exam create flow from a category.

## Not yours

- Sitting, scoring at submit, positions and the leaderboard (D).
- Access rules (C).
- Package endpoints and `popular` (B).
- The notice writer (orchestrator).
