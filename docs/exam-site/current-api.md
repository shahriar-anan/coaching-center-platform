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
- **No edit lock.** Staff may edit an exam or a package at any time. On a **published** one the edit is **staged** in a revision (`live_exam_revisions`, `live_exam_module_revisions`): students and the sitting keep reading the published rows, and staff read the working copy with `pendingChanges: true`. The Master Admin reads the change list (`GET …/pending-changes`) and publishes it with the `revisionId`. Publishing re-scores submitted MCQ papers when scoring changed, writes `live_exam_change_log`, and sends an `EXAM_UPDATED` notice (no mail) when the time, duration, written paper or MCQ question content changed.
- **Questions and options can be added or removed only until the first sitting** (`QUESTION_STRUCTURE_LOCKED`). Wording, key, marks and explanations stay editable.
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

`EXAM_WINDOW_INVALID` (400), `QUESTION_STRUCTURE_LOCKED`, `ANSWER_LOCKED`, `ATTEMPT_IN_PROGRESS`, `ACCESS_EXPIRED`, `PENDING_CHANGES_STALE`, `NO_PENDING_CHANGES`, `EXAM_ALREADY_IN_PACKAGE` (all 409 unless stated).

### Superseded by NR-01

- The live window equals the duration, and the take deadline is the shared window end. Now: separate, and the deadline is the sitting's own start plus the duration.
- One later MCQ attempt only for a student who never took the live sitting; no written later attempt. Now: unlimited MCQ retakes, one granted written retake.
- MCQ review only after the sitting ends; the MCQ leaderboard opens five minutes after the window. Now: the result and key at submit; the board opens at the window end.
- `EXAM_WINDOW_CONFLICT` (five-minute gap) and `EXAM_WINDOW_ENDED` on publish: no longer raised.
- `CONTENT_LOCKED` after `liveAt`: no longer raised. Any edit returning an exam to draft: replaced by staged edits.
- The no-submit zero stays on the live board: replaced by the pass-mark filter on the board.
- The answers-released switch: stores a value and gates nothing.
- One instructor per package: now per exam, with a package default.
