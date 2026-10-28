# Stage 2: Commerce and access (agent C)

**Covers** decisions A2', B5–B8, R4 (access side), R5 and R19. Read them in [../NR-01-exam-segment.md](../NR-01-exam-segment.md).

Branch `nr01/c-commerce`. Worktree `F:\cc-wt\nr01-c`. Test database `cc_test_nr01_c`. The files you own are in [README.md](README.md#ownership). **Merge first in stage 2:** everyone's tests rely on access.

**First, in C1:** replace the `primaryModuleId` reads and `hasExam` calls in your files per [contracts.md §8](contracts.md#8-several-packages-per-exam-how-each-primary-package-read-resolves) (exam orders and grants carry no package). The order and grant `getModuleId()` reads listed in `progress/f-done.md` §3a are yours to keep or null-guard.

## C1: Access with free exams and expiry

Inside `LiveExamAccess` (keep the frozen signatures):

- **Free exam** (`free = true`, published): `granted`, with `source = FREE`, for every active student. `endsAt` is null.
- **Grant sources**, in order:
  1. exam grant;
  2. package grant or roster row through an active membership, or through a membership removed after the grant start (R4);
  3. bundle;
  4. manual grant.
  - With several sources, use the one with the **latest** `endsAt`. Null (lifetime) beats any date.
- **`endsAt`** comes from `AccessWindow.end(exam, grantStart)`. `expired = endsAt != null && now >= endsAt`.
- **`canSit`** is `granted && !expired`. **`hasEver`** is true when any grant or roster row ever existed for this exam, by any route, or the student has an attempt on it. Revoked grants count for reading only if the student has an attempt.
- `hasModule` keeps its meaning.

**Tests:** new `AccessExpiryIT`:
- lifetime;
- 30 days, bought before the window: open until `endsAt + 30d`, then `ACCESS_EXPIRED` on take (D maps it; assert here through `canSit` and the access list);
- bought after the window: counted from the purchase;
- an undated exam does not expire;
- practice is anchored on `publishedAt`;
- two sources, where the longer one wins;
- a free exam with no grant can be sat;
- removed from the package after purchase: still granted (R4).

Update `ExamAccessGateIT`, `StudentAccessIT` and `LiveRosterIT` if they assert `hasExam`.

## C2: Orders with discount and free

- **Order amount** is `ExamPricing.effective(…)` at order time, for the exam or package. The order records `listPrice` and `discountPercent`.
  - An open order keeps its amount (R19): price or discount changes do not touch existing `INITIATED` orders.
- **Free exam** or no effective price: `EXAM_NOT_FOR_SALE`.
- **Module only:** an exam with no own price can be bought only through a package; that stays as today.
- **`EXAM` orders no longer need `moduleId`.** `MODULE` and `BUNDLE` behave as today.
  - A bundle charges each exam's effective price.
  - Read `LiveExamOrderBundle` and keep its rules.
- **Already holds access:** a student who already can sit (`canSit`) gets the existing `ALREADY_PURCHASED`. One whose access has **expired** may buy again; the new grant restarts the clock from its own start.
- **Payment return, IPN and recheck** (`LiveExamOrderTx`, `LiveExamPaymentRecheck`) create grants as today. An `EXAM` grant without a module is allowed.
- **Receipt mail and the Master Admin purchase mail** show the amount paid, as today. If the texts show a price, use the order amount.

**Tests:**
- update `LiveExamPurchaseIT`, `BundleOrderIT` and `ExamPurchaseIT`, if each touches live orders;
- new `DiscountOrderIT`:
  - a discount is applied;
  - a discount after its end date is not applied;
  - the amount at the boundary;
  - an open order keeps its amount after a price change;
  - a free exam is refused;
  - an expired holder can re-buy;
  - an active holder is refused.

## C3: Grants and access lists

- **`POST /live-exam-grants`**: an `EXAM` grant without `moduleId`. Validation is unchanged otherwise.
- **`GET /me/live-exam-access`** and **`GET /students/{id}/live-exam-access`**: each row has `endsAt` and `expired`.
  - Rows for free exams are not listed; free is not a grant.
  - Expired rows stay listed, marked `expired`.
- Order list, detail and history: `listPrice` and `discountPercent`.

**Tests:** update `LiveGrantAdminIT` and `StudentAccessIT`; add the expiry fields.

## Not yours

- Price fields on the exam and package write paths (A writes exam fields and B package fields, both validating with `ExamPricing`).
- How `take` reacts to `canSit` (D).
- Catalog rows (B).

## For web-impact

Write to `web-impact/c.md`:
- the checkout shows the struck-through list price;
- free exams show no buy button;
- the access list shows the expiry date;
- an expired exam offers a re-buy.
