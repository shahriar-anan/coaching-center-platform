# C3: grants and access lists (agent C)

Branch `nr01/c-commerce`. C1 `2b22271`, C2 `d58fa04`. The C3 changes were written before the usage limit cut the earlier session; this session finished them.

## What changed

- `LiveExamGrantAdminService` (owner C): an `EXAM` grant needs no `moduleId`, and stores none (R5). A `moduleId` that is given must be a package the exam is in now, else `400 VALIDATION_FAILED`. The duplicate check now asks `LiveExamAccess`: `holdsExamGrant` or `holdsModuleGrant` gives `409 GRANT_ALREADY_ACTIVE`. An expired grant of the same target is retired (`RETIRED`) first, so the one-unrevoked-grant rule holds.
- `StudentOwnAccessService` (`GET /me/live-exam-access`): every row carries `grantedAt`, `endsAt` and `expired`. Expired rows stay listed. Exam rows come from `AccessWindow.end`, module rows from `LiveExamAccess.moduleEnd`. Free-exam grants are not listed. The SQL no longer joins exam grants through `g.module_id`; the package is a `LEFT JOIN LATERAL` on the exam's first active membership, for display only.
- `StudentLiveExamAccessService` (`GET /students/{id}/live-exam-access`): the same fields and the same rule. A row whose grant has no package is listed, with `moduleId` null.
- `LiveExamOrderAdminService` and `LiveExamOrderHistoryService`: rows carry `listPrice` and `discountPercent` (list, detail and history all return these records directly).
- `LiveExamAccess`: `expired(...)` is package-visible, so the two access services use the same rule.
- No new routes, so `AuditLogIT.API_OPERATIONS` is unchanged (no C block needed for C1 to C3).

## Tests

- `AccessExpiryIT` (13 tests): C1's 11 plus two new ones, "the access lists show when access ends and hide free exams" and "a manual grant replaces a lapsed one and is refused while the one before is live".
- `LiveGrantAdminIT` (13 tests): one assertion changed on purpose. An `EXAM` grant's `moduleId` is now absent (decided, R5), so `jsonPath("$.moduleId").value(module)` became `doesNotExist()`.
- `StudentAccessIT` (2 tests): see the deviation below. Added `expired` assertions for the rows.

Real result (surefire, this run):

```
AccessExpiryIT     Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
LiveGrantAdminIT   Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
StudentAccessIT    Tests run: 2,  Failures: 0, Errors: 0, Skipped: 0
Total: 28 tests, 0 failures, EXIT 0
```

The first run of this set had one failure: `StudentAccessIT.staffSeeEveryReasonAStudentCanSitAndNobodyElseCan:113`, `$.totalElements` expected 0, was 1 (after the package is deleted). It is fixed as described below.

## Deviations

- **Decided behaviour change, `StudentAccessIT` line ~113.** After a package is deleted, the student's bought exam still appears in the staff access list as `EXAM_PURCHASE` (one row). The old assertion expected 0 rows because the old query joined the exam grant through the package. R5 says buying exam X gives access to X only, and the exam still exists after its package is soft-deleted, so the row stays. The assertion now expects 1 row (`EXAM_PURCHASE`, the bought exam). Nothing was weakened: the check is stricter about which row remains. Raised as a question below: should deleting a package keep its bought exams? The current code says yes.
- `LiveGrantAdminIT`: the `moduleId` assertion changed (decided, R5, see above).
- `hasModule` and `hasExam` stay as C1 left them.

## Questions for the orchestrator

1. Deleting a package (not removing an exam from it) keeps the exam grants of its buyers, per R5. Confirm this is wanted. If not, the package delete would have to revoke them, and that is a decision, not a fix.
2. `AnnouncementService.TO_MODULE` (not C's file) still matches grants by `g.module_id`, so exam buyers get no module announcements (carried from C2).
