# Progress L2: payees and the offline instructors mirror

Built:
- `AccPayeeService` + `AccPayeeController` (package `accounting.ledger`): list per group (`q`, `archived`), the picker `GET /payees`
  (`q` required, `limit` 1 to 50, default 20; live payees of live groups), create, read, PATCH, archive, restore, last prices,
  `GET /users/linkable`. Nine routes.
- The offline mirror: a payee of the Offline instructors group has the same id as its `offline_instructors` row. Create, edit, archive
  and restore change both in one transaction (through `OfflineInstructorRepository`, the entity the routine picker reads).
- `OfflineInstructorService`: the write methods are gone; `list` and the view stay. The alias rules moved to the public static
  `cleanAliases(Collection, problems)` so the payee service calls the same code.
- `OfflineInstructorController`: GET only (`OFFLINE_BATCH_ROUTINE`, unchanged).
- Deleted: `OfflineInstructorPayController`, `OfflineInstructorPayService`, `OfflineInstructorPayIT` (git rm).
- `OfflineInstructorDataIT`: the three tests that used the removed routes are gone (their rules are in AccPayeeIT, the archive guard is
  dropped, see below); a new staff search test; the instructor helper makes the payee with `AccTestData.payee(group 1)`.
- `AuditLogIT`: the 11 removed routes are out of the set; the 9 payee routes are in the `// ACC-L` block. `AccAccessIT` lists them too.

Routes removed (for W to mirror; all under the old offline paths):
- `GET /api/v1/offline-instructors/{id}/payments`, `POST /api/v1/offline-instructors/{id}/payments`,
  `GET /api/v1/offline-instructors/{id}/pay-summary`
- `GET /api/v1/offline-instructor-payments`, `GET .../figures`, `GET .../cards`,
  `PATCH /api/v1/offline-instructor-payments/{recordKind}/{id}`, `POST .../{recordKind}/{id}/void`
- `POST /api/v1/offline-instructors`, `PATCH /api/v1/offline-instructors/{id}`, `POST /api/v1/offline-instructors/{id}/archive`
- Kept: `GET /api/v1/offline-instructors` (routine picker; no pay figures in it).

Tests (`acc-mvn.ps1 -Agent L -Tests ...`), all green:
- AccPayeeIT 10 run, 0 failures: figures and the detail (balance, totals, this month, last payment), last prices (newest active charge per
  type, voided ignored), the mirror (picker finds it, rename and phone reach `offline_instructors`, archive and restore), the alias rules
  (`alias_taken` both ways, case-insensitive), names per group and restore refusing a clash, archived payees off the list but in the owed
  figure, the linked user (`user_linked`, `user_not_linkable`, unlink), edit with the version and name uniqueness, `group_mode` and
  `group_archived` for a new payee, the picker needs `q`, 404s.
- AccAccessIT 1 run, 0 failures (27 routes now: 18 from L1, 9 from L2).
- AuditLogIT 3 run, 0 failures.
- OfflineInstructorDataIT 4 run, 0 failures.

Decisions where the plan was silent (the safer reading):
- The archive guard of the old instructor (a live class still names the instructor) is **dropped** for payees: plan 4.3 says nothing
  about it and says a payee with a balance may be archived. Open question for the orchestrator.
- An archived payee can still be edited (name, phone, note, aliases, link). Only the entries and the group/type/account edits have the
  archived refusals of L1.
- `aliases` null on PATCH clears them (full-form, as the other optional fields). The alias rules are the old ones (trim, drop blanks and
  repeats, 20 names, 200 characters).
- `restore` of a group-1 payee refuses `name_taken` when a live instructor has the name or an alias (plan 4.3); `create` and `PATCH` refuse
  such a clash as `alias_taken` (contracts 5). Name clashes with a live payee of the same group are `name_taken` everywhere.
- Create in a payee group whose group is archived: `group_archived`; restore of a payee into an archived group: `group_archived`.
- `linkable` for an unknown user id is `user_not_linkable` (no 404: the user is not a payee row).
- `staffPayBalance` reads the linked user's `staff_ledger` lines with the same query as StaffLedgerService and `StaffPayCalculator.totals`
  (read only; nothing in `staffledger/` changed). A user with no lines shows "0.00".
- Batches are listed for group 1 payees only (contracts: "for instructors").

Open questions for the orchestrator (not my files):
- `OfflineClassIT` (lines 279, 391, 404, 411) and `OfflineRoutineIT` (lines 252, 663, 678, 696) create and rename instructors with the
  removed `POST` and `PATCH /api/v1/offline-instructors`. They fail until they make the instructor another way (the payee route, or
  `AccTestData.payee(group 1)`). Not run by me.
- `OfflineClassService` and `OfflineClassController` have javadoc links to the deleted `OfflineInstructorPayService` and controller. They
  compile (javadoc only); W or the orchestrator may want them reworded.
- `OFFLINE_INSTRUCTOR_PAY` is still checked by `PaymentRecordController` (R's file), so the permission line stays until R is merged.
- The API omits null fields (`non_null`); W reads a missing field as null (see the L1 note).
