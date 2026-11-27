# Accounting: plan (27 Nov 2026)

Status: **go given 27 Nov; phase 1 (agent F) started.** Written 27 Nov 2026 for Shahriar and the agents who build it. Every decision below was made by
Shahriar on 27 Nov (two rounds of questions). How the build is split across agents: [orchestration/README.md](orchestration/README.md).

## 1. The change

Today the Master Admin records **payments** to offline instructors by hand (24 Nov, `docs/exam-site/instructor-pay-simple-plan.md`).
Nothing records what an instructor is **owed**, so there is no balance, and nothing records any other cost of the centre.

The client wants a full **accounting** section to keep track of all his money going out (and the income the site does not see):

- **Groups** (খাত): Offline instructors, Staff, Suppliers, Personal, Other income are built in. He can add his own.
- **Payees** inside a group: each instructor, staff member or supplier has a profile.
- **Owed first, paid later.** He records what a payee is owed (a **charge**) and, separately, what he paid (a **payment**). The
  payee's **balance** is charged minus paid: positive is **owed**, negative is **advance**.
- Instructor charges are **quantity × price**: 12 classes × ৳1,000, 40 answer sheets × ৳50, 30 questions × ৳20.
- Every payment says **which money account** it came from (Cash box, bKash, Nagad, Rocket, Bank).

It replaces the **Instructor payments** and **Offline instructors** pages. Every payment already recorded moves into the new ledger
with a matching charge, so every balance starts at zero and Finance totals do not change.

## 2. Decisions (Shahriar, 27 Nov)

