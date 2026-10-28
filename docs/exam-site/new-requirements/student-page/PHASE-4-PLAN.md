# Student page, Phase 4: plan

Status: **planned and answered (2026-10-29). Ready to build.** No code yet.
Branch: `ui/nr01-ui` (worktree `F:\cc-wt\nr01-ui`). Commit only, no push.
Builds on: S0 to S3 (see `plan.md`, `progress/S0-review.md` to `progress/S3-review.md`).

## 1. What the user asked for

| # | Request | Where it lands |
|---|---|---|
| A1 | My exams: filter for MCQ and Written | `catalog/my-exams-page.tsx` |
| A2 | Remove MCQ and Written links from the desktop top bar (doors stay reachable from Home) | `shell/student-shell.tsx` |
| A3 | Results: same MCQ and Written filter | `result/results-hub.tsx` |
| A4 | Category and subcategory cards: exam count and package count | `catalog/door-page.tsx`, `catalog-helpers.ts` |
| A5 | Package page: smaller photo; purchase button is the main focus | `catalog/module-page.tsx` |
| A6 | Exam page: exam image as a banner, but details first | `catalog/exam-page.tsx`, API E1 |
| A7 | Wish List control: compact, not a full row | `catalog/favourite-button.tsx`, `exam-row.tsx`, `module-page.tsx`, `exam-page.tsx` |
| A8 | Notices: tabs for Results, Exams, Announcements; restyled cards | `app/(site)/notices/`, API N1 |
| A9 | Payment history: industry-standard; linked from the menu and from every page where a payment matters, so it can be checked and managed easily | `app/(site)/account/payments/`, many pages, API P1 to P4 |

## 2. Decisions (user answers, 2026-10-29)

| Q | Decision |
|---|---|
| Q1 Category counts | No answer; the recommendation stands: a parent card counts the whole branch, a leaf card counts its own exams (the two are the same on a leaf). |
| Q2 Exam picture | Exam cover approved (E1). Layout puts details first (section 5, S4.4). |
| Q3 Filter control | No answer; the recommendation stands: a segmented control "All · MCQ · Written" under the existing tabs, default All. |
| Q4 Notice tabs | Mapping in S4.5 confirmed. `PURCHASE_GRANTED` goes under **Exams**. |
| Q5 Payment history | Keep `/account/payments` and restyle it. Link it from other pages too (section 5, S4.6, "Where the payment links live"). |
| Q6 API | N1, P1, P2, P3 approved. P4 (receipt number and PDF) and E1 (exam cover) approved. |
| Q7 Refunds | No refunds. No refund states, no refund wording. |
| Q8 Bangla | Confirmed for now. May change after the client sees it, so keep every term in the message catalogs (no hard-coded Bangla). |

## 3. Current state (checked in code, 2026-10-29)

**Exam image.** The API has no exam image. The exam page shows the cover of its first package as a banner (`exam-page.tsx` ~line 145 and ~line 219, `h-28 md:h-40`).

**Package cover.** One optional cover per package (V41: `cover_stored_name`, `cover_content_type`, `cover_byte_size`, `cover_updated_at`). Upload `POST /api/v1/live-exam-modules/{id}/cover` (Master Admin, `MODULE_COVER_MANAGE`), staff read `GET /api/v1/live-exam-modules/{id}/cover`, public read for published packages. Up to 2 MB (`ModuleCoverService.MAX_COVER_BYTES`). The cover is saved at once, not staged as a revision. Staff upload control: `components/staff/catalog/cover-uploader.tsx`.

**Package page.** Full-width cover on top, then title, then the buy section. On desktop the buy block starts below the cover. A sticky bottom bar already exists for phones (`module-page.tsx` ~line 104).

**Exam page.** Banner, title, four-column stats list (`dl` ~line 253), action block. The Wish List mark sits in the header (~line 279).

**Wish List control.** `StudentToggle` is an icon button with a `sr-only` label, wrapped in a `flex-col` span that also holds its error text. In a row that wrapper becomes its own column.

**Category cards.** `CategoryCard` (door-page.tsx ~line 24) shows the subcategory count or the package count (`countModules`). The tree already holds each category's own exams (`PublicCategory.exams`, `LiveExamPublicService` ~line 490), with the same published filter as the category exam list. No API change needed.

**Top bar.** `TOP_LINKS` (student-shell.tsx lines 31 to 38) holds `/mcq` and `/written`. The mobile `TABS` do not. `UserMenu` (~lines 117 to 121) lists Home, Account, Wish List, Notices, Routine. No payment link.

