# A1: exam fields, window and no lock (agent A)

## What changed

**Create and update (`LiveExamService`)**
- `create` and `update` share one `applyEdit`. They accept `mode`, `liveAt`, `endAt`, `durationMinutes`, `negativeMarks`, `answersChangeable`, `previewEnabled`, `price`, `free`, `discountPercent`, `discountEndsAt`, `accessDays`. The window is checked by `ExamWindow.validate` and the price by `ExamPricing.validate` (field errors come back as `fieldErrors`).
- Compatibility: a request with `liveAt` and no `endAt` gets `endAt = liveAt + duration`. A duration change on a window that was exactly one duration long moves the end with it. A wider window keeps its end.
- `mode` changes only on a draft that nobody has started (`participants.existsByExamId`). A practice exam has no dates.
- Commercial fields need `EXAM_PRICE` (the controller's `requirePricePermission` now covers all of them).
- `popular` is not read here (B).

**Standalone create**
- `POST /api/v1/live-exams` (`LiveExamController.createStandalone`) takes `categoryId`. The category must exist and not be archived (`LiveExamCatalogVisibility.requireOpen`, `CATEGORY_ARCHIVED`).
- Create inside a package takes the package's category, joins the membership, and inherits the package's default instructor.

**Locks removed**
- Every `CONTENT_LOCKED` raise is gone (the helper `lockedContent()` is deleted). The enum value stays.
- `rejectWindowConflict` and the five-minute gap are gone; `EXAM_WINDOW_CONFLICT` and `EXAM_WINDOW_ENDED` are no longer raised.
- Publish no longer refuses an ended window. Publish now checks `visibility.requireOpen(categoryId)` instead of the package, and the mail queue is only triggered for an exam that has a package (the queue reads recipients through a package; contracts section 8 leaves that to the mail owner).
- An edit to a published exam leaves its status `PUBLISHED` (E27). Before A1 it went back to `DRAFT` before `liveAt`, and was refused after it. In A1 the edit still writes the row directly; staging is A4.

**Delete (`LiveExamRemoval`)**
- Still refused by `EXAM_HAS_PARTICIPANTS` and `EXAM_HAS_PURCHASES`. On delete it sets `removed_at` on every active membership and drops any pending revision (`LiveExamRevisionRepository`).

**Copy (`POST /live-exams/{id}/copy`)**
- Copies the new fields (`previewEnabled`, `free`, `accessDays`, `negativeMarks`, `answersChangeable`, `mode`, category, instructor), and every active membership (not only the primary one). Dates, price and `popular` stay empty.
- Copies the source's published paper as a new version 1 whose `created_by` is the acting user.

**Paper author**
- `uploadQuestionPdf` and `copy` pass the acting user into `paperVersion`, so `live_exam_question_papers.created_by` is set.

**`ExamView`**
- Gains `mode`, `phase`, `categoryId`, `packages`, `instructor` (staff), `negativeMarks`, `answersChangeable`, `previewEnabled`, `popular`, `accessDays`, `pricing`, `pendingChanges` (staff), `questions[].multipleAnswer`, and for students `access`, `attempts`, `myStatusDetail`.
- `moduleId` is the primary package (display only), null for a standalone exam. `endAt` is the stored `ends_at`.
- `myStatusDetail` currently repeats `myStatus`. The detail values (`ARCHIVED_OPEN`, `PRACTICE_OPEN`, `EXPIRED`) come from D's `LiveExamStudentStatus`; until D lands they are not produced.

**Visibility**
- Students see an exam by its home category branch (`visibleToStudents`), not by a package (contracts section 8). B owns the public list.

**Repository**
- `LiveExamRepository.findActiveStudentsWithAccessToExam(examId)`: the union over every active membership (roster rows and package grants) plus exam grants, revoked grants left out, deduplicated. Used by publish. A free exam adds nobody (section 7 item 10).

## Tests

Command (through the script, lock taken, database `cc_test_nr01_a`):

    powershell -NoProfile -File docs\exam-site\new-requirements\nr-01\tools\nr01-mvn.ps1 -Agent A -Tests "ExamWindowAuthoringIT,LiveExamIT,LiveEvaluationIT,AuditLogIT,LiveExamArchiveIT"

Result (exit 0, surefire reports from this run):

| Class | Tests | Failures | Errors |
|---|---|---|---|
| `ExamWindowAuthoringIT` (new) | 12 | 0 | 0 |
| `LiveExamIT` | 11 | 0 | 0 |
| `LiveEvaluationIT` | 63 | 0 | 0 |
| `LiveExamArchiveIT` | 2 | 0 | 0 |
| `AuditLogIT` | 3 | 0 | 0 |

## Changes to existing tests (decided behaviour, not weakened)

- `LiveExamIT`: the lock assertions are replaced by the new behaviour. An ended window can be published. A delete question inside the window now succeeds. The status of a published exam stays `PUBLISHED` after an edit. A window that overlaps another one in the same package publishes. A schedule, duration, pass percent or paper-language edit on a published exam is accepted during the window. The CONTENT_LOCKED assertions are gone with the locks (E27, E30, D17).
- `LiveEvaluationIT` (owned by D by the file list, changed by A because the clash rule is A's: contracts section 5 and README row "Window clash"): `aClashingPublishNamesTheExamItClashesWith` becomes `overlappingWindowsInOnePackageBothPublish`. The clash is decided away (D17). Nothing else in that class changed.
- `AuditLogIT`: the `API_OPERATIONS` A block lists `POST /api/v1/live-exams`.

## Deviations

- `myStatusDetail` is not yet the detail value (see above). D changes this.
- The status of an edited published exam stays `PUBLISHED` already in A1 (the decided behaviour; A4 then removes the row write for published exams entirely).
- Test fixture `AuthoringFixture` is shared by the A tests; it is test code only.

## Not done in A1

- Staged edits (A4), the question structure lock (A2), the paper revision (A3), the pending badge and preview (A5, A6).
