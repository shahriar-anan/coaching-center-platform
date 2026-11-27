# Workstream F: schema, move and shared code (phase 1)

Agent F works alone; L, R and W wait for it. Read [README.md](README.md), [contracts.md](contracts.md) and plan sections 4, 5 and 6.1.
Your branch starts from `demo/client-review` (production), which has **no** `lms/` code: do not look for `LmsErrors` and the like
there; build the equivalents below from what the branch has.

## F1. Schema and seed: `V107_1__accounting.sql`

- The tables, checks and indexes of plan 5.2, the sequence `acc_entry_no_seq`, and the seed of plan 5.3 with the fixed ids.
- Seed Bangla names in natural Bangla (plan section 3; look at `web/src/i18n/messages/bn/instructorPay.ts` and `staffPay.ts` for the
  terms in use: শিক্ষক, বেতন, …). Seeded `created_by` is null.
- Additive only: no existing table is altered.
- Test `AccSchemaIT`: the seed is present with its ids; each check of plan 5.2 refuses a bad row (one assertion per check); the partial
  unique indexes allow a duplicate name once the first is archived.

## F2. Moving the old payments: `V107_2__accounting_move.sql`

- Exactly plan 5.4, in SQL (a `DO $$ … $$` block for the refusals and the final checks). Read `V88`, `V89`, `V90`, `V104`, `V106` and
  `V107` first for the old columns. Old `method` → account id: CASH `…0201`, BKASH `…0202`, NAGAD `…0203`, ROCKET `…0204`, BANK `…0205`.
- Class payments: the class kind is `offline_classes.kind` (LECTURE, EXAM, OTHER); the description uses `subject` and the class's date
  (read `V89` for the column name).
- Numbers: assign `no` with `nextval('acc_entry_no_seq')` in the order of plan 5.4 step 5 (charge before its payment).
- Test `AccMoveIT`. Flyway migrates the test database to the latest version at start-up, so build a second, empty schema (or database)
  in the test with the Flyway API: `target("107")`, insert old rows (instructors, an archived instructor, approved, voided, rejected
  hand payments with each kind and with and without `class_count`, approved and voided class payments, each method), then migrate to
  the latest and assert:
  - payees: same ids, names, archived state;
  - per old approved payment: a charge and a payment, paired, right type, quantity, batch, account, description, `legacy_*`;
  - voided rows voided on both entries with the old reason; rejected rows absent;
  - every balance 0; the active payment total equals the old approved total;
  - a `PENDING` row (in either table, or a pending `payment_edits` of kind CLASS or OTHER) makes the migration fail.
  Drop the scratch schema in `@AfterAll`.
  How: `Flyway.configure().dataSource(ExternalPostgres.url(), user(), password()).schemas("acc_move_" + random)
  .createSchemas(true).locations("classpath:db/migration").target("107").load().migrate()`, insert with a `JdbcTemplate` whose
  connections set `search_path` to that schema, then the same configuration with `target("latest")`. No migration uses `public.` or
  `CREATE EXTENSION`, so a separate schema works (checked 27 Nov). The old rows need a `users` row (seed one in the scratch schema)
  and an `offline_batches` and `offline_classes` row for class payments (read their NOT NULL columns in V81, V89, V100, V103).
  If the Flyway API route fails for a reason you cannot fix, fall back to: copy the SQL of `V107_2` into a test that runs it inside
  the shared database's transaction, rolled back at the end, and say so in the report.

## F3. Shared classes (`com.coachingcenter.api.accounting`)

| Class | What |
|---|---|
| `AccIds` | The fixed ids of plan 5.3 as `UUID` constants (groups, the instructor types, the accounts). |
| `AccMoney` | `counted(quantity, unitPrice)` (half up, 2 places); `parse(String)` for money and quantity with the limits of plan 4.4 (amount ≤ 1,00,00,000; unit price ≤ 10,00,000; quantity ≤ 100,000; > 0; ≤ 2 decimals); `format(BigDecimal)` → `"1250.00"`. |
| `AccDates` | `today()` in Dhaka from the `Clock` bean; `parseDay`, `parseMonth`, `monthOf(day)`, `notFuture(day)`. |
| `AccRefusals` | `refuse(String key)` returns `new ApiException(ErrorCode.ACC_REFUSED, HttpStatus.CONFLICT, "accounting.errors." + key)`. Add `ACC_REFUSED` at the **end** of `ErrorCode.java`. Also `stale()` (`STALE_VERSION`, 409) and `notFound(what)` (`RESOURCE_NOT_FOUND`, 404), worded as `OfflineInstructorService` words them. |
| `AccRules` | Pure checks on an `EntryInput`-like record plus the looked-up group, type, payee and account rows: kind fits mode and direction; type and payee belong to the group; required fields per kind and type (plan 4.4 table); counted amount; deduction sign; `paidNow` only on a charge that is not a deduction (`paid_now_deduction`) and not more than the charge; a `legacy` entry of a Counted type may keep a quantity with no price and a typed amount; archived group, type or account refused only when newly chosen (an edit keeping the entry's own archived type or account passes; take a "was" value for this). Returns the normalised values (amount, stored sign) or refuses with the contract's keys. L calls it on create, many and edit. |
| `AccBalances` | SQL helpers: `balance(payeeId)`, `balances(groupId)` (map payee → balance), `owedAndAdvance(groupId or all)`, `paidInRange(payeeId, from, to)`, and the "spent" and "income" sums of contracts section 4 for a range, optionally by group. Used by L (payee rows) and R (reports). |
| `AccPermissionContributor` | Grants `Permissions.ACCOUNTING` to MASTER_ADMIN only (`PermissionScope.ANY`). Add the constant to `Permissions.java`, following `OfflineInstructorPermissionContributor`. Do **not** touch that contributor: it also grants `FINANCE_READ`. |

Test helpers `src/test/java/.../accounting/AccTestData.java` (JDBC): `group(...)`, `type(...)`, `payee(...)` (also inserts the
`offline_instructors` row for group 1, same id), `account(...)`, `charge(...)`, `payment(...)`, `spend(...)`, `income(...)`,
`voidEntry(...)`. L and R add methods at the end only.

Tests: `AccMoneyTest`, `AccRulesTest` (unit; every refusal key it can raise), and the balance helpers inside `AccSchemaIT` or a small
`AccBalancesIT`.

## Done when

- F1 to F3 are committed with progress notes; it compiles; the F tests pass (if tests are allowed, README section 5).
- At the very end (tests allowed only): one `-Full` run; report its totals.