**My exams and Results.** My exams has Exams / Packages tabs (~line 102); Results has Results / Marking / Other tabs (~line 39). Both rows carry `examType`.

**Notices.** `GET /api/v1/me/notices?page&size` has no kind filter; 20 per page. `POST /api/v1/me/notices/read-all` marks every notice. `GET /api/v1/me/notices/unread-count` returns one count. Each notice carries `exam.examType` and `updated`.

**Orders, student side.**
- List `GET /api/v1/me/live-exam-orders` (page and size only). Row: `orderId, tranId, status, amount, currency, scope, moduleId, moduleTitle*, examId, examTitle*, paymentChannel, createdAt, listPrice, discountPercent`. The frontend `MyOrder` type misses `listPrice` and `discountPercent` and types `scope` as `MODULE | EXAM`, but the API also sends `BUNDLE`. The payments page labels a bundle as an exam (bug).
- Status words: `PENDING, PAID, FAILED, CANCELLED` (`LiveExamOrderService.publicStatus`).
- Times: `created_at` and `updated_at` only. No paid time.
- One order `GET /api/v1/live-exam-orders/{id}` (owner check) returns `OrderView` with `examIds`, `listPrice`, `discountPercent`, no titles, no redirect.
- Cancel `POST /api/v1/live-exam-orders/{id}/cancel` (student, own pending order).
- Continue a pending payment: `POST /api/v1/live-exam-orders` with the same target finds the open order (`findOpen`) and returns a fresh gateway redirect. **No API change needed** for "Continue payment"; the page re-posts the order's target (module, exam, or the bundle's `examIds`).
- Receipt: a browser print view at `/account/payments/receipt`, built from the list row. The backend also mails a receipt (`ExamOutboundMail.PAYMENT_RECEIPT`). No receipt number. PDFBox is already a dependency (`PdfCompressor`).

**Grants.** `GET /api/v1/me/live-exam-access` returns `orderId`, `grantedAt`, `endsAt`, `expired` per module and exam grant (`orderId` null for roster and manual grants).

## 4. Research applied

Two searches. The results were LMS plugin docs, templates and general e-commerce, not the big platforms; treat them as patterns.

