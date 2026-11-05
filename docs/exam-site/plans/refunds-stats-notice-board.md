# Plan: refund status, discount date clear, live landing figures, public notice board

Status: planned, not started. Written 5 November 2026 for Shahriar and the agent who builds it. The site takes real money since 5 November (live SSLCommerz store, `prod` profile), so every release follows section 7.

Four changes Shahriar asked for, plus one small guard found during the live test:

| # | Change | API | Web | Migration |
|---|---|---|---|---|
| A | Discount end date gets a Clear button | none (one web bug fix) | staff exam and package editors | none |
| B | Landing figures: live students and mentors, "app coming soon" | new public endpoint | landing `Numbers` | none |
| C | A manual refund is recorded and shown to the student | refund action, order fields | staff payment detail, student payments and receipt | `V69` |
| D | Public notice board: admins draft, Master Admin publishes | staff and public endpoints | staff editor, public page, navbar | `V70` |
| E | A price that ends under 10 BDT cannot be saved | validation | error text | none |

Build in this order: A, E, B, C, D. Each is its own commit and can ship alone.

## 1. Decisions to confirm

Each has a recommendation. The sections below are written to the recommendation; change them if Shahriar picks otherwise.

| ID | Question | Recommendation |
|---|---|---|
| R1 | Does marking an order refunded remove the access it bought? | Yes by default. The refund form has "Also remove access", ticked. The Master Admin can untick it (for example a goodwill partial refund) |
| R2 | Partial refunds? | Allowed: the amount is from 0.01 up to the paid amount, default the full amount. Full shows "Refunded", partial shows "Partly refunded" |
| R3 | Who records a refund? | Master Admin only, like confirming an order |
| R4 | Is the student told outside the payments page? | Not in this delivery. No email, no inbox notice. The payments list and payment details show it |
| R5 | Can a refund record be undone? | No. A confirm dialog guards it. A wrong record is fixed in the database with Shahriar's go |
| S1 | Where does the student count start? | 874 plus every student account created after a fixed moment, the release time of slice B. Today it shows 874; the next sign-up shows 875. The six students who joined from 3 November count inside the 874 |
| S2 | What counts as a mentor? | **Decided by Shahriar, 5 November:** the count starts from 2. Each instructor who has activated their account (set a password; status `ACTIVE`, not deleted) adds 1. A pending invitation does not count. There are none today, so it shows 2; the first activated instructor makes it 3 |
| N1 | Where does the public board live? | A new public page `/notice-board`. The landing navbar's "Notices" points there. The signed-in student's inbox stays at `/notices` (bell and sidebar) |
| N2 | Two things called "Notices"? | Rename the inbox's menu label to "Notifications" / "নোটিফিকেশন" and keep "Notices" / "নোটিশ" for the board. Check the Bangla against existing terms first |
| N3 | Who edits what? | System Admin and Master Admin create and edit drafts. Only the Master Admin publishes, unpublishes, edits a published notice, or deletes a published one |
| N4 | What must a notice have to be published? | All four fields: English and Bangla title, English and Bangla description. A draft may be incomplete |
| N5 | Text format | Plain text with line breaks kept. No rich text, links or images |

## 2. A. Clear button for the discount end date

**Today:** the exam and package price sections use `DateTimeInput` for "Discount ends". Once a date is picked there is no way to remove it, short of removing the discount.

**Change:**
- Give `web/src/components/ui/date-time-input.tsx` an optional `clearable` prop. When set and the field has a value, a small "Clear" button (`common.actions.clear`, or add it: "Clear" / "মুছুন") sits beside the input and calls `onChange("")`. It is a real button with its own label, 44 px tall to tap, disabled with the input.
- Use it in `web/src/components/staff/catalog/exam-price-fields.tsx` ("Discount ends") and in the package (module) price fields that use the same input (`staffCatalog` keys around `discountEndsAt`, lines 425 and 523 of the English messages).
- **Bug to fix with it:** `examBodyFor` in `exam-form-state.ts` (line 251) sends `discountEndsAt: form.discountEndsAt`, which is `""` after a clear, not `null`. Send `null` when it is empty. Check the package form's body builder the same way. The API clears the field on `null` (`ExamState.instant`), and `ExamPricing.validate` already allows a discount with no end.
- A cleared end on a published exam goes through the usual working copy and "publish changes", like any other edit. The change list (`change-list-dialog.tsx`) already formats `discountEndsAt`; check it shows "none" for the cleared side.

