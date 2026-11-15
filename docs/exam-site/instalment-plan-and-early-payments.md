# Plan: instalments set one by one, and payments before onboarding (15 Nov 2026)

Status: **LIVE 15 Nov 2026, 19:25 Dhaka** (`ebdc417`, Flyway 102). See section 6. Builds on the course fee release (`course-fee-plan.md`).

## 1. Decisions (Shahriar, 15 Nov)

| # | Decision |
|---|---|
| E1 | The admin sets **each instalment's amount and due date**. ৳10,000 in 5 can be ৳4,000, ৳2,000, ৳2,000, ৳1,000, ৳1,000 on any dates. |
| E2 | **The amounts make the course fee:** the course fee is their sum, with no separate total to match. |
| E3 | **A payment can be recorded before onboarding**, once the setup has a batch and instalments (from the form being submitted). |
| E4 | **Receipts as today:** a receipt exists once the payment is approved. The Master Admin's own payment is approved at once. A staff payment waits for approval. Staff see the receipt on the admission once it is approved, before or after onboarding. |

## 2. Instalments, one by one (E1, E2)

**Setup screen, "Batch and fees":**
- Choosing a batch fills the instalments from it, as today: the batch's course fee split evenly, one a month from the first due date,
  cut to what fits before the course end.
- **A table of instalments:** each row has a due date and an amount, both editable. Rows can be added (up to 24) or removed (at least
  one). Under the table: **Course fee (total) ৳X**, the sum.
- **Spread evenly:** a course fee and a number of instalments, with "Fill instalments". This makes the even split again (whole taka,
  the rest on the last) from the first row's date, when the admin wants to start over.
- Problems are shown on the row:
  - an amount below ৳1 or with more than two decimals;
  - a date before the admitted-on date, after the course end, or not after the row above.

**API:**
- `offline_admissions.installment_plan` (JSONB, V102) holds the admin's rows `[{dueOn, amount}]`. `course_fee` is their sum,
  `installments` their count, and `first_due_on` the first date, all kept in step.
- Without saved rows (every admission set up before this change), the plan is the even split, as now.
- The setup PATCH takes `installmentPlan` (the whole list). `courseFee` and `installments` without a list still mean "split evenly"
  (the Spread evenly button, and older callers).
- The ready check refuses a plan with any of the problems above.
- Onboarding writes the dues from the plan.

**After onboarding:** change terms works as today (a new fee or count spreads the rest evenly over the open instalments).

## 3. Payments before onboarding (E3, E4)

**When:** once the admission has a batch and an instalment plan with a total above zero, in Submitted, Changes requested or Ready, and
after onboarding as now. Not before the form is submitted, and not once closed.

**How it works:**
- Record payment and approval work as today: a staff payment waits for the Master Admin, whose own payment counts at once.
- **The most that can be taken** is the plan's total minus approved payments, the same check as after onboarding.
- **The receipt** is given at approval, as now. Before onboarding it lists no instalments (the instalments do not exist yet). It shows
  the amount, the method, the reference, the batch and "still owed" (plan total minus approved payments).
- **At onboarding (or the join, for an account applicant)** the dues are written from the plan. Every approved payment is then
  matched to them in order of payment date, exactly as if it had been recorded after onboarding. The receipt then lists its
  instalments.
- **Changing the plan after a payment:** refused when the new total would be less than what is already paid or waiting.
- **Closing before onboarding** with payments: allowed, as now. The payments stay recorded (Finance counts them) and can be voided by
  the Master Admin if the money is returned. The close dialog says how much was paid.
- **Student side:** a student with an account gets the payment notice and the receipt email as after onboarding. The student's
  schedule page still waits for onboarding.
- **Finance:** a payment counts on its paid date under the admission's batch, as now.

**Staff screens:**
- The admission page before onboarding gets a **Payments** card under the setup summary. It shows the plan (date, amount), paid so far
  and still owed, the payments with their status, **Record payment**, and for approved payments **Open receipt**.
- After recording: the Master Admin's payment toast offers **Open receipt** at once. A staff payment says "Waiting for the Master
  Admin's approval; the receipt follows".
- The Fee approvals page lists these payments too, and approval works the same.

## 4. Data and release

- **V102:** `offline_admissions.installment_plan JSONB NULL`. Nothing else changes. The previous release ignores the column (rollback
  safe).
- **Tests:**
  - API: setup with rows (sum, count, dates); refusals; onboarding writes the rows; payment before onboarding (record, approve,
    receipt, owed); matching at onboarding and at join; plan total below paid refused; close with payments.
  - Web: the instalment table; Spread evenly; the pre-onboarding Payments card; Open receipt after recording.
- **Browser check** of the flows above. Both full suites. Release as before: backup, swap with automatic rollback, smoke checks.

## 5. Routine: nothing blocks the confirm (Shahriar, 15 Nov evening)

- Every routine preview can be confirmed. The problems are listed above the rows as warnings: a date earlier than the row above, a
  date outside the batch's dates, a weekday that does not match, or no subject.
- **On confirm:**
  - a row with no readable date or time is left out (the warning says so; typing the date in keeps it);
  - a row with no subject is saved as "Class";
  - wrong dates are kept as written.
- Checked: OfflineRoutineIT confirms the client's PDF with its typos (`aWrongDateOnlyWarnsAndTheRoutineConfirmsAsWritten`); the
  browser check confirmed 43 classes with the warnings in view.

## 6. Released (15 Nov, 19:25 Dhaka)

- **Commit and builds:** `ebdc417`. Jar `api-ebdc417.jar` (SHA-256 `0cee61aa…`). Web `web-ebdc417-20261115-132019.tgz`, build
  `JoBC6K7fuY8f__ol7HtYC`, offline on.
- **Suites:**
  - Web 2174/2174, typecheck clean; browser check 19/19.
  - Offline and Finance ITs 192/192.
  - Full API suite 1269/1270. The one failure is `StaffAssignmentWatcherIT`, the 2-minute schedule racing the test's own call; it
    passes 3/3 alone.
- **Backup (19:22):** `coaching-pre-plan-20261115-132241.dump` (581 entries) and the files tar, on the box and in
  `F:\cc-releases\backup-pre-plan-20261115-132241` (hashes match). `api.jar.before-plan-20261115-132241` is on the box.
- **Swap:** checked first: no sitting open, no payment starting, no exam within 3 h.
  - API: Flyway 102, 0 failed, started in 19 s.
  - Web: up after 1 s; the previous site is kept at `/opt/coaching-web.before-20261115-132604`.
- **Smoke:**
  - 9 pages and APIs answer 200; staff routes 401;
  - 0 errors and 0 5xx (4 PDF font warnings, as before);
  - the column exists; counts are as expected (users 203, participants 192, orders 7, grants 31).
  - Live admissions: 1 onboarded and 2 closed. The office onboarded one before this release, so it keeps the even split it was
    onboarded with.
- **Rollback:** put back `api.jar.before-plan-20261115-132241` (V102 only adds a column) and the web directory.
