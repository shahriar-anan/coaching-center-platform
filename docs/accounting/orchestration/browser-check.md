# Accounting: browser check (phase 3, gate 3)

Written 28 Nov 2026 for the agent that runs the end-to-end browser check of the accounting section, and for Shahriar. It checks every
accounting screen against the real API, on a local copy of production data, at desktop and phone width, in English and Bangla. Read
[README.md](README.md), [contracts.md](contracts.md) and [../accounting-plan.md](../accounting-plan.md) (sections 4 and 5) first.

## 0. Rules (read before anything else)

1. **Production data stays on this PC.** The database copy, the screenshots and the notes hold real names, phones and amounts. Keep
   them in `F:\acc-browser-check\` (outside every repository). Never commit them, never publish them as an artifact, never paste them
   into anything that leaves the PC. The committed report names no student, instructor or staff member: say "an instructor" or use
   the entry number.
2. **Never touch production.** No SSH, no deploy. The production dump is already on this PC (section 1.3).
3. **Never touch the sample or dev databases** (`coaching_center_sample`, `coaching_center_dev`) or any other database. You create and
   drop only `cc_qa_acc`.
4. **No real mail and no payment gateway calls.** The API runs with the `dev` profile (mail is only logged), every job switch off, and
   SSLCommerz pointed at a dead address (section 1.5). Never start it any other way.
5. **Do not stop a process you did not start.** If port 8092 or 3000 is busy, stop and report it.
6. **Fix real bugs; only note UX ideas** (section 4). Never implement a UX idea during the check.
7. Your final message is the report summary; the full report is a file (section 5).

## 1. Set-up

### 1.1 Worktree

```
git worktree add F:\cc-wt\acc-bc -b acc/browser-check acc/integration
copy F:\coaching-center-platform\api\.env F:\cc-wt\acc-bc\api\.env
```

Web dependencies: run `npm ci` in `F:\cc-wt\acc-bc\web` (several minutes). Do not create a junction to another `node_modules`.

Web dev env file `F:\cc-wt\acc-bc\web\.env.development.local` (git-ignored), exactly:

```
NEXT_PUBLIC_API_BASE_URL=http://localhost:8092
NEXT_PUBLIC_OFFLINE_ADMISSION=1
```

### 1.2 Tools folder

```
mkdir F:\acc-browser-check\tools, F:\acc-browser-check\shots, F:\acc-browser-check\files, F:\acc-browser-check\notes
cd F:\acc-browser-check\tools
npm init -y
npm i playwright bcryptjs
```

Playwright's browsers are already installed on this PC (`%LOCALAPPDATA%\ms-playwright`). If the installed `playwright` version wants a
different Chromium, run `npx playwright install chromium` in the tools folder. Scripts you write live in `F:\acc-browser-check\tools`.

### 1.3 Database: a scratch copy of production

The newest production dump is in `F:\exam-vps-backups\nightly\db\` (`db-YYYYMMDD-HHMMSS.dump`, custom format). Use the newest file.
PostgreSQL 17 tools are in `C:\Program Files\PostgreSQL\17\bin`; user and password are `DB_USERNAME` and `DB_PASSWORD` in
`F:\coaching-center-platform\api\.env` (read them, never print them).

```
createdb cc_qa_acc
pg_restore --no-owner --no-privileges -d cc_qa_acc <newest dump>
```

Check: `select max(version) from flyway_schema_history where success` answers `107`. Anything else: stop and report.

### 1.4 Before the move: record the old figures

The API's Flyway run (next step) moves the old instructor payments into the ledger. Record first, in `notes\before.txt`:

```sql
-- waiting payments would stop the move (plan 5.4 step 1)
select 'other', status, count(*), sum(amount) from offline_instructor_other_payments group by status
union all select 'class', status, count(*), sum(amount) from offline_class_payments group by status;
select count(*) from payment_edits where kind in ('CLASS','OTHER') and status = 'PENDING';
-- Finance per month, the old way: approved payments of both tables
select to_char(paid_on, 'YYYY-MM') m, sum(amount) from (
  select paid_on, amount from offline_instructor_other_payments where status = 'APPROVED'
  union all select paid_on, amount from offline_class_payments where status = 'APPROVED') x group by 1 order by 1;