**Check:** set a discount with an end date, save, clear the date, save. The student exam page shows the discount with no end. The same on a package. The exam's change list shows the old date and "none".

## 3. E. No sale price under 10 BDT

**Found on 5 November:** a 10 BDT exam with a 20% discount cost 8 BDT. SSLCommerz refuses a payment under 10 BDT, so the student saw "Payments aren't available right now" and could not buy.

**Change:**
- `ExamPricing.validate` (API) refuses a price, or a price with its discount, whose effective amount is under 10.00 BDT: a `FieldErrorDetail` on `price` or `discountPercent`, "The price after the discount must be at least 10 BDT." The same for packages and bundles if they share the rule.
- The web form mirrors it in `exam-form-state.ts` with a translated message, so the editor shows it before saving.
- Existing rows are not changed. Today only the test exam "Payment EN" breaks the rule, and it is unpublished after the test.

**Check:** 10 BDT with 20% is refused with the message; 13 BDT with 20% (10.40) saves.

## 4. B. Live landing figures

**Today:** `web/src/components/student/landing/numbers.tsx` has four fixed figures: 874 students, 2 mentors, 200 app downloads, 1000 learning materials.

**API:**
- New `GET /api/v1/public/site-stats`, no sign-in (same as `/api/v1/public/exam-site/status`), answering `{ "students": 874, "mentors": 2 }`.
  - `students` = `app.stats.students-base` (874) + students (`role = 'STUDENT'`, `deleted_at IS NULL`) with `created_at >= app.stats.students-counted-from`.
  - `mentors` starts from 2: `app.stats.mentors-base` (2) + activated instructors (`role = 'INSTRUCTOR'`, `status = 'ACTIVE'`, `deleted_at IS NULL`). An invited instructor who has not activated yet (`PENDING_ACTIVATION`) is not counted; an instructor made inactive or deleted drops out again.
- The three values live in `application.properties` with defaults: base 874, base 2, and `students-counted-from` set to the release time of this slice (an ISO instant, for example `2026-11-05T12:00:00Z`; write the real value in the deploy record). They can be overridden from the env file without a rebuild.
- Two count queries, cached in memory for 60 seconds, so the landing page cannot load the database.
- Counts only, never names or emails.

**Web:**
- `Numbers` reads the endpoint with React Query (`lib/api/exam/` beside the other public reads). While loading or on an error it shows 874 and 2, so the box never jumps or shows a blank.
- Students and mentors keep the "+" (874+, 2+) and Bangla digits as now.
- **App:** the third card keeps its icon and colour, shows "Coming soon" / "শীঘ্রই আসছে" in place of the number, and its label becomes "Mobile app" / "মোবাইল অ্যাপ". No count anywhere. Check both strings against existing Bangla terms: natural wording, not a literal translation.
- Learning materials stays 1000+.

**Check:** the page shows 874+ and 2+. Register a test student: 875+ within a minute. Activate a test instructor: 3+. Delete both test accounts afterwards: back to 874+ and 2+. The app card shows no number in either language.

## 5. C. Refund recorded and shown to the student

Refunds are made by hand in the SSLCommerz merchant panel. The site only records them. It never calls SSLCommerz's refund API.

### 5.1 Data (`V69__live_exam_order_refund.sql`)

Add to `live_exam_orders`, all nullable:

| Column | Type | Meaning |
|---|---|---|
| `refunded_at` | `TIMESTAMPTZ` | When the refund was recorded |
| `refunded_by` | `UUID` → `users` | The Master Admin who recorded it |
| `refund_amount` | `NUMERIC(12,2)` | 0.01 up to `amount` (check constraint) |
| `refund_reason` | `VARCHAR(500)` | Staff only, never shown to the student |
| `refund_reference` | `VARCHAR(128)` | Optional SSLCommerz refund reference from the panel |

`status` stays `SUCCEEDED`, so receipts, the paid date, reports and the recheck job keep working. "Refunded" is derived: `refunded_at IS NOT NULL`. A check constraint keeps the five columns all set or all null, and `refunded_at` only on a `SUCCEEDED` order.

### 5.2 API

