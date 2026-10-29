# S4 review: Phase 4 (filters, detail layout, notices, payment history, staff cover)

Written 29 October 2026 on `ui/nr01-ui`, after the final run. Plan: `PHASE-4-PLAN.md`. **Status: all sub-steps done; ready for QA.** Open points are listed at the end.

## Commits

| Area | Commit | Subject |
|---|---|---|
| Plan | `05c9eda`, `98d853b` | Phase 4 plan, then the plan with the answers |
| API N1 | `5e12f15` | Notice tabs: RESULTS, EXAMS, ANNOUNCEMENTS |
| API P1 to P4 | `5185d9f` | Order filters, paid time, receipt number and PDF |
| API E1 | `cb77c25` | Exam cover |
| API S4.0 | `1ee56cf` | API notes and the P4 runner value |
| API merge | `52c0295` | `p4/api` merged into `ui/nr01-ui` (`--no-ff`) |
| S4.1 | `937b616` | Top bar without MCQ and Written; category counts |
| S4.2 | `b04a76b` | MCQ and Written filter on My exams and Results |
| S4.5 | `d481ab3` | Notice tabs and restyled cards |
| S4.6 (first) | `de21159` | Payment history page and the payment links |
| S4.7 | `015156e` | Exam cover uploader; staff order columns |
| S4.4 (part) | `21be251` | Exam banner: own cover first |
| Fixes | `b87aec0`, `7e8cb0d` | ws2 label assertions; type errors from the build |
| Progress (first) | `8bfcacf` | Interim note (superseded by this file) |
| S4.3 | `0cb95ad` | Shared detail layout for the package and exam pages |
| S4.4 | `d17b0d0` | Package and exam pages on the shared layout; compact Wish List |
| S4.6 (final) | `5742bb2` | Payment links on the purchase result and the payment page |
| S4.8 seed | `994c3f8` | Pending, failed and cancelled orders for the review student |
| S4.8 tests | `9fc396d` | Exam cover fallback chain |

Nothing is pushed. `demo/client-review` and `main` are untouched. The API test worktree is `F:\cc-wt\p4-api`.

## Status per sub-step

| Step | Status | Notes |
|---|---|---|
| S4.0 API (N1, P1 to P4, E1) | Done | V52 (paid time, receipt number, backfill), V53 (exam cover) |
| S4.1 Top bar and category counts | Done | MCQ and Written removed from the desktop bar only |
| S4.2 Type filter | Done | My exams (exam tab only) and Results; `?type=` in the URL |
| S4.3 Shared detail layout | Done | Phone: header, card, cover band (160 px), details. Desktop (lg and up): sticky right column under the top bar |
| S4.4 Package and exam pages | Done | Both on the shared layout; compact 44 px Wish List mark, error as toast |
| S4.5 Notices | Done | Three tabs with unread counts, `?tab=`, mark-all per tab, restyled cards |
| S4.6 Payment history | Done | Page, filters, detail panel, actions per status, links from menu, My exams, buy cards, purchase result, notice |
| S4.7 Staff side | Done | Exam cover uploader in the exam editor; paid-time and receipt columns on the order list |
| S4.8 Checks | Done (see below) | Lint, targeted and full tests, build, overflow report, seed, full API suite |

## Checks

Reported by the implementation run. The coordinator checked the commits, the clean tree and the overflow report file; the test and build results are the run's own reports.

- **Lint:** ESLint on the 22 web files changed in this phase exits 0.
- **Web tests:** full `vitest run`: 134 files, 807 tests, all passing (baseline at the start of Phase 4: 130 files, 787 tests). New tests under `web/tests/ws-p4/`.
- **Web build:** `npm run build` exits 0 ("Compiled successfully"). The build ran with `NEXT_PUBLIC_API_BASE_URL=http://localhost:8093` set on the command line; no env file was edited.
- **API suite:** runner `-Full` with value P4: 137 classes, 629 tests, 0 failures, 0 errors, 0 skipped. `DemoContentSeedIT`: 16 tests, 0 failures.
- **Overflow report:** `scratchpad\overflow-p4-report.txt`, 14 pages at 320, 375, 768 and 1280 px.
  - 768 and 1280 px: no overflow on any page.
  - 320 and 375 px: the only flagged element on every page is the brand name in the top bar (`student-shell.tsx`). "পোর্টাল" is cut at the right edge (45 px wide, right edge at 97 px). This was already there before Phase 4, and `student-shell.tsx` is not changed in this phase. Not fixed.
  - The package page "বিবরণ" clipping at 320 px no longer appears in the report.
