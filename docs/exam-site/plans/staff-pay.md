# Plan: staff pay, payout accounts and the staff notification centre

Status: planned, not started. Written 5 November 2026 for Shahriar and the agent who builds it. The site is live and takes real payments, so section 2 (how this ships without disturbing the running site) governs every phase.

## 1. What is being built

The client pays two kinds of staff by hand, and wants the site to keep the record:

- **System Admin:** a fixed monthly salary.
- **Instructor:** paid per written answer sheet they mark, 50 BDT a sheet for now. A sheet is earned when the Master Admin publishes its result.

Money moves outside the site (bank, bKash, Nagad, Rocket). The site shows:
- what each person has earned or is due
- what has been paid
- the balance
- when the next payment is due

Every role sees its own part:

| Role | Sees |
|---|---|
| Master Admin | A **Staff pay** page: everyone's balance, what is owed and when, analytics, each person's payout accounts (read only), and every payment recorded. Records payments and adjustments, sets salaries and the sheet rate. A **notification centre** that reminds him whom he owes |
| System Admin | **My pay**: salary, next pay date, payments received, balance. Their own payout accounts. Their notifications |
| Instructor | **My pay**: answer sheets marked, sheets waiting to be published, sheets published, money earned, payments received, balance, per exam. Their own payout accounts. Their notifications |

The notification centre is built for staff in general. Pay is its first user; other ed-tech notifications come later and are out of scope.

### 1.1 Decisions (Shahriar, 5 November 2026)

