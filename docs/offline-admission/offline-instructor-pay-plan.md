# Offline classes, instructor pay and finance: plan

Status: plan, 13 November 2026. Nothing is built. Decisions are Shahriar's, from the conversation on 13 November 2026 (sample routine: `20th BJS Foundation Batch- NOVEMBER.pdf` in this folder).

## 0. Build log

Each stage adds what was built, where it differs from this plan, and the test results.

**Stage 4 (notifications).** Built: `OfflineClassDigestJob` (21:00 Dhaka daily digest of unsettled classes, one notice per active Master Admin or System Admin per person per day; 21:00 Dhaka every Sunday, the weekly reminder for held classes over a week, one per person per ISO week), `OfflineClassNotices`, and one OFFLINE_ENTRIES_DECIDED notice per recorder per decision batch, written inside the approve and reject transactions. Kinds added to `StaffNotificationKind`: OFFLINE_CLASSES_TO_SETTLE, OFFLINE_CLASSES_OVERDUE, OFFLINE_ENTRIES_DECIDED. The routine-confirm student notice is unchanged (BATCH_ROUTINE_UPDATED, once per confirm, and it carries no instructor). Differs from the plan: the digest and the weekly reminder share one job class, and the Master Admin's waiting line is sent only when pending entries exist. Tests: 15 offline and staff-notification suites green on two passes on a new database (coaching_it_stage4b); OfflineClassNoticesIT 7, StaffNotificationWriterTest 8.

**Note for the web agent (text, not built here).** Add these three kinds to the staff notification text in both languages (`web/src/i18n/messages/en/staffNotifications.ts`, `web/src/i18n/messages/bn/staffNotifications.ts` and `web/src/components/staff/notifications/notification-text.ts`). Write the bn text in natural Bangla.
- OFFLINE_CLASSES_TO_SETTLE, payload `count` (and `waiting` for the Master Admin only). Text: "{count} offline classes are waiting for payment or a not-taken mark." When `waiting` is present, add a second line: "{waiting} entries wait for your approval." Link: /staff/offline-instructor-payments.
- OFFLINE_CLASSES_OVERDUE, payload `count`. Text: "{count} offline classes have waited more than a week." Link: /staff/offline-instructor-payments.
- OFFLINE_ENTRIES_DECIDED, payload `approved` and `rejected`. Text: "{approved} of your entries were approved, {rejected} rejected." Link: /staff/offline-instructor-payments.

**Change of 13 November 2026 (Shahriar): routines count from their upload day, not a month.** This replaces the monthly model of sections 4.2, 4.4 and 8.1.
- No month is chosen or read. A routine counts from the Dhaka date it is uploaded (`effective_from`, `V92__offline_routine_effective_from.sql`). A batch has one live (CONFIRMED) routine; confirming a new one supersedes the old one, which stays as the record. Versions count per batch.
- Rows dated before that day are flagged DATE_PAST (replacing DATE_OUT_OF_MONTH), shown folded on the preview, and still become classes, which can be edited like any other.
- A confirm replaces only the classes from `effective_from` onward: matched unsettled classes take the new values, and unsettled routine classes no row lists are cancelled. Classes before that day are left as they are, even unsettled ones; an earlier row with no class adds one.
- Instructors come from the PDF. A name no instructor has (by name or alias) is added as a new instructor on confirm and does not block it; a row with no name stays without an instructor.
- One Change dialog, on the preview and on the Classes tab: "Instructor as per routine: {name}", the routine's (or batch's) instructors to pick, or a typed name (an existing one is reused, a new one is added). On the preview, picking for a name that is not an instructor yet updates every row with that name and keeps the name as an alias. A class's instructor can be changed until it is settled; the class Edit dialog no longer has the instructor field.
- The student copy for a month is the newest confirmed routine that counts from a day in or before that month.


## 1. What is being built

