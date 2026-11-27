# Workstream L: the ledger API (phase 2)

Groups, types, money accounts, payees and entries: contracts section 3.1. Read [README.md](README.md), [contracts.md](contracts.md),
plan sections 4.1 to 4.5, 4.9, 4.10, 6. Package `com.coachingcenter.api.accounting.ledger`. Use F's shared classes; do not change them.
Before writing tests read README section 5.1 (shared database: own rows with random names; never archive or rename a seeded row).

## L1. Groups, types, money accounts

- `AccGroupService`/`Controller`, `AccTypeService`/`Controller`, `AccAccountService`/`Controller`: the routes of contracts 3.1 for
  these three, with `archived` filters, `order` routes (the ids must be exactly the live rows; else 400), `version` checks.
- `Group` figures (`payeeCount`, `owed`, `advance`, `thisMonth`) through `AccBalances`, in one query per list, not one per group.
- Rules: mode and direction fixed; income only with Log; Offline instructors cannot be archived (`group_locked`); a type's kind
  changes only while unused (`type_kind_used`); a unit is required for Counted; names unique among live rows (`name_taken`).
- Audit: plan 6.3.
- `AuditLogIT`: add every new route in a `// ACC-L` block.
- Tests `AccGroupIT`, `AccTypeIT`, `AccAccountIT`: each route; each refusal; a System Admin gets 403 on every route (one parametrised
  test).

## L2. Payees and offline instructors

- `AccPayeeService`/`Controller`: list per group (`q` on name and phone), `GET /payees` across groups, create, read, edit,
  archive, restore, last prices, `GET /users/linkable`.
- **Offline instructors mirror** (plan 4.3): for group `AccIds.GROUP_OFFLINE_INSTRUCTORS`, create inserts the `offline_instructors` row
  with the **same id** (name, phone, note, aliases); edit, archive and restore change both in the same transaction. Reuse the alias
  rules in `OfflineInstructorService` (a name or alias may belong to one live instructor only → `alias_taken`), moving the code into a
  method both can call rather than copying it. `aliases` is accepted only for that group (else ignored and answered as `[]`).
- Remove the write routes `POST /api/v1/offline-instructors`, `PATCH /offline-instructors/{id}`, `POST /offline-instructors/{id}/archive`
  and their service code. `GET /offline-instructors` stays for the routine picker; drop any pay figures from its answer (the web's
  picker reads only names, ids and aliases; check `web/src/lib/api/exam` for the type and tell W what you removed).
- `Payee` figures: balance, totals, paid this month, last payment, payments count through `AccBalances`. `batches` for instructors:
  the batches with a class of theirs that is not cancelled (as the old `pay-summary` did; read `OfflineInstructorPayService`).
  `staffPayBalance`: the linked user's balance in `staff_ledger`, read with the same SQL `StaffLedgerService` uses (read only; do not
  change `staffledger/`).
- Linkable users: role System Admin or Instructor (`users.role`), active, name match, at most 20. Linking another role is
  `user_not_linkable`; a user already linked to another live payee is `user_linked`.
- Restore refuses `name_taken` when a live payee of the group has the name; for group 1 also when a live offline instructor has it as a
  name or alias.
- Tests `AccPayeeIT`: create in group 1 makes the `offline_instructors` row (same id, aliases) and the routine picker list shows it;
  rename and archive mirror; alias clash; archived payee hidden then shown with `archived=true`; last prices pick the newest active
  charge per type; balance figures after a few `AccTestData` entries; linkable users. Update `OfflineInstructorDataIT` for the removed
  routes.

## L3. Entries: create, several, copy, read

- `AccEntryService`/`Controller`: `POST /entries` (with `paidNow`), `POST /entries/many` (1 to 200, all or nothing; `too_many_lines`),
  `GET /groups/{id}/copy-source`, `GET /entries/{id}`.
- Every input goes through `AccRules`; then insert with `no = nextval('acc_entry_no_seq')`. `paidNow` inserts the payment after the
  charge with `paired_entry_id` = the charge; the answer lists the charge first.
- Archived payee: allowed (an old balance can be settled). Archived group, type or account: refused.
- Batch: any `offline_batches` row (batches are ACTIVE or ARCHIVED, never deleted); exam: a `live_exams` row with `deleted_at` empty.
  Unknown → 404.
- Copy source: plan 4.4 and contracts (`CopyDraft`). Dated `AccDates.today()`; period moved one month on; `alreadyThisMonth` as the
  contract defines it. Legacy charges are copied as plain amounts (no quantity) since they have no price.
- `EntryDetail`: the paired entry (either direction: the payment's charge, or the charge's payment) and the edits.
- Audit: `ACC_ENTRY_CREATED` per entry; `ACC_ENTRIES_CREATED` for each entry made by `many`.
- Tests `AccEntryIT`: each kind in the right and wrong group mode; counted maths (40 × 50.00 = 2000.00; 3 × 33.335 refused as 3
  decimals; 1.5 × 999.99 = 1499.99 half up); deduction stored negative and lowers the balance; needs-month and needs-description types;
  future date; paid now full and part, and more than the charge refused; many with one bad line saves nothing; copy source drafts.

## L4. Edit, void, and the old pay code

- `PATCH /entries/{id}`: plan 4.5. Write `acc_entry_edits` (before and after values of the changed fields only, as JSON with the
  contract's field names), set `corrected_at`, bump `version`. `voided` on a voided entry; `type_kind_mismatch`; legacy CLASS entries
  accept only date, amount, account, reference, note (`legacy_fields`). For a counted type, an edit of quantity or price recomputes the
  amount; a legacy OTHER charge of a counted type keeps "quantity, no price, typed amount" unless the edit gives a price.
  Entries in archived groups, or keeping their own archived type or account, can be edited and voided (plan 4.4).
- `POST /entries/{id}/void`: reason required; `alsoVoidPaired` voids the paired entry too (if active). A voided **legacy CLASS
  payment** sets its class back from `PAID` to `SCHEDULED` when no other active legacy CLASS payment points at that class (the same
  update `OfflineInstructorPayService` runs today).
- Delete `OfflineInstructorPayController`, `OfflineInstructorPayService`, `OfflineInstructorPayIT`, and the routes in `AuditLogIT` that
  they served (plan 6.2). If anything else in `offline/` calls them, tell the orchestrator before changing it.
- If nothing you own still checks `Permissions.OFFLINE_INSTRUCTOR_PAY`, say so in the report (the orchestrator removes it at the end).
- Tests `AccEntryEditIT`: edit each field kind, the edit history, the sign rule, legacy limits; void with and without paired;
  void of a legacy class payment unlocks its class; edit and void of a voided entry refused; stale version 409.

## Done when

L1 to L4 committed with progress notes; it compiles; your test classes pass (if allowed); the report lists the routes you removed and
any change W must make.
