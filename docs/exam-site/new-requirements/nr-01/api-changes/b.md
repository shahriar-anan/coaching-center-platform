# NR-01 API changes: agent B (packages and catalog)

Table format of `current-api.md`. The orchestrator folds these in during integration.

## New routes

| Method | Route | Who | Notes |
|---|---|---|---|
| POST | `/api/v1/live-exam-modules/{id}/exams/{examId}` | Master Admin, System Admin (LIVE_EXAM_PACKAGE_EDIT) | Adds an existing exam at the end of the package. Staged on a published package. Returns `{moduleId, examIds, pendingChanges}`. `EXAM_ALREADY_IN_PACKAGE`, `EXAM_ARCHIVED`, `MODULE_ARCHIVED`. |
| DELETE | `/api/v1/live-exam-modules/{id}/exams/{examId}` | Master Admin, System Admin | Removes the exam (`removed_at`). Staged on a published package. 204. Holders keep the exam (R4, `LiveExamAccess`). |
| PATCH | `/api/v1/live-exam-modules/{id}/exams/order` | Master Admin, System Admin | Body `{examIds:[…]}`, exactly the package's exams, each once. Staged on a published package. `VALIDATION_FAILED` otherwise. |
| GET | `/api/v1/live-exam-modules/{id}/pending-changes` | Master Admin, System Admin | `ChangeList` (fields, packageExams, impact). 404 when nothing is staged. |
| DELETE | `/api/v1/live-exam-modules/{id}/pending-changes` | Master Admin, System Admin | Discards the staged revision. 204. `NO_PENDING_CHANGES` (409) when none. |
| PUT | `/api/v1/live-exams/{id}/popular` | Master Admin (LIVE_EXAM_POPULAR) | Body `{popular}`. Applied at once. Audit `LIVE_EXAM_POPULAR_SET`. |
| PUT | `/api/v1/live-exam-modules/{id}/popular` | Master Admin | As above, for a package. |
| GET | `/api/v1/live-exams` | Master Admin, System Admin (all); Instructor (own exams only) | Staff list. Params: `packageId`, `categoryId`, `standalone`, `type`, `mode`, `status`, `phase`, `q`, `pending`, `popular`, `page`, `size`, `sort` (`liveAt`, `title`, `updatedAt`). Rows: `id`, both titles, `examType`, `mode`, `phase`, `status`, `liveAt`, `endAt`, `durationMinutes`, `pricing`, `popular`, `pendingChanges`, `packages[]`, `categoryId`, `questionCount`, `hasPaper`, `instructor`. |
| GET | `/api/v1/public/live-exam-categories/{id}/exams` | Public | Published exams whose home category is `{id}`. Params: `examType`, `mode`, `phase`, `popular`, paging. Empty for a hidden branch; 404 for an unknown category. |
| GET | `/api/v1/me/favourites` | Student (LIVE_EXAM_FAVOURITE) | `{exams:[…], packages:[…]}` in the public row shape, with `access`. Hidden items are skipped. |
| PUT | `/api/v1/me/favourites/exams/{id}` | Student | Idempotent. 204. 404 unless published and in a visible branch. |
| DELETE | `/api/v1/me/favourites/exams/{id}` | Student | Idempotent. 204. |
| PUT | `/api/v1/me/favourites/packages/{id}` | Student | As for exams, for a package. |
| DELETE | `/api/v1/me/favourites/packages/{id}` | Student | Idempotent. 204. |

## Changed routes

| Method | Route | Change |
|---|---|---|
| PATCH | `/api/v1/live-exam-modules/{id}` | On a **published** package the edit is staged in a revision. The status stays `PUBLISHED`, `finallySaved` is not reset, and the response is the working copy with `pendingChanges: true`. A draft is edited as before. Accepts `discountPercent` and `discountEndsAt` (validated with `ExamPricing`). |
| POST | `/api/v1/live-exam-modules/{id}/publish` | Body `{revisionId?}` (optional). On a published package with a revision: Master Admin only (`LIVE_EXAM_PUBLISH`); `PENDING_CHANGES_STALE` when `revisionId` is not the current one; a missing `revisionId` is accepted from the Master Admin only and logged at warn. `NO_PENDING_CHANGES` (409) on a published package with nothing staged. The first publish of a draft is unchanged (EXAM_MANAGE). |
| GET | `/api/v1/live-exam-modules` | Accepts `popular=true` (student list). |
| GET | `/api/v1/live-exam-modules/{id}` and list | Staff reads show the working copy and `pendingChanges`. New fields on every module row: `discountPercent`, `discountEndsAt`, `popular`, `pricing` (`PriceView`), `pendingChanges`. |
| DELETE | `/api/v1/live-exam-modules/{id}` | Also ends the package's memberships (`removed_at`). Exams stay in their categories. |
| GET | `/api/v1/live-exam-categories` (and `/{id}` tree) | Each tree node gains `exams: [{id, categoryId, titleEn, titleBn, examType, mode, status, popular, packages[]}]`: every exam whose home is that category, listed once. |
| GET | `/api/v1/public/live-exam-categories` | Each node gains `exams: [CategoryExam]` (published, visible home branch). An exam-type filter keeps a category when it holds an exam of that type. |
| GET | `/api/v1/public/live-exam-modules` | Accepts `popular=true`. Rows gain `popular`, `pricing`, `favourite` (students only). Membership-based exam-type filter. |
| GET | `/api/v1/public/live-exam-modules/{id}` | Gains `popular`, `pricing`, `favourite` (students only). |
| GET | `/api/v1/public/live-exam-modules/{id}/exams` | Membership position order. Only exams whose home branch is visible. Accepts `popular=true`. Rows gain `categoryId`, `mode`, `phase`, `popular`, `pricing`, `packages[]`, `favourite` (students only). |
| GET | `/api/v1/public/live-exams/{id}` | No longer 404 for a standalone exam or for an exam whose package is unpublished: it depends only on its home branch (published exam, visible category). New fields as above. |
| GET | `/api/v1/public/live-exams?upcoming=true` | Includes standalone exams (joined through the category). Not-yet-ended exams only (`endsAt > now`). `moduleId` is the primary package, for display. Rows gain `categoryId`, `mode`, `phase`, `popular`, `pricing`, `packages[]`, `favourite` (students only). `moduleOnly` follows the rule in `current-api` section 7 of contracts. |

## Compatibility

- No field removed or renamed. `price` stays at the top level of every row.
- `favourite` is absent for a visitor (`null`) and `true`/`false` for a student.
- `ModuleView.pendingChanges` is `false` on every student and public read.

## Audit actions used

`LIVE_EXAM_PACKAGE_EXAM_ADD`, `LIVE_EXAM_PACKAGE_EXAM_REMOVE`, `LIVE_EXAM_MODULE_REVISION_PUBLISH`, `LIVE_EXAM_MODULE_REVISION_DISCARD`, `LIVE_EXAM_POPULAR_SET`. The `LIVE_EXAM_MODULE` entity is used for package actions; the change log is `live_exam_change_log`.
