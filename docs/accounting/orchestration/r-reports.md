# Workstream R: reports, Finance and Payment records (phase 2)

Contracts section 3.2. Read [README.md](README.md), [contracts.md](contracts.md), plan sections 4.6 (home, entries, reports,
statement), 4.7, 4.8. Package `com.coachingcenter.api.accounting.report`. Use F's shared classes (`AccBalances` above all); do not change
them. Your tests create data with `AccTestData` (add methods at the end if you need more), never through L's services.

Before writing tests read README section 5.1: the database is shared and never emptied, so home, reports and Finance figures are
asserted as differences or inside a year only your test class uses.

## R1. Entries list, CSV, home

- `GET /entries`: every filter of the contract; `q` matches description, note, reference, counterparty, payee name and `no` (a number
  typed as `1042` or `#1042`). `totals` cover every matching active row, not only the page. Sorts as listed; ties by `no` descending.
  One SQL for the page and one for the totals; joins for names (group, payee, type, account, batch, exam, recorded and voided by).
- `GET /entries.csv`: same filters and order; UTF-8 with a BOM so Excel shows Bangla; at most 20,000 rows (`too_many_rows`). File name
  `accounting-<from>-<to>.csv`. Amounts as plain numbers (`1250.00`), dates `YYYY-MM-DD`.
- `GET /dashboard`: contracts `Dashboard` and section 4. "This month" and "last month" are Dhaka months of `AccDates.today()`.
  `months`: 12, oldest first, ending this month; months with nothing are `"0.00"`. `latest`: the 10 newest by `recorded_at`.
- `AuditLogIT`: add your routes in a `// ACC-R` block.
- Tests `AccEntriesListIT` (each filter, totals across pages, voided excluded from totals but listed, sorts, `#` search, CSV header,
  BOM and a Bangla name), `AccDashboardIT` (each figure with charges, payments, deductions, spending, income, a voided entry and an
  entry of last month; a group with `inFinance` off still counts here), 403 for a System Admin on each route.

## R2. Reports and statement

- `GET /reports/owed`: every live payee with a balance that is not zero, by group in group order, payees by balance descending;
  archived payees with a balance are included (marked `archived`).
- `GET /reports/monthly`: range checks (`from` ≤ `to`, at most 36 months, 400 otherwise); one row per group that has anything in the
  range, in group order; `expense` and `income` per month.
- `GET /groups/{id}/summary`: plan 4.6 Reports; `quantity` per type summed only for Counted types (else null).
- `GET /payees/{id}/statement`: `opening` = balance from active entries dated before `from`; `lines` = active entries in the range,
  oldest first by date then `no`, each with `balanceAfter`; `closing` = opening + charged − paid (deductions count inside `charged` as
  negative). Range default: this Dhaka month.
- Tests `AccReportsIT`, `AccStatementIT`: worked examples with exact figures, including an advance, a deduction, a voided entry and an
  entry on the range's first and last day.

## R3. Finance and Payment records

Real-money pages. Every existing `FinanceIT` and payment-records assertion keeps its meaning; where an assertion seeds old instructor
payment rows, seed the same money through `AccTestData` instead and keep the expected totals.

- `FinanceService`:
  - `OFFLINE_INSTRUCTORS`: replace the union over `offline_class_payments` and `offline_instructor_other_payments` with active
    `PAYMENT` entries of group `AccIds.GROUP_OFFLINE_INSTRUCTORS` when that group's `in_finance` is on. Description
    `'Instructor pay: ' || payee name || ' (' || type or 'Payment' || ')'`; `reference`, `id`, `batch_id` from the entry. Keep its
    place under the offline admission switch as today.
  - New `ACCOUNTS_EXPENSE` (active `PAYMENT` and `SPEND` of every other Expense group with `in_finance` on; description
    `group name: type name or payee name`) and `ACCOUNTS_INCOME` (active `INCOME` of Income groups with `in_finance` on). Add them to
    `ALL`, to the expense/income direction, and to `BATCH_COLUMN`; not under the offline switch.
  - Batch cost (`batches`): `OFFLINE_INSTRUCTORS` and `ACCOUNTS_EXPENSE` entries with a batch.
  - `instructors()`: read group 1's active payments from `acc_entries` (and names from `acc_payees`).
- `PaymentRecordService` and controller: remove the `CLASS` and `OTHER` kinds from the list and the edit flow, and the `instructorId`
  filter. Fee and direct enrolment records are unchanged. Old rows stay in `payment_edits`.
- Tests: `FinanceIT` updated and extended (new sources; `in_finance` off hides a group; batch cost from a printing spend). The move's totals are
  checked by F's `AccMoveIT` and the release rehearsal, not here. Payment records tests updated.
- Report to W: the new Finance source keys and anything removed from the payment records answer.

## Done when

R1 to R3 committed with progress notes; it compiles; your test classes pass (if allowed); the report lists the Finance and payment
records changes W must mirror.