- `POST /api/v1/live-exam-orders/{id}/refund`, body `{ amount, reason, reference?, revokeAccess }`. Master Admin only: new permission `LIVE_EXAM_ORDER_REFUND`, granted to `MASTER_ADMIN` in `LiveExamPermissionContributor`. Put it in `LiveExamOrderDecisionController` beside confirm and reject.
  - 409 unless the order is `SUCCEEDED` and not yet refunded. 400 for an amount outside 0.01 to `amount`, or a reason that is empty or over 500 characters.
  - With `revokeAccess`, every live grant whose `order_id` is this order gets `revoked_at`, `revoked_by` and `revoke_reason` ("Refunded"), using the columns `V39` already added. Grants stay as rows.
  - One audit record, `LIVE_EXAM_ORDER_REFUND`, with the order id.
  - One transaction. Calling it twice gives 409 and changes nothing.
- The staff order views (list and the `/staff/payments/[tranId]` detail) gain `refund: { amount, at, by, reason, reference, accessRevoked } | null`.
- The student order views (`/api/v1/me/live-exam-orders`, its detail, and the receipt PDF) gain `refund: { amount, at } | null`. No reason, no staff name.
- The student payment status (`PaymentStatus` in the web, mapped by the API) gains `REFUNDED` for a full refund and `PARTLY_REFUNDED` for a partial one. The status filter accepts both.
- Staff payment totals show the gross amount, the refunded amount and the net amount, where they show a total today.

### 5.3 Staff web

- `/staff/payments/[tranId]`: a "Record refund" button for the Master Admin on a paid, unrefunded order. It opens a dialog with:
  - amount, default the paid amount
  - SSLCommerz refund reference (optional)
  - reason (required)
  - "Also remove access", ticked
  - a sentence saying this only records a refund already made in the SSLCommerz merchant panel

  After saving, the page shows a "Refunded" panel with the amount, date, who, reference, reason and whether access was removed.
- `/staff/payments` list: a "Refunded" or "Partly refunded" chip on the row.

### 5.4 Student web

- `/account/payments` list: the status chip reads "Refunded" / "ফেরত দেওয়া হয়েছে" or "Partly refunded" / "আংশিক ফেরত দেওয়া হয়েছে". `payment-model.ts`: add both to `STATUSES`, give them `statusKey` translations, and give `actionsFor` the receipt only (no "Open" when access was removed; keep "Open" when it was kept).
- The payment details view (`account/payments/receipt`, `receipt-view.tsx`): a clear notice at the top, for example: "Refunded: 15 BDT on 5 November 2026. The money goes back to the account you paid with (bKash). It can take a few working days to arrive." Use the payment channel when the order has one. If access was removed, add "Access to this exam has ended."
- The receipt PDF: the same line under the totals, and the word "REFUNDED" near the status.

### 5.5 Checks

- A paid order: record a full refund with access removed. The student list says Refunded. The details show the notice. The exam asks to buy again. The PDF says refunded.
- A partial refund with access kept: "Partly refunded", and the exam stays open.
- A second refund call, a System Admin, and a refund on an unpaid order are each refused.
- After release, record the real 15 BDT test refund (order `6775cda08192479782376e5e038135`) this way.

## 6. D. Public notice board

The signed-in student inbox (`/notices`, the `student_notices` and `announcements` tables) stays as it is. The board is separate: public, bilingual, and published by the Master Admin.

### 6.1 Data (`V70__site_notices.sql`)

`site_notices`:

| Column | Type | Note |
|---|---|---|
| `id` | `UUID` PK | |
| `title_en`, `title_bn` | `VARCHAR(300)` NULL | Required to publish |
| `body_en`, `body_bn` | `TEXT` NULL | Required to publish; plain text |
| `status` | `VARCHAR(16)` | `DRAFT`, `PUBLISHED`, `UNPUBLISHED` |
| `created_by`, `updated_by`, `published_by` | `UUID` → `users` | `published_by` null until published |
| `created_at`, `updated_at`, `published_at` | `TIMESTAMPTZ` | `published_at` set on each publish |
| `deleted_at` | `TIMESTAMPTZ` NULL | Soft delete |

A check constraint: a `PUBLISHED` row has all four texts and a `published_at`. An index on `(status, published_at DESC)` where `deleted_at IS NULL`.

### 6.2 API

