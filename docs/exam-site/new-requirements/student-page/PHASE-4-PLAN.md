# Student page, Phase 4: plan

Status: **planning only.** No code yet. Questions in section 9 need answers before step S4.1 starts.
Branch: `ui/nr01-ui` (worktree `F:\cc-wt\nr01-ui`). Commit only, no push.
Builds on: S0 to S3 (see `plan.md`, `progress/S0-review.md` to `progress/S3-review.md`).

## 1. What the user asked for

| # | Request | Where it lands |
|---|---|---|
| A1 | My exams: filter or tab for MCQ and Written | `my-exams-page.tsx` |
| A2 | Remove MCQ and Written links from the desktop top bar (doors stay reachable from Home) | `shell/student-shell.tsx` |
| A3 | Results: same MCQ and Written filter | `result/results-hub.tsx` |
| A4 | Category and subcategory cards: show exam count and package count | `catalog/door-page.tsx`, `catalog-helpers.ts` |
| A5 | Package page: smaller photo; purchase button is the main focus | `catalog/module-page.tsx` |
| A6 | Exam page: details first; no large photo | `catalog/exam-page.tsx` |
| A7 | Wish List control: compact, not a full row | `catalog/favourite-button.tsx`, `exam-row.tsx`, `module-page.tsx`, `exam-page.tsx` |
| A8 | Notices: tabs for Results, Exams, Announcements; restyled cards | `app/(site)/notices/` |
| A9 | Payment history: previous payments, backend features, link from menu and from bought items in My exams | `app/(site)/account/payments/`, `shell/student-shell.tsx`, `my-exams-page.tsx` |
| A10 | Plan verified, API changes listed, questions asked | this file |

## 2. Current state (checked in code, 2026-10-29)

**Exam image.** The API has no exam image. The exam page shows the cover of its first package (`exam-page.tsx` line ~145, `coverPackage = packageReads[0]?.data`, banner at `h-28 md:h-40`). So the big picture A6 talks about is a package cover, not an exam cover.

**Package page.** `PackageCover` is full width at the top, then title, then the buy section. On desktop the buy block starts below the cover, so the cover pushes it down. A sticky bottom bar already exists for phones (`barAction`, line ~104).

**Exam page.** Same pattern: cover banner, title, a four-column stats list (`dl` at line ~253), then the action block. The Wish List mark sits in the header (line ~279).

**Wish List control.** `StudentToggle` renders a `<button>` with a star and a `sr-only` label, but inside a `flex-col` span that also holds an error text. In a row it becomes its own column, which is why it looks like a full block. The star itself is already icon-only.

**Category cards.** `CategoryCard` (door-page.tsx line ~24) shows one line: categories count if it has children, otherwise packages count (`countModules`). Exams are not counted. The tree already carries exams: `PublicCategory.exams` holds the exams whose home is the category (`LiveExamPublicService` line ~490). So the count needs no API change. Direct exams = `node.exams.length`; branch exams = the sum over the subtree. The category list uses the same published filter, so the counts match the list.

**Top bar.** `student-shell.tsx` shows MCQ and Written links on desktop; the mobile `TABS` (Home, My exams, Results, Notices, Account) do not include them.

**My exams and Results.** My exams already has an Exams / Packages `Tabs` (`my-exams-page.tsx` line ~102). Results has Results / Marking / Other `Tabs` (`results-hub.tsx` line ~39). Both rows already carry `examType` (MCQ or WRITTEN). No API change is needed for the type filter.

**Notices.** `GET /api/v1/me/notices?page&size` takes no kind filter (`StudentInboxController` line ~34). The page lists 20 per page. A tab built on the loaded page would hide notices on later pages, so tabs need an API change (N1). Each notice already carries `exam.examType` and `updated`.

**Payments.** `/account/payments` already exists and is linked only from the account page. It shows a table of `MyOrder` rows (`lib/api/exam/account.ts`) and a print view at `account/payments/receipt`. The print view is built from the list row and uses `window.print()`; there is no receipt endpoint.

