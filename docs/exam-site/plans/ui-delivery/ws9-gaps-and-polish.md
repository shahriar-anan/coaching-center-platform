# WS9 — Close the gaps, finish the features, polish the UI

Written 25 October 2026 after every workstream (WS0–WS6b) was merged into `catalog-acquisition` and the merged code was green (API suite 315 run, only `BookOrderIT` and `CoursePurchaseIT` failing; web lint 0 errors, `tsc` clean, 222 tests, build ok with 69 routes). **One agent** does this whole file. After it is merged, Shahriar does the typography review and the manual QA himself on the dev server, using the guide in Part 4. [ws8-final-qa.md](ws8-final-qa.md) stays available as an optional automated pass later.

Read first: [README.md](README.md) (architecture, standards, LMS practices), [bangla-copy.md](bangla-copy.md), [ws0-foundation.md](ws0-foundation.md) (tokens, components, the Report with the real contracts), [../api-completion/README.md](../api-completion/README.md) (API practices), [../../current-api.md](../../current-api.md).

## What the analysis found (25 October)

- **Two Blockers found in a second review (Part 5): staff cannot open the written question PDF (the endpoint is student-only), and there is no exam price field anywhere in the staff UI.** Fix these first.
- Usability by role (Part 5): the instructor has no marking queue, no recent papers and no category browsing; the exam creation journey has no overview of its fourteen steps; students cannot search or see upcoming exams across modules.
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

## Part 5 — Usability audit by role (added after a second code review, 25 October)

Shahriar asked for a site that is easy to use for every user type, and stated two needs explicitly: an instructor must be able to browse the exams and pick the papers to mark, with recent papers easy to reach; and an admin must be able to create an exam through every step without guessing. The code review below found real gaps. Items are numbered G8+ (API) and U1+ (UI). **Blocker** items come before everything else in this file.

### Findings from the code review

| # | Finding | Where | Severity |
|---|---|---|---|
| R1 | **Staff cannot open the written question PDF.** `GET /api/v1/live-exams/{id}/question-pdf` calls `requireStudent()`, but the staff written editor ("দেখুন") and the student preview call it, so they fail with 403. The admin cannot check the file they uploaded, and an instructor cannot see the question paper while marking. | `LiveExamSittingController`, `written-paper.tsx`, `exam-editor.tsx` | Blocker |
| R2 | **No exam price in the staff UI.** The API supports a price on each exam (single-exam purchase), and the student site offers "পরীক্ষাটি কিনুন", but `exam-meta-form.tsx` has no price field and the module exam table shows none. The client cannot sell a single exam. | exam editor, module exams tab | Blocker |
| R3 | The instructor cannot read the category tree (`GET /live-exam-categories` needs `EXAM_READ`, which instructors lack), and module rows carry only `categoryId`, so the instructor cannot browse by category. | API, instructor home | Major |
| R4 | The staff submission rows have no time fields (`submittedAt`, `evaluatedAt`, `publishedAt`), and submissions can only be listed per module. There is no way to show "recently submitted papers" or sort by time. | `LiveExamEvaluationService.SubmissionView` | Major |
| R5 | The instructor home is a flat card per module with three counts. No queue of papers waiting, no "start marking", no per-exam view, no category grouping. | `instructor-home.tsx`, `/staff/submissions` | Major |
| R6 | Exam creation is one long page per exam with a checklist that covers only the exam itself (final save, pass percent, questions or PDF). Nothing shows the whole journey: category, module details, module final save and publish, price, each exam's readiness, the teacher, direct enrolment, and whether students can actually see and buy it. | module and exam editors | Major |
| R7 | The module exam table shows name, type, language, date and status, but not price, duration, question count or PDF readiness, so an admin cannot see at a glance what is unfinished. | `module-exams.tsx` | Minor |
| R8 | Students have no search and no cross-module "upcoming exams" view; to find the next exam they must drill down category by category. | doors | Minor |

### API work

| # | Change | Priority |
|---|---|---|
| G8 | `GET /api/v1/live-exams/{id}/question-pdf` also serves staff: Master Admin and System Admin (`EXAM_READ`), and an Instructor only for an exam in a module assigned to them. The student rule (only during their own open sitting) stays exactly as it is. Tests for each role, including an unassigned instructor (404) and a student outside the window (unchanged refusal). | **Blocker** |
| G9 | No API change for price: the exam `PATCH` already takes `price` (Master Admin only; System Admin gets 403 when the body includes it). Confirm in `LiveExamService` and the existing tests, then build U5. | **Blocker** (UI) |
| G10 | Staff module rows (`GET /api/v1/live-exam-modules`, list and single) carry `categoryPath`: the chain from the root to the module's category, each `{ id, titleEn, titleBn }`. Instructors get it for their assigned modules, so they never need the category endpoint. | P0 |
| G11 | A cross-module submission feed: `GET /api/v1/live-exam-submissions?status=&moduleId=&examId=&q=&sort=&page=&size=` for staff with evaluate or read permission (Instructor: assigned modules only; Master Admin and System Admin: all). Rows: `participantId`, `examId`, exam titles, `moduleId`, module titles, `categoryPath`, `studentName`, `status`, `kind`, `answerPdf`, `submittedAt`, `evaluatedAt`, `publishedAt`, `evaluatorName` (not for students; this is staff-only). `status` filter values: `AWAITING` (answer stored, no draft), `DRAFT`, `PUBLISHED`, `FAILED_TO_SUBMIT`. `sort`: `submittedAt,desc` (default), `submittedAt,asc`, `studentName`. Written exams only. Also add `submittedAt`, `evaluatedAt` and `publishedAt` to the existing per-module submission rows. Add the route to `AuditLogIT`. | P0 |
| G12 | Students: `q` (title in either language) on `GET /api/v1/public/live-exam-modules`, and `GET /api/v1/public/live-exams?examType=&upcoming=true&size=` returning published, dated, not-yet-ended exams across published modules, soonest first, with module titles. | P1 |
| G13 | Copy an exam: `POST /api/v1/live-exams/{id}/copy` (Master Admin, System Admin) creates a draft in the same module with the same texts, settings and questions (or the same question PDF), no date, no price, title with "(কপি)" / "(copy)". | P2 |

**Business decision for Shahriar, not built:** instructors choosing their own modules. Today the Master Admin assigns one teacher per module (decided). The plan keeps that and gives the instructor full browsing and choice *within* their assigned modules. The instructor empty state says "আরও মডিউলে মূল্যায়ন করতে মাস্টার অ্যাডমিনের সাথে যোগাযোগ করুন।".

### Instructor (U1–U3)

**U1. Instructor home becomes a marking dashboard (P0).**

