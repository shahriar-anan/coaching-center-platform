# Current live exam API

Read this instead of re-auditing. Phase 3 is complete against the roster model. The code below is what a plan extends. New tables start at Flyway `V29`. Do not edit `V14`–`V28`.

## Schema

| Table | Migration | Role |
|---|---|---|
| `live_exam_categories` | `V24` | Unlimited tree. `title_en`, `title_bn`. Archive hides the branch. |
| `live_exam_modules` | `V24`, `V32` | One category. Both titles and descriptions. `access_mode` is constrained to `MANUAL`. Status `DRAFT`, `PUBLISHED`, `ARCHIVED`. Nullable `price`. |
| `live_exams` | `V25`, `V30`, `V31`, `V32` | `MCQ` or `WRITTEN`. Both titles, descriptions, and rules. `live_at` may be null until the date is announced. `duration_minutes`, `pass_percent`. Written maximum and one question PDF. `paper_language` is `BN` or `EN`. Nullable `price`. |
| `live_exam_questions`, `live_exam_options` | `V25` | One correct option per question. |
| `live_exam_roster` | `V26` | `(module_id, student_id)`. Master Admin only. |
| `live_exam_orders`, `live_exam_grants` | `V32` | SSLCommerz checkout. A grant is `MODULE` or `EXAM`. Course and book rows on `payments` are out of this delivery. |
| `live_exam_participants`, `live_exam_answers` | `V27` | One row per exam and student (`LIVE` or `RETAKE`). Written answer file columns. |
| `live_exam_instructors` | `V28` | One instructor per module. |
| Participant evaluation columns | `V28` | `comment`, `evaluated_by`, `evaluated_at`, evaluated PDF. No draft or published state. |

Package: `com.coachingcenter.api.exams`.

## Who can do what

Permissions are granted in `ExamPermissionContributor` and `LiveExamPermissionContributor`.

- Master Admin: manage, delete, roster, assign instructor, evaluate, and set a live exam price. `EXAM_PRICE` is Master Admin only. There is no staff call that marks a live exam order paid. `PAYMENT_VERIFY` belongs to course and book payments, which are out of this delivery.
- System Admin: manage and read. Cannot delete, roster, assign, evaluate, or set a price. Can read the evaluator name.
- Instructor: `LIVE_EXAM_EVALUATE` on assigned modules only.
- Student: `LIVE_EXAM_BROWSE`. Module and exam lists require a published module whose category branch is not archived, and a roster row or a purchase grant. An exam grant covers that exam only. A module grant covers exams published later in that module.

## Student sitting

`LiveExamSittingController` and `LiveExamSittingService`.

| Call | Behavior to preserve |
|---|---|
| `GET /api/v1/me/exam-routine` | Published exams on granted modules whose window has not ended. Does not create a participant. |
| `POST /api/v1/live-exams/{id}/take` | Creates `LIVE` only inside the shared window. |
| `POST /api/v1/live-exams/{id}/retake` | One `RETAKE` after the window, only when no participant row exists. |
| `PUT /api/v1/live-exams/{id}/answers` | MCQ only. Replaces the full selection. |
| `POST /api/v1/live-exams/{id}/submit` | MCQ score can return early. The sitting stays open until the deadline. |
| `GET /api/v1/live-exams/{id}/result` | Own participant. MCQ review after the deadline. |
| `GET /api/v1/live-exams/{id}/question-pdf` | Inline, `no-store`, framed by CSP, only during that sitting. |
| `POST /api/v1/live-exams/{id}/answer-pdf` | Written PDF only. |

Files go through `LocalFileStorage`: UUID name, local directory, no public URL. `PdfCompressor` stores the compressed PDF. Ceiling is 80 MB in `LiveExamSittingService.MAX_PDF_BYTES`.

## Evaluation

`LiveExamEvaluationController` and `LiveExamEvaluationService`.

