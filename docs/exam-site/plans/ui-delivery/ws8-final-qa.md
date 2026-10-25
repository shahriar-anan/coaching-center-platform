# WS8 — Final quality check

**One agent only**, after every workstream is merged and the final build passes, and before the client sees the site. A shorter second pass runs on staging after the deploy. Read [README.md](README.md), [bangla-copy.md](bangla-copy.md), [../../decisions.md](../../decisions.md), [../../current-api.md](../../current-api.md), and [../../ui/admin-prompt.md](../../ui/admin-prompt.md) and [../../ui/student-flow-prompts.md](../../ui/student-flow-prompts.md) (their "Rules the API enforces" sections are the business rules).

The goal is the check a production team does before a launch. Nothing is assumed to work because a test passed. Every page, every role, every rule, every pixel that a client could notice, in both languages and both themes, on a phone and a desktop.

## Who, where, and what is allowed

- **Agent:** one QA agent. Worktree `F:\cc-wt\qa`, branch `ui/ws8-qa`, cut from `catalog-acquisition` after the last merge. No other agent runs during WS8, so the machine is free and there is no lock contention. Still take the heavy lock for builds and Maven runs, in case the integrator runs something.
- **Unlike the building agents, the QA agent may run the API and the web app locally and drive real browsers.** It installs `@playwright/test` and `@axe-core/playwright` as dev dependencies on its own branch, and the Playwright browsers (`npx playwright install chromium firefox webkit`). The integrator decides later whether to merge these into `catalog-acquisition`.
- **Fixes:** the QA agent fixes **Minor** and **Polish** defects itself (copy, tokens, spacing, small UI bugs), each with a test where one makes sense. **Blocker** and **Major** defects are fixed by the QA agent when the fix is contained (one area, no contract change). Otherwise they are written up and sent to the integrator first. Ownership boundaries from the build phase no longer apply on this branch, but the README architecture rules still do: tokens, message catalogs, `lib/config`, no hosts in code.
- Never touch the staging server, never use the live SSLCommerz store, never print a secret.

## Setup (local stack)

1. **Database.** Create a fresh local database `coaching_center_qa` (never the test database used by the suite). Start the API from the QA worktree with the `dev` profile, `DB_URL` pointing at `coaching_center_qa`, `SEED_DEMO=true` and the `SEED_*` accounts (passwords from a local, gitignored env file), `DEV_EXPOSE_EMAIL_CODES=true` so verification and reset codes appear in the API log, and a `MASTER_ADMIN_*` set for the bootstrap. Flyway builds the schema; the demo seed fills it. Record the accounts in the QA report without their passwords.
2. **Web.** `npm run build` then `npm run start` on port 3000, with `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`, `NEXT_PUBLIC_SITE_URL` and `NEXT_PUBLIC_STAFF_URL` both `http://localhost:3000`. The API's `WEB_ORIGIN` is `http://localhost:3000`. Never `next dev` for QA: test the production build.
3. **Split-host run** (section 10): a second build or runtime config with `SITE_URL=http://site.localhost:3000` and `STAFF_URL=http://admin.localhost:3000` (`*.localhost` resolves to the loopback address in browsers and Playwright), and `WEB_ORIGIN` listing both, student first.
4. **Time travel.** Exams have fixed windows. Use the staff exam editor (as Master Admin) to move a demo exam's start a few minutes ahead and set a short duration (2–5 minutes) to test the open window, the deadline, the review and the leaderboard opening five minutes later. Write down every change in the report.
5. **Payments locally.** The sandbox cannot call back to `localhost`, so locally a student gets access through the roster or the seed. The real checkout runs in Part C on staging.

## Part A — Static audit (no browser)

### A1. API ↔ UI coverage matrix

Build two tables in the report.

- **API → UI:** every endpoint a student or staff user can call (from the controllers under `api/src/main/java`, and `current-api.md`) against the page that uses it. Each row is "used by <route>", "deliberately unused (reason)", or **gap** (the API supports something the UI does not offer, for example a reissue, a filter or a field). Course, book and old exam-module endpoints are out of scope and listed once as excluded.
- **UI → API:** every `apiRequest`, `apiUpload` and `apiBlob` call in `web/src` against the controller mapping. Check method, path, query names, body field names and response fields read by the page. A UI call or field with no API counterpart is a **Blocker**.

### A2. Error-code coverage

