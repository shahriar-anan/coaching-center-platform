# Offline admission: plan

Status: plan, reviewed, 13 November 2026. Nothing is built. Decisions are Shahriar's, from the conversation on 13 November 2026.
Choices in section 1.2 were made while planning; say if any is wrong. Every open question is answered (section 1.3). Section 12 lists what the review changed.

## 0. Build log

Each stage adds: what was built, where it differs from this plan, and the test results (command, counts, failures and how they were fixed).

### Stage 1: schema and course-end access (built, gate passed, committed 9b39e34)
- V81 to V84, seven entities and repositories, the OFFLINE grant source, `access_ends_at`, the cap in exam and package access, seven permissions.
- Gate: OfflineSchemaIT 4/4; AccessExpiryIT 18/18 (five new cap cases); LiveEnrolmentListIT 9/9; PackageEnrolmentRulesIT 6/6. Full API suite 194 classes, 1056 tests, all passing.

### Stage 2: desk creation and the student's form API (built; backend only)
- Desk: `POST /api/v1/students` with `offlineAdmission: true` creates the student and an AWAITING_FORM admission in one transaction.
- Student API: `GET /api/v1/me/admission` (404 with no open admission), `PUT .../form` (partial saves), `PUT .../photo` (the admission photo is the profile photo, plan side effect 10), `POST .../submit` (validates every field, keeps a submission row, notifies each admin with ADMISSION_SUBMITTED or ADMISSION_RESUBMITTED).
- Gate: `OfflineAdmissionGate` refuses take, retake and order creation with ADMISSION_PENDING while the admission is pending (plan 4.5).
- Differences from the plan: the session flag (side effect 26) is not added yet; the web reads My admission state from GET /me/admission instead. Staff endpoints (queue, setup, onboarding) are Stage 3.
- Gate results: OfflineAdmissionFormIT 14/14 (the test's own setup rows were fixed in the test); take, order and retake regressions LiveSittingIT 38/38, LiveExamIT 11/11, LiveExamPurchaseIT 16/16, WrittenRetakeGrantIT 3/3, DiscountOrderIT 8/8.
- Full suite on a fresh database (coaching_it_stage2): 195 classes, 1070 tests, 1 failure. StaffAssignmentWatcherIT.aLaterChangeWritesItsRowsAndNoticesOnceWithTheActor fails only in the full run and passes alone on a fresh database, so it depends on what ran before it. Not yet explained; the test needs isolation.
- Test-hygiene finding: StaffPayAnalyticsIT is not transactional and uses fixed 2031 dates, so a second run on the same database doubles its data (1000 against 500). Run the suite on a fresh database each time, and the test needs isolation.

## 1. What is being built

A student who walks into the centre gets an account at the desk, activates it with the existing OTP, and fills in an admission form on their phone. Staff check the form and prepare the admission: batch, monthly fee, first due date, course end date, and the online exams and packages included. Only the Master Admin can press the final button that onboards the student. After that, the student has a **My admission** page with their batch, the batch routine PDF, the exams and packages they can use, what they have paid, and what is due next. They get an email when admitted, and reminders before and after each monthly due date.

### 1.1 Decisions (Shahriar, 13 November 2026)

| # | Decision |
|---|---|
| D1 | Staff = the System Admin role. Staff and the Master Admin can both create the account at the desk, check the form, set the payment and the exams or packages, record payments, print the form, and upload the signed copy. |
| D2 | Only the Master Admin can onboard (the final button). Staff mark an admission "ready" and the Master Admin is told. |
| D3 | Only accounts created at the desk as an offline admission see the form. Online sign-ups and desk accounts made before this feature never see it. |
| D4 | Until onboarded, the student sees only the form, or an "Admission under review" page. No catalog, no buying, no exams. |
| D5 | The Master Admin (or staff) can send the form back with a reason. The student sees the reason, fixes the form and submits it again. |
| D6 | Form fields, all required: photo, full name, father's name, mother's name, date of birth, email, gender, university, semester/batch, NID, mobile, emergency mobile, hall/mess (holding no.), thana, district, present address, permanent address. The paper form's address block (thana, post code, district, division) is replaced by two free-text fields: present address and permanent address. |
| D7 | "Courses" on the paper form is not filled in by the student. The admin chooses it, and it becomes a choice of **offline batch** (D11). |
| D8 | Photo: from the phone's gallery, or taken with the camera on the spot. |
| D9 | The submitted form is printable by the student (their own) and by staff. Staff print it, the student signs it at the desk, and staff may later upload the signed copy. The upload is optional and can happen any time. |
| D10 | Payment is a fixed monthly fee that covers every exam and package in the admission. The admin picks the course end date with a date picker; courses usually run three to four months or more. |
| D11 | Offline batches: a batch has a name, end date, default monthly fee and one routine PDF. Staff and the Master Admin can both view and upload the routine. Every student in the batch sees it. |
| D12 | The first due date is picked by the admin and defaults to one month after the admission date (admitted 15 Nov → first due 15 Dec). Later dues fall on the same day each month until the course end date. |
| D13 | The Master Admin can onboard with nothing paid. Admin pages must show clearly when the admission or a monthly fee is unpaid. |
| D14 | A missed payment notifies the admins. Access is never paused automatically. |
| D15 | Staff can record a monthly payment, but it counts only once the Master Admin approves it. A payment the Master Admin records counts at once. |
| D16 | After onboarding, the Master Admin alone can change the fee, the end date or the exams and packages. The student is emailed about the change. |
| D17 | Online exams and packages given to an offline student work exactly as for an online student, including marking and results. |
| D18 | Admins are notified properly, for example when staff mark an admission ready for the Master Admin, when a payment waits for approval, and when a fee is overdue (section 7). |
| D19 | Reminders follow the recommendation: email the student 3 days before the due date, on the due date, and 3 days after if still unpaid; notify the admins at the overdue mark (section 7). |
| D20 | Access to the admission's exams and packages ends on the course end date. |
| D21 | An overdue student sees only an overdue line on My admission, beyond the reminders. Nothing else changes for them. |
| D22 | **My admission** exists only for students with an offline admission. Other students never see the page, its link or its menu entry. It has sections for what the student needs: exams, packages, routine, payments and their form (section 6.4). |
| D23 | The new admin pages must be easy to use for the admins (section 6.3). |

### 1.2 Choices made while planning (say if any is wrong)

| # | Choice |
|---|---|
| C1 | Access ends at the end of the course end date in Dhaka time (D20), through a per-grant cap (section 4.4). |
| C2 | No one-off admission fee: one fixed monthly fee only (D10). A one-off fee can be added later as an extra schedule row. |
| C3 | The printable form is a **web print page**, not a server PDF. The server PDFs use standard fonts that cannot draw Bangla (see `LiveExamReceiptPdf`), and names, addresses and the batch name may be in Bangla. A print page also lets the student save it as a PDF from the phone. |
| C4 | One admission at a time per student. A student who finishes a batch and joins another gets a new admission record; the old one stays as history. |
| C5 | Onboarding does not send one "access granted" notice and email per exam or package. The student gets one admission notice and one admission email listing everything. |
| C6 | The email on the form is where admission mail goes. If the account has no email, onboarding sets it from the form (still unverified). If the account already has a different email, mail goes to the account email and the form email is kept on the admission only. |
| C7 | Monthly fees are their own ledger (section 4.3), not the existing per-grant payment plans. A payment plan belongs to one grant (`live_exam_payment_plans.grant_id` is unique); one monthly fee covers many exams and packages. The ledger reuses the same patterns: receipt numbers, voiding, notices, reminder job. |
| C8 | While an admission is pending, the API also refuses buying, taking exams and free exams for that student (D4), not just the web pages. |
| C9 | A form draft is saved as the student types (on the server, so a phone that reloads or a second device keeps it). |
| C10 | Creating, editing and archiving a batch stays with the Master Admin; staff can view batches and upload the routine (D11). |
| C11 | "Admitted on" defaults to the day the account was created at the desk, and the first due date to one month after it (D12). Both can be changed before onboarding. |
| C12 | Payments are recorded once the admission is onboarded, because the schedule exists only then. Cash taken at the desk earlier is recorded after onboarding with its real "paid on" date. |
| C13 | The tab bar keeps its five tabs. An offline student reaches My admission from a card at the top of the home page, the account menu and the account page (section 6.4). |
| C14 | Staff do not edit the student's form; they send it back with a reason, so the printed and signed form is always what the student entered. The setup is the admins' part. |
| C15 | An `OFFLINE` grant's access ends on the course end date only; the exam's own access days do not shorten it. Otherwise an exam with a short access period would end before the course does. |
| C16 | While an admission is `READY`, its setup is locked. To change it, someone presses "Back to review", which returns it to `SUBMITTED` and tells whoever marked it ready. |

### 1.3 Answered (13 November 2026)

| # | Question | Answer |
|---|---|---|
| Q1 | Does access end on the course end date? | Yes (D20). |
| Q2 | Can staff see the batch routine PDFs? | Yes, and they can upload it (D11). |
| Q3 | Does an overdue student see more than the reminders? | Only an overdue line on My admission (D21). |

## 2. Worked example

1. 15 Nov: Rahim comes to the desk. A staff member creates his account on **Students → New student** with "Offline admission" ticked, and reads him the activation code.
2. Rahim activates on his phone, sets a password, and lands on the admission form. He takes his photo with the camera, fills every field and submits. The form saves as he types.
3. The admins get "Admission form submitted: Rahim". Staff open it and see the NID is short a digit, so they send it back with "NID has 9 digits, it needs 10 or 17". Rahim gets a notice and an email, fixes it and submits again.
4. Staff choose batch "BJS Evening, Nov 2026" (default fee ৳3,000, ends 15 Feb). The first due date is filled in as 15 Dec. They add package P1 and exam 6, see an overlap warning (Rahim already holds exam 6 from an online purchase), remove exam 6, and press **Ready for approval**. Every Master Admin is notified.
5. Staff print the form; Rahim signs; staff upload the signed scan later that day.
6. The Master Admin opens the admission, checks it and presses **Onboard**. Rahim gets P1 until 15 Feb, an "Admission approved" notice and email, and his schedule: ৳3,000 due 15 Dec, 15 Jan and 15 Feb.
7. 12 Dec: Rahim is emailed "৳3,000 due on 15 Dec". 15 Dec: due today. 16 Dec: staff record ৳3,000 cash; it waits for approval, and the Master Admin is notified and approves it. Rahim gets the receipt notice and email; his next due is 15 Jan.
8. 18 Jan: the January fee is unpaid, 3 days late. Rahim is emailed, and the admins see "Overdue: Rahim, ৳3,000, due 15 Jan". His access stays.
9. 15 Feb: the course ends. His access to P1 ends. The admins were told 7 days before.

## 3. What exists today, and what it means here

| Area | Today | Meaning for this feature |
|---|---|---|
| Account creation | `POST /api/v1/students` (`STUDENT_CREATE`) and `POST /api/v1/students/{id}/activation-code` (`STUDENT_ISSUE_ACTIVATION_CODE`). Both are granted to MASTER_ADMIN and SYSTEM_ADMIN (`IdentityPermissionContributor.grantStaff`). The web Students page is open to both roles. | Staff can already create accounts. Add the "Offline admission" option to the create form and request. |
| Activation | OTP activation sets the password; the student then logs in. | Unchanged. After login, the web sends an offline-admission student to the form (Stage 6). |
| Grants | `live_exam_grants` with `source` (`ORDER`, `MANUAL`), revoke with reason, audit. `LiveExamGrantAdminService.grant` is Master Admin only (`LIVE_EXAM_GRANT`), writes an `ACCESS_GRANTED` notice and an exam-time email. | Onboarding creates grants with a new source `OFFLINE` through a new service path, without per-item notices or emails (C5). |
| Access end | `AccessWindow.end` from the exam's `accessDays`; grants have no end date of their own. | Add a per-grant cap so access ends on the course end date (C1, section 4.4). |
| Direct enrolment payments | `live_exam_payment_plans` (one per grant), installments, direct payments, receipts, `InstallmentReminderJob`, notices and mail (V79, V80). Master Admin only (`LIVE_EXAM_INSTALLMENT`). | Not reused as-is (C7); its patterns are copied: receipt numbers, voiding, reminder timing, kind constraints. |
| Student notices | `student_notices` with a kind check constraint, re-created in full in each migration that adds kinds (V80 header). Tabs from `NoticeGroup`. | New kinds go in the same way. Admission and fee notices sit under Exams, like installment notices. |
| Staff notifications | `StaffNotificationKind` enum, `StaffNotificationWriter.notify(recipient, kind, payload, link, dedupeKey)`, sensitive keys refused in payloads. | New kinds for admissions and fees. Payloads carry names, amounts and dates only, never NID or address. |
| Mail | `exam_outbound_mail` outbox with a kind check constraint; SMTP in prod. | New mail kinds for the admission email, receipts, reminders and changes. |
| Student photo | `StudentPhotoService` (V77): 8 MB upload, EXIF stripped, stored at most 1 MB. | The form's photo uses the same pipeline. On onboarding it becomes the profile photo too. |
| Package routine PDF | V78 module routines, `PUT /api/v1/live-exam-modules/{id}/routine-pdf`. | The batch routine copies this pattern. |
| Receipt PDF | `LiveExamReceiptPdf`, English only (standard fonts). | Fee receipts follow it: English PDF plus a web print view with Bangla. |

## 4. Data and rules

Migration numbers continue from V80. Every new kind is added by re-creating the check constraint with the full previous list, as V80 did.

### 4.1 Offline batches (`V81__offline_batches.sql`)

`offline_batches`: `id`, `name_en`, `name_bn`, `starts_on` (date, optional), `ends_on` (date), `default_monthly_fee` (numeric), routine file fields (stored name, original name, size, content type, uploaded_by, uploaded_at), `status` (`ACTIVE`, `ARCHIVED`), `created_by`, `created_at`, `updated_at`, `version`.

Rules: only the Master Admin creates, edits or archives a batch (C10). Staff and the Master Admin can view batches and upload or replace the routine (D11). A routine is a PDF, at most 20 MB like the package routine. A batch's end date and fee are only defaults: each admission keeps its own copy, so changing a batch never changes an onboarded admission silently. Uploading a new routine notifies every onboarded student in the batch (section 7). A batch with onboarded admissions cannot be archived; the page names them. An archived batch cannot be chosen for a new admission.

### 4.2 Admissions (`V82__offline_admissions.sql`)

`offline_admissions`: one row per admission.

- Who and state: `id`, `student_id`, `status`, `created_by` (desk), `created_at`, `version` (optimistic lock).
- Form (student's fields, D6): `full_name`, `father_name`, `mother_name`, `date_of_birth`, `email`, `gender`, `university`, `semester_batch`, `nid`, `mobile`, `emergency_mobile`, `hall_mess`, `thana`, `district`, `present_address`, `permanent_address`, photo file fields, `draft_saved_at`, `submitted_at`, `submit_count`.
- Review: `changes_reason`, `changes_requested_by`, `changes_requested_at`.
- Setup (admin, D7, D10, D12): `batch_id`, `monthly_fee`, `admitted_on`, `first_due_on`, `course_ends_on`, `ready_by`, `ready_at`.
- Onboarding: `onboarded_by`, `onboarded_at`.
- Signed form (D9): file fields, `signed_uploaded_by`, `signed_uploaded_at`.
- Ending: `closed_at`, `closed_reason` (withdrawn, course ended).

Unique index: one admission per student whose `status` is not `CLOSED` (C4).

`offline_admission_submissions`: one row per submission, holding the submitted form as JSON, the time, and the reason it was sent back if it was. This is the history for D5.

`offline_admission_items`: `admission_id`, `scope` (`EXAM`, `MODULE`), `exam_id` or `module_id`, `grant_id` (set at onboarding), `added_by`, `added_at`, `removed_at`.

**States:**

| State | Meaning | Who moves it on |
|---|---|---|
| `AWAITING_FORM` | Account made at the desk; form not yet submitted (a draft may exist). | Student submits → `SUBMITTED`. |
| `SUBMITTED` | Waiting for an admin. Setup can be edited. | Admin sends back → `CHANGES_REQUESTED`; staff or Master Admin mark ready → `READY`. |
| `CHANGES_REQUESTED` | The student sees the reason and can edit and resubmit. | Student resubmits → `SUBMITTED`. |
| `READY` | Setup complete and locked (C16); waiting for the Master Admin. | Master Admin onboards → `ONBOARDED`; staff or Master Admin press "Back to review" → `SUBMITTED` (setup change), or send it back → `CHANGES_REQUESTED` (form problem). |
| `ONBOARDED` | Access granted, schedule running. | Master Admin closes → `CLOSED`; the course end closes it automatically one day after the end date. |
| `CLOSED` | History. | None. |

"Ready" requires: a valid form, a batch, a monthly fee above zero, a first due date on or after the admission date, an end date after the first due date, and at least one exam or package.

### 4.3 Monthly fees (`V83__offline_fees.sql`)

`offline_fee_dues`: the schedule. `admission_id`, `seq`, `due_on`, `amount`, `created_at`, `cancelled_at`. Generated at onboarding: one row per month from `first_due_on` up to and including the last due date on or before `course_ends_on` (D12). A day that does not exist in a month (31st) falls on that month's last day.

`offline_fee_payments`: `id`, `admission_id`, `amount`, `method` (`CASH`, `BKASH`, `NAGAD`, `ROCKET`, `BANK`, as direct payments), `reference`, `paid_on`, `note`, `status` (`PENDING`, `APPROVED`, `REJECTED`, `VOIDED`), `recorded_by`, `recorded_at`, `decided_by`, `decided_at`, `decision_reason`, `receipt_no` (set on approval, from the same receipt sequence as direct payments so numbers stay unique), `version`.

**Rules:**
- Payments are matched to dues in order, oldest first, by amount (as installments are, C8 of the installments plan). A partial payment leaves the rest due. A payment larger than the oldest due carries over to the next one. A payment larger than everything still owed is refused with the amount still owed.
- Payments can be recorded only on an onboarded admission (C12).
- Closing an admission cancels its future unpaid dues. Dues already past stay owed and visible until paid or written off by the Master Admin (void reason "written off").
- A payment the Master Admin records is `APPROVED` at once. A staff payment is `PENDING` until the Master Admin approves or rejects it (D15). Only approved payments count, get a receipt number, and trigger the receipt notice and email.
- A voided payment stops counting; the dues it covered are due again.
- Changing the monthly fee (D16) changes the amount of every unpaid future due from the next one on. Paid and part-paid dues keep their amounts.
- Changing the end date adds or cancels future unpaid dues. A due with any approved payment is never cancelled; the change is refused with a clear message.
- Changing the first due date is allowed only before onboarding.
- "Unpaid" flags for admin pages (D13): `no payment yet`, `overdue` (a due date passed with money owed), and `pending approval` (a staff payment waits).

### 4.4 Grants and access (`V84__grant_access_cap.sql`)

- Add `access_ends_at TIMESTAMPTZ NULL` to `live_exam_grants`. For an `OFFLINE` grant, access ends at this date and the exam's own access days are not used (C15). For other sources the column stays null, so today's behaviour is unchanged.
- Add `OFFLINE` to the grant sources (the `source` column is 8 characters; `OFFLINE` fits). It sits after `MANUAL` in the access precedence list.
- Onboarding creates one grant per item, source `OFFLINE`, `access_ends_at` = the end of the course end date in Dhaka time, reason "Offline admission". Changing the end date updates the caps of every live `OFFLINE` grant of the admission. Removing an item revokes its grant with reason "Removed from offline admission". Closing the admission caps access at the close time.
- Overlaps: before ready and before onboarding, the setup screen shows any access the student already holds to each item (reuse the overlap part of `LiveExamGrantAdminService.quote`). Onboarding refuses an item the student already holds by an active grant, with a message naming it, rather than failing half-way.

### 4.5 Gating (D4, C8)

- A student with an admission in `AWAITING_FORM`, `SUBMITTED`, `CHANGES_REQUESTED` or `READY` is "pending".
- API: a pending student is refused (`ADMISSION_PENDING`, 403) on taking or retaking an exam, starting checkout or an order, and free-exam access. Reading their own profile, notices, admission and photo still works.
- Web: after login a pending student goes to `/admission`; every other student page redirects there. The tab bar, sidebar and catalog links are hidden for a pending student. Logout and language switching stay.

### 4.6 Permissions (`OfflineAdmissionPermissionContributor`)

| Permission | Master Admin | System Admin (staff) | Student |
|---|---|---|---|
| `OFFLINE_ADMISSION_READ` (queue, detail, print, signed copy) | yes | yes | own (print, own form) |
| `OFFLINE_ADMISSION_REVIEW` (send back, edit setup, mark ready, upload signed copy) | yes | yes | no |
| `OFFLINE_ADMISSION_ONBOARD` (onboard, change after onboarding, close) | yes | **no** | no |
| `OFFLINE_BATCH_MANAGE` (create, edit, archive) | yes | no | no |
| `OFFLINE_BATCH_ROUTINE` (view batches, upload or replace the routine) | yes | yes | own batch's routine, read only |
| `OFFLINE_FEE_RECORD` (record a payment; staff → pending) | yes | yes | no |
| `OFFLINE_FEE_APPROVE` (approve, reject, void) | yes | no | no |

Instructors get none of these. NID, date of birth, addresses and the photo are only ever returned to these roles and to the student themself.

## 5. API

All under `/api/v1`. Every write records an audit event. Every state change checks `version` and answers `409 STALE_VERSION` when someone else changed the admission first.

**Desk**
- `POST /students` takes `offlineAdmission: true`, which creates the `AWAITING_FORM` admission in the same transaction.

**Student**
- `GET /me/admission`: state, form, reason if sent back, and after onboarding: batch, routine link, items, dues, payments, next due, overdue flag.
- `PUT /me/admission/form`: save a draft (partial allowed).
- `PUT /me/admission/photo`: multipart photo (gallery or camera), through the photo pipeline.
- `POST /me/admission/submit`: validates every field (D6); only from `AWAITING_FORM` or `CHANGES_REQUESTED`.
- `GET /me/admission/batch-routine.pdf`: the routine of the student's batch, after onboarding.
- `GET /me/admission/payments/{id}/receipt.pdf`: approved payments only.

**Staff**
- `GET /offline-admissions?status=&batchId=&q=&flag=unpaid|overdue|pending-approval`: the queue.
- `GET /offline-admissions/{id}`: everything, including submission history and photo link.
- `PATCH /offline-admissions/{id}/setup`: batch, fee, admitted on, first due, end date (defaults from the batch, and first due = admitted on + 1 month).
- `POST /offline-admissions/{id}/items`, `DELETE /offline-admissions/{id}/items/{itemId}`: with the overlap check (4.4). Before onboarding, staff and Master Admin; the items are only a list. After onboarding, Master Admin only (D16); adding creates the grant at once, removing revokes it, and the student is told (`ADMISSION_UPDATED`).
- `POST /offline-admissions/{id}/request-changes` `{reason}`, `POST /offline-admissions/{id}/ready`, `POST /offline-admissions/{id}/back-to-review` (C16).
- `POST /offline-admissions/{id}/onboard`: Master Admin only.
- `PATCH /offline-admissions/{id}/terms`: after onboarding, Master Admin only (D16): fee, end date.
- `POST /offline-admissions/{id}/close` `{reason}`: Master Admin only.
- `PUT /offline-admissions/{id}/signed-form` (PDF or image) and `GET` it.
- `GET /offline-admissions/{id}/photo`.

**Batches**
- Master Admin: `POST /offline-batches`, `PATCH /offline-batches/{id}`, `POST /offline-batches/{id}/archive`.
- Staff and Master Admin: `GET /offline-batches`, `GET /offline-batches/{id}`, `PUT /offline-batches/{id}/routine-pdf`, `GET /offline-batches/{id}/routine.pdf`, `GET /offline-batches/{id}/students`.

**My admission visibility**
- `GET /me/admission` answers 404 for a student with no admission (D22). The web uses that answer, and a flag on the session user (`offlineAdmission: none | pending | onboarded | closed`), to decide whether to show the page and its links.

**Fees**
- `POST /offline-admissions/{id}/payments`: record (staff → `PENDING`, Master Admin → `APPROVED`).
- `POST /offline-fee-payments/{id}/approve`, `/reject` `{reason}`, `/void` `{reason}`: Master Admin.
- `GET /offline-fee-payments?status=PENDING`: the approval queue.
- `GET /offline-fee-payments/{id}/receipt.pdf`.

**Counts for the dashboard**
- `GET /offline-admissions/counts`: submitted, ready, pending payments, overdue.

The OpenAPI types used by the web (`npm run check:openapi`) are regenerated at the end of each API stage.

## 6. Web

### 6.1 Staff (admin host)

- **Students → New student**: an "Offline admission" checkbox (both roles). After creating, the dialog shows the activation code and says the student will see the admission form after activating.
- **Admissions** (new nav item, both roles): tabs Submitted, Changes requested, Ready, Onboarded, Closed, plus filters for unpaid, overdue and pending approval. Each row: photo, name, mobile, batch, state, and flags (No payment yet, Overdue, Payment waiting).
- **Admission detail**:
  - The form, read-only, with the photo and the submission history.
  - "Send back with a reason".
  - The setup panel: batch (prefills fee and end date), monthly fee, admitted on, first due (prefilled one month after admitted on), end date, and items with overlap warnings.
  - "Ready for approval" for staff. The Master Admin also sees **Onboard**, which staff do not see.
  - Print form, and upload or view the signed copy.
  - After onboarding: dues and payments, record payment, and for the Master Admin, change fee or end date, add or remove items, close.
- **Print view** (`/staff/admissions/{id}/print`, and the student's own `/admission/print`): A4 layout following the paper form, with logo, photo, every field, batch name, date, and empty "Student's signature" and "Officer signature" lines. Uses `@media print`, Bangla included (C3).
- **Offline batches**: list, students in the batch, routine view and upload (staff and Master Admin); create, edit and archive (Master Admin).
- **Offline fees**: the pending approvals queue (Master Admin approves or rejects) and the overdue list.
- **Dashboard**: cards for Submitted, Ready for approval (Master Admin), Payments waiting (Master Admin), Overdue fees.
- **Students list**: an "Offline" badge and the admission state, with a link to the admission.

### 6.2 Student (student host, phone first)

- **After login**: a pending student goes to `/admission` (4.5).
- **Admission form**: one column, large inputs, the right keyboard per field (number pad for mobile and NID, email keyboard, date picker).
  - Photo: "Take a photo" (camera) and "Choose from gallery", preview, retake.
  - Draft saves as the student types (C9), and errors show at each field.
  - Submit asks for confirmation.
  - The tab bar is hidden while pending.
- **Under review**: what happens next, the submitted date, "View / print my form", and logout.
- **Changes requested**: the admin's reason at the top, the form open for editing, and "Submit again".
- **My admission**: only for offline-admission students, after onboarding (D22). Design in section 6.4.
- **Notices**: the new kinds render with the right icon and link (section 7). Access badges show "Offline admission" for `OFFLINE` grants.
- Bangla copy is written as natural Bangla matching existing terms, not translated word for word.

### 6.3 Admin pages: UI and UX

The admin pages are used at a busy desk, mostly on a desktop and sometimes on a tablet or phone. The aim: an admin always knows what state an admission is in, what is missing, and the one next thing to do.

**Principles (every admin page)**
1. **One next action, shown first.** Each admission shows its state and a single primary button for the next step a person in that role can take. Actions the role cannot take are not shown as dead buttons. Instead, a short line says who acts next, for example "Waiting for the Master Admin to onboard".
2. **Show what is missing, not just that something is.** Before "Ready for approval" is allowed, a checklist lists each requirement (form valid, batch, fee, first due, end date, at least one item) with a tick or the missing part. The button stays off until all are ticked, and the list says why.
3. **Money problems are visible everywhere** (D13). Pills in one fixed colour code appear on the queue rows, the detail header, the student list and the batch's student list: "No payment yet", "Overdue ৳X", "Payment waiting approval".
4. **Defaults that are almost always right.** The batch fills the fee and end date. "Admitted on" fills from the desk date and the first due date from one month after it (C11). Admins only change what differs.
5. **Confirm with a summary before anything that cannot be undone.** Onboarding, closing, voiding and approving show a dialog that restates what happens: the student, the items, the fee, the first due date, the end date, and "the student is emailed".
6. **Send-back reasons are quick to give.** The send-back dialog has one-tap reasons (photo unclear, NID wrong, name does not match the NID, address incomplete) plus free text. The student sees exactly what was chosen.
7. **Nothing is lost.** Edits to the setup save as they are made, with a "Saved" note. If someone else changed the admission meanwhile, the page reloads it and says who changed it (the 409 handling, side effect 17).
8. **Links land in the right place.** Every staff notification (section 7) opens the exact admission, payment or batch, scrolled to the relevant part.
9. **Same look as the rest of the admin panel.** Existing components: `Tabs`, `Dialog`, `ConfirmDialog`, `FileDrop`, `Pill`, the sticky action bar and the empty states. Both languages. Usable at 360 px, with tables turning into stacked cards on a phone.
10. **Fast to find people.** The queue searches by name, mobile and NID, and remembers its tab and filters in the URL.

**Admissions queue (`/staff/admissions`)**
- Tabs with counts: Submitted, Changes requested, Ready, Onboarded, Closed. The default tab is "Ready" for the Master Admin and "Submitted" for staff, so each role lands on its own work.
- Filters: batch, the money pills (unpaid, overdue, waiting approval), and a date range.
- Each row: photo thumbnail, name, mobile, batch, state, time in this state ("submitted 2 hours ago"), money pills, and the row's next action as a button (Review, Onboard, Record payment).
- Empty states that say what to do, for example "No forms waiting. New desk admissions appear here once the student submits."

**Admission detail (`/staff/admissions/{id}`)**
- **Header:** photo, name, mobile, state, money pills, and a progress strip: Form → Review → Ready → Onboarded. Under it, the primary action, or the "waiting for" line.
- **Before onboarding, two columns** (one on a phone):
  - Left: the form, read-only and grouped (personal, education, contact, addresses), with the photo large enough to check, and the submission history (each submission, and any reason it was sent back).
  - Right, sticky: the setup panel (batch, fee, admitted on, first due, end date, a preview of the dues it will create, items with search and overlap warnings), the ready checklist, then the actions.
- **After onboarding, tabs:**
  - Overview: batch, dates, fee, next due, money pills.
  - Fees: dues and payments in one timeline, with "Record payment" prefilled with the next due amount and today's date.
  - Exams and packages: each with access-until, plus add or remove (Master Admin).
  - Form: the form, print, and the signed copy.
  - History: an audit timeline of who did what and when.
- **Print and signed copy:** "Print form" opens the print view in a new tab. "Signed copy" is a `FileDrop` that accepts PDF or a phone photo of the paper, and shows "Not uploaded yet" until it has one. It never blocks anything.

**Fee approvals (`/staff/offline-fees`)**
- For the Master Admin: a list of payments waiting approval. Each row shows the student, amount, method, reference, paid-on date, who recorded it and when, and Approve and Reject buttons in the row. Approving several at once is allowed after one confirmation that lists them.
- For staff: the payments they recorded, with their state.
- An Overdue tab: student, batch, amount, due date, days late, and a "Record payment" action.

**Batches (`/staff/offline-batches`)**
- A list with name, end date, default fee, the number of students, and whether a routine is uploaded ("No routine yet" as a pill).
- Batch page: details (editable by the Master Admin), the routine (view, upload or replace for staff and Master Admin, showing who uploaded it and when, and how many students were notified), and the students with their money pills.

**Dashboard and navigation**
- Dashboard cards: "Forms to review" (staff and Master Admin), "Ready to onboard" (Master Admin), "Payments to approve" (Master Admin), "Overdue fees". Each opens the queue already filtered.
- One nav group "Offline admission" with Admissions, Fee approvals and Batches, with counts as badges.

**Desk creation**
- On Students → New student, the "Offline admission" checkbox is on by default for staff. After creating, a card shows the activation code in large type, the student's phone, and the next steps to read out ("Open the site, activate with this code, then fill in the form").

### 6.4 My admission (student)

Only for students with an offline admission (D22). Everyone else gets a 404 from the API, and the web shows no link, card or menu entry. The page is phone-first, one column, sections in this order:

1. **Header:** batch name, "Admitted on", "Course ends on", and the state ("Active", or "Course ended" when closed).
2. **Payments:**
   - A card at the top with the next amount and due date.
   - An overdue line in warning colour when a due has passed unpaid: "৳3,000 was due on 15 Jan" (D21). Nothing else about overdue appears elsewhere.
   - The schedule (each month: amount, paid, owed), then the payments with receipt links. A staff payment waiting approval is not shown to the student until it is approved.
3. **Exams:** the admission's exams with their status, linking to each exam page, and access until the course end date.
4. **Packages:** the admission's packages with their exam count, linking to each package page.
5. **Routine:** the batch routine in the existing PDF viewer (download allowed), with "Updated on". The section says "Your routine will appear here" when none is uploaded yet.
6. **My form:** a summary with "View / print my form".

**Links:** a card at the top of the home page ("My admission: next payment ৳3,000 on 15 Dec"), an entry in the account menu, and a row on the account page (C13). After the admission closes, the page stays as read-only history and the home card goes away.

**While the admission is pending,** the student sees the form, or the under-review page, instead of My admission (section 6.2).

## 7. Notifications and emails

Admins below means every Master Admin and every System Admin unless a row says Master Admin. Each message has a dedupe key, so a repeated job run or a double click adds nothing. Payloads hold names, amounts, dates and batch names only, never NID, date of birth, address or phone. `StaffNotificationWriter` already refuses sensitive keys.

| Event | Staff notification (to) | Student notice | Student email |
|---|---|---|---|
| Form submitted (first time) | `ADMISSION_SUBMITTED` (admins) | none | none |
| Form resubmitted | `ADMISSION_RESUBMITTED` (admins, and whoever sent it back) | none | none |
| Sent back with a reason | none | `ADMISSION_CHANGES_REQUESTED` (with reason) | yes, with the reason |
| Marked ready | `ADMISSION_READY` (Master Admins) | none | none |
| Back to review (C16) | `ADMISSION_RETURNED` (whoever marked it ready, if someone else pressed it) | none | none |
| Onboarded | `ADMISSION_ONBOARDED` (whoever marked it ready, if not the Master Admin) | `ADMISSION_APPROVED` | yes: batch, items, fee, end date, first due |
| Staff recorded a payment | `OFFLINE_PAYMENT_PENDING` (Master Admins) | none | none |
| Payment approved | `OFFLINE_PAYMENT_APPROVED` (the recorder) | `OFFLINE_FEE_RECEIVED` | yes, receipt attached (English PDF) and a web link |
| Payment rejected | `OFFLINE_PAYMENT_REJECTED` (the recorder, with reason) | none | none |
| Payment voided | none | `OFFLINE_FEE_VOIDED` | yes |
| Due in 3 days | none | `OFFLINE_FEE_DUE_SOON` | yes |
| Due today | none | `OFFLINE_FEE_DUE_TODAY` | yes |
| 3 days overdue | `OFFLINE_FEE_OVERDUE` (admins) | `OFFLINE_FEE_OVERDUE` | yes |
| Still overdue, weekly after that | `OFFLINE_FEE_OVERDUE` (Master Admins, weekly) | none | none |
| Terms changed (fee, end date, items) | none | `ADMISSION_UPDATED` | yes, old and new values |
| Batch routine uploaded or replaced | none | `BATCH_ROUTINE_UPDATED` (every onboarded student of the batch) | none |
| Course ends in 7 days | `OFFLINE_COURSE_ENDING` (Master Admins) | none | none |
| Course ended (admission closed) | none | `ADMISSION_CLOSED` | yes |

The reminder job (`OfflineFeeReminderJob`) copies `InstallmentReminderJob`: once a day at 06:30 Dhaka, each admission in its own transaction, suspended students get nothing but admins are still told, and a missed run is caught up by the next. The course-end job runs in the same pass.

## 8. Side effects to handle

1. **Kind constraints.** `student_notices`, `exam_outbound_mail` and any staff notification check are re-created with the full previous list plus the new kinds (V80 pattern). Missing one breaks an existing feature at runtime.
2. **Notice tabs.** New student kinds land under Exams (`NoticeGroup.of` defaults there). The web `NOTICE_KINDS`, icon map and labels get the new kinds, or the cards show the generic label.
3. **Staff notification rendering.** The web builds staff notification text from kind and payload. Each new kind needs its text (en and bn) and link, or the row is blank.
4. **Access precedence.** `OFFLINE` must be in `LiveExamAccess.PRECEDENCE`. The web `AccessBadge` and access-source helpers need an `OFFLINE` label. Otherwise the student sees no badge or an "unknown" one.
5. **The access cap touches every access path.** Holding, expiry, "access until" dates on exam and package pages, `retireExpiredGrants`, My exams, and leaderboard eligibility. Each reads `AccessWindow.end`, so the cap goes there once, and the tests in 10.2 cover each place.
6. **Existing grant flows.** Direct enrolment (`LiveExamGrantAdminService.grant`) refuses when a grant is active. An `OFFLINE` grant counts as active, so a later direct enrolment of the same item is refused with the existing message. That is correct; the enrolment page names the source.
7. **Revoke on the enrolments page.** The Master Admin can revoke any grant today. Revoking an `OFFLINE` grant there should also mark the admission item removed, or the two disagree. Alternatively, the enrolments page refuses `OFFLINE` grants and links to the admission. **Plan: refuse and link.**
8. **Exam-time emails.** `examMail.onDirectEnrolment` sends per-item exam emails after a grant. Onboarding skips it (C5), but the normal exam reminders (`StudentReminderJob`, hour-before) still reach the student, as for any holder.
9. **Pending student and existing pages.** Guards on the student site (`RequireStudent`, the shell) must route a pending student to `/admission`, including deep links from emails and notices. API gating (4.5) covers direct calls.
10. **Profile sync at onboarding.** The form name updates `student_profiles.full_name`; the form photo becomes the profile photo (V77); the email rule is C6. Later profile edits do not change the admission record (it is the signed form's copy).
11. **Phone guard.** The desk account already has a phone (`StudentPhoneGuard`), so pending students are never asked for a phone. The form's mobile may differ from the login phone; both are kept.
12. **Payments overview and staff pay analytics.** Offline fee income is a new stream. The payments and analytics pages either show it as its own line or leave it out on purpose. **Plan: own line "Offline fees"**, approved payments only.
13. **Receipt numbers.** Fee receipts share the direct-payment receipt sequence so numbers never clash; tests assert uniqueness.
14. **Suspension and deletion.** A suspended student gets no notices or emails (as E10). Deleting a student with an admission is refused while it is not `CLOSED`.
15. **Search.** Staff quick search finds students by name, mobile and NID for staff roles only; instructors' search never returns NID.
16. **Privacy.** NID, date of birth, addresses and photos are only served to staff roles and the student. File URLs are authorized routes, never public paths. Logs never print form fields.
17. **Concurrency.** Two staff editing setup, or staff editing while the Master Admin onboards: the `version` check returns 409 and the web reloads the admission with a message.
18. **Time zone.** Dues and end dates are dates in Asia/Dhaka. The access cap is the end of that day in Dhaka. The 31st falls on the month's last day.
19. **Mail without an email.** The form requires one. If delivery fails, the outbox retries as today, and the admin detail page shows the last mail status.
20. **Draft autosave on the server.** Saves are rate-limited (at most one per few seconds) and partial, and the submit endpoint does the full validation.
21. **Migrations on live data.** The prod database is live with real money. All four migrations only add tables, columns and constraint kinds, so there is no backfill. A backup is taken before the release (section 11).
22. **Feature switch.** `app.offline-admission.enabled` (default off in prod until the walkthrough passes). When off, the checkbox and nav items are hidden and the endpoints answer 404.
23. **The student shell after this session's changes.** The tab bar now always shows (`SiteFooter tabBarInset`). For a pending student `showTabs` must be false, so the bar is hidden and the footer padding goes with it.
24. **My admission is invisible to other students** (D22). The home card, account menu entry and account-page row render only when the session says the student has an admission. `/admission` and `/my-admission` for anyone else show the normal not-found page. The API answers 404, never an empty admission.
25. **Exam access days vs the course end** (C15). Exam and package pages show "Access until <course end>" for an `OFFLINE` grant, not the exam's own access period, so the dates shown match the real access.
26. **Session flag.** The session user gains `offlineAdmission` (none, pending, onboarded, closed). It changes on submit, onboarding and close, so the web refetches the session after those, or a just-onboarded student would still be sent to the review page.
27. **Sample data.** A small seed addition (one batch, one admission in each state) for QA on `coaching_center_sample`, run only with Shahriar's go.

## 9. Stages

Each stage ends with a test gate (section 10). A stage is not started until the previous gate passed and the build log is written.

| Stage | Scope | Gate |
|---|---|---|
| **1. Schema and access cap** | V81–V84; entities and repositories; `OFFLINE` source; `access_ends_at` in `AccessWindow` and `LiveExamAccess`; kind constraints re-created; permissions contributor. | Gate 1 |
| **2. Desk and student form API** | `offlineAdmission` on create; `/me/admission` draft, photo, submit, resubmit; API gating (4.5); submission history; notices and staff notifications for submit and resubmit. | Gate 2 |
| **3. Review, setup, batches, onboarding API** | Queue and detail; setup with defaults; items with overlap check; send back; ready and unready; onboard (Master Admin only) creating grants with caps, schedule, profile sync; batches with routine; signed-form upload; changes after onboarding; close; related notices and staff notifications. | Gate 3 |
| **4. Fees API and jobs** | Record payment (pending or approved), approve, reject, void; matching to dues; fee and end-date changes; receipt PDF; `OfflineFeeReminderJob` with course-end handling; all fee and course mails. | Gate 4 |
| **5. Staff web** | Create checkbox; Admissions queue and detail; setup panel; print view; signed upload; batches pages; fees pages; dashboard cards; nav items; students list badge; enrolments page refusing `OFFLINE` revoke; notification texts. | Gate 5 |
| **6. Student web** | Post-login routing and guards; admission form with camera and gallery photo, draft autosave; under review and changes requested pages; My admission page; print view; notice kinds and access badge. | Gate 6 |
| **7. Walkthrough and release prep** | Feature switch; sample seed addition (with go-ahead); full walkthrough on the sample DB; regression list; release notes and deploy steps. | Gate 7 |

## 10. Testing

### 10.1 Rules

1. **Test at the end of each stage, not after every small edit.** During a stage, run only the tests for the files being changed. Run the full suites at the gate.
2. **Update the existing tests before running them.** Every stage changes lists and contracts that existing tests pin down, such as permission sets, notice and mail kinds, nav items, message keys and access sources. Before running a gate:
   - Search the test folders for each enum, list, key or route the stage touched (for example `rg "ACCESS_GRANTED|INSTALLMENT_PAYMENT_RECEIVED" api/src/test web/tests`), and update the expectations deliberately.
   - Name every updated test in the build log, with the reason.
   - Never weaken an assertion to make it pass. If an old expectation is now wrong, change it to the new behaviour; if it is still right, the code is wrong.
3. **Add the new tests in the same stage** as the code they cover.
4. **Write down every result** in the build log: the command, counts, failures, and the fix.
5. API tests need Docker running (Testcontainers PostgreSQL).

**Commands**
- API, one class: `cd api && ./mvnw.cmd -q test -Dtest=OfflineAdmissionFlowIT` (on Windows; `./mvnw` elsewhere)
- API, full suite: `cd api && ./mvnw.cmd -q test`
- Web, one folder: `cd web && npx vitest run tests/offline-admission`
- Web, full suite: `cd web && npx vitest run`
- Type-check and lint (from Stage 5): `cd web && npx tsc --noEmit && npm run lint`, and `npm run check:openapi` after API stages.
- Phone checks (Stages 6 and 7): Playwright scripts at 360, 390 and 412 px, kept outside the project (as in the mobile fixes round), against the local servers on the sample DB.

### 10.2 What each gate checks

**Gate 1: schema and access cap**
- Flyway applies V81–V84 on an empty database and on a copy of the current schema (Testcontainers).
- Every notice and mail kind that existed before still inserts; each new one inserts.
- Access cap: an `OFFLINE` grant with a past cap gives no access; a future cap gives access until then, even past the exam's own access days (C15); every other grant (null cap) behaves exactly as before. Covered for exam grants, package grants, roster, the "access until" date, and `retireExpiredGrants`.
- Existing to update first: access and enrolment ITs (`PackageEnrolmentRulesIT`, `LiveEnrolmentListIT`, the access ITs), and any test listing grant sources or permissions.
- Then: full API suite.

**Gate 2: desk and form**
- Desk: staff and Master Admin create an offline-admission student; an instructor cannot; an online sign-up has no admission.
- Form: a draft saves partial data; submit refuses each missing field by name; the photo is accepted as JPEG, PNG or WebP (an iPhone's HEIC arrives as JPEG from the browser; a raw HEIC is refused with a clear message), with EXIF stripped; resubmit works only after changes are requested.
- Gating: a pending student is refused on take, retake, checkout and a free exam, and can still read their notices and profile.
- Notifications: submit and resubmit reach the admins once each (dedupe).
- Then: full API suite.

**Gate 3: review and onboarding**
- Roles: staff can edit setup, send back and mark ready, and get 403 on onboard, terms change and close. The Master Admin can do all of them.
- Ready validation refuses each missing setup part; defaults (first due one month after the admission date, batch fee and end date) fill in.
- Overlaps: an item the student already holds is flagged at setup and refused at onboarding by name, with nothing half-done.
- Onboarding creates one `OFFLINE` grant per item with the cap, the dues schedule (including the 31st case), profile name and photo, one notice and one email, and no per-item `ACCESS_GRANTED`.
- Version conflicts answer 409.
- Batches: only the Master Admin manages them; a routine upload notifies onboarded students of the batch once.
- Signed upload: PDF and image accepted, other types refused, staff only.
- Terms change: fee and end date update future unpaid dues and grant caps; a paid due is never cancelled.
- Then: full API suite.

**Gate 4: fees and jobs**
- A staff payment is pending and counts for nothing; approval counts it, numbers the receipt and sends notice and email; rejection notifies the recorder; a void restores the due.
- Matching: oldest first; partial payments; over-payment carries to the next due.
- Reminder job with a fixed clock: 3 days before, due day, and 3 days after (student); overdue (admins), then weekly (Master Admins); course ending in 7 days; course end closes the admission and access ends. A second run on the same day sends nothing new; a suspended student gets nothing but admins are told.
- Receipt numbers stay unique across direct payments and fee payments.
- Mail delivery IT for each new mail kind (as `InstallmentMailDeliveryIT`).
- Then: full API suite and `npm run check:openapi`.

**Gate 5: staff web**
- Component tests: queue tabs and flags; detail actions per role (staff never see Onboard); setup defaults; overlap warnings; print view fields and signature lines; fees approval queue; dashboard counts.
- Existing to update first: staff nav tests (`tests/s1-b/staff-nav.test.ts`), staff notification text tests, message parity tests (en and bn keys), students page tests.
- Then: web full suite, type-check, lint.

**Gate 6: student web**
- Component tests: a student without an admission sees no My admission card, menu entry or row, and gets not-found on its URL (D22); routing of a pending student from every page to `/admission`; a just-onboarded student reaches My admission after the session refresh; form validation messages; draft restore; the changes-requested reason shown; My admission with dues, overdue line, routine and items; new notice kinds; the `OFFLINE` badge.
- Existing to update first: notice kind tests (`tests/stu-lms/notice-kinds.test.ts`, `tests/notices/*`), access source tests (`tests/pur-stu/access.test.ts`, `tests/stu-lms/access-source.test.ts`, `tests/student-nav/access-source.test.tsx`), shell and tab bar tests (`tests/ws9/sticky-bar-offset.test.tsx`).
- Phone checks at 360, 390 and 412 px: no horizontal scroll on the form, review page, My admission and print view; the camera and gallery inputs are present; the submit button is reachable; the tab bar is hidden while pending.
- Then: web full suite, type-check, lint.

**Gate 7: walkthrough**
- The worked example (section 2) end to end on the sample DB, with a fixed date for the reminder steps (job run with a test clock).
- Regression: online sign-up and purchase; direct enrolment with installments; exam taking and written upload; notices tabs; staff pay pages; payments overview.
- Both full suites green, type-check, lint, and `check:openapi` clean.

## 11. How this ships

1. Build on a branch from `main`; one commit per stage, each with its build log entry.
2. After Gate 7, a release plan in the style of `direct-enrolment-release.md`: backup, migrations V81–V84, deploy, feature switch on for staging first, a walkthrough on staging, then on prod.
3. Prod stays with the feature switch off until Shahriar turns it on.

## 12. Review (13 November 2026)

What the review of this plan found, and what changed:

| # | Finding | Change |
|---|---|---|
| R1 | An exam with a short access period would end an offline student's access before the course end, though the course end is meant to govern it. | C15: an `OFFLINE` grant ends on the course end date only. Side effect 25 keeps the dates shown on exam pages right. |
| R2 | Staff could change the setup after marking it ready, so the Master Admin might onboard terms nobody re-checked. | C16: setup is locked while ready; "Back to review" reopens it and tells whoever marked it ready. |
| R3 | Adding or removing items after onboarding had no stated owner or effect. | Section 5: Master Admin only after onboarding; grants change at once and the student is told. |
| R4 | Over-payment, payments before onboarding, and what happens to dues when an admission closes were not defined. | Section 4.3 rules and C12. |
| R5 | A batch could be archived with students still in it. | Section 4.1: refused while it has onboarded admissions. |
| R6 | Whether staff may correct the student's form was not stated. | C14: no; they send it back, so the signed form matches what the student entered. |
| R7 | A just-onboarded student could be stuck on the review page until a reload. | Side effect 26: a session flag, refetched after submit, onboarding and close. |
| R8 | My admission had no rule for students without an admission. | D22, side effect 24, and a Gate 6 test. |
| R9 | The photo test expected HEIC uploads, which the photo pipeline does not read. | Gate 2: JPEG, PNG and WebP; a raw HEIC is refused with a message. |
| R10 | Small errors: activation pointed at Stage 7 instead of 6; the test commands used `./mvnw`, which does not run in this Windows setup. | Fixed. |

Left as it is, on purpose:
- Staff get a notification for every submitted form. With many staff this could be noisy. If it is, the fix later is a per-person setting, not a change to the flow.
- Fee receipts are English PDFs (standard fonts cannot draw Bangla), with the Bangla web view next to them, as for existing receipts.