| Call | Today |
|---|---|
| `POST /api/v1/live-exam-modules/{moduleId}/instructor` | Master Admin assigns one instructor. |
| `GET /api/v1/live-exam-modules/{moduleId}/submissions` | Assigned instructor, or a staff reader. |
| `GET /api/v1/live-exam-participants/{id}/answer-pdf` | Marker download. |
| `POST /api/v1/live-exam-participants/{id}/evaluation` | Marks, optional comment, evaluated PDF. The student can see them on this save. |
| `GET /api/v1/live-exams/{id}/leaderboard` | Empty until five minutes after the window. Live rows only. Written rows wait until `evaluated_at` is set, except a failed-to-submit zero. |
| `GET /api/v1/live-exams/{id}/evaluated-pdf` | The student, any time the file is stored. |

## Catalog reads that are staff-only or roster-gated

- `GET /api/v1/live-exam-categories` requires `EXAM_READ`.
- `GET /api/v1/live-exam-modules` for a student calls `LiveExamModuleService.list` with the roster or purchase-grant filter.
- `GET /api/v1/live-exam-modules/{moduleId}/exams` for a student returns the published exams that roster, a module grant, or an exam grant allows.
- `GET /api/v1/live-exams/{id}` for a student is `studentExam`, which adds MCQ questions only after Take exam and only while the sitting is open or in review.

## Auth and payments that already exist

- `POST /api/v1/auth/register` takes name, phone, email, and password. Login does not wait for email verification. There is no Google path.
- `POST /api/v1/auth/forgot-password` sends mail when SMTP is configured.
- Course and book checkout on `payments` (`BKASH` / `NAGAD`, `COURSE_ENROLLMENT` / `BOOK_ORDER`) is out of this delivery. Leave that table and those screens alone. `payable_type` also includes `EXAM_MODULE`, which points at the superseded `exam_module_purchases` flow (`ExamModulePurchaseController`), not at `live_exam_modules`. Live exam checkout is `live_exam_orders`. Its method is `SSLCOMMERZ`. `exam_module_purchases` does not grant a live exam.
- There is no notification table.

## Web

`web/src/app` is the admin catalog: courses, books, payments, students, admins, login, activate. Locale switch and Noto Sans Bengali are already loaded. There is no live-exam route. A `STUDENT` session does not enter the admin shell. Course and book payment screens are out of this delivery.

## Tests

Live exam coverage is the `*IT` classes named from `docs/phases/phase-3/phase-3-status.md`. They assert the current written-result and roster behavior. A step that changes that behavior updates those assertions in the same change. Do not treat a failing old assertion as a reason to keep the outdated rule.

## Added 25 October 2026 (plans/api-completion)

| Call | Who | Notes |
|---|---|---|
| `GET /api/v1/instructors?q=&status=&assigned=` | Master Admin | Directory with each instructor's modules. |
| `DELETE /api/v1/live-exam-modules/{moduleId}/instructor` | Master Admin | Removes the one instructor. Drafts stay. |
| `DELETE /api/v1/live-exams/{id}` | Master Admin | `409 EXAM_HAS_PARTICIPANTS` or `EXAM_HAS_PURCHASES`. |
| `DELETE /api/v1/live-exams/{id}/questions/{questionId}` | Master Admin, System Admin | Same lock as edit. Returns the exam to draft. |
| `GET /api/v1/live-exam-participation` and `/summary` | Master Admin, System Admin, assigned instructor | Per-exam counts and the menu badge numbers. |
| `myStatus`, `retakeAvailable` on the student list, detail, and routine | Student | Nine statuses derived from the window and the student's own sitting. |
| `GET /api/v1/me/notices`, `/unread-count`, `POST /{id}/read`, `POST /read-all` | Student | Reminder, MCQ score, written result. Flyway `V36`. |

