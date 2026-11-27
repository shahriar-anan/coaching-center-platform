# Progress L3: entries (create, several, copy last month, read)

Built (package `accounting.ledger`):
- `AccEntryService` + `AccEntryController`:
  - `POST /entries`: one entry, or a charge with its payment ("paid now": the payment starts as the charge, may be less, not more;
    the charge is saved first, the payment points at it with `paired_entry_id`; the answer lists the charge first).
  - `POST /entries/many`: 1 to 200 lines, no paid now, all or nothing. A bad line refuses the whole save; a field error names the line
    as `entries[i].field`; more than 200 lines is `too_many_lines`.
  - `GET /groups/{id}/copy-source?month=YYYY-MM` (default last month): active charges of a Payees group or spending of an expense Log
    group, dated today, period moved one month on, `alreadyThisMonth` when an active entry this Dhaka month has the same payee, type and
    period. Payments and income are never offered; an income group answers an empty list; a moved-over charge is copied as a plain amount.
  - `GET /entries/{id}`: the entry's fields at the top level (`@JsonUnwrapped`), the paired entry, and the edits (empty until L4).
- Every input goes through `AccRules.check` (and `AccRules.paidNow`), with the amount maths of `AccMoney`. Unknown group, payee, type,
  account, batch, exam, or entry: 404. Entry fields that do not apply to the kind are not stored (reference: not on a charge;
  counterparty: spending and income only; live exam: charges only).
- Audit: `ACC_ENTRY_CREATED` per saved row (a paid-now payment too); `ACC_ENTRIES_CREATED` per row of a "many" save.
- Routes added: 4 (`POST /entries`, `POST /entries/many`, `GET /groups/{id}/copy-source`, `GET /entries/{id}`).

Tests (`acc-mvn.ps1 -Agent L -Tests ...`), all green:
- AccEntryIT 11 run, 0 failures: each kind in the right and wrong group mode; the counted maths (2000.00; 1.5 x 999.99 = 1499.99 half up;
  numbers and strings accepted; three decimals refused as a field error; quantity_required; price_required); a deduction stored negative
  and lowering the balance, and paid-now refused on it; the month and description types; the future date refused, a bad date a field
  error; paid now in full and in part, more than the charge refused, and the detail's paired entry both ways; several lines all or
  nothing with the line named, the audit row, the empty and the 201-line refusals; copy last month (the moved period, already this month,
  payments excluded, income empty, a bad month); the unknown ids as 404, archived account, type and group refused for new entries; one
  audit row per saved entry.
- AccAccessIT 1 run, 0 failures (31 routes: 18 + 9 + 4 now).
- AuditLogIT 3 run, 0 failures.

Decisions where the plan was silent:
- `reference` is kept for payments, spending and income only; `counterparty` for spending and income; `liveExamId` for charges only; the
  batch for every kind. The paid-now payment takes the charge's batch.
- `amount` typed for a counted type is ignored (the server works it out), so a web that sends the computed amount does not fail.
- The copy draft of a moved-over counted charge has no quantity and no price; saving it unchanged is refused `quantity_required`, which
  asks the user for the price (a new entry of a counted type always needs both, plan 4.4).
- Copy of a charge with a deduction gives the amount as a positive input (the stored sign is applied on save).
- The "many" error field is `entries[i].field`; a refusal (409) is not renamed.
- `EntryDetail` uses `@JsonUnwrapped` on the entry (the contract's flat shape). Verified by the test.

Open questions: none new for the orchestrator. The `GET /entries` list, CSV and the filters are R's.
