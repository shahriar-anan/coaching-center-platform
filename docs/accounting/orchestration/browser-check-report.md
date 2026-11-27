# Accounting: browser check report (phase 3, gate 3)

Run 27 Nov 2026 on `acc/browser-check` (from `acc/integration`), against a local copy of the newest production dump (database `cc_qa_acc`, Flyway 107 before the move). Desktop 1366×900 and phone 390×844; English everywhere, Bangla on the screens marked (bn). Screenshots and notes are on the PC in `F:\acc-browser-check\` and are referred to by name only. No real person is named here: instructors are "an instructor", payees by their order in the list, and test rows carry the `QA ` prefix.

## 1. Verdict

**Ready after the listed fixes.** Four fixes are committed on this branch (section 5). One bug is left open for Shahriar to decide: the entries list has no payee filter (section 5, B1). Ten UX notes (section 6).

## 2. Release blockers

**None.** No payment was `PENDING` (0 of the offline payment rows, 0 `payment_edits` of kind CLASS or OTHER). The move (V107_1, V107_2) applied; the API logged `Successfully applied 2 migrations`. The figures match (section 3).

Note on the set-up: the literal query `select max(version) from flyway_schema_history where success` answers `99`, because `version` is text. Ordered by `installed_rank` the highest success is `107` before the start and `107.2` after it, as the plan expects.

## 3. Move check (totals and counts only)

| | Before the move | After the move |
|---|---|---|
| Instructor payees | 6 (instructors) | 6 |
| Old payments (approved) | 3 (two hand-recorded, one class) | 3 moved |
| Old payments in the month | 1 month | 1 month, same total |
| Total approved payments | 2,100.00 | 2,100.00 |
| Batch costs | 2 batches (2,000.00 and 100.00) | same two batches, same totals |
| Payees with a balance not zero | – | 0 |
| Pending old payments | 0 | – |

**Match, to the paisa.** The figures were taken before any check write (`notes\before.txt`, `notes\after.txt`). Later steps changed the month: step 3.9.7 voided the moved class payment on purpose, so the moved-only figure for that month is now 2,000.00, which is the expected result.

## 4. Steps

Result per viewport: pass, fixed (the fix is in section 5 and the step passed after it), bug (left open), look (checked by eye, no number to compare), note. Screenshot names: `<section>-<step>-<desktop|phone>-<en|bn>.png` in `F:\acc-browser-check\shots\`.

| Step | Check | Desktop | Phone | Screenshots (prefix) |
|---|---|---|---|---|
| 3.1.1 | Menu: Accounting, no old items | pass | pass | s1-1-menu |
| 3.1.2 | Old addresses redirect (group, payee, entries) | pass | pass | s1-2a, s1-2b, s1-2c |
| 3.1.3 | Payment records: no instructor filter | pass | pass | s1-3-payment-records |
| 3.2.1 | Home cards (owed settled, spent, income) | pass (fixed chart below) | pass | s2-1-home |
| 3.2.2 | Group rows, top owed, latest, 12-month chart | fixed (chart colours, F1) | fixed | s2-2-rows |
| 3.2.3 | Spent this month = SQL | pass (2,100 at check time) | – | – |
| 3.3.1 | Groups: add, rename, move, archive, restore | pass | pass | s3-1-groups, s3b-1 |
| 3.3.2 | Types: add three types for QA Printing | pass | pass | s3b-2-types |
| 3.3.2 | Kind change after use | pass: kind select is locked with a hint, no UI refusal reached | – | s6b-10-kind-refused |
| 3.3.3 | Money accounts: add, archive, restore | pass | pass | s3c-3 |
| 3.4.1 | Old instructors listed, balances 0 | pass | pass (count includes the new test payee) | s4-1-list |
| 3.4.2 | Search by name, phone; show archived | pass | pass | s4-2a, s4-2c, s4c-2d |
| 3.4.3 | Add payee with alias; routine picker offers it | pass | pass | s4b-3-picker |
| 3.4.4 | Duplicate alias refused, readable message | pass | – | s4-4-alias-refused |
| 3.5.1 | Payee summary (balance, totals, batches) | pass | pass | s5-1-summary |
| 3.5.2 | Entries pair up, balance back to 0, references | pass | pass | s5-2-entries |
| 3.5.2 | Voided old payment shows Voided | not checked (none in the copy) | – | – |
| 3.5.3 | Statement for all time, closing 0 | pass | pass | s5-3-statement |
| 3.6.1 | Counted charge 40 × 50 = 2,000, live line | pass | pass | s6-1-counted-live |
| 3.6.2 | Last price starts at 50 | pass | pass | s6-2-last-price |
| 3.6.3 | Paid now: 4,800 → 6,000 → typed 3,000 kept | pass | pass | s6-3-paid-now |
| 3.6.4 | Payment only, account remembered | pass | pass | s6-4-payment |
| 3.6.5 | Overpay shows Advance | pass | pass | s6-5-advance |
| 3.6.6 | Deduction lowers balance, no paid now | pass | pass | s6-6-deduction |
| 3.6.7 | Other without description refused | pass | pass | s6-7-needs-description |
| 3.6.8 | Future date refused; month field for a type that needs one | pass | pass | s6-8-future-date, s6b-11-rent |
| 3.6.9 | Batch and online exam saved and shown | pass | pass | s6-9-batch-exam-entry |
| 3.6.10 | Printing payee; 100 × 15 posters paid with batch | pass | pass | s6b-10-posters |
| 3.6.11 | Monthly rent 8,000; amount pre-fills next time | pass | pass | s6b-11-rent |
| 3.6.12 | Spending 250, no payee asked | pass | – | s6b-12-spending |
| 3.6.13 | Other income 1,500 raises Home income | pass | – | – |
| 3.6.14 | Quick add from Home: kinds, group, payee, form | pass | pass (form reached, not saved) | s14b-1 to s14b-3 |
| 3.7.1 | Several payees: pre-fill, reset on type change, saved | pass | pass (nothing saved) | s7-1-several-rent, s7-1-several-posters |
| 3.7.2 | Type archived in second tab: refusal, nothing saved | pass (readable message, type reset, F-note U5) | – | s7-2-refused |
| 3.7.3 | Copy last month: drafts, two saved | pass | pass (view) | s7-3-copy-drafts, s7-3-saved |
| 3.7.4 | Copy twice: already added unticked | pass | pass | s7-4-copy-twice |
| 3.8.1 | Filters, URL keeps them, totals match | pass (payee filter missing, B1) | pass | s8-1-filtered, s8-1-list |
| 3.8.2 | CSV: BOM, names, deduction sign, `'=1+1` | pass | pass | s8-2 (files) |
| 3.9.1 | Edit note with reason; batch and exam kept | pass | not run | s9-1-corrected |
| 3.9.2 | Edit without reason refused | pass | not run | – |
| 3.9.3 | Moved class payment: amount only, batch kept | pass | view only | – |
| 3.9.4 | Moved counted charge with no price | not checked (none in the copy) | – | – |
| 3.9.5 | Deduction keeps its sign after edit | pass | view only | – |
| 3.9.6 | Void paid-now charge with its payment; no edit after | pass | not run | s9-6-void |
| 3.9.7 | Void moved class payment → class Scheduled | pass (checked in the database) | not run | – |
| 3.10.1 | Owed now report | pass | pass | s10-1-owed |
| 3.10.2 | By month, last 12 | pass | pass | s10-2-by-month |
| 3.10.3 | Group summary this year | pass | pass | s10-3-summary |
| 3.10.4 | CSV on each tab, Print present | pass | pass | files/reports-*.csv |
| 3.11.1 | Voucher of a payment, A5 and A4 PDF, English and Bangla words | pass | not run | s11-1-voucher, s11-1-voucher-bn |
| 3.11.2 | Voided voucher shows VOID | pass | not run | s11-2-void |
| 3.11.3 | Amount-only voucher: no quantity × price line | fixed (F4) | not run | s11-1-voucher |
| 3.11.4 | Statement this month: closing equals payee balance | pass | not run | s11-4-statement |
| 3.12.1 | Finance month totals match the ledger; moved totals as at the move | pass | – | s12-1-this-month |
| 3.12.2 | Sources and labels; Count in Finance off removes spending | pass | – | s12b-2-count-off |
| 3.12.3 | Instructors table lists moved instructors with old totals | pass (API; table not eyeballed) | – | s12b-3-instructors |
| 3.13.1 | System Admin: no menu item, pages say Master only, API 403 | pass | pass | s13-1-accounting-page |
| 3.13.2 | Routine picker works for System Admin | pass | pass | s13-2-picker |
| 3.14 | Phone: no overflow, decimal keyboard, sheet height, Save reachable | – | pass (programmatic) | s5, s8, s6 phone shots |