| `GET /api/v1/live-exam-orders?status=&tranId=&q=` | Master Admin (`LIVE_EXAM_ORDER_READ`) | Staff list of exam payments, newest first. Each row has the student's contact, the exam or module, `paymentChannel` from SSLCommerz (for example `BKASH-BKash`), `valId`, and `accessGranted`. Flyway `V37`. Plan: [plans/api-completion/a7-staff-payments.md](plans/api-completion/a7-staff-payments.md). |

Jobs: a one-minute deadline job posts expired sittings (index `V35`), at most 500 per run. A fifteen-minute job writes the inbox reminder for exams within 48 hours.

Mail on a successful payment: the student gets the receipt, and the Master Admin gets one "New purchase" email with the student's name, phone and email, the exam or module, the amount, the payment channel, the `tranId` and the time (Flyway `V38`, [plans/api-completion/a8-purchase-notice.md](plans/api-completion/a8-purchase-notice.md)). Failed and cancelled orders send neither.

Own phone change ([plans/master-admin-handover.md](plans/master-admin-handover.md)): `POST /api/v1/auth/change-phone` with `currentPassword` and `phone`, for every signed-in account that has a password, the Master Admin included. It returns `MeView`. A wrong or missing password gives `400 CURRENT_PASSWORD_INVALID`, a number in use `409 DUPLICATE_PHONE`, and a bad format `400 VALIDATION_FAILED`. The same number changes nothing. A change writes a `PHONE_CHANGED` audit row and mails a notice to the account email. Refresh tokens stay valid. No Flyway version. `POST /api/v1/me/phone` still only fills an empty phone after Google sign-up.

Removed 25 October 2026: `POST /api/v1/admin/mail-test` and `MAIL_TEST_ENABLED`. A real mail check now uses a normal register and forgot-password.

## Limitations

Shahriar confirmed these on 2026-10-23. The resolution order is [plans/resolve-api-gaps.md](plans/resolve-api-gaps.md).

| Limitation | API today | Resolved by |
|---|---|---|
| Written practice | Resolved in step 2a. `POST /api/v1/live-exams/{id}/retake` returns `409` `RETAKE_NOT_ALLOWED` for a written exam after the access check. The one MCQ later attempt remains. | Step 2a |
| Unannounced date | Resolved in step 2b. Flyway `V31` makes `live_at` nullable. A published exam with no start stays on the module list and is omitted from the personal routine. Take exam stays closed. A dated publish still enforces the five-minute gap against other dated exams in the module. | Step 2b |
| Purchase | Resolved in step 3. Flyway `V32` adds prices, `live_exam_orders`, and `live_exam_grants`. A verified notice grants access once. Take exam, the question PDF, and the routine accept a roster row or a purchase grant. Automated tests use a fake gateway. | Step 3 |

## Added for the UI delivery

Plan: [plans/ui-delivery/ws1-api.md](plans/ui-delivery/ws1-api.md). Flyway: none (no new migration).

| Call | Who | Notes |
|---|---|---|
| `GET /api/v1/live-exam-modules/{moduleId}/roster?q=&page=&size=` | Master Admin | The roster in name order. `q` matches name, phone, email or code. Each row has `addedAt` and `startedAny`, true when the student has a sitting in the module. |
| `GET /api/v1/students/{id}/live-exam-access` | Master Admin, System Admin | One row per roster membership and per purchase grant, ordered by module title. `via` is `ROSTER`, `MODULE_PURCHASE` or `EXAM_PURCHASE`. An exam purchase row names its one exam in `exams`. A module purchase has `exams` empty. Unknown student `404`. |
| `GET /api/v1/me/live-exam-orders?page=&size=` | Student | Own orders, newest first. Status uses the public words (`PENDING`, `PAID`, `FAILED`, `CANCELLED`). No `valId`. |
| `GET /api/v1/live-exam-orders` | Master Admin, System Admin | System Admin added (read only). |
| `examType=MCQ|WRITTEN` on the two catalog doors | Public, student | Filters `categories`, `live-exam-modules` and `live-exam-modules/{id}/exams`. Public module rows gain `mcqCount` and `writtenCount`. An unknown value is `400 VALIDATION_FAILED`. |
| `GET /api/v1/live-exams/{id}` (student, open sitting) | Student | `selected` is now true on each option saved for the open sitting. `correct` and `explanation` stay hidden until the review rule allows them. |
| `GET /api/v1/live-exams/{id}` gains `sitting` | Student | `{ kind: LIVE \| RETAKE, startedAt, deadlineAt, submittedAt }` from the requester's own participant row, or `null` when there is none. Never another student's row. Staff reads are unchanged. |
| `GET /api/v1/live-exams/{id}/leaderboard` rows gain `mine` | Student, staff | `true` only on the requesting student's own row. Always `false` for staff. No ids or phones are added. |
| `GET /api/v1/live-exams/{id}/result` gains `kind` | Student | `"LIVE"` for the live sitting, `"RETAKE"` for the later attempt. |