**Backend order fields** (`LiveExamOrderHistoryService.OwnOrderRow`): `orderId, tranId, status, amount, currency, scope, moduleId, moduleTitle*, examId, examTitle*, paymentChannel, createdAt, listPrice, discountPercent`. The API already sends `listPrice` and `discountPercent`; the frontend type `MyOrder` does not declare them.

**Order scope.** The backend writes `BUNDLE` for bundle orders (`LiveExamOrderBundle.SCOPE`). The frontend type says `"MODULE" | "EXAM"`, and the payments page labels anything not MODULE as an exam. A bundle row would therefore read as a single exam. This is a frontend bug to fix in S4.7.

**Grants carry the order.** `GET /api/v1/me/live-exam-access` returns `orderId`, `grantedAt`, `endsAt`, `expired` on each module and exam grant. My exams can link a bought item to its payment without a new endpoint, but the payment list cannot yet be filtered to one order (see P3).

**Cancel.** Students can cancel a pending order: `POST /api/v1/live-exam-orders/{id}/cancel` (`LiveExamOrderController` line ~99). The payment page can offer it without an API change.

## 3. Research applied

Two searches were run. The results were mostly LMS plugin docs and templates, not the big platforms, and the payment results were general e-commerce. Treat these as patterns, not as a description of Udemy or Coursera.

