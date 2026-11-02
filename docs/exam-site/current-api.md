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

- Master Admin: manage, delete, roster, assign instructor, evaluate, and set a live exam price. `EXAM_PRICE` is Master Admin only. Only the Master Admin can mark a pending live exam order paid, or reject it, through `POST /api/v1/live-exam-orders/{id}/confirm` and `/reject` (`LIVE_EXAM_ORDER_CONFIRM`). System Admin and Instructor get `403`. `PAYMENT_VERIFY` belongs to course and book payments, which are out of this delivery.
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
| `GET /api/v1/live-exam-modules/{moduleId}/submissions` | Assigned instructor, or a staff reader. Read-only: it never posts a sitting. No web screen calls it; the staff screens use `GET /api/v1/live-exam-submissions`. |
| `GET /api/v1/live-exam-participants/{id}/answer-pdf` | Marker download. |
| `POST /api/v1/live-exam-participants/{id}/evaluation` | Marks, optional comment, and the marked PDF. The marked PDF is optional when one is already stored; a save without a file keeps it. The first save needs a PDF (`400 VALIDATION_FAILED`). A save to a paper that is not released yet is a draft. A save to a released paper is a correction: the student keeps the released mark, and the correction waits for Master Admin to release it (`releasePending` on the staff view, 2026-10-27). |
| `GET /api/v1/live-exams/{id}/leaderboard` | Empty until five minutes after the window. Live rows only. Written rows wait until `evaluated_at` is set, except a failed-to-submit zero. An instructor gets `404 RESOURCE_NOT_FOUND` on a written exam until one of its papers is released (`resultsReleased` on the staff exam read); Master Admin and System Admin are not held to that. |
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
| Unannounced date | Resolved in step 2b. Flyway `V31` makes `live_at` nullable. A published exam with no start stays on the module list and on the personal routine, last, with a blank date (decisions.md, "Product", line 10). Take exam stays closed. A dated publish still enforces the five-minute gap against other dated exams in the module. | Step 2b |
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
| `GET /api/v1/live-exams/{id}/question-pdf` | Master Admin, System Admin, assigned Instructor | Staff read of the written question PDF (G8). An unassigned instructor gets `404`. An assigned instructor gets it only after the exam's window has ended (`liveAt` plus `durationMinutes`), and only for a published exam; before that, `404` (2026-10-27). The student rule is unchanged: only during their own open sitting. |
| `GET /api/v1/live-exam-participants/{id}` | Staff readers | Gains `comment`, `evaluatedFileName` (the stored marked PDF's original name), `moduleId`, `moduleTitleEn`, `moduleTitleBn`, `examTitleEn`, `examTitleBn`, `submittedAt`, `evaluatedAt`, `publishedAt` (G1, G2, R4). Same fields on the POST evaluation and publish responses and on each row of `GET /live-exam-modules/{moduleId}/submissions`. |
| `GET /api/v1/live-exam-modules` | Master Admin, System Admin, Instructor | Staff reads accept `status` (`DRAFT`, `PUBLISHED`, `ARCHIVED`) and `q` (title in either language), and stay on server paging. Staff rows carry `categoryPath` (root first, `{id, titleEn, titleBn}`), `instructor` (`{id, name, phone}` or `null`), `mcqCount`, `writtenCount` (all non-deleted exams, any status) (G5, G10, G4). An instructor gets only assigned modules. Student rows have these four fields `null`. |
| `GET /api/v1/live-exam-modules/{id}` | Master Admin, System Admin, Instructor | Same staff fields as the list (G4, G10). An instructor not assigned to the module gets `404`. |
| `GET /api/v1/live-exam-submissions?moduleId=&examId=&status=&q=&sort=&page=&size=` | Master Admin, System Admin (all modules), Instructor (assigned modules only) | Written submissions across modules (G11). `status`: `AWAITING`, `DRAFT`, `PUBLISHED`, `FAILED_TO_SUBMIT`. `sort`: `submittedAt,desc` (default), `submittedAt,asc`, `studentName`. Rows: `participantId`, `examId`, `examTitleEn/Bn`, `moduleId`, `moduleTitleEn/Bn`, `categoryPath`, `studentName`, `status`, `kind`, `answerPdf`, `submittedAt`, `evaluatedAt`, `publishedAt`, `evaluatorName` (staff only). Open sittings are left out. A sitting is posted by the deadline job (about one minute after the deadline), so a sitting that just ended shows after that. |
| `GET /api/v1/live-exams/{id}/results?kind=&status=&q=&sort=&page=&size=` | Master Admin, System Admin, assigned Instructor (written exams only) | Results report (G14). Body: `{ summary, rows }`, `rows` is the usual page. `summary`: `participants`, `submitted`, `failedToSubmit`, `averageScore`, `highestScore`, `lowestScore`, `passCount`, `passRate` (percent, one decimal), `practiceParticipants`. Live sittings only. A score counts once it is final: an MCQ submit, a written publish, or a no-submit zero. `kind`: `LIVE` or `RETAKE` (practice). `sort`: `submittedAt,desc` (default), `submittedAt,asc`, `score,desc`, `score,asc`, `studentName`. An unassigned instructor, or an MCQ exam read by an instructor, gets `404`. No phone numbers. |
| `GET /api/v1/students/{id}/live-exam-results?page=&size=` | Master Admin, System Admin | A student's sittings, newest first (G15): `examTitleEn/Bn`, `moduleTitleEn/Bn`, `kind`, `status`, `score`, `maximum`, `passed`, `submittedAt`, `published` (written: a publish or a no-submit zero; MCQ: submitted). Unknown student `404`. |
| `POST /api/v1/live-exams/{id}/publish-results` | Master Admin only | Releases every ready draft and every correction waiting for release of a written exam in one transaction (G16). Refused with `409 EXAM_MARKING_INCOMPLETE`, `details: { awaiting, open }`, while a submitted paper has no mark or a sitting is still open (2026-10-27). A failed-to-submit zero does not block it. Returns `{ "published": n }`. Each one writes a `LIVE_EXAM_RESULT_PUBLISH` audit row and a student notice, as the single publish does. The single publish now writes the same audit row. |
| `POST /api/v1/live-exams/{id}/copy` | Master Admin, System Admin | Draft copy in the same module (G13), `201`. Titles get ` (copy)` / ` (কপি)`. Texts, settings, MCQ questions with options, or the question PDF as a new stored file are copied. No date, no price. |
| `GET /api/v1/public/live-exam-modules?q=` | Public | Title search (either language) with the existing `categoryId` and `examType` filters (G12). Hidden categories stay out. |
| `GET /api/v1/public/live-exams?upcoming=true&examType=&page=&size=` | Public | Published, dated exams in published modules that have not ended, soonest first (G12). `examType` is `MCQ` or `WRITTEN`. Rows: `id`, `examType`, `titleEn/Bn`, `moduleId`, `moduleTitleEn/Bn`, `liveAt`, `endAt`, `price`. `upcoming` must be `true`, otherwise `400 VALIDATION_FAILED`. |
| `PATCH /api/v1/me` | Student | Body `{ "fullName" }`, trimmed, 1 to 100 characters (G7). Returns `MeView`. Staff and Master Admin names are not editable here: `403`. |
| `POST /api/v1/live-exams/{id}/publish` | Master Admin only (`EXAM_PUBLISH`). System Admin gets `403 ACCESS_DENIED`. Publish needs the English and Bangla descriptions and rules, else `409 EXAM_TEXT_REQUIRED`. A clash (`409 EXAM_WINDOW_CONFLICT`) now carries `details`: `{ id, titleEn, titleBn, liveAt, endAt }` of the exam it clashes with (G3). `details` is present only on this error. |

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
| `PUT /api/v1/live-exams/{id}/answers-released` | Master Admin, System Admin. No staff screen uses it since the answer-release switch was removed from the exam page (decision D4). Whether the endpoint stays is still open. | Releases (`{ "released": true }`) or withholds (`false`) the MCQ correct answers and explanations of one exam (R-4). Returns `{ examId, released, releasedAt }`. Setting the state it already has changes nothing and writes no audit row. Audit: `ANSWERS_RELEASED` and `ANSWERS_WITHHELD`. The student review reads the flag: until it is released, `correct` and `explanation` are null after the sitting. Withholding keeps `answers_released_by` as the last person who released. Written papers are not affected. Unknown exam `404`. `released` is required, `400` otherwise. |
| `POST /api/v1/announcements` | Master Admin only | Sends an announcement (R-5). Body: `titleEn`, `titleBn`, `bodyEn`, `bodyBn` (each may be blank, not all four; titles up to 500 characters, bodies up to 5000), `audience` (`ALL` with no `moduleId`, or `MODULE` with `moduleId`). Writes the announcement and one `ANNOUNCEMENT` notice per target student in one transaction. `ALL` targets active students. `MODULE` targets active students with a roster row or an active grant (module or exam) in that module, and the notice names the module. Returns `201` with `{ id, audience, moduleId, recipients, createdAt }`. Not editable or deletable. Audit: `ANNOUNCEMENT_SENT`. Errors: `400 VALIDATION_FAILED`, `404 RESOURCE_NOT_FOUND` (unknown module), `403` for other roles. |
| `DELETE /api/v1/students/{id}` | Master Admin | Changed (R-10). Refused with `409 ACCOUNT_HAS_PAID_ORDERS` when the student has any `SUCCEEDED` order or any grant, active or revoked. The message tells the caller to suspend the account instead. Otherwise unchanged. |
| `PATCH /api/v1/students/{id}/status` | Master Admin, System Admin | Suspend and restore students (S-022, C-17, decided 2026-10-26). Read, create and update of students stay with System Admin. |
| `PATCH /api/v1/instructors/{id}/status` and `DELETE /api/v1/instructors/{id}` | Master Admin only | Suspend and restore an instructor (`{ "status": "ACTIVE" \| "INACTIVE" }`), and delete one (R-2, S-023). Delete is refused with `409 ACCOUNT_HAS_MARKED_WORK` when the instructor has evaluated any submission, and the message says to suspend instead. Other roles get `403`. Module assignments are not removed by suspend or delete; remove them with the existing unassign route. |
| `PATCH /api/v1/students/{id}/status` (Master Admin, System Admin); `PATCH /api/v1/admins/{id}/status` and `PATCH /api/v1/instructors/{id}/status` (Master Admin only) | As listed | Suspend (`INACTIVE`) and restore (`ACTIVE`) students, system admins and instructors (R-2, S-022, S-023). Audit `USER_SUSPENDED` or `USER_RESTORED` (this replaces `USER_STATUS_CHANGED`). Master Admin cannot be suspended. Suspension takes effect on the account's next request, including a token already issued, and ends its refresh sessions. |
| `GET /api/v1/students/{id}/live-exam-access` | Master Admin, System Admin | Gains `via` = `MANUAL` for each access granted by hand (one row for a module, one for an exam). Revoked grants are left out. |

## Added for the phase 2 UI (API-ACCESS)

Plan: [plans/ui-delivery/student-purchase-lms-plan.md](plans/ui-delivery/student-purchase-lms-plan.md) (S-003, S-005, S-012, S-018, S-024, S-027). Flyway: none (reads `V39`). A null field is left out of the body, as everywhere else in the API, unless a row says otherwise.

| Call | Who | Notes |
|---|---|---|
| `GET /api/v1/me/live-exam-access` | Student | Own access only (S-003): `{ modules: [{ moduleId, via, orderId }], exams: [{ examId, moduleId, via, orderId }] }`. Module `via`: `ROSTER`, `MODULE` (order or bundle grant on the module), `MANUAL` (grant by hand). Exam `via`: `EXAM` (order or bundle grant) or `MANUAL`. `orderId` is left out for a roster row and a manual grant. Revoked grants, deleted modules and deleted exams give nothing. A module grant covers every exam of the module, so its exams are not listed again in `exams`. No paging. Staff get `403`, no token `401`. |
| `access` on `GET /api/v1/public/live-exam-modules`, `/live-exam-modules/{id}`, `/live-exam-modules/{id}/exams` and `/live-exams/{id}` | Signed-in student | `{ state, buyable, blockedReason }` on each module and exam (S-003). Visitors, staff and an invalid token get no `access` field. `state`: `NONE`, `ROSTER`, `MODULE`, `MANUAL` (a module-wide reason applies to every exam of the module, in that order of preference), else for an exam `EXAM` or `MANUAL` from a grant on that exam. Owning one exam does not own its module. `blockedReason` is always written: `null` when `buyable`, else the first of `ALREADY_HAS_ACCESS` (state is not `NONE`), `PRICE_REQUIRED` (no price of its own), `CHECKOUT_OFF` (no payment gateway), `PHONE_REQUIRED`. The category tree carries no `access`. |
| `GET /api/v1/public/exam-site/status` | Public | `{ checkoutEnabled }` (S-005, S-013). True when the SSLCommerz gateway is configured, the same test `POST /api/v1/live-exam-orders` makes before its `503 PAYMENT_GATEWAY_UNAVAILABLE`. No configuration value is returned. |
| `categoryPath` on public module rows (`/public/live-exam-modules`, `/{id}`, and the modules inside `/public/live-exam-categories`) | Public | Root first, `[{ id, titleEn, titleBn }]`, the same shape as the staff `categoryPath` (G10, S-012). |
| `GET /api/v1/public/live-exams?upcoming=true` rows gain `moduleOnly` | Public | `true` when the exam has no price of its own and its module is priced, so it is sold only inside the module (S-027). `false` otherwise, including an exam that is not sold at all. |
| `GET /api/v1/live-exams/{id}/result` and the student `GET /api/v1/live-exams/{id}` review | Student | MCQ answer release (S-018, R-4). `correct` on each option and `explanation` on each question are left out of every review until `answers_released_at` is set on the exam (`PUT /api/v1/live-exams/{id}/answers-released`, API-ADMIN) and the student's own sitting is over. Before that the review still shows the questions, the options and `selected`. The shape is unchanged. Written papers are not affected. |
| Written deadline (R-7, S-024) | Student | No change, now covered by `LiveSittingIT`: an upload at or after `deadlineAt` gets `409 DEADLINE_PASSED` and keeps the stored file; the latest upload before the deadline is posted as `SUBMITTED` by the deadline job without a submit click; no upload is posted as `FAILED_TO_SUBMIT` with score `0`. |


## Added for the phase 2 UI (API-NOTICE)

Plan: [plans/ui-delivery/student-purchase-lms-plan.md](plans/ui-delivery/student-purchase-lms-plan.md) sections 6.2 and 8. Flyway: V40 (merged), which widens the kind check, makes `student_notices.exam_id` nullable, and adds `student_notices.module_id`.

Every kind uses the existing `GET /api/v1/me/notices` and `POST /api/v1/me/notices/{id}/read` rows. No new route. Each row has `id`, `kind`, `createdAt`, `readAt`, and `exam` (`id`, `moduleId`, `examType`, `titleEn`, `titleBn`, `liveAt`, `endAt`). Only `MCQ_SCORE` and a published `WRITTEN_RESULT` carry `score`, `maximum` and `passed`. An `ANNOUNCEMENT` row has no `exam`; it carries `announcement` (`id`, `titleEn`, `titleBn`, `bodyEn`, `bodyBn`, `audience`, and `moduleId` for a module audience). Null fields are left out of the JSON.

| Kind | Who gets it | When | Message key | Written by |
|---|---|---|---|---|
| `EXAM_REMINDER` | Students with access | 48 hours before start (unchanged) | `studentId:examId:liveAtMillis` | `StudentReminderJob.scan` |
| `EXAM_STARTING_SOON` | Students with access | Within 60 minutes before start (S-017, in-app only, no mail) | `studentId:examId:liveAtMillis` | `StudentReminderJob.scanSoon` |
| `EXAM_OPEN` | Students with access | When the exam's window opens; not after it closes | `studentId:examId:liveAtMillis` | `StudentReminderJob.scanOpened` |
| `PURCHASE_GRANTED` | The buyer, or the student granted by hand | When an exam grant is created (module grants are not written yet) | `studentId:EXAM:examId` | `LiveExamOrderTx.writeGrant` (paid or confirmed order); `LiveExamGrantAdminService.grant` (manual grant). Both call `StudentNoticeWriter.purchaseGranted(studentId, moduleOrExamId, scope)` |
| `EXAM_PUBLISHED` | Students with access to the module or the exam | When the exam is published; publishing again adds nothing | `studentId:examId` | `LiveExamService.publish` |

`RETAKE_AVAILABLE` is not written. The retake rule (`LiveExamStudentStatus`: a missed MCQ exam after its window) exists, but a notice needs a scan at window end, which is new logic.

`ANNOUNCEMENT` is written by API-ADMIN's `AnnouncementService`, one row per recipient. The inbox joins exams and announcements with left joins, so these rows show.

**Announcement audience rule (decision, item 9).** `POST /api/v1/announcements` with `audience` = `MODULE` sends to the students who have access to that module: active students with a roster row in the module, or an active grant (module or exam scope) in it. Revoked grants do not count. Exam-only buyers are included, because they are in the module's grants. To narrow this to roster only, or to widen it, change the `TO_MODULE` query in `AnnouncementService`; the tests in `LiveGrantAdminIT` check the rule. Accounts that are suspended or deleted never receive a notice.

## Added for the admin gaps (ADMIN-API)

Plan: [plans/ui-delivery/student-purchase-lms-plan.md](plans/ui-delivery/student-purchase-lms-plan.md), section 2b (AG-01 to AG-04). Flyway: none. Tests named below are the new or extended assertions.

| Call | Who | Notes |
|---|---|---|
| `GET /api/v1/announcements?page=&size=` | Master Admin only | Announcement history (AG-01), newest first, paged with `PageResponse` (`content`, `page`, `size`, `totalElements`, `totalPages`). Size defaults to 20 and is capped at 100. Each row: `id`, `titleEn`, `titleBn`, `audience`, `moduleId`, `moduleTitleEn`, `moduleTitleBn` (set for `MODULE` only), `createdAt`, and `recipients` (the count of `student_notices` rows with that `announcement_id`, so `0` when nobody got it). The body is not returned. Other roles get `403 ACCESS_DENIED`. Tests: `LiveGrantAdminIT.announcementHistoryIsMasterAdminOnlyNewestFirstWithoutBodiesAndCountsRecipients`. |
| `GET /api/v1/live-exams/{id}` and `answersReleasedAt` | Staff (staff view), student (student view) | AG-02. The staff view carries `answersReleasedAt` only while the release is on. The student view never carries it. Tests: `LiveSittingIT.answersReleasedAtFollowsTheReleaseAndIsNeverOnTheStudentView` (null when withheld, set after release, null after withdraw, null on the student view in each state). |
| `GET /api/v1/system-status` | Master Admin only | AG-03. Tests in the new `SystemStatusIT`: Master Admin gets `200` with the seven check names, each with `name`, `ok` (boolean) and `note`, and no configuration value; System Admin, Instructor and Student get `403 ACCESS_DENIED`. |
| `GET /api/v1/students/{id}/live-exam-access` | Master Admin, System Admin | AG-04. Tests in `LiveGrantAdminIT.theAccessListGivesARosterRowNoGrantIdAndLeavesARevokedGrantOut`: a roster row has no `grantId`, a manual grant row carries its id, and after revoke the grant is gone from the list. |

`GET /api/v1/announcements` is also listed in `AuditLogIT` (the OpenAPI operations check).

## Added for the API staff read

| Call | Who | Notes |
|---|---|---|
| `GET /api/v1/live-exam-participants/{id}/evaluated-pdf` | Master Admin, System Admin, assigned Instructor | The stored marked PDF, `application/pdf`. Returned whether or not the result is published. Same gate as the other staff reads (`LIVE_EXAM_EVALUATE` or `EXAM_READ`, then the module check). An unassigned instructor gets `404`, as does an unknown or deleted participant and a paper with no stored marked PDF. A student or other role gets `403`. The student route `GET /api/v1/live-exams/{id}/evaluated-pdf` is unchanged. |

## Added for the phase 2 UI (API-BUNDLE)

Plan: [plans/ui-delivery/student-purchase-lms-plan.md](plans/ui-delivery/student-purchase-lms-plan.md), decision D1 and section 8. Flyway: none (uses `live_exam_order_exams` and the `BUNDLE` scope from V39). The web flag `NEXT_PUBLIC_BUNDLE_ORDERS` stays off.

| Call | Who | Notes |
|---|---|---|
| `POST /api/v1/live-exam-orders` with `{ "examIds": [uuid, ...] }` | Student | One payment for several exams of one module. Send exactly one of `moduleId`, `examId`, `examIds`; any other mix is `400 VALIDATION_FAILED` (a body with `moduleId` and `examIds` used to be read as a module order and is now refused). `examIds`: an array of 1 to 20 distinct exam ids, all in one module, else `400 VALIDATION_FAILED`. Each exam must exist, be published, and sit in a published, visible module, else `404 RESOURCE_NOT_FOUND`; each must have its own price, else `409 PRICE_REQUIRED`. If the student has a roster row or a module grant for the module, or an active exam grant for any listed exam, the whole request is `409 ALREADY_PURCHASED` and nothing is written. Otherwise one order: `scope` = `BUNDLE`, `moduleId` set, no `examId`, `amount` = the sum of the exam prices, BDT, SSLCommerz, `INITIATED`, with one `live_exam_order_exams` row per exam. `201` for a new order. The same exam set again (in any order) while that order is `INITIATED` returns it with `200` and a fresh `redirectUrl`; a different set makes a new order. The SSLCommerz product name is the module's English title. |
| `examIds` on the order view | Student, Master Admin | Added to every response that returns the order view: initiate, `GET /api/v1/live-exam-orders/{id}`, `/cancel`, `/ipn`, `/confirm` and `/reject`. `BUNDLE`: the bundle's exams, in exam time order (undated last). `EXAM`: a one-item list with the exam. `MODULE`: left out (null), because a module order covers the module's future exams as well. The other fields do not change. |
| Paying a bundle | — | The IPN, the browser return, the recheck job and Master Admin `confirm` write one grant per exam: `scope` = `EXAM`, `source` = `BUNDLE`, `order_id` = the order, and one `PURCHASE_GRANTED` notice per exam (key `studentId:EXAM:examId`). The "already granted" check is per exam, so a replayed IPN adds nothing and an exam granted another way after checkout started is skipped while the rest are granted. A failed verification sets `FAILED` and grants nothing; `reject` grants nothing. The receipt mail and the Master Admin purchase mail go once per order, titled with the module; exam-time mail is queued for the bundle's exams only. |
| Read paths | — | `GET /api/v1/live-exam-orders` (staff) and `GET /api/v1/me/live-exam-orders` (student): a bundle row has `scope` = `BUNDLE`, no `examId` or exam titles, and the module's titles. On the staff list `accessGranted` is true when any of its exam grants is active. `GET /api/v1/live-exam-orders/status`: `examName` is the module's English title. `GET /api/v1/me/live-exam-access` and the staff access list show each bundle grant as an exam row (`EXAM` / `EXAM_PURCHASE`) with the bundle's `orderId`. |

Tests: `BundleOrderIT`.

## Added 2026-10-27 (release workflow, plan `plans/release-workflow-plan.md`)

Flyway: V42 (pending correction columns), V43 (`ready_for_release_at`).

| Call | Who | Notes |
|---|---|---|
| `POST /api/v1/live-exam-participants/{id}/ready` | Assigned instructor, Master Admin | Flags a marked paper ready for release. `409 EVALUATION_NOT_ALLOWED` on a paper with no mark. Any save clears the flag. |
| `DELETE /api/v1/live-exam-participants/{id}/ready` | Assigned instructor, Master Admin | Clears the flag. |
| `GET /api/v1/live-exams/{id}/release-status` | Master Admin, System Admin | `{ awaiting, open, drafts, ready, failedToSubmit, canRelease }`. Read only: an expired sitting shows as open until the deadline job posts it (about a minute). |
| `POST /api/v1/live-exams/{id}/publish-results?ready=true` | Master Admin | Releases only the papers flagged ready. No completeness gate: the marker has said those are finished. |
| `GET /api/v1/live-exam-submissions?ready=true` | Master Admin, System Admin, instructor | Only the papers flagged ready. |
| Feed rows and the marking read | Staff | `releasePending` (a correction waits for release) and `readyForRelease` (flagged). Null when false. |
| `GET /api/v1/live-exam-participation` rows | Staff | `readyForRelease`: the count of papers flagged ready. `correctionsWaiting`: the count of corrections to released results that wait for release (2026-10-27). The `draftsAwaitingPublish` count includes a correction waiting for release, and `published` does not (fixed 2026-10-27). |
| `POST /api/v1/live-exams/{id}/archive` | Master Admin only (`EXAM_DELETE`) | Archives an exam (2026-10-27). Status `ARCHIVED`, from `V44__live_exam_archive.sql` (the status check now allows it). Returns the exam view. An archived exam is off every student read (those read `PUBLISHED`), keeps its posted results, and staff still read it with `status` `ARCHIVED`. Archiving twice is harmless. Audit: `LIVE_EXAM_ARCHIVE`. |
| `PATCH /api/v1/live-exams/{id}`, `POST /live-exams/{id}/final-save`, `POST /live-exams/{id}/publish` on an archived exam | Staff | `409 EXAM_ARCHIVED` ("An archived exam cannot be changed."). Not yet guarded: the question routes, the question PDF upload and copy. Tests: `LiveExamArchiveIT`. |

## Added 2026-10-28 (NR-01, the exam segment; plan `new-requirements/nr-01/`)

**Read this section first where it disagrees with the rows above.** NR-01 changes the sitting, scoring, editing and catalog rules. The rows it replaces are listed under "Superseded" at the end. Per-agent detail is in `new-requirements/nr-01/api-changes/` (a.md authoring, b.md catalog, c.md commerce, d.md sitting). Flyway `V45` to `V51`. The website was not changed: every response keeps its old fields and the old vocabularies below stay (contracts section 7).

### Model

- **Exam home.** An exam belongs to a **category** (`live_exams.category_id`, required). A package (`live_exam_modules`) is a bundle of exams through `live_exam_package_exams`. An exam may be in several packages or none. `live_exams.module_id` is history only; nothing reads it. The API paths still say `live-exam-modules`; the screens say "package" (R1).
- **Window and duration are separate.** `liveAt` to `endAt` is the live window (`ends_at`, at least as long as the duration). A sitting's deadline is its own start plus `durationMinutes`; the window end never caps it. `mode` is `LIVE` or `PRACTICE` (no window, open from publish).
- **Attempts.** The first attempt is `LIVE` (started inside the window), `ARCHIVED` (after it) or `PRACTICE`. Later attempts are `RETAKE`. The kind is set at start and never changes. MCQ retakes are unlimited. A written exam allows one retake, only with a grant (`live_exam_retake_grants`).
- **Scoring.** Questions may have several correct options (`multipleAnswer`). Full marks need an exact match. A non-empty wrong selection loses `negativeMarks`; a blank scores 0. Scores are decimals and may be negative. Per exam: `answersChangeable`, `negativeMarks`, `passPercent`.
- **Results at submit.** An MCQ result, the key and the explanations come back as soon as the paper is submitted, with a `position`. A written result still waits for release.
- **Positions.** `{type: CURRENT | RELATIVE | BOARD, rank, of, belowPass}`. CURRENT: a live attempt during the window. BOARD: a live attempt after the window, or a practice first attempt. RELATIVE: an archived attempt or a retake, ranked as if on the board. `rank` is 1 plus the number of board rows with a strictly higher score; ties share the better rank; below the pass mark there is no rank.
- **Leaderboard.** Board attempts only (`LIVE`, or `PRACTICE` first attempts), at or above the pass mark, highest score first, earliest submit breaking ties. Students see only their own position until the window ends.
- **No edit lock.** Staff may edit an exam or a package at any time. On a **published** one the edit is **staged** in a revision (`live_exam_revisions`, `live_exam_module_revisions`): students and the sitting keep reading the published rows, and staff read the working copy with `pendingChanges: true`. The Master Admin reads the change list (`GET …/pending-changes`) and publishes it with the `revisionId`. Publishing never re-scores a paper, writes `live_exam_change_log`, and sends an `EXAM_UPDATED` notice (no mail) when the time, duration, written paper or MCQ question content changed.
- **Questions and options can be added, removed or changed at any time**, including after a student has started. Each attempt keeps what it started with: an MCQ attempt keeps its questions, options, key, marks, negative mark and pass percent (`question_snapshot`), and a written attempt keeps its question PDF version, maximum and pass percent. Later edits reach only attempts started after the publish. Question and option rows are soft-deleted, so saved answers still resolve.
- **Withdraw and archive wait for open sittings.** `POST /live-exams/{id}/archive` (shown as Withdraw) is `EXAM_SITTING_OPEN` (409) while a student is sitting the exam. Archiving a category or a package, or deleting a package, is `CATALOG_SITTING_OPEN` (409) while a student is sitting an exam under it.
- **A withdrawn exam takes edits.** If it was ever published, edits are staged and the Master Admin can publish them; it stays withdrawn. Restoring it returns it to draft with any staged edits applied.
- **The leaderboard ranks by raw marks.** Ties go to the earlier submit, then the name. Each row shows the attempt's own `maximum` and `percent`.
- **Paper versions.** Each written question PDF is a version (`live_exam_question_papers`). A participant and their evaluator always read the version that participant sat.
- **Access.** A student can sit an exam through an exam grant; through a roster row or grant on a **published** package the exam is in; through a bundle or manual grant; or because the exam is free (signed-in students). An archived, draft or deleted package gives no access through it. Removing an exam from a package keeps it for anyone who held the package before the removal. Access ends at the later of the grant start and the window end, plus `accessDays` (never for lifetime, free, or an undated exam). Expiry stops new attempts and the paper; results stay readable.
- **Commerce.** `discountPercent` (1 to 99) with an optional `discountEndsAt`, on exams and packages. The charged amount is the effective price. Discounts, prices and the free toggle need `EXAM_PRICE` (Master Admin). An order records `listPrice` and `discountPercent`.
- **Instructor.** One instructor per **exam** (`live_exams.instructor_id`). A package keeps a default instructor that new exams inherit.
- **Popular and favourites.** `popular` on exams and packages (Master Admin). Students favourite exams and packages without buying.
- **Preview.** With `previewEnabled`, a signed-in student can read the questions (no key, no explanation) once the window has ended, or at any time for a practice exam.
- **Mail.** The exam-time and hour-before emails reach holders of any published package the exam is in, plus exam grant holders, so an exam in no package is mailed too.

### Legacy fields kept for the current website

- `kind` stays `LIVE | RETAKE` (`ARCHIVED`, `RETAKE` and `PRACTICE` map to `RETAKE`); `attemptKind` has the full value. Existing staff rows keep `kind` and add `attemptKind`.
- `myStatus` keeps its nine values; `myStatusDetail` adds `ARCHIVED_OPEN`, `PRACTICE_OPEN` and `EXPIRED`.
- `PUT /answers` still accepts `{questionId, optionId}`.
- `POST /retake` with no earlier attempt on an MCQ exam whose window has ended acts as `take`.
- `POST /live-exams/{id}/publish` without a `revisionId` is accepted from the Master Admin and logged at warn.
- `price` stays at the top level of every row; `pricing` is the new object.

### New and changed routes

The authoritative tables are in `new-requirements/nr-01/api-changes/`. Summary:

| Area | Routes |
|---|---|
| Authoring | New: `POST /live-exams` (standalone), `GET` and `DELETE /live-exams/{id}/pending-changes`, `GET /live-exam-pending-changes`, `GET /live-exams/{id}/preview` and `/preview/question-pdf`, `PUT /live-exams/{id}/popular`. Changed: create, `PATCH`, questions, `question-pdf`, `copy`, `publish`, `DELETE`. |
| Packages and catalog | New: `POST` and `DELETE /live-exam-modules/{id}/exams/{examId}`, `PATCH …/exams/order`, `GET` and `DELETE …/pending-changes`, `PUT …/popular`, `GET /live-exams` (staff list), `GET /public/live-exam-categories/{id}/exams`, `GET /me/favourites`, `PUT` and `DELETE /me/favourites/exams/{id}` and `/packages/{id}`. Changed: package `PATCH`, `publish` and `DELETE`, the category trees, and the public package, exam and upcoming reads. A package's exam list is sorted by date, soonest first with undated last, and by membership order among equal dates. |
| Commerce | Changed: `POST /live-exam-orders` (no package for an exam order, effective price, `EXAM_NOT_FOR_SALE`), the order lists (`listPrice`, `discountPercent`), `POST /live-exam-grants` (exam grant without a package), the access lists (`endsAt`, `expired`, `grantedAt`). |
| Sitting and marking | New: `PUT /live-exams/{id}/answers/{questionId}`, `GET /live-exams/{id}/attempts`, `POST` and `DELETE /live-exams/{id}/retake-grants`, `POST` and `DELETE /live-exams/{id}/instructor`, `GET /live-exam-participants/{id}/question-pdf`. Changed: `take`, `retake`, `answers`, `submit`, `result`, `leaderboard`, results, participation, the submission feeds, the package instructor routes (`applyToExams`, default true). |

### New error codes

`EXAM_WINDOW_INVALID` (400), `EXAM_SITTING_OPEN`, `CATALOG_SITTING_OPEN`, `ANSWER_LOCKED`, `ATTEMPT_IN_PROGRESS`, `ACCESS_EXPIRED`, `PENDING_CHANGES_STALE`, `NO_PENDING_CHANGES`, `EXAM_ALREADY_IN_PACKAGE` (all 409 unless stated).

### Superseded by NR-01

- The live window equals the duration, and the take deadline is the shared window end. Now: separate, and the deadline is the sitting's own start plus the duration.
- One later MCQ attempt only for a student who never took the live sitting; no written later attempt. Now: unlimited MCQ retakes, one granted written retake.
- MCQ review only after the sitting ends; the MCQ leaderboard opens five minutes after the window. Now: the result and key at submit; the board opens at the window end.
- `EXAM_WINDOW_CONFLICT` (five-minute gap) and `EXAM_WINDOW_ENDED` on publish: no longer raised.
- `CONTENT_LOCKED` after `liveAt`: no longer raised. Any edit returning an exam to draft: replaced by staged edits.
- The no-submit zero stays on the live board: replaced by the pass-mark filter on the board.
- The answers-released switch: stores a value and gates nothing.
- One instructor per package: now per exam, with a package default.

## Added 2026-10-29 (Student page Phase 4, S4.0; plan `new-requirements/student-page/PHASE-4-PLAN.md` section 5)

All additive. Old clients keep working: every existing field and route is unchanged, and new fields are added at the end of a response.

### Migrations

- **V52** (`live_exam_orders`): `paid_at timestamptz` and `receipt_no varchar(24)` (unique), with the sequence `live_exam_receipt_seq`. Paid time and the receipt number are set in the transaction that marks an order PAID (gateway verify, IPN, Master Admin confirm). A repeat notice does not move either.
- **Backfill (V52).** Each existing PAID order gets `paid_at = updated_at`. That is the best value on record and is approximate for an order changed after payment. Existing PAID orders take receipt numbers in paid order. The demo seed does the same for its paid orders.
- **V53** (`live_exams`): `cover_stored_name`, `cover_content_type`, `cover_byte_size`, `cover_updated_at`, as V41 on packages.

### Notices (N1)

- `GET /api/v1/me/notices?group=RESULTS|EXAMS|ANNOUNCEMENTS`. Absent means all, as before. Any other value is 400 `VALIDATION_FAILED`.
- The tab of a kind is in one place, `NoticeGroup`: `MCQ_SCORE` and `WRITTEN_RESULT` are RESULTS, `ANNOUNCEMENT` is ANNOUNCEMENTS, and every other kind (including one added later) is EXAMS.
- Each notice row adds `group` (the tab name).
- `GET /api/v1/me/notices/unread-count` returns `{count, byGroup: {RESULTS, EXAMS, ANNOUNCEMENTS}}`. `count` is unchanged.
- `POST /api/v1/me/notices/read-all?group=` marks one tab. Absent marks every notice, as before.

### Orders (P1, P2, P3, P4)

- `GET /api/v1/me/live-exam-orders` takes optional filters, all combinable:
  - `status`: repeatable, one of `PENDING`, `PAID`, `FAILED`, `CANCELLED`.
  - `scope`: `MODULE`, `EXAM` or `BUNDLE`.
  - `moduleId`: the order's package, or the package of its exam (the same value the row shows as `moduleId`).
  - `examId`: an exam's own orders, and any bundle that holds the exam.
  - `from` and `to` (ISO dates, Bangladesh time, `to` inclusive): compared with the paid time, or the created time for an order not yet paid.
  - `orderId` (P3): zero or one row with titles. Another student's id returns an empty page.
  - Bad values are 400 `VALIDATION_FAILED`, and so is `from` after `to`.
- Each row adds `paidAt` and `receiptNo` (null until paid). `OrderView` (`POST /live-exam-orders`, `GET /live-exam-orders/{id}`, and the rest) adds the same two fields. The staff order list (`GET /live-exam-orders`) adds them too.
- `GET /api/v1/me/live-exam-orders/summary` returns `{totalPaid, currency, paidCount, pendingCount}` for the signed-in student, all time. `currency` is `BDT`.
- `GET /api/v1/me/live-exam-orders/{id}/receipt.pdf` is an A4 PDF of the student's own PAID order, sent as an attachment named by the receipt number. Any other order (pending, failed, cancelled, another student's, unknown) is 404. The PDF follows an invoice layout with the Symam Law company block and logo, the billed-to block, an item table (one row per module, exam, or bundle exam, with its validity), the totals with a red discount line and a PAID stamp, and the payment details; the company details are constants at the top of `LiveExamReceiptPdf`. It is in English, because PDFBox cannot draw Bangla: the student's name keeps only its Latin letters, an item with no English title prints the order id in its place, and the order id is always printed.
- The receipt email (`ExamOutboundMail` payment receipt) carries the receipt number as one more line.