WS9 API stage (plan: [plans/ui-delivery/ws9-gaps-and-polish.md](plans/ui-delivery/ws9-gaps-and-polish.md)). Each row below is new or changed; nothing else moved.

| Call | Who | Notes |
|---|---|---|
| `GET /api/v1/live-exams/{id}/question-pdf` | Master Admin, System Admin, assigned Instructor | Staff read of the written question PDF (G8). An unassigned instructor gets `404`. The student rule is unchanged: only during their own open sitting. |
| `GET /api/v1/live-exam-participants/{id}` | Staff readers | Gains `comment`, `evaluatedFileName` (the stored marked PDF's original name), `moduleId`, `moduleTitleEn`, `moduleTitleBn`, `examTitleEn`, `examTitleBn`, `submittedAt`, `evaluatedAt`, `publishedAt` (G1, G2, R4). Same fields on the POST evaluation and publish responses and on each row of `GET /live-exam-modules/{moduleId}/submissions`. |
| `GET /api/v1/live-exam-modules` | Master Admin, System Admin, Instructor | Staff reads accept `status` (`DRAFT`, `PUBLISHED`, `ARCHIVED`) and `q` (title in either language), and stay on server paging. Staff rows carry `categoryPath` (root first, `{id, titleEn, titleBn}`), `instructor` (`{id, name, phone}` or `null`), `mcqCount`, `writtenCount` (all non-deleted exams, any status) (G5, G10, G4). An instructor gets only assigned modules. Student rows have these four fields `null`. |
| `GET /api/v1/live-exam-modules/{id}` | Master Admin, System Admin, Instructor | Same staff fields as the list (G4, G10). An instructor not assigned to the module gets `404`. |
| `GET /api/v1/live-exam-submissions?moduleId=&examId=&status=&q=&sort=&page=&size=` | Master Admin, System Admin (all modules), Instructor (assigned modules only) | Written submissions across modules (G11). `status`: `AWAITING`, `DRAFT`, `PUBLISHED`, `FAILED_TO_SUBMIT`. `sort`: `submittedAt,desc` (default), `submittedAt,asc`, `studentName`. Rows: `participantId`, `examId`, `examTitleEn/Bn`, `moduleId`, `moduleTitleEn/Bn`, `categoryPath`, `studentName`, `status`, `kind`, `answerPdf`, `submittedAt`, `evaluatedAt`, `publishedAt`, `evaluatorName` (staff only). Open sittings are left out. A sitting is posted by the deadline job (about one minute after the deadline), so a sitting that just ended shows after that. |
| `GET /api/v1/live-exams/{id}/results?kind=&status=&q=&sort=&page=&size=` | Master Admin, System Admin, assigned Instructor (written exams only) | Results report (G14). Body: `{ summary, rows }`, `rows` is the usual page. `summary`: `participants`, `submitted`, `failedToSubmit`, `averageScore`, `highestScore`, `lowestScore`, `passCount`, `passRate` (percent, one decimal), `practiceParticipants`. Live sittings only. A score counts once it is final: an MCQ submit, a written publish, or a no-submit zero. `kind`: `LIVE` or `RETAKE` (practice). `sort`: `submittedAt,desc` (default), `submittedAt,asc`, `score,desc`, `score,asc`, `studentName`. An unassigned instructor, or an MCQ exam read by an instructor, gets `404`. No phone numbers. |
| `GET /api/v1/students/{id}/live-exam-results?page=&size=` | Master Admin, System Admin | A student's sittings, newest first (G15): `examTitleEn/Bn`, `moduleTitleEn/Bn`, `kind`, `status`, `score`, `maximum`, `passed`, `submittedAt`, `published` (written: a publish or a no-submit zero; MCQ: submitted). Unknown student `404`. |
| `POST /api/v1/live-exams/{id}/publish-results` | Master Admin only | Publishes every draft of a written exam in one transaction (G16). Returns `{ "published": n }`. Each one writes a `LIVE_EXAM_RESULT_PUBLISH` audit row and a student notice, as the single publish does. The single publish now writes the same audit row. |
| `POST /api/v1/live-exams/{id}/copy` | Master Admin, System Admin | Draft copy in the same module (G13), `201`. Titles get ` (copy)` / ` (কপি)`. Texts, settings, MCQ questions with options, or the question PDF as a new stored file are copied. No date, no price. |
| `GET /api/v1/public/live-exam-modules?q=` | Public | Title search (either language) with the existing `categoryId` and `examType` filters (G12). Hidden categories stay out. |
| `GET /api/v1/public/live-exams?upcoming=true&examType=&page=&size=` | Public | Published, dated exams in published modules that have not ended, soonest first (G12). `examType` is `MCQ` or `WRITTEN`. Rows: `id`, `examType`, `titleEn/Bn`, `moduleId`, `moduleTitleEn/Bn`, `liveAt`, `endAt`, `price`. `upcoming` must be `true`, otherwise `400 VALIDATION_FAILED`. |
| `PATCH /api/v1/me` | Student | Body `{ "fullName" }`, trimmed, 1 to 100 characters (G7). Returns `MeView`. Staff and Master Admin names are not editable here: `403`. |
| `POST /api/v1/live-exams/{id}/publish` | Master Admin, System Admin | A clash (`409 EXAM_WINDOW_CONFLICT`) now carries `details`: `{ id, titleEn, titleBn, liveAt, endAt }` of the exam it clashes with (G3). `details` is present only on this error. |

**Upload limits (G6, checked against the code on 25 October 2026).** The web must use these numbers.

| Upload | Server limit | Where it is set |
|---|---|---|
| Written question PDF | 100 MiB by default (`104857600` bytes) | `app.files.material-max-bytes`, env `MATERIAL_MAX_BYTES`. Shared with the material uploads. |
| Written answer PDF (student) | 80 MiB (`83886080` bytes) | `LiveExamSittingService.MAX_PDF_BYTES`. |
| Marked PDF (instructor) | 80 MiB | Same constant. |
| Any multipart request | 100 MB | `spring.servlet.multipart.max-file-size` and `max-request-size`. |
| Reverse proxy | `client_max_body_size 105m` (Nginx) | Server config. |

A file over the storage limit gets `413 FILE_TOO_LARGE` with message `File is too large.`. A file over the multipart cap also gets `413 FILE_TOO_LARGE` (before, it fell through to `500`).

**Error body.** `details` is an optional object. It is absent from every error except the clash above.

Staging demo content: `SEED_DEMO=true` with the `staging` or `dev` profile loads the demo catalog. Details and cleanup in [api/.cursor/progress/ui-delivery/demo-seed.md](../../api/.cursor/progress/ui-delivery/demo-seed.md).

## Added for the phase 2 UI (API-ADMIN)

Plan: [plans/ui-delivery/student-purchase-lms-plan.md](plans/ui-delivery/student-purchase-lms-plan.md), sections 6 and 8. Flyway: V39 (merged), read in section 7.

| Call | Who | Notes |
|---|---|---|
| `POST /api/v1/live-exam-grants` | Master Admin, System Admin | Grants a student access by hand (R-3). Body: `{ studentId, scope, moduleId, examId, reason }`. `scope` is `MODULE` (with `moduleId`) or `EXAM` (with `examId`; `moduleId` may be sent and must match the exam's module). `reason` is required, 1 to 500 characters. Returns `201` with `{ id, studentId, scope, moduleId, examId, source, grantedBy, reason, createdAt, revokedAt, revokedBy, revokeReason }`, `source` = `MANUAL`. Errors: `400 VALIDATION_FAILED`, `404 RESOURCE_NOT_FOUND` (unknown student, module or exam, or the id is not a student), `409 GRANT_ALREADY_ACTIVE` (the student already has an active grant on that target). Audit: `ACCESS_GRANTED`. |
| `DELETE /api/v1/live-exam-grants/{id}?reason=` | Master Admin | Revokes a purchase or manual grant (R-2). `reason` is required, 1 to 500 characters. Returns the grant with `revokedAt`, `revokedBy` and `revokeReason`. A revoked grant gives no access: the module and exam reads, the purchase guard, the order admin access flag and the staff access list all ignore it. A new grant on the same target works. A second revoke is `404`. No money moves (no refund). Audit: `ACCESS_REVOKED`. |
| `POST /api/v1/live-exam-orders/{id}/confirm` and `/reject` | Master Admin only | Decide a pending (`INITIATED`) order after checking the payment at SSLCommerz (R-8). Body: `{ "reason" }`, required, 1 to 500 characters. Confirm sets the order `SUCCEEDED` with channel `MANUAL-CONFIRMED`, records `confirmed_by` and `confirm_reason`, writes the grant with source `ORDER`, and queues the purchase receipt mail. Reject sets `FAILED` and `rejected_reason`, and grants nothing. Both return the order view. A decided order is not pending: `409 ORDER_NOT_PENDING`. Unknown order `404`. Audit: `ORDER_CONFIRMED`, `ORDER_REJECTED`. The `PURCHASE_GRANTED` notice on confirm is A2's (grant path). |
| `PUT /api/v1/live-exams/{id}/answers-released` | Master Admin, System Admin | Releases (`{ "released": true }`) or withholds (`false`) the MCQ correct answers and explanations of one exam (R-4). Returns `{ examId, released, releasedAt }`. Setting the state it already has changes nothing and writes no audit row. Audit: `ANSWERS_RELEASED` and `ANSWERS_WITHHELD`. The student review reads the flag: until it is released, `correct` and `explanation` are null after the sitting. Withholding keeps `answers_released_by` as the last person who released. Written papers are not affected. Unknown exam `404`. `released` is required, `400` otherwise. |
| `DELETE /api/v1/students/{id}` | Master Admin | Changed (R-10). Refused with `409 ACCOUNT_HAS_PAID_ORDERS` when the student has any `SUCCEEDED` order or any grant, active or revoked. The message tells the caller to suspend the account instead. Otherwise unchanged. |
| `PATCH /api/v1/students/{id}/status` | Master Admin | Changed (S-022). Suspend and restore students. System Admin no longer has this permission and gets `403`. Read, create and update of students stay with System Admin. |
| `PATCH /api/v1/instructors/{id}/status` and `DELETE /api/v1/instructors/{id}` | Master Admin only | Suspend and restore an instructor (`{ "status": "ACTIVE" \| "INACTIVE" }`), and delete one (R-2, S-023). Delete is refused with `409 ACCOUNT_HAS_MARKED_WORK` when the instructor has evaluated any submission, and the message says to suspend instead. Other roles get `403`. Module assignments are not removed by suspend or delete; remove them with the existing unassign route. |
| `GET /api/v1/students/{id}/live-exam-access` | Master Admin, System Admin | Gains `via` = `MANUAL` for each access granted by hand (one row for a module, one for an exam). Revoked grants are left out. |
