# Progress R1: entries list, CSV and home (package accounting.report)

Built (`api/src/main/java/com/coachingcenter/api/accounting/report/`):
- `AccReportController` (`/api/v1/accounting`): `GET /dashboard`, `GET /entries`, `GET /entries.csv`. Master Admin only (ACCOUNTING); others 403.
- `AccEntryListService`: every filter of contract 3.2 (from, to, groupId, payeeId, typeId, kind, accountId, batchId, status, q, sort, page,
  size). Totals and the count come from one statement; the page from a second. CSV: BOM, the contract columns, `too_many_rows` above 20,000.
- `AccReportService`: `dashboard()` (owed and advance from `AccBalances.owedAndAdvance(null)`, the month and last month from
  `AccBalances.totals`, each live group, top 10 owed, 10 latest by recorded time, 12 months).
- `AccEntryRows` (the shared entry select and mapper) and `AccPayeeRows` (the payee rows with balances; R2 reuses it).

Tests (acc-mvn.ps1 -Agent R -Tests "AccEntriesListIT,AccDashboardIT,AuditLogIT"; then AccEntriesListIT alone after one fix):
- AccEntriesListIT: 7 run, 0 failures (paging and totals with a voided row, each filter, search by text and by number, sorts, CSV
  BOM/header/Bangla name/filename, 400 for bad values, 403 for a System Admin).
- AccDashboardIT: 2 run, 0 failures (figures as differences: owed, advance, spent and income this month and last, the 12 months, a group
  with in_finance off still counted, the group rows, latest order, top owed sorted; 403).
- AuditLogIT: 3 run, 0 failures (ACC-R block of three routes added).

Decisions where the spec was silent:
- `balanceAfter` is a field of the shared entry shape, null in every list. It is set only in a statement line (R2). W: read it as optional.
- CSV names are the English names (group, payee, type, account); "Unit" is the type's English unit.
- Home "owed" counts archived payees and archived groups (AccBalances rule); the group rows list live groups only.
- The search matches a number typed as `1042` or `#1042`; the `#` in a test URL must be sent as a parameter, not in the template (MockMvc
  encodes it twice). No effect on the browser.
- AccTestData gained one method at its end: `annotate(entry, description, reference, note)`.

Not tested: `too_many_rows` (needs over 20,000 rows; the branch is a plain count check).

Open questions for the orchestrator: none for R1.
