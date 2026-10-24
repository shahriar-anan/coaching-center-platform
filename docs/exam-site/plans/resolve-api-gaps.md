# Resolve the three API gaps

Done. Steps 2a, 2b, and 3 are implemented. Flyway `V31` is the unannounced date. Flyway `V32` is purchase. Do not reopen these steps. Package `com.coachingcenter.api.exams`.

The limitations are marked in [../current-api.md](../current-api.md). The rules are in [../decisions.md](../decisions.md).

## Step 2a — Written exams have no later attempt

No migration. Binding spec: [../steps/02a-written-no-practice.md](../steps/02a-written-no-practice.md).

`LiveExamSittingService.retake` keeps the current access check first. A hidden exam, a missing roster row, and an archived branch still return `404`. When the exam is visible and its type is `WRITTEN`, the call returns `409` `RETAKE_NOT_ALLOWED` and inserts no participant. The MCQ branch is unchanged: one `RETAKE` after the window, only when no participant row exists, personal timer, off the live leaderboard.

### Tests

Run `mvn test "-Dtest=LiveSittingIT,LiveEvaluationIT"` on PostgreSQL. Do not use H2.

- `aStudentWhoNeverTappedCanRetakeOnceAndALiveParticipantCannot` stays an MCQ case and still passes.
- `theQuestionPdfDownloadsOnlyDuringTheSitting` keeps the live written sitting: the PDF is inline during that sitting and `404` after the deadline. The written retake in that test becomes `409` `RETAKE_NOT_ALLOWED`, and that student receives no question PDF.
- `aWrittenRetakeCanBeMarkedAndStaysOffTheLiveBoard` becomes a rejection: after the window, the student who never sat receives `409`, and no retake participant exists. The MCQ board test still shows a later MCQ attempt off the live board.
- An archived written exam still returns `404` on retake, as `LiveSittingIT` already asserts.
- A written retake during the window returns `409` `RETAKE_NOT_ALLOWED`. A student with no roster row receives `404`. No participant row is created, and the question PDF stays `404`.

## Step 2b — Unannounced exam date

Flyway `V31`. Binding spec: [../steps/02b-unannounced-date.md](../steps/02b-unannounced-date.md). Do not edit `V14`–`V30`.

### Slice 1 — Nullable start

`V31__live_exam_live_at_nullable.sql`:

```sql
ALTER TABLE live_exams ALTER COLUMN live_at DROP NOT NULL;
```

Existing rows keep their start. `duration_minutes` stays `NOT NULL`.

`POST /api/v1/live-exam-modules/{moduleId}/exams` accepts a missing or null `liveAt`. A present value is still parsed as an offset date-time. `durationMinutes` stays required.

`PATCH /api/v1/live-exams/{id}` accepts `liveAt: null` and clears the start while no window has started. A null start is unlocked: the patch does not call `isBefore` on it. Setting or clearing the start returns the exam to draft and clears the final-save flag, the same way a title edit does. At or after a non-null `liveAt`, a schedule edit returns `409` `CONTENT_LOCKED`. The new time is not live until final save and publish.

`publish` allows a null start. A null start skips `EXAM_WINDOW_ENDED` and `rejectWindowConflict`, because that exam has no window. A publish that has a start runs both checks unchanged, against every other published exam in the module that has a start. Undated siblings are ignored. Two undated exams do not conflict. A date that overlaps another published window, or sits inside the five-minute gap, still returns `409` `EXAM_WINDOW_CONFLICT`. There is no placeholder timestamp and no routine table. Another module may still use the same clock time.

`endAt` is null when `liveAt` is null. `LiveExamService`, `LiveExamPublicService`, `LiveExamSittingService`, and the leaderboard open time in `LiveExamEvaluationService` all use that rule.

### Slice 2 — Reads and sitting

The public exam payload and the staff exam payload omit `liveAt` and `endAt` when the start is null. This API already omits null fields. The exam stays on `GET /api/v1/public/live-exam-modules/{id}/exams` and on the roster-gated module exam list. List order stays `liveAt` ascending, with nulls last.

`GET /api/v1/me/exam-routine` omits an exam whose `liveAt` is null.

`POST .../take` and `POST .../retake` return `409` `SITTING_NOT_OPEN` while `liveAt` is null. The leaderboard stays empty for that exam, because it has no window end to open from.

### Tests

Run `mvn test "-Dtest=LiveExamIT,LivePublicCatalogIT,LiveSittingIT,LiveEvaluationIT"`.

- Create with no `liveAt`, publish, and the public exam read returns null `liveAt` and null `endAt`. The module exam list contains the exam.
- Take exam on that paper returns `409` `SITTING_NOT_OPEN`.
- A patch that sets `liveAt` returns the exam to draft. After publish, Take exam succeeds inside the window.
- A patch at `liveAt` returns `409` `CONTENT_LOCKED` and leaves the date.
- A patch that clears `liveAt` before the window returns the exam to draft. The personal routine omits it. A second undated exam in the same module publishes without `EXAM_WINDOW_CONFLICT`.
- The leaderboard for an undated exam is empty.

## Step 3 — Purchase

Implemented. Binding spec: [../steps/03-purchase-sslcommerz.md](../steps/03-purchase-sslcommerz.md). The slice plan is [03-purchase-sslcommerz.md](03-purchase-sslcommerz.md). It owns Flyway `V32`. Automated tests use a fake gateway. The public HTTPS callback remains the external blocker in the step file.

The implementation adds the price columns, the exam order, the SSLCommerz initiate and verified callback, and the purchase grant beside the roster. An undated exam can be bought and stays on the module list. Take exam stays closed until `liveAt` is set. Sandbox and live profiles are named and the live store is not selected.

## Out of this plan

Google sign-in, notices, status pills, and screens. Question paper delivery, PDF-only written answers, the leaderboard, and the category tree stay as they are.
