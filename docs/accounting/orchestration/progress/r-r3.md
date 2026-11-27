# Progress R3: Finance and payment records

Built:
- `finance/FinanceService`:
  - `OFFLINE_INSTRUCTORS` reads the active payments of the Offline instructors group (`acc_entries`, group `a0000000-…0001`) when that
    group's `in_finance` is on. Description `Instructor pay: <payee name> (<type name or Payment>)`; reference, id and batch from the entry.
    Still under the offline admission switch.
  - New `ACCOUNTS_EXPENSE`: active PAYMENT and SPEND of every other Expense group with `in_finance` on. Description
    `<group name>: <type name or payee name>`.
  - New `ACCOUNTS_INCOME`: active INCOME of Income groups with `in_finance` on. Same description form. Not under the offline switch.
  - Both added to `ALL` (at the end), to `direction` (EXPENSE for ACCOUNTS_EXPENSE, INCOME for ACCOUNTS_INCOME), and to `BATCH_COLUMN`.
  - Batch filter: the offline sources and ACCOUNTS_EXPENSE (`BATCH_SOURCES`). ACCOUNTS_INCOME is not offered under a batch.
  - `batches()`: cost = OFFLINE_INSTRUCTORS + ACCOUNTS_EXPENSE entries with a batch; only those sources and the fee source are grouped.
  - `instructors()`: payments from `acc_entries` (group 1, active PAYMENT) by payee; names from `acc_payees` (group 1, archived too).
- `offline/PaymentRecordService` and `PaymentRecordController`:
  - CLASS and OTHER removed from the list (no rows, no kind filter, no visible kind), from the edit flow (`apply`, `table`, `personName`),
    and from `KINDS`. `EXPENSE` now holds STAFF only. `applyClass` deleted.
  - The `instructorId` filter removed (controller parameter and `Filters` field). `PaymentRow` keeps its `instructorId` and
    `instructorName` fields, which are always null now (the JSON omits them).
  - `waiting()` refuses a correction whose kind is not in KINDS (409 ADMISSION_STATE), so an old CLASS or OTHER correction cannot be
    finalized or rejected from this page.
  - `CLASS` and `OTHER` constants kept (retired, unused here), because `OfflineInstructorPayService` still names them until L deletes it.
- Test helper: `AccTestData.inBatch(entry, batch)` added at the end.

Tests (acc-mvn.ps1 -Agent R -Tests "FinanceIT,FinanceSwitchOffIT,PaymentRecordKindsIT,OfflineAdminIT,InstallmentPlanIT"; one run, all green):
- FinanceIT: 11 run, 0 failures. The seed's old instructor pay rows are now ledger rows (payments of the offline instructors group, a
  voided payment for the left-out 75 and 60, a charge of 150); every expected total is unchanged. New test
  `theAccountingSourcesCount...`: ACCOUNTS_EXPENSE 200 and ACCOUNTS_INCOME 60 in March (a group off in Finance left out, a voided spend
  left out), the batch cost 120 from the printing spend, the batch row, the descriptions, the instructors table from the ledger.
- FinanceSwitchOffIT: 1 run, 0 failures.
- PaymentRecordKindsIT (new, offline package): 2 run, 0 failures. Listed kinds are FEE, DIRECT, ONLINE, STAFF only; kind CLASS or OTHER
  lists nothing; an old instructor filter is ignored; a correction of CLASS or OTHER is 404.
- OfflineAdminIT (FEE corrections): 24 run, 0 failures. InstallmentPlanIT (DIRECT list and corrections): 8 run, 0 failures.

Not run by me: `OfflineInstructorPayIT` (L deletes it). On this branch its payment-records assertions (the `kind=OTHER` list with an
`instructorId` filter, and the `instructorId` list) depend on the removed behaviour, so it will fail here until L's branch removes it.

Decisions where the spec was silent:
- A batch filter offers ACCOUNTS_EXPENSE but not ACCOUNTS_INCOME (a batch's income is not part of its figures).
- Payments with no charge to pair (`paired_entry_id` empty) show `(Payment)` in the OFFLINE_INSTRUCTORS description.

For W (Finance source keys and description changes):
- New keys: `ACCOUNTS_EXPENSE` (direction EXPENSE), `ACCOUNTS_INCOME` (direction INCOME). They appear in `summary.sources`, `monthly[].bySource`
  and the entries `bySource` like the others. A batch-filtered summary offers `OFFLINE_FEES`, `OFFLINE_INSTRUCTORS`, `ACCOUNTS_EXPENSE`.
- `OFFLINE_INSTRUCTORS` keeps its key; its descriptions now read `Instructor pay: <payee> (<type or Payment>)`.
- Payment records: `kind` is one of FEE, DIRECT, ONLINE, STAFF; `CLASS` and `OTHER` are gone from the list and the correction routes (404).
  `instructorId` is no longer a filter. Shapes are otherwise unchanged.

For the orchestrator (release):
- Pending corrections of kinds CLASS and OTHER in `payment_edits` cannot be finalized or rejected after this build (they are not listed and
  answer 409). Clear them on the current pages before the release (plan 7, step 2). This is a hard precondition.
- `FinanceSwitchOffIT` says the batch filter has only offline sources; with ACCOUNTS_EXPENSE it has one more. Its assertion still holds (it has
  no accounting rows), but its comment is now out of date. It is not my file.

Open questions for the orchestrator: none blocking.