-- batch cost, the old way
select batch_id, sum(amount) from (
  select o.batch_id, o.amount from offline_instructor_other_payments o where o.status = 'APPROVED'
  union all select c.batch_id, p.amount from offline_class_payments p join offline_classes c on c.id = p.class_id
  where p.status = 'APPROVED') x group by 1 order by 1;
select count(*) from offline_instructors;
```

**If any row is `PENDING`:** this is a **release blocker** (plan section 7 step 2). Write it at the top of the report. To go on with
the check, in `cc_qa_acc` only, set those rows to `REJECTED` and record how many and their total.

### 1.5 Local log-ins (scratch copy only)

Production passwords are unknown and must stay so. In `cc_qa_acc` only, give the Master Admin and one active System Admin a local
password (`Qa-Check-2026!`):

```
node -e "console.log(require('bcryptjs').hashSync('Qa-Check-2026!', 10))"      (in the tools folder)
```

```sql
select id, email, phone, role, status from users where role in ('MASTER_ADMIN','SYSTEM_ADMIN') and deleted_at is null;
update users set password_hash = '<hash>' where id in ('<master id>', '<one active system admin id>');
```

Note in `notes\logins.txt` which emails you set (local file only). If no active System Admin exists, create one through the Master
Admin's Admins page once the site runs.

### 1.6 Start the API (from the worktree)

```
cd F:\cc-wt\acc-bc\api
.\mvnw.cmd -q -DskipTests package
java -jar target\api-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev --server.port=8092 `
  --DB_URL=jdbc:postgresql://localhost:5432/cc_qa_acc --SEED_ENABLED=false --SEED_DEV=false `
  --app.exam-jobs.scheduled=false --app.staff-pay.jobs-enabled=false --app.offline-admission.enabled=true `
  --SSLCZ_BASE_URL=http://127.0.0.1:9 --SSLCZ_STORE_ID= --SSLCZ_STORE_PASSWORD= --MAIL_HOST= `
  --FILE_STORAGE_DIR=F:\acc-browser-check\files `
  --app.payout.encryption-key=<a fresh random 32-byte key, base64> --app.payout.key-version=1
```

Run it in the background with output to `F:\acc-browser-check\notes\api.log`.

- The throwaway payout key only satisfies the start-up guard. Payout numbers cannot be read with it; payout pages are not part of
  this check. Never put the production key on this PC.
- Uploaded files are not copied, so PDFs, photos and receipts of old records will not open. That is expected; do not report it.
- **Flyway must reach 107.2.** In `api.log` find the V107_1 and V107_2 lines. If V107_2 fails, the move refused or its checks
  failed: **release blocker**. Copy the exact error into the report and stop.
- Check: `GET http://localhost:8092/api/v1/auth/session` with header `X-Client-Type: web` answers 200.

### 1.7 After the move: compare

In `notes\after.txt`, and compare with `before.txt`; every number must match to the paisa:

```sql
select count(*) from acc_payees where group_id = 'a0000000-0000-4000-8000-000000000001';  -- = offline_instructors
select to_char(entry_date, 'YYYY-MM') m, sum(amount) from acc_entries
where group_id = 'a0000000-0000-4000-8000-000000000001' and kind = 'PAYMENT' and status = 'ACTIVE' group by 1 order by 1;
select batch_id, sum(amount) from acc_entries
where group_id = 'a0000000-0000-4000-8000-000000000001' and kind = 'PAYMENT' and status = 'ACTIVE' group by 1 order by 1;
select p.id from acc_payees p left join acc_entries e on e.payee_id = p.id and e.status = 'ACTIVE'
group by p.id having coalesce(sum(case e.kind when 'CHARGE' then e.amount when 'PAYMENT' then -e.amount end), 0) <> 0;  -- none
```

A mismatch is a **release blocker**.

### 1.8 Start the web

```
cd F:\cc-wt\acc-bc\web
npx next dev -p 3000
```

Background, output to `notes\web.log`. Check that `http://localhost:3000/login` loads. If a page that exists answers 404, stop the
web, delete `web\.next\dev`, start again (known Next dev cache issue).

## 2. How to run the check

- **Playwright scripts**, Chromium, headless. One script per section of part 3 (`s1-home.mjs`, …), each logging in fresh at
  `http://localhost:3000/login` with the Master Admin (or System Admin for section 3.13).
- **Two viewports** for every screen: desktop `1366×900` and phone `390×844` (`isMobile: true`, `hasTouch: true`).
- **Two languages**: every screen in English; the screens marked **(bn)** also in Bangla. Switch with the site's own language control
  (find it in the staff header; note how in the report).