### Exam cover (E1)

- `POST /api/v1/live-exams/{id}/cover` (multipart `file`) and `DELETE /api/v1/live-exams/{id}/cover`: Master Admin (`MODULE_COVER_MANAGE`). Same types and 2 MB limit as the package cover. Saved at once, not staged.
- `GET /api/v1/live-exams/{id}/cover`: staff (`EXAM_READ`), draft or published, `Cache-Control: private, no-cache`.
- `GET /api/v1/public/live-exams/{id}/cover`: visitors. Only a PUBLISHED exam; any other exam is 404. `Cache-Control: public, max-age=3600`.
- `coverUrl` (null when none) is added to: the staff and student exam read (`ExamView`, `StudentExam`), the package exam list (`ExamView`), the category exam rows (`CategoryExam`), the upcoming and popular rows (`UpcomingExam`), the public exam read and the favourites rows (`PublicExam`). The path carries the upload time as `?v=`.
- The cover is the exam's own. The web falls back to the first package's cover, then the placeholder.

### Known gaps

- The bundle match for `examId` is covered by the SQL only; no IT builds a bundle with several exams in the new filter test.
- No refunds and no refund states.

## Added 2026-10-29 (NR-01 UI Step 1)

Plan `new-requirements/nr-01-ui/STEP-1-PLAN.md`, slice A1. All additive for old clients, except the two changes below, which the web follows.