Home totals, checked against the ledger for this month: spent 21,350.00 and income 1,510.00, both equal to the SQL sums of active payments, spending and income. Owed 47,300.00 is equal to the owed report total.

## 5. Fixes made

| Commit | What | Test added (fails without the fix) |
|---|---|---|
| `039a8aeb` | Month chart bars and legend were invisible: they use the finance palette variables, which only exist under `.finance-viz` with `FinanceTheme`. The chart now carries both. (F1) | `web/tests/accounting/month-chart.test.tsx` |
| `ed94d4f2` | Accounting switches (Paid now, Show archived, In Finance, Needs a month, Needs a description, also void) showed a bare toggle; the shared `Switch` now takes an opt-in `showLabel` and the accounting switches pass it. (F2) | `web/tests/accounting/switch-labels.test.tsx` |
| `ef7356a8` | Counted units read "5 classs ×": the English plural now follows the ending (class → classes). (F3) | `web/tests/accounting/unit-plural.test.ts` |
| `26e2e282` | A payment voucher printed an empty "Quantity × price —" line (and "Type —"). Those lines now print only for entries that have them. (F4) | `web/tests/accounting/voucher-rows.test.tsx` |

Checks run for the fixes: `npx vitest run tests/accounting` → 18 files, 89 tests passed; `npx tsc --noEmit -p .` → no errors. No API code changed, so no API suite was run for this check.

