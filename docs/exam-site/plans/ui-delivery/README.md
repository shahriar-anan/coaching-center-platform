# Exam site UI delivery — plan

Written 25 October 2026 for delivery to the client the same night. **Audience:** the agents building the website and API in parallel, and Shahriar who merges, deploys and does the browser pass. [../../decisions.md](../../decisions.md) and [../../current-api.md](../../current-api.md) win where this folder is silent. The four files in [../../ui/](../../ui/) are inputs, not specs: they list pages and states in detail, and this plan says where to follow them and where they are out of date.

## Outcome

By tonight, `https://srv2089893.hstgr.cloud` is a complete exam website the client can approve:

- **Student:** a polished, mobile-first site in the client's style (cream, gold, navy, Bangla first). Every page, from landing to results, works on a phone and a desktop.
- **Master Admin, System Admin, Instructor:** one staff tool in the admin-prompt style (calm, dense, blue on grey, desktop first, usable on a phone). The client can author a catalog, grant access, mark, publish and see payments without help.
- Realistic demo content is loaded, so every page shows something on first open.
- Changes the client asks for are cheap: copy is in catalogs, colors are tokens, and pages are built from a small set of shared components.

## Decisions (Shahriar, 25 October 2026)

| # | Topic | Decision |
|---|---|---|
| U1 | Scope | Exam site only, all four roles. Nothing about courses, books, the library, or course and book payments. |
| U2 | Old admin pages | Hidden from navigation. The code and routes stay. `/` no longer redirects to `/students`. `/sandbox` and the `/live-exams` stub are removed. |
| U3 | Browsing | One category tree. The home has two doors, MCQ and Written. Each door is the same tree filtered by exam type. A module page opened through a door lists only exams of that type. Buying a module still covers both types, and the page says so. |
| U4 | Brand | Text logo "⚖️ এক্সাম**পোর্টাল**" from the mockup. A logo file is swapped in later through one component. |
| U5 | Staff look | The admin-prompt visual system, separate from the student look. |
| U6 | Demo data | A realistic BJS catalog with questions, a written PDF, a demo instructor and demo students, loaded on staging only. Removable. |
| U7 | Payments | Master Admin and System Admin read exam payments. System Admin is read-only. |
| U8 | New reads | Add a roster membership list and a student access list to the API. |
| U9 | Policy pages | Terms, Privacy, Refund, About and Contact, with clearly marked placeholder text. SSLCommerz needs them for a live store. |
| U10 | Google | Show "গুগল দিয়ে চালু করুন" disabled with "শীঘ্রই" until the server is configured. |
| U11 | MCQ paper | All questions on one scrolling page, sticky timer, a jump grid of question numbers, autosave on each tap. |
| U12 | Extras | Bengali digits in the Bangla UI, a next-exam countdown on the home page, and the student's own payment history. |
| U13 | Way of work | Parallel agents, one git worktree and branch per workstream, fixed file ownership, merged in a set order. |

Defaults this plan takes (Shahriar can change them):

- **D-a:** during an open MCQ sitting the API returns the student's own saved selection, so a reload or a second device shows the answers already chosen. It still returns no correct option until the deadline. Without this, a refresh during a live exam looks like lost answers.
- **D-b:** a category with no modules at all shows "শীঘ্রই আসছে" in both doors. A category whose modules are all the other type is hidden from that door.
- **D-c:** a staff user may browse the public student pages, but cannot buy or sit. A student who opens `/staff` sees "এই পাতাটি শুধু অ্যাডমিন ও শিক্ষকদের জন্য" and a link home.
- **D-d:** the student site and the admin panel will have different URLs at launch. Tonight they share the staging host. One code base serves both setups through environment values ([ws0-foundation.md](ws0-foundation.md) section 9).

## Files

