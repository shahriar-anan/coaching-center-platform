# Progress F2: moving the old payments (V107_2)

Built: `api/src/main/resources/db/migration/V107_2__accounting_move.sql`, one `DO $$` block. Plan 5.4 in order:
1. refuses to run while any old payment (either table) or any `payment_edits` row of kind CLASS or OTHER is PENDING;
2. one payee per `offline_instructors` row, same id, group 1, archived ones too;
3-5. every APPROVED or VOIDED hand payment and class payment, in one loop ordered by paid_on, recorded_at, old id; for each, a charge
   (no unit price; quantity = class count, or 1 for a class payment; type by kind) then its payment (account by method, paired);
   REJECTED rows are not moved; numbers come from the sequence in that order, so the charge gets the lower number;
6. checks that stop the migration: active group-1 payments equal the old approved payments; no moved payee has a non-zero balance;
   the moved counts equal the old APPROVED + VOIDED counts (one for each table).

Tests: `api/src/test/java/com/coachingcenter/api/accounting/AccMoveIT.java`. Each test builds a scratch schema, migrates it to 107
(the old tables), adds old rows, then migrates it to latest.
- `everyApprovedAndVoidedPaymentMovesWithItsChargeAndTheChecksHold`: payees, a lecture with a class count, an exam without one,
  an archived instructor, a voided and a rejected hand payment, class payments (one whose payee differs from its class's
  instructor, one for an other class, one voided); the paired charges, types, accounts, descriptions, numbers, recorded-by and
  -at, totals (3350.00) and zero balances.
- `theMoveRefusesToRunWhileAnOldPaymentIsStillPending`: each of the three pending cases makes the migration fail
  (FlywayException), then with nothing pending the move runs and moves nothing.

Tests (acc-mvn.ps1 -Agent F -ResetDb -Tests "AccMoveIT,AccSchemaIT", then -Tests "AccMoveIT"): AccMoveIT 2 run, 0 failures;
AccSchemaIT 20 run, 0 failures (reset run).

Decisions taken where the plan was silent:
- A VOIDED old row with no void reason gets "Voided before the move" (the check needs a reason; the old service always wrote one).
- Both the charge and the payment of a class payment carry `legacy_class_id`; only the payment carries `legacy_id` (plan 5.4 step 3).
- The charge's `corrected_at` stays empty; the payment's is copied (plan 5.4 step 3 lists the payment only).

Notes for the orchestrator:
- The shared test database now has V107_2 applied (it was reset once, so it ran on an empty accounting schema). V107_2 is a one-time
  move: a test database that already has V107_1 applied and holds accounting rows will refuse V107_2 at start-up if those rows break
  step 6. `AccSchemaIT` inserts payments in the seeded Offline instructors group, so a later reset is needed before any run that
  starts from a database that had both. Run `-ResetDb` once on `cc_test_acc_f` before the full suite.
