# Progress L1: groups, types, money accounts

Built (package `com.coachingcenter.api.accounting.ledger`):
- `LedgerSupport` (package-private): the ACCOUNTING check (403 for everyone else, System Admins included), the signed-in user, text and
  choice fields, the version check (missing: 400 on "version"; stale: 409 STALE_VERSION), the exact-order check.
- `AccGroupService` + `AccGroupController`: list (archived=true shows archived too), create, PATCH, archive, restore, order. The list
  figures (payees, owed, advance, this month) come from one query for all groups.
- `AccTypeService` + `AccTypeController`: list per group, create, PATCH, archive, restore, order.
- `AccAccountService` + `AccAccountController`: list, create, PATCH, archive, restore, order.
- Routes: 18 (contract 3.1, the L1 rows). `AuditLogIT` has the `// ACC-L` block with them.

Tests (`acc-mvn.ps1 -Agent L -Tests ...`), all green:
- AccGroupIT 8 run, 0 failures (modes and the income rule, names, field errors, PATCH with version and stale, group_locked, archive and
  restore, the figures as a difference-free check on own groups, the order with a swap, 404).
- AccTypeIT 7 run, 0 failures (kinds, unit required, deduction in Log refused, names, type_kind_used, archive and restore, seeded order,
  order, 404).
- AccAccountIT 6 run, 0 failures (create, name_taken, version, archive and restore, order, 404).
- AccAccessIT 1 run, 0 failures (a System Admin gets 403 on each of the 18 routes; grows with each slice).
- AuditLogIT 3 run, 0 failures (the openapi route set includes the 18 new routes).

Decisions where the contract or plan was silent (the safer reading):
- Editing an archived group, type or account is refused (`group_archived`, `type_archived`, `account_archived`). Creating a type in an
  archived group, or restoring a type into one, is `group_archived` too.
- `version` is required on every PATCH (missing is a 400 on "version"). PATCH bodies are full-form: a required name left out keeps it,
  an optional field sent null is cleared (`nameBn`, `unitBn`, `note`), `inFinance` and the type flags left out keep their value.
- `payeeCount` counts live payees; `owed` and `advance` include archived payees (matches `AccBalances.owedAndAdvance`).
- `archive` of an archived row and `restore` of a live row change nothing and do not bump the version.
- Reorder bumps no version; it audits each row whose position changed (`*_UPDATED`), so every write is audited.
- A new group is in Finance when `inFinance` is left out.
- Accounts: `kind` can change at any time (it only maps the account for the web); names unique among live accounts.

Notes for the orchestrator and W:
- The API leaves null fields out of every answer (`spring.jackson.default-property-inclusion=non_null` in application.properties). The
  contract's `string | null` fields come back absent. W must read a missing field as null.
- Money accounts stay English-only (the F1 question). Nothing here needs a Bangla account name.
