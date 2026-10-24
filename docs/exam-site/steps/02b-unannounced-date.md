# Step 2b — Unannounced exam date

API only. After step 2a. Flyway `V31`. Do not edit `V14`–`V30`.

## Outcome

A Master Admin or System Admin can publish an exam whose date and time are not announced. The module exam list includes that exam with `liveAt` and `endAt` null. Take exam stays closed until a date is set and the server clock is inside the window.

## Bind these

- Read [decisions.md](../decisions.md). The routine is this exam list. Do not add a routine table or a routine document.
- `durationMinutes` stays required.
- The five-minute gap inside one module stays. Do not add a routine table, and do not store a placeholder time.
- Publish of an exam with a null `liveAt` skips the ended-window check and the gap check. That exam is not a window.
- Publish of an exam that has a start runs both checks as they work today, against every other published exam in the module that has a start. An undated sibling is ignored. Two undated exams publish together. A date that lands inside another published window, or within five minutes of it, still returns `409` `EXAM_WINDOW_CONFLICT`.
- Setting or clearing `liveAt` returns the exam to draft. The new time is not published until final save and publish.
- `GET /api/v1/me/exam-routine` omits an exam with a null `liveAt`. The module exam list includes it.
- Take exam and the MCQ later attempt return `409` `SITTING_NOT_OPEN` while `liveAt` is null.
- The leaderboard stays closed while `liveAt` is null.
- A staff edit may set or clear `liveAt` only while no window has started. That edit returns the exam to draft. At or after `liveAt`, the field returns `409` `CONTENT_LOCKED`.
- A null `liveAt` is unlocked. `LiveExamService.update` must not call `isBefore` on a null start.

## Where the code is

- `V25` creates `live_at TIMESTAMPTZ NOT NULL`. `LiveExamService.create` requires `liveAt`.
- `LiveExamService.update` treats the exam as locked when the clock is no longer before `liveAt`, and it ignores a null `liveAt` on the patch.
- `LiveExamService.publish` calls `rejectWindowConflict`, which reads `liveAt` on this exam and on every other published exam in the module.
- `LiveExamService.end`, `LiveExamPublicService.end`, and `LiveExamSittingService.end` add the duration to `liveAt`.
- `LiveExamEvaluationService` opens the leaderboard five minutes after that end.

## Plan must name

- The migration.
- Create, patch, and publish with a null `liveAt`.
- What the public exam payload, the module exam list, the personal routine, Take exam, and the leaderboard return.
- The `*IT` cases.

## Out of this step

Prices, SSLCommerz, Google, notifications, status pills, and screens.