Permissions: `SITE_NOTICE_EDIT` for `MASTER_ADMIN` and `SYSTEM_ADMIN`; `SITE_NOTICE_PUBLISH` for `MASTER_ADMIN` only.

| Route | Who | Does |
|---|---|---|
| `GET /api/v1/site-notices?status=&page=` | edit | All notices, newest change first |
| `GET /api/v1/site-notices/{id}` | edit | One notice |
| `POST /api/v1/site-notices` | edit | Creates a draft |
| `PUT /api/v1/site-notices/{id}` | edit; a published or unpublished one needs publish | Saves the four fields. Edits to a published notice show at once |
| `POST /api/v1/site-notices/{id}/publish` | publish | 400 naming each missing field. Sets `published_at` and `published_by` |
| `POST /api/v1/site-notices/{id}/unpublish` | publish | Hides it from the public |
| `DELETE /api/v1/site-notices/{id}` | edit for a draft; publish otherwise | Soft delete |
| `GET /api/v1/public/site-notices?page=` | anyone | Published only, newest `published_at` first, 10 a page |
| `GET /api/v1/public/site-notices/{id}` | anyone | One published notice; 404 for any other |

Each write records an audit entry (`SITE_NOTICE_CREATE`, `_UPDATE`, `_PUBLISH`, `_UNPUBLISH`, `_DELETE`). Titles are limited to 300 characters and bodies to 10,000. Text is stored and returned as typed; the web renders it as text, never as HTML.

### 6.3 Staff web

- A new staff menu item "Notice board" (`/staff/notice-board`), for Master Admin and System Admin. Keep it apart from "Announcements", which messages students directly.
- List: title (in the viewer's language, the other if blank), a status chip (Draft / Published / Unpublished), last change, and published date.
- Editor: four fields, English title and description and Bangla title and description, with plain textareas and a character count. "Save draft" for both roles.
  - For the Master Admin: "Publish" (and "Unpublish" on a published notice), each with a confirm.
  - A System Admin sees "Waiting for the Master Admin to publish" on a complete draft.
  - Missing fields are named before publishing.
- Delete with a confirm, within the rules in 6.2.

### 6.4 Public web

- `/notice-board` (public, in the landing site's layout): published notices as cards. Each card shows the title, publish date and the first three lines; "Read more" opens `/notice-board/[id]` with the full text. Line breaks are kept. The visitor's language is used first and the other when a field is blank (it cannot be, once published). Empty state: "No notices yet" / "এখনো কোনো নোটিশ নেই". Paged, 10 a page.
- `web/src/components/shell/site-menu.tsx` line 30: the "Notices" item points to `/notice-board`. Today it points to the signed-in inbox, which sends a visitor to the login page.
- Per N2, the inbox's own label (bell, sidebar, student shell) becomes "Notifications"; its route stays `/notices`.
- Page title and description metadata for both pages.

### 6.5 Checks

- A System Admin creates a draft and cannot publish (button absent, API 403). The Master Admin publishes. A signed-out visitor sees it on `/notice-board` in both languages. Unpublish removes it. A draft is never public (`/api/v1/public/site-notices/{id}` gives 404).
- Publishing with a missing Bangla body is refused and names the field.
- Text with `<script>` shows as text.

## 7. Release (the site takes real money)

- API slices (B, C, D, E) need a new jar. C and D add migrations `V69` and `V70`, which only add columns and a table; no row is changed.
- Before each API release: dump the database to `/var/backups/coaching/`, check that no sitting is open (`live_exam_participants` with status `IN_PROGRESS`), keep the running jar. The API restarts in about 20 s, and a student mid-checkout may see an error for that time. Choose a quiet hour.
- The website build: `SITE_URL=https://symamlaw.com STAFF_URL=https://admin.symamlaw.com bash web/scripts/release-build.sh <out.tgz>`, then `deploy-web.ps1`, dry run first.
- Tests: write them with each slice (API: refund rules and permissions, notice permissions and publish rule, stats counting, the 10 BDT rule; web: the payment model, the date clear, the notice editor's state). Run them only when Shahriar says.
- Record each release in `reports/client-review-deploy.md`, including the `students-counted-from` value.

## 8. Not in this plan

- Calling SSLCommerz's refund API, refund emails, or inbox notices for refunds (R4).
- Showing notices on the landing page itself, pinning, images, attachments, or scheduling a publish time.
- A real app download count.