The offline segment gets its own expenditure record. When a batch's monthly routine PDF is uploaded, the system reads each class from it (date, subject, instructor, topic, time) and shows the classes on the batch page after the admin checks an import preview. The admin sets a default pay rate per instructor for the batch, and can override it on any class. After a class is held, staff or the Master Admin record that the instructor was paid, or mark the class "not taken"; a staff entry waits for the Master Admin's approval. A dedicated page lists the classes that are finished and not yet settled. Students see the routine without instructor names, with the SYMAM LAW watermark, and are told when it changes. A new Finance page shows all income and all expenditure, online and offline, with filters by source, so the client sees the full profit.

### 1.1 Decisions (Shahriar, 13 November 2026)

| # | Decision |
|---|---|
| F1 | One routine PDF per batch per month. Each upload adds that month's classes; earlier months stay. |
| F2 | A changed routine uploaded again updates only the future, unsettled classes. Classes already paid or marked not taken stay as they are. |
| F3 | Rows with no instructor (for example "Evidence Act - -") import with the instructor empty; the Master Admin or staff fill it in. |
| F4 | Exam rows (MCQ Exam, Monthly Exam) are paid too; the admin sets the instructor (invigilator or examiner) and the amount. |
| F5 | Offline instructors are names kept for records only. They have no account and never log in. |
| F6 | The offline segment is separate from the online one: offline instructor pay has nothing to do with Staff pay. |
| F7 | The default rate is per instructor within a batch, set on the batch page; any class can override it. |
| F8 | Pay is always recorded class by class. It may be recorded days after the class, with the real paid-on date. |
| F9 | Staff record a payment or a "not taken"; it waits for the Master Admin's approval. The Master Admin's own entry is approved at once. |
| F10 | A substitute: the admin changes the instructor on that class and pays the substitute. |
| F11 | Students see the routine without instructor names: the routine table and the PDF they open both leave the names out, and carry the SYMAM LAW watermark. The instructor and pay pages are never shown to students. |
| F12 | Admitted students of the batch are notified when its routine is updated. |
| F13 | A dedicated page shows the finished classes and the instructors not yet paid. |
| F14 | Admin notifications are grouped, never one per class: a daily digest at 21:00 Dhaka when finished classes are still unsettled, a weekly reminder while any stay open, worded as "check the offline instructor payments page". |
| F15 | The Finance page shows full profit: online and offline income, online staff pay and offline instructor pay as expenditure, with filters per source. |

### 1.2 Choices made while planning (say if any is wrong)

| # | Choice |
|---|---|
| G1 | The import is never saved straight from the PDF: the admin always confirms an editable preview, because the sample already has a wrong date (27-07-2026 among October dates), blank instructors, exam rows with text in the instructor column, merged cells and repeated lecture numbers. |
| G2 | The student copy of the PDF is generated on the server at import: each page is rendered to an image, the Instructor column is painted over using the positions found while reading the PDF, the watermark is stamped, and the pages are saved as a new PDF. Painting over the text alone would leave the names in the file; rendering to images removes them, and keeps the Bangla header exactly as the client made it (the PDF library cannot shape Bangla text itself). |
| G3 | The Finance page is for the Master Admin only (money across the whole centre). Staff keep the pages they have today. |
| G4 | An offline instructor's name is matched once: when the import finds a name it does not know, the preview asks the admin to pick an existing instructor or add a new one ("Milon" and "Milon Hossain" can be the same person). The choice is remembered for later imports. |
| G5 | A "held" class is one whose end time has passed (Dhaka time). Before that it is "scheduled" and cannot be paid or marked. |
| G6 | Payment methods are the same five as fees: CASH, BKASH, NAGAD, ROCKET, BANK, with a reference. No receipt PDF for instructors (they are not users); the record itself is the proof. |
| G7 | Older purchase tables (books, courses, exam modules: `payments`, `book_orders`, `exam_module_purchases`, `enrollments`) appear on the Finance page as "Other online" only if they hold live rows; Stage 5 checks this on the production schema before it is shown. |

## 2. Worked example

