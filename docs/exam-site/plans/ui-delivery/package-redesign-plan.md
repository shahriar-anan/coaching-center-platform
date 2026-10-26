# Package redesign: module pages, package cards, purchase sheet, cover images

> **Status (2026-10-26):** the API, the module page, package cards and the staff uploader are committed (`e387608`). The exam page and purchase flow are built but not committed. Deviations from this plan and the current progress are recorded in [package-page-design-plan.md](package-page-design-plan.md), section 10. Where the two documents differ, section 10 is the current record.

Written 2026-10-26 for Shahriar. Decided after an LMS practice review (course-page patterns from Coursera, Udemy, Teachable and Moodle). Scope is confirmed: a **package is a module**, each package has **one cover image**, and **only the Master Admin** uploads it. Image rules: JPG, PNG or WebP, up to 2 MB, shown in a 16:9 crop.

Checks are run once, at the end (section 9). Steps are built in the order in section 8.

## 1. Problems being fixed

| # | Problem | Where |
|---|---|---|
| P1 | Exam row descriptions run past the card edge, and the status and price on the right are hidden. | Module page, exam list |
| P2 | Module page is a column of similar boxes; nothing marks the package as the main product. | Module page |
| P3 | Price is small and sits in a plain box; no "what is included". | Module page, purchase card |
| P4 | Purchase sheet is a stack of text lines with two buttons. | Purchase sheet |
| P5 | No package image anywhere; cards look unfinished. | Module list, landing "Our packages", module page |
| P6 | Master Admin has no way to add a cover image. | Staff module details |

## 2. Design rules (from the LMS review, kept within the client mockup tokens)

- **Cards:** 16:9 cover on top; the title clamped to two lines; a one-line summary; one meta row (exam count, type, next date); price at the bottom.
- **Text never escapes its box.** Every text line in a card or row has `min-w-0` on its flex parent, and long text uses a line clamp. Full text is kept in the page body, not hidden.
- **Hero:** cover on the left (desktop) or top (mobile), title, short description, facts. The buy box sits beside it on desktop and as a sticky bottom bar on mobile.
- **Price:** large and bold in the accent colour, with "what is included" as ticks.
- **One primary action per screen.** Secondary actions are outline or ghost.
- **Fallback cover:** until an image is uploaded, a navy-to-gold gradient with the exam type icon and the initial letters of the title.
- **Column width:** student pages stay in the mockup's 760 px column. Package lists may use the wide landing layout only on `/`.

## 3. API (Java, Spring)

Reuse the exam-image pipeline already used for question images (`ExamImageStorage`, `ExamImageProcessor`, `LocalFileStorage`). The cover image is stored the same way, with its own size limit.

1. **Migration `V41__module_cover.sql`:** add nullable columns to `live_exam_modules`: `cover_stored_name varchar(80)`, `cover_content_type varchar(40)`, `cover_byte_size bigint`, `cover_updated_at timestamptz`.
2. **Entity `LiveExamModule`:** the four fields, with getters and setters.
3. **Permission:** add `Permissions.MODULE_COVER_MANAGE`, granted only to `MASTER_ADMIN` in the exams permission contributor.
4. **Endpoints:**
   - `POST /api/v1/live-exam-modules/{id}/cover` (multipart, field `file`): Master Admin only. Replaces any old cover and deletes the old stored file. Returns the module view.
   - `DELETE /api/v1/live-exam-modules/{id}/cover`: Master Admin only.
   - `GET /api/v1/public/live-exam-modules/{id}/cover`: public, streams the image with `Cache-Control: public, max-age=3600`. Returns 404 when there is no cover.
5. **Views:** `ModuleView` (staff) and `PublicModule` (public) get a `coverUrl` string: the public GET URL when a cover exists, otherwise null. Staff also gets `coverUpdatedAt` so the browser can cache-bust.
6. **Errors:** wrong type → `VALIDATION_FAILED` 400; over 2 MB → `FILE_TOO_LARGE` 413; not Master Admin → 403; no module → 404.
7. **Tests:** an integration test for upload (ok, wrong type, too large, wrong role), fetch (public, 404 when empty) and delete.

