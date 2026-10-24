# Step 2a — Written exams have no later attempt

API only. No Flyway version. Do this before step 2b and before purchase.

## Outcome

`POST /api/v1/live-exams/{id}/retake` rejects a written exam. An MCQ keeps one later attempt, and only when the student never tapped Take exam during the window. That later attempt stays off the live leaderboard.

## Bind these

- Read [decisions.md](../decisions.md) section "Exam rules that stay".
- A student who sat the live MCQ still cannot start the later attempt. A second MCQ attempt still returns `409` `RETAKE_NOT_ALLOWED`.
- A written exam returns `409` `RETAKE_NOT_ALLOWED` after the access check. A hidden exam, a missing grant, and an archived branch still return `404`, so the type check does not reveal them.
- The call creates no participant row for a written exam.
- The question PDF stays available only during a live written sitting. There is no written sitting after the window.
- MCQ scoring, the five-minute MCQ board, and the written publish gate stay as they are.

## Where the code is

- `LiveExamSittingService.retake` starts a `RETAKE` for any exam type once the window has ended and no participant row exists.
- `LiveSittingIT.theQuestionPdfDownloadsOnlyDuringTheSitting` expects a written retake to return the question PDF.
- `LiveEvaluationIT.aWrittenRetakeCanBeMarkedAndStaysOffTheLiveBoard` expects a written retake to be marked and published. The MCQ board test already proves a later MCQ attempt stays off the live board.

## Plan must name

- The order of the access check and the type check.
- The `*IT` cases that move from a successful written retake to `409`, and the MCQ cases that stay.

## Out of this step

Nullable `liveAt`, prices, SSLCommerz, Google, notifications, and screens.
