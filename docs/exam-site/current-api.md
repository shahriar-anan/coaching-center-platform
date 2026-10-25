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

Removed 25 October 2026: `POST /api/v1/admin/mail-test` and `MAIL_TEST_ENABLED`. A real mail check now uses a normal register and forgot-password.

## Limitations

Shahriar confirmed these on 2026-10-23. The resolution order is [plans/resolve-api-gaps.md](plans/resolve-api-gaps.md).

| Limitation | API today | Resolved by |
|---|---|---|
| Written practice | Resolved in step 2a. `POST /api/v1/live-exams/{id}/retake` returns `409` `RETAKE_NOT_ALLOWED` for a written exam after the access check. The one MCQ later attempt remains. | Step 2a |
| Unannounced date | Resolved in step 2b. Flyway `V31` makes `live_at` nullable. A published exam with no start stays on the module list and is omitted from the personal routine. Take exam stays closed. A dated publish still enforces the five-minute gap against other dated exams in the module. | Step 2b |
| Purchase | Resolved in step 3. Flyway `V32` adds prices, `live_exam_orders`, and `live_exam_grants`. A verified notice grants access once. Take exam, the question PDF, and the routine accept a roster row or a purchase grant. Automated tests use a fake gateway. | Step 3 |
