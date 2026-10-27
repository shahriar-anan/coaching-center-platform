# Exam site UI — existing feature scope

This is the live-exam system the API already implements, plus the account system those four roles share. Expected pages are the screens that system needs. They are planning names, not routes that exist today, unless the details say the page is already in `web/`.

Read this before drawing the exam site. Course and book admin screens already exist and stay as they are. The older exam-module API is superseded. In-app notices are not built.

Roles: Student, Master Admin, System Admin, Instructor. A visitor is anyone with no account. The public catalog does not require login.

## What the website already has

| Surface | Route | Who can open it |
|---|---|---|
| Login (email or phone, plus password) | `/login` | All roles. A student session is sent to `/purchase/result`. Staff land in the admin shell. |
| Activation for an admin-created account | `/activate` | Student, System Admin, Instructor |
| SSLCommerz return | `/purchase/result` | Student. Polls the order status. |
| Exam name stub | `/live-exams` | Anyone. Shows the `name` query only. |
| Admin shell | `/students`, `/courses`, `/books`, `/book-orders` | Master Admin, System Admin, Instructor (the shell does not yet special-case Instructor) |
| Payments and admins | `/payments`, `/admins` | Master Admin only, in the nav |

Bangla and English chrome, and Noto Sans Bengali, are already loaded. There is no register page, no Google button, no forgot-password page, and no live-exam authoring, sitting, or marking screen.

## Accounts

| Feature | Expected pages | User type | Details |
|---|---|---|---|
| Self-registration | Register | Student | `POST /api/v1/auth/register` takes name, phone (`+8801` and nine digits), email, and password. The account is active immediately. There is no SMS OTP. Email verification does not block login. No page exists yet. |
| Login and session | Login (exists) | Student, Master Admin, System Admin, Instructor | `POST /api/v1/auth/login` accepts email or phone. The website keeps the access token in memory and the refresh token in an httpOnly cookie. Logout and refresh exist. |
| Google sign-in | Login, plus a set-phone page | Student | `GET /api/v1/auth/google` starts the redirect. A verified Google email links to an existing student. A new Google student has no phone until `POST /api/v1/me/phone`; `GET /api/v1/me` returns `phoneRequired`. A password is optional for a Google-only account. Google does not sign in an admin or an instructor. No button exists yet. |
| Email verification | Verify email | Student | `POST /api/v1/auth/verify-email` and resend. Login works before the email is verified. |
| Forgot and reset password | Forgot password, Reset password | Student, Master Admin, System Admin, Instructor | Email reset when SMTP is configured. No actor sets, sees, or resets another user's password. The Master Admin account cannot be reset by anyone else. No pages exist yet. |
| Change own password | Account | All signed-in roles | `POST /api/v1/auth/change-password`. The current password is required. |
| Own profile | Account | All signed-in roles | `GET /api/v1/me` returns id, phone, email, role, status, name, student code, and `phoneRequired`. A student may update their own display name. Email and the Master Admin name stay as created. |
| Activate an invited account | Activate (exists) | Student, System Admin, Instructor | Admin-created accounts start pending, with no password. The one-time code is entered on `/activate`. The creator never types the password. |
| Create a student | Students, New student (both exist) | Master Admin, System Admin | Name, phone, email. No password field. Reissue the activation code. Change status. Search the list. |
| Delete a student | Students (exists) | Master Admin | System Admin receives 403. |
| Create a System Admin | Admins, New admin (both exist) | Master Admin | Same activation path as a student. List, status, reissue code, and delete are Master Admin only. Nothing creates, edits, deactivates, or deletes the Master Admin. |
| Create an instructor | Instructors, New instructor | Master Admin | `POST /api/v1/instructors`. Same activation path. The directory and the assign picker are specified under "In the UI now, API later". The instructor signs in on `/login` after activation. |
| Audit log | Audit log | Master Admin | `GET /api/v1/audit-log`. No page exists. |

## Public catalog

Anyone can read a published category, module, and exam. Drafts stay off this read. An archived category hides its whole branch. The payload has titles, descriptions, rules, paper language, schedule, duration, and price. It has no questions, no correct answers, and no PDF.

