# Student purchase, access and LMS plan, version 2

Status (26 Oct): phase 2 is mostly merged. Section 9.1 lists what remains. Written for the integrator and the agents that build the fixes. Version 2 applies the decisions made on 25 Oct, and replaces version 1 (the audit findings stay, with their new status).
Checked against: `catalog-acquisition` at `d6506c9` (26 Oct). Status was checked in code. The API suite passed on `18727c6` (363 tests). The web checks passed on `d6506c9` (100 files, 587 tests). The app was not run in a browser.

## 1. Verdict

The student site already has most of what a purchase-and-exam LMS needs: door, category and subcategory browse, module page with price and exam list, exam offer page with every owned, unowned and undated state, purchase sheet, payment result with polling, set-phone, order history, receipt, routine, my modules, results, leaderboard, notices with a bell and a tab badge, and written results with comment and marked PDF.

The gaps that remain after the decisions:

1. **Per-exam selection cannot buy several exams at once.** Decided: a bundle order (D1). It needs a schema change.
2. **The student cannot ask what they own.** The UI probes staff-shaped endpoints and reads a 404 as "not owned" (S-003). Decided: a student access endpoint and an `access` object.
3. **Notices do not match the API.** The labels are wrong (S-002). New kinds are missing: the 1-hour reminder, purchase granted, exam open, exam published, and staff announcements (S-004, S-017, S-020).
4. **Admins cannot grant, revoke, or confirm access.** There is no API for any of it (S-019).
5. **MCQ correct answers are shown to students after their own deadline, with no release control** (S-018).
6. **Account deletion ignores paid orders** (S-021). **System Admins can suspend students**, which should be Master Admin only (S-022). **Instructors have no suspend or delete route** (S-023).

Mail is configured, so the mail warnings from version 1 are closed (S-006, S-007).

**Status (26 Oct):** items 2 to 6 are merged on main. Item 1, the bundle order (D1), is not built. Section 9.1 has the rest.

## 2. Decisions taken (25 Oct)

| # | Topic | Decision |
|---|---|---|
| D1 | Several exams in one payment | Bundle order (`examIds[]`), one payment, one grant per exam. Needs schema change V39 (section 7). |
| D2 | New in-app notice kinds | Yes, see section 6.2. |
| D3 | Mail | Configured. Receipt copy keeps the email sentence. |
| D4 | Config status page | Yes, Master Admin page `/staff/system-status`, names only. |
| D-d5 | Mixed package on both doors | Yes. |
| D-d6 | Reminders | 48-hour `EXAM_REMINDER` stays. New in-app 1-hour `EXAM_STARTING_SOON`. The hour-before mail stays skipped (S-016). |
| D-d7 | Retire `StagingExamSeed`'s exam part | Yes. |
| D-d8 | Replace the old demo catalog | Yes. |
| D-d9 | Live window | 30 minutes. |
| D-d10 | Content review | Yes. A reviewer still needs a name. |
| D-d11 | Test student password | One env password. |
| R-1 | **Refunds** | **None.** No refund in the site or the API. |
| R-2 | **Removing access** | **Master Admin only**, for every account type: students (revoke a purchase or a manual grant), system admins and instructors (suspend and restore, and remove instructor assignments). |
| R-3 | **Granting access** | **Master Admin and System Admin** can grant a student access to a module or an exam, by hand, with a reason. |
| R-4 | **MCQ correct answers** | **Released per exam by Master Admin or System Admin.** Until released, students never see correct options or explanations, not even after their own deadline. |
| R-5 | **Announcements** | **Master Admin only.** Sent to all students or to the students with access to one module. They appear in the student's notice box and bell. |
| R-6 | **Export and marksheets** | **None.** Downloads only: staff download the answer PDFs students submitted for written papers (existing endpoint), and students download their marked PDF (existing). MCQ has no PDF and no export. |
| R-7 | **Late uploads for written papers** | Upload any time before the deadline, and each upload replaces the last. At the deadline the latest upload counts automatically, without a submit button. No upload means "could not finish" (`FAILED_TO_SUBMIT`, score 0). Uploads after the deadline are refused. |
| R-8 | **Stuck pending orders** | No filter. **Master Admin** checks the payment at SSLCommerz, and when it succeeded confirms it on the order. That marks the order paid and grants access. A failed payment can be rejected the same way. |
| R-9 | **Accessibility** | No separate test plan. Fix only when a problem is seen in testing. |
| R-10 | **Account deletion with paid orders** | **Not allowed.** A student with any paid order or any grant cannot be deleted. Master Admin can suspend instead. |
| R-11 | **Lower priority items** | None considered now. |

Defaults applied where the request did not say (object to any of these):
- Written publish stays manual (C3 bulk publish), with no scheduled publish.
- Questions are text only: no images, formulas or tables.
- No negative marking. The mockup's negative-mark stat is removed from the design, not shown.
- A bundle covers exams of one module (one module per order), so one `module_id` applies.
- A bundle has 1 to 20 distinct exams. Decided (26 Oct): 20 is above any real module (largest in the dev data: 7 exams), so it only stops malformed requests.
- A revoked grant does not refund money. Revoking is for mistakes or misuse. A revoked student can be granted again.
- The sitting jump grid stays at 44 px, the README's touch target (STU's decision, accepted by default; the P4 figure of 40 px is not used).

## 2a. Status update (26 Oct): what changed, and the decisions taken

Merged on `catalog-acquisition`: API-ACCESS (`edce9c1`), API-ADMIN (`0351a1e`, final `0351a1e`), API-NOTICE (`28fc65c`), the staff marked-PDF read (`7597850`), PUR-FND (`cb33c16`), STU-LMS (`c228315`), STU-PUR (`4f47065`), MRK (`d42342b`). The full API suite passed with 352 tests before the web merges.

