# Progress F3: shared classes (com.coachingcenter.api.accounting)

Built:
- `AccIds`: the seeded ids as UUID constants (groups, the instructor/staff/personal/income types used, the five accounts).
- `AccMoney`: `counted` (quantity x price, half up to the paisa), `parseMoney` and `parseQuantity` (form checks: above 0, at most
  2 decimals; quantity at most 100,000; field errors 400), `format` (money, 2 decimals), `formatQuantity` (no trailing zeros). The
  caps of plan 4.4 (amount 1,00,00,000; unit price 10,00,000) are refusals in AccRules, not parse errors.
- `AccDates`: `today(clock)` in Dhaka, `parseDay`, `parseMonth`, `monthOf`, `notFuture(day, today)` (refusal `future_date`).
- `AccRefusals`: `refuse(key)` (409 ACC_REFUSED, message `accounting.errors.<key>`), `stale()` (409 STALE_VERSION), `notFound(what)`,
  `validation(field, message)` and `validation(list)` (400 VALIDATION_FAILED).
- `AccRules`: pure. `check(draft, group, type, payee, account, was, legacy, today)` returns the values to store; `paidNow(charge,
  paid, account, wasAccountId)` returns the PAYMENT for "Paid now". Refusal keys raised: group_mode, group_archived, payee_required,
  payee_wrong_group, type_wrong_group, type_archived, period_required, description_required, quantity_required, price_required,
  amount_too_large, account_required, account_archived, future_date, paid_now_deduction, paid_now_too_much.
- `AccBalances` (@Component): `balance`, `balances(group)` (every payee of the group, archived ones too, zero when empty),
  `owedAndAdvance(group or null)`, `paidInRange`, `totals(from, to, group or null)` (charged, paid, spent, income; contract section 4).
- `AccPermissionContributor`: grants `Permissions.ACCOUNTING` to MASTER_ADMIN, scope ANY. The offline instructor contributor is
  untouched.
- `ErrorCode.ACC_REFUSED` added at the end (`INTERNAL_ERROR,` now has a comma). `Permissions.ACCOUNTING` added after `FINANCE_READ`.

Test helpers (test sources): `AccTestData` (JDBC: masterAdmin, group, type, payee (also the offline_instructors row for group 1),
account, charge, countedCharge, payment, spend, income, voidEntry). L and R add methods at the end only.

Tests (acc-mvn.ps1 -Agent F -Tests "AccMoneyTest,AccRulesTest,AccBalancesIT,AccSchemaIT,AccMoveIT"): all green:
- AccMoneyTest 5 run, 0 failures
- AccRulesTest 18 run, 0 failures (every refusal key listed above, the field errors for kind, date, type and amount, the edit
  keeping an archived type or account, the legacy counted entry, the paid-now rules)
- AccBalancesIT 2 run, 0 failures (balances, owed and advance, paid in range, range totals per group)
- AccSchemaIT 20 run, 0 failures; AccMoveIT 2 run, 0 failures (run again with the F3 code present)
Total for these five classes: 47 run, 0 failures.

Decisions taken where the plan was silent:
- A counted unit price over the cap is refused as `amount_too_large` (the one money-cap key in contracts.md). A quantity over the
  cap is a field error (400), since the contract has no key for it.
- A deduction type on a kind other than a charge is refused as `group_mode` (the kind does not fit the type's group rules).
- `AccRules.check` does not compare the old and new type kinds on an edit (the sign rule of plan 4.5, `type_kind_mismatch`). The
  caller holds the kind of the entry's own type; L checks it.
- Description length and note/reference length are not checked here; L does them with the field errors.
- `notFound(what)` gives the contract message for every unknown id; `what` is only there for the reader of the code.

Open questions for the orchestrator:
- `AccRules.check` takes `Was` (group, type, account ids). L must pass it on edit so archived values that are kept do not refuse.
- `AccBalances.owedAndAdvance(null)` covers all payees, archived ones too; L and R must agree on whether the home page owed total
  counts archived payees (plan 4.3 says a payee with a balance may be archived, so the figure includes them).