1. A summary strip: খাতা বাকি (answers waiting for a mark), খসড়া (drafts not yet published), প্রকাশিত (published), from G11 counts or the participation summary.
2. A primary button "মূল্যায়ন শুরু করুন" that opens the oldest waiting paper (G11 with `status=AWAITING&sort=submittedAt,asc&size=1`), disabled with "এখন কোনো খাতা বাকি নেই" when the queue is empty.
3. **সাম্প্রতিক খাতা**: the ten most recently submitted papers across the instructor's modules (G11, newest first): student, exam, module, submitted time (relative, "২০ মিনিট আগে"), status pill, each row opens the paper. "সব খাতা দেখুন" goes to `/staff/submissions`.
4. **আমার মডিউল**: the assigned modules **grouped by category path** (G10), each module a card with its counts and its written exams listed underneath, each exam with its own waiting count and a "খাতা দেখুন" link to that exam's queue.
5. Empty state for an instructor with no modules, with the sentence above.

**U2. A browsable submissions area (P0).**

- `/staff/submissions` becomes a two-pane browser on desktop (one column on phones): on the left, the category → module → exam tree for the modules the user may mark (G10), each node with its waiting count; on the right, the paper list for the selected node from G11, with filters (বাকি / খসড়া / প্রকাশিত / জমা হয়নি / সব; default বাকি for instructors), sort (নতুন আগে, পুরনো আগে, নাম), search by student name, and the submitted time column. Selection, filters and sort live in the URL.
- The existing per-module page (`/staff/submissions/m/[moduleId]`) stays working as a deep link (it can render the same browser preselected).
- Master Admin and System Admin get the same browser over all modules, with the teacher's name column.

**U3. The marking screen (P0).**

- Three views on desktop: the answer PDF, the **question paper** (G8) in a tab next to it, and the mark form; phones get tabs.
- The form shows the maximum, prefills the saved marks and comment (G1), shows the stored marked PDF with "দেখুন" and "বদলান", and states the save rule plainly.
- Keep the queue navigation and shortcuts; add a small "কীবোর্ড শর্টকাট" popover listing them.
- After the last paper in the current filter: "এই তালিকার সব খাতা দেখা হয়েছে" with a link back to the browser.

### Master Admin and System Admin: the exam creation journey (U4–U9)

The full journey an admin must be able to complete in the UI, in order. **Every step must exist, be reachable from the previous one, and show whether it is done:**

| Step | What the admin does | Who | Where |
|---|---|---|---|
| 1 | Create or choose the category (and subcategory) | MA, SA | categories |
| 2 | Create the module: titles, descriptions (both languages), category | MA, SA | module details |
| 3 | Set the module price (optional; no price means it cannot be sold as a module) | MA | module details |
| 4 | Final-save the module (চূড়ান্ত করুন), then publish it (প্রকাশ করুন) | MA, SA | module details |
| 5 | Add an exam: choose MCQ or written (fixed after this) | MA, SA | module exams tab |
| 6 | Exam details: titles, descriptions, rules (both languages), paper language | MA, SA | exam editor |
| 7 | Schedule and marks: start date and time (or "তারিখ এখনো ঠিক হয়নি"), duration, pass percent; written also maximum marks | MA, SA | exam editor |
| 8 | Content: MCQ questions (stem, 2–6 options, one correct, marks, explanation; reorder; total marks shown), or the written question PDF (upload, view, replace) | MA, SA | exam editor |
| 9 | Exam price (optional; needed to sell this exam alone) | MA | exam editor |
| 10 | Preview as a student | MA, SA | exam editor |
| 11 | Final-save the exam, then publish it; the five-minute clash names the other exam | MA, SA | exam editor |
| 12 | Assign the teacher (needed before written answers can be marked) | MA | module teacher tab |
| 13 | Direct enrolment for students who should sit without paying (optional) | MA | module direct-enrolment tab |
| 14 | Check what students see: a link that opens the public module page | MA, SA | module and exam editor |

**U4. A setup-progress panel on the module page (P0).** At the top of `/staff/modules/[id]`, a compact stepper or checklist of steps 2–14 for this module: each step shows done, missing, or "মাস্টার অ্যাডমিন করবেন" (for a System Admin looking at a Master-Admin-only step), and links to the exact field or tab. It summarises the exams: "৪টি পরীক্ষা · ৩টি প্রকাশিত · ১টি খসড়া (প্রশ্ন নেই)". The last line says plainly whether students can see and buy the module now, and why not if not (module not published, category archived, no price on module or any exam). Use one shared `Stepper`/checklist component in `components/ui`.

**U5. The exam editor as clear numbered sections (P0).**

- Sections in the journey order, numbered: ১ বিবরণ, ২ সময় ও নম্বর, ৩ প্রশ্ন (or প্রশ্নপত্র), ৪ মূল্য, ৫ যাচাই ও প্রকাশ. On desktop a sticky section nav on the side shows each section's state (done / missing / error) and jumps to it; on phones the sections are collapsible with the same states in their headers.
- **৪ মূল্য:** the exam price input for the Master Admin (validation like the module price: blank or above zero with at most two decimals; "দাম নেই" when blank), read-only text for the System Admin (G9). The publish checklist mentions price as optional with what it means ("দাম না থাকলে পরীক্ষাটি আলাদা করে বিক্রি হবে না; মডিউল কিনলে পাওয়া যাবে").
- The MCQ section shows a running summary: "১২টি প্রশ্ন · মোট ৫০ নম্বর · ২টিতে সঠিক উত্তর বাছাই হয়নি", and jumps to the first incomplete question.
- The written section views the stored PDF (G8) and shows the size limit (G6).
- The save model is explicit: each section saves on its own button, a dirty section shows "সেভ হয়নি", the leave guard (F2) covers in-app links, and the helper "প্রকাশিত অবস্থায় সেভ করলে এটি আবার খসড়া হয়ে যাবে।" sits next to every save while published.
- After publish: the next-steps toast (F3) and, for a written exam in a module with no teacher, a warning "এই মডিউলে শিক্ষক দেওয়া নেই — খাতা মূল্যায়নের আগে শিক্ষক দিন" linking to the teacher tab.
- A "শিক্ষার্থীর চোখে দেখুন" link (via `siteHref`) to the public exam page once published.

**U6. The module exam table shows readiness (P0).** Columns: name, type, date and time (or "তারিখ পরে জানানো হবে"), duration, price (or "দাম নেই"), content ("১২টি প্রশ্ন" / "প্রশ্নপত্র আছে" / "প্রশ্ন নেই" in the warning tone), status pill, and actions (edit, preview, participation, leaderboard, and copy when G13 exists). Rows sorted by date, undated last.