- **Screenshots**: `shots\<section>-<step>-<desktop|phone>-<en|bn>.png`, full page. Look at each one yourself (open the image) before
  you judge the step.
- **Console and network**: record every console error and every API answer ≥ 400 that the step did not expect
  (`page.on('console')`, `page.on('response')`). An unexpected one is a bug.
- **Expected results** are listed per step. Check the numbers, not only that the page loaded.
- **Test data**: create it through the UI as the steps say, with names starting `QA ` (for example `QA Printing`, `QA Rahim`), so
  real rows and test rows are easy to tell apart. It is a scratch database; nothing needs cleaning up inside it.
- **Print pages**: screenshot them, and also save a PDF with `page.pdf({ format: 'A4' })` (voucher also `A5`) to check the print
  layout.

## 3. What to check

Every step: screenshot both viewports; English always, Bangla where marked. Record pass, bug or UX note per step.

### 3.1 Menu, access and old addresses
1. Master Admin: the staff menu shows **Accounting** in the money group; **Instructor payments** and **Offline instructors** are gone.
2. Open `/staff/offline-instructors` → lands on the Offline instructors group page. Open `/staff/offline-instructors/<an old
   instructor id>` (from `acc_payees`) → that payee's page. Open `/staff/offline-instructor-payments` → the entries list filtered to
   that group.
3. Payment records page: no instructor payments and no instructor filter; fee and direct-enrolment records unchanged.

### 3.2 Home `/staff/accounting` (bn)
1. Cards: Owed now (and advance), Spent this month (with last month), Income this month. Right after the move, owed is 0 for the
   instructors (every moved balance is 0).
2. A row per live group in order; Owed the most; Latest entries; the 12-month chart (moved payments show in their months).
3. Cross-check: Spent this month equals the SQL sum of this month's active `PAYMENT` and `SPEND` entries in expense groups.

### 3.3 Settings `/staff/accounting/settings` (bn)
1. Groups: the five built-in groups with Bangla names. Add `QA Printing` (Payees, Expense, Count in Finance on) and `QA Snacks`
   (Log, Expense, off). Rename one, move it up and down, archive and restore it. Offline instructors has no archive.
2. Types: for `QA Printing` add `QA Posters` (Counted, unit "poster"/"পোস্টার"), `QA Rent` (Amount, needs a month), `QA Fine`
   (Deduction). Try to change the kind of a type after it has an entry (step 3.6) → refused with a clear message.
3. Money accounts: five seeded, Bangla names on the Bangla page. Add `QA Bank 2`, archive it, see it gone from the entry forms,
   restore it.

### 3.4 Group page: Offline instructors (Payees) (bn)
1. Every old instructor is listed, balance 0 (settled), last payment shown for those who had one, paid this month.
2. Search by name and by phone; "Show archived" shows archived instructors.
3. Add payee `QA Instructor One` with an alias `QA Instr 1`. Then open a batch's routine (offline batches) and check the instructor
   picker offers `QA Instructor One` (the mirror into `offline_instructors`).
4. Try to add another payee named `QA Instr 1` → refused (alias taken).

### 3.5 Payee page: an old instructor (moved data) (bn)
1. Pick an instructor with several old payments. Summary: balance 0, total charged = total paid = their old total, payments count,
   batches they teach.
2. Entries tab: each old payment shows as a charge plus a payment (paired), running balance back to 0 after each pair, old dates,
   old references; voided old payments show Voided with their reason.
3. Statement for all time: opening 0, every line, closing 0. Print it (section 3.11).

### 3.6 Entry forms (desktop and phone; the phone form is the main one)
On `QA Instructor One`:
1. **Counted charge**: type Answer sheet evaluation, quantity 40, unit price 50 → the live line reads "40 answer sheets × ৳50 = ৳2,000";
   save → balance Owed ৳2,000.
2. **Last price**: open Add charge again, same type → unit price starts at 50.
3. **Paid now**: Lecture, 4 × 1,200, Paid now on → paid amount starts at 4,800; change quantity to 5 → paid amount follows to 6,000;
   type 3,000 → stays 3,000; save → charge and payment saved together, balance goes up by 3,000.