| ID | Decision |
|---|---|
| P1 | A sheet is one student's submitted written answer script. Retake scripts count separately. An automatic zero for a paper that was not submitted is not a sheet |
| P2 | A sheet is paid once. A correction published later pays nothing more |
| P3 | The instructor whose mark was first published earns the sheet. A sheet marked by the Master Admin or a System Admin earns nothing |
| P4 | One default rate, 50 BDT, set by the Master Admin, with an optional rate per instructor. Each sheet keeps the rate it was earned at; a later rate change never alters past earnings. ~~No rate per exam~~ **Superseded on 5 November by P18:** each written exam carries its own rate |
| P5 | Before publishing, the instructor sees the sheets marked and waiting, and the amount they would bring. Only published sheets are owed |
| P6 | Both the instructor and the Master Admin see, per instructor and per exam, how many sheets were marked, how many are waiting, and how many the Master Admin has published, with the money for each |
| P7 | System Admin: a monthly amount, a start date, an optional end date and a pay day. Each month falls due on the pay day of the following month (pay day 5: November's salary is due 5 December). No pro-rating |
| P8 | Bonuses, advances, deductions and part months are one-off **adjustments** (plus or minus) with a note |
| P9 | The Master Admin is not paid through this |
| P10 | A payment record holds the amount, date, method (bank, bKash, Nagad, Rocket), the receiving account, a reference, and a note. Partial payments are allowed. Each person has a running balance: earned or due, minus paid |
| P11 | Instructors are paid on a running balance whenever the Master Admin chooses. He is reminded when a balance has been unpaid for a set number of days (default 14) |
| P12 | Payout accounts: bank (account name, number, bank, branch, routing number) or mobile wallet (bKash, Nagad, Rocket number). Several per person, one preferred. **Only the owner can add, change or remove them, not even the Master Admin.** The owner and the Master Admin can see them. Any change notifies the Master Admin |
| P13 | Staff do not confirm receipt. They see each payment and its reference |
| P14 | Notifications are in the admin site: a bell and a page, read and unread, each linking to the right page. In-app only for now; email can be added later |
| P15 | Staff are notified too: "payment recorded", and for instructors "results published, you earned ৳X" |
| P18 | **Decided by Shahriar, 5 November:** every written exam records its instructor and its pay per sheet, set in the exam's "Instructor and pay" section (not in the new-exam dialog; moved there on request after the first build). A change to either is recorded, and the instructors affected and the Master Admin are told. Rate order for a sheet: the exam's rate, else the instructor's own rate, else the default (Phase 8) |
| P17 | **Decided by Shahriar, 5 November (after build):** no separate tracking of who first published a sheet. If the Master Admin publishes a sheet and later unpublishes it, he publishes the corrected version himself. A correction published by an instructor after an earlier publish is not treated separately. This leaves the gap described in the Phase 4 report as accepted |
| P16 | Analytics on the Master Admin's page: owed now (salaries and marking), paid this month, payroll by month, each person's earned, paid and balance, and sheets per instructor by month. **No comparison with student payment income** |

## 2. Shipping safely on the live site

These rules hold for every phase:

1. **New tables only.** No `ALTER` on `users`, profiles, `live_exam_participants`, orders or any existing table. No existing row is changed by any migration. Migrations follow `V70` (`V71` onward). `V69` and `V70` (refunds, notice board) are not on the live server yet, so they ship first or in the same release; Flyway applies them in order.
2. **Account creation, sign-in, activation and profiles are not touched.** A System Admin or instructor with no salary, rate or payout account works exactly as today. The pay pages show "not set yet" and invite them to add details. Nothing is required at sign-in and nothing blocks their work.
3. **Marking and publishing code is not touched.** Earnings come from a separate job that **reads** the columns marking already writes (`evaluated_by`, `published_at`) and writes only to the new pay tables. If the job fails, marking and publishing carry on, and the job catches up on its next run.
4. **Additive edits only in shared code:**
   - new permission constants and a new permission contributor class
   - new audit actions and error codes
   - new staff menu items
   - a bell in the staff header
   - the new routes in `AuditLogIT`

   Nothing existing is renamed or removed.
5. **The two jobs can be switched off** from the env file (`STAFF_PAY_JOBS_ENABLED=false`) without a new build, if anything misbehaves after a release.
6. **Each phase ships alone** and leaves the site working. Menu items appear only with the phase that makes them work.
7. **Personal financial data:**
   - Payout account numbers are visible only to the owner and the Master Admin. Lists show them masked (`•••• 4821`).
   - They never appear in notifications, logs or the audit log; the audit log records the account's id only.
   - A payment keeps a copy of the account it was paid to, so a later edit never rewrites history.
8. **Before each release:** rehearse the migrations on a copy of the live database dump on the PC, take a fresh dump on the server, check that no sitting is open, then deploy. This is the usual routine in `reports/client-review-deploy.md`.

## 3. Data (all new tables)

| Table | Holds | Notes |
|---|---|---|
| `staff_notifications` | One row per recipient: `recipient_id`, `kind`, `payload` (JSONB: names, amounts, counts; never account numbers), `link`, `created_at`, `read_at`, `dedupe_key` | Unique `dedupe_key` (nullable) stops a reminder job from repeating itself. Index on `(recipient_id, read_at, created_at DESC)` |
| `staff_payout_accounts` | `user_id`, `kind` (`BANK`, `BKASH`, `NAGAD`, `ROCKET`), bank fields (`account_name`, `account_number`, `bank_name`, `branch`, `routing_number`), `wallet_number`, `preferred`, timestamps, `deleted_at` | A check constraint requires the bank fields for `BANK` and the wallet number for wallets. At most one preferred live account per user (partial unique index). Soft delete |
| `staff_pay_defaults` | One row: `sheet_rate` (50.00), `unpaid_reminder_days` (14), `salary_due_soon_days` (3), `updated_by`, `updated_at` | Created with its one row by the migration |
| `staff_pay_settings` | Per person: `user_id` (PK), `monthly_salary`, `pay_day` (1–28), `salary_start`, `salary_end`, `sheet_rate` (instructor override), `updated_by`, `updated_at` | Row only for people the Master Admin has set up |
| `staff_ledger` | Every money line: `user_id`, `kind` (`MARKING`, `SALARY`, `ADJUSTMENT`, `PAYMENT`), `amount`, `due_on` | Details below |

`staff_ledger` columns by kind:

| Kind | Extra columns | Rule |
|---|---|---|
| `MARKING` | `participant_id` (unique), `exam_id`, `sheet_rate` | One row per published sheet, `amount = sheet_rate`, `due_on` = publish date (Dhaka) |
| `SALARY` | `period` (`2026-11`) | Unique `(user_id, period)`; `due_on` = pay day of the next month |
| `ADJUSTMENT` | `note` (required) | Amount may be negative |
| `PAYMENT` | `paid_on`, `method`, `account_snapshot` (text, for example "bKash 017••••4821"), `reference`, `note` | Amount positive; subtracted from the balance |

All rows: `created_by`, `created_at`, and `voided_at`, `voided_by`, `void_reason`. A mistaken payment or adjustment is **voided** with a reason, never deleted, and a voided row leaves the balance.

**Balance** = sum of live `MARKING`, `SALARY` and `ADJUSTMENT` amounts − sum of live `PAYMENT` amounts. **What is overdue** is worked out oldest first: payments settle the oldest dues first, and any due whose date has passed and is not yet covered is overdue.

## 4. Permissions

A new `StaffPayPermissionContributor` (beside the existing contributors, none of them edited):

| Permission | Granted to | Allows |
|---|---|---|
| `STAFF_NOTICE_READ` | Master Admin, System Admin, Instructor | Own notifications |
| `STAFF_PAY_SELF` | System Admin, Instructor | Own pay pages and own payout accounts (read and write) |
| `STAFF_PAY_MANAGE` | Master Admin | Everyone's pay, settings, payments, adjustments, analytics, and payout accounts read only |

There is **no route** by which anyone writes another person's payout accounts. The write routes exist only under `/api/v1/me/...` and act on the signed-in user.

## 5. Phases

Each phase: API, web, checks. Each can ship alone.

### Phase 1. Staff notification centre (foundation)

**API**
- `V71__staff_notifications.sql`.
- `StaffNotificationWriter`: `notify(recipientId, kind, payload, link, dedupeKey)`. Insert-or-ignore on `dedupe_key`. Used by later phases.
- Routes (`STAFF_NOTICE_READ`):

  | Route | Does |
  |---|---|
  | `GET /api/v1/me/staff-notifications?page=&unread=` | List, newest first |
  | `GET /api/v1/me/staff-notifications/unread-count` | The bell's number |
  | `POST /api/v1/me/staff-notifications/{id}/read` | Marks one read |
  | `POST /api/v1/me/staff-notifications/read-all` | Marks all read |

- Kinds are a fixed list in code: `PAYOUT_ACCOUNT_CHANGED`, `MARKING_PUBLISHED_OWED`, `MARKING_PUBLISHED_EARNED`, `PAYMENT_RECORDED`, `PAYMENT_VOIDED`, `SALARY_DUE_SOON`, `SALARY_OVERDUE`, `MARKING_UNPAID_REMINDER`. More can be added later without a migration.

**Web**
- A bell with an unread count in the staff header for all three roles. It is separate from the student inbox, and its query only runs for staff.
- `/staff/notifications`: list with "Unread" and "All" tabs, "Mark all read", and each row's text built from its kind and payload in the viewer's language, linking to its page.

**Checks:** a notification inserted for a user shows in their bell and list and not in another user's. Marking read updates the count. A repeated `dedupe_key` adds nothing.

### Phase 2. Payout accounts (owner only)

**API**
- `V72__staff_payout_accounts.sql`.
- Routes (`STAFF_PAY_SELF`, the signed-in user's own accounts only):

  | Route | Does |
  |---|---|
  | `GET /api/v1/me/payout-accounts` | Own accounts, numbers in full |
  | `POST /api/v1/me/payout-accounts` | Adds one; the first becomes preferred |
  | `PUT /api/v1/me/payout-accounts/{id}` | Edits one |
  | `DELETE /api/v1/me/payout-accounts/{id}` | Soft delete; if it was preferred, the next becomes preferred |
  | `POST /api/v1/me/payout-accounts/{id}/preferred` | Makes it preferred |

  Validation:
  - Wallet numbers in the Bangladesh mobile form, the same rule as phone numbers.
  - Bank fields required for `BANK`.
  - A routing number of 9 digits.
  - At most 5 live accounts per person.
- Each change: an audit entry (account id only), and a `PAYOUT_ACCOUNT_CHANGED` notification to the Master Admin ("Rahim changed a payout account"; no number in it).
- `GET /api/v1/staff-pay/people/{userId}/payout-accounts` (`STAFF_PAY_MANAGE`): read only, numbers in full. Comes with Phase 3's person page.

**Web**
- **Payout accounts** section on `/staff/account` for System Admins and instructors: list (preferred first), add and edit form (bank or wallet), delete with a confirm, and "Make preferred". The Master Admin's account page does not show it (P9).

**Checks:**
- A System Admin adds a bKash and a bank account, changes the preferred one, and the Master Admin gets a notification for each change.
- An instructor cannot read or change another person's accounts (403 or 404).
- The Master Admin has no write route (any attempt is 403).
- The audit log and notifications hold no account number.

### Phase 3. The pay ledger, payments and adjustments

**API**
- `V73__staff_pay.sql`: `staff_pay_defaults` (with its row), `staff_pay_settings`, `staff_ledger`.
- `StaffPayService`: balance, the oldest-first overdue split, summaries.
- Master Admin routes (`STAFF_PAY_MANAGE`):

  | Route | Does |
  |---|---|
  | `GET /api/v1/staff-pay/people?role=&owing=` | Every System Admin and instructor (active or not): role, balance, overdue amount, last payment, pay set up or not |
  | `GET /api/v1/staff-pay/people/{userId}` | Settings, summary, preferred payout account, ledger page |
  | `GET /api/v1/staff-pay/people/{userId}/ledger?page=` | All lines, newest first, voided ones marked |
  | `POST /api/v1/staff-pay/people/{userId}/payments` | Records a payment. `accountId` picks one of the person's payout accounts; its text is copied into `account_snapshot`. A payment larger than the balance needs `allowAdvance: true`. Notifies the person (`PAYMENT_RECORDED`) |
  | `POST /api/v1/staff-pay/people/{userId}/adjustments` | Amount ± and a required note |
  | `POST /api/v1/staff-pay/ledger/{id}/void` | Voids a payment or adjustment with a reason. Marking and salary lines cannot be voided; a wrong one is offset by an adjustment. Notifies the person on a voided payment |
  | `GET`/`PUT /api/v1/staff-pay/defaults` | Sheet rate and the two reminder settings |
  | `PUT /api/v1/staff-pay/people/{userId}/settings` | Salary fields for a System Admin, rate override for an instructor. 400 for the wrong role's fields |

- Self routes (`STAFF_PAY_SELF`):
  - `GET /api/v1/me/pay/summary`: balance, total earned or due, total paid, and for a System Admin the salary and next pay date.
  - `GET /api/v1/me/pay/ledger?page=`
- Every write: one audit entry and one transaction.

**Web**
- **Master Admin**, menu item **Staff pay** (`/staff/pay`):
  - Table of people: name, role, balance, overdue, last payment, and a "Pay not set up" badge where needed.
  - Person page `/staff/pay/[userId]`:
    - a summary
    - payout accounts (read only, numbers shown, a copy button)
    - "Record payment": amount defaults to the balance; date, method, account from their list, reference, note
    - "Add adjustment"
    - the ledger, with "Void" on payments and adjustments
    - a settings panel (salary or rate)
  - `/staff/pay/settings`: default sheet rate and reminder days.
  - From `/staff/instructors` and `/staff/admins`, a "Pay" link to the person page.
- **System Admin and instructor**, menu item **My pay** (`/staff/my-pay`): summary cards, payments received (date, amount, method, the account, reference), and the ledger.

**Checks:**
- Record a payment and see the person's balance drop on both sides.
- A partial payment leaves the rest owed.
- Voiding restores the balance and keeps the row visible as voided.
- The account snapshot stays the same after the person edits that account.
- A System Admin cannot open another person's pay (403).

### Phase 4. Instructor marking pay

**API**
- `StaffEarningsSyncJob`, every 2 minutes (switchable, section 2.5). It finds written answer sheets where:
  - `published_at` is set and `deleted_at` is null;
  - `evaluated_by` is a user whose role is `INSTRUCTOR`;
  - there is no `MARKING` line for the sheet yet.

  For each, it inserts a `MARKING` line at the instructor's rate (override, else default), `ON CONFLICT (participant_id) DO NOTHING`. It reads only the existing columns and touches no marking code.
- Because a correction is published into the same sheet, the unique `participant_id` stops a second payment (P2). The job runs within minutes of a publish, so the first published marker is the one paid (P3).
- After inserting, the job groups the new lines by instructor and exam and notifies:
  - the Master Admin (`MARKING_PUBLISHED_OWED`): "Results published for *Muslim Law*: you owe *Rahim* ৳500 for 10 sheets. Balance ৳1,200."
  - the instructor (`MARKING_PUBLISHED_EARNED`): "10 sheets for *Muslim Law* were published: you earned ৳500."
- Counts (P5, P6), per instructor and per exam, read live from `live_exam_participants` and the ledger:

  | Figure | Counted from |
  |---|---|
  | Marked | Written sheets with a mark by this instructor, in the released or draft columns (`evaluated_by` or `pending_evaluated_by`) |
  | Waiting to be published | Marked by them, `published_at` null |
  | Ready for release | Of those, `ready_for_release_at` set |
  | Published | Their `MARKING` lines |
  | Earned | Sum of those lines |
  | Expected | Waiting × their current rate |

- Routes:
  - `GET /api/v1/me/pay/marking` (instructor): totals and the per-exam table.
  - `GET /api/v1/staff-pay/people/{userId}/marking` (Master Admin): the same for one instructor.
- `MARKING_UNPAID_REMINDER`: a daily job tells the Master Admin when an instructor's oldest unpaid marking is older than `unpaid_reminder_days`. At most once a week per instructor (dedupe key: user and week).

**Web**
- Instructor **My pay** gains:
  - cards: marked, waiting to be published, published, earned, expected
  - a per-exam table: exam, marked, waiting, published, earned
  - the current rate
- Master Admin person page for an instructor: the same cards and table.

**Checks** (on the PC with sample data):
- An instructor marks 3 sheets: 3 waiting, ৳150 expected, ৳0 owed.
- The Master Admin publishes. Within 2 minutes: 3 published, ৳150 earned, both notified, balance ৳150.
- Publish a correction to one sheet: no new line.
- A sheet marked by the Master Admin: no line.
- A retake sheet marked by the instructor: its own line.
- Changing the default rate leaves existing lines at 50.
- Switching the job off stops new lines. Switching it back on catches up.

### Phase 5. System Admin salary

**API**
- `StaffSalaryJob`, daily at 06:00 Dhaka (switchable). For each System Admin with salary settings, it writes the `SALARY` line for every finished month from `salary_start` to `salary_end` (or last month) that has none yet. `amount = monthly_salary`; `due_on` = pay day of the next month; unique `(user_id, period)`. A late or missed run catches up.
- Reminders to the Master Admin from the same job:
  - `SALARY_DUE_SOON`: `salary_due_soon_days` before a due date that is not yet covered.
  - `SALARY_OVERDUE`: the day after it, then weekly while it stays unpaid.

  Dedupe keys per user, period and kind (and week).
- `GET /api/v1/me/pay/summary` for a System Admin adds: monthly salary, next pay date, and the amount due on it.

**Web**
- System Admin **My pay**: salary, next pay date with its amount, and a list of months (period, due date, paid or due or overdue).
- Master Admin person page: the same, plus the salary settings form (amount, start, end, pay day).

**Checks:**
- Salary 20,000, start 1 October, pay day 5. On 1 November the October line appears, due 5 November. A "due soon" notice arrives on 2 November and an "overdue" on 6 November if unpaid.
- A payment of 20,000 clears it.
- An end date stops new lines.

### Phase 6. Master Admin overview and analytics

**API**
- `GET /api/v1/staff-pay/overview`:
  - owed now, split into salaries and marking, and the overdue part
  - paid this month
  - people owed, sorted by overdue then balance
  - salary dues in the next 30 days
- `GET /api/v1/staff-pay/analytics?from=&to=` (months):
  - payroll by month: earned or due by kind, and paid
  - sheets published per instructor by month
  - each person's earned, paid and balance for the range

**Web**
- `/staff/pay` gets an overview band above the people table: four figure tiles (owed now, overdue, paid this month, due in 30 days) and "Who you owe next".
- An **Analytics** tab:
  - payroll by month, as a stacked bar of salary and marking, with a "paid" line
  - sheets per instructor by month
  - a per-person table for the chosen range
- Charts follow the project's existing chart conventions and work in both themes and both languages.

**Checks:** the figures equal the sums of the ledger for the same range, voided rows are left out, and empty months show as zero.

### Phase 7. Release

- Release in two drops, or one if all phases are ready:
  - **Drop 1:** phases 1–4, so instructors can start being recorded.
  - **Drop 2:** phases 5–6.
  - **Drop 3:** phase 8 (migration `V74`). Check the watcher's first run in the log: baseline rows only, no notices.
- Per drop:
  1. Rehearse migrations on a copy of the latest live dump.
  2. Dump on the server and check that no sitting is open.
  3. Deploy the jar.
  4. Deploy the web with `SITE_URL=https://symamlaw.com STAFF_URL=https://admin.symamlaw.com`.
- After Drop 1: the Master Admin sets the default rate (50) and any instructor overrides. Instructors add their payout accounts.
- After Drop 2: he sets each System Admin's salary.
- Record each drop in `reports/client-review-deploy.md`.
- Existing data: there are no instructors on the live site, so nothing needs back-paying. If any instructor sheets were published before Drop 1, the job pays them at the rate set at release; check the job's first run in the log.

### Phase 8. Instructor and pay per written exam (added 5 November)

Decision P18. This phase ships as its own drop after Phases 1 to 6. It follows section 2: new tables only, and no change to existing rows or to the marking and publishing code.

**What exists today, and stays**
- `live_exams.instructor_id` holds the assigned instructor. It is set by `POST`/`DELETE /api/v1/live-exams/{id}/instructor` (`LIVE_EXAM_ASSIGN`, Master Admin only).
- On a published exam the assignment is staged and takes effect only when the Master Admin publishes the changes.
- MCQ exams never have an instructor (409 `INSTRUCTOR_NOT_FOR_MCQ`).
- All of this is reused unchanged.

**Data (`V74__live_exam_pay_terms.sql`, new tables)**

| Table | Holds |
|---|---|
| `live_exam_pay_terms` | `exam_id` (PK, → `live_exams`), `sheet_rate` (`NUMERIC(12,2)`, > 0), `updated_by`, `updated_at`. One row per written exam that has its own rate |
| `live_exam_assignment_log` | One row per change: `exam_id`, `kind` (`ASSIGNED`, `UNASSIGNED`, `RATE_SET`, `RATE_CHANGED`), `instructor_id`, `previous_instructor_id`, `sheet_rate`, `previous_sheet_rate`, `changed_by` (null when unknown), `created_at`. Append only; the exam's history |

Existing written exams get no row. They keep earning at the instructor's own rate or the default, exactly as in Phases 1 to 6, until the Master Admin sets a rate on them.

**Rate**
- A written exam's rate is a required field when creating it in the new-exam dialog, prefilled with the default (50).
- The Master Admin can change it later on the exam page. It takes effect at once and is **not** staged with publish changes, because students never see it.
- The earnings job (Phase 4) reads the rate as `live_exam_pay_terms.sheet_rate`, else the instructor's own rate, else the default. This is a change to its candidate query only.
- Each `MARKING` line still copies the rate in force when it is written (within 2 minutes of the publish), so later changes never alter earned lines.

**Instructor at creation**
- The new-exam dialog for a written exam gets an optional instructor picker. After the exam is created, new code calls the existing assign service, so the existing rules apply.
- The instructor stays optional at creation, because the marker is often chosen later. On a written exam with no instructor, the exam page shows "No instructor assigned" as a warning, not a block. Publishing rules do not change (see open question O2).

**Recording and telling people about changes**
- **Rate changes** are written to the log by the new rate route itself, with the actor.
- **Instructor changes** are detected by a small watcher job, `ExamAssignmentWatcher`, every 2 minutes, under the same switch as the pay jobs:
  - It compares each written exam's live `instructor_id` with the last instructor in the log, and writes `ASSIGNED` or `UNASSIGNED` rows.
  - This catches every path (a draft assignment, a staged one taking effect on publish, an unassignment) without changing the existing assignment or publishing code.
  - The actor is taken from the latest matching audit entry (`LIVE_EXAM_INSTRUCTOR_ASSIGN` or `_UNASSIGN`, or the publish-changes entry), else left null ("changed").
- **First run on the live site:** the watcher records every current assignment as a baseline, **without sending any notice**, so release does not flood anyone. Notices start from the next real change.
- **New notification kinds** (payloads hold names, the exam title through `fitText`, and amounts only):

  | Kind | To | Text |
  |---|---|---|
  | `EXAM_ASSIGNED` | the new instructor | "You are the marker for *Muslim Law*, ৳60 per sheet" |
  | `EXAM_UNASSIGNED` | the previous instructor | "You are no longer the marker for *Muslim Law*. Sheets you already marked are still paid" |
  | `EXAM_RATE_CHANGED` | the assigned instructor | "Pay for *Muslim Law* changed from ৳50 to ৳60 per sheet, for sheets published from now" |
  | `EXAM_PAY_TERMS_CHANGED` | every Master Admin except the one who made the change | "*Name* changed *Muslim Law*: marker Rahim → Karim, ৳50 → ৳60" |

- Who earns is unchanged: the instructor whose mark is published (P3), not the one assigned. A sheet marked before a reassignment is still paid to its marker.

**Routes**

| Route | Who | Does |
|---|---|---|
| `GET /api/v1/live-exams/{id}/pay-terms` | Master Admin; the assigned instructor (read) | Current instructor, the exam's own rate if any, the effective rate, and the change log |
| `PUT /api/v1/live-exams/{id}/pay-terms` | Master Admin (`STAFF_PAY_MANAGE`) | Sets or clears the exam's rate (400 on an MCQ exam). Writes the log and the notices |
| `GET /api/v1/me/pay/exams` | Instructor | Their assigned written exams, each with the effective rate |

The existing create route gains optional `sheetRate` and `instructorId` fields, handled by new code after the exam is created. Requests without them behave exactly as today.

**Web**
- New-exam dialog, written only: "Pay per sheet" (required, prefilled 50) and "Marker" (optional picker of active instructors).
- Exam page, written only: an "Instructor and pay" section with:
  - the marker (using the existing assign and unassign actions)
  - the rate, with edit for the Master Admin
  - the change history: who, when, from and to
- Instructor My pay: an "My exams" table listing each assigned written exam and its rate per sheet. The per-exam marking table (Phase 4) also shows each exam's rate.
- Master Admin person page for an instructor: the same "My exams" table.

**Checks**
- Create a written exam with ৳60 and Instructor 1. Instructor 1 is told. The log has `ASSIGNED` and `RATE_SET`. The exam page shows both.
- Change the rate to ৳70. Instructor 1 and any other Master Admin are told. Sheets published afterwards earn ৳70, and lines already written stay at ৳60.
- Reassign to Instructor 2 on a published exam:
  - Nothing is sent until the changes are published.
  - Then Instructor 1 gets "unassigned", Instructor 2 gets "assigned", and the Master Admins get the summary.
- Release on a copy of the live data: the watcher's first run writes baseline rows only, and no notices.
- An MCQ exam refuses a rate. An exam with no rate of its own uses the instructor's rate or the default, as before.

**Decided by Shahriar, 5 November**
- O1. **Only the Master Admin**, never a System Admin, assigns instructors or sets an exam's rate. `LIVE_EXAM_ASSIGN` and `STAFF_PAY_MANAGE` stay Master Admin only, and no grant is added. With one Master Admin, the `EXAM_PAY_TERMS_CHANGED` notice usually has no recipient. The Master Admin's own record of a change is the exam's change history, which shows who, when, from and to. The notice remains for a second Master Admin, if there ever is one.
- O2. **Warning only:** a written exam with no instructor can still be published. The exam page and the publish step show "No instructor assigned" as a warning. Publishing rules do not change.

### Addendum: link to payment details on My pay (added 5 November)

On the My pay page (System Admin and instructor), a link "Update payment details" takes the person to where they manage their payout accounts (Phase 2). It is a link only: no new route, no new permission, and no change to the payout accounts section.

- The link opens the Account page (`/staff/account`) at the top, with no scroll to a section (changed on request, 5 November). The payout accounts section keeps its `id="payout-accounts"`, which is harmless.
- It appears in the My pay header, beside the title, for System Admin and instructor only. It is not shown to the Master Admin (P9).
- Label, English and Bangla: "Update payment details" / "পেমেন্টের তথ্য হালনাগাদ করুন".
- Check: the link opens the Account page at the payout accounts section, and the owner can add or edit accounts there.

## 6. Tests

Written with each phase. Run when Shahriar says.
- **Unit:**
  - balance and the oldest-first overdue split
  - salary period and due date maths, including month ends and pay day 28
  - the rate snapshot
  - payout account validation
  - notification text for each kind
- **Integration (`*IT`):**
  - permissions per role, including "no route writes another person's accounts"
  - the earnings job rules: P1 to P3, retakes, corrections, a sheet the Master Admin marked
  - the salary job and the dedupe of reminders
  - void
  - every new route added to `AuditLogIT`

  These need a Docker environment for the test database, which this PC does not have. They run wherever Docker is available; until then, the phases are checked by hand on the PC's sample database.
- **Web:** the new pages, the bell, the pay forms, the money formatting in both languages, and the role gates.

### Phase 9. Encryption of payout account numbers

**Status (7 November 2026, 04:55 Dhaka): deployed, plain columns kept.** Live with `80c1f0a`: the key is set by Shahriar
(version 1), the backfill sealed the 1 live account and is switched off again, and dual write is on. The pre-change check,
dumps and rehearsal are recorded in `../reports/client-review-deploy.md` ("Media features and payout encryption"). Still to
do: Shahriar's verification (step 7) and the later migration that drops the plain columns. Earlier status, as written: migration `V75__payout_account_encryption.sql` (adds nullable sealed columns and `key_version`, changes no data); `PayoutNumberCipher` (AES-256-GCM, a fresh nonce per value, bound to table, column and account id); the owner's account reads and writes and the ledger's account reads, which seal on write and open on read, falling back to the plain columns for rows that predate the key; `PayoutNumberEncryptionGuard` (refuses to start when sealed rows exist and the key is missing or of another version); `PayoutNumberBackfill` (off by default, verifies each batch and rolls it back on a mismatch); the properties `app.payout.encryption-key`, `app.payout.key-version` and `app.payout.backfill-enabled`; and tests `PayoutNumberCipherTest` and `PayoutEncryptionIT`. Not yet done: the key (see key custody), the pre-change check and dump, the rehearsal, the deploy, the verification, and the later migration that drops the plain columns.

Previously recorded status: planned, not started. Decided by Shahriar on 5 November 2026, after the release of Phases 1 to 8. Until it ships, payout account numbers are stored as plain text in `staff_payout_accounts`. They are shown only to their owner and the Master Admin, and masked in lists, but they are not encrypted at rest.

**Why:** these are personal bank and wallet numbers. A copy of the database, a backup or a support screen should not reveal them.

**Live data state (checked 7 November 2026, read-only):** the live database already holds payment-related data, so the migration must keep it. On the live server:
- `staff_payout_accounts`: 1 row, live, a BANK account with an account number, preferred, owned by an INSTRUCTOR. No wallet numbers.
- `staff_ledger`: 1 MARKING line, 2 ADJUSTMENT lines, and 1 PAYMENT of 100.00 BDT on 6 November 2026 (method BANK, empty `account_snapshot`).
- `live_exam_orders`: 2 SUCCEEDED student payments (exam purchases; not staff pay).
- `staff_pay_settings`: none. `staff_pay_defaults`: 1 row.

The rule for this phase is therefore: **production data is kept.** The "ship as it is" option only applies when the check below finds no payout account with a number. The check is repeated on the day of the change, because the count can change.

**Data preservation rules**
1. **Pre-change check:** count live payout accounts with a number. If zero, the migration may add encryption directly. If one or more, the in-place path below applies. Record the count in the deploy report.
2. **Backup first:** a fresh `pg_dump -Fc coaching` on the server, checked with `pg_restore --list`, copied to the PC outside the repository, before any change.
3. **Rehearse on a copy:** restore that dump into a rehearsal database, run the migration and the backfill, and compare before and after: row counts for every table touched, the masked value of every account, and every number decrypting back to the original.
4. **No plain column is dropped on the day it is added.** The plain columns stay until the verification in step 7 has passed, and they are removed only in a later migration.
5. **Dual write during the window:** while the plain columns exist, new writes fill both the plain and the encrypted columns. A rollback to the previous jar therefore still reads correct data.
6. **Backfill is idempotent and batched:** it encrypts only rows whose encrypted column is empty, in small transactions, and logs counts only, never values.
7. **Verification before the plain columns go:** every account has an encrypted value; every value decrypts; the masked display is unchanged for every row; the owner and the Master Admin still see the full number; no plain number appears in logs, notifications or the audit log. Shahriar approves the verification result before the plain columns are dropped.

**What it will do**
- Add `account_number_enc`, `routing_number_enc` and `wallet_number_enc` (bytea) and a `key_version` column to `staff_payout_accounts`, in a new migration (the next free number at the time). No existing column is dropped in that migration.
- Encrypt the sensitive fields at the application level with AES-256-GCM, a fresh random nonce per value, and authenticated data naming the table, the column and the account id, so a ciphertext cannot be moved to another row.
- Read the key from the environment (`PAYOUT_ENCRYPTION_KEY`). It is never written to the repository, the database, the logs or the audit log.
- Refuse to start when encrypted rows exist and the key is missing or does not match the stored key version, rather than showing garbage or empty numbers.
- Keep the masked display (`•••• 4821`) by decrypting only the last four digits when a list is built.
- Add key-version support, so the key can be rotated later without losing data.

**Key custody (before the change)**
- The production key is generated once, stored in the owner's password manager, and set in `/etc/coaching-api.env` on the server. A second copy is kept off the server. A lost key means the numbers cannot be recovered, so the backup must not be the only copy.
- The key is set on the server before the new jar starts with encryption enabled.

**Rollback**
- Before the plain columns are dropped: redeploy the previous jar. It reads the plain columns, which dual write has kept current.
- After they are dropped: restore the backup taken in step 2, after Shahriar's approval, and accept that writes made after the backup are lost unless they are replayed.

**Before it ships**
- Decide where the production key lives, and record it outside the repository (see key custody).
- Rehearse the migration and backfill on a copy of the live database, as in the rules above.
- Confirm no plain number appears in logs, notifications, error messages or the audit log.
- Shahriar approves the release in writing, since it changes a live table that holds payment data.

**Check:** the database holds only ciphertext for these fields once the plain columns are dropped; before that, both agree for every row; the owner and the Master Admin still see the full number on the account screen; a wrong or missing key fails loudly; the masked display is unchanged.

## 7. Not in this plan

- Paying through the site (bank or wallet APIs), payslip PDFs, tax.
- Email notifications (in-app only, P14).
- Comparing payroll with student income (P16).
- A rate per exam (P4).
- Receipt confirmation by staff (P13).
- Other ed-tech notifications (the centre is built to carry them later).
