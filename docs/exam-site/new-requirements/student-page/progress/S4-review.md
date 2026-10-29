# S4 review: Phase 4 (filters, detail layout, notices, payment history, staff cover)

Written 29 October 2026 on `ui/nr01-ui`, after the implementation run. Plan: `PHASE-4-PLAN.md`. **Status: partly done.** Sections marked NOT DONE are open and are not ready for QA.

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
| S4.6 | `de21159` | Payment history page and the payment links |
| S4.7 | `015156e` | Exam cover uploader; staff order columns |
| S4.4 (part) | `21be251` | Exam banner: own cover first |
| Fixes | `b87aec0`, `7e8cb0d` | ws2 label assertions; type errors from the build |

Nothing is pushed. `p4/api` was kept as a worktree at `F:\cc-wt\p4-api` for review.

## Status per sub-step

| Step | Status | Notes |
|---|---|---|
| S4.0 API (N1, P1 to P4, E1) | Done | V52 (paid time, receipt number, backfill), V53 (exam cover). `current-api.md` and `decisions.md` updated |
| S4.1 Top bar and category counts | Done | MCQ and Written removed from the desktop bar only |
| S4.2 Type filter | Done | My exams (exam tab only), Results. `?type=` in the URL |
| S4.3 Shared detail layout | **NOT DONE** | Desktop sticky purchase card and phone order not built |
| S4.4 Package and exam pages | **PARTLY DONE** | Exam banner fallback (own cover, package cover, placeholder) done. Not done: layout reorder, compact Wish List control (A7), "বিবরণ" clipping fix at 320 px |
| S4.5 Notices | Done | Three tabs with unread counts, `?tab=`, mark-all per tab, restyled cards |
| S4.6 Payment history | **PARTLY DONE** | Done: summary strip, URL filters, detail panel, actions per status, bundle label, links from the menu, My exams (header and each bought row) and the purchase notice. Not done: account card rename, purchase-result links, pending-payment lines on the package and exam buy cards |
| S4.7 Staff side | Done | Exam cover uploader in the exam editor; order columns for paid time and receipt number |
| S4.8 Checks | **PARTLY DONE** | See "Checks" below. Overflow report not done; seed rows not checked |

## Checks

Reported by the implementation run. The coordinator did not rerun these.

- **Lint:** `eslint` on each slice's files exits 0. The project-wide lint was not run.
- **API tests:** runner value `P4` (database `cc_test_nr01_p4`), 14 test classes. The first run had one error, a fixture bug in `PaymentHistoryIT`; after the fix that class passes 7 of 7. The full suite (`-Full`) was not run.
- **Web tests:** full `vitest run` on the last full pass: 130 files, 787 tests, 129 files passing. Three `tests/ws2/account.test.tsx` assertions were updated for the new labels and pass. The full suite was not rerun after the last two commits; the affected folders pass, including the key parity test (`tests/ws5/messages.test.ts`).
- **Build:** `npm run build` exits 0 after the type fixes.
- **Overflow report at 320, 375, 768, 1280 px:** NOT DONE. No report exists.
- **Seed rows:** not checked or added. The seed was changed so that paid orders get a paid time and a receipt number.

## Decisions taken during the run

- The runner has a new `P4` value, so this work has its own database.
- Order date filters compare the paid time, or the created time for orders not yet paid.
- The bundle `examId` match is covered by SQL only. No integration test builds a bundle with several exams.
- The receipt PDF is English only, as the plan says. Names keep only Latin letters.
- The receipt email has an overload with the receipt number line; the old 10-argument call still works.
- Mark-all on notices sets `?tab=` first, so the tab does not move when counts change.

## Bangla (draft, to confirm after the client's review)

New labels such as receipt number, "view payment", the notice tab names, "mark all as read in this tab", and the access lines are drafts.

## QA checklist

Run on the dev server with the seeded review accounts.

1. **Notices:** each tab shows only its kinds; the unread counts match; mark-all on one tab leaves the others unread; `?tab=` survives a reload; card restyle at phone width.
2. **Payment history, one order of each kind:** paid, pending, failed, cancelled, a bundle, a discounted order. Check the list, the detail panel and the actions for each (Download PDF, Print, Continue payment, Cancel, Buy again).
3. **Filters:** status, type, date range, "Clear filters", and the summary strip.
4. **Payment links:** the student menu; the My exams header; each bought row in My exams; the purchase notice. The package and exam buy cards and the purchase-result page have **no** payment link yet (see open items).
5. **Exam banner:** an exam with its own cover, one with only a package cover, one with neither.
6. **My exams and Results:** the MCQ and Written filter in each tab, with the badges; the Packages tab has no type filter.
7. **Top bar:** MCQ and Written gone on desktop; the phone tab bar unchanged.
8. **Category cards:** counts on a three-level tree; "Coming soon" for an empty one.
9. **Staff:** upload and remove an exam cover; order list shows paid time and receipt number.
10. **Wish List:** the mark still works on rows and the Wish List page. The compact-control change is **not** done, so the mark still takes its current space.

## Open items (not done)

- S4.3 shared detail layout (package and exam pages, desktop sticky card, phone order).
- S4.4 Wish List compact control, and the "বিবরণ" clipping at 320 px (check it).
- S4.6 account card rename, purchase-result links, pending-payment lines on buy cards.
- S4.8 overflow report at 320, 375, 768, 1280 px; full API suite run; full web test run after the last commits; seed rows for pending, failed and cancelled orders.
- Stray folder `F:\Users\HP\AppData...` to be deleted by you.