4. **Payment only** of ৳1,000 from bKash with a reference → balance goes down; the money account defaults to the last used next time.
5. **Overpay**: pay more than owed → balance shows Advance.
6. **Deduction**: Deduction ৳500 → balance down by 500; Paid now is not offered for it.
7. **Needs a description**: type Other with no description → refused before saving.
8. **Future date** → refused. **Month** field appears for a type that needs one, starting at the date's month.
9. **Batch and exam**: a charge with a batch and an online exam → both shown on the entry page.
On `QA Printing` and `QA Snacks`:
10. Add a payee `QA Press`; a charge `QA Posters` 100 × 15 with batch set → Finance batch cost shows it once paid (3.12).
11. Monthly amount type `QA Rent` for `QA Press` ৳8,000 for this month; next time the amount pre-fills ৳8,000.
12. `QA Snacks` (Log): add spending ৳250 "paid to QA Shop"; no payee asked.
13. Other income: add income ৳1,500 type Book and sheet sales → Income this month on Home goes up by 1,500.
14. Quick add (+ Add) from Home: walk kind → group → payee → form, on the phone.

### 3.7 Several payees and copy last month
1. On `QA Printing`, Add for several payees: tick `QA Press` and a second QA payee, type `QA Rent` → each line pre-fills that payee's
   last amount; change the type → lines start again; save → both charges saved.
2. Force a refusal: open the sheet, tick two payees and pick `QA Rent`; in a second tab, archive the type `QA Rent` in Settings; then
   Save in the first tab → refused with a readable message (type archived), nothing saved. Restore the type afterwards. (Naming the
   failing payee per line is covered by the web tests; this checks a refusal reaches the screen.)
3. Copy last month on Offline instructors with the month set to a month that holds **moved** charges → drafts listed; moved counted
   charges show quantity and price that multiply to the old amount (or 1 × amount); a draft already added this month is unticked.
   Untick all but two and save → saved; open them and check amounts.
4. Copy last month twice → the second time the drafts start unticked ("Looks already added this month").

### 3.8 Entries list `/staff/accounting/entries` (bn)
1. Filters: date range, group, payee, type, kind, account, batch, status, search by text and by `#number`. The URL keeps them
   (reload keeps the filters). Totals (charged, paid, spent, income) match the rows.
2. Download CSV with filters on; open it (as text): Bangla names readable (BOM), one row per entry, a deduction is `-500.00`, a
   description typed as `=1+1` (make one) appears as `'=1+1`.

### 3.9 Entry page, edit and void
1. Edit the counted charge of 3.6.1: change the note only, with a reason → saved, "Corrected on" shown, the correction listed with
   before and after; **batch and exam still set** (3.6.9 entry).
2. Edit without a reason → refused before saving.
3. Edit a **moved class payment** (an old entry with legacy CLASS; find one with `select id from acc_entries where legacy_source =
   'CLASS' and kind = 'PAYMENT' limit 5`): change its amount → saved. Its batch stays. Only date, amount, account, reference and note
   are offered.
4. Edit a **moved charge with no price** (`legacy_source = 'OTHER'`, counted type, `unit_price is null`): the amount field is offered;
   change it → saved.
5. A deduction keeps its sign after an edit (amount shown positive in the form, stored negative).
6. Void the Paid now charge of 3.6.3 → "Also void the payment made with it?" ticked → both voided, balance back. Voided entries
   cannot be edited or voided again.
7. Void a moved class payment → its class shows Scheduled again on the batch's Classes page (if no other payment points at it).

### 3.10 Reports `/staff/accounting/reports` (bn)
1. Owed now: groups with payees whose balance is not zero; totals match the payee pages.
2. By month: the last 12 months; moved months match `before.txt` for the instructor group; expense and income rows.
3. Group summary for Offline instructors, this year: per type quantity and amount, per payee charged and paid.
4. Print and CSV on each tab.

### 3.11 Print pages
1. Voucher of the 3.6.4 payment: A5 and A4 PDFs; number, date, paid to, account, reference, amount in figures and in words (English
   page in English words, Bangla page in Bangla words), signature lines. Bangla text prints (no boxes).
2. Voucher of a voided entry shows VOID.
3. Voucher of an amount-only entry (3.6.4) shows the amount, not "quantity × price".
4. Statement of `QA Instructor One` this month: opening, each line with running balance, closing = the balance on the payee page.

