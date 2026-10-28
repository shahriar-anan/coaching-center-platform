# NR-01 API changes: agent A (exam authoring)

Table format of [../../../current-api.md](../../../current-api.md). The orchestrator folds these in during integration. Each slice adds its rows.

## A1: exam fields, window, no lock

| Call | Change |
|---|---|
| `POST /api/v1/live-exams` | **New.** Standalone exam. Body: `categoryId` plus the create fields. The category must exist and not be archived (`CATEGORY_ARCHIVED`, 409). Master Admin and System Admin. |
| `POST /api/v1/live-exam-modules/{id}/exams` | Changed. Sets `categoryId` from the package, joins the membership, inherits the package instructor. Accepts `mode`, `endAt`, `negativeMarks`, `answersChangeable`, `previewEnabled`, `free`, `discountPercent`, `discountEndsAt`, `accessDays`. Commercial fields need `EXAM_PRICE`. |
| `PATCH /api/v1/live-exams/{id}` | Changed. The same new fields. No schedule or content lock. A `mode` change only on a draft nobody has started (`VALIDATION_FAILED`, `fieldErrors[0].field = "mode"`). A window shorter than the duration is `EXAM_WINDOW_INVALID` (400). A published exam keeps `PUBLISHED` after an edit. Edits still write the row directly until A4 stages them. |
| `POST /api/v1/live-exams/{id}/publish` | Changed. An ended window publishes (`EXAM_WINDOW_ENDED` is no longer raised). Overlapping windows in one package publish (`EXAM_WINDOW_CONFLICT` is no longer raised). Recipients are the union over every active membership, plus exam grants. |
| `POST /api/v1/live-exams/{id}/copy` | Changed. Copies the new fields and every active membership. No dates, price or `popular`. The copy's paper version records the acting user. |
| `POST /api/v1/live-exams/{id}/question-pdf` | Changed. The paper version records the acting user. |
| `DELETE /api/v1/live-exams/{id}` | Changed. Also removes the exam's active memberships and drops any pending revision. |
| `GET /api/v1/live-exams/{id}`, lists | Changed. `ExamView` gains `mode`, `phase`, `categoryId`, `packages`, `instructor` (staff), `negativeMarks`, `answersChangeable`, `previewEnabled`, `popular`, `accessDays`, `pricing`, `pendingChanges` (staff), and for students `access`, `attempts`, `myStatusDetail`. Questions carry `multipleAnswer`. `moduleId` is the primary package and may be null. |

## A2: questions

| Call | Change |
|---|---|
| `POST /api/v1/live-exams/{id}/questions` | Changed. Accepts `multipleAnswer`. Key rules by `multipleAnswer`. `409 QUESTION_STRUCTURE_LOCKED` once any participant row exists. |
| `PATCH /api/v1/live-exams/{id}/questions/{questionId}` | Changed. `multipleAnswer`. `409 QUESTION_STRUCTURE_LOCKED` when the option count changes after a start. Option ids kept when the count is unchanged. |
| `DELETE /api/v1/live-exams/{id}/questions/{questionId}` | Changed. `409 QUESTION_STRUCTURE_LOCKED` once any participant row exists. |

## A3 and A4: staged edits and paper versions

| Call | Change |
|---|---|
| `GET /api/v1/live-exams/{id}`, staff | Changed. Returns the working copy (the staged revision applied to the rows), with `pendingChanges: true` when a revision waits. Students read the published rows only. |
| `PATCH /api/v1/live-exams/{id}` | Changed. On a published exam the edit is staged; the status stays `PUBLISHED`. |
| `POST /api/v1/live-exams/{id}/questions`, `PATCH` and `DELETE` on questions | Changed. On a published exam the change is staged. A count change after a start is `409 QUESTION_STRUCTURE_LOCKED`. |
| `POST /api/v1/live-exams/{id}/question-pdf` | Changed. On a published exam the new version is staged; the published paper does not move until publish. |
| `GET /api/v1/live-exams/{id}/pending-changes` | **New.** The change list: `revisionId`, `fields`, `questions`, `paper`, `packageExams`, `impact`. `404` when nothing is staged. Master Admin and System Admin. |
| `DELETE /api/v1/live-exams/{id}/pending-changes` | **New.** Discards the revision (`204`). Audit `LIVE_EXAM_REVISION_DISCARD`. |
| `POST /api/v1/live-exams/{id}/publish` | Changed. Optional body `{revisionId}`. A stale id gives `409 PENDING_CHANGES_STALE`. Without a body the compatibility path publishes the current revision (logged at warn). |
| `POST /api/v1/live-exams/{id}/copy` | Changed. Copies the working copy, all memberships, no dates, price, discount or `popular`. |

## A5: changes-waiting badge

| Call | Change |
|---|---|
| `GET /api/v1/live-exam-pending-changes` | **New.** `{exams, packages, items:[{kind: EXAM|PACKAGE, id, titleEn, titleBn, updatedAt, updatedBy}]}`, newest first. Master Admin and System Admin. Students get `403`. |

## A6: preview

| Call | Change |
|---|---|
| `GET /api/v1/live-exams/{id}/preview` | **New.** Signed-in student. Published exam with `previewEnabled`, in phase `ENDED` or `PRACTICE`, in an open category branch. MCQ: stems, options and `multipleAnswer`, no `correct` and no `explanation`. Written: `{hasPaper}`. Otherwise `404`. No purchase or grant needed. |
| `GET /api/v1/live-exams/{id}/preview/question-pdf` | **New.** The published paper under the same rule. Inline, `no-store`. |

## A7: answers-released switch

`PUT /api/v1/live-exams/{id}/answers-released` is unchanged: it stores the value and gates nothing (D removes the reads).