### Migrations

- **V55** (`live_exams`): `paper_language` widened to `VARCHAR(8)`, now nullable. The check is replaced (the V30 check was `live_exams_paper_language_check`): `BN`, `EN` or `BOTH`, and null passes. No existing row changes.

### Exams

- `paperLanguage` is `BN | EN | BOTH | null` on the staff exam, the student exam, the public exam and the preview. `null` means no language was chosen: nothing is shown, and the answer letters follow the viewer's interface language (C2).
- **Create** (`POST /live-exams`, `POST /live-exam-modules/{id}/exams`): `paperLanguage` is optional. Absent or `null` stores `null`. Any other value other than `BN`, `EN` or `BOTH` is `VALIDATION_FAILED`, as is a blank string.
- **Create** accepts blank or missing `descriptionEn`, `descriptionBn`, `rulesEn` and `rulesBn` (stored as empty strings). Titles stay required. **PATCH still refuses a blank value** for these four fields.
- **Publish** (`POST /live-exams/{id}/publish`, and the staged publish): descriptions and rules are optional, for exams and packages. A blank one is stored as an empty string and is not shown to students.
- **PATCH with `"paperLanguage": null`** clears the language. An absent member leaves it as it is.
- **HTML on save.** The six exam text fields and the two package description fields are sanitized on every save, in the draft path and in the staged path. A value with no tags is plain text and is stored unchanged (line breaks kept). A value with tags keeps only `p br h2 h3 strong em u ul ol li a`, `text-align` on `p h2 h3`, and links with `href` of `http`, `https` or `mailto`, given `rel="nofollow"` (no `target` is kept, so `noopener` is not needed). Scripts, event handlers, images and other tags are removed. Library: OWASP Java HTML Sanitizer `20260924.2` (`api/pom.xml`).
- The package routes (`/live-exam-modules`) sanitize `descriptionEn` and `descriptionBn` on create and on edit. The legacy `/exam-modules` routes do not (not used by the NR-01 web).