| File | Workstream |
|---|---|
| [ws0-foundation.md](ws0-foundation.md) | Tokens, fonts, shells, shared components, data layer, auth routing, test helpers, message files. Its **Contracts** section is frozen at the start so everyone can build against it. |
| [ws1-api.md](ws1-api.md) | API additions and the demo seed |
| [ws2-student-entrance.md](ws2-student-entrance.md) | Auth pages, account, notices, payment history, help and policy pages, errors |
| [ws3-student-catalog.md](ws3-student-catalog.md) | Home dashboard, doors, categories, modules, exam page, routine, purchase, payment result |
| [ws4-student-sitting.md](ws4-student-sitting.md) | MCQ paper, written sitting, results hub, results, review, later attempt, leaderboard, PDF viewer |
| [ws5-staff-catalog.md](ws5-staff-catalog.md) | Staff homes, categories, modules, exams, MCQ and written editors, roster, instructor |
| [ws6-staff-ops.md](ws6-staff-ops.md) | Students, instructors, system admins, participation, marking, payments, leaderboard, audit, staff account |
| [ws7-integration.md](ws7-integration.md) | Merge, checks, build, deploy, demo seed, walkthrough |
| [ws8-final-qa.md](ws8-final-qa.md) | Final quality check by one agent after the last merge and build (static audit, browser audit with Playwright and axe, business-rule flows, performance, privacy), then a staging pass after the deploy |
| [bangla-copy.md](bangla-copy.md) | Bangla copy guide and glossary for every workstream. Wins over any Bangla quoted in the briefs or the UI prompts. |

## Team of five

Five agents start at the same time. The **integrator** is Shahriar's main session (WS7): it merges, answers questions, resolves shared-file requests and deploys. It does not build pages.

| Agent | Phase 1 (from the start) | Phase 2 (when phase 1 is merged) |
|---|---|---|
| **A** | WS0 foundation (about 1.5 h). Merge as early as possible; publish the contracts first. | WS2 student entrance |
| **B** | WS1 API and demo seed | WS6 staff operations, second half (payments, audit, staff account, staff leaderboard) |
| **C** | WS3 student catalog | continues WS3 |
| **D** | WS4 student sitting | continues WS4 |
| **E** | WS5 staff catalog | WS6 staff operations, first half (students, instructors, admins, participation, marking) |

How C, D and E start before WS0 is merged: WS0's **Contracts** section fixes the file paths, export names, props, Tailwind token classes, hook helpers, formatting functions, guards and test helpers. Phase-1 page agents write their API hooks, local types, copy, page components and tests against those contracts, in their own folders. They render inside a minimal local wrapper until WS0 lands. As soon as the integrator merges WS0, each agent runs `git merge catalog-acquisition` into its branch, deletes the local wrapper, and fixes anything that breaks. If WS0 has to change a contract, it tells the integrator first, and the integrator tells everyone.

WS6 is split in two by route: the first half is `students`, `instructors`, `admins`, `participation` and `submissions`; the second half is `payments`, `audit`, `account` and `exams/[examId]/leaderboard`. They are two branches (`ui/ws6a-staff-people`, `ui/ws6b-staff-ops`), each with its own message file (`staffPeople`, `staffOps`) and hooks file (`staff-people.ts`, `staff-ops.ts`).

```
A: WS0 ──merge──> WS2 ─────────────┐
B: WS1 ──merge──> WS6b ────────────┤
C: WS3 (build against contracts) ──┼─> integrator merges in order ─> deploy
D: WS4 (build against contracts) ──┤
E: WS5 ──────────> WS6a ───────────┘
```

Merge order: WS0, WS1, WS4 (the PDF viewer others reuse), WS3, WS2, WS5, WS6a, WS6b. Each merges as soon as it reports done and its checks pass.

## Priority inside every workstream

- **P0:** everything in the page tables. The client must see it tonight.
- **P1:** items marked *(P1)*, mostly LMS conveniences. Build them after all P0 pages in your workstream work.
- **P2:** items marked *(P2)*, polish. Only if there is time left; never at the cost of P0 quality.

