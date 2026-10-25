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
- **D-c:** a staff user may browse the public student pages, but cannot buy or sit. A student who opens `/staff` sees "এই অংশ কর্মীদের জন্য" and a link home.

## Files

| File | Workstream | Starts | Agent |
|---|---|---|---|
| [ws0-foundation.md](ws0-foundation.md) | Tokens, fonts, shells, shared components, data layer, auth routing, route skeleton, message files | First | 1 |
| [ws1-api.md](ws1-api.md) | API additions and the demo seed | Same time as WS0 | 1 |
| [ws2-student-entrance.md](ws2-student-entrance.md) | Landing, auth pages, account, notices, payment history, policy pages, errors | After WS0 merges | 1 |
| [ws3-student-catalog.md](ws3-student-catalog.md) | Doors, categories, modules, exam offer, purchase, payment result, home dashboard | After WS0 merges | 1 |
| [ws4-student-sitting.md](ws4-student-sitting.md) | MCQ paper, written sitting, results, review, later attempt, leaderboard | After WS0 merges | 1 |
| [ws5-staff-catalog.md](ws5-staff-catalog.md) | Staff homes, categories, modules, exams, MCQ and written editors, roster, instructor | After WS0 merges | 1 |
| [ws6-staff-ops.md](ws6-staff-ops.md) | Students, instructors, system admins, participation, marking, payments, leaderboard, audit, staff account | After WS0 merges | 1 |
| [ws7-integration.md](ws7-integration.md) | Merge, checks, build, deploy, demo seed on staging, client walkthrough | Last | 1 (Shahriar's main session) |

```
WS0 ──┬─> WS2 ─┐
      ├─> WS3 ─┤
      ├─> WS4 ─┼─> WS7
      ├─> WS5 ─┤
      └─> WS6 ─┘
WS1 ───────────┘   (WS3, WS5, WS6 code against WS1's contract and are merged after it)
```

Rough clock for tonight: WS0 and WS1 about 2 hours. WS2–WS6 in parallel about 3 hours. WS7 about 1 hour.

## Architecture (all workstreams)

**Stack.** Next 16 App Router, React 19, Tailwind 4, TypeScript, already in `web/`. Read `web/node_modules/next/dist/docs/` before using a Next API, as `web/AGENTS.md` requires. This Next has breaking changes; `middleware` is now `proxy` (`web/src/proxy.ts`).

**New dependencies (only these, added by WS0):** `@tanstack/react-query` (server-state cache), `@radix-ui/react-dialog`, `@radix-ui/react-dropdown-menu`, `@radix-ui/react-tabs`, `@radix-ui/react-popover` (accessible primitives), `clsx`, `lucide-react` (icons, tree-shaken), and `pdfjs-dist` (WS4 only, lazy-loaded). Any other package needs Shahriar's OK.

**Route groups.**

| Group | Prefix | Shell | Who |
|---|---|---|---|
| `(site)` | `/`, `/mcq`, `/written`, `/exam/...`, `/notices`, `/account`, policy pages | `StudentShell` | Visitors and students. Staff may browse (D-c). |
| `(auth)` | `/login`, `/register`, `/forgot-password`, `/reset-password`, `/activate`, `/set-phone` | `StudentShell`, no dashboard | Signed out. Staff also sign in here. |
| `(staff)` | `/staff/...` | `StaffShell` | Master Admin, System Admin, Instructor |
| `(admin)` | `/students`, `/courses`, ... | old `AdminShell` | Unlinked. Untouched. |

**Data.** `lib/api/client.ts` stays the one fetch path (refresh cookie, memory token, error mapping). WS0 adds TanStack Query with typed hooks per API area under `web/src/lib/api/exam/`. Defaults: `staleTime` 60 s for catalog reads, 0 for sitting and payment status, `retry` once for reads and never for writes, refetch on window focus for the dashboard, notices and sitting. Query keys live in one file per area. Mutations invalidate the narrowest keys.

**Rendering.** Pages are client components that render a skeleton at once and fill from the cache. The landing page and policy pages are static. Server rendering of the public catalog for search engines is a later step, not tonight (personal state mixes into the same pages).

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

## Rules for every agent

1. Work in your own worktree and branch, named `ui/wsN-<name>`, cut from `catalog-acquisition` after WS0 is merged (WS0 and WS1 cut from it now).
2. Only create or edit the files your workstream file lists under **Owns**. Need something in a shared file? Ask in your report; WS7 resolves it. Never edit another workstream's pages.
3. Never change the API from a web workstream, and never change the web from WS1.
4. Copy goes in your message namespace in both languages. No inline strings.
5. Before you hand over: `npm run lint` with 0 errors, `npm test` green (component tests with mocked `fetch`, no running API), `npm run build` passes. WS1: the touched `*IT` classes green against local PostgreSQL with `TEST_DB_*` set. Run the full API suite alone (it runs out of memory beside other heavy jobs).
6. Do not open a browser or run the API for UI checks. Shahriar does the browser pass.
7. Commit on your branch with a message that says what and why, ending with the attribution line. Do not push, merge or deploy. Write a short progress note in your workstream file's **Report** section: what was done, what is left, anything another workstream needs.

## Definition of done (tonight)

- Every page in WS2–WS6 exists with its loading, empty, error and success states, in Bangla and English, light and dark.
- A student can register, browse a door, open a module, buy with the sandbox, sit an MCQ, upload a written answer, read notices, see results and the leaderboard, all on a phone.
- The Master Admin can create a category, module and exam, add questions or a PDF, publish, grant the roster, assign an instructor, see participation, mark, publish a result, and see payments. The System Admin and Instructor see only what they may.
- `lint`, `test` and `build` pass on the merged branch, and the full API suite fails only `BookOrderIT` and `CoursePurchaseIT`.
- Deployed to staging with demo content, and the walkthrough in [ws7-integration.md](ws7-integration.md) passes.
