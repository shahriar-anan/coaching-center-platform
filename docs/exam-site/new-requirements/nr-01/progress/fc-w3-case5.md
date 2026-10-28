# NR-01 final check, W3, case 5: package and bundle discounts

**Outcome:** new test class, 4 tests, all green. No production change needed.

**Test:** `api/src/test/java/com/coachingcenter/api/exams/PackageDiscountOrderIT.java`
- `aPackageDiscountAppliesOnceToTheWholePackageAndTheGrantCoversEveryMemberExam`: package at 1000 with 25% off and no end gives a MODULE amount of 750.00, list price 1000.00, discount 25. After the payment notice the grant covers both member exams.
- `aPackageDiscountThatEndedYesterdayIsNotApplied`: the amount is 1000.00 and no percent is shown.
- `aBundleIsTheSumOfItsExamsEachWithItsOwnDiscount`: exam at 200 with 50% off plus exam at 300 with none gives 400.00 (list 500.00); the percent is null because the two discounts differ; the grant covers both exams.
- `aPackageDiscountChangedWhileTheOrderIsOpenLeavesTheOpenOrderAtItsAmount`: a changed package price or discount does not change the open order (same order id, amount 750.00).

**Already covered (for reference):** `DiscountOrderIT` covers exam-level discounts only (20% off, an ended discount, a discount ending now, and an open exam order keeping its amount). `BundleOrderIT` covers bundle grants but not bundle amounts. Package-level and bundle-level discount amounts were not covered before this class.