### Known gaps

- The legacy `/exam-modules` routes take package descriptions unsanitized. The web renders them as plain text, so no HTML is shown, but the stored value is not cleaned.

## Added 2026-10-29 (NR-01 UI Step 1 fixes)

Fixes to the NR-01 UI Step 1 work. Two API behaviours change; the rest is web only.

### Exams

- **Package exam order (staff).** `GET /live-exam-modules/{id}/exams` and `GET /live-exams?packageId=` (staff) list a package's exams in the package's own order: membership `position`, then `addedAt`, then the membership id, then the exam id. The reorder `PATCH /live-exam-modules/{id}/exams/order` writes that position. An explicit `sort` parameter still wins. Before this fix both sorted by the window start (or `updatedAt`). A package in draft takes the new order at once. **Gap:** on a published package the reorder is staged, so the staff list shows the new order only after the Master Admin publishes the package.
- **Package exam order (student and public).** Unchanged: by date, soonest first with undated last, and by membership order among equal dates.
- **Category on PATCH.** `PATCH /live-exams/{id}` accepts `categoryId` for any exam, in a package or standalone. Master Admin and System Admin only (`EXAM_MANAGE`), as before. The category must exist, not be deleted, and not be archived (nor sit under an archived branch). Otherwise `400 VALIDATION_FAILED` with `fieldErrors[0].field = "categoryId"`. A body that repeats the exam's current category is accepted even when that category has been archived since, so other fields can still be saved. Create (`POST /live-exams`, `POST /live-exam-modules/{id}/exams`) is unchanged: a package exam takes the package's category.
- **Category on a published exam.** The change is staged like the other fields. The change list (`GET /live-exams/{id}/pending-changes`) names it as a field `categoryId` with the published and the staged category ids in `before` and `after`. It applies when the Master Admin publishes the revision.