**Course details (A5, A6, A7).**
- Two columns on desktop: content on the left, a narrow purchase card on the right that stays in view. The card opens with the media (cover), then price, main button, what is included. Sources: [Masteriyo single course layout](https://docs.masteriyo.com/course-creation/single-course-layout), [Tutor LMS sticky sidebar](https://docs.themeum.com/tutor-lms/tutorials/sticky-sidebar/).
- Phones: the purchase action in a bottom bar. Source: [StyleMix LMS course settings](https://docs.stylemixthemes.com/globalstudy/masterstudy-lms-settings/course).
- Old price struck through beside the new one, one clear button. Source: [Lovable course storefront template](https://lovable.dev/hi/templates/websites/ecommerce/reel-creator-course-storefront-template).
- Long lists (curriculum) as a drawer or tab on small screens. Source: [Blake UI course detail](https://blake-ui.vercel.app/docs/course-detail-page).

**Payment history (A9).**
- Filters for status, method and date, rarer ones behind "more", filters kept in the address. Source: [Lightspeed orders filter](https://resto-support.lightspeedhq.com/hc/en-us/articles/360005902274-Filtering-the-Orders-List).
- Clear payment status words. Source: [Zoho orders](https://help.zoho.com/portal/en/kb/commerce/user-guide/orders/articles/orders).
- Say what is hidden by default. Source: [Intercom orders tab](https://intercom.help/dfy-crm/en/articles/12395046-how-to-use-the-orders-tab-in-your-payments-section).
- Compact rows that open for detail. Source: [shadcn order history](https://www.shadcnblocks.com/block/order-history2).
- Clear status plus links to help. Source: [Baymard order pages](https://baymard.com/ecommerce-design-examples/104-orders-overview).

## 5. API changes (approved)

All additive: old clients keep working. Work in its own worktree on branch `p4/api` from `ui/nr01-ui`, run with the `nr01-mvn.ps1` runner (own DB), merged into `ui/nr01-ui` when green. Migrations start at **V52** (latest is V51).

### N1. Notice groups

- `GET /api/v1/me/notices?group=RESULTS|EXAMS|ANNOUNCEMENTS` (absent = all, as now). Unknown value: 400 `VALIDATION_FAILED`.
- Server mapping (one place, `NoticeGroup` enum):
  - RESULTS: `MCQ_SCORE`, `WRITTEN_RESULT`
  - EXAMS: `EXAM_OPEN`, `EXAM_PUBLISHED`, `EXAM_REMINDER`, `EXAM_STARTING_SOON`, `RETAKE_AVAILABLE`, `EXAM_UPDATED`, `PURCHASE_GRANTED`, and any kind added later unless mapped elsewhere
  - ANNOUNCEMENTS: `ANNOUNCEMENT`
- `GET /api/v1/me/notices/unread-count` adds `byGroup: {RESULTS, EXAMS, ANNOUNCEMENTS}`; `count` stays.
- `POST /api/v1/me/notices/read-all?group=` marks one group (absent = all).
- Tests: every kind lands in one group; paging inside a group; counts; read-all per group; other students' notices never appear.

### P1. Order filters and summary

- `GET /api/v1/me/live-exam-orders` adds `status` (PENDING, PAID, FAILED, CANCELLED; repeatable), `scope` (MODULE, EXAM, BUNDLE), `moduleId`, `examId` (an exam matches its own orders and bundles that hold it), `from`, `to` (dates, Bangladesh time, `to` inclusive), `orderId` (see P3). Sort newest first, as now.
- `GET /api/v1/me/live-exam-orders/summary` returns `{ totalPaid, currency, paidCount, pendingCount }` for the signed-in student (all time).
- Tests: each filter alone and combined; date edges at midnight Bangladesh time; bundle match by `examId`; another student's orders never counted.

### P2. Paid time

- V52: `live_exam_orders.paid_at timestamptz null`. Set in the same transaction that marks an order PAID (gateway verify, IPN, Master Admin confirm).
- Backfill: PAID rows get `paid_at = updated_at`. This is the best value on record; note it in `current-api.md`.
- `paidAt` added to the student list row, to `OrderView`, and to the staff order row.
- Tests: each path that pays sets it once; a later update does not move it.

### P3. One order

- `orderId` filter on the list (P1), returning zero or one row with titles. Another student's id returns an empty page (no leak).
- `OrderView` keeps its shape; the page uses the list row.

### P4. Receipt number and PDF

- V52 (same file as P2): `receipt_no varchar(24) unique null`, from a sequence, set when the order becomes PAID. Format `RCPT-2026-000123` (year of `paid_at`, Bangladesh time). Backfill existing PAID orders in `paid_at` order.
- `receiptNo` added to the list row, `OrderView`, staff row, and the receipt email.
- `GET /api/v1/me/live-exam-orders/{id}/receipt.pdf`: own PAID order only (404 otherwise). One A4 page built with PDFBox: site name, receipt number, paid time, student name and phone, item (type and title), list price, discount, amount paid, payment method, transaction ID, order ID.
- **Bangla in the PDF.** PDFBox does not shape Bangla script, so Bangla text would render broken. The PDF uses English labels and the English title; when an item has only a Bangla title, the PDF prints the item type and order ID instead. The web print view stays as the Bangla receipt, and the page offers both ("Download PDF", "Print").
- Tests: number assigned once and in order; PDF for own PAID order is a valid PDF holding the receipt number; 404 for pending, other student's, and unknown orders.

### E1. Exam cover

- V53: on `live_exams` the same four columns as V41 (`cover_stored_name`, `cover_content_type`, `cover_byte_size`, `cover_updated_at`).
- `ExamCoverService` mirroring `ModuleCoverService` (same store, same 2 MB limit, same types, old file removed after commit). Saved at once, not staged, same as the package cover.
- Routes: `POST` and `DELETE /api/v1/live-exams/{id}/cover` (Master Admin, `MODULE_COVER_MANAGE`), staff `GET /api/v1/live-exams/{id}/cover`, public `GET /api/v1/public/live-exams/{id}/cover` (published exams only).
- `coverUrl` (null when none) added to: public exam read, student exam read, category exam rows, package exam rows, popular rows, favourites rows.
- Audit entries as for the package cover.
- Seed: none (no image asset in the seed). Review by upload.
- Tests: upload, replace, delete, size and type limits, draft cover hidden from the public route, permission.

### Not changed

listPrice and discountPercent (already sent); category counts (tree); MCQ/Written filters (rows); notice exam type; grant order links; continue payment (re-post).

## 6. Sub-steps

Working rules as before: lint per slice, targeted unit tests, one build for the phase, headless overflow text report only, no screenshots. Every new label in `en` and `bn`.

### S4.0 API (N1, P1 to P4, E1)

As section 5, then `current-api.md` (new "Added" section), `decisions.md` (Phase 4 lines), and the frontend types (`MyOrder`: `scope` with BUNDLE, `listPrice`, `discountPercent`, `paidAt`, `receiptNo`; exam types: `coverUrl`; notices: `group`, `byGroup`).

### S4.1 Top bar and category counts (A2, A4)

- Remove `/mcq` and `/written` from `TOP_LINKS` (desktop only). Routes stay; Home keeps the doors.
- `catalog-helpers.ts`: `countBranchExams(node)` (own exams plus all subcategories).
- `CategoryCard`: two lines in one: "34 exams · 3 packages" (branch counts, so a leaf shows its own). A card with subcategories adds "5 categories" before them. Nothing at all: "Coming soon" as now. Zero parts are left out ("3 packages" alone if no exams).
- Keys: `catalog.countExams`.
- Tests: top bar (MCQ and Written gone on desktop; mobile tabs unchanged); counts on a three-level tree.

### S4.2 Type filter on My exams and Results (A1, A3)

- Segmented control "All · MCQ · Written" under the existing tabs. URL `?type=MCQ|WRITTEN`. Badges count the chosen type. Empty text names the type.
- My exams, Packages tab: a package holds both types, so the control filters the exam tab only and hides itself on the Packages tab.
- Tests: pure filter function; both pages, including URL restore.

### S4.3 Shared detail layout (A5, A6)

One layout component used by the package page and the exam page (`catalog/detail-layout.tsx`):
- Desktop (`lg` and up): left column for header and details; right column a sticky card (`top` below the top bar) that opens with the cover (16:9, card width, about 320 px) and then price, discount, main action, what is included, access length.
- Phone and tablet: header first (type, title, status, short summary), then the main action block, then a 16:9 cover band capped at 160 px tall, then details. The existing sticky bottom bar stays.
- No cover: the typed placeholder that `PackageCover` already draws, at the same small size.

### S4.4 Package page and exam page (A5, A6, A7)

- Package page on the shared layout: buy card on the right (desktop) or right after the header (phone); tabs Exams, Routine, About below. Owned package: the card shows the progress bar and the payment line (S4.6).
- Exam page on the shared layout: cover is the exam's own cover; with none, the first package's cover; with neither, the placeholder. Stats list and description in the left column, packages below.
- Wish List: a 44 px icon button in the top-right corner of the header (detail pages) and of each card (rows), absolutely placed so the text keeps its width. The toggle's error message moves to a toast. Same in `ExamRow`, `CatalogExamRow`, package cards on the Wish List page.
- Exam rows and cards with an exam cover: no image in rows (rows stay compact); the cover shows on the detail page only.
- Fix the "বিবরণ" clipping at 320 px seen earlier.
- Tests: DOM order (header and action before the cover on phone); cover fallback chain; favourite button one icon with a name; overflow report.

### S4.5 Notices (A8)

- Tabs: Results | Exams | Announcements, each with its unread count (`byGroup`). URL `?tab=`. Default: Results if it has unread, else Exams if it has unread, else Announcements if it has unread, else Exams.
- "Mark all as read" acts on the open tab.
- Card restyle:
  - A kind icon and a coloured edge per tab (results: success colour; exams: accent; announcements: muted).
  - Kind pill, title, meta line (exam title, date and time), unread dot.
  - Results: score chip "34/50" and a pass or fail pill; "View result".
  - Exams: start time for reminders; "Open exam".
  - Purchase granted: "Open exam" (or "My exams" when no exam) and "View payment" (to `/account/payments?examId=` or `?moduleId=`).
  - Announcements: read in place, expand control, as now.
- Unknown kinds: Exams tab with the generic label (matches the server's group rule).
- Tests: tab per kind for every kind in `NOTICE_KINDS` and one unknown; tab switch and URL; read-all per tab; empty states per tab.

### S4.6 Payment history (A9)

Route `/account/payments`, title "Payment history".

**Page.**
- Summary strip (P1 summary): total paid, paid orders, awaiting payment (the last one is a button that sets the status filter).
- Filters in the URL: status, type (Package, Exam, Bundle), date range; "Clear filters". A line says what is filtered ("Showing: Paid, last 30 days").
- Desktop table: date, item, type, status, amount, method, receipt number. Phone: cards with the same fields.
- Amount: paid amount; when it differs, the list price struck through and "−25%".
- Row opens a detail panel: order ID and transaction ID (copy), paid time (P2) or created time for others, receipt number (P4), method, what was bought (bundle: each exam, by title, from the access read), access until (`endsAt` from the grant: date, "Lifetime", or "Expired on").
- Actions by status:
  - Paid: "Download PDF" (P4), "Print receipt" (existing view), "Open" (the package or exam).
  - Awaiting payment: "Continue payment" (re-posts the target, goes to the gateway), "Cancel" (existing endpoint, with a confirm).
  - Failed or cancelled: "Buy again" (opens the item page).
- `?order=<id>` opens that row's panel (P3); `?examId=` and `?moduleId=` filter to one item.
- Help line at the bottom: "Problem with a payment? Contact us" → `/contact`, showing the order ID to quote.
- Empty state: "No payments yet" with the door actions.
- Bundle label fix (`scope` BUNDLE).

**Where the payment links live** (Q5: checked and managed easily).

| Place | Link |
|---|---|
| Student menu (`UserMenu`) | "Payment history" after Account |
| Account page | Existing card, renamed "Payment history" |
| My exams page header | "Payment history" |
| My exams, each bought row (grant with `orderId`) | "Purchased {date} · View payment" → `?order=` |
| Package page and exam page, when bought | Same line in the buy card |
| Package page and exam page, with an open order | "Payment waiting" with "Continue payment" and "Cancel" (P1 `moduleId`/`examId` plus `status=PENDING`) |
| Purchase result page | "View payment" and, when paid, "Download receipt" |
| Purchase-granted notice | "View payment" |
| Receipt print view | "Back to payment history" (exists) |

Rows from a roster or manual grant show "Given by admin" or "Roster", no payment link.

- Tests: filters to query mapping; each status's actions; bundle row; `?order=` opens the panel; continue payment posts the right target for MODULE, EXAM and BUNDLE; links present on each place in the table.

### S4.7 Staff side (needed by E1 and P4)

- Exam editor: cover upload using `cover-uploader.tsx` (same as packages), with remove. Placed in the exam details section. Not staged; a note says the change is live at once (same as the package cover).
- Staff order list: show `paidAt` and `receiptNo` columns.
- Tests: uploader on the exam editor; order list columns.

### S4.8 Checks and review

- Lint per slice; targeted unit tests; API ITs through the runner; one web build at the end.
- Overflow text report at 320, 375, 768, 1280 px for Home, Browse (door and category), package page, exam page, My exams, Results, Notices, Payment history, receipt view, Wish List.
- `en` and `bn` key parity (pattern: `tests/ws5/messages.test.ts`).
- `progress/S4-review.md`: review accounts and cases (an exam with and without a cover, a paid, a pending, a failed and a cancelled order, a bundle order, a discounted order). Seed: add one pending, one failed and one cancelled order for the review student (insert-only, dev and staging only), if the seed lacks them.
- QA checklist addition for Phase 4.

## 7. Markers

None expected: every item has API support once S4.0 lands. If S4.0 is late, the UI slices ship behind these interim behaviours and list them in `markers.md`:

| Marker | Waiting on | Interim |
|---|---|---|
| M-N1 | N1 | No tabs; single list as now |
| M-P1 | P1 | No filters or summary; payment lines on detail pages hidden |
| M-P2 | P2 | "Created on" only |
| M-P3 | P3 | "View payment" goes to the full list |
| M-P4 | P4 | No receipt number; print view only |
| M-E1 | E1 | Package cover fallback only |

## 8. Final recheck

Verified against the code and the answers:

- A1 to A9 each map to a step (S4.1 to S4.6), and every API change to S4.0.
- E1 and P4 need staff work (upload, order columns): added as S4.7.
- Bangla in the PDF: PDFBox cannot shape it; English PDF plus the Bangla print view.
- Pending orders: "Continue payment" needs no API change (the open order is reused); detail pages need P1 item filters to know about it: added `moduleId`, `examId` to P1.
- `examId` filter must match bundles holding the exam: in P1.
- Notice "Mark all as read" would clear other tabs: per-group read-all added to N1.
- New notice kinds later: the server default puts them under Exams; the page does the same.
- Paid time backfill is approximate for old orders: recorded in `current-api.md`.
- Bundle label bug on the payment page: fixed in S4.6.
- Packages tab on My exams has no single type: filter hidden there.
- Roster and manual grants have no order: no payment link, a source label instead.
- Review data: seed adds the order states the review needs.
- Refunds: none, per Q7.
- Not covered on purpose: refunds, invoices and tax lines, instalments, search in payment history, a pending order's price after a discount change (existing behaviour: the open order keeps its amount).

## 9. Order of work

1. S4.0 API on `p4/api` (one agent), in parallel with S4.1 and S4.2 on `ui/nr01-ui` (no API needed).
2. S4.3 and S4.4 (E1 for the exam cover; package side needs nothing).
3. Merge `p4/api` into `ui/nr01-ui` when its ITs pass.
4. S4.5 and S4.6.
5. S4.7.
6. S4.8 checks and review file; the user's QA.

Nothing is pushed. Each slice is its own commit.