A finished, polished P0 page beats three half-done pages. Stop adding scope at the time the integrator calls freeze.

## Architecture (all workstreams)

**Stack.** Next 16 App Router, React 19, Tailwind 4, TypeScript, already in `web/`. Read `web/node_modules/next/dist/docs/` before using a Next API, as `web/AGENTS.md` requires. This Next has breaking changes; `middleware` is now `proxy` (`web/src/proxy.ts`).

**New dependencies (only these, all added by WS0 in its first commit):** `@tanstack/react-query` (server-state cache), `@radix-ui/react-dialog`, `@radix-ui/react-dropdown-menu`, `@radix-ui/react-tabs`, `@radix-ui/react-popover` (accessible primitives), `clsx`, `lucide-react` (icons, tree-shaken), and `pdfjs-dist` (used by WS4, lazy-loaded). Any other package needs Shahriar's OK.

**Worktrees.** Each agent's worktree has its own `web/node_modules`: run `npm ci` in `web/` once after cutting the branch, and again after merging `catalog-acquisition` if `package-lock.json` changed. The API worktree (agent B) uses the shared local PostgreSQL test database; only agent B and the integrator run API tests.

**Route groups.**

| Group | Prefix | Shell | Who |
|---|---|---|---|
| `(site)` | `/`, `/mcq`, `/written`, `/exam/...`, `/notices`, `/account`, policy pages | `StudentShell` | Visitors and students. Staff may browse (D-c). |
| `(auth)` | `/login`, `/register`, `/forgot-password`, `/reset-password`, `/activate`, `/set-phone` | `StudentShell`, no dashboard | Signed out. Staff also sign in here. |
| `(staff)` | `/staff/...` | `StaffShell` | Master Admin, System Admin, Instructor |
| `(admin)` | `/students`, `/courses`, ... | old `AdminShell` | Unlinked. Untouched. |

**Data.** `lib/api/client.ts` stays the one fetch path (refresh cookie, memory token, error mapping). WS0 adds TanStack Query with typed hooks per API area under `web/src/lib/api/exam/`. Defaults: `staleTime` 60 s for catalog reads, 0 for sitting and payment status, `retry` once for reads and never for writes, refetch on window focus for the dashboard, notices and sitting. Query keys live in one file per area. Mutations invalidate the narrowest keys.

**Rendering.** Pages are client components that render a skeleton at once and fill from the cache. The landing page and policy pages are static. Server rendering of the public catalog for search engines is a later step, not tonight (personal state mixes into the same pages).

**Environments and URLs.** The student site and the admin panel are two surfaces of one Next app. On staging they share one host (staff under `/staff`); at launch they get separate URLs. The browser always calls the API by relative path (`/api/...`), and each host's Nginx forwards it. Hosts come only from `NEXT_PUBLIC_SITE_URL` and `NEXT_PUBLIC_STAFF_URL`, read through `lib/config`. Any link or redirect between the two surfaces uses `siteHref()` or `staffHref()`. Never write a host, and never write a student-to-staff link as a bare relative path. Details in [ws0-foundation.md](ws0-foundation.md) section 9.

**Time.** All times are shown in Asia/Dhaka. Countdowns use the deadline the server sent plus a clock offset measured from the API response `Date` header, never the phone's clock alone.

**i18n.** Bangla is the default. The existing locale context stays, but messages are split into one file per namespace (`web/src/i18n/messages/{bn,en}/<namespace>.ts`) so agents never edit the same file. Formatting helpers (`formatDate`, `formatTime`, `formatMoney`, `formatNumber`, `formatDuration`) apply Bengali digits in Bangla. Titles pick `titleBn` or `titleEn` from the locale. The paper language only selects the question typeface.

## UI standards (all workstreams)

**Student look** (client mockup, improved). Tokens in [ws0-foundation.md](ws0-foundation.md). Mobile first, 760 px column, cream page, white cards with 16 px radius, gold accents, navy text. Fixes to the mockup: navy text on gold buttons (white fails contrast), real labels on inputs, no photo-upload or negative-mark copy.

