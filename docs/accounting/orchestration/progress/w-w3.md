# W3: entries, entry page, home, reports (progress note)

Built (web only):
- API hooks (`web/src/lib/api/accounting/`): `reports.ts` (dashboard, owed, monthly, group summary), `entries.ts` additions (one entry, edit, void, CSV download with the same filters as the list).
- `components/staff/accounting/csv.ts`: CSV built in the browser (byte order mark, CRLF, quoting). Used by every report's CSV.
- Home (`accounting-home.tsx`): cards (Owed now with the advance under it; Spent this month with last month; Income this month), a row per live group (owed for Payees, this month for Log), Owed the most (top 10), Latest entries (10), and the twelve months.
- `month-chart.tsx`: the 12-month chart with its figures in a table under it (a `details` element). See the open question on reuse.
- Entries list (`entries/entries-page.tsx`, route `/staff/accounting/entries`): filters in the URL (from, to, group, payee, type, kind, account, batch, status, search), payee and type choices follow the group, totals of the rows shown, paging, Download CSV with the same filters. From defaults to the first of this month, to to today.
- Entry page (`entries/entry-page.tsx`, route `/staff/accounting/entries/[entryId]`): all fields, paired entry link, void details when voided, corrections (before to after, reason, who, when), Edit (form with a required reason; a moved class payment changes only money fields and reference, note, date), Void (required reason; for a charge with an active payment, "Also void the payment made with it?" ticked by default), Voucher button (link to the W4 page) for payments, spending and income.
- Reports (`reports/reports-page.tsx`, route `/staff/accounting/reports`): Owed now (total, advance, per group with people), By month (from and to months, at most 36, default the last 12; a column per month; expense and income rows), Group summary (group and date range; totals per type; per person for a Payees group). Each tab has Print (`window.print` inside `PrintArea`) and CSV.
- i18n: `home`, `entry`, `edit`, `reports` namespaces added; `entries` and `log` extended in both files. Literal keys all checked present (script).

Tests (`web/tests/accounting`): `home.test.tsx` (3), `entries.test.tsx` (4), `entry-page.test.tsx` (4), `reports.test.tsx` (7). Accounting total: 66 tests in 12 files, all pass (`npx vitest run tests/accounting`). `npx tsc --noEmit -p .` passes.

Fixes found by the tests (for the record):
- The entries list crashed when a group's payee list had an undefined row: a fixture import mistake in the test, fixed in the test. The page itself is unchanged.

Open questions for the orchestrator:
1. Plan 4.6 says the 12-month chart reuses the Finance chart. `components/staff/finance/finance-chart.tsx` is keyed to Finance sources (its buckets and `INCOME_SOURCES`/`EXPENSE_SOURCES`), and accounting months have no such sources yet. I wrote a small chart in the same palette (`--fin-src-1`, `--fin-src-2`) with the figures in a table instead. If the orchestrator wants the Finance component, that needs the ACCOUNTS sources in the Finance model (W4 territory, and R's change), so I left it.
2. `GET /groups/{id}/summary` is sent with `from` and `to` as `YYYY-MM-DD` dates (the contract does not give the format of these two; the monthly report uses months). Please confirm.
3. The Print buttons call `window.print()` inside `PrintArea`. The print stylesheet (A4 and A5 pages, voucher and statement) is W4's; until then the report prints the app's plain layout.
4. The payment "Voucher" button links to `/staff/accounting/entries/<id>/voucher`, built in W4.
5. The "Edit" form sends `accountId` only for non-charge entries (a charge has no money account), and `amount` only for non-counted types, matching contract 3.1.

Routes called in W3 (new): `GET /dashboard`, `GET /reports/owed`, `GET /reports/monthly`, `GET /groups/{id}/summary`, `GET /entries`, `GET /entries/{id}`, `PATCH /entries/{id}`, `POST /entries/{id}/void`, `GET /entries.csv`.
