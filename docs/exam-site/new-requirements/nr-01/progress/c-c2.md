# C2: orders with discount and free (agent C)

Branch `nr01/c-commerce`. Slice C1 committed as `2b22271`. Merged `nr01/integration` first (same commit, no-op).

## What changed

- `LiveExamOrderService` (owner C):
  - An exam order is no longer tied to its package (R5). It needs a published exam with a visible home category. The order carries no `moduleId`.
  - The amount is `ExamPricing.effective(...)` at order time, and the order records `listPrice` and `discountPercent` (the percent only while the discount is active).
  - A free exam, or an exam with no price, is `EXAM_NOT_FOR_SALE` (409). Before, an unpriced exam gave `PRICE_REQUIRED`.
  - A bundle charges each exam's effective price. Its exams must share a package, the oldest common one is recorded on the order (the bundle keeps `module_id`, which the schema requires). A free exam in a bundle gives `EXAM_NOT_FOR_SALE`. An unpriced one still gives `PRICE_REQUIRED`, as today.
  - `rejectOwned` now asks `LiveExamAccess`: an exam is refused when `canSit`, a package when `moduleState` is granted and not expired. An expired holder may buy again (A2').
  - `OrderView` gains `listPrice` and `discountPercent`.
- `LiveExamOrderTx`:
  - `writeGrant` no longer checks "any grant exists". It skips only when the student can already sit the target. Otherwise it retires the expired grant, then creates a new one from its own start. The new exam grant has no `module_id` (R5).
  - Bundle grants follow the same rule per exam.
  - `LiveExamAccess.retireExpiredGrants` revokes expired grants with reason `RETIRED`, because the database allows one unrevoked grant per student and target. The row is kept for the record.
  - `insert` takes `listPrice` and `discountPercent`. The old five-argument `insert` stays as an overload, so `ExamMailIT` and `LiveEvaluationIT` (not C's files) compile unchanged.
- `LiveExamOrderAdminService` and `LiveExamOrderHistoryService`: an exam order's `moduleId` and titles now come from the exam's primary package, joined through `live_exam_package_exams` (display only, contract §8). The field stays populated, so the current staff and student web keep their rows.

## Tests

- New `DiscountOrderIT` (8 tests, real PostgreSQL, JDBC seeding, `@Transactional` rollback, fake payment gateway, mutable clock). Covers: discount applied with list price and percent recorded; a discount that ended before now is not applied; the boundary (ends now: not applied; ends one second later: applied); an open order keeps its amount after a price change; a free exam is refused; an unpriced exam is refused; an active holder is refused; an expired holder may buy again, and the new grant restarts the clock with one unrevoked grant left.
- `LiveExamPurchaseIT`: one assertion changed on purpose. The unpriced exam order now expects `EXAM_NOT_FOR_SALE` (decided: contract §C2). No assertion was weakened.

Real result (the last gate run for the classes that changed, after the admin/history fix):

```
LiveExamPurchaseIT 15/0 failures, DiscountOrderIT 8/0, BundleOrderIT 8/0,
StudentAccessIT 2/0, LiveRosterIT 6/0, LiveGrantAdminIT 13/0
```

The earlier C2 gate run had 2 failures in `LiveExamPurchaseIT` (`moduleTitleEn` missing on exam-order rows). They were fixed by the primary-package join described above, not by changing the assertions.

## Deviations and notes

- Bundles: a bundle exam with no price stays `PRICE_REQUIRED` (today's rule), and a free one is `EXAM_NOT_FOR_SALE`. Decided by me; the contract only names exam orders.
- `rejectOwned` for a package refuses a student whose package still has a paid exam they can sit. A package with no paid exam is refused while the roster row or grant exists, as before.
- The admin and history rows for exam orders now show the exam's primary package, not `NULL`. This is display only (contract §8).
- `AnnouncementService.TO_MODULE` (not C's file, not in any table) still matches grants by `g.module_id`. Exam grants now carry no package, so exam buyers no longer receive module announcements. Flagged to the orchestrator.
- The access-list endpoints (`/me/live-exam-access`, `/students/{id}/live-exam-access`) still join grants to modules and so drop exam grants that carry no package. That is C3, next slice.