Changed after the merges, on main:
- **S-016, the one-hour-before mail is off** (`64ac138`): `app.exam-mail.hour-before.enabled`, default off, set from `EXAM_MAIL_HOUR_BEFORE_ENABLED`.
- **S-004, a paid module order writes `PURCHASE_GRANTED`** (`8e247b9`): the module-level notice from V40. This closes the gap A2 reported.
- **S-005, `GET /api/v1/system-status`** (`ddec462`): the Master Admin's seven configuration checks, as names and yes or no only.
- **Admin screens: the access rows carry `grantId`, and the staff exam view carries `answersReleasedAt`** (`02481d5`). Without them, the revoke and the release switch couldn't be built.
- **S-028 and the unused exports** (`1223356`), committed after being left uncommitted earlier.

Decisions taken (26 Oct), for the items the client did not decide:
- **44 px jump grid:** kept at 44 px (the README's touch target).
- **Announcement audience `MODULE`:** students with a roster row or an active grant in that module, revoked grants excluded (API-ADMIN's reading).
- **`RETAKE_AVAILABLE`:** skipped for this phase. It has a label on the student side and no API writer.
- **Copy, where the copy guide and the brief disagree:** the brief's wording is used: "দাম এখনো নির্ধারণ হয়নি।", "শুধু এই পরীক্ষাটি খুলবে।", "এই মডিউলের বর্তমান ও ভবিষ্যতের সব পরীক্ষা খুলবে।", and "পেমেন্টে যান". `bangla-copy.md` should be updated to match in the Bangla pass.
- **Module purchase notices:** fixed, not deferred (`8e247b9`).
- **Hour-before mail:** off (`64ac138`).
- **Master Admin password:** change-password exists in the API and in the staff and student account pages.

Status, later on 26 Oct: the staff admin screens, AUT, OPS and the admin-gap fixes are merged. Still open: the bundle order (API-BUNDLE, not built; the student flag stays off), the seed data (SEED-DATA, not started), the QA run (QA-E2E, not started), and the admin placements (ADMIN-UI item 6, after the integrator's go). The full API suite passed on `18727c6` with 363 tests.

## 2b. Admin gaps (27 Oct), found by checking the plan against the code

These block the admin screens from being complete. Two agents work on them at the same time: ADMIN-API (API) and ADMIN-UI (web). Their files do not overlap.

| ID | Side | Gap | Found by | Fix | Owner |
|---|---|---|---|---|---|
| AG-01 | API | No way to list sent announcements. The admin can send one but cannot see the history. | `GET /api/v1/announcements` does not exist | `GET /api/v1/announcements` (Master Admin only): paged, newest first. Each row: id, both titles, audience, module, created time, recipient count. | ADMIN-API |
| AG-02 | API | The `answersReleasedAt` field has no assertion in any test. | grep of the test folder | Assert it: null when withheld, set after release, cleared after withdraw, null on the student view. | ADMIN-API |
| AG-03 | API | The `GET /api/v1/system-status` role gate has no integration test. | grep for the route in the tests | Integration test: Master Admin 200; System Admin, Instructor and Student 403. | ADMIN-API |
| AG-04 | API | `grantId` is asserted in the access tests but not for the roster row (null) or for a revoked grant (absent). | the access tests | Assert both. | ADMIN-API |
| AG-05 | UI | No admin component is on any page. The slots were not marked in the OPS and AUT branches. | grep of `web/src/app` | Place the components after OPS and AUT are merged, and mark the slots in those pages first. | ADMIN-UI (after the integrator says OPS and AUT are merged) |
| AG-06 | UI | The announcements page has no history list. | the page | A history list, using AG-01, with its empty and error states. | ADMIN-UI |
| AG-07 | UI | No check that each control matches the access matrix in section 5. | the tests | A test per page and role: a control the API would refuse is shown as information, not as a control. | ADMIN-UI |
| AG-08 | UI | Confirm dialogs are not checked for keyboard focus and escape. | the tests | Focus moves into the dialog on open and returns on close, and escape closes it, for revoke, confirm, reject, release and announcement. | ADMIN-UI |
| AG-09 | UI | The admin copy is not checked for raw error codes. | the tests | Every code the admin routes return has a message in both languages. An unknown code shows the generic message. | ADMIN-UI |
| AG-10 | UI | The status page must never show a value. | the test | A test with a fake secret in the response: the page does not show it. | ADMIN-UI |

Not gaps, checked and fine: the revoke route takes the reason as a query parameter, the instructor status and delete routes exist, the student delete refusal is 409 with its code, the marked-PDF staff read exists, and the password change exists in the API and in both account pages.

Still open after these two agents: the browser checks at 375 px and 1440 px, both themes and by keyboard, which need a person on a device.

## 3. Student flow inventory (unchanged from version 1, with status)

| Area | Screen (route) | Status | Notes |
|---|---|---|---|
| Browse | Home (`/`) | Present | Two doors, nudges, live row. |
| Browse | Door (`/mcq`, `/written`) | Present | Category cards, module search, upcoming strip. |
| Browse | Category (`/mcq/c/[id]`, `/written/c/[id]`) | Present | Subcategories through `children`. |
| Package | Module (`/mcq/m/[id]`, `/written/m/[id]`) | Polish | Ownership inferred (S-003). No selection (S-001, D1). |
| Exam | Exam offer (`/exam/[id]`) | Polish | All states present. Ownership inferred (S-003). |
| Purchase | Purchase sheet, payment result, set phone, order history, receipt | Present or polish | Receipt polish: name and code, print shell (S-008). |
| Owned | My modules, routine | Present | My modules relies on the list endpoint. |
| Results | Results hub, result page, leaderboard | Present | MCQ correct answers: release rule (S-018). Written: awaiting, then comment and marked PDF. |
| Notices | Notices page, bell, tab badge | Polish | Label mismatch (S-002). Missing kinds (S-004, S-017, S-020). |
| Account | Account page | Present | Name edit (errors S-009). Deletion is not offered for paid students (R-10). |

## 4. Findings

Severity: **Blocker** stops the flow, **Major** a required behaviour is missing or wrong, **Minor** polish or copy. "Owner" is the agent in section 9.

| ID | Sev | Where | Finding | Fix | Owner | Status |
|---|---|---|---|---|---|---|
| S-001 | Major | `LiveExamOrderService.readTarget` (`:210`) | One target per order. | Bundle order (D1). | A4 API-BUNDLE, STU-PUR | Open: API-BUNDLE (not built) |
| S-002 | Major | `web/src/app/(site)/notices/page.tsx:31`; `messages/{bn,en}/notices.ts:11` | UI expects `REMINDER`, `MCQ_RESULT`; API sends `EXAM_REMINDER`, `MCQ_SCORE` (`StudentNoticeWriter.java:20-24`). | Map the real kinds. Remove the old keys. | STU-LMS | Done (STU-LMS) |
| S-003 | Major | `exam-page.tsx:69,82,92,118`; `catalog.ts` `useMyExam`, `useMyModule` | Ownership inferred from 404s. A 5xx reads as "not owned". Every unowned exam logs a 404. | `GET /api/v1/me/live-exam-access` and an `access` object on public responses. Remove the probes. | A1, STU-PUR | Done (A1 and STU-PUR) |
| S-004 | Major | `StudentNoticeWriter.java:20-24` | Only three kinds. | New kinds in section 6.2. | A2 | Done (A2) |
| S-005 | Major | `LiveExamOrderService.java:74-75` | No public config status. Staff cannot see why checkout is off. | `GET /api/v1/public/exam-site/status`, and `/staff/system-status`. | A1, STAFF-ADMIN-UI | Done (A1 and STAFF-ADMIN-UI) |
| S-006 | Closed | `purchase.ts:36` | Receipt copy promises email. | Mail is configured (R, D3). Keep the email sentence. | none | Closed |
| S-007 | Closed | `api/.env` | Mail not in local env. | Mail is configured (user confirmed). | none | Closed |
| S-008 | Minor | `receipt-view.tsx` | No student name or code; print shell not hidden. | ST-3, ST-8. | STU-PUR | Merged (STU-PUR); check in QA-E2E |
| S-009 | Minor | `account/page.tsx` | Name save error copy. | ST-4. | STU-PUR | Merged (STU-PUR); check in QA-E2E |
| S-010 | Minor | `door-discovery.tsx` | Upcoming strip copy for module-only exams. | ST-2. | STU-PUR | Merged (STU-PUR); check in QA-E2E |
| S-011 | Minor | `door-discovery.tsx`, `catalog.ts` | Search ignores the door type. | ST-1. | STU-PUR | Merged (STU-PUR); check in QA-E2E |
| S-012 | Minor | `LiveExamPublicService.java:330` | No `categoryPath` on public modules. | Add it, same shape as G10. | A1 | Done (A1) |
| S-013 | Minor | `LiveExamOrderService.java:200-203` | Generic 503. | Covered by S-005. | A1 | Done (covered by S-005) |
| S-014 | Minor | `purchase-sheet.tsx` | Sheet lacks exam count and next date. | Optional. | STU-PUR | Open (optional, not planned) |
| S-015 | Minor | none | No in-app notice on failed or cancelled purchase. | None. The result page is enough. | none | Closed |
| S-016 | Major (skip) | `ExamMailService.java:80` | Hour-before mail runs every 5 minutes. | Gate behind `app.exam-mail.hour-before.enabled`, default off. | SEED-DATA | Done (`64ac138`) |
| S-017 | Major (new, decided) | `StudentReminderJob.java` | Only the 48-hour reminder exists. | Add `EXAM_STARTING_SOON`: one notice per student, exam and start time, when the exam starts within 60 minutes. The 48-hour one stays. In-app only. | A2 | Done (A2) |
| S-018 | Major | `LiveExamSittingService.java:218-220`, `questionViews` (`:388-404`) | Correct options and explanations are shown once the student's own deadline has passed, with no release control. A student can see them while others are still sitting. | Release per exam (R-4): flag `answers_released_at` on the exam. Until set, `correct` and `explanation` are null in every review. | A1 (read), A3 (release action) | Done (A1 and A3); check in QA-07 |
| S-019 | Major (new, decided) | none | No API to grant access by hand, revoke a grant, or confirm or reject a pending order. | Section 6.1 and 6.3. | A3 | Done (A3, `0351a1e`) |
| S-020 | Major (new, decided) | none | No staff announcement. | Section 6.4. | A2 (notice), A3 (send) | Done (A2 and A3) |
| S-021 | Major (new, decided) | `AccountService.java:155-160` | `delete` ignores paid orders and grants. | Block with `ACCOUNT_HAS_PAID_ORDERS` (409) when the student has any SUCCEEDED order or any grant (including revoked). | A3 | Done (A3) |
| S-022 | Major (new, decided) | `IdentityPermissionContributor.java:28` (`grantStaff`) | System Admins can suspend students. | `STUDENT_STATUS` to Master Admin only. System Admins keep read, create and update. | A3 | Done (A3) |
| S-023 | Major (new, decided) | `AccountController.java` | No `PATCH /api/v1/instructors/{id}/status` and no instructor delete. The staff people page calls `/api/v1/${kind}/${id}/status` for instructors. | Add instructor status (suspend and restore) and delete, Master Admin only. Fix the page call. | A3, STAFF-ADMIN-UI | Done (A3 and STAFF-ADMIN-UI) |
| S-024 | Minor (checked) | `LiveExamSittingService.java:325-339` | Written late-upload rule (R-7) already works: at the deadline the stored answer counts, and no answer gives `FAILED_TO_SUBMIT`. | Add tests for the deadline during an upload (F10 open item). Add the copy on the paper page. | A1 (tests), STU-LMS (copy) | Done (tests, `e20cde2`) |
| S-025 | Minor (checked) | `LiveExamEvaluationController.java:69` | Staff answer PDF download exists. | None. Recorded so nobody builds export. | none | Closed |
| S-026 | Minor (new) | `LiveExamOrderController.java:62-77` | The order list supports status and tran-id filters, but has no action to confirm or reject. | Covered by S-019. | A3 | Done (covered by S-019) |
| S-027 | Minor (raised by STU) | API upcoming exams (`LiveExamPublicService.upcoming`) | ST-2 needs a flag for an exam that is sold only inside its module. The upcoming row can't show the module-only label without it. | Add `moduleOnly: boolean` to each upcoming exam: true when the exam has no price of its own and its module is priced. The row then shows "শুধু মডিউলে কেনা যাবে". | A1 (API), STU-PUR (UI) | Done (A1) |
| S-028 | Minor (raised by STU) | `lib/api/client.ts` (`ApiError`); `lib/api/exam/account.ts` (`isNotDeployed`) | `ApiError` has no HTTP status. `isNotDeployed` matches the status text, which is fragile (ST-4). | Add `status` to `ApiError` in `client.ts`, and switch `isNotDeployed` to the status code (405 and bare 404 mean "not deployed"). | Integrator, Wave 0 (one small commit, no agent) | Done (`1223356`) |
| S-029 | Minor (raised by STU) | `web/src/components/ui/toast.tsx` (FND's file, now shared) | Still uses `text-[15px]`, which breaks the no-arbitrary-size rule. | Use `text-label` (15 px on the scale). | PUR-FND (shared `components/ui/**`) | Done (no arbitrary size left in toast.tsx) |

Checked and correct (do not re-audit):
- Purchase status words map to the public ones (`publicStatus`).
- A second order for an owned target is refused (`ALREADY_PURCHASED`, 409). A module grant blocks buying one of its exams.
- An unpublished or hidden target gives 404, and a missing price gives `PRICE_REQUIRED` (409).
- The gateway return and IPN verify amount, currency, store and tran id before granting.
- `phoneRequired` is in `/me`.
- A module grant covers exams published later (`LiveExamAccess.hasModule`).
- Written marks, comment and marked PDF are shown only after publish.
- The MCQ score notice is written on submit.
- The recheck job reads only pending orders, so paid orders never trigger gateway calls.

## 5. Who can do what (access matrix)

Grant and revoke apply to students. Account actions apply to all account types. "Master Admin" is `MASTER_ADMIN`. "System Admin" is `SYSTEM_ADMIN`. "Instructor" is `INSTRUCTOR`.

| Action | Master Admin | System Admin | Instructor | Student |
|---|---|---|---|---|
| Grant a module or exam by hand, with a reason (R-3) | yes | yes | no | no |
| Revoke a purchase or a manual grant (R-2) | yes | no | no | no |
| Confirm or reject a pending order (R-8) | yes | no | no | no |
| Release or withhold MCQ answers for an exam (R-4) | yes | yes | no | no |
| Send an announcement to students (R-5) | yes | no | no | no |
| Suspend or restore a student (S-022) | yes | no | no | no |
| Suspend or restore a system admin | yes | no | no | no |
| Suspend or restore an instructor (S-023) | yes | no | no | no |
| Remove an instructor from a module | yes | no | no | no |
| Delete a student (blocked when paid, R-10) | yes | no | no | no |
| Download a student's written answer PDF | yes | yes | only for modules assigned to them | no |
| See own access and buy | no | no | no | yes |

## 5a. Staff pages and features by role (checked 26 Oct)

Every staff action the API allows, the page it lives on, and whether a role can use it today. Checked by comparing the API's permission grants and routes with the web routes, the UI role table (`web/src/lib/auth/guards.tsx`), and each page. "Gap" rows are open; their owner is in the last column.

Roles: **MA** Master Admin, **SA** System Admin, **IN** Instructor. "info" means the page shows a sentence about who can do it, not a control.

| Page (route) | Feature | MA | SA | IN | Status |
|---|---|---|---|---|---|
| Home (`/staff`) | Counts, pending payments, 14-day exam agenda, first-run steps | yes | yes | own home (assigned work) | Built (AUT, MRK) |
| Categories (`/staff/categories`) | Create, edit | yes | yes | no | Built |
| | Delete | yes | info | no | Built |
| Modules (`/staff/modules`, `/[id]`) | Create, edit, final-save, publish, archive, copy an exam, search and status filter | yes | yes | read assigned | Built |
| | Delete, roster, assign or remove the instructor | yes | info | no | Built |
| Exam editor (`/staff/exams/[id]`) | Details, schedule, MCQ questions (add, paste many, delete), question PDF, publish with summary | yes | yes | no | Built |
| | Price | yes | read-only text | no | Built |
| | Delete | yes | info | no | Built |
| | **Release or withhold MCQ answers** | yes | yes | no | **Gap: not placed** (ADMIN-UI, item 6) |
| Results report (`/staff/exams/[id]/results`) | Summary and table, filters | yes | yes | written exams in assigned modules | Built (OPS) |
| | Publish all drafts | yes | info | no | Built (OPS) |
| Leaderboard (`/staff/exams/[id]/leaderboard`) | View | yes | yes | yes | Built |
| Participation (`/staff/participation`) | View, counts | yes | yes | yes | Built |
| Submissions and marking (`/staff/submissions/**`) | Open the answer PDF, upload the marked PDF, marks and comment, save a draft | yes | read | assigned modules | Built (MRK) |
| | Publish a written result | yes | info | no (draft only) | Built (MRK) |
| | **Open the marked PDF already uploaded** (G1) | yes | yes | assigned | **Gap: no button; the route exists** (ADMIN-UI, item G1) |
| Students list (`/staff/students`) | Search, create, reissue the activation code | yes | yes | no | Built |
| | Suspend or restore | yes | **shown, API refuses** | no | **Gap: hide for SA** (ADMIN-UI) |
| | Delete (blocked with paid orders, R-10) | yes | no | no | **Gap: the refusal message and the offer to suspend are missing** (ADMIN-UI) |
| Student page (`/staff/students/[id]`) | Name, exam history | yes | yes | no | Built |
| | **Access list: grant by hand** | yes | yes | no | **Gap: not placed** (ADMIN-UI, item 6) |
| | **Access list: revoke** | yes | info | no | **Gap: not placed** (ADMIN-UI, item 6) |
| | Suspend or restore | yes | **shown, API refuses** | no | **Gap: replace with the admin control** (ADMIN-UI, item 6) |
| | **Delete** | yes | no | no | **Gap: not placed** (ADMIN-UI, item 6) |
| Instructors (`/staff/instructors`) | Create, reissue the activation code | yes | no | no | Built |
| | **Suspend or restore, delete** (refused with marked work) | yes | no | no | **Gap: not placed** (ADMIN-UI, item 6) |
| System admins (`/staff/admins`) | Create, suspend or restore, delete, reissue the code | yes | no | no | Built |
| Payments (`/staff/payments`, `/[tranId]`) | List, summary, detail | yes | read | no | Built |
| Pending orders (`/staff/orders`) | List | yes | read | no | Built |
| | Confirm or reject with a reason | yes | info | no | Built |
| Announcements (`/staff/announcements`) | Send, history | yes | info | no | Built |
| Audit (`/staff/audit`) | Read | yes | no | no | Built |
| System status (`/staff/system-status`) | Configuration checks, names and yes or no | yes | no | no | Built |
| Account (`/staff/account`) | Profile, change password | yes | yes | yes | Built |

Every staff API route has a caller in the web, except the marked-PDF read (G1). The API routes of the older exam system (`exam-papers`, `question-bank`, `exam-files`) are not part of the exam site.

Not covered by the table: the browser checks at 375 px and 1440 px, both themes, and keyboard (QA-E2E).

## 6. Rules, and what each feature stores

### 6.1 Access by hand, revoke and pending orders

- **Grant by hand** (R-3): creates a grant with `source = MANUAL`, the student, the module or exam, `granted_by` and a reason of up to 500 characters. It is audited. It fails if the student already has an active grant for the same target.
- **Revoke** (R-2): sets `revoked_at`, `revoked_by` and a reason on the grant. It does not delete the grant or the order, so the audit trail stays. Every access check ignores revoked grants: `LiveExamAccess`, the purchase guard (`rejectOwned`), the access view, and the public `access` object.
- **Confirm a pending order** (R-8): for an `INITIATED` order, Master Admin confirms that the payment succeeded at SSLCommerz. The order becomes `SUCCEEDED`, the channel is `MANUAL-CONFIRMED`, the grant is created with `source = ORDER`, and the student gets a `PURCHASE_GRANTED` notice. Confirmation records who confirmed it and why. Reject sets the order to `FAILED` with a reason. Both are refused when the order is not pending (`ORDER_NOT_PENDING`, 409).

### 6.2 Notice kinds

| Kind | Who gets it | When | Source |
|---|---|---|---|
| `EXAM_REMINDER` | students with access | 48 hours before start (existing) | job |
| `EXAM_STARTING_SOON` | students with access | within 60 minutes of start, once per exam and start time (S-017) | job |
| `EXAM_OPEN` | students with access | the window opens, once | job |
| `PURCHASE_GRANTED` | the buyer, or the student granted by hand | when access is created | grant path |
| `EXAM_PUBLISHED` | students with access to the module | when an exam is published in that module | publish path |
| `RETAKE_AVAILABLE` | the student (optional) | when a retake opens | sitting path |
| `ANNOUNCEMENT` | all students, or students with access to one module | when Master Admin sends it (R-5) | announcement path |
| `MCQ_SCORE` | the student | on MCQ submit (existing) | sitting path |
| `WRITTEN_RESULT` | the student | on publish (existing) | evaluation path |

Labels live under `notices.kind.<KIND>`. `REMINDER` and `MCQ_RESULT` are removed. Every kind is idempotent by `(kind, message_key)`.

### 6.3 MCQ answer release (R-4)

- The exam carries `answers_released_at` and `answers_released_by`. It is off by default.
- Master Admin or System Admin can set and clear it, per exam, with an audit row.
- Students see `correct` and `explanation` on the review only when it is set and their sitting is over. Before that the review shows the selected options and the outcome, with the copy "উত্তর এখনো প্রকাশ হয়নি" when the answers are withheld.
- Written papers are not affected.

### 6.4 Announcements (R-5)

- Master Admin writes a title and a message in Bangla and in English. Either language may be left blank, and the page shows the one in the student's language, with the other as fallback.
- Audience: all students, or the students with access to one module. The send writes one `ANNOUNCEMENT` notice per student and an audit row. The notice is in the notice box and bell.
- An announcement cannot be edited or deleted after it is sent.

### 6.5 Written papers: late uploads (R-7)

- Upload is allowed until the deadline. Each upload replaces the previous one (existing).
- At the deadline, the latest upload counts as the submitted script, with no submit click needed (existing `post()` path).
- No upload at the deadline gives `FAILED_TO_SUBMIT` with score 0 (existing).
- An upload after the deadline is refused with `DEADLINE_PASSED` (existing).
- The paper page says this in one sentence (copy in `catalog`, STU-LMS).

### 6.6 Account deletion (R-10)

- A student can be deleted only when they have no `SUCCEEDED` order and no grant, active or revoked.
- Otherwise the API returns `ACCOUNT_HAS_PAID_ORDERS` (409). The page shows: "এই শিক্ষার্থীর পেমেন্ট বা অ্যাক্সেস আছে, তাই মুছে ফেলা যাবে না। অ্যাকাউন্ট নিষ্ক্রিয় করুন।" and offers suspend.

### 6.7 Suspend, restore and removal for each account type (R-2)

- Students, system admins and instructors: suspend and restore, Master Admin only.
- Instructors: remove from a module, Master Admin only (existing `LIVE_EXAM_ASSIGN`).
- Instructor delete: Master Admin only, blocked when the instructor has marked work, the same way as the student rule.

## 7. Schema change V39 (one migration, owned by A3)

Rules: never edit `V1`–`V38`. Every schema change is `V39`. A3 writes it, merges it first in Wave 1, and tells the integrator before merging.

| Table | Change | Reason |
|---|---|---|
| `live_exam_grants` | `order_id` nullable. Add `source` (`ORDER`, `MANUAL`, `BUNDLE`), `granted_by`, `reason`, `revoked_at`, `revoked_by`, `revoke_reason`. Drop the unique `order_id`. Replace the module and exam unique indexes with partial ones: one active grant per student and module, and per student and exam, where `revoked_at IS NULL`. | Manual grants have no order. Bundles give several grants to one order. Revoke keeps history. |
| `live_exam_orders` | Scope check allows `BUNDLE` (module_id still required). Method check unchanged. Add `confirmed_by`, `confirm_reason`, `rejected_reason`. | D1, R-8. |
| `live_exam_order_exams` (new) | `order_id`, `exam_id`, unique pair. | The bundle's exams. |
| `live_exams` | `answers_released_at`, `answers_released_by`. | R-4. |
| `announcements` (new) | `id`, `title_en`, `title_bn`, `body_en`, `body_bn`, `audience` (`ALL` or `MODULE`), `module_id` nullable, `created_by`, `created_at`. | R-5. |
| `student_notices` | Add `announcement_id` nullable. | Link the notice to its announcement. |

Audit rows (new `AuditRecorder` constants): `ACCESS_GRANTED`, `ACCESS_REVOKED`, `ORDER_CONFIRMED`, `ORDER_REJECTED`, `ANSWERS_RELEASED`, `ANSWERS_WITHHELD`, `ANNOUNCEMENT_SENT`, `USER_SUSPENDED`, `USER_RESTORED`.

New error codes: `ACCOUNT_HAS_PAID_ORDERS` (409), `ORDER_NOT_PENDING` (409), `GRANT_ALREADY_ACTIVE` (409). Existing codes are used otherwise.

## 8. Contracts (write these before Wave 1; web agents code against them with mocks)

- `GET /api/v1/me/live-exam-access` (student): `{ modules: [{ moduleId, via, orderId }], exams: [{ examId, moduleId, via, orderId }] }`. `via` is `ROSTER`, `MODULE`, `EXAM` or `MANUAL`. Paging only if one student can have more than 200 rows.
- `access` on signed-in public module and exam responses: `{ state, buyable, blockedReason }`. `state` is `NONE`, `MODULE`, `EXAM`, `ROSTER` or `MANUAL`. `blockedReason` is `null`, `PHONE_REQUIRED`, `PRICE_REQUIRED`, `ALREADY_HAS_ACCESS` or `CHECKOUT_OFF`.
- `GET /api/v1/public/exam-site/status`: `{ checkoutEnabled }`. No configuration values.
- Upcoming exams (`GET /api/v1/public/live-exams?upcoming=true`): each row gains `moduleOnly: boolean` (S-027). It is true when the exam has no price of its own and is sold only inside its module.
- `ApiError` (web) gains `status: number` (S-028). It is set from the HTTP response, so the client can tell "not deployed" (405, or a bare 404) from a missing resource.
- Bundle order: `POST /api/v1/live-exam-orders` with `{ examIds: [uuid, ...] }`. The response is an `OrderView` with `scope = BUNDLE`, `moduleId` set, `examId` null, and a new field `examIds: [uuid]` (API-BUNDLE adds it; the existing fields do not change). One payment, one grant per exam (`source = BUNDLE`, `order_id` set), and one `PURCHASE_GRANTED` notice per exam. Rules:
  - All exams belong to one module. Otherwise `VALIDATION_FAILED` (400).
  - Each exam is published and priced, and the same rules as one exam apply (`NOT_FOUND`, `PRICE_REQUIRED`).
  - If the student already owns any exam in the list, through a module grant, an exam grant or the roster, the whole request returns `ALREADY_PURCHASED` and nothing is charged.
  - The amount is the sum of the exam prices.
  - A second request with the same exam set reuses the open order. A callback for an exam already granted adds nothing.
- Master Admin and System Admin: `POST /api/v1/live-exam-grants` (manual grant), `DELETE /api/v1/live-exam-grants/{id}` (revoke, Master Admin only), `POST /api/v1/live-exam-orders/{id}/confirm` and `/reject` (Master Admin only), `PUT /api/v1/live-exams/{id}/answers-released` with `{ released }` (Master Admin and System Admin).
- Announcements: `POST /api/v1/announcements` (Master Admin only).
- Accounts: `PATCH /api/v1/instructors/{id}/status` and `DELETE /api/v1/instructors/{id}` (Master Admin only). Existing `students` and `admins` routes keep their paths, with `STUDENT_STATUS` narrowed to Master Admin.
- `GET /api/v1/live-exams/{id}/result` review: `correct` and `explanation` are null until `answers_released_at` is set and the sitting is over (the shape does not change).

Shape details are written in `current-api.md` under each agent's heading before the agent merges.

## 9. Agents and waves (at most three at a time, per `ws9-stages.md`)

### 9.1 Agents

| Agent | Branch | Worktree | Scope |
|---|---|---|---|
| **A3 API-ADMIN** | `ui/api-admin` | `F:\cc-wt\api-admin` | V39; manual grant, revoke, confirm and reject; answer release; announcements (send); account rules (S-021, S-022, S-023); the permission narrowing. |
| **A1 API-ACCESS** | `ui/api-access` | `F:\cc-wt\api-access` | Student access endpoint; `access` object; `status`; `categoryPath`; reads the answer release for the review; tests for the written deadline (S-024); `moduleOnly` on upcoming exams (S-027). |
| **A2 API-NOTICE** | `ui/api-notice` | `F:\cc-wt\api-notice` | Notice kinds (6.2); the 1-hour job; announcement notice rows (uses V39 after A3 merges). |
| **A4 API-BUNDLE** | `ui/api-bundle` | `F:\cc-wt\api-bundle` | Bundle order (D1) in `LiveExamOrderService`, `LiveExamOrderTx`, `live_exam_order_exams`. |
| **SEED-DATA** | `ui/seed-data` | `F:\cc-wt\seed-data` | Seed and cleanup; mail gate (S-016). Plan in `seed-data-plan.md`. |
| **PUR-FND** | `ui/pur-fnd` | `F:\cc-wt\pur-fnd` | Shared purchase components in `components/ui` (FND's files, FND approves). |
| **STU-LMS** | `ui/stu-lms` | `F:\cc-wt\stu-lms` | Notices, label keys, result review (answers withheld copy), written paper copy (R-7), my modules. |
| **STU-PUR** | `ui/stu-pur` | `F:\cc-wt\stu-pur` | Module and exam pages, purchase sheet, selection UI (after A4), receipt, probes removed. |
| **STAFF-ADMIN-UI** | `ui/staff-admin` | `F:\cc-wt\staff-admin` | Student access section (grant, revoke); pending orders with confirm and reject; exam answers switch; announcements (Master Admin); instructor suspend and delete; the deletion message; `/staff/system-status`. |
| **QA-E2E** | `ui/qa-e2e` | `F:\cc-wt\qa-e2e` | Test run on the local dev profile and the SSLCommerz sandbox. |

Staging is not used. A staging run needs the user's approval.

**Status (26 Oct):** merged: API-ADMIN, API-ACCESS, API-NOTICE, PUR-FND, STU-LMS, STU-PUR, STAFF-ADMIN-UI. Open: API-BUNDLE (Maven under lock `API`), SEED-DATA (Maven under lock `API`), QA-E2E (lock `QA`). The prompts are in `prompts/phase2/`. Two Maven or npm builds never run at once.

### 9.2 File ownership inside `api/`

| Agent | Owns | Must not touch |
|---|---|---|
| A3 | V39; `LiveExamAccess` (revoked filter); `LiveExamOrderService` (confirm, reject, manual grant only); a new `LiveExamGrantAdminController`; `AccountService.delete`; `AccountController` (instructor and student status); `IdentityPermissionContributor`; `AuditRecorder` constants | `LiveExamPublicService`, notice classes, `StudentReminderJob` |
| A1 | `LiveExamPublicService` (`access` and `categoryPath`), new `StudentAccessController`, `StudentLiveExamAccessService`, `ExamSiteStatusController`; the review read in `LiveExamSittingService.questionViews` | grant writes, notice classes, V39 |
| A2 | `StudentNoticeWriter`, `StudentInboxService`, `StudentReminderJob`, the notice hooks in grant, publish and sitting paths, the announcement notice writer | `LiveExamPublicService`, V39 |
| A4 | `LiveExamOrderService` (bundle path only), `LiveExamOrderTx`, new `LiveExamOrderBundle` classes | notice classes, V39 (uses A3's) |
| SEED-DATA | `DemoContentSeed*`, `StagingExamSeed` (exam part only), `ExamMailService` (gate only), seed keys in `application-*.properties`, `demo-seed.md`, `demo-cleanup.sql` | everything else |

Shared file rule: `current-api.md` gets one heading per agent. Only A3 writes V39.

### 9.3 Waves

| Wave | Agents | Starts when | Notes |
|---|---|---|---|
| 0 (integrator) | merge the open WS9 branches; write section 8 into `current-api.md`; write the prompt files; write the V39 spec (section 7) into A3's prompt; one commit for S-028 (`ApiError` status and `isNotDeployed`) | WS9 has no active agent | No build. |
| 1 | A3 API-ADMIN (V39 first, merged early), A1 API-ACCESS (merges after A3's V39, because it reads `answers_released_at`), A2 API-NOTICE (1-hour job first; announcement rows after V39 merges) | Wave 0 done | API tests, one build at a time under `API`. |
| 2 | SEED-DATA (mail gate first; catalog and accounts; grants and orders after V39; notices after A2), PUR-FND (stubs first), STU-LMS | A3 merged for SEED grants; A2 merged for SEED notices | `npm run build` under `FND` for PUR-FND; `STU` for STU-LMS. |
| 3 | A4 API-BUNDLE (after A3's grant merge), STU-PUR (after PUR-FND and A1), STAFF-ADMIN-UI (after A3 and A2) | the listed merges | `npm run build` under `STU` for STU-PUR and STAFF-ADMIN-UI, one at a time. |
| 4 | QA-E2E on the local dev profile | all waves merged | Holds the lock for the run, then releases it. |

Merge order: as each wave finishes, `--no-ff`, with the gate below. A wave merges when every agent in it has sent READY TO MERGE and the checks pass.

### 9.4 Integrator gate after each merge

1. `git merge --no-ff` the branch into `catalog-acquisition`.
2. API: touched `*IT` classes, then the full suite from clean, alone, under the lock. The full suite passed on `18727c6` (363 tests, no failures). A failure in a later run is for that run's agent to explain before the merge.
3. Web: `npm run lint` (0 errors), `npx tsc --noEmit`, `npm test` (single process), `npm run build` under the lock.
4. Spot-check each claimed item in code.
5. Record the wave in this file's status line.

### 9.5 Prompt files

Each new agent gets `docs/exam-site/plans/ui-delivery/prompts/phase2/agent-<NAME>.md`, in the WS9 format: setup (worktree command), reading list, owned files, items with finding IDs, rules (one commit per finding ID, attribution, lock names), done, ready message.

The three remaining agents have files in the same folder: `agent-API-BUNDLE.md`, `agent-SEED-DATA.md` and `agent-QA-E2E.md`.

## 10. Acceptance checks (QA-E2E, local dev profile)

QA-E2E runs these on the seed in `seed-data-plan.md`. The seed's own checks use the IDs `SQ-01` to `SQ-14`, so the two lists do not clash.

- QA-01: a visitor sees the three packages (`b1`, `b2`, `b3`), their exams and prices. Buy goes to login and back.
- QA-02: 5104 (no phone) sees the disabled buy button and the set-phone link.
- QA-03: 5101 owns package `b3`. Its module and exams show "অ্যাক্সেস আছে" with no buy buttons. No 404 probes.
- QA-04: 5102 owns `e4`. Its buy button is hidden. Package `b1` is still buyable.
- QA-05: with checkout off, buy buttons are disabled and the config notice shows.
- QA-06: after a sandbox payment, "এখন আপনার জন্য চালু" shows, and `PURCHASE_GRANTED` appears in the bell.
- QA-07: a student's MCQ review shows no correct options or explanations until Master Admin releases `e1`. Released answers then show.
- QA-08: 5101's written `e13` shows the comment and marked PDF. In the staff submissions list, 5005 on `e7` shows `FAILED_TO_SUBMIT` ("could not finish"). (5005 has no login, so this is a staff check.)
- QA-09: each notice kind shows the right label in both languages.
- QA-10: each page at 375 px and 1440 px, both themes, both languages, no horizontal scroll.
- QA-11: no raw colours, no `text-[..]`, no `text-sm` in changed files.
- QA-12: Master Admin grants `e6` to a student by hand; the student sees it; revoke removes it; a second grant works.
- QA-13: Master Admin confirms the stuck order `DEMO-STUCK-E9-5001` (seed, student 5001) after checking the payment; access is granted and the notice appears. Rejecting a second pending order works.
- QA-14: System Admin cannot suspend a student or delete an instructor (403). Master Admin can.
- QA-15: a student with paid orders cannot be deleted (409 `ACCOUNT_HAS_PAID_ORDERS`); a student with none can.
- QA-16: a written paper with an upload at the deadline is submitted without a submit click; with no upload it shows "could not finish"; an upload after the deadline is refused.
- QA-17: an announcement from Master Admin reaches all students (or one module's students) in the notice box and bell, once each.
- QA-18: `EXAM_STARTING_SOON` for `e3` reaches 5105 once, and a second scan adds nothing.

Accessibility (R-9): no test plan. If QA-10 or any other check shows a keyboard, focus or label problem, fix it in the same agent that owns the page, and record it in that agent's report.

## 11. Open items (26 Oct)

- **Bundle flag.** `NEXT_PUBLIC_BUNDLE_ORDERS` stays off until API-BUNDLE is merged and QA-E2E has passed the bundle path. Turning it on for the client demo is the user's decision.
- **`dd9fd63` (moduleOnly on every exam payload): decided (26 Oct), dropped.** Its rule (`price == null`) ignored whether the module is priced, it conflicted with main, and the web reads `moduleOnly` only on upcoming rows, which main covers. Branch `ui/w9-api` and its worktree were removed.
- **Database for the seed and QA: decided (26 Oct), `coaching_center_qa`.** Its 12 users are all `@example.com` accounts from the 25 Oct demo and staging seeds, and its schema is at V38 (Flyway applies V39 and V40 on start). Never `coaching_center_dev` (5,437 users, 617 exams, 114 orders).
- Client content review (D-d10): a reviewer's name is needed.
- Bangla copy: unify the "price not set" wording to the brief's (section 2a); update `bangla-copy.md`; a Bangla reviewer for the new strings.
- Admin placements and the staff gaps in section 5a: ADMIN-UI, prompt `prompts/admin-gaps/agent-ADMIN-UI-placement.md`.
- F9 (drag reorder): needs the integrator's approval to add `@dnd-kit/core` and `@dnd-kit/sortable`.
- Browser checks at 375 px and 1440 px, both themes, and by keyboard: QA-E2E. Any check it cannot run is listed as not run.
- Client environment checks: SSLCommerz sandbox end to end, mail, `DEV_EXPOSE_EMAIL_CODES` off, `SEED_*` off in production. Not checked in this round.
- The students list is capped at 100 modules for `my-modules`. Acceptable for launch.

The defaults in section 2 are applied unless objected to. The purchase flow is not yet run in a browser; QA-E2E does that.

## 12. Links

- Seed data: [seed-data-plan.md](seed-data-plan.md). Its dependencies on this plan are the V39 columns and A2's notice kinds.
- Previous version: the audit findings S-001 to S-016 keep their numbers. S-017 to S-026 are new in version 2.