## Added 2026-11-01 (announcements by package, exam and student; plan `docs/exam-site/plans/ui-delivery/admin-reviews-enrol-announcements-plan.md`, step 1, API)

All additive. `POST /api/v1/announcements` still takes `ALL` and `MODULE` as before, and the history keeps every field it had. Nulls are left out of JSON (the global `non_null` setting), so `moduleId`, `examId`, `withdrawnAt` and `exam` are absent when they are null.

### Migrations

- **V57** (`announcements`): `exam_id` (FK `live_exams`), `withdrawn_at` and `withdrawn_by` (FK `users`). The audience check now allows `ALL`, `MODULE`, `EXAM` and `SELECTED`. A target check: `module_id` is set for `MODULE` only, `exam_id` for `EXAM` only, and each is required for its own audience.

### Audiences

The holder rule is one class, `AnnouncementAudience`. The recipients of a send and the page check both use it, so they cannot drift. Every recipient is an active, non-deleted student.

| Audience | Who gets the notice | Who sees it on the page |
|---|---|---|
| `ALL` | Every active student | Nobody on a page (Notices only) |
| `MODULE` (package) | A roster row, a package grant, a grant on one of its exams, or a started attempt on one of its free exams | Every current holder of the package |
| `EXAM` | A grant on the exam, or a roster row or package grant on a published package that actively contains it, or a started attempt on the exam if it is free | Every current holder of the exam, and any signed-in student if the exam is free (published and `is_free`) |
| `SELECTED` | The students named (1 to 500, deduplicated) | Nobody on a page (Notices only) |

