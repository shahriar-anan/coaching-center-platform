# A6: preview (agent A)

## What changed

- `GET /api/v1/live-exams/{id}/preview` (`LiveExamPreviewService.preview`) and `GET /api/v1/live-exams/{id}/preview/question-pdf` (`paperPdf`).
- Open only when the exam is published, `previewEnabled`, in phase `ENDED` or `PRACTICE`, and its category branch is visible. Anything else is `404`. A signed-out request is `401`.
- MCQ returns stems, options and `multipleAnswer`. The JSON carries no `correct` and no `explanation`. A written exam returns `hasPaper` and serves the published paper (inline, `no-store`, framed only by `frame-ancestors 'self'` plus the configured origins).
- No purchase and no access grant are needed (C10', P-b): the preview is promotion.

## Tests

1. Invocation: `ExamPreviewIT,ExamWindowAuthoringIT,AuditLogIT`.
   - `ExamWindowAuthoringIT` 13/0 (includes the new discount and free gate test, `discountsAndFreeExamsFollowThePricePermissionOnCreateAndInAStagedEdit`).
   - `AuditLogIT` 3/0 (two preview routes listed).
   - `ExamPreviewIT` 4/5: the failing case created a priced exam as a System Admin (a test bug, the correct answer is 403). Fixed by creating that exam as the Master Admin.
2. Rerun of `ExamPreviewIT` alone: **5/0**.

## Tests added

`ExamPreviewIT` (new): closed before and during the window, open after it; no purchase needed and no `correct` or `explanation` in the JSON; a practice exam at any time; a disabled preview and a draft are `404`; a signed-out request is `401`; a written preview returns `hasPaper` and the PDF.

## Deviations

- The discount gate test is in `ExamWindowAuthoringIT` (per the orchestrator's addition). The gate itself was already in the A1 controller's `requirePricePermission`, which covers `price`, `free`, `discountPercent`, `discountEndsAt` and `accessDays`.