### 3.12 Finance `/staff/finance`
1. Pick the months in `before.txt`: Offline instructors totals equal the old ones, month by month; batch costs equal the old ones.
2. The new sources: accounting spending (QA Printing payments, QA Snacks not counted since off) and income (3.6.13) appear with their
   labels and colours; turning `Count in Finance` off for `QA Printing` removes it.
3. Finance instructors table lists moved instructors with their old totals.

### 3.13 System Admin
1. Log in as the System Admin: no Accounting in the menu; `/staff/accounting` says it is for the Master Admin; any
   `/api/v1/accounting/...` call answers 403.
2. The routine's instructor picker still works for them (routines are theirs).

### 3.14 Phone pass
Every screen of 3.2 to 3.11 at 390 px: nothing cut off or overlapping, sheets fill the height, the Save button stays reachable, the
amount keyboard is decimal (`inputmode="decimal"`), lists turn into cards, tables scroll sideways rather than break.

## 4. Bugs and UX notes

| It is a **bug** (fix it) | It is a **UX note** (write it down only) |
|---|---|
| A wrong number or total; a figure that does not match SQL or another page | A label, wording or Bangla word that could be clearer |
| A crash, an error page, a blank screen, a console error, an unexpected 4xx/5xx | Layout, spacing, order of fields, emphasis |
| A step of section 3 that cannot be done | A missing shortcut, filter or default that would save time |
| Data saved wrong (lost batch, wrong sign, wrong account) | Anything that works but could be nicer |
| Text cut off or overlapping so that it cannot be read or used on a phone | |
| A refusal with no readable message | |

**Fixing a bug**
1. Fix it in `F:\cc-wt\acc-bc` on `acc/browser-check`. Keep the fix small; follow the code around it.
2. Add or extend a test that fails without the fix (web: `web/tests/accounting/`; API: the accounting IT classes).
3. Web: typecheck and the accounting tests (`npx tsc --noEmit -p .`, `npx vitest run tests/accounting`). API: the touched test classes
   through `docs\accounting\orchestration\tools\acc-mvn.ps1 -Agent INT -ResetDb -Tests "..."`. Tests use their own database
   `cc_test_acc_int`, never `cc_qa_acc`, so they can run while the check's API is up. Build the jar for the API only after the tests
   pass, because `package` and the test run share `api\target`.
4. Web fixes hot-reload. API fixes need the jar rebuilt and the API restarted (section 1.6; Flyway is already at 107.2).
5. Commit each fix on its own (`Accounting browser check: <what>`), ending with the attribution line the session gives. Redo the step.
6. A bug you cannot fix safely (data model, migration, money rule): do not fix it; mark it **blocker** in the report.

**A UX note**: screen, viewport, language, what you saw, why it matters, a concrete suggestion, the screenshot file name.

## 5. Report

File `docs/accounting/orchestration/browser-check-report.md` on `acc/browser-check` (no personal data, rule 1):

1. **Verdict**: ready / ready after the listed fixes / not ready (blockers).
2. **Release blockers** (pending old payments, move failure, figure mismatch, anything unfixable).
3. **Move check**: the before and after figures as totals and month counts only (no names), match or not.
4. **Steps**: a table of every step of section 3 with pass / fixed / bug left / note, and the screenshot names.
5. **Fixes made**: commit id, what, test added.
6. **UX notes**, grouped by screen, most valuable first.
7. **Not checked** and why.

Screenshots and notes stay in `F:\acc-browser-check\`; the report refers to them by file name.

## 6. Clean-up

1. Stop the web and the API you started (only those).
2. `dropdb cc_qa_acc`.
3. Keep `F:\acc-browser-check\shots` and `notes` until Shahriar has read the report; then he decides whether to delete them.
4. Leave the worktree and branch for the orchestrator to merge.

## 7. Agent prompt

```
You are the accounting browser-check agent. Follow F:\cc-wt\acc-int\docs\accounting\orchestration\browser-check.md exactly, in order.
Your worktree will be F:\cc-wt\acc-bc on branch acc/browser-check (section 1.1 creates it). Use absolute paths; your shell does not stay
in a folder between calls. Rules in section 0 come first: production data never leaves F:\acc-browser-check\, no production access,
only the cc_qa_acc database, no real mail or gateway, never stop a process you did not start, fix bugs but only note UX ideas.
Stop and report at once on a release blocker in section 1. Your final message is the report summary (verdict, blockers, fixes with
commit ids, the count of UX notes) and the path of the report file.
```