For each endpoint the UI uses, list the error codes it can return (from the services and `ErrorCode`) and the message the UI shows. Every user-facing code has its own sentence from the copy guide (for example `PHONE_REQUIRED`, `ALREADY_PURCHASED`, `PRICE_REQUIRED`, `PAYMENT_GATEWAY_UNAVAILABLE`, `SITTING_NOT_OPEN`, the five-minute clash, `EXAM_HAS_PARTICIPANTS`, `EXAM_HAS_PURCHASES`, duplicate phone and email, invalid code, lockout, rate limit). A raw code or a generic "something went wrong" where the user could act is a **Major**.

### A3. Role and route matrix

Rows: every route in the README route table. Columns: visitor, student, System Admin, Master Admin, Instructor (and the split-host variant). Each cell: allowed, redirected to login, staff-only or student-only message, or not found; plus which nav items and controls are visible. Compare with the role rules in `admin-prompt.md` and the decisions in the README. A control the server would refuse that is visible (for example a price field for a System Admin, a publish button for an Instructor, delete for a System Admin) is a **Major**.

### A4. Code hygiene

- `grep` for hex colors, `rgb(`, and pixel font sizes outside `globals.css` and tokens; for hosts (`hstgr.cloud`, `http://`, `https://`) outside `lib/config.ts`, env files and tests; for inline user-facing strings outside message catalogs; for `console.log`; for `TODO`/`FIXME`; for `_tmp` and `_preview` folders. Each hit is fixed or justified.
- `npx tsc --noEmit` clean, `npm run lint` 0 errors, full `npm test` green, full API suite from clean (only `BookOrderIT` and `CoursePurchaseIT` may fail).
- Message catalogs: every key used exists in both `bn` and `en` (write a small script); no unused keys left from removed pages; no English text inside `bn` files except loanwords the guide allows.

### A5. Copy audit

- Search `bn` catalogs for the guide's "Do not use" terms (প্রবেশ, সক্রিয়, রোস্টার, ইনস্ট্রাক্টর, ড্রাফট, ফাইনাল, অ্যাক্সেস আছে, পঠিত, তারিখ ঘোষণা হবে, পেমেন্টে যান, দাম এখনো নির্ধারণ হয়নি, ফেরত নীতি, ভেরিফাই, সাবমিট, লিডারবোর্ড, এই অংশ কর্মীদের জন্য, and the rest of the table). Each hit is a **Minor** and is fixed.
- One term per concept across all pages (for example রেজাল্ট in chips and the tab bar, ফলাফল in headings).
- Read every Bangla string once in context (Part B screenshots): word order, verb at the end, দাঁড়ি only on sentences, no word-for-word translation, buttons short enough for 320 px. English reads as English.
- Policy pages carry the placeholder banner; no "ক্লায়েন্ট" wording visible to users.

## Part B — Browser audit on the local stack (Playwright)

Write the checks as Playwright specs under `web/e2e/` so they can be re-run later. Run them in Chromium, Firefox and WebKit, and in the `Pixel 7` and `iPhone 14` device profiles. Save screenshots under `web/e2e/screenshots/<route>/<viewport>-<locale>-<theme>.png` (gitignored; keep a zip for Shahriar).

### B1. Visual and typography

For every route, at 320, 375, 768, 1024 and 1440 px, in Bangla and English, light and dark:

- **Fonts** (read computed styles): student chrome Hind Siliguri; Bangla question bodies Noto Sans Bengali; English question bodies and staff Latin text Inter; staff Bangla Noto Sans Bengali. No fallback serif anywhere. Fonts load from the app (no external font request).
- **Sizes and weights** against the spec. Student: body ≥ 16 px, line height 1.6, section titles 20 px/700, card titles 17 px/700, hero word `clamp(64px, 22vw, 110px)`/800, pills 12 px/700, breadcrumbs 14 px. Staff: page title 22 px, tables 14 px, forms 16 px, line height 1.45. Report each deviation with the element and the computed value.
- **Colors** match the tokens of the surface, in both themes. Gold buttons have navy text. Status is never color alone.
- **Shape and spacing:** card radius 16 px (site) and 8 px (staff), button radius, padding, gaps consistent between similar components; aligned icons; no clipped Bangla glyphs (descenders and conjuncts in buttons, pills and table cells); long titles wrap or truncate with a tooltip, never overflow.
- **Targets:** interactive elements at least 44 px (site) and 40 px (staff) tall.
- **No horizontal page scroll:** `document.documentElement.scrollWidth <= window.innerWidth` on every page at 320 px. Tables scroll inside their card; staff tables turn into cards under 640 px.
- **Fixed elements:** the phone tab bar never covers content or the sitting paper; the sticky sitting bar never covers the first question; dialogs and sheets fit on a 320 × 568 screen.
- **Layout shift:** CLS under 0.1 on load; skeletons match the final layout.
- **Bengali digits** in every number, date, price, mark and count in Bangla, Latin digits in English; Bangla day periods, never AM/PM; `৳` formatting per the guide.