1. 25 Oct: the Master Admin opens batch "20th BJS Foundation", Classes tab, and uploads the November routine. The preview shows 46 rows. Three rows are flagged: 27-07-2026 (date outside the month: fixed to 27-10-2026), Evidence Act with no instructor (left empty for now), the MCQ Exam row (marked as an exam, instructor left empty). "Milon" is new: the admin adds him as an offline instructor. Confirm: 46 classes are saved, students are told the routine changed, and the student copy (no instructor column, watermark) is generated.
2. The admin sets the batch rates: Tuhin Hossain ৳400, Anarul Islam ৳400, Milon ৳500. Every class takes its instructor's rate unless overridden.
3. 25 Oct, 21:00: the digest tells the admins "2 classes held today are waiting: check the offline instructor payments page".
4. 28 Oct: a staff member pays Tuhin for 25 and 26 Oct in cash and records both (paid on 28 Oct). They wait for approval; the Master Admin approves both from the same page.
5. 30 Oct: the Evidence Act class is taught by a guest, Rafiq: the admin sets him on that class with ৳600 and records the payment.
6. 31 Oct: Milon did not come: staff mark the class "not taken"; the Master Admin approves; nothing is owed.
7. 1 Nov: a changed routine for November is uploaded: the preview shows the difference (3 moved, 1 added, 1 removed); paid and not-taken classes are untouched.
8. The Finance page for October shows fees collected, instructor cost, online income and online staff pay, with the net, and the batch's own profit.

## 3. What exists today

| Area | Today | Meaning here |
|---|---|---|
| Batch routine | One PDF per batch on `offline_batches` (V81, V86), replaced on each upload; students read it at `GET /api/v1/me/admission/batch-routine.pdf` as uploaded. | Becomes a list of monthly routines. The student route must serve the student copy without instructor names (F11) — the current one would show them. |
| Batch page | `/staff/offline-batches/{id}`: details, routine upload, students. | Gains a Classes tab and an Instructor rates panel. |
| Notices | Students get BATCH_ROUTINE_UPDATED on upload. Staff notifications are code kinds (`StaffNotificationKind`). | Students keep the routine notice. New staff kinds for the digest and approvals. |
| Offline fee payments | `offline_fee_payments` with PENDING/APPROVED (V83). | Income on the Finance page; the instructor payments copy the same approval pattern. |
| Online income | `live_exam_orders` (paid online orders), `live_exam_direct_payments` (installments). | Finance income sources. |
| Online expenditure | `staff_ledger` (PAYMENT rows for salaries and marking, with voids). | Finance expense source, read only. Nothing is written there (F6). |
| PDF library | PDFBox (receipts, PDF compression). | Reads the routine's text with positions, and renders pages to images for the student copy. |
| Feature switch | `app.offline-admission.enabled` hides every offline route. | Covers the new offline routes too. The Finance page has online sources too, so it is not behind the switch, but its offline sources are left out while the switch is off. |

## 4. Data (migrations V88 onward)

### 4.1 Offline instructors (`V88__offline_instructors.sql`)

`offline_instructors`: `id`, `name`, `phone` (optional), `note` (optional), `aliases` (text array: names seen in routines that mean this person, G4), `archived_at`, `created_by`, `created_at`, `updated_at`, `version`. Names are unique among non-archived instructors (case-insensitive).

`offline_batch_instructor_rates`: `batch_id`, `instructor_id`, `rate` (numeric, >= 0), `updated_by`, `updated_at`. Primary key (batch, instructor). F7.

### 4.2 Routines and classes (`V89__offline_routines_and_classes.sql`)

`offline_batch_routines`: one row per monthly upload: `id`, `batch_id`, `month` (first day of the month), `original_file` (stored name, size), `student_file` (the generated copy, G2), `version` (1, 2, … for re-uploads of the same month), `status` (`PREVIEW`, `CONFIRMED`, `SUPERSEDED`), `uploaded_by`, `uploaded_at`, `confirmed_by`, `confirmed_at`, `parse_report` (JSON: flagged rows and why). Unique: one CONFIRMED routine per batch and month.