- **Seed:** insert-only orders for the review student (student 4), dev and staging only, behind the seed sentinel:
  - `DEMO-EXM-E60-D`: pending, 300.00
  - `DEMO-PKG-B8-D`: pending, 900.00
  - `DEMO-EXM-E63-D`: failed, 300.00
  - `DEMO-PKG-B7-D`: cancelled, 750.00

## Decisions and deviations

- **Runner:** the P4 value was added to the ValidateSet so this work has its own database (`cc_test_nr01_p4`).
- **Order date filters:** `from` and `to` compare the paid time, or the created time for orders not yet paid.
- **Bundle `examId` match:** covered by SQL only; no integration test builds a bundle with several exams.
- **Receipt PDF:** English only, as the plan says; names keep only Latin letters.
- **Receipt email:** an overload adds the receipt number; the old 10-argument call still works.
- **Notice mark-all:** sets `?tab=` first, so the tab does not move when counts change.
- **Package page Wish List:** the package page had no mark before; one was added to its header, since the plan lists `module-page.tsx` for the compact control.
- **Exam price:** moved from the stats list into the buy card.
- **Package access length:** not shown on an unowned package, because the API has none for an unowned package. An owned exam shows its access-until line.
- **Purchase result links:** "View payment" goes to the full list when the browser does not know the order id. "Download receipt" appears only when it does. The order id is kept in `sessionStorage` by the purchase sheet, so after a new browser session the purchase result link may be missing. Ask if this should be fixed server-side.
- **Key change:** `purchase.result.receipts` replaced by `viewPayment`.
- **Seed test counts:** `DemoContentSeedIT` counts updated for the new rows (15 orders, 3 failed, 2 cancelled, 5 initiated).

## Bangla (draft, to confirm after the client's review)

All new Bangla strings are drafts, including the receipt number, "view payment", the notice tab names, "mark all as read in this tab", the access lines and the payment-waiting lines.

## Open items

- **Brand name clipping at 320 and 375 px** in the top bar. Pre-existing and outside this phase. Decide whether to fix now.
- **Visual check:** the desktop sticky card and the phone order were checked by text report only. QA should look at both.
- **Purchase result "View payment":** depends on the browser session memo (see deviations).
- **Stray folder** `F:\Users\HP\AppData...` still to be deleted by you.

## QA checklist

Run on the dev server with the seeded review accounts (student 4 has the four new orders).

1. **Package page:** header, then the buy card (right on desktop, after the header on phone); the cover is a band on phone and a 16:9 card on desktop; the sticky card stays in view when scrolling on desktop; the Wish List mark is in the top-right corner and its error shows as a toast; the "বিবরণ" button is not clipped at 320 px.
2. **Exam page:** the exam's own cover (e.g. a cover you upload) comes first; with none, the package cover; with neither, the placeholder. Details come before the packages; the Wish List mark is compact.
3. **Owned package and exam:** progress bar and "Purchased … · View payment" line.
4. **Buy cards with an open order:** "Payment waiting" with Continue and Cancel, for the pending seed orders.
5. **Payment history:** each order kind (paid, pending, failed, cancelled, bundle, discounted). Check the list, the detail panel and the actions: Download PDF, Print, Continue payment, Cancel, Buy again.
6. **Filters:** status, type, date range, "Clear filters" and the summary strip; the URL keeps them.
7. **Payment links:** the student menu; the My exams header and each bought row; the purchase result page (after a purchase in this browser session); the purchase notice.
8. **Notices:** each tab shows its kinds only; counts match; mark-all on one tab leaves the others; `?tab=` survives a reload.
9. **My exams and Results:** the MCQ and Written filter in each tab, with the badges; the Packages tab has no type filter.
10. **Top bar:** MCQ and Written gone on desktop; the phone tab bar unchanged.
11. **Category cards:** counts on a three-level tree; "Coming soon" for an empty category.
12. **Staff:** upload and remove an exam cover; order list shows paid time and receipt number.
13. **Wish List:** the mark works on rows, the package cards and the Wish List page.
14. **Bangla:** every new label, as drafts.