**U7. First-run onboarding (P1).** When the catalog is empty (the client after cleanup), the Master Admin and System Admin home shows "প্রথম পরীক্ষা তৈরি করুন" with the steps 1 → 2 → 5 as three linked cards, instead of empty tables.

**U8. Staff quick search (P1).** A search field in the staff top bar (Master Admin and System Admin): typing a phone, email or name offers students (`GET /students?q=`) and modules (G5 `q`), each result opening its page; `/` focuses it.

**U9. System Admin parity (P0).** Walk the whole journey as a System Admin: every Master-Admin-only step is shown as information, never as a dead button, and nothing the server refuses is offered.

### Student (U10–U12)

**U10. Find exams faster (P1).** A search field on the door pages (G12 `q`), and an "আসন্ন পরীক্ষা" strip on each door (G12 upcoming, filtered by the door's type): title, module, date and time, price or "চালু আছে", each linking to the exam page.

**U11. Module page "কী পাবেন" summary (P1).** Under the description: "৫টি এমসিকিউ · ২টি রিটেন · পরবর্তী পরীক্ষা ১২ নভেম্বর" from the counts already on the page.

**U12. A copy-link button on the exam page (P2)**, so a student can share an exam with a friend.

### Style rules for everything in Part 5

- Build only from the shared components in `components/ui` and the shells. If a pattern is new (the stepper, the two-pane browser, the summary strip), add it once to `components/ui` with props, tokens and a test, then use it everywhere it applies.
- Staff screens keep the admin look: dense, calm, tables and side panels, 40 px targets, the named type scale from Part 3 P1. Student screens keep the mockup look.
- Every new list has loading, empty, error and success states; every count comes from the server; every time is Asia/Dhaka with Bengali digits in Bangla; every new string is written by the copy guide (খাতা, মূল্যায়ন, খসড়া, প্রকাশিত, সরাসরি ভর্তি, শিক্ষক, চূড়ান্ত করুন).
- Every new screen works at 375 px and 1440 px, in both languages and themes, and by keyboard.

## Part 6 — Core functions by role (third review, against the client brief and the decisions)

Method: every requirement in [../../client-brief.md](../../client-brief.md) sections 4–6 and every rule in [../../decisions.md](../../decisions.md) was checked against the merged UI, and every exam-site API endpoint was checked for a caller in `web/src`. **Endpoint coverage is complete** (every endpoint the UI needs is called). The gaps are missing features and workflows, listed here as C-items.

### Core matrix

| Role | Core function (source) | UI today | Gap |
|---|---|---|---|
| Student | Register and log in with email or phone, no OTP (brief 4.1) | done | — |
| Student | Google sign-in (brief 4.1) | button disabled until configured (U10 decision) | config only; `NEXT_PUBLIC_GOOGLE_ENABLED` turns it on |
| Student | Bangla / English switch (4.1) | done | — |
| Student | Home with MCQ and Written entry; type → category → subcategory → module (4.2) | done | U10, U11 improve it |
| Student | Module page with description, routine, rules, full exam list with status; no questions before purchase (4.2) | done | — |
| Student | Buy one exam or the whole module; module covers past and future exams (4.3) | done (student side) | **R2: no exam price in staff UI, so single-exam sale cannot be configured** |
| Student | Dashboard: purchases, live now, reminders, result notices (4.4) | done | — |
| Student | Take exam only in the window, server-enforced; paper styled formally; PDF inline, watermark, no download (4.5) | done | — |
| Student | Written: PDF only, resubmit until the deadline, no retake (4.6) | done | — |
| Student | MCQ: automatic marking; one practice attempt per decisions (4.6, decisions) | done | — |
| Student | Past exams in a bought module (4.7) | MCQ practice attempt done; past written paper not viewable after the window | **decision needed (D-W1)**, see below |
| Student | Results after publication, per-exam leaderboard with names, history (4.8) | done (results hub, leaderboard, own row) | — |
| Instructor | Credentials created by the admin (5) | done | — |
| Instructor | Only assigned exams and their submissions (5) | done | U1–U2 make it usable |
| Instructor | Open the answer PDF, mark it on a device, upload the marked PDF, enter marks, save as draft, cannot publish (5) | done | U3, G1 (saved comment), G8 (question paper) |
| Admin | Manage categories, subcategories, modules, exams (6) | done | U4–U6 make the journey clear |
| Admin | MCQ correct answers at creation; exam language; text or PDF content (6) | done | — |
| Admin | Create instructors and assign them (6) | done | G4 |
| Admin | Review and publish results (6) | done one paper at a time | **C3: publish many at once** |
| Admin | **Analytics: who is taking exams, how many, participation and results (6)** | participation counts only; no per-exam results list, no averages or pass rate | **C1: exam results report** (no API exists) |
| Admin | Payment permissions: System Admin cannot change prices or financial records (6) | done (server and UI) | R2 adds the Master Admin exam price |
| Admin (support) | Answer "what did this student sit and score?" | student page shows access only | **C2: student exam history** (no API exists) |

**Decision D-W1 for Shahriar (not built):** the brief assumes a past written paper stays viewable to a buyer after its window; the decisions say the question PDF is shown only during the student's own sitting. Today the decisions apply. If Shahriar wants past written papers viewable after the window (without a new submission), that is one API rule change in the question-PDF read plus a "প্রশ্নপত্র দেখুন" button on the exam page for ended written exams.

### New core items

| # | Item | API | UI | Priority |
|---|---|---|---|---|
| C1 | **Exam results report** for staff. | G14: `GET /api/v1/live-exams/{id}/results?kind=&status=&q=&sort=&page=&size=` for Master Admin and System Admin (all exams) and the assigned Instructor (written exams in their modules). Rows: student name, student code, status, score, maximum, passed, kind (live or practice), started, submitted, published (written). A `summary` object: participants, submitted, failed to submit, average score, highest, lowest, pass count and pass rate (live sittings only; practice attempts counted separately). Never phone numbers for the Instructor. Tests per role. | New route `/staff/exams/[examId]/results` (owned by the staff-ops stage): summary strip on top, then the table with filters and sort; linked from the module exam table, the participation rows and the exam editor. Numbers only, no charts (admin-prompt rule). | P0 |
| C2 | **Student exam history** on the staff student page. | G15: `GET /api/v1/students/{id}/live-exam-results?page=&size=` for Master Admin and System Admin: each sitting with exam and module titles, kind, status, score, passed, submitted time, published flag. | A "পরীক্ষার ইতিহাস" section under the access list on `/staff/students/[id]`, newest first, each row linking to the exam results report. | P0 |
| C3 | **Publish many written results at once** (Master Admin). | G16: `POST /api/v1/live-exams/{id}/publish-results` publishing every draft of that exam in one transaction, returning the count; audit row per participant (same as single publish), student notices as today. Tests: only drafts are published, Instructor and System Admin refused, notices written. | On the submissions browser and the results report: "সব খসড়া প্রকাশ করুন (১২টি)" with a confirm dialog naming the exam and the count; also row checkboxes for "নির্বাচিতগুলো প্রকাশ করুন" (calls the single publish per row with progress). | P0 |

## Part 7 — User-friendly features (beyond the core)

These come from how students, teachers and coaching-centre admins actually use exam and LMS platforms. Each is small and built from shared components.

| # | Role | Feature | Priority |
|---|---|---|---|
| UF1 | Student | **Payment receipt page**: from payment history, "রসিদ দেখুন" opens a printable receipt (site name, student, item, amount, method, transaction number, date and time, status) with print styles; data from `GET /me/live-exam-orders`, no new API. | P1 |
| UF2 | Student | **Result detail polish**: on the MCQ review, a filter "সব / ভুল / সঠিক / উত্তর দেননি" and a jump grid colored by outcome; on the results hub, a small per-exam pass line ("পাস নম্বর ৪০%"). | P1 |
| UF3 | Student | **Empty-state guidance everywhere a new student lands**: home, my modules, results, notices, routine each link to the doors with one sentence on what to do next. | P0 |
| UF4 | Instructor | **Comment snippets**: save frequently used comments on the device and insert them with one click on the marking form. | P2 |
| UF5 | Instructor | **Today's work line** on the dashboard: "আজ ১২টি খাতা মূল্যায়ন করেছেন" (from G11 rows with `evaluatedAt` today and the instructor as evaluator). | P2 |
| UF6 | Admin | **Exam agenda** on the staff home: the next 14 days of dated exams across all modules grouped by day, each with its module, time, status and participant count, so clashes and gaps are visible (participation list sorted by date). | P1 |
| UF7 | Admin | **Publish confirmation summary**: before publishing an exam, a dialog that restates what students will get ("সিআরপিসি · এমসিকিউ · ১২টি প্রশ্ন · মোট ৫০ নম্বর · ১২ নভেম্বর দুপুর ১২:০০ – ১২:৪০ · মূল্য ৳৪০০"). | P0 |
| UF8 | Admin | **Edit in context**: from any list (module exams, results report, participation), a row menu with the next likely action (edit, results, leaderboard, publish, copy), consistent everywhere. | P1 |
| UF9 | All staff | **Remember list preferences** (page size, last filter) per device, on top of the URL state. | P2 |
| UF10 | All | **Session-safe forms**: long staff forms keep an unsent draft in `localStorage` (per record) and offer "সেভ না করা পরিবর্তন ফিরিয়ে আনুন" after an accidental reload. | P2 |

## Order of work (whole file)

1. **Blockers:** G8 (staff question PDF) and U5's price section with G9 (exam price). When several agents run, follow [ws9-stages.md](ws9-stages.md) instead of this list.
2. Part 3 P1 type scale and P6 clean-up.
3. G1, G2, G3, G4, G7, G10, G11, and the Part 6 core items C1, C2, C3 with their API (G14, G15, G16).
4. U1, U2, U3 (instructor), then U4, U5, U6, U9 (exam creation journey).
5. The rest of Part 3 (P2–P5) across all screens, including the new ones.
6. Part 2 P0 (F1, F2) and Part 7 P0 (UF3, UF7), then the P1 items in Parts 1, 2 and 5 (G5, G6, G12, U7, U8, U10, U11, F3–F6, F10), then P2 (G13, F7–F9, U12). Part 7 P1 items (UF1, UF2, UF6, UF8) go with the other P1 items; UF4, UF5, UF9, UF10 with P2.
7. Part 4, the dev-server guide.

## Checks and report

- API: touched `*IT` classes, then the full suite from clean, alone, under the lock; only `BookOrderIT` and `CoursePurchaseIT` may fail.
- Web: `npm run lint` 0 errors, `npx tsc --noEmit` clean, `npm test` green, `npm run build` under the lock (and `npm run build` again after F8 if it was done).
- Write a **Report** section at the end of this file: each G, F, U and P item, and each finding R1–R8, as done, partly done, or skipped (with the reason), the new or changed API fields, screenshots are not expected.

Then send the integrator:

```
READY TO MERGE: ui/ws9-gaps-polish (agent G)
Last commit: <hash>
Checks: API suite <n> run, only BookOrderIT and CoursePurchaseIT failing; web lint 0 errors, tsc clean, tests <n> passed, build ok
Gaps closed: <G ids> | Features: <F ids> | Usability: <U ids> | Polish: <P ids>
Skipped: <ids and why>
API additions: <list>
Dev QA guide: docs/exam-site/reports/ui-dev-qa-guide.md (boot verified)
```

## Report — STU

**Done** (each one a commit naming the item id):
- **P1 type scale** on every student and auth page: no arbitrary `text-[..]`, no `text-sm`, `text-xs`, `text-base`, `text-lg` or `text-xl` left in `(site)`, `(auth)` and `components/student`. The hero uses `text-display`. Semantic classes come from FND's `@theme`; until FND merges they render at inherited size.
- **U11** module summary "কী পাবেন" from the counts the page already has, for this door's type, plus the next exam.
- **U12** copy-link button on the exam page, with a toast.
- **G7** student name edit on the account page (`PATCH /api/v1/me`, 1–100 characters, trimmed). A 404 shows "coming soon".
- **F6** registration helpers: phone grouped as typed (017 1234 5678), a check when valid, and a live count of the password characters still needed.
- **F5** text size control (A− / A+, 16 / 18 / 20 px) on the MCQ paper and the result review, remembered per device. Stems and options read one CSS variable.
- **UF1** printable payment receipt at `/account/payments/receipt` (from the paid rows of payment history; no new API).
- **UF2** result review filter (সব / ভুল / সঠিক / উত্তর দেননি), a jump grid coloured by outcome with the outcome in its accessible name, and a pass-mark line on the results hub.
- **UF3** empty states on home, routine, notices and payment history point to both doors, with one sentence on what to do next.
- **U10** module search on the door page (`q`, also filtered by title) and an upcoming-exams strip for the door's type. Both hide while the API lacks the G12 endpoints. Search text lives in the URL.
- **F10 (partly)**: tests for the name edit, registration helpers, text size, result outcome, door discovery and the receipt.

**Partly done**
- **Bangla pass**: the new strings were reviewed; existing student messages were not re-read.

**Not done**
- **P4 (student details)**: the sticky action bar on the exam page and the sitting bar need FND's `StickyActionBar`, which is not merged. The hero spacing, the 320 px checks and the dark-mode checks were not done.
- **F10 (rest)**: upload replace state, written deadline transition, offline flush, take and retake redirects, and the payment-result polling stop after 60 s.
- **UF1 print**: `PrintArea` is not used; the receipt hides its buttons with `print:hidden`, but the shell is not hidden in print until FND's print styles merge.

**API assumptions** (check against API's G12 and G7 when merged):
- `GET /api/v1/public/live-exams?examType=&upcoming=true&size=5` returns a page of exams with `moduleTitleEn` and `moduleTitleBn` on each row.
- `GET /api/v1/public/live-exam-modules?q=` returns a page of modules.
- `PATCH /api/v1/me` with `{ "fullName" }` returns `Me`.

**Contract changes:** none to FND or API contracts. `HubExam` now includes `passPercent`.

**Notes for other agents:** `components/ui/toast.tsx` (FND) still uses `text-[15px]`; it is FND's file, so it was not changed here.

**Checks:** lint 0 errors (2 existing `<img>` warnings); `tsc --noEmit` clean; `npm test` (single process) 238 passed in 35 files; `npm run build` under the lock: passed.

### Addendum — STU second pass

- **Read now:** `docs/exam-site/ui/client-mockup.html` and `docs/exam-site/ui/student-flow-prompts.md` (visual rules, standards, do-not-draw list). Checked: no negative-mark copy, no photo upload, no SMS code, and the login and reset copy follows the glossary (the invalid-code sentence in `bangla-copy.md` wins over the briefs).
- **P4, now done:** hero spacing 42 px top and 30 px bottom, gold left border on the countdown card, the exam page's primary action pinned on phones through a local `StickyActionBar` stand-in (`components/student/stand-ins/sticky-action-bar.tsx`; delete it and switch the import once FND merges), roomier paper options (12 px vertical padding).
- **P4, still open:** the sitting jump grid stays at 44 px cells (the README's student target), not the 40 px in P4. The results stat numbers in gold need FND's `Stat`, and the routine table's fade edge and the status pill's no-wrap need FND's `Pill` or a change to my `status-pill.tsx`. Dark-mode parity and the 320 px checks were not run on a device.
- **F10, now done:** the payment polling rule is extracted as `paymentPollDelay` and tested (3 s while pending, stops at one minute or at a final answer). The written answer replacement is tested (stored note waits until the new file is sent). **Not covered:** the deadline passing during an upload. The test harness raised the rejected `ApiError` from the test body; the case is left for a later pass.
- **F10, still open:** take and retake redirects, the offline flush beyond the existing MCQ backup test.
- **Bangla pass:** my student namespaces (`auth`, `account`, `catalog`, `purchase`, `result`, `sitting`, `notices`, `legal`) checked against the forbidden-term list; no changes were needed. `legacy`, `staff*` and FND's `common` are not STU's and were not touched.

**Checks:** lint 0 errors; `tsc` clean; `npm test` (single process) 242 passed in 37 files; the build is re-run under the lock after these changes (see the build line above).

### Addendum 2 — STU

- **F10, now done:** take and retake redirects (a live start opens the paper in `live` mode, a later attempt in `retake` mode) and the offline flush (answers kept while offline are sent once the connection returns).
- **F10, still open:** the deadline passing during a written upload (the test harness raised the rejected error from the test body; left for a later pass).
- **Build:** the app code has not changed since the build that passed under the lock (BUILD_EXIT 0); later commits only add tests and docs.
- **Checks:** `tsc` clean; lint 0 errors; `npm test` (single process) 245 passed in 39 files.


### Addendum 3 — STU, integrator review fixes

- **ST-1 (module search by door type):** the rule is now `searchMatches` (title match and modules with exams of this door's type; an unknown count from an older API is kept), tested directly.
- **ST-2 (upcoming price):** a row shows a price only when one is set. Module-only exams have no price field in the upcoming data, so they show no price line rather than "price not set". To show the right line, the API should send whether an exam is sold only inside a module.
- **ST-3 (receipt name):** the receipt shows the signed-in student's name.
- **ST-4 (name save):** field errors from the server show under the name field. A 405, or a bare 404 that the client maps to `INTERNAL_ERROR`, shows "coming soon". `ApiError` carries no HTTP status, so this matches on the status text; the cleaner fix is to put the status on `ApiError` in `client.ts` (not STU's file).
- **ST-5 (pills):** the status and type pills wrap in a no-wrap span.
- **ST-7 (sticky bar):** the exam page uses FND's `StickyActionBar`; the stand-in is deleted.
- **ST-8 (print):** the receipt sheet is wrapped in FND's `PrintArea`.
- **Notice bell:** `text-white` changed to `text-on-warn`.
- **Merge:** `catalog-acquisition` (with FND) merged; the one conflict was in this plan doc, and both report sections are kept.
### Addendum 4 — STU, open items fixed

- **Done:** the deadline passing during a written upload is now tested (the earlier harness problem did not come back). The result-page stat numbers are gold.
- **Still open, not STU's to fix:** the API flag for module-only exams (ST-2), and the HTTP status on `ApiError` in `client.ts`, which would replace the status-text match in `isNotDeployed`.
- **Still open, a decision:** the sitting jump grid stays at 44 px, the README's student target, instead of the 40 px in P4.

## Report — FND

Worktree `F:\cc-wt\w9-fnd`, branch `ui/w9-foundation`. Ready to merge after PUR-FND's P-8 lands (see the toast note below).

**Done**
- P1 type scale: named classes in `globals.css`; every arbitrary size and `text-sm`/`xs`/`lg`/`xl`/`2xl` in `components/ui` and `components/shell` uses the scale. Staff overrides on `data-surface="staff"`. The toast is already converted on this branch (see the note).
- P6, colours: `form-error`, `locale-switcher` and `require-auth` use tokens (no zinc or red).
- P6, dead code and unused keys: `components/ui/checkbox.tsx` removed (nothing imports it). 63 unused keys removed from the legacy catalog and 21 from `common`, in bn and en. Each key was checked against every string literal in `src` and `tests`, including template keys. No temporary files (`_tmp`, `.bak`, `.orig`) were found.
- Contracts, all real, each with tests: `Stepper`, `SummaryStrip`, `SplitBrowser` (phones stack the tree above the pane), `RowMenu`, `ShortcutsPopover`, `SectionNav`, `StickyActionBar` (above the student tab bar; reserves page space on phones), `PrintArea` (print rules in `globals.css`).
- F2: `useUnsavedChangesGuard(dirty, message?)` covers tab close, same-site link clicks (captured; navigation is held until the shared `ConfirmDialog` confirms) and browser back. Both shells mount the dialog.
- F1: notices tab badge from the shared unread query; the tab name includes the count for screen readers.
- U8: staff quick search in the top bar (students by `q`, modules by G5 `q`), keyboard navigation (↑ ↓ Enter, `aria-activedescendant`), `/` focuses it, translated module status, and a phone button that opens the same search in a dialog.
- UF9 and UF10: `useListPrefs` and `useDraftForm`. A draft is kept while the user types and until restore or clear. Storage is read through `useSyncExternalStore`, so server and client renders match.
- P2 and P3 (shared components): button sizes sm 40, md 44 (default), lg 48; spinner over the label with no width change; active and aria-disabled states; state classes on icon buttons, text inputs, checkboxes and tabs; skeleton height prop. Pagination and copy-field get their hover and active states from the shared `Button` (secondary variant). The file drop zone has a hover border; the select has a hover background and an active border.
- F8: fonts are self-hosted from `web/src/app/fonts` with `@font-face` rules (unicode-range) in `globals.css`. The same families, weights and subsets as before. `next/font/local` cannot express unicode-range, so the faces are declared directly.
- Integrator audit, FN-1 to FN-11: one commit each.

**Still open, for the owners (not touched by FND)**
- Unused message keys in other namespaces (48): `account.payments.*`, `auth.setPhone.saved`, `auth.google.done`, `auth.google.needPhone` (STU); `catalog.*` (STU); `notices.*` (STU); `purchase.amount`, `purchase.result.loading` (STU); `result.*` (STU); `sitting.*` (STU); `staffCatalog.categories.edit` (AUT); `staffOps.paymentDetail.student`, `staffOps.account.verifyTitle` (OPS); `staffPeople.participation.openSubmissions`, `staffPeople.submission.commentPlaceholder` (MRK or OPS, by owner of `staffPeople`). The scan is in the agent's scratch folder and can be rerun.
- Unused exports in `lib`: `useOrder` (`lib/api/exam/purchase.ts`, STU), `ApiErrorCode` (`lib/api/exam/types.ts`), `TIME_ZONE` (`lib/format`), `recordServerTime` and `clockOffset` (`lib/time/clock.ts`). Their owners decide.
- `components/student/notice-bell.tsx` (STU's file) still has `text-white` on its badge; STU should use `text-on-warn`, the token added in FN-10.

**Toast (P-8, PUR-FND)**
- `components/ui/toast.tsx` already uses `text-label` on this branch (the P1 commit `a453d4c`). PUR-FND's P-8 change to the same line will conflict. PUR-FND must rebase on `ui/w9-foundation` or take this line as it is. Nothing else in `toast.tsx` changed.

**Checks (web, this worktree, head of the P6 follow-up)**
- `npx tsc --noEmit`: clean.
- `npm run lint`: 0 errors, 2 warnings (the existing `<img>` ones).
- `npx vitest run`: 38 files, 269 tests passing.
- `npm run build`: see the ready message for the run under the lock.

**Contract changes**
- None to existing props. Additive only: `Button` gets `size`; `Skeleton` gets `height`; `useUnsavedChangesGuard` keeps its signature and the shells mount `UnsavedChangesDialog`.
- `useListPrefs(key)` returns `[prefs, setPrefs]`. `useDraftForm<T>(key, initial)` returns `{ value, setValue, hasDraft, restore, clear }`.


- `common` gains `shortcuts`, `sectionNav`, `quickSearch`, `unsaved` and `unreadNotices` (bn and en). `common` loses 21 unused keys, and `legacy` loses 63 (P6).
- `components/ui/checkbox` is removed. It had no users.

## Report — API

Branch `ui/w9-api`, worktree `F:\cc-wt\w9-api`. Checks: full suite from clean, alone, under the lock: 322 run, only `BookOrderIT` and `CoursePurchaseIT` failing (both expected, not caused by this work). `LiveEvaluationIT` 44 of 44 green. `LiveExamIT`, `LiveSittingIT`, `LivePublicCatalogIT`, `LiveExamPurchaseIT`, `AuditLogIT` green in the targeted run. Dev guide boot-verified (API on `coaching_center_qa`, web `/`, `/mcq`, `/staff` 200).

| Item | Status | Note |
|---|---|---|
| R1 / G8 | done | Staff (Master Admin, System Admin, assigned instructor) read the written question PDF. Unassigned instructor 404. Student rule unchanged. |
| G1 | partly done | The read returns `comment` and `evaluatedFileName`. Not changed: the API still requires a new marked PDF on every save (the current rule). The form must say so. |
| G2 | done | Exam and module titles, ids, `maximum` on the submission read. |
| G3 | done | Clash 409 carries `details` `{ id, titleEn, titleBn, liveAt, endAt }`. Error body gains an optional `details` field, present only there. |
| G4 | done | Staff module read returns `instructor` (`id`, `name`, `phone`) or `null`. Phone is staff-only. |
| G5 | done | Staff list takes `status` and `q`, rows carry `mcqCount` and `writtenCount`. Server paging was already there. URL state is the web's. |
| G6 | partly done | Real limits found and documented in `current-api.md` (question PDF 100 MiB via `MATERIAL_MAX_BYTES`, answer and marked PDF 80 MiB, multipart 100 MB, Nginx 105m). Over-cap upload now returns 413 `FILE_TOO_LARGE` (was 500). Client helper text and message are the web's. |
| G7 | done | `PATCH /api/v1/me` `{ fullName }` for students, 1 to 100 characters after trim. Staff get 403. |
| G8 | done | See R1. |
| G9 | done (no change) | Confirmed: only Master Admin holds `EXAM_PRICE`; System Admin sending a price gets 403. |
| G10 | done | Staff module rows and single read carry `categoryPath` (root first). Instructors get it for assigned modules. |
| G11 | done | `GET /api/v1/live-exam-submissions` (cross-module feed). Instructor scope enforced in the service. Open sittings are excluded; a sitting shows once the deadline job posts it. |
| G12 | done | Public `q` on modules. `GET /api/v1/public/live-exams?upcoming=true` (soonest first, ended exams dropped, no questions or PDFs). |
| G13 | done | `POST /api/v1/live-exams/{id}/copy`, 201, draft, no date, no price. MCQ questions and the question PDF (as a new stored file) are copied. |
| G14 / C1 | done | `GET /api/v1/live-exams/{id}/results`, body `{ summary, rows }`. Summary counts live sittings only; a score counts once final, as on the leaderboard. |
| G15 / C2 | done | `GET /api/v1/students/{id}/live-exam-results` (Master Admin, System Admin). |
| G16 / C3 | done | `POST /api/v1/live-exams/{id}/publish-results` (Master Admin only), returns `{ published: n }`, one audit row and notice per participant. The single publish now writes the same audit row (it did not before). |
| R3 | done | Through G10 (`categoryPath`) on the module reads. |
| R4 | done | Submission rows carry `submittedAt`, `evaluatedAt`, `publishedAt`. |
| R8 (API part) | done | Through G12. |
| Part 4 | done | `docs/exam-site/reports/ui-dev-qa-guide.md`, boot-verified. Visual and typography pass is Shahriar's, per Part 4 item 7. |
| Skipped | none | |

**API additions (for the integrator and the web agents):**
- Error body: optional `details` (only the clash 409).
- `SubmissionView` gains `comment`, `evaluatedFileName`, `moduleId`, `moduleTitleEn`, `moduleTitleBn`, `examTitleEn`, `examTitleBn`, `submittedAt`, `evaluatedAt`, `publishedAt`.
- `ModuleView` gains `categoryPath`, `instructor`, `mcqCount`, `writtenCount`; null on student and public reads, and on create, update and publish responses.
- New routes: `GET /api/v1/live-exam-submissions`, `GET /api/v1/live-exams/{id}/results`, `GET /api/v1/students/{id}/live-exam-results`, `POST /api/v1/live-exams/{id}/publish-results`, `POST /api/v1/live-exams/{id}/copy`, `GET /api/v1/public/live-exams`, `PATCH /api/v1/me`. All in `AuditLogIT`.
- Changed: `GET /api/v1/live-exam-modules` accepts `status` and `q` (staff); `GET /api/v1/public/live-exam-modules` accepts `q`; `GET /api/v1/live-exams/{id}/question-pdf` serves staff.
- No Flyway migration. No business rule changed.

**Contract changes for the integrator:** the `details` error field, the new `SubmissionView` and `ModuleView` fields above, the `{ summary, rows }` results shape, and `PATCH /api/v1/me`.

### Addendum 5 — STU, WS9 follow-up

- **STU-FU-1 Bangla pass** (`auth`, `account`, `legal`, `help`, `contact`): the logout title now uses the glossary term লগআউট; the contact phone number is in Latin digits, as the copy guide asks for phone numbers. Everything else in those files matched the forbidden-term list and the glossary.
- **STU-FU-2 320 px and dark mode, by reading the code:** one finding, fixed. The MCQ option text now wraps inside its row (`min-w-0`, `break-words`), so a long option no longer pushes the row wider than a 320 px screen. No fixed widths overflow (the paper column is a `max-w-[760px]` cap). The dialog scrim uses `bg-black/40` on purpose: it darkens both themes equally, and FND has no scrim token. Not checked without a device: the sitting top bar at 320 px, long bilingual titles in the dialogs, and the dark-mode contrast of the gold on the paper.
- **STU-FU-3:** the sitting score figure uses `text-section` (20 px, the mockup's stat size) in place of `text-2xl`.
- **STU-FU-4 (decision, pending user confirmation):** the sitting jump grid stays at 44 px, the README's touch target. Accepted by STU; pending user confirmation.
- **Not touched, per the follow-up's list:** `components/student/catalog/**`, `purchase/**`, `notice-bell.tsx`, the notices page, `result/**`, `home/**`, the listed `lib/api/exam` files, and the messages `catalog`, `purchase`, `notices`, `result`, `sitting`. ST-2 and ST-4 are not STU's.

## Report — API-ADMIN

Branch `ui/api-admin`, worktree `F:\cc-wt\api-admin`. Merged `catalog-acquisition` first (includes V39 at `3fa6238`). V39 itself was not changed.

Checks: full suite from clean, under the lock: 334 run, 2 failures, both expected: `BookOrderIT`, `CoursePurchaseIT`. One more error on HEAD, outside this work: `StudentAccessIT.staffSeeEveryReasonAStudentCanSitAndNobodyElseCan` inserts a grant with raw SQL and no `source`. V39 dropped the column default, so the insert fails. That file belongs to API-ACCESS (A1), and the fix is theirs; API-ADMIN left it as it is. Touched `*IT` classes green in targeted runs: LiveGrantAdminIT (9), LiveExamPurchaseIT (15), LiveSittingIT (31), LivePublicCatalogIT (5), ExamMailIT (13), LiveEvaluationIT (46), AuditLogIT (3), AuthorizationIT (5), AuthorizationTest (4).

| Item | Status | Note |
|---|---|---|
| V39 (S-019) | done | `a726e1e` (merged as `3fa6238`). |
| 2 Manual grant (R-3) | done | `f2ad59c`. `POST /api/v1/live-exam-grants`, Master Admin and System Admin. `GRANT_ALREADY_ACTIVE`, `RESOURCE_NOT_FOUND`, `ACCESS_GRANTED`. |
| 3 Revoke (R-2) | done | `ccdc190`. `DELETE /api/v1/live-exam-grants/{id}?reason=`, Master Admin only. Every access check ignores revoked grants. |
| 4 Confirm or reject order (R-8) | done | `26f4948`. Confirm grants with source `ORDER`, channel `MANUAL-CONFIRMED`. `ORDER_NOT_PENDING`. The `PURCHASE_GRANTED` notice is A2's. |
| 5 Answer release (R-4) | done | `5a13126`. Sets the flag only; the student review gating is A1's. |
| 6 Student deletion blocked (R-10, S-021) | done | `f41b614`. `ACCOUNT_HAS_PAID_ORDERS`, for a SUCCEEDED order or any grant, active or revoked. |
| 7 Permission narrowing (S-022) | done | `48d32db`. `STUDENT_STATUS` is Master Admin only. |
| 8 Instructor status and delete (S-023) | done | `1e84219`. `ACCOUNT_HAS_MARKED_WORK` (new code). |
| 9 Announcements (R-5, S-020) | **blocked** | Not committed. See the decision below. |
| 10 Suspend and restore (R-2) | done | `7c5a1c1`. Audit `USER_SUSPENDED` and `USER_RESTORED`, replacing `USER_STATUS_CHANGED`. |

**Item 9 is blocked on a schema decision.** Section 7 adds only `announcement_id` to `student_notices`. That table still has a `kind` CHECK limited to three kinds (`EXAM_REMINDER`, `MCQ_SCORE`, `WRITTEN_RESULT`), and `exam_id` is `NOT NULL`. An announcement has no exam and needs the new `ANNOUNCEMENT` kind, so its notice row cannot be written. The `announcements` table and the send route alone would give no notices, so nothing was committed. Options: (a) a V40 that widens the kind CHECK and makes `exam_id` nullable (this conflicts with the plan's rule that every schema change is V39, so it needs the integrator's approval); (b) a different notice storage for announcements, which changes the plan. Decision needed before item 9 can go on.

**Notes for A1, A2 and A4:**
- A1: `StudentAccessIT` (see the check note above) needs `source` on its two grant inserts. The staff access list now shows `via = MANUAL` for manual grants and excludes revoked grants. `LiveExamOrder` has `confirmed_by`, `confirm_reason`, `rejected_reason`. `LiveExam` has `answers_released_at` and `answers_released_by`, which the review gating should read.
- A2: confirm writes no notice; `PURCHASE_GRANTED` on confirm (and on manual grant) is yours, from the grant path. The `ANNOUNCEMENT` kind waits on the item 9 decision.
- A4: bundle orders are not handled by confirm or reject (only MODULE and EXAM exist today); `live_exam_grants.source` already allows `BUNDLE`.
- Module assignments are not removed by instructor suspend or delete; the existing unassign route does that.

**Contract changes (for the integrator):**
- New routes: `POST /api/v1/live-exam-grants`; `DELETE /api/v1/live-exam-grants/{id}`; `POST /api/v1/live-exam-orders/{id}/confirm` and `/reject`; `PUT /api/v1/live-exams/{id}/answers-released`; `PATCH /api/v1/instructors/{id}/status`; `DELETE /api/v1/instructors/{id}`. All in `AuditLogIT`.
- Changed: `DELETE /api/v1/students/{id}` (409 `ACCOUNT_HAS_PAID_ORDERS`); `PATCH /api/v1/students/{id}/status` (System Admin gets 403); the staff access list shows `MANUAL` rows; status changes audit `USER_SUSPENDED` / `USER_RESTORED`.
- New error codes: `GRANT_ALREADY_ACTIVE`, `ORDER_NOT_PENDING`, `ACCOUNT_HAS_PAID_ORDERS`, `ACCOUNT_HAS_MARKED_WORK`.
- New permissions (all Master Admin only except where noted): `LIVE_EXAM_GRANT` (Master Admin, System Admin), `LIVE_EXAM_REVOKE`, `LIVE_EXAM_ORDER_CONFIRM`, `LIVE_EXAM_RELEASE` (Master Admin, System Admin), `INSTRUCTOR_STATUS`, `INSTRUCTOR_DELETE`.
- Documented in `current-api.md` under "Added for the phase 2 UI (API-ADMIN)".

## Report — API-ACCESS

Branch `ui/api-access`, worktree `F:\cc-wt\api-access`, rebased on `catalog-acquisition` at `134e687` (API-ADMIN merged). Plan: [student-purchase-lms-plan.md](student-purchase-lms-plan.md). No Flyway migration. Tests ran on the external test database from `api/.env` (`TEST_DB_*`), because Docker is not available on this machine.

**Class names.** The student self-view is its own class, `StudentOwnAccessService` (used by `StudentAccessController` and the public catalog). `StudentLiveExamAccessService`, the staff access list, is exactly as on `catalog-acquisition`; no commit on this branch touches it or `LiveExamAccess`.

**Checks**
- After the rebase, touched classes under the lock: `StudentAccessIT` 2, `PublicAccessIT` 4 (new), `LivePublicCatalogIT` 6, `LiveSittingIT` 33, `AuditLogIT` 3. All 48 green.
- Not rerun after the rebase: the full suite, and API-ADMIN's classes (`LiveGrantAdminIT`, `LiveExamPurchaseIT`, `ExamMailIT`, `LiveEvaluationIT`). The lock was taken by another agent as soon as I released it.
- Full suite before the rebase (on `3fa6238`): 332 run, 15 failures and 10 errors, none from this branch. `BookOrderIT` and `CoursePurchaseIT` passed.
  - 15 were the V39 `source` column with no value in the grant entity (`LiveExamPurchaseIT`, `ExamMailIT`, `LiveEvaluationIT`). API-ADMIN's merge fixes this.
  - 10 were leftover rows in the shared test database: the bootstrap Master Admin login (401 or 429) in `ActivationMailIT`, `ActivationSendFailureIT`, `PasswordResetAudienceIT`, `BootstrapIT` and `InstructorDirectoryIT`, and `SEED_INSTRUCTOR_PHONE is already in use` in `DemoContentSeedIT`. This branch does not touch auth, users or the seed.

| Item | Status | Note |
|---|---|---|
| S-003 (1) | done | `GET /api/v1/me/live-exam-access`. Roster, module and exam grants; `via` `ROSTER`, `MODULE`, `EXAM`, `MANUAL`; revoked grants and other students' rows never appear. |
| S-003 (2) | done | `access { state, buyable, blockedReason }` on public module list, module, module exams and exam, for a signed-in student only. |
| S-005, S-013 (3) | done | `GET /api/v1/public/exam-site/status` → `{ checkoutEnabled }`. |
| S-012 (4) | done | `categoryPath` on public module rows, root first. |
| S-027 (5) | done | `moduleOnly` on upcoming rows. |
| S-018 (6) | done | `correct` and `explanation` withheld until `answers_released_at` is set and the sitting is over. Read through a native query (`LiveExamRepository.answersReleased`). |
| S-024 (7) | done | Tests only. No behaviour change. |

**Contract changes**
- New: `GET /api/v1/me/live-exam-access`, `GET /api/v1/public/exam-site/status`. Both in `AuditLogIT`.
- Added fields: `access` and `categoryPath` on `PublicModule`; `access` on `PublicExam`; `moduleOnly` on `UpcomingExam`.
- Changed behaviour: the MCQ review hides `correct` and `explanation` until release. Existing `LiveSittingIT` cases now release the answers before they assert them.
- Shapes are in `current-api.md` under "Added for the phase 2 UI (API-ACCESS)". A null field is left out of the body, as elsewhere, except `access.blockedReason`, which is always written.

**Notes for the other agents**
- STU-PUR: drop the 404 probes. Use `access.state` and `access.blockedReason` on the module and exam reads, and `/me/live-exam-access` for "my modules". `orderId` is left out for roster rows and manual grants. Owning one exam does not own its module: the module stays `NONE` and buyable. `blockedReason` order is `ALREADY_HAS_ACCESS`, `PRICE_REQUIRED`, `CHECKOUT_OFF`, `PHONE_REQUIRED`. Upcoming rows: show "শুধু মডিউলে কেনা যাবে" when `moduleOnly`.
- STU-LMS: when a review has `questions` but no `correct` on any option, the answers are withheld. Show "উত্তর এখনো প্রকাশ হয়নি".
- API-ADMIN: `LiveExamPublicService` and `StudentOwnAccessService` read grants directly (ignoring revoked ones), not through `LiveExamAccess`.
