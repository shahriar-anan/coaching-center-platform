# WS9 — Close the gaps, finish the features, polish the UI

Written 25 October 2026 after every workstream (WS0–WS6b) was merged into `catalog-acquisition` and the merged code was green (API suite 315 run, only `BookOrderIT` and `CoursePurchaseIT` failing; web lint 0 errors, `tsc` clean, 222 tests, build ok with 69 routes). **One agent** does this whole file. After it is merged, Shahriar does the typography review and the manual QA himself on the dev server, using the guide in Part 4. [ws8-final-qa.md](ws8-final-qa.md) stays available as an optional automated pass later.

Read first: [README.md](README.md) (architecture, standards, LMS practices), [bangla-copy.md](bangla-copy.md), [ws0-foundation.md](ws0-foundation.md) (tokens, components, the Report with the real contracts), [../api-completion/README.md](../api-completion/README.md) (API practices), [../../current-api.md](../../current-api.md).

## What the analysis found (25 October)

- Every route in the README route table exists. No page is missing.
- Gaps reported by the building agents, still open: G1–G7 below (API plus page).
- Features planned as P1/P2 and skipped: listed in Part 2.
- Style: no hard-coded hex colors in the exam site. But there is no named type scale: arbitrary sizes `text-[15px]` (28 uses), `text-[17px]` (13), `text-[14px]`, `text-[16px]`, `text-[22px]`, and `text-sm` (14 px) is used about 89 times in student components and 89 in staff components. Three older shared components (`components/form-error.tsx`, `components/locale-switcher.tsx`, `components/require-auth.tsx`) still use raw `zinc` colors.

## Where and how

- Worktree `F:\cc-wt\ws9`, branch `ui/ws9-gaps-polish`, cut from `catalog-acquisition`. No other agent runs, but take `F:\coaching-center-platform\.heavy.lock` (write `G`) for `npm run build` and Maven, and delete it after.
- This agent may change both `api/` and `web/`. The README architecture rules still hold: tokens, message catalogs, `lib/config`, no hosts in code, single-process tests, Bangla by the copy guide.
- No new npm packages except, if Part 2 item F9 needs one, `@dnd-kit/core` and `@dnd-kit/sortable` (ask the integrator first). Never edit Flyway `V1`–`V38`; a schema change, if any, is `V39`.
- The agent may start the API and `next dev` **only to verify the Part 4 guide boots**, with `curl` checks. It does not do a browser pass; that is Shahriar's.
- Commit small, one item per commit, the message naming the item id (G1, F3, P2…).

## Part 1 — API gaps (P0 unless marked)

Each item: API change with tests in the matching `*IT`, an `AuditLogIT` route line if a route is added, a line in `current-api.md` under "Added for the UI delivery", and the page change that uses it with a component test.

| # | Gap | Change |
|---|---|---|
| G1 | A teacher reopening a marked paper does not see the saved comment, so saving again can erase it. | `GET /api/v1/live-exam-participants/{id}` (staff submission read) returns `comment`. The marking form prefills marks and comment from the saved draft and shows the stored marked PDF name with "দেখুন" and "বদলান". Saving without a new marked PDF keeps the stored one only if the API already allows it; otherwise the form says a marked PDF is required on every save (current rule). |
| G2 | The submission read has no exam or module info, so a paper opened without `?m=` shows no exam name. | Add `examId`, `examTitleEn`, `examTitleBn`, `moduleId`, `moduleTitleEn`, `moduleTitleBn`, `maximum` to the same read. Remove the `?m=` dependency from the paper page; keep it only for the queue's list order. |
| G3 | The five-minute clash error cannot name the other exam. | On the clash conflict, the error body carries the clashing exam (`id`, `titleEn`, `titleBn`, `liveAt`, `endAt`) in a `details` object (or `fieldErrors` on `liveAt`, matching the shared error shape). The exam editor shows "সাক্ষ্য আইন (১২ নভেম্বর, দুপুর ১২:০০ – ১২:৪০) শেষ হওয়ার অন্তত ৫ মিনিট পরে শুরু করতে হবে।" with a link to that exam. |
| G4 | The module's current teacher is found by scanning the instructor list. | The staff module read (`GET /api/v1/live-exam-modules/{id}`) returns `instructor` (`id`, `name`, `phone`) or `null`. The teacher tab uses it. |
| G5 | The staff module list filters search and status only on the loaded page; no exam counts. | `GET /api/v1/live-exam-modules` (staff) accepts `q` (title in either language) and `status`, and each row carries `mcqCount` and `writtenCount`. The list uses server paging, search and status in the URL. *(P1)* |
| G6 | The written question PDF limit is 20 MB in the browser, unchecked against the server. | Find the server's real limit for the question PDF and the answer PDF (`AppProperties` `app.files.*`, multipart limits, Nginx `client_max_body_size 105m`). Make the client limits match exactly, show the limit in the helper text, and map the server's too-large error to "ফাইলটি অনেক বড়। সর্বোচ্চ {n} এমবি।". *(P1)* |
| G7 | A student cannot change their own display name. | `PATCH /api/v1/me` with `{ "fullName": "..." }` for students only (1–100 characters, trimmed; staff and Master Admin names stay as created, per decisions). The account page gets an inline edit with save and cancel. |

