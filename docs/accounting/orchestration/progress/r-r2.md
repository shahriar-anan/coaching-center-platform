# Progress R2: reports and statement (package accounting.report)

Built:
- `AccReportService` (R2 part): `owed()`, `monthly(from, to)`, `summary(groupId, from, to)`, `statement(payeeId, from, to)`.
- `AccReportController`: `GET /reports/owed`, `GET /reports/monthly`, `GET /groups/{id}/summary`, `GET /payees/{id}/statement` (all under
  `/api/v1/accounting`; Master Admin only).
- AuditLogIT ACC-R block: the four routes added.

Tests (acc-mvn.ps1 -Agent R -Tests "AccReportsIT,AccStatementIT,AuditLogIT"; two reruns for two fixes, then AccReportsIT alone):
- AccReportsIT: 4 run, 0 failures (owed: totals as differences, payees by balance, archived payee marked, zero left out, advance;
  monthly: per-month sums as differences, voided and out-of-range left out, boundary days, rows per group, the 36-month rule and the
  12-month default; group summary: quantity only for counted types, per type and per payee, voided and boundary days, 404, 400).
- AccStatementIT: 3 run, 0 failures (opening, range, closing, running balance, same-day order by number, balance cross-check with
  `AccBalances.balance`, default range this month, 404, 400, 403).
- AuditLogIT: 3 run, 0 failures.

Decisions where the spec was silent:
- Owed: all groups, archived ones included, with a group's payees in balance order; an archived group's payees are listed too, so the
  total equals the home page's owed total.
- Monthly: a row for every group with at least one active entry in the range, charges included (a charge-only group shows zeros).
  `from` alone means up to this month; `to` alone means eleven months before it. Expense is payments and spending in expense groups;
  income is income entries; both are sums over every group, whatever its Finance switch.
- Group summary: `byPayee` lists the payees with a charge or payment in the range (Payees groups only; a Log group gives []).
  `quantity` is the sum for counted types only.
- Statement: each day defaults to this month's first or last day. `balanceAfter` is set on each line.

For W (important):
- The API omits null fields (`spring.jackson.default-property-inclusion=non_null`), so a null in the contract arrives as a missing key:
  `voidedAt`, `quantity`, `unitPrice`, `lastPaymentOn`, `lastPaymentAmount`, `balanceAfter` (outside statement lines), `payeeName`, and so on.
  Read a missing key as null.

Open questions for the orchestrator: none for R2.