| Feature | Expected pages | User type | Details |
|---|---|---|---|
| Category tree | Home, Category | Visitor, Student | Unlimited depth. Both Bangla and English titles. `GET /api/v1/public/live-exam-categories`. |
| Module shop | Category, Module | Visitor, Student | One category per module. Both titles and descriptions. Price may be null. `GET /api/v1/public/live-exam-modules` and `GET /api/v1/public/live-exam-modules/{id}`. |
| Exam list on the module | Module | Visitor, Student | The list is the routine: title, `liveAt`, `endAt`, type (`MCQ` or `WRITTEN`), paper language, price. A null `liveAt` means the date is not announced. Past, live, and upcoming rows all stay on this list. `GET /api/v1/public/live-exam-modules/{id}/exams`. |
| Exam offer | Exam | Visitor, Student | Title, description, rules, duration, price, schedule. Questions stay hidden. `GET /api/v1/public/live-exams/{id}`. |
| Site language | Chrome on every page | Visitor, Student, Master Admin, System Admin, Instructor | The existing locale switch covers shop copy and, on the question page, the timer, save, submit, and marks labels, plus title, description, and rules. `paperLanguage` (`BN` or `EN`) only picks the typeface for the question body. It does not translate the paper. |

## Purchase

Checkout is SSLCommerz. Only the Master Admin can mark an exam order paid by hand, from the pending orders list. The System Admin sees the list read-only (decided 2026-10-26, see `decisions.md`, "Staff roles"). Course and book bKash/Nagad screens are a different system.

| Feature | Expected pages | User type | Details |
|---|---|---|---|
| Buy one exam | Exam, then the gateway, then Payment result (exists) | Student | `POST /api/v1/live-exam-orders` with the exam id. The response carries `redirectUrl`. A grant covers that exam only. Buying again after a grant returns "already have access". |
| Buy a whole module | Module, then the gateway, then Payment result (exists) | Student | Same call with the module id. The grant covers exams already in the module and exams published in it later. |
| Payment result | Payment result (exists at `/purchase/result`) | Student | The gateway returns the browser here. The page polls `GET /api/v1/live-exam-orders/status`. Status is `PENDING`, `PAID`, `FAILED`, or `CANCELLED`. A failure does not grant access. |
| Cancel an open order | Exam or Module, before redirect | Student | `POST /api/v1/live-exam-orders/{id}/cancel`. |
| Price | Module editor, Exam editor | Master Admin | Nullable price on the module and on the exam. System Admin can edit the rest of the record and receives 403 if the body includes `price`. A null price cannot be checked out. |

Access to sit an exam is a Master Admin roster row or a paid grant. The public page does not check either.

## Student home, routine, and sitting

A signed-in student also has a grant-filtered read. `GET /api/v1/live-exam-modules` and the exams under a module return published rows the roster or a grant allows. Questions are still absent until Take exam.