| # | Decision |
|---|---|
| A1 | Replaces offline instructor pay only. **Staff pay** (System Admin salary, online marking pay, `staff_ledger`) stays as it is. |
| A2 | The **Staff** group holds offline staff by name (no login), like offline instructors. |
| A3 | Each group is one of two modes: **Payees** (profiles with balances: charges and payments) or **Log** (simple list of spending or income, no balance). |
| A4 | A payment reduces the payee's running balance. It is **not** matched to a particular charge. |
| A5 | Overpaying is allowed; the payee shows an **advance**. |
| A6 | A charge can be recorded with **"Paid now"**, which adds the payment in the same step (full or part). |
| A7 | Instructor work is **quantity × unit price** (classes, exams, answer sheets, questions). The price of the last charge of that type for that payee is filled in and can be changed. There is no separate rate setup. |
| A8 | Staff **monthly salary is entered by hand** each month. Nothing is added automatically. |
| A9 | Nothing is added automatically from classes, routines or exams. |
| A10 | Every old instructor payment is moved over with a **matching charge**, so every balance starts at zero. |
| A11 | Charges and payments may name a **batch** (optional). With a batch, Finance counts it in that batch's cost. |
| A12 | Each group has a **"Count in Finance"** switch. Personal is off by default; the others are on. |
| A13 | Reports: total owed now, spending by group by month, one payee's statement, CSV export. |
| A14 | No due dates or reminders in this version. |
| A15 | Master Admin only. System Admins see none of it. |
| A16 | Edit and void need a reason and are audited, as today. Nothing is ever deleted. |
| A17 | Receipt photos: later, not in this version. |
| A18 | Groups, types, payees and money accounts can be archived (hidden, kept), never deleted. |
| B1 | **Money accounts**: every payment, spending and income entry names the account it came from or went to. Account balances and transfers between accounts are for a later version. |
| B2 | **Other income** (book and sheet sales, donations, a partner's investment) is recorded as a Log group with income direction, and feeds Finance. |
| B3 | Same as A7: the last price is filled in. |
| B4 | A charge may optionally name an **online exam** it was for (question making, evaluation), besides the batch. |
| B5 | **Deductions** (fines, advance recovered) are a charge type that lowers the balance. |
| B6 | **Add for several payees**: one charge type, date and note, one line per payee (e.g. Eid bonus or this month's salary for all staff). |
| B7 | **Copy last month**: on a group, last month's charges or spending are offered as a ticked list to add again for this month. Payments are never copied. |
| B8 | Each group has its own **types** (sub-categories), which covers Personal's Food, Transport, etc. |
| B9 | A **Suppliers** group is built in (printing, stationery, advertising, rent, utilities). |
| B10 | A **voucher** for each payment and a **statement** for each payee, printable (and saved as PDF from the browser's print). |
| B11 | Receipt photos later (A17). |
| B12 | Past months are **not** locked. Edits stay allowed with a reason. |
| B13 | A payee may be **linked to a site user** (an online instructor or System Admin). The profile then shows their Staff pay balance with a link. The two balances stay separate. |
| B14 | **Mobile first**: the add forms are built for a phone. |
| B15 | Home page: total owed, spending this month by group, payees owed the most, latest entries, 12-month chart. No budgets. |

## 3. Words used (en and bn)

The Bangla follows `natural-bangla-copy`: natural words, existing terms where they exist. Suggested; W keeps an existing term where the
site already has one.

| English | Bangla | Meaning |
|---|---|---|
| Accounting | হিসাবপত্র | The section (menu item) |
| Group | খাত | Offline instructors, Staff, … |
| Payee | ব্যক্তি / প্রতিষ্ঠান | A profile in a Payees group |
| Type | ধরন | Lecture, Monthly salary, Food, … |
| Charge | পাওনা | What a payee is owed |
| Payment | পরিশোধ | Money paid to a payee |
| Spending | খরচ | An entry in a Log expense group |
| Income | আয় | An entry in a Log income group |
| Owed | বাকি | Positive balance |
| Advance | অগ্রিম | Negative balance |
| Money account | টাকার উৎস | Cash box, bKash, … |
| Voucher | ভাউচার | Printable proof of one payment |
| Statement | হিসাব বিবরণী | A payee's entries and running balance |
| Void | বাতিল | Cancel an entry, with a reason |

## 4. Behaviour

### 4.1 Groups

| Field | Rule |
|---|---|
| Name (English) | Required, up to 100 characters, unique among groups that are not archived (ignoring case). |
| Name (Bangla) | Optional, up to 100. The Bangla page shows it, else the English name. |
| Mode | **Payees** or **Log**. Chosen when the group is made; it cannot change. |
| Direction | **Expense** or **Income**. Chosen when made; cannot change. An income group is always Log. |
| Count in Finance | On or off; can change at any time (Finance then reads the group's entries, old and new). |
| Order | Drag or up/down on Settings; the menu and the home page follow it. |

Built-in groups (seeded with fixed ids, section 5.3): **Offline instructors** (Payees, Expense, in Finance), **Staff** (Payees,
Expense, in Finance), **Suppliers** (Payees, Expense, in Finance), **Personal** (Log, Expense, not in Finance), **Other income** (Log,
Income, in Finance). They can be renamed and reordered. Offline instructors cannot be archived (the routine needs its instructors);
the others can.

An archived group takes no new entries and new payees, and leaves the home page and the menus; its entries stay in reports, Finance
(if counted) and the entries list. It can be restored.

### 4.2 Types

Each group has its own list of types.

| Field | Rule |
|---|---|
| Name (en, bn) | As for groups, unique within the group. |
| Kind | **Counted** (quantity × unit price; has a unit, e.g. "class" / "ক্লাস"), **Amount** (a plain amount), **Deduction** (a plain amount that lowers the balance; Payees groups only). Fixed once any entry uses the type. |
| Unit (en, bn) | Required for Counted, up to 30 characters, singular ("answer sheet"). The page shows "40 answer sheets × ৳50". |
| Needs a month | On for types like Monthly salary and Rent: the entry asks which month it is for ("November 2026"). |
| Needs a description | On for "Other": the entry must say what it was for. |

Seeded types (section 5.3):
- **Offline instructors:** Lecture (counted, class), Exam (counted, exam), Answer sheet evaluation (counted, answer sheet), Question
  making (counted, question), Bonus (amount), Deduction (deduction), Other (amount, needs a description).
- **Staff:** Monthly salary (amount, needs a month), Bonus (amount), Festival bonus (amount), Overtime (counted, hour), Deduction,
  Other (needs a description).
- **Suppliers:** Printing (amount), Stationery (amount), Advertising (amount), Rent (amount, needs a month), Utilities (amount, needs a
  month), Repairs (amount), Other (needs a description).
- **Personal:** Food, Transport, Family, Health, Bills, Other (needs a description); all amount.
- **Other income:** Book and sheet sales, Donation, Investment, Other (needs a description); all amount.

### 4.3 Payees (Payees groups)

| Field | Rule |
|---|---|
| Name | Required, up to 200, unique in the group among payees that are not archived (ignoring case). |
| Phone | Optional, up to 20. |
| Note | Optional, up to 500 (for example "bKash 017…, pays on Fridays"). Not shown outside the payee's page. |
| Other spellings | **Offline instructors only**: the routine aliases (section 4.10). |
| Linked site user | Optional. Any user with the System Admin or Instructor role, linked to at most one live payee. Shows "Also on Staff pay: balance ৳X" with a link (B13). |

- An archived payee leaves the group's list (a "Show archived" toggle brings it back) but can still be charged and paid, so an old
  balance can be settled. It can be restored, unless a live payee in the group now has the same name (`name_taken`).
- Archiving a payee with a balance that is not zero is allowed; the page warns first ("Rahim is still owed ৳2,000").
- **Offline instructors are the same records the routine uses.** A payee of the Offline instructors group has the same id as its
  `offline_instructors` row. Adding, renaming, archiving or restoring one changes both in the same transaction. The routine's
  instructor picker is unchanged.

### 4.4 Entries

One ledger holds four kinds of entry.

| Kind | Group mode | What it is | Counts in the balance as |
|---|---|---|---|
| **Charge** | Payees | What the payee is owed for some work or a month | + amount (a deduction: − amount) |
| **Payment** | Payees | Money paid to the payee | − amount |
| **Spending** | Log, Expense | Money spent | (no balance) |
| **Income** | Log, Income | Money received | (no balance) |

Fields:

| Field | Charge | Payment | Spending / Income | Rule |
|---|---|---|---|---|
| Date | ✓ | ✓ | ✓ | Required. Starts as today in Dhaka; any past day; a future day is refused. |
| Type | ✓ | – | ✓ | Required. A type of the entry's group, not archived. |
| Month | if the type needs it | – | if the type needs it | A month, e.g. 2026-11. Starts as the month of the date. |
| Quantity | Counted types | – | optional | More than 0, up to 2 decimals, at most 100,000. |
| Unit price | Counted types | – | optional | More than 0, up to 2 decimals, at most ৳10,00,000. Filled in from the last charge of that type for the payee (A7). |
| Amount | ✓ | ✓ | ✓ | More than 0, up to 2 decimals, at most ৳1,00,00,000. For Counted types it is **quantity × unit price** (rounded half up to the paisa), computed by the server and shown live on the form; the admin cannot type it. |
| Money account | – | ✓ | ✓ | Required. Not archived. Starts as the last one used. |
| Reference | – | ✓ | ✓ | Optional, up to 100 (bKash transaction id, cheque number). |
| Paid to / received from | – | – | ✓ | Optional, up to 200 ("Agora", "Rahim Library"). |
| Batch | optional | optional | optional | Any offline batch, active or archived (batches are never deleted). |
| Online exam | optional | – | – | Any exam (`live_exams`) with `deleted_at` empty (B4). |
| Description | if the type needs it | optional | if the type needs it | Up to 500. |
| Note | optional | optional | optional | Up to 500. |

- **Paid now** (charge form only, A6): a switch that shows the payment fields (account, reference, amount). The amount starts as the
  charge amount and may be less (part payment), not more. Both entries are saved together; the payment points at its charge
  (`paired_entry_id`). Not offered for a deduction.
- **A payment needs no charge before it.** Paying someone with a zero balance gives an advance (A5).
- **Moved-over charges** (`legacy` set) of a Counted type have a quantity but no unit price: their amount was typed in the old pages. They
  keep it that way; an edit may change the amount directly. New charges of a Counted type always need quantity and price.
- **Archived things on old entries**: an entry in an archived group, or with an archived type or money account, can still be edited
  (keeping that type or account) and voided. Only choosing an archived group, type or account for a **new** value is refused.
- **Unknown ids** (group, payee, type, account, batch, exam, entry) answer 404 `RESOURCE_NOT_FOUND`.
- **Add for several payees** (B6, Payees groups): pick a type, date, month (if needed), description and note once; then one line per
  payee (tick the payees; each line has its own quantity and price, or amount, starting from that payee's last price). Up to 200 lines,
  saved together or not at all. Charges only.
- **Copy last month** (B7): on a group page. Shows last month's active charges (Payees) or spending (Log) of that group, all ticked,
  each with its amount editable, dated today and with the month moved one forward. The admin unticks what does not repeat and saves.
  Payments and income are never offered. Saved through the same "several" call. A draft that looks **already added this month** (an
  active entry this month in the group with the same payee, type and month) starts unticked with a note, so pressing Copy twice does
  not double the month.
- Every entry gets a **number** (`no`) from a sequence, shown as `#1042` and on the voucher.
- An entry is never deleted.

### 4.5 Edit and void

- **Edit** opens the same form, filled in, and asks for a **reason** (required, up to 500). It applies at once and records the
  before and after values (`acc_entry_edits`). The entry shows "Corrected on <date>", and its page lists each correction.
  - Kind, group and payee never change (void it and enter it again).
  - The type may change to another type of the same group whose kind gives the same sign (a deduction stays a deduction).
  - A moved-over class payment (`legacy = CLASS`) can change only its date, amount, money account, reference and note.
- **Void** asks for a reason. A voided entry leaves every balance, total, report and Finance; it stays in lists with a "Voided" pill
  and its reason. It cannot be edited or voided again. Voiding a charge that was saved with "Paid now" asks "Also void the payment
  made with it?" (ticked by default).
- Voiding a moved-over class payment sets its class from Paid back to Scheduled, as today, so the class can be edited again.

### 4.6 Pages (all under `/staff/accounting`, Master Admin only)

| Page | Shows |
|---|---|
| **Home** `/staff/accounting` | Cards: **Owed now** (all payees with a positive balance; the advance total under it), **Spent this month** (payments and spending in expense groups, with last month beside it), **Income this month**. A row per group: name, owed (Payees) or this month (Log), link. **Owed the most**: top 10 payees by balance. **Latest entries**: 10. **12-month chart**: expense and income per month. A big **+ Add** button (fixed at the bottom on a phone) opens "What do you want to add?": charge, payment, spending, income → pick a group (and payee) → the form. |
| **Group** `/staff/accounting/groups/[groupId]` | Payees mode: the payee list (name, balance as Owed/Advance, last payment, paid this month), search, "Show archived", **Add payee**, **Add for several payees**, **Copy last month**, the group's totals this month. Log mode: the group's entries (date, type, paid to, amount, account), filters (date range, type, account), total, **Add spending** / **Add income**, **Copy last month**. |
| **Payee** `/staff/accounting/payees/[payeeId]` | Header: name, group, pills (Archived, Linked user). Summary: **balance** (Owed ৳X or Advance ৳X), total charged, total paid, paid this month, last payment, number of payments; for instructors, the batches they teach (from the routine). Buttons: **Add charge**, **Record payment**, **Statement**, **Edit**, **Archive**. Tabs: **Entries** (newest first; date, #, kind, type, "40 answer sheets × ৳50", batch, amount signed, account, status, running balance; filters by date range, kind and type), **Details** (phone, note, other spellings, linked user). |
| **Entries** `/staff/accounting/entries` | Every entry of every group. Filters: date range (starting this month), group, payee, type, kind, money account, batch, status, search (description, note, reference, paid to, number). Totals of the rows shown: charged, paid, spent, income. **Download CSV** with the same filters. |
| **Entry** `/staff/accounting/entries/[entryId]` | All fields, the paired entry, who recorded it and when, each correction (before → after, reason, who, when), void details. Buttons: Edit, Void, **Voucher** (payments, spending, income). |
| **Reports** `/staff/accounting/reports` | Three tabs. **Owed now**: by group, every payee with a balance that is not zero, the group total and the grand total. **By month**: a table, a row per group and a column per month (up to 36 months, default the last 12), expense and income totals. **Group summary**: pick a group and a date range: totals per type (quantity and amount) and per payee (charged, paid). Each tab has Print and CSV. |
| **Settings** `/staff/accounting/settings` | Tabs: **Groups** (add, rename, order, Count in Finance, archive or restore), **Types** (per group: add, edit, order, archive or restore), **Money accounts** (add, rename, kind, order, archive or restore). |
| **Voucher** `/staff/accounting/entries/[entryId]/voucher` | A print page (A5 portrait, prints on A4): centre name and logo, "Payment voucher" (or "Spending voucher", "Money receipt" for income), number, date, paid to, group and type, quantity × price, amount in figures and words, account and reference, description, "Corrected on" if any, signature lines "Received by" and "Paid by". Voided entries print a large VOID across. Bangla and English print correctly because it is HTML. |
| **Statement** `/staff/accounting/payees/[payeeId]/statement?from&to` | A print page: the payee, the range (default this month), opening balance, every active entry with a running balance, totals, closing balance, printed-on date. |

- **Mobile first** (B14): forms open as full-height sheets on a phone, amount fields use the decimal keypad, the quantity × price total
  is shown under the fields as they are typed, the Save button stays at the bottom, and lists turn into cards below 640 px.
- **Old addresses redirect**: `/staff/offline-instructors` → the Offline instructors group, `/staff/offline-instructors/[id]` →
  `/staff/accounting/payees/[id]` (same id), `/staff/offline-instructor-payments` → the entries list filtered to that group.
- **Menu**: the "Instructor payments" and "Offline instructors" items are replaced by one **Accounting** item in the money group
  (Master Admin only).

### 4.7 Finance

Finance stays **cash based**: it counts money that moved (payments, spending, income), never charges.

- The `OFFLINE_INSTRUCTORS` source now reads the active **payments** of the Offline instructors group from the new ledger (when that
  group counts in Finance). Old payments are in the ledger after the move, so its totals do not change.
- Two new sources: **`ACCOUNTS_EXPENSE`** (active payments and spending of every other expense group that counts in Finance) and
  **`ACCOUNTS_INCOME`** (active income of every income group that counts in Finance). Unlike the offline sources, these do not depend
  on the offline admission switch.
- **Batch cost**: every counted expense entry with a batch adds to that batch's cost (instructor payments as today, and now also, for
  example, printing for a batch).
- The **instructors** table on Finance reads the instructor group's payments from the ledger.
- `STAFF_PAY` (online staff, `staff_ledger`) is unchanged.

### 4.8 Payment records

Payment records stops listing instructor payments (its `CLASS` and `OTHER` kinds) and loses its instructor filter. They are corrected
in Accounting now, with its own history. Fee and direct enrolment payments are unchanged. Old corrections stay in `payment_edits`; the
release waits until none of them is pending (section 7).

### 4.9 Who can do what

| Action | Master Admin | System Admin |
|---|---|---|
| Every accounting page and action | Yes | No: not in the menu; the pages say they are for the Master Admin; the API answers 403 |
| Routine upload, classes, picking an existing offline instructor | Yes | Yes (unchanged) |
| Add, rename or archive an offline instructor | Yes, in Accounting | No (403, as today) |
| Finance | Yes (unchanged) | No (unchanged) |

### 4.10 Classes and routines

Nothing changes for students or for the routine. The routine preview's message for unknown names changes from "ask the Master Admin to
add them" to "the Master Admin can add them in Accounting → Offline instructors". The picker still offers only existing instructors.

## 5. Data

### 5.1 Migration numbers

Production (`demo/client-review`) is at V107. The online-courses build has reserved V108 to V112 on `lms/integration` and has not
shipped. Flyway here runs **in order only** (`out-of-order` is off), so:

- If Accounting ships **before** the courses build (expected): its migrations are **`V107_1__accounting.sql`** (schema and seed) and
  **`V107_2__accounting_move.sql`** (moving the old payments). Flyway sorts 107.1 and 107.2 after 107 and before 108, so the courses
  migrations still apply later.
- If the courses build ships first: rename them to **V113** and **V114** before the release (a file rename only; nothing else refers to
  the numbers). The orchestrator decides at release time.
- **General rule:** at release, both files must sort **above the highest version applied on production**. If a hotfix adds a
  migration to `demo/client-review` while this is being built (say V108), rename ours to sort after it (V108_1, V108_2) when
  `demo/client-review` is merged in (section 7). **Shahriar decided 27 Nov: Accounting ships first.**

### 5.2 Tables (`V107_1__accounting.sql`, all new; prefix `acc_`)

Money is `NUMERIC(12,2)`, quantities `NUMERIC(10,2)`, ids `UUID`, times `TIMESTAMPTZ`, days `DATE`, months `CHAR(7)` (`YYYY-MM`).
Every table has `created_at`, `updated_at`, and the editable ones `version BIGINT NOT NULL DEFAULT 0`.

| Table | Columns | Constraints |
|---|---|---|
| `acc_groups` | id, name_en, name_bn null, mode (`PAYEES`, `LOG`), direction (`EXPENSE`, `INCOME`), system_key null (`OFFLINE_INSTRUCTORS`, `STAFF`, `SUPPLIERS`, `PERSONAL`, `OTHER_INCOME`), in_finance, position, archived_at, created_by null (FK users) | name not blank; `direction = 'EXPENSE' OR mode = 'LOG'`; unique `system_key`; unique `lower(name_en)` where not archived |
| `acc_types` | id, group_id (FK), name_en, name_bn, kind (`COUNTED`, `AMOUNT`, `DEDUCTION`), unit_en, unit_bn, needs_period, needs_description, position, archived_at | `kind <> 'COUNTED' OR unit_en IS NOT NULL`; unique `(group_id, lower(name_en))` where not archived |
| `acc_payees` | id, group_id (FK), name, phone, note, user_id null (FK users), archived_at, created_by (FK users) | name not blank; unique `(group_id, lower(name))` where not archived; index `(group_id, archived_at)` |
| `acc_accounts` | id, name, kind (`CASH`, `BKASH`, `NAGAD`, `ROCKET`, `BANK`), note, position, archived_at | unique `lower(name)` where not archived |
| `acc_entries` | id, no (BIGINT, from `acc_entry_no_seq`, unique), kind (`CHARGE`, `PAYMENT`, `SPEND`, `INCOME`), status (`ACTIVE`, `VOIDED`), group_id (FK), payee_id null (FK), type_id null (FK), entry_date, period null, quantity null, unit_price null, amount (signed), account_id null (FK), reference, counterparty, batch_id null (FK offline_batches), live_exam_id null (FK live_exams), description, note, paired_entry_id null (FK acc_entries), legacy_source null (`CLASS`, `OTHER`), legacy_id null, legacy_class_id null (FK offline_classes), recorded_by (FK users), recorded_at, corrected_at, voided_by, voided_at, void_reason | see below |
| `acc_entry_edits` | id, entry_id (FK), before_values JSONB, after_values JSONB, reason, edited_by (FK users), edited_at | index `(entry_id, edited_at)` |

`acc_entries` checks:
- payee present for `CHARGE` and `PAYMENT`, absent for `SPEND` and `INCOME`;
- type present for `CHARGE`, `SPEND`, `INCOME`; absent for `PAYMENT`;
- account present for `PAYMENT`, `SPEND`, `INCOME`; absent for `CHARGE`;
- `amount <> 0`; `amount > 0` unless `kind = 'CHARGE'` (a deduction is stored negative);
- `quantity IS NULL OR quantity > 0`; `unit_price IS NULL OR unit_price > 0`; `unit_price IS NULL OR quantity IS NOT NULL`;
- `period ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'` when present;
- `status = 'VOIDED'` exactly when `voided_at` is set (with `void_reason`);
- unique `(legacy_source, legacy_id)` where `legacy_source IS NOT NULL`.

Indexes: `(payee_id, status)`, `(group_id, entry_date DESC)`, `(entry_date DESC)`, `(batch_id)` where present, `(type_id)`.

The rules a check cannot express (the type and payee belong to the entry's group; the group's mode and direction match the kind; a
deduction type gives a negative amount; counted amount = quantity × price) are enforced by the service (`AccRules`, section 6.1).

### 5.3 Seed (same migration, fixed ids)

Fixed ids, so the move, the tests and the code agree. They are also constants in `AccIds.java`.

- Groups `a0000000-0000-4000-8000-00000000000N`: 1 Offline instructors, 2 Staff, 3 Suppliers, 4 Personal, 5 Other income.
- Types `a0000000-0000-4000-8000-0000000001GT` (G = group digit, T = position): as in section 4.2. The instructor group's Lecture is
  `…0111`, Exam `…0112`, Answer sheet evaluation `…0113`, Question making `…0114`, Bonus `…0115`, Deduction `…0116`, Other `…0117`.
- Money accounts `a0000000-0000-4000-8000-00000000020N`: 1 Cash box (CASH), 2 bKash, 3 Nagad, 4 Rocket, 5 Bank.
- Bangla names for every seeded row, natural Bangla (section 3).

### 5.4 Moving the old payments (`V107_2__accounting_move.sql`, SQL only)

1. **Refuse to run** (`RAISE EXCEPTION`) if any `offline_instructor_other_payments` or `offline_class_payments` row is `PENDING`, or any
   `payment_edits` row of kind `CLASS` or `OTHER` is `PENDING`.
2. **Payees**: one `acc_payees` row per `offline_instructors` row (archived ones too), **same id**, in group 1; name, phone, note,
   archived_at, created_by, created_at copied.
3. **Hand-recorded payments** (`offline_instructor_other_payments`, `APPROVED` or `VOIDED`): for each, a **charge** and a **payment**,
   both with `legacy_source = 'OTHER'`. Only the payment carries `legacy_id` (the old row's id); the charge has `legacy_id` empty and
   is found through the payment's `paired_entry_id`.
   - Charge: type by `kind` (LECTURE → Lecture, EXAM → Exam, OTHER → Other), `quantity = class_count`, `unit_price` empty,
     `amount` = the payment's amount, date = `paid_on`, batch copied, description = the old `reason`, note "Moved from instructor
     payments".
   - Payment: date, amount, batch, reference, note, `recorded_by`, `recorded_at`, `corrected_at` copied; account by method (CASH →
     Cash box, BKASH → bKash, …); `paired_entry_id` = the charge.
   - A `VOIDED` row: both entries voided with the old `voided_by`, `voided_at`, `void_reason`.
   - `REJECTED` rows are not moved.
4. **Class payments** (`offline_class_payments`, `APPROVED` or `VOIDED`): the same, `legacy_source = 'CLASS'`, `legacy_class_id` = the
   class, type by the class's kind, quantity 1, batch = the class's batch, description "Class: <subject> on <class date>". The payee
   is the **payment's** `instructor_id` (not the class's current instructor, which may have changed since).
   For every moved entry: `recorded_by` and `recorded_at` from the old payment, `created_at` and `updated_at` = `recorded_at`.
5. Numbers (`no`) in order of `paid_on`, then `recorded_at`.
6. **Check before commit** (`RAISE EXCEPTION` on any mismatch):
   - the sum of active payments in group 1 equals the sum of the old `APPROVED` payments of both tables;
   - every moved payee's balance is 0.

The old tables are left as they are, read by nothing after the release. Dropping them is a later clean-up.

## 6. API

### 6.1 Code layout (`com.coachingcenter.api.accounting`)

JdbcTemplate and records, like `offline/OfflineInstructorPayService`. Controllers under `/api/v1/accounting/...`, guarded by the new
permission `ACCOUNTING` (Master Admin). Refusals are 409 `ACC_REFUSED` with `message = "accounting.errors.<key>"`; field errors are
400 as elsewhere; a stale `version` is 409 `CONFLICT`.

| Class | Owner | What |
|---|---|---|
| `AccIds`, `AccRules`, `AccMoney`, `AccDates`, `AccRefusals`, `AccBalances`, `AccPermissionContributor` | F | Shared: fixed ids; field and cross-row rules; amount maths (`quantity × price`, half up, caps); Dhaka today, month parsing; refusal helper; balance SQL for one payee, a group, or all; permission. |
| `AccGroupService`, `AccTypeService`, `AccAccountService` + controllers | L | Groups, types, money accounts. |
| `AccPayeeService` + controller | L | Payees, the `offline_instructors` mirror, last prices, linked user. |
| `AccEntryService` + controller | L | Create (one, paid now, several), copy source, edit, void, legacy class unlock. |
| `AccReportService` + controller | R | Home, owed, by month, group summary, statement, entries list and CSV. |
| `finance/FinanceService` changes, `offline/PaymentRecordService` changes | R | Section 4.7 and 4.8. |

The routes and JSON shapes are in [orchestration/contracts.md](orchestration/contracts.md).

### 6.2 What goes

- `OfflineInstructorPayController`, `OfflineInstructorPayService` and their 8 routes (`/offline-instructors/{id}/payments`,
  `/pay-summary`, `/offline-instructor-payments/**`).
- `POST /offline-instructors`, `PATCH /offline-instructors/{id}`, `POST /offline-instructors/{id}/archive` (now payees).
- `GET /offline-instructors` **stays** (the routine picker, `OFFLINE_BATCH_ROUTINE`), without pay figures.
- `OFFLINE_INSTRUCTOR_PAY` stays only while something still checks it; the orchestrator removes it at the end if nothing does.
  **Careful:** `OfflineInstructorPermissionContributor` also grants `FINANCE_READ`, which Finance needs. Remove only the
  `OFFLINE_INSTRUCTOR_PAY` line, never the class or the `FINANCE_READ` grant.
- Old staff notifications and bookmarks that link to `/staff/offline-instructors/...` keep working through the web redirects (4.6).

### 6.3 Audit actions

`ACC_GROUP_CREATED`, `ACC_GROUP_UPDATED`, `ACC_GROUP_ARCHIVED`, `ACC_GROUP_RESTORED`, the same four for `ACC_TYPE_`, `ACC_PAYEE_`,
`ACC_ACCOUNT_`, then `ACC_ENTRY_CREATED`, `ACC_ENTRIES_CREATED` (several at once; one audit row per entry), `ACC_ENTRY_EDITED`,
`ACC_ENTRY_VOIDED`. The entity id is recorded; amounts and phone numbers are not put in the audit log.

## 7. Release

Only on Shahriar's go. API and web ship together (the old pages and routes go in the same release).

**Branch flow (Shahriar, 27 Nov):** everything is built and tested on `acc/integration`, a branch of its own made from
`demo/client-review`. Only after gate 3 and Shahriar's browser check is it merged into `demo/client-review`, and that merged result is
what gets deployed:

- During the build, if `demo/client-review` gets new commits (a hotfix), merge `demo/client-review` into `acc/integration` and rerun
  the API full suite; check the migration rule of 5.1.
- At the end: in the integration worktree, merge `demo/client-review` into `acc/integration` one last time; gate 3 on that. Then on a
  worktree of `demo/client-review`: `git merge --no-ff acc/integration`; run the API full suite and the web suite, lint and build once
  more on the merged `demo/client-review`; then the steps below. Nothing is pushed or deployed without Shahriar's go.
- Use `docs/exam-site/plans/instructor-pay-release.md` and `docs/exam-site/reports/client-review-deploy.md` as the templates for the
  release record and the deploy routine.

1. On the PC: restore a fresh dump of production (symam-vps) into a scratch database; run Flyway; the move's own checks must pass.
   Compare Finance before and after for every month since the first record: `OFFLINE_INSTRUCTORS` totals and batch costs must match
   to the paisa. Record the numbers in the release note.
2. On production, before deploying: `PENDING` count in both old payment tables and in `payment_edits` (kinds `CLASS`, `OTHER`) must be
   0; if not, the Master Admin clears them on the current pages first.
3. Build the web with `OFFLINE_ADMISSION=1` and an **empty** API base (`release-build-offline-flag`, `web-build-api-base`); check the
   built chunks before the swap.
4. Fresh `pg_dump` on the server, check that no exam sitting is open, deploy API and web, watch Flyway reach 107.2 (or 114).
5. Live checks as Master Admin: home figures; an old instructor's payee page shows balance 0 and their old payments; add a counted
   charge with Paid now and see balance and Finance; void it; voucher and statement print; System Admin gets no menu item and 403.

## 8. Not in this version

Receipt photos; locked months; money account balances and transfers; reminders and due dates; budgets; automatic salary; a rate table;
payout account numbers for offline payees; staff or instructors seeing their own accounting entries (offline payees have no login);
dropping the old pay tables.

## 9. Risks

| Risk | Guard |
|---|---|
| The move changes Finance history | The migration checks its own totals; the release compares Finance month by month on a production copy. |
| Old and new pay code running side by side | No switch: the old routes, pages and writes go in the same release as the move. |
| Merge clash with the courses build (`finance/**`, `PaymentRecord*`, `staff-nav.ts`, i18n index, `Permissions.java`, `AuditLogIT`) | Whichever ships second merges the other's branch first; the orchestrator resolves by keeping both sets of changes. Finance sources are additive. |
| Instructor ids shared with `offline_instructors` | One service writes both in one transaction; a test checks they stay equal. |
| Bangla in printouts | Vouchers and statements are HTML print pages, not PDFBox (which cannot draw Bangla). |