### B2. Accessibility

- `@axe-core/playwright` on every route in both themes: zero serious or critical violations.
- Keyboard-only run of the main flows (login, browse, buy confirm, sit MCQ, submit, staff create exam, mark, publish): every control reachable in a sensible order, visible focus ring, dialogs trap focus and return it, Escape closes.
- `lang` on `<html>` follows the locale; icon-only buttons have names; form errors are announced; the countdown announces each minute only; `prefers-reduced-motion` removes animation.

### B3. Functional flows against the business rules

Each flow lists the rules it proves. A broken rule is a **Blocker**.

**Student**

1. Register (duplicate phone and duplicate email messages), verify email with the code from the API log, log in by phone and by email, wrong password (generic message), lockout after repeated failures, forgot and reset password, change password, change phone, logout. Session expiry returns to login with `next` and comes back.
2. Doors: MCQ door shows only modules with MCQ exams and only MCQ exams on a module page; Written door the same for written; an empty category shows "শীঘ্রই আসছে"; a mixed module appears in both doors; undated exams show "তারিখ পরে জানানো হবে" and sort last.
3. Module and exam pages: price bar, "চালু আছে" when owned, no buy button without a price, module purchase copy says later exams are included and both types, exam page primary action per `myStatus` (all nine states, by moving times).
4. Sitting MCQ: start confirm copy (live start forfeits the practice attempt); answers save (status shown); reload and a second browser context keep the answers (saved selection from the API); the countdown follows the server; flag for review; warnings at 10, 5, 1 minutes (use a short exam); submit shows the score and keeps the paper editable; at the deadline the page moves to the result; the review shows "আপনার উত্তর", "সঠিক উত্তর" and the explanation only after the deadline; no negative marking.
5. Practice attempt: only for an MCQ, only when the student never started the live sitting, after the window; banner and own timer; not on the leaderboard; labeled on the result.
6. Written: the PDF shows inline with the watermark (name and student code), no download control; upload a PDF, replace it, an image is refused with the right message, an over-80 MB file is refused; no file at the deadline gives "খাতা জমা হয়নি" and zero; a stored file shows "মূল্যায়ন চলছে" until the Master Admin publishes; after publish the marks, the comment without the teacher's name, and the marked script; no practice attempt offered for written.
7. Leaderboard: closed until five minutes after the end with the opening time; ties share a rank and the next rank skips; own row highlighted; no phone column; written rows only after publish, except a failed-to-submit zero.
8. Results hub, routine (grouping, `.ics` file content), my modules (progress), notices (reminder, MCQ score, written result; mark one, mark all; the bell count), payment history (statuses in student words), home dashboard (countdown, live now, upcoming, nudges, welcome card once).

**Staff**

9. Master Admin: create, edit, final save and publish a category, module and both exam types; price input; the five-minute clash names the other exam; an exam is read-only after its start; delete rules (exam with participants or purchases refused with the right sentence); archive hides the branch from students; roster list, search, add, paste result, remove with the warning when the student has started; assign the instructor, conflict on a second assign, remove; participation counts and filters; marking a draft with a marked PDF, publishing, saving again hides the published result until republish; payments list with method and access, detail, copy buttons, link to the roster; activity log newest first; student detail access list with reasons; create a student, instructor and system admin with the one-time code dialog; reissue; status; delete with the typed name.
10. System Admin: authoring without price, delete, archive, roster or instructor tabs; payments read-only; submissions read-only with the examiner's name and no download; no activity log, instructors or system admins pages.
11. Instructor: only assigned modules; submissions and draft marking with download; no publish button; participation for assigned modules only; no catalog editors, people pages or payments.
12. Staff account: verify email, change phone and password.

**Cross-cutting**

13. Language switch and theme switch on every page keep the user's place; both persist across reloads.
14. A staff user on student pages can browse but not buy or sit; a student on `/staff` sees the student-only message.

### B4. Resilience and edge states

- API stopped: every page shows its error state with "আবার চেষ্টা করুন" and keeps the last good content; nothing crashes to a blank page.
- Slow network (Playwright throttling, 3G profile): skeletons appear at once, no layout jump when content arrives.
- Offline during a sitting: the offline banner shows, answers are kept, saving resumes when back online.
- Token expiry: the refresh happens silently; an invalid refresh returns to login with `next`.
- Error responses the UI must handle: 400 validation on each form, 401, 403, 404 (bad links show "কিছু পাওয়া যায়নি"), 409 conflicts, 429 rate limit and lockout, 503 gateway unavailable.
- Empty states on every list (no categories, no modules, no exams, no notices, no payments, no results, no roster, no submissions, empty search).
- Very long Bangla and English titles, a module with 30 exams, an MCQ with 100 questions (paper stays smooth), a page of 100 rows on staff lists.