| Feature | Expected pages | User type | Details |
|---|---|---|---|
| My modules | Dashboard | Student | Granted modules only. A module grant and a single-exam grant both qualify. An exam grant does not open the other exams in that module. |
| Personal routine | Dashboard, Routine | Student | `GET /api/v1/me/exam-routine`. Published granted exams whose window has not ended, soonest first. An exam with no `liveAt` stays on the routine, last, with a blank date and no sitting (decisions.md, "Product", line 10). Reading it does not start a sitting. |
| Live list | Dashboard | Student | The same routine, limited to exams whose server window is open now. The client brief's separate "live" block has no extra endpoint. |
| Take exam | Exam, then Question paper | Student | `POST /api/v1/live-exams/{id}/take` creates the live sitting only inside the shared window. The server clock decides. Before `liveAt`, after the end, or with no date, the call stays closed. |
| MCQ paper | Question paper | Student | After Take exam, `GET /api/v1/live-exams/{id}` adds stems and options while the sitting is open, and again for review after it ends. `PUT /api/v1/live-exams/{id}/answers` replaces the full selection and can be called until the deadline. |
| MCQ submit and score | Question paper, Result | Student | `POST /api/v1/live-exams/{id}/submit` can return the score early. The sitting stays open until the deadline, and answers can still change. The deadline posts saved answers with no submit call. A blank paper scores zero. A wrong option adds nothing. There is no negative mark. |
| MCQ review | Result | Student | `GET /api/v1/live-exams/{id}/result`. The chosen option, the correct option, and the explanation wait until the sitting ends. |
| One later MCQ attempt | Exam | Student | `POST /api/v1/live-exams/{id}/retake` only when the student never tapped Take exam during the window. One attempt, personal timer, off the live leaderboard. A student who sat the live paper cannot start it. |
| Written paper | Question paper | Student | One PDF, shown only during that sitting from `GET /api/v1/live-exams/{id}/question-pdf` (inline, no public URL). The page draws the watermark from the student's name and id. There is no download control in the product rules. |
| Written answer | Question paper | Student | `POST /api/v1/live-exams/{id}/answer-pdf`. PDF only, 80 MB ceiling, latest file replaces the previous one, allowed until the deadline. No file at the deadline is failed to submit and scores zero. |
| Written retake | — | Student | The retake call returns 409 for a written exam. No page should offer it. |
| Own result | Result | Student | MCQ score is available when the paper posts. Written marks, comment, and the evaluated PDF stay hidden until the Master Admin publishes. The student never sees who marked the paper. `GET /api/v1/live-exams/{id}/evaluated-pdf` returns the file only after publish. |
| Leaderboard | Leaderboard | Student | `GET /api/v1/live-exams/{id}/leaderboard`. Empty until five minutes after the window. Live sittings only. Highest score first, ties share a rank. Names are public. A written row waits for publish, except a failed-to-submit zero, which stays on the board. The later MCQ attempt stays off it. |

## Catalog authoring

Create, edit, and final save are shared by Master Admin and System Admin. Publish is the Master Admin's only (decided 2026-10-26). Delete and archive are Master Admin only. Each edit of a published record returns it to draft. Publishing an exam with a start time enforces a five-minute gap against the other dated exams in that module. An exam with no `liveAt` is skipped by that check. Setting or clearing `liveAt` returns the exam to draft.

| Feature | Expected pages | User type | Details |
|---|---|---|---|
| Categories | Categories, Category editor | Master Admin, System Admin | `GET/POST/PATCH /api/v1/live-exam-categories`. Both titles. Position. Parent for a subcategory. The staff tree includes drafts. |
| Delete or archive a category | Category editor | Master Admin | Archive hides the branch from students, including an exam opened by id. A posted result stays readable. System Admin receives 403. |
| Modules | Modules, Module editor | Master Admin, System Admin | Both titles and descriptions. One category. Status `DRAFT`, then final save, then `PUBLISHED`. `accessMode` stays `MANUAL`. |
| Delete or archive a module | Module editor | Master Admin | System Admin receives 403. |
| Exams | Module editor, Exam editor | Master Admin, System Admin | `MCQ` or `WRITTEN` on the same module. Both titles, descriptions, and rules. `paperLanguage` is `BN` or `EN`, required on create, editable until `liveAt`. Duration is required. Pass percent is required before publish. `liveAt` may be null until the date is announced. |
| MCQ questions | Exam editor | Master Admin, System Admin | Stems, options, one correct option, marks, explanation. The MCQ maximum is the sum of the question marks. |
| Written question PDF | Exam editor | Master Admin, System Admin | One PDF on the exam. The written maximum is stored on the exam, not summed from questions. |
| Publish an exam | Exam editor | Master Admin, System Admin | Final save, then publish. A published exam stays published after its window ends. This publish is the paper going live in the catalog. It is not the written-result publish below. |

## Roster and instructor assignment

