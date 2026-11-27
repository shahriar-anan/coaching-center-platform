# Progress L4: edit, void, and the old pay code

Built (package `accounting.ledger`, in `AccEntryService` and `AccEntryController`):
- `PATCH /entries/{id}` (`EntryPatch`, the form filled in: a field sent null is cleared, the required ones are checked by AccRules):
  - the version is required (missing: 400; stale: 409 STALE_VERSION); a voided entry is `voided`; `reason` is required (400).
  - the kind, group and payee never change. The group, type and account the entry already has are passed to AccRules as the `Was`
    values, so an archived group, type or account that is kept does not refuse; a new archived one does.
  - a type may change only to one whose kind keeps the sign: a charge whose old type is a deduction must stay one, and the reverse
    (`type_kind_mismatch`). The check compares the old and new type kinds, as the foundation note asked.
  - a moved-over class entry (`legacy_source = CLASS`, payment or charge) changes only date, amount, account, reference and note; any
    other field that differs is `legacy_fields`. A moved-over OTHER counted entry with no price keeps its typed amount (AccRules).
  - each changed field goes into `acc_entry_edits` (before and after, changed fields only, contract names, values as text), with the
    reason and the editor; `corrected_at` is set and the version goes up by one. An edit that changes nothing writes nothing and returns
    the entry.
- `POST /entries/{id}/void` (`{ reason, alsoVoidPaired }`): reason required; a voided entry is `voided`; `alsoVoidPaired` voids the
  other entry of the pair if it is still active. The answer is the voided entry, then its pair if voided too. Voiding a moved-over class
  payment sets its class from PAID back to SCHEDULED when no other active class payment points at that class (the old update, with the
  "no other active payment" condition of plan 4.5). Audit: `ACC_ENTRY_EDITED`, `ACC_ENTRY_VOIDED`.
- The old instructor pay code was removed in L2 (controller, service and IT, with the eleven routes). Nothing of it is left in L4.
- Routes added: 2 (`PATCH /entries/{id}`, `POST /entries/{id}/void`). Total ledger routes: 18 + 9 + 4 + 2 = 33.

Tests (`acc-mvn.ps1 -Agent L -Tests ...`), all green:
- AccEntryEditIT 9 run, 0 failures: the history and version (the reason required, stale 409, a changed field only, the same form changes
  nothing, a note makes version 2); a counted charge recomputed from a new price; the kind fixed and the deduction sign (both ways
  refused, a same-sign type allowed); a moved-over class payment (its money fields change, its description refused as legacy_fields);
  a voided entry is final (void again and edit refused, balance 0); void with the pair and alone; the class unlock only when the last
  active class payment is voided (the class in offline_classes, through the routine's batch); an edit in an archived group keeping its
  archived type, and a move to another archived type refused; 404s.
- AccEntryIT 11, AccAccessIT 1 (33 routes now, the L4 two added), AuditLogIT 3, AccAccountIT 6 and OfflineInstructorDataIT 4 (touched
  for the order-test change and the class javadoc only) also re-run green.

Notes:
- The order test of accounts no longer reorders the seeded accounts; it swaps two accounts the test makes (README 5.1).
- The instructor (payee) archive has no live-class guard now (L2 decision; see the L2 note).

Open questions for the orchestrator are in the L2 note (OfflineClassIT, OfflineRoutineIT, the javadoc links, OFFLINE_INSTRUCTOR_PAY
still checked by PaymentRecordController).