**Staff look** (admin prompt). Desktop first, left sidebar 232 px, content to 1120 px, 14 px tables, blue action color, grey background. At 768 px the sidebar becomes a menu; under 640 px table rows become stacked cards with the same fields and the same primary action.

**Usability rules.**

- One primary action per screen. Destructive actions are quiet text until a dialog names the object.
- Every async action disables its button and shows progress. Values stay in the form after an error. Errors sit under the field that failed and say how to fix it.
- First load is a skeleton shaped like the content, never a blank page or a lone spinner. A failed load keeps the last good content and offers "আবার চেষ্টা".
- Empty states say what is missing and what to do next.
- Lists keep page, search and filters in the URL, so Back and refresh return to the same view. Search waits 300 ms after typing.
- Status is always a word, never a color alone. Money is ৳ with two decimals; a missing price is a sentence, not zero. Undated exams say "তারিখ ঘোষণা হবে".
- Confirm before leaving the site for payment, and before anything that cannot be undone.
- A control the server would refuse for this role is absent, not shown disabled, unless the reason is the point (for example "পরীক্ষা শুরু করুন" before the window opens, with the time).

**Accessibility (WCAG 2.2 AA).** Contrast 4.5:1 for text. Visible focus ring. Targets at least 44 px (student) and 40 px (staff). Labels on every input. Dialogs trap focus, close on Escape and return focus. `prefers-reduced-motion` removes motion. `lang` follows the locale. Icon-only buttons have an accessible name.

**Responsive.** Test at 320, 375, 768, 1024 and 1440 px. No sideways page scroll at 320 px. Tables scroll inside their card, not the page. Safe-area insets on phones.

**Performance budgets.**

- Student routes: under 150 KB of JavaScript (gzip) on first load, excluding the PDF viewer, which loads only on the written sitting and staff PDF views.
- Largest contentful paint under 2.5 s on a mid-range phone over 4G for the landing, door and module pages.
- Fonts through `next/font` (self-hosted, `display: swap`, Bangla subset). No layout shift from fonts or skeletons.
- No image larger than it is shown. The brand is text and emoji, so there are almost no images.
- Lists are paged by the server (20 rows). No client-side "load everything".
- `next/link` prefetch for navigation cards. Heavy components (`pdfjs-dist`, the question editor) are dynamic imports.

## Route ownership

One owner per route. Creating a route that is not yours, or a second page for an owned route, is a merge blocker.

| Route | Owner |
|---|---|
| `/` (home and signed-in dashboard), `/mcq/**`, `/written/**`, `/exam/[examId]`, `/routine`, `/my-modules`, `/purchase/result` | WS3 |
| `/exam/[examId]/sit`, `/exam/[examId]/result`, `/exam/[examId]/leaderboard`, `/results` | WS4 |
| `/login`, `/login/google`, `/register`, `/forgot-password`, `/reset-password`, `/activate`, `/set-phone`, `/account/**`, `/notices`, `/help`, `/about`, `/contact`, `/terms`, `/privacy`, `/refund` | WS2 |
| `/staff`, `/staff/categories/**`, `/staff/modules/**`, `/staff/exams/[examId]` | WS5 |
| `/staff/students/**`, `/staff/instructors/**`, `/staff/admins/**`, `/staff/participation`, `/staff/submissions/**` | WS6a |
| `/staff/payments/**`, `/staff/audit`, `/staff/account`, `/staff/exams/[examId]/leaderboard` | WS6b |
| layouts, `not-found`, `error`, shells, `components/ui`, providers, `package.json` | WS0 |

## LMS practices adopted

What students and staff expect from current learning and exam platforms (Moodle, Canvas, Google Classroom; in Bangladesh, 10 Minute School, Shikho, Testbook-style mock-test sites), and where each lands in this plan:

| Practice | Where |
|---|---|
| A home dashboard with "what's next": countdown to the next exam, a "continue" card for an exam in progress, what is live now | WS3 home |
| Bottom tab bar on phones for signed-in students (হোম, আমার পরীক্ষা, রেজাল্ট, নোটিশ, অ্যাকাউন্ট); header navigation on desktop | WS0 `StudentShell` |
| Progress you can see: "৫টির মধ্যে ৩টি পরীক্ষা দেওয়া হয়েছে" with a bar on each owned module | WS3 module and my-modules *(P1)* |
| An agenda view of upcoming exams grouped by day, with "ক্যালেন্ডারে যোগ করুন" (.ics file made in the browser) | WS3 `/routine` *(P1 for .ics)* |
| A gradebook: one page with every result and every script still being marked | WS4 `/results` |
| A clear pre-start screen: rules, duration, "the timer starts now", and that starting gives up the later attempt | WS3 exam page, start confirm |
| Exam-taking standards: question navigator, flag for review, autosave status, time warnings at 10, 5 and 1 minute, a connection-lost banner, confirm submit with the unanswered count, leave-page guard | WS4 |
| Reading comfort: text size control on the paper | WS4 *(P2)* |
| Notifications centre with unread counts, plus email for receipts | WS2, A8 |
| Help centre and FAQ: how to buy, how live exams work, how to upload a written answer | WS2 `/help` |
| Trust at checkout: exact price, what it unlocks, "SSLCommerz-এর মাধ্যমে নিরাপদ পেমেন্ট · বিকাশ, নগদ, কার্ড", refund-policy link | WS3 confirm sheet |
| Onboarding nudges that never block: verify-email card, phone needed before buying | WS3 home, WS2 account |
| Authoring lifecycle draft → final save → publish, with a student preview | WS5 *(preview P1)* |
| Faster question entry: paste many questions in one go | WS5 *(P2)* |
| Marking queue: previous and next submission, "সেভ করে পরেরটি", work-queue badges | WS6a |
| Low-end phones and slow networks: skeletons, prefetch, retry, tiny bundles, no heavy images | All |
| Bangla first: Bengali digits, Bangla day periods, both titles kept | All |

## Rules for every agent

**Ownership**