`offline_classes`: one row per class: `id`, `batch_id`, `routine_id` (the routine that last set it), `held_on` (date), `starts_at` and `ends_at` (time, Dhaka), `subject`, `topic`, `kind` (`LECTURE`, `EXAM`, `OTHER`), `instructor_id` (nullable, F3, F4), `instructor_text` (the name as written in the PDF, kept for the record), `rate_override` (nullable), `status` (below), `status_by`, `status_at`, `cancelled_at` (removed by a later routine), `version`. The amount due is `rate_override`, else the batch rate of the instructor, else unset (the class cannot be paid until it has an amount).

**Class status:**

| Status | Meaning |
|---|---|
| `SCHEDULED` | Before its end time (G5). Can be edited, cannot be settled. |
| `HELD` | End time passed, not settled. Shows on the unsettled page and in the digest. |
| `PAID_PENDING` | Staff recorded a payment; waits for the Master Admin. |
| `PAID` | Payment approved (or recorded by the Master Admin). Locked. |
| `NOT_TAKEN_PENDING` | Staff marked not taken; waits for the Master Admin. |
| `NOT_TAKEN` | Approved. Nothing owed. Locked. |
| `CANCELLED` | Removed by a later routine before it was settled. Kept for the record, hidden by default. |

`HELD` is not stored: a class is held when its status is `SCHEDULED` and its end has passed. The stored statuses are SCHEDULED, PAID_PENDING, PAID, NOT_TAKEN_PENDING, NOT_TAKEN, CANCELLED. (Storing it would need a job to flip it; computing it from the time cannot be wrong.)

### 4.3 Instructor payments (`V90__offline_instructor_payments.sql`)

`offline_class_payments`: `id`, `class_id`, `instructor_id` (the instructor paid; a substitute is set on the class first, F10), `amount`, `method`, `reference`, `paid_on`, `note`, `status` (`PENDING`, `APPROVED`, `REJECTED`, `VOIDED`), `recorded_by`, `recorded_at`, `decided_by`, `decided_at`, `decision_reason`, `voided_by`, `voided_at`, `void_reason`, `version`. At most one non-rejected, non-voided payment per class.

`offline_class_decisions`: the "not taken" requests and their approval, same shape (class, requested_by, reason, status PENDING/APPROVED/REJECTED, decided_by, decided_at). Keeps the not-taken trail apart from money.

### 4.4 Rules

- Rates: changing a batch rate changes the amount of every unsettled class of that instructor in the batch that has no override. Settled classes keep the amount they were paid.
- Editing a class (date, time, subject, topic, instructor, override): allowed while SCHEDULED or HELD; refused once PAID_PENDING, PAID, NOT_TAKEN_PENDING or NOT_TAKEN (the admin rejects or voids first).
- Payment: the class must be HELD and have an instructor and an amount; the amount recorded may differ from the due amount (an advance or a deduction), and the difference is shown.
- Void (Master Admin only): an approved payment can be voided with a reason; the class returns to HELD.
- Re-upload (F2): rows of the new routine are matched to existing classes of the same batch and month by date + start time + subject (then by date + subject when the time moved). Matched unsettled classes take the new values; settled ones are never touched; unmatched new rows are added; existing unsettled future classes with no match are CANCELLED. The preview shows each of these before anything is saved.
- Students' view uses only CONFIRMED routines and never instructor fields.

## 5. Reading the PDF (Stage 2)