- Expiry follows `AccessWindow` and `LiveExamAccess.moduleEnd`, written as SQL. An exam grant ends `access_days` after the later of the grant and the window end (or the publish time, for practice). A package grant or roster row ends when every paid exam it covers has run out; a package with no paid exam never ends. A revoked grant is not a holder, and nor is an expired one.
- A started attempt makes a holder only on a free exam. A paid exam is open through a live grant or roster row, so an attempt on a paid exam whose grant has been revoked or has run out does not count.
- A free package does not exist in the data model: `live_exam_modules` has only a nullable `price` and no free flag. Only exams are free.
- A withdrawn announcement is not shown anywhere. A deleted target hides its notices, and its page shows nothing.

### Endpoints

- `POST /api/v1/announcements`: body `{titleEn, titleBn, bodyEn, bodyBn, audience, moduleId?, examId?, studentIds?}`. Response `{id, audience, moduleId, examId, recipients, createdAt}`. `moduleId` is required for `MODULE`, `examId` for `EXAM`, and `studentIds` (1 to 500) for `SELECTED`. Any other target field given for an audience is `VALIDATION_FAILED`. An unknown or deleted package or exam is 404. For `SELECTED`, an id that is not an active student is 400 `VALIDATION_FAILED`, with one `fieldErrors` entry per bad id (`field` is `studentIds`, and the message starts with the id). A package or exam with no recipients is allowed (`recipients` is 0).
- `GET /api/v1/announcements/preview?audience=&moduleId=&examId=`: `{recipients}`. Audience `ALL`, `MODULE` or `EXAM`. `SELECTED`, or any other value, is 400. It writes nothing, and it uses the same rule and student filter as the send. Same permission as the send.
- `GET /api/v1/announcements`: each row adds `examId`, `examTitleEn`, `examTitleBn`, `withdrawn` (boolean) and `withdrawnAt` (ISO string, or absent). Optional `audience=` filters the rows; any other value is 400. The body is still left out.
- `PUT /api/v1/announcements/{id}/withdraw`: returns the history row. Idempotent: a second call changes nothing, keeps the first `withdrawnAt`, and is not audited again. Unknown id is 404. Audit action `ANNOUNCEMENT_WITHDRAWN` on the announcement entity, recorded on the first withdrawal.
- `GET /api/v1/me/announcements?kind=package|exam&id=`: students only (`LIVE_EXAM_BROWSE` and role `STUDENT`, as `/me/notices`). A bare JSON array, newest first, at most 50, of `{id, titleEn, titleBn, bodyEn, bodyBn, createdAt}`; `createdAt` is an ISO instant. Only the non-withdrawn announcements for that target. A non-holder gets `[]` (200). An unknown or deleted target gets `[]`. An unknown `kind` is 404. No sign-in is 401.
- Notices (`/me/notices`): `announcement` adds `examId`, which is set only for an `EXAM` announcement (`moduleId` only for `MODULE`). An `EXAM` announcement's notice is in the `ANNOUNCEMENTS` tab, and its `exam` is null, as for every announcement. The notice is hidden when its exam is deleted.
- Withdrawn announcements are left out of `/me/notices`, `/me/notices/unread-count`, and `POST /me/notices/{id}/read` (404 for a withdrawn notice). `POST /me/notices/read-all` marks only the notices the inbox shows, so a withdrawn or deleted-target notice is not marked read.
- Permission `LIVE_EXAM_ANNOUNCE` is now granted to `SYSTEM_ADMIN` as well as `MASTER_ADMIN`. Instructors and students get 403 `ACCESS_DENIED` on send, preview, history and withdraw.