0. **Every file you create or edit is inside your own worktree folder** (`F:\cc-wt\ws0`, `ws1`, `ws3`, `ws4` or `ws5`). Use paths relative to it. Never write anything under `F:\coaching-center-platform\` — that is the integrator's checkout — except creating and deleting the `.heavy.lock` file. Before your first edit, run `git rev-parse --show-toplevel` and check it prints your worktree.
1. Work only in your own worktree on your own branch (`ui/ws0-foundation`, `ui/ws1-api`, `ui/ws2-student-entrance`, `ui/ws3-student-catalog`, `ui/ws4-student-sitting`, `ui/ws5-staff-catalog`, `ui/ws6a-staff-people`, `ui/ws6b-staff-ops`).
2. Create or edit only the files listed under **Owns** in your workstream file, and only the routes the table above gives you. Everything else is read-only for you.
3. Never change the API from a web workstream, and never change the web from WS1.
4. Only WS0 edits `web/package.json` and the lockfile. All dependencies in the list above are added by WS0 at the start, including `pdfjs-dist`. Need another package? Ask the integrator; do not install it.
5. Shared types: use `lib/api/exam/types.ts` (WS0) when it already has the type. Otherwise declare the type in your own hooks file. If two workstreams need the same new type, tell the integrator; do not import from another workstream's files, except the stubs WS0 defines (`NoticeBell`, `PdfViewer`).
6. Copy goes only in your own message namespaces, in both languages. No inline strings, except data from the API. Write the Bangla by [bangla-copy.md](bangla-copy.md): natural Bangla first, the client's words and the glossary terms, never a word-for-word translation. Bangla quoted in the briefs and the UI prompts shows intent only; the guide wins. Read your Bangla aloud once before reporting done.
6a. Never write a host name or an absolute URL. Links between the student site and the admin panel go through `siteHref()` and `staffHref()` from `lib/config`; API calls go through `apiRequest`, which uses the relative `/api` path.

**Contracts and syncing**

7. Build against the WS0 **Contracts** and the WS1 **Contracts** exactly. If one is wrong or missing, write it in your **Report** and tell the integrator; do not work around it in someone else's file.
8. When the integrator merges something you depend on, run `git merge catalog-acquisition` in your branch (do not rebase), fix what breaks in your own files, and run your checks again. **Expected conflicts after WS0 lands** (because page agents started before it): your message files (`i18n/messages/{bn,en}/<your namespace>.ts`) were created empty by WS0 — keep your content; `components/pdf/pdf-viewer.tsx` — keep WS0's wrapper and put the viewer body in `pdf-viewer-impl.tsx` (WS4 only). Then delete your `_tmp` stand-ins and import the real WS0 modules. A conflict in any other file means a rule was broken: ask the integrator.
9. Web pages that need WS1 endpoints use mocked `fetch` in tests and work against the documented shape. They must handle a `404` or `400` from an old API gracefully until WS1 is deployed.

**Machine limits (one PC, 16 GB)**

10. Only one heavy job at a time across all agents: `npm run build`, `mvn test`, or `mvn package`. Before starting one, create the lock file `F:\coaching-center-platform\.heavy.lock` with your agent letter inside; if it exists, wait and retry every minute; delete it when your job ends. `npm run lint` and type checks are light and need no lock.
10a. **Run web tests in one process**, always: `npx vitest run --pool=forks --poolOptions.forks.singleFork=true` (add a path to run only your tests, for example `tests/ws3`). Vitest's default worker pool ran out of memory on this PC while several agents were working (seen 25 October). In this mode tests need no lock. Once the integrator sets it as the default in `vitest.config.ts`, plain `npm test` does the same.
10b. **Wrap guarded pages in `Suspense`.** `RequireStudent` and `RequireStaff` read the URL query (`useSearchParams`), so every route page that renders them must wrap its content in `<Suspense fallback={<Skeleton variant="card" />}>`, or `npm run build` fails on prerender (found by WS4, 25 October).
11. Do not run `next dev` or the API. Do not open a browser. Shahriar does the browser pass.

**Quality gate before you report done**

12. `npm run lint` with 0 errors, `npm test` green (component tests with mocked `fetch`; use the WS0 test helpers), `npm run build` passes (under the lock). WS1: the touched `*IT` classes, then the full suite from clean, alone; only `BookOrderIT` and `CoursePurchaseIT` may fail.
13. Every page has its loading, empty, error and success states; works at 320 px and 1440 px; works in Bangla and English, light and dark; can be used by keyboard.
14. Commit on your branch with a message that says what and why, ending with the attribution line. Commit small and often. Do not push, merge or deploy.
15. Fill in your workstream file's **Report**: what is done, which P1 and P2 items are done or skipped, any contract change, and anything another workstream needs. Then tell the integrator you are ready to merge.

**Communication**

16. The integrator is the only channel between agents. Questions go to the integrator with the file and the exact contract in question. Agents do not message each other or edit each other's Report sections.

## Definition of done (tonight)

- Every P0 page in WS2–WS6b exists with its loading, empty, error and success states, in Bangla and English, light and dark, at 320 px and 1440 px.
- A student can register, browse a door, open a module, buy with the sandbox, sit an MCQ, upload a written answer, read notices, see results and the leaderboard, all on a phone.
- The Master Admin can create a category, module and exam, add questions or a PDF, publish, grant the roster, assign an instructor, see participation, mark, publish a result, and see payments. The System Admin and Instructor see only what they may.
- `lint`, `test` and `build` pass on the merged branch, and the full API suite fails only `BookOrderIT` and `CoursePurchaseIT`.
- Deployed to staging with demo content, and the walkthrough in [ws7-integration.md](ws7-integration.md) passes.
