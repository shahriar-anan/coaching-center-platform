# C1: access with free exams and expiry (agent C)

Branch `nr01/c-commerce`. Merged `nr01/integration` first (already at the same commit, no-op).

## What changed

- `LiveExamAccess` (owner C):
  - `state` picks the route that lasts longest, not the latest grant start. Lifetime beats any date. Ties keep the earlier route (exam grant, then roster or package, then bundle, then manual).
  - A free exam that is published gives `FREE` access to every student, with no end.
  - Package routes (roster row or package grant) cover an exam through any active membership, or one removed after the grant start (R4).
  - `moduleState` and `moduleEnd` answer for a whole package: it ends when its paid exams have all run out, and never ends when a paid exam is lifetime or when the package has no paid exam.
  - `hasEver` counts only live candidates or an attempt, so a revoked grant reads only through an attempt.
  - `hasModule` counts only module-scope grants. Exam grants, which may now carry a package id, no longer grant the package.
  - Helpers for C2 and C3: `holdsExamGrant`, `holdsModuleGrant`, `retireExpiredGrants`.
  - Constructor now takes `LiveExamRepository`.
- `StudentOwnAccessService.Viewer` (catalog buy state) answers from `LiveExamAccess` instead of the primary package. An expired route reads as `NONE`, so the student can buy again. A free exam reads as `FREE` with reason `ALREADY_HAS_ACCESS`. The constructor takes `LiveExamAccess` and `Clock`. `mine()` and its SQL are unchanged here (C3).
- `LiveExamPackageExamRepository` gains `findByModuleId` (removed rows included).

## Tests

- New `AccessExpiryIT` (11 tests, real PostgreSQL, `@Transactional` rollback, JDBC seeding): lifetime; 30 days bought before the window runs from the window end; a purchase after the window runs from the purchase; undated never expires; practice runs from publish; the longer route wins; a free exam is open with no grant and is not a grant for `hasEver`; a package removed after purchase still covers the earlier buyer and not the later one; a revoked grant reads only through an attempt; a package ends when its paid exams run out (free exam ignored); a package with no paid exam never ends.

Real result:

```
Gate classes: AccessExpiryIT, StudentAccessIT, LiveRosterIT, LiveGrantAdminIT, LivePublicCatalogIT, PublicAccessIT,
ModuleCoverIT, LiveCatalogIT, LiveExamPurchaseIT, BundleOrderIT, ExamPurchaseIT, ExamAccessModeIT,
LiveExamPaymentRecheckTest, AccessWindowTest, ExamPricingTest
Tests run: 101, Failures: 0, Errors: 0, Skipped: 0 (16 classes), EXIT 0
```

Baseline before C1 (same set minus `AccessExpiryIT`): 91 tests, 0 failures.

No existing assertion was changed in C1. `ExamAccessGateIT` (legacy course access) and `StudentAccessIT` still pass unmodified, so the spec's "update if they assert hasExam" did not apply.

## Deviations

- `hasModule` is now scope-specific. It was `existsByStudentIdAndModuleIdAndRevokedAtIsNull`, which also matched exam grants that carry a package id. This is intended: an exam grant must not give package access (R5). No test depended on the old behaviour.
- `hasExam` (deprecated) still ignores expiry, as F left it. Its remaining callers (`ExamMailTexts`, `LiveExamService`) are outside C.