| Feature | Expected pages | User type | Details |
|---|---|---|---|
| Roster grant | Module, Roster | Master Admin | Search by email, phone, or name. Mark one student. Paste email and phone lines. Remove a row. `GET/POST/DELETE` under `/api/v1/live-exam-modules/{moduleId}/roster`. System Admin receives 403. Removing the row after Take exam leaves that result readable. |
| Assign one instructor | Module, Assign instructor | Master Admin | `POST /api/v1/live-exam-modules/{moduleId}/instructor` with `instructorId`. One instructor per module. A second assign conflicts. System Admin receives 403. The instructor account must already exist. |

## Marking and written publish

| Feature | Expected pages | User type | Details |
|---|---|---|---|
| Assigned modules | Instructor home | Instructor | `GET /api/v1/live-exam-modules` returns only modules assigned to that instructor. |
| Submission list | Module submissions | Instructor, Master Admin, System Admin | `GET /api/v1/live-exam-modules/{moduleId}/submissions`. Each row has student name, status, score, pass, whether an answer PDF and an evaluated PDF exist, evaluator name, and `published`. The instructor is limited to assigned modules. System Admin can read the list and the evaluator name. |
| Open one submission | Submission | Instructor, Master Admin, System Admin | `GET /api/v1/live-exam-participants/{id}`. |
| Download the answer PDF | Submission | Instructor, Master Admin | `GET /api/v1/live-exam-participants/{id}/answer-pdf`. System Admin cannot download it. |
| Save a draft mark | Submission | Instructor, Master Admin | `POST /api/v1/live-exam-participants/{id}/evaluation` with marks, an optional comment, and the marked PDF. The screen should say the save is a draft. A later save clears publish until the Master Admin publishes again. System Admin receives 403. |
| Publish a written result | Submission | Master Admin | `POST /api/v1/live-exam-participants/{id}/publish`. Requires a draft mark. Instructor and System Admin receive 403. Until this call, the student does not see marks, comment, or the evaluated PDF, and the row stays off the leaderboard. |
| Staff leaderboard | Leaderboard | Instructor, Master Admin | The same leaderboard call. A staff reader with evaluate permission can open it for an assigned module. |

The participation screen is specified under "In the UI now, API later". The four counts are sitting, submitted, failed to submit, and written drafts waiting for publish.

## In the UI now, API later

These screens are in [stitch-prompt.md](stitch-prompt.md). The API does not serve them yet.

| Feature | Expected pages | User type | Details |
|---|---|---|---|
| Notices | Dashboard bell, Notices | Student | In-app only. One reminder 1–2 days before a granted exam, a notice when that student's MCQ score posts, and a notice when the Master Admin publishes that written result. The student can mark one read or mark all read. The row names the exam and that student's own marks. No other student, no phone number, no examiner, no compose box, no email, SMS, or push. |
| Instructor directory | Instructors, Assign instructor | Master Admin | Search by name, phone, or email. Filter by active, pending activation, and unassigned. Each row shows the one module, or none. Create is name, phone, and email. A pending row can reissue the activation code through `POST /api/v1/instructors/{id}/activation-code`. The module assign action opens this list as a picker. System Admin has no page. |
| Participation | Participation, and the counts on Overview and Instructor home | Master Admin, System Admin, Instructor | Per exam: sitting, submitted, failed to submit, and written drafts waiting for publish. Search, filter by module, type, and date, and sort. A count opens the submission list already filtered. An instructor sees assigned modules only. MCQ has no drafts column. This is a read of participants, not a new analytics product. |

| Gap | What the product asked for |
|---|---|
| Status pills | For a student who already has access. The window and the participant row are enough to derive them later. The client mockup already shows them, and the Stitch prompt keeps them. |

## Leave out of this UI

| System | Why |
|---|---|
| Courses, books, course content, book orders | Admin pages already exist. They are not part of the exam site. |
| Course and book payments | Manual bKash/Nagad verification on `/payments`. Exam checkout does not use that screen. |
| Old exam modules, question bank, and catalog exam attempts | Superseded API under `/api/v1/exam-modules`, `/api/v1/question-banks`, and `/api/v1/catalog/exam-modules`. Those purchases do not grant a live exam. |
| Mobile app | This delivery is the website. |