## Part 2 — Features planned and not built

| # | Feature | Where | Priority |
|---|---|---|---|
| F1 | Unread-notice badge on the phone tab bar's নোটিশ item (reuse the bell's unread-count query; one request shared through the query cache). | `StudentShell` | P0 |
| F2 | Leave-page guard for in-app navigation on dirty staff forms (category, module, exam, question, marking): a `ConfirmDialog` "সেভ না করে চলে যাবেন?" when a link is clicked, not only on tab close. One shared hook (`useUnsavedChangesGuard`) in `lib/`. | staff editors | P0 |
| F3 | Next-steps toast after publishing a module or exam: "রোস্টারে…" becomes per the glossary "সরাসরি ভর্তি করুন" (Master Admin) and "অংশগ্রহণ দেখুন". | WS5 editors | P1 |
| F4 | Summary strips: payments (today's successful count and total, pending count; three `size=1`/status calls cached for 60 s) and participation (sitting now, awaiting marking, drafts awaiting publish, from the existing summary). | staff payments, participation | P1 |
| F5 | Text size control (A− / A+, three steps, remembered per device) on the sitting paper and the result review. | WS4 | P1 |
| F6 | Registration helpers: password length guidance as the student types (8–72), phone formatting and a check mark when it matches `01` + 9 digits. | `/register`, account phone change | P1 |
| F7 | Paste many MCQ questions: the documented plain-text format, parsed preview with per-block errors, sequential create with progress, stop on first server error. | MCQ editor | P2 |
| F8 | Self-hosted fonts: switch `next/font/google` to `next/font/local` with the WOFF2 files committed under `web/src/app/fonts/`, so builds never depend on a network fetch (one worktree's build failed on the Google font loader). Keep the same families, weights and CSS variables. | `app/layout.tsx` | P2 |
| F9 | Drag reordering of MCQ questions on desktop (keep the up/down buttons for keyboard and phones). Needs `@dnd-kit`; ask first. | MCQ editor | P2 |
| F10 | Tests the earlier workstreams left out: upload replace state, written deadline transition, offline flush, take/retake redirects, payment-result polling stop after 60 s. | WS4, WS3 tests | P1 |

Not to build (decided): a "লগইন সফল হয়েছে" card before redirecting (a direct redirect is the modern pattern); a single-payment staff read (the `tranId` filter is enough).

## Part 3 — UI polish

The aim: the site should look finished and consistent everywhere, so the client approves it on sight. Keep the two looks: the student site stays the client's mockup style (cream, gold, navy, 760 px column, cards with 16 px radius, emoji and gold chevrons); the staff panel stays the calm admin style. Polish within those, never redesign them.

### P1. A named type scale (do this first; P0)

Add semantic text styles to `globals.css` `@theme` (or small utility classes) and replace every arbitrary size:

| Style | Student | Staff |
|---|---|---|
| `text-display` | `clamp(64px, 22vw, 110px)`, 800, line height 1.05 (hero word only) | — |
| `text-title` | 22 px, 700 (page title) | 22 px, 600 |
| `text-section` | 20 px, 700 | 18 px, 600 |
| `text-card` | 17 px, 700 | 16 px, 600 |
| `text-body` | 16 px, 400, line height 1.6 | 14 px tables / 16 px forms, line height 1.45 |
| `text-meta` | 14 px, muted (helper text, dates, breadcrumbs) | 13 px, muted |
| `text-label` | 15 px, 600 (buttons, field labels) | 14 px, 500 |
| `text-pill` | 12 px, 700 | 12 px, 600 |

Rules: student **content** (descriptions, rules, question stems, options, answers, notices, help text paragraphs) is never smaller than 16 px; 14 px is only for secondary meta. Replace all `text-[15px]`, `text-[17px]`, `text-[14px]`, `text-[16px]`, `text-[22px]` and review every `text-sm` on student pages. Bangla text never gets uppercase or letter-spacing. Question bodies keep their paper typeface.

### P2. Spacing, shape and rhythm (P0)

- One spacing rhythm per surface: student page gutters 16 px, card padding 18 px, gap between cards 12 px, section gap 24 px; staff page padding 24 px, card padding 16–20 px, table cell 10 × 12 px.
- Radii only from the tokens (`rounded-card`, `rounded-control`, pills fully rounded).
- Buttons: one height per size (student 48 px primary, 44 px secondary; staff 40 px), consistent icon gap 8 px, spinner replaces the icon while loading, never changes width.
- Icons from `lucide-react` at 16 px in staff tables and 20 px in student actions, aligned to the text baseline; emoji only where the mockup uses them (doors, category and module cards, section headings).

### P3. States and feedback (P0)

- Every interactive element has hover, active, focus-visible and disabled styles from the tokens (student hover: lift 2 px and gold border, as the mockup; staff hover: subtle background).
- Skeletons match the final layout exactly (same card count and heights), so nothing jumps.
- Empty states: an emoji or `lucide` icon, a title, one sentence, and one action, centered in the card. Error states the same, with "আবার চেষ্টা করুন".
- Toasts for saves and copies; inline errors under fields; no `alert()`.
- Motion 150–250 ms, `ease-out`, only on transform and opacity; none under `prefers-reduced-motion`.

### P4. Student site details (P0)

- Home: hero spacing as the mockup (42 px top, 30 px bottom), the two door cards equal height, dashboard sections as cards with 17 px titles; the countdown card stands out (gold left border) without new colors.
- Module page: the three chips stay in one row at 320 px; routine table scrolls inside its card with a subtle fade at the edge; status pills always fit on one line.
- Exam page: sections as cards in a clear order; one primary button pinned at the bottom of the viewport on phones (sticky action bar with safe-area padding), inline on desktop.
- Sitting: the sticky bar reads well at 320 px (title truncates, countdown never wraps); options have 12 px vertical padding and a clear selected state; the jump grid uses 40 px cells.
- Results and leaderboard: the three-stat row as the mockup, numbers in gold; tables with right-aligned numbers.
- Forms (auth, account): labels above inputs, 16 px input text (prevents phone zoom), consistent field spacing (16 px), the primary button full width.
- Dark mode parity on every student page: check the gold, the pills and the borders in dark.

### P5. Staff panel details (P0)

- One page-header pattern everywhere: title, breadcrumbs, and actions on the right (stacked under the title on phones).
- Tables: sticky header, zebra or hover rows (one choice everywhere), numbers right-aligned, status as pills, row actions in a menu on narrow widths, empty and loading states in the table body.
- Forms: two-column on desktop for paired Bangla and English fields, one column on phones; a sticky save bar at the bottom of long editors showing the dirty state.
- Filters: one filter bar pattern (search on the left, filters next to it, reset link), the same on every list.
- Sidebar: active item clearly marked, badges aligned, the role chip under the logo.

### P6. Legacy and consistency clean-up (P0)

- Replace the raw `zinc` colors in `components/form-error.tsx`, `components/locale-switcher.tsx` and `components/require-auth.tsx` with tokens, or stop using these components in the exam site (check every import; the old course admin may keep its own).
- Remove dead code left from the build phase: unused components, unused message keys, leftover `_tmp` files.
- Bangla pass over every message file against [bangla-copy.md](bangla-copy.md) (forbidden terms, one term per concept, Bengali digits through the formatters).

## Part 4 — Dev-server guide for Shahriar's manual QA (P0)

Write `docs/exam-site/reports/ui-dev-qa-guide.md` and **verify every step boots** (start the API and `next dev`, check with `curl` that `/`, `/mcq`, `/staff` and `/api/v1/public/live-exam-categories` answer, then stop them):

1. Database: create `coaching_center_qa` locally (exact `psql` or pgAdmin steps), never the test database.
2. API: the env values to set for a local run on the `dev` profile (names only, with example non-secret values): `DB_URL` to `coaching_center_qa`, `SEED_DEMO=true` and the `SEED_*` accounts, `DEV_EXPOSE_EMAIL_CODES=true` (codes appear in the API log), `MASTER_ADMIN_*`, `WEB_ORIGIN=http://localhost:3000`. The command to start it from `api/`, and what the log shows when the seed has run.
3. Web: `web/.env.local` with `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080` and both site URLs `http://localhost:3000`; `npm run dev`; the URLs to open.
4. Accounts to sign in with (which env names hold them), and where verification and reset codes appear.
5. How to make an exam live now (staff exam editor, set the start a few minutes ahead and a short duration) to test sitting, deadline, review and leaderboard.
6. How to view the split-host setup locally (`site.localhost` and `admin.localhost`).
7. A typography and visual checklist matching Part 3 (type scale values per style, spacing, states, dark mode, 320 / 375 / 768 / 1024 / 1440 px), so Shahriar can tick it off page by page.
8. Known limits on a local run: no SSLCommerz checkout (no public callback), mail is logged not sent.

## Checks and report

- API: touched `*IT` classes, then the full suite from clean, alone, under the lock; only `BookOrderIT` and `CoursePurchaseIT` may fail.
- Web: `npm run lint` 0 errors, `npx tsc --noEmit` clean, `npm test` green, `npm run build` under the lock (and `npm run build` again after F8 if it was done).
- Write a **Report** section at the end of this file: each G, F and P item as done, partly done, or skipped (with the reason), the new or changed API fields, screenshots are not expected.

Then send the integrator:

```
READY TO MERGE: ui/ws9-gaps-polish (agent G)
Last commit: <hash>
Checks: API suite <n> run, only BookOrderIT and CoursePurchaseIT failing; web lint 0 errors, tsc clean, tests <n> passed, build ok
Gaps closed: <G ids> | Features: <F ids> | Polish: <P ids>
Skipped: <ids and why>
API additions: <list>
Dev QA guide: docs/exam-site/reports/ui-dev-qa-guide.md (boot verified)
```