**Open bug, not fixed (B1):** the entries list `/staff/accounting/entries` has no payee filter (the plan's section 4.6 lists one). The API takes `payeeId`, so the gap is in the page: it needs a payee picker that depends on the group. That is a design choice, so it is left for Shahriar.

## 6. UX notes (10, most valuable first)

1. **Home, phone (en, bn):** the 12-month chart scrolls sideways, so the latest months are off screen until you scroll. Suggest six months on a phone, or bars that fit. `s2-1-home-phone-en.png`.
2. **Entries list, phone (bn):** the Kind and Type filters are both labelled "ধরন", so they cannot be told apart. Suggest a different word for each. `s8-1-list-phone-bn-top.png`.
3. **Entries list, phone (bn):** each entry is a tall card of label/value lines (about 250 px), so 17 entries are several screens. Suggest a compact row with date, what and amount, and the rest on tap. `s8-1-list-phone-bn.png`.
4. **Entry form, missing description:** the refusal reads "Say what it was for.", which is the help line, not an error. Suggest "Add a description: say what it was for." `s6-7-needs-description-desktop-en.png`.
5. **Several dialog, type archived elsewhere:** the refusal names the payee and "This type is archived", but the Type field resets to "Choose a type" without a note. Suggest keeping the chosen type and marking it. `s7-2-refused-desktop-en.png`.
6. **Several dialog, desktop:** Cancel and Save sit at the bottom edge of the 900 px dialog and scroll with it. Suggest a sticky footer. `s7-1-several-rent-desktop-en.png`.
7. **Statement and payee page:** the statement writes a negative as "৳-900.00", the payee page as "−৳1,000.00". Suggest one format. `s5-3-statement-desktop-en.png`.
8. **Settings, Types:** the kind select is disabled once a type is used, with the hint "fixed once an entry uses this type". That is clear; a short note on why would help. `s6b-10-kind-refused-desktop-en.png`.
9. **System Admin, accounting page:** the page still requests the dashboard and gets two 403s before it shows "Accounting is for the Master Admin". Suggest not calling the API for other roles. Console noise only. `s13-1-accounting-page-desktop-en.png`.
10. **Batch page (not accounting):** the batch photo request returns 404 when a batch has no photo, which is a console error on every batch page. Suggest the page not request it when there is none.

## 7. Not checked, and why

- **Voided old payment shows Voided (3.5.2):** the copy has no voided moved payments.
- **Moved counted charge without a price (3.9.4):** the copy has none (moved old charges are amount types).
- **Phone pass for edit and void (3.9):** done on desktop; the phone pass opened the moved class payment and a deduction in the edit form only.
- **PDFs (3.11):** A4 and A5 PDFs were written to `F:\acc-browser-check\files` but not opened. Bangla print was judged from the print page screenshot (words and signature lines render).
- **Browser print dialog (3.10, 3.11):** the Print buttons are present and the print pages render; the system print dialog cannot be driven.
- **Finance instructors table (3.12.3):** checked through the API (the moved instructor's old total is 2,000.00); not looked at on screen.
- **Move up and down on groups (3.3.1):** clicked; the resulting order was not asserted separately.
- **Phone, Bangla home (3.2):** the Bangla phone home was captured but not reviewed line by line.
- **Phone, add person (3.4.3):** done on desktop only.
- **Scratch data:** the QA rows listed in section 4 are in `cc_qa_acc` only, which is dropped at clean-up. One setup step was needed: QA Snacks Log is a new Log group with no types, so a type "QA Snack Shop" (Amount) was added to it so spending could be recorded (noted at step 3.6).

## 8. Files

- Report (this file): `docs/accounting/orchestration/browser-check-report.md` on `acc/browser-check`.
- Screenshots, CSV and PDF outputs, notes (`steps.log`, `run.log`, `before.txt`, `after.txt`, `findings.md`, `api.log`, `web.log`): `F:\acc-browser-check\` (not committed).