1. Extract text with positions (PDFBox `PDFTextStripper` with positions). Find the header row by its labels (Date, Day, Subject, Instructor, Topics, Time) and take each column's x range from it. If the header is not found, the import stops with "This PDF does not match the routine format" and nothing is saved.
2. Group text into rows by y position, per page; a cell spanning two lines (times such as "9.00AM- / 10.20AM") joins into one value. A date cell spanning several rows (merged) applies to the rows under it until the next date; the same for the day.
3. Normalise: dates `dd-MM-yyyy`; times "5.10 PM" (start only: the end is start + the batch's default class length, set on the batch, 80 minutes by default) and "9.00AM-10.20AM" (start and end); "-" or blank instructor or topic becomes empty.
4. Classify: subject or text containing "Exam" (MCQ Exam, Monthly Exam, Exam) is an EXAM; text that spans several columns ("(26 Oct- 17 Nov)") goes into the topic.
5. Flag for the preview (G1): a date outside the routine's month, or out of order; a weekday that does not match the date; no instructor; an instructor name not known (G4); an exam row; the same subject and lecture twice; an unreadable time. Flags never block saving; they guide the check.
6. The student copy (G2): render each page at 150 dpi, paint the Instructor column (from its header x range, across every table row) in the cell's background colour, stamp "SYMAM LAW" as a diagonal light watermark, and write the images as a PDF. The original stays for staff only.

Tests use the sample PDF in this folder as a fixture: the number of rows, each flag above, the merged dates, and that no instructor name can be found in the text of the student copy.

## 6. API

All under `/api/v1`, behind the offline switch, audited, with version checks (409 on a stale edit).

**Instructors** (Master Admin and staff): `GET/POST /offline-instructors`, `PATCH /offline-instructors/{id}`, `POST /offline-instructors/{id}/archive`.

**Batch rates**: `GET /offline-batches/{id}/rates`, `PUT /offline-batches/{id}/rates/{instructorId}` {rate}, `DELETE` for none.

**Routines**:
- `POST /offline-batches/{id}/routines` (multipart PDF, month) → creates a PREVIEW with the parsed rows, flags and, for a month that already has a routine, the difference (added, changed, cancelled, untouched settled).
- `GET /offline-batches/{id}/routines`, `GET /offline-routines/{id}` (preview or confirmed rows).
- `PATCH /offline-routines/{id}/rows/{index}` (fix a row in the preview), `POST /offline-routines/{id}/instructor-matches` (map an unknown name to an instructor or a new one).
- `POST /offline-routines/{id}/confirm` → applies the classes, makes the student copy, supersedes the earlier version, notifies the batch's onboarded students once (F12). `DELETE /offline-routines/{id}` discards a preview.
- `GET /offline-routines/{id}/original.pdf` (staff), `GET /offline-routines/{id}/student.pdf` (staff, for checking).

**Classes**: `GET /offline-batches/{id}/classes?month=&status=&instructorId=`, `PATCH /offline-classes/{id}` (instructor, override, date, time, subject, topic), `POST /offline-batches/{id}/classes` (add a class by hand).

**Settling**: `POST /offline-classes/{id}/payment` {amount, method, reference, paidOn, note}; `POST /offline-classes/{id}/not-taken` {reason}; `POST /offline-class-payments/{id}/approve|reject|void`; `POST /offline-class-decisions/{id}/approve|reject`. Staff entries are PENDING; the Master Admin's are approved at once (F9).

**Unsettled page**: `GET /offline-classes/unsettled?batchId=&instructorId=&from=&to=` (HELD, PAID_PENDING and NOT_TAKEN_PENDING, oldest first) and `GET /offline-classes/unsettled/counts`.

**Student**: `GET /me/admission/routine` (the confirmed classes of the student's batch: date, day, time, subject, topic, kind; never instructor or pay) and `GET /me/admission/batch-routine.pdf` changed to serve the student copy of the latest confirmed routine (and `?month=` for others).

**Finance** (Master Admin only, G3): `GET /finance/summary?from=&to=&sources=&batchId=` (income and expense totals per source, net), `GET /finance/monthly` (per month per source), `GET /finance/batches` (per batch: fees, instructor cost, net), `GET /finance/instructors?from=&to=` (offline: owed, paid, pending per instructor), `GET /finance/entries?…` (the rows behind a figure, paged, exportable as CSV).

Finance sources: income — `ONLINE_ORDERS` (paid `live_exam_orders`, net of refunds), `DIRECT_ENROLMENT` (non-voided `live_exam_direct_payments`), `OFFLINE_FEES` (approved `offline_fee_payments`), `OTHER_ONLINE` (G7); expense — `STAFF_PAY` (non-voided PAYMENT rows of `staff_ledger`), `OFFLINE_INSTRUCTORS` (approved `offline_class_payments`). Money is counted on the date it was paid (cash basis), in Dhaka time.

## 7. Notifications

| Event | To | How |
|---|---|---|
| Routine confirmed (new or changed) | Onboarded students of the batch | Existing BATCH_ROUTINE_UPDATED notice, once per confirm. |
| Daily digest, 21:00 Dhaka, only when there are held unsettled classes | Master Admin and staff | Staff notification OFFLINE_CLASSES_TO_SETTLE: "{n} offline classes are waiting for payment or a not-taken mark. Check the offline instructor payments page." One per person per day (dedupe on the date). |
| Weekly reminder (Sunday 21:00) while any class has been unsettled for more than 7 days | Master Admin and staff | OFFLINE_CLASSES_OVERDUE: "{n} offline classes have waited more than a week." |
| Staff entries waiting for approval | Master Admin | Folded into the daily digest as a second line ("{n} entries wait for your approval"), not one notice per entry (F14). |
| Approval or rejection of an entry | The staff member who made it | One notice per decision batch: when the Master Admin approves several at once, one notice ("{n} of your entries were approved"). |

The job copies the existing reminder jobs: one run a day, missed runs caught up, dedupe keys, suspended staff skipped.

## 8. UI and UX

The same principles as the admission pages (offline admission plan 6.3): one clear next action, what is missing shown not hidden, money states in fixed colours, confirm with a summary before money moves, nothing lost, links that land in the right place, the admin panel's own components, usable at 360 px.

### 8.1 Batch page (edited)

- Tabs: **Overview** (today's details), **Classes** (new), **Instructor rates** (new), **Routines** (new), **Students** (today's list).
- **Routines tab**: one card per month (November 2026 · version 2 · confirmed 1 Nov by Shahriar · 46 classes · student copy ✓) with "View original", "View student copy" and "Upload changed routine". "Upload routine" asks for the month first, then the file.
- **Import preview** (a full page, not a dialog, because it is long): a summary bar (46 rows · 3 flagged · 1 new instructor) and a table grouped by date (Date / Day / Time / Subject / Topic / Instructor / Kind), with flagged rows tinted and a reason under them ("Date is outside November", "No instructor yet", "Exam row: set who is paid"). Each cell edits in place. Unknown names show a "Match instructor" chip that opens a small picker (existing instructors, or "Add Milon as new"). For a re-upload, a difference view: Added (green), Changed (old → new), Cancelled (struck through), Settled and untouched (grey, locked). The primary button "Confirm 46 classes" is off until every unknown name is matched; flags do not block it. "Discard" with a confirmation.
- **Instructor rates tab**: a table of the batch's instructors (from its classes) with an editable rate each, "Saved ✓" on change, and a note: "A class can override this on its own row." Instructors with classes but no rate are listed first with "Set a rate".
- **Classes tab**: filters (month, instructor, status) in the URL; a list grouped by date, each class a row: time, subject and topic, instructor (or "Set instructor"), amount (rate or override, with an "overridden" mark), and a status pill. Row actions follow the status: Scheduled → Edit; Held → "Record payment" and "Mark not taken"; Pending → "Waiting for approval" (and Approve / Reject for the Master Admin); Paid / Not taken → read only (Void for the Master Admin). On a phone, each class is a card.

### 8.2 Offline instructor payments page (new, F13)

- Nav: in the Offline admission group, "Instructor payments", with a badge for the unsettled count.
- Top: four cards: "Held, not settled" (count and amount), "Waiting for approval" (Master Admin), "Paid this month", "Not taken this month".
- Tabs: **To settle** (held classes, oldest first), **Waiting for approval**, **History** (paid and not taken, filterable by date).
- Filters: batch, instructor, date range; grouped by instructor by default ("Milon · 3 classes · ৳1,500 due"), each group expandable to its classes, so the admin can work through one instructor at a time.
- Each class row: date and time, batch, subject and topic, amount due, and the two actions. "Record payment" opens a short dialog prefilled with the due amount and today's date, the method and reference, and a note when the amount differs from the due amount. "Mark not taken" asks for a reason (one-tap reasons: instructor absent, class cancelled, holiday) and confirms.
- Approvals (Master Admin): select several and "Approve N" after one confirmation that lists them; reject asks for a reason.
- Empty states: "Nothing to settle. Classes appear here after their time has passed."

### 8.3 Offline instructors page (new)

A simple list: name, phone, batches taught, classes this month, owed now; "Add instructor"; an instructor's page shows their classes across batches (owed, paid, pending) and their known names (aliases), with merge for duplicates (moves their classes and rates to one record).

### 8.4 Finance page (new, Master Admin, F15)

- Nav: a new "Finance" item at the top of the Money group.
- Filter bar (in the URL): date range (presets: this month, last month, this year, custom), sources (two groups of checkboxes: Income — Online orders, Direct enrolment, Offline fees, Other online; Expense — Staff pay, Offline instructors), batch (offline only), and grouping (month or week).
- Top row: three cards: Income, Expenditure, Net (green or red), each with the change against the previous period.
- A chart of income and expenditure per month as grouped bars with a net line; legend toggles a source on and off (built with the shared chart rules: one colour per source, the same colours everywhere on the page, readable in light and dark).
- Tables under it, as tabs: **By source** (each source's total and share), **By batch** (fees collected, instructor cost, net, margin %), **By instructor** (offline: classes, paid, owed, pending), **Entries** (every row behind the figures, paged, with "Export CSV").
- Every figure links to the entries behind it. Amounts in taka with Bangla digits in Bangla.
- Phone: cards stack, the chart scrolls sideways in its own box, tables become cards.

### 8.5 Student (edited)

- My admission → Routine: a clean table of the batch's classes (date, day, time, subject, topic; exams marked) for the current month with a month switcher, and "Open the PDF" which opens the student copy in the PDF viewer (watermarked, no instructor column). No instructor name anywhere.
- The routine notice links to that section.

## 9. Side effects

1. **Instructor names reach students today**: the current student routine route serves the original PDF. Stage 3 must switch it to the student copy before any routine with names is uploaded under the new flow; until then, keep the old behaviour only for routines uploaded before V89.
2. The old single routine on `offline_batches` (V81, V86): migrate it into `offline_batch_routines` as a confirmed routine for its upload month, with no classes (a plain PDF), and generate its student copy only if it can be parsed; otherwise mark it "staff only" so it is not shown to students with names.
3. The routine permission (OFFLINE_BATCH_ROUTINE, staff and Master Admin) covers uploading and confirming; new permissions: OFFLINE_CLASS_SETTLE (record payment and not taken: staff and Master Admin), OFFLINE_CLASS_APPROVE (Master Admin), FINANCE_READ (Master Admin).
4. Staff notification kinds: OFFLINE_CLASSES_TO_SETTLE, OFFLINE_CLASSES_OVERDUE, OFFLINE_ENTRIES_DECIDED; texts in both languages, or the rows render blank.
5. The OpenAPI operations list test (AuditLogIT) and the staff notification kind list test must be updated with the new routes and kinds before running the gates.
6. The feature switch: the offline routes answer 404 when it is off; the Finance page still works, without its offline sources.
7. Large routines: a 3-page PDF renders in well under a second; the render runs in the confirm request with a 20 MB cap; if it fails the confirm fails and nothing is saved.
8. Time zone: class times are Dhaka; the digest runs at 21:00 Dhaka; "held" compares against Dhaka now.
9. Refunds on online orders reduce online income; voided direct payments and voided fee payments are excluded.
10. Privacy: instructor phones are staff-only; pay amounts never appear in any student response.
11. Dark mode and Bangla digits on all new pages; Bangla copy written as natural Bangla matching the existing terms.

## 10. Stages

| Stage | Scope | Gate |
|---|---|---|
| 1. Data | V88–V90, entities, permissions, migration of old routines (side effect 2), instructor list API, batch rates API. | Gate 1 |
| 2. Reading the PDF | Parser with positions, flags, matching of names, preview API, confirm and re-upload difference, student copy rendering. | Gate 2 |
| 3. Classes and settling | Classes API, payment and not-taken with approval, void, unsettled list and counts, student routine routes switched to the student copy. | Gate 3 |
| 4. Notifications | Digest job, weekly reminder, decision notices, student routine notice on confirm. | Gate 4 |
| 5. Finance API | Sources, summary, monthly, per batch, per instructor, entries, CSV; the check for older purchase tables (G7). | Gate 5 |
| 6. Staff web | Batch page tabs, import preview, rates, classes, instructor payments page, instructors page, Finance page, notification texts. | Gate 6 |
| 7. Student web and walkthrough | Routine table and student copy on My admission; the worked example (section 2) end to end; release notes. | Gate 7 |

## 11. Testing

Rules as in the admission plan (section 10.1): test at the end of each stage; update existing tests that pin lists (permissions, kinds, OpenAPI operations, nav items) before running; add new tests in the same stage; never weaken an assertion; run the full suites on a fresh database at each gate; record every result in the build log.

| Gate | What it checks |
|---|---|
| 1 | Migrations apply on an empty database and on a copy of today's schema; the old routine moves into the new table; rates and instructors with roles (staff 403 on approve). |
| 2 | The sample PDF: 46 rows (or the exact count), merged dates, the 27-07 flag, blank instructors, exam rows, "Milon" unknown then matched; a PDF without the header is refused; the student copy contains no instructor name in its text; re-upload difference (added, changed, cancelled, settled untouched). |
| 3 | Held is computed from the end time; payment refused before the end, without an amount, or twice; staff entries pending, Master Admin approved at once; approve, reject, void restore the right status; substitute change then pay; rates change unsettled amounts only; students never get instructor fields or the original PDF. |
| 4 | Digest at 21:00 with a fixed clock, only when something is unsettled, once per day per person; weekly reminder after 7 days; approvals folded into the digest; decision notices grouped. |
| 5 | Each source's total against seeded rows; refunds and voids excluded; the date filter on paid dates in Dhaka; per batch net; CSV columns; switch off hides offline sources. |
| 6 | Component tests for the preview (flags, matching blocks confirm), rates (saved state), classes actions per status and role, the payments page grouping and bulk approve, the Finance filters and totals; full web suite, type-check, lint; phone checks at 360 and 390 px. |
| 7 | Student routine without names (table and PDF); the walkthrough on the sample database with the sample PDF; both full suites green. |

## 12. Open items

None blocking. G1–G7 are choices to confirm; the rest is decided.

## Browser check (15 Nov, after the build)
Run on coaching_center_review with the client's routine PDF (flow 5, 54/54), screens reviewed: instructor added; upload and preview
(43 rows, 24 earlier dates folded, flags in words); "Milon" matched to an existing instructor, which reaches all 12 of his classes,
earlier ones too; confirm; the student's routine and student copy carry no names; rates; a substitute typed on an earlier class with its
own rate; staff payment with a note for a lower amount, a not-taken mark, a class not held yet refused; Master Admin approve, reject,
own payment at once; staff decision notice; a rate change leaves the paid class alone; a fee recorded; Finance by batch (fees 3000,
instructor cost 950, net 2050, 68.3%), by instructor, entries and CSV, phone width, refused to the System Admin; a re-upload keeps
settled classes.

Fixed during the check:
- Students saw "Your routine will appear here" after a routine was confirmed: hasRoutine read only the old single-PDF column. It now
  also counts a confirmed routine (OfflineAdmissionService, with a test in OfflineRoutineIT).
- Assign on a class with no instructor crashed the batch page: the API leaves out empty fields, and the dialog read the missing name.
- Every class showed an "Overridden" pill, for the same reason; the pay dialog and the instructor grouping had the same strict checks.
- Recording a fee always failed with "Check the highlighted fields" and nothing highlighted: the API needs a reference, the dialog
  treated it as optional. It is now required, filled in for cash (CASH-yyyymmdd) as in the direct-enrolment dialog, and its error shows.
- The routine check page cut off times and the kind at 1280 px and crushed subject and topic at 1024 px: the table now starts at
  1280 px, with a four-column card below it.

Noted, not changed: the sample's mistyped date 27-07-2026 is flagged out of order in the preview, but confirming does not require a fix,
so it becomes a held class on 27 July, before the batch starts.
