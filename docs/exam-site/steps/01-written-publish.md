# Step 1 — Written publish gate

Plan this first. API only. No website.

## Outcome

An instructor save of a written paper is a draft. The student sees the marks, the comment, and the evaluated PDF only after the Master Admin publishes. The written leaderboard uses the same gate. MCQ scoring stays immediate.

## Bind these

- Read [decisions.md](../decisions.md) section "Written results" and [current-api.md](../current-api.md) section "Evaluation".
- A save by the Master Admin is still a draft. Publish is a second action, Master Admin only.
- Instructor and System Admin receive `403` on publish.
- A later save clears the published state until the next publish.
- The student payload still has no evaluator id or name.
- A failed-to-submit zero stays on the live board. It is not an instructor mark.
- MCQ submit, MCQ result, and the five-minute MCQ board stay as they are.
- The one retake stays off the live board, including after a written retake is published.

## Where the code is

- `LiveExamEvaluationService.evaluate` writes `score`, `passed`, `comment`, `evaluated_at`, and the PDF, then the student can read them.
- `LiveExamSittingService.result` returns `score`, `passed`, `comment`, and `evaluatedPdf` from the participant row.
- `LiveExamEvaluationService.leaderboard` includes a written live row when `evaluated_at` is set, and always includes `FAILED_TO_SUBMIT`.
- `GET /api/v1/live-exams/{id}/evaluated-pdf` returns bytes whenever the file is stored.
- Columns are on `live_exam_participants` from `V28`. Add the published state in `V29` or later. Do not edit `V28`.

## Plan must name

- The new column or status, and who can change it.
- The publish call, its permission, and the `403` cases.
- What `result`, `evaluated-pdf`, the submission list, and the leaderboard return before and after publish.
- What a second save does to a published row.
- The `*IT` class and the cases. Existing evaluation tests that expect the student to see the mark on save have to move to the publish call.

## Out of this step

Public catalog, prices, SSLCommerz, Google, notifications, and screens. The written-published notice is step 5. The publish button is step 7.