## 4. Web: shared pieces

New files under `web/src/components/student/catalog/` and `web/src/components/ui/package/`:

- `package-cover.tsx`: `PackageCover({ src, type, title, aspect })`. Renders the image with `next/image` when `src` exists, otherwise the gradient fallback. Always 16:9 (or the given aspect).
- `package-card.tsx`: `PackageCard({ href, cover, type, title, summary, examCount, nextDate, price, footer })`. Link card. Title clamped to two lines, summary clamped to two lines.
- `exam-row.tsx`: `ExamRow`. Fixed layout: icon tile, a `min-w-0` text column (title one line, summary two lines), and a `shrink-0` right column for status or price. Replaces the inline row in the module page.
- `included-list.tsx`: `IncludedList({ items })`. Ticks with the accent colour.
- `lms-blocks.tsx` (already added): keep `SectionCard`, `FactTile`, `ruleLines`.

Types: `PublicModule` and `StaffModule` gain `coverUrl: string | null` and `coverUpdatedAt: string | null`.

## 5. Web: pages

1. **Module page** (`module-page.tsx`):
   - Hero: `PackageCover` plus title, type, description (full text), facts (exam count, next date, paper type).
   - Buy box: price, `IncludedList` (all exams of this type, future exams included, paper language), primary button. On desktop it is a right-hand column; on mobile it is a sticky bottom bar.
   - Routine table: kept, with the fixed header and zebra rows from the last change.
   - Exam list: `ExamRow` for each exam.
2. **Module list pages** (`/mcq`, `/written`, door pages): `PackageCard` in a grid (1, 2 or 3 columns).
3. **Landing "Our packages"** (`featured.tsx`): `PackageCard` with the cover.
4. **Exam page:** no structural change this round; the price duplicate is removed (price stays in the buy box only). Uses `lms-blocks.tsx` as now.

## 6. Web: purchase sheet

`purchase-sheet.tsx` gets a package summary at the top: cover thumbnail (16:9, small), title, exam count, price in large type. Then `IncludedList`, the secure-payment line, the refund line, and the actions. The primary button is full width; cancel is outline. Error states keep their current text and placement.

## 7. Staff: cover upload

`web/src/components/staff/catalog/module-details.tsx` gets a `CoverUploader` section, shown only to the Master Admin (`MODULE_COVER_MANAGE`). It shows the current cover in 16:9, a "Replace" button (file input, JPG, PNG or WebP, max 2 MB, checked in the browser before upload), and a "Remove" button. Errors use the existing error mapping. A new hook `useUploadModuleCover` and `useRemoveModuleCover` go in `lib/api/exam/staff-catalog.ts`.

## 8. Build order

Each step is built and kept on the branch, with no checks run until section 9.

1. API: migration, entity fields, permission, cover endpoints, views.
2. API: integration test for the cover endpoints.
3. Web types and the shared pieces (`PackageCover`, `PackageCard`, `ExamRow`, `IncludedList`).
4. Module page: hero, buy box, exam rows.
5. Module list pages and landing "Our packages" use `PackageCard`.
6. Purchase sheet: package summary and included list.
7. Exam page: remove the duplicate price.
8. Staff: `CoverUploader` and its hooks.
9. Web tests: update the module, purchase and landing tests; add tests for `PackageCover` fallback, `ExamRow` clamp and `CoverUploader` role gating.

## 9. Checks (run only after step 9 is done)

- API: compile and the new cover integration test, plus the existing exam tests.
- Web: type check, lint, the full vitest suite.
- Build: `npm run build`, then restart the site on port 3000.
- Browser: measure overflow (`scrollWidth` must equal the viewport) and screenshot the module page, the exam list, the purchase sheet, the package cards and the staff uploader at 360, 390, 768 and 1440 px, light and dark. Check the exam-row clipping is gone.
- Demo data: upload one cover to a demo module through the staff screen to confirm the full path.

## 10. Out of scope

- Several images per package, or a gallery.
- Image cropping in the browser. The API accepts any 16:9-ish image and stores it as uploaded (the processor re-encodes it).
- Changes to the purchase flow, SSLCommerz or the access rules.