### Error codes (from the reviews work, documented here for the first time)

- `REVIEW_NOT_ALLOWED` (403): a student tries to write a review without having sat the exam. For a package, the student must hold it and have sat one of its exams.
- `REVIEW_NOT_FEATURABLE` (409): a feature or unfeature is asked for a review that is not `APPROVED` or has no comment.
- `INSTRUCTOR_NOT_FOR_MCQ` (409): an instructor is assigned to an MCQ exam ("An MCQ exam has no instructor."). A package default skips MCQ exams without an error.

### Known gaps

- For an `EXAM` announcement, a student whose only route to the exam was a package the exam has since been removed from keeps exam access in `LiveExamAccess` (R4), but is not a holder here: the plan says "actively contains". Decide later whether the rule should follow R4.
- Withdrawal is audited only on the first change, not on every repeat call.
- Hidden notices of a deleted module keep the existing rule: an exam notice, including an `EXAM` announcement, is hidden when the exam's own legacy `module_id` names a deleted package.

## Added 2026-11-01 (a submitted exam is final; the review waits while the live exam runs; API)

The owner's rule: once a student submits an exam, the submission is final, and it is not reviewable again while the live exam is still going on. This replaces decision D21' of NR-01 for the key and the explanations (`new-requirements/nr-01/d-sitting.md` line 79, `progress/d-d4.md`): an MCQ submit no longer returns the key at once. The score still shows at once. A written upload is the submission: the script cannot be replaced, and the question paper is no longer offered while the sitting is open.

### The review rule (`ReviewRule`, one definition for every read)

The review of a submitted MCQ attempt is open when any of these holds at the server time:

- (a) the attempt's own deadline (`participant.deadlineAt`) has been reached;
- (b) the attempt is not a live-window attempt: its kind is `PRACTICE`, `ARCHIVED` or `RETAKE`, or the exam is in practice mode, or the live run is over. The live run is over at `ExamWindow.lastLiveDeadline`, the window end plus the duration, the latest a live sitting can still run. A sitting that started just before the window end runs to its own deadline, so the window end alone does not end the run;
- (c) staff released the answers (`live_exams.answers_released_at` is set). Unsetting it closes the review again.

While the review is closed, the key and the explanations are not returned, and `reviewOpensAt` says when the review opens on its own (ISO instant). That is the attempt's deadline: a live-window attempt started inside the window, so the live run ends after its deadline. Only if staff moved the window end before the attempt started is the live run's end earlier, and then it is that end.

### Routes

- `GET /api/v1/live-exams/{id}` (student): `questions` is left out for a submitted MCQ whose review is closed, and `reviewOpensAt` is set then. A running MCQ attempt gets its questions without the key, as before. `StudentExam` adds `reviewOpensAt`.
- `GET /api/v1/live-exams/{id}/result`: the score, maximum, passed, status and position come as before. For a submitted MCQ whose review is closed, `questions` is left out and `reviewOpensAt` is set; when open, the questions come with the key and the explanations and `reviewOpensAt` is absent. `ReviewedResult` adds `reviewOpensAt`.
- `GET /api/v1/me/exam-results`: unchanged. The score row is visible (`resultVisible`) as soon as the MCQ is submitted.
- `GET /api/v1/live-exams/{id}/question-pdf` (student): once an answer script is stored and the sitting is open, it answers 409 `SITTING_NOT_OPEN`. After the sitting has ended it answers 404 as before.
- Written: `StudentExam.questionPdf` is `true` only while the sitting is open and no answer is stored, and `false` once the script is stored. A stored script before the deadline gives status `SUBMITTED` (already the rule in `LiveExamStudentStatus`). A second upload is `ANSWER_ALREADY_SUBMITTED`, and the first file stays.
- Older catalog path (`McqAttemptService`, `ExamResultService`): the score follows the release flags as before. The key, the explanations and the marks per question wait for the review. Its window is the component's; its review opens at the attempt's deadline, at the window end, or when staff publish results (`results_published`). The paths are `GET` and `POST` `/api/v1/catalog/exam-modules/{moduleId}/components/{componentId}/attempts`, `.../result` and `/api/v1/catalog/exam-modules/{moduleId}/papers/{paperId}/result`.

### Read paths checked, and left alone

- Staff reads (exam view, the staff result, the evaluation and submission feeds, the question-stats, the staff MCQ attempt list) keep the key.
- The public preview (`LiveExamPreviewService`) never carried the key.
- The written catalog result shows explanations only when staff publish results, as before.

### Known gaps

- The older catalog path has no release column of its own. Its staff release is `results_published`; the new path's is `answers_released_at`.
- A question paper PDF that staff upload for an MCQ exam is still offered by `question-pdf` during the sitting. It is not a key, and the rule does not cover it.

## 2026-11-01: a published exam's edit not showing on the student site