### B5. Performance

- From the production build output: first-load JavaScript per route; every student route under 150 KB gzip (the PDF viewer only on the routes that need it).
- Lighthouse (mobile, simulated 4G) on `/`, `/mcq`, a module page and an exam page: LCP under 2.5 s, CLS under 0.1, TBT reported. Record the scores.
- No console errors or warnings on any page. No failed network requests on the happy paths. No request repeated in a loop, no one-request-per-row pattern where a page lists many items, and catalog data cached between navigations.

### B6. Privacy and security in the UI

- Access tokens never in `localStorage` or `sessionStorage` (memory only); the refresh cookie is HttpOnly.
- A student never sees another student's phone, email, answers or marks; the leaderboard shows names only; the teacher's name never appears to a student; `valId` never appears to a student.
- Staff pages send `noindex`; student pages are indexable.
- Error pages show no stack traces or raw server messages.

### B7. Split-host check

With the split-host run: the admin host's `/` opens the admin panel; student routes on the admin host redirect to the student site; `/staff` on the student host redirects to the admin host; links between the two surfaces are absolute and correct; each login page accepts its own audience and links the other; sessions stay separate per host.

## Part C — Staging pass after the deploy

Run after WS7 deploys, on `https://srv2089893.hstgr.cloud`, with test accounts only. Tell the client before any payment (the Master Admin purchase email goes to the client's inbox).

- Smoke every route as a visitor and as each role; fonts load over HTTPS; no mixed content; no console errors.
- One registration with a real inbox: the verification email arrives from `SYMAM LAW <no-reply@symamlaw.com>` and reads well in Bangla.
- Sandbox checkout with a card and with bKash: the IPN arrives (check the API log), access opens, the receipt reaches the student, and the "New purchase" email reaches the Master Admin. Failed and cancelled checkouts grant nothing and send nothing.
- One short live MCQ (time moved in the staff editor) taken on a real phone over mobile data: autosave, reload, submit, deadline, review.
- Written upload of a real scanned PDF from a phone.
- Repeat B1 screenshots for the five most important pages on the staging build, to catch environment-only differences.

## Defect report

Write `docs/exam-site/reports/ui-final-qa.md`:

- Summary: date, commit tested, environment, accounts used (no passwords), time changes made to demo exams.
- The A1, A2 and A3 matrices.
- One row per defect: ID (QA-001…), severity, area, route, role, viewport, locale, theme, steps, expected (cite the rule: `decisions.md`, a prompt rule, the copy guide, or the spec value), actual, screenshot path, status (open, fixed in `<commit>`, sent to integrator, accepted by Shahriar).

Severities:

| Severity | Meaning |
|---|---|
| Blocker | A business rule broken, data shown to the wrong person, a flow that cannot be completed, a crash, a UI call with no API |
| Major | A role sees a control it cannot use, a missing user-facing error message, a page broken at a supported size, a serious accessibility violation, wrong data shown |
| Minor | A copy-guide violation, an inconsistent term, a token or size off the spec, a missing empty state |
| Polish | Spacing, alignment, animation, small visual inconsistencies |

## Exit criteria

- Zero open Blockers. Zero open Majors, unless Shahriar accepts one in writing in the report.
- Every route checked at every viewport, both locales and both themes, with screenshots.
- A1–A3 matrices complete; every gap either fixed, listed as "deliberately unused", or accepted.
- axe: zero serious or critical violations; budgets met or the overrun recorded.
- `lint`, `tsc`, `npm test`, Playwright specs and the API suite green on the final commit.
- Part C passed on staging.

## Hand-over

Commit fixes and specs on `ui/ws8-qa` in small commits. Then send the integrator:

```
QA DONE: ui/ws8-qa
Last commit: <hash>
Defects: <n> found — blockers <n> (open <n>), majors <n> (open <n>), minors <n>, polish <n>
Fixed on branch: <n>; sent to integrator: <list of IDs>
Checks: lint 0 errors, tsc clean, tests <n>, playwright <n> specs in 3 browsers + 2 devices, axe 0 serious, API suite ok
Budgets: <route sizes / Lighthouse summary>
Report: docs/exam-site/reports/ui-final-qa.md
```

The integrator merges `ui/ws8-qa` (with or without the Playwright dev dependencies), rebuilds, redeploys, and asks the QA agent for Part C.