**Course details (A5, A6, A7).**
- Two columns on desktop: the content on the left, a narrow purchase card on the right that stays in view while scrolling. Sources: [Masteriyo single course layout](https://docs.masteriyo.com/course-creation/single-course-layout), [Tutor LMS sticky sidebar](https://docs.themeum.com/tutor-lms/tutorials/sticky-sidebar/).
- The purchase card holds the price, the main button, what is included, and the remaining count. Source: [Masteriyo single course layout](https://docs.masteriyo.com/course-creation/single-course-layout).
- On phones the purchase action sits in a bottom bar. Source: [StyleMix LMS course settings](https://docs.stylemixthemes.com/globalstudy/masterstudy-lms-settings/course).
- Pricing shows the old price struck through next to the new one, with one clear button. Source: [Lovable course storefront template](https://lovable.dev/hi/templates/websites/ecommerce/reel-creator-course-storefront-template).
- Curriculum as a drawer on small screens. Source: [Blake UI course detail](https://blake-ui.vercel.app/docs/course-detail-page).

**Payment history (A9).**
- Filters for status, payment method and date, with less common filters behind "more". Keep the current filters in the address so a view can be shared. Source: [Lightspeed orders filter](https://resto-support.lightspeedhq.com/hc/en-us/articles/360005902274-Filtering-the-Orders-List).
- Status vocabulary: paid, unpaid, refunded, partly paid. For us: Paid, Awaiting payment, Failed, Cancelled (refund is not in the model yet). Source: [Zoho orders](https://help.zoho.com/portal/en/kb/commerce/user-guide/orders/articles/orders).
- Say clearly what is hidden by default. Sources: [Lightspeed](https://resto-support.lightspeedhq.com/hc/en-us/articles/360005902274-Filtering-the-Orders-List), [Intercom orders tab](https://intercom.help/dfy-crm/en/articles/12395046-how-to-use-the-orders-tab-in-your-payments-section).
- Compact rows that open for detail. Source: [shadcn order history](https://www.shadcnblocks.com/block/order-history2).
- Payment confirmations show a clear status and links to related help. Source: [Baymard order pages](https://baymard.com/ecommerce-design-examples/104-orders-overview) (general e-commerce).

## 4. API changes (proposed)

These need the user's go before S4 starts. Each has a fallback if the user says no.

| ID | Change | Why | Without it |
|---|---|---|---|
| N1 | `GET /api/v1/me/notices` takes `group=RESULTS\|EXAMS\|ANNOUNCEMENTS` (kinds map on the server, see section 5.6). Optional: `unreadByGroup` in the response or in the unread count. | Tabs must be correct across pages | Tabs filter only the loaded page. Wrong counts. Not acceptable. |
| P1 | `GET /api/v1/me/live-exam-orders` takes `status`, `scope` (MODULE, EXAM, BUNDLE), `from`, `to` (dates). | History filters must work across all pages | Filters work on one page of 20 only. |
| P2 | Add `paidAt` (nullable, ISO UTC) to `OwnOrderRow`. | "Paid on" is different from "created on"; `updated_at` is not reliable for it | Show "created on" only, and label it that way. |
| P3 | Student order filter: `orderId` query param on the list, or `GET /api/v1/me/live-exam-orders/{id}`. | A "View payment" link from My exams must open one order | Link opens the full list with no highlight, or the link is dropped. |
| P4 | Receipt number and PDF receipt endpoint. | Industry-standard receipts carry a number and a file | Keep the browser print view (no change). **Out of scope unless the user asks.** |
| E1 | Exam cover: column, upload and read (mirrors the package cover, V41). | Only if the user wants an exam image | Not needed for the details-first layout. |

No change needed for: listPrice and discountPercent (already sent), category counts (tree), MCQ/Written filters (examType on rows), notice exam type, grant order links.

## 5. Sub-steps

Each step: files, behaviour, tests, and what to check at the end. Working rules are as before: lint per slice, targeted unit tests, one build for the phase, headless overflow text report only, no screenshots.

### S4.1 Top bar and category counts (A2, A4)

- `student-shell.tsx`: remove the MCQ and Written links from the desktop bar only. Mobile `TABS` unchanged. Home still has the doors.
- `catalog-helpers.ts`: add `countExams(node)` (direct) and `countBranchExams(node)` (recursive), and use `countModules` as today.
- `door-page.tsx` `CategoryCard`: show both counts, e.g. "12 exams · 3 packages". A category with no exams and no packages keeps "Coming soon". Zero counts are written out, not hidden.
- Keys: `catalog.countExams`, `catalog.countPackages` in `en` and `bn` (`bn` marked draft).
- Tests: `tests/student-nav/top-bar.test.tsx` (MCQ and Written absent on desktop, present on mobile tabs), a new `category-counts.test.tsx`.

### S4.2 Type filter on My exams and Results (A1, A3)

- A segmented control "All · MCQ · Written" under the existing tabs (not a replacement).
- State in the URL: `?type=MCQ|WRITTEN` via `useListParams`, so the view can be shared.
- Badges on the existing tabs count only the chosen type.
- Empty state text names the type ("No written exams in this tab").
- Results: the same control; filters the three existing tabs.
- Tests: pure filter function unit test; component test for both pages.

### S4.3 Package page (A5, A7)

- Desktop: two columns. Left: cover (small), title, summary, tabs (Exams, Routine, About). Right: a sticky purchase card with price, discount, main button, what is included, access length.
- Cover: from full width to a thumbnail (about 120 px wide, 16:9) beside the title on desktop; on phone a thin 96 px band at the top. Use `PackageCover` with a size prop. Do not change the cover endpoint.
- Phone: the buy block comes right after the title, above the exam list; keep the sticky bottom bar.
- Exam rows: the Wish List mark becomes a 44 px icon button at the top-right corner of the card (absolute position), so the card keeps its text width. Error text from the toggle moves to a toast.
- Tests: `module-page` layout test (buy block before the exam list in DOM order on phone); overflow check at 320, 375, 768, 1280 px.
- Known issue to check: the "বিবরণ" button clipping at 320 px (from the earlier overflow check).

### S4.4 Exam page (A6, A7)

- No big banner. Header first: type, title, status pill; then the primary action; then the stats list; then description and packages.
- The package cover (the one the exam page shows now) becomes a small thumbnail next to the title, or is removed. Decision in question Q2.
- Desktop: right sticky card with the status and primary action (same pattern as S4.3).
- Wish List: the same compact corner icon (44 px).
- Tests: DOM order (title before the cover, action before the description on phone); favourite button is one icon with an accessible name.

### S4.5 Notice cards and tabs (A8)

- Tabs: Results | Exams | Announcements. Each tab carries its unread count when N1 provides it. Tab state in the URL (`?tab=`).
- Mapping (proposed, Q4 to confirm):
  - Results: `MCQ_SCORE`, `WRITTEN_RESULT`
  - Exams: `EXAM_OPEN`, `EXAM_PUBLISHED`, `EXAM_REMINDER`, `EXAM_STARTING_SOON`, `RETAKE_AVAILABLE`, `EXAM_UPDATED`
  - Announcements: `ANNOUNCEMENT`
  - `PURCHASE_GRANTED`: no tab yet (Q4 offers a fourth "Purchases" tab, or Exams).
  - Unknown kinds: Exams tab, with the generic label (the current fallback).
- Card restyle (all tabs): a kind pill with a left accent bar in the tab's colour; title; meta line (exam title, date and time); for results a score chip "34/50" and a pass/fail pill; unread dot; one action button ("View result", "Open exam"); announcements keep read-in-place and the expand control.
- Read state: marking read on open stays as now.
- Tests: `noticeTabOf(kind)` unit test covering every kind in `NOTICE_KINDS` and one unknown kind; component test for tab switch and URL state.

### S4.6 Payment history (A9)

Route: keep `/account/payments` (links, print view and bookmarks stay valid). Page title becomes "Payment history" (bn draft).

- Menu: add "Payment history" to the student `UserMenu` (after Account). The mobile Account tab leads to the account page, which keeps its card.
- My exams: each bought module or exam row (grant with `orderId`) gets "View payment" → `/account/payments?order=<id>` (needs P3; without it, the link is dropped and the row shows the order date only).
- Layout (industry pattern, section 3):
  - Summary strip: total paid (this page or all, per P1), number of orders.
  - Filters: status, scope (Package, Exam, Bundle), date range. Visible filters at the top; "more" hides the rarer ones. Filters in the URL.
  - Desktop: table (date, item, status, amount, method, order ID with copy). Phone: cards with the same fields.
  - Amount: amount paid, and when different, the list price struck through and the discount percent.
  - Expandable row: payment method, transaction ID (copy), order ID, access until (`endsAt` from the grant, when known), what was bought (bundle items, from `examIds`).
  - Actions: "Cancel" for Awaiting payment (existing endpoint); "Print receipt" for Paid (existing print view).
  - Empty state: "No payments yet" with a link to Home.
- Bundle fix: scope `BUNDLE` gets its own label (and `MyOrder.scope` type updated).
- Status labels: Paid, Awaiting payment, Failed, Cancelled (existing keys, check wording in `bn`).
- Tests: filter-to-query mapping unit test; list render for each status and scope (fixtures); print view unchanged; `BUNDLE` row label test.

### S4.7 Data and types

- `MyOrder`: add `listPrice`, `discountPercent`, and `paidAt` (when P2 lands). Scope type: `"MODULE" | "EXAM" | "BUNDLE"`.
- Existing receipt page uses `useMyOrders(params.page)`; keep working after P1 adds params.

### S4.8 Checks

- Lint for each slice.
- Targeted unit tests for each slice.
- One build at the end of the phase.
- Headless overflow text report at 320, 375, 768, 1280 px for: Home, Browse (door and category), package page, exam page, My exams, Results, Notices, Payment history, Wish List. No screenshots.
- i18n: `en` and `bn` key parity (see the messages parity test pattern in `tests/ws5/messages.test.ts`).
- Progress review file `progress/S4-review.md`, markers in `markers.md` for anything waiting on an API change.

## 6. Markers (waiting on an API change or a decision)

| Marker | Waiting on | Interim behaviour |
|---|---|---|
| M-N1 | N1 notices group filter | Tabs hidden; single list as now |
| M-P1 | P1 order filters | Filters work on the loaded page, labelled "This page" |
| M-P2 | P2 paidAt | Show "Created on" only |
| M-P3 | P3 order lookup | "View payment" link not shown |
| M-E1 | E1 exam cover | Not implemented unless Q2 says yes |
| M-P4 | P4 receipt number | Print view as now |

## 7. Omission check (what was verified)

- Every item in section 1 is mapped to a step (A1 to A9 → S4.1 to S4.6).
- Mobile layout: the tab bar keeps Results and My exams; removing the desktop links does not remove mobile access to MCQ or Written, because those are filters now.
- Deep links: `/mcq` and `/written` (door pages, `student-shell.tsx` TOP_LINKS lines 33-34) stay as routes; only the desktop links go. They remain reachable from Home.
- Payment history has no menu entry today: `UserMenu` (student-shell.tsx ~line 117-121) lists Home, Account, Wish List, Notices, Routine. S4.6 adds it.
- Old `/favourites` redirect unchanged.
- The Wish List error text moves to a toast (no lost message).
- Filters and tabs are in the URL, so back and refresh keep state.
- Bundle orders were missing from the payment labels (found in this check); added to S4.6.
- Order `updated_at` is not used as "paid on" (found in this check); P2 added.
- Notice tabs cannot be done client-side across pages (found in this check); N1 added.
- Exam image: none in the API; the exam page banner is the package cover (found in this check).
- Refunds: not in the order status model. Not added; see Q7.
- Account and receipt: no receipt number in the API (P4 optional).
- Pending order cancel: endpoint exists; used in S4.6.

## 8. Things not covered

- Refund flow and refund status (not in the model).
- Invoices and tax lines (not in the model).
- Instalments (not in the model).
- Search inside payment history.
- Staff side (not part of the student page).

## 9. Questions for the user

1. **Category counts (A4).** Count only exams whose home is this category, or the whole branch (the same choice the category exam list makes with its branch switch)? *Recommend: show both on the card: "12 exams (34 in branch)" is too busy; use branch count on parent cards and direct count on leaf cards.* Your call.
2. **Exam picture (A6).** Remove the package cover from the exam page, or keep a small thumbnail? An exam-specific picture needs API change E1. *Recommend: small thumbnail of the package cover for now, no E1.*
3. **My exams and Results filter (A1, A3).** Segmented control under the tabs (recommended), or chips, or a single dropdown? Default: All.
4. **Notice tabs (A8).** Confirm the mapping in S4.5. Where should `PURCHASE_GRANTED` go: Exams, or a fourth "Purchases" tab? *Recommend: Exams.*
5. **Payment history (A9).** Keep `/account/payments` and restyle (recommended), or a new `/payments` route? Fields in S4.6 are right, or add or remove any?
6. **API changes (section 4).** Approve N1, P1, P2, P3? P4 and E1 only if you want them.
7. **Refunds.** Out of scope for this phase, or needed? If needed, the refund states must come from the backend first.
8. **Bangla terms.** Confirm the new Bangla labels for: Payment history, Awaiting payment, Results / Exams / Announcements tabs, "Wish List" (still draft), "Exams and packages" count labels.

## 10. Order of work

1. S4.1 (quick, no API) and S4.2 (no API) in parallel.
2. S4.3 and S4.4 (layout, no API; compact Wish List).
3. API changes N1, P1, P2, P3 if approved (backend agent, separate branch and tests).
4. S4.5 (needs N1 for real tabs; shows markers until then).
5. S4.6 and S4.7 (needs P1 to 3 for full behaviour; markers until then).
6. S4.8 checks and review file.

Nothing is pushed. Each slice is a separate commit on `ui/nr01-ui`.