**Reproduced:** no for the API. `ExamRevisionIT.everyStagedFieldReachesEveryStudentReadAfterThePublish` stages title, descriptions, rules, paper language, pass percent, duration, start time, negative marks, answers changeable, preview, access days, category, price and discount on a published MCQ exam, edits one question and adds one, publishes with the revision id, then reads the public exam, the old and new category lists, the package exam list, the student exam read (maximum 3), the routine and a running sitting (questions "New stem", "Added stem"). Every read returns the new values. All 13 tests in the class pass.

**Root cause (web, confirmed by code trace):**
- `web/src/lib/api/exam/catalog.ts` (the public catalog reads: tree, package, package exams, category exams, public exam, popular, upcoming, module search and the my-exams public rows) set `staleTime` 60 s and did not set `refetchOnWindowFocus`, so the global default `false` applied. An open list or package page kept the pre-publish title and price until a reload or a remount after 60 s. Nothing polled those reads.
- `web/src/lib/api/exam/staff-catalog.ts`: the publish, discard, membership, popular and instructor mutations (`refreshChanged`, `usePublishPendingChanges`), the exam and package actions (`useExamAction`, `useModuleAction`) and archive (`useArchiveExam`) invalidated only the `staffCatalog` keys. Any student read cached in the same client (the public catalog, the routine, the student's own exam, the exam preview key `live-exam-preview`) was not refreshed.

**Fix:**
- `catalog.ts`: `refetchOnWindowFocus: true` on the public content reads, so a list that has gone stale refetches when the tab regains focus.
- `staff-catalog.ts`: new `refreshStudentReads` invalidates `catalogKeys.all`, `["live-exam-preview"]` and, for an exam, `sittingKeys.exam(id)`. It runs from the publish, discard and membership path and from the exam and package actions and archive.

**Found, not fixed (outside this report):**
- `api/.../exams/ExamChangeListBuilder.java` `compare()` does not compare question `position`, so a position-only staged edit gives an empty change list and `LiveExamRevisionService.stage()` drops the revision. The staff UI cannot send such an edit today (see next item).
- `web/src/components/staff/catalog/mcq-questions.tsx` move up and down sends `{ position }` only; `QuestionInput.parse` (`api/.../exams/QuestionInput.java`) requires `marks` and `stem`, so the reorder fails with 400 on draft and published exams.

## Added 2026-11-01 (the MCQ answer review opens at submit; supersedes the review rule above; API)

The owner decided on 1 November 2026 that the answers are shown at submit. Once a student submits an MCQ attempt, the answer key and the explanations are returned to that student at once, for every exam. The rule no longer depends on the live window, the attempt's own deadline, the release switch (`live_exams.answers_released_at`) or the catalog results flags. The score still shows at once, as before. Nothing else changes: a save after submit still gets 409 `ANSWER_ALREADY_SUBMITTED`, and written exams are unchanged.

This supersedes the review rule of the section above ("a submitted exam is final; the review waits while the live exam runs"): clauses (a) to (c), the `reviewOpensAt` routes and the catalog wait for the deadline or a release. What still holds from that section: a running attempt never shows the key, and a submitted attempt is final.

- `ReviewRule.open` returns true for a submitted attempt (live: `submittedAt` is set; catalog: status `SUBMITTED`). A running attempt is never open. `ReviewRule.opensAt` is removed.
- `reviewOpensAt` is now always absent. `StudentExam.reviewOpensAt` and `ReviewedResult.reviewOpensAt` stay in the Java records and in the OpenAPI spec (the web contract check needs the property), but they are always null, so the JSON omits them. The web already shows the answers when the field is absent.
- `GET /api/v1/live-exams/{id}` (student) and `GET /api/v1/live-exams/{id}/result` return `questions` with the key and the explanations for a submitted MCQ.
- Catalog path (`McqAttemptService`, `ExamResultService`): a submitted attempt's key is returned wherever its result is returned. The result's own gate is unchanged (immediate results or results published), so while that gate is closed the result read is still refused, as before.

## Added 2026-11-02 (a repeat buy uses the current price; API)

`POST /api/v1/live-exam-orders` used to return the student's open order for the same target unchanged, so a "Continue payment" after a price or discount change sent the old amount to SSLCommerz. Now, when the open order's `amount` differs from the price the student would pay now, that order is set to `CANCELLED` and a new order is made at the current price (`201`, new `tranId`). An open order at the same amount is reused as before (`200`, same `tranId`). If the gateway marked the old order paid in the meantime, the buy stops with `409 ALREADY_PURCHASED`.

A payment that still completes on the old SSLCommerz page is granted at the old order's amount, as for any cancelled order the gateway later validates. Test: `LiveExamPurchaseIT.buyingAgainAfterAPriceChangeOpensANewOrderAtTheNewPrice` (written, not yet run).

## Added 2026-11-02 (the payment's risk flag; API)

- **V66** (`live_exam_orders`): `risk_level varchar(16)`, null. It holds SSLCommerz's `risk_level` from the validation reply ("0" or "1"), set when a payment is verified. Null before V66, before payment, and for an order the Master Admin confirmed by hand.
- `GET /api/v1/live-exam-orders` adds `riskLevel` to each row, left out when null. A flagged payment is still granted (`decisions.md`, "SSLCommerz decisions").
- The session request also sends `cus_postcode` (`1000`). Tests: `SslCommerzGatewayTest`, `LiveExamPurchaseIT.theMasterAdminFindsOrdersWithThePaymentChannelAndTheGrant` (written, not yet run).

## Added 2026-11-02 (MCQ questions from a Word file, and the paper header; API)

Staff can replace an MCQ exam's questions from a `.docx` file (plan `docs/exam-site/plans/mcq-docx-upload/README.md`). The file is read by `McqDocxParser` (zip and StAX, no DTD or external entities, 20 MB cap, no new library). Its layout:

- Lines before question 1 are the **paper header**, kept as one line per line. `পূর্ণমান: n` sets the full marks and `সময়: n মিনিট` the minutes. Bangla digits are read.
- A question is a line that starts with a number and a dot (`1.`, `১।`).
- An option starts with `ক)`, `খ)`, `গ)` or `ঘ)`, at the start of a line or after a space.
- The correct option is the one whose bold characters are at least half of its non-space characters. A question with no bold option, or with more than one, is a problem.

**V68** (`live_exams`): `paper_header text`, null. Set by the file import, or left null.

Routes (both need `EXAM_MANAGE`; multipart field `file`):

- `POST /api/v1/live-exams/{id}/questions/import/preview`: reads the file and saves nothing. Returns `header`, `fullMarks`, `durationMinutes`, `questions` (`number`, `stem`, `options[{text, correct}]`), `problems` (`question`, `code`), `warnings`, `currentQuestions` and `staged`.
- `POST /api/v1/live-exams/{id}/questions/import`: saves the file's questions, options, header and paper language `BN`, and sets the duration when the file has one. A draft replaces its questions now (old rows are soft-deleted). A published exam, or a withdrawn one that was published, stages the change for the Master Admin and returns `staged: true`. Returns `{ questions, staged }`.

A time in the header longer than the exam's window (the staged window on a published exam) is the problem `DURATION_OUTSIDE_WINDOW`, shown in the preview. A file with any problem is refused with `409 QUESTION_FILE_HAS_PROBLEMS` and nothing is saved; the problems come in `fieldErrors` (`where`, `code`). A file that is not a `.docx` is refused the same way. An exam that is not MCQ gets `EXAM_TYPE_MISMATCH`.

The header is shown on the student's sitting (`StudentExam.paperHeader`) in place of the title and time line. The exam's own `titleEn` and `titleBn` do not change. A pinned attempt keeps the header it started with (`McqAttemptSnapshot`). The header is a field of the change list (`paperHeader`).

Known gaps: `McqDocxParserTest` is written but not run, as requested, and the import endpoints have no integration test yet. The Bangla problem and warning texts are proposals until the client confirms them. The change list has no summary line for a large question replacement.
