# UI audit findings: Agent A (public site and student entrance)

**Status: COMPLETE, with caveats.** Every route in the slice was reached and snapshotted after the reset, every flow walk has a result, and every checklist item is ticked or listed as not run with a reason. Caveats: real devices, Edge and Samsung Internet, and the real Google sign-in are manual QA. The mid-session expiry was simulated (mocked 401 responses), and the phoneless set-phone state was reached with a stub (phone cleared in the QA database). Open findings: 20.

## Header

- **Agent:** A. **Date:** 2026-10-26.
- **Database:** `coaching_center_qa_1`, reset on 2026-10-26 after Shahriar's approval. Verified after the reset: Flyway 40 of 40 migrations successful, 13 students, 2 instructors, 1 Master Admin, 1 System Admin, 14 exams (2 with no date).
- **Reset record (section 2.2a):**
  - Backup: `coaching_center_qa_1-2026-10-26.dump`, in the agent's scratchpad (`scratchpad\backups\2026-10-26\`), outside the repo. `pg_restore -l` lists 51 table-data entries.
  - Drop and recreate: done, with the name checked before each command.
  - Seed result: no `ERROR` lines, no `already in use` messages, no older-catalog stop, no Flyway error. Roles and catalog confirmed (see above).
  - Pre-reset snapshots kept in `snapshots/A/pre-reset/` as evidence. They are not cited as walks done in this round.
- **API 8081:** restarted by this agent on 2026-10-26 (Shahriar approved the stop). Effective `DB_URL` printed before start: `jdbc:postgresql://localhost:5432/coaching_center_qa_1`. Process: `java -jar target/api-0.0.1-SNAPSHOT.jar`, started from `F:\coaching-center-platform\api`.
  - Stdout log: `C:\Users\HP\qa-logs\api-8081.log`. Stderr log: `C:\Users\HP\qa-logs\api-8081.err.log` (empty).
  - Email codes (`DEV_EXPOSE_EMAIL_CODES=true`) were read from the stdout log for the verification and reset walks.
  - `FILE_STORAGE_DIR`: `C:\Users\HP\qa-storage\qa_1`.
  - Start-up log: "Started ApiApplication". No ERROR-level entries. Spring logged 8 "Encoded password does not look like BCrypt" warnings during login attempts (the no-password demo accounts). Not investigated; not a UI finding.
- **Web 3001:** the process belongs to the `qa-e2e` worktree. This agent did not rebuild or restart it, and its build commit was not verified. The repo HEAD and the audit worktree (`F:\cc-wt\audit-a`, branch `ui/audit-a`) are at `c2cd843`. `NEXT_PUBLIC_API_BASE_URL` points at 8081.
- **Secrets:** this agent's file `C:\Users\HP\qa-secrets\qa_1.env`. Values loaded in memory only. Nothing from the file is in this report. Login checks used the master admin, system admin, first instructor and test student passwords. The second instructor's login was not confirmed (not used by slice A).
- **Browsers:** Chromium (Chrome 156) at all widths. WebKit and Firefox (Playwright engines) at 360 and 1440 px. The engines are emulated, not Safari or Firefox on a device: emulated only, confirm on device.
- **Fallback (2.3):** not used.
- **Data written during the run** (all in `coaching_center_qa_1`): one test student registered and verified, then reset its password (account prefix `audit-a-r2-`); one test student registered in the first walk (prefix `audit-a-`) whose password is not kept; several INITIATED orders created through the buy flow for the seeded test student; four status updates on those orders to stage the success, failed and cancelled return states. No payment was made and no SSLCommerz page was loaded. Also in this round: a change-password and change-phone test account (prefix `audit-a-cp-`); an empty-state test account (prefix `audit-a-st-`) with 40 stub `PURCHASE_GRANTED` notice rows, whose phone was cleared in the database and then set again through the form; its password was reset through forgot-password with the log code. Also approved by Shahriar: 15 stub modules labelled "Long-list stub" in `live_exam_modules`, rostered to a test account (prefix `audit-a-long-`).

## Checklist (section 11.2 and 11.3)

| Item | State |
|---|---|
| Stack row checked, effective `DB_URL` printed and matched | done |
| Reset per 2.2a, recorded in header | done |
| Code and requirements pass, every route in the slice | done |
| Section 3.8 table, each row marked | done |
| Snapshots, every route, 360 / 390 / 768 / 1024 / 1440, bn and en, light | done: guest 600 shots (30 routes), student 210 shots (14 routes) |
| Dark snapshots at 360 and 1440, bn and en | done (guest and student) |
| Every role in the slice signed in and walked | done: guest, seeded test student, and the new test student (register, verify, forgot, reset, login). The phoneless set-phone state was reached with a stub (phone cleared in the QA database) |
| Browser coverage: Chromium all widths | done |
| Browser coverage: WebKit and Firefox at 360 and 1440, key pages | done: guest 6 key pages x 2 widths x 2 langs x 2 engines = 48 shots; signed-in 8 pages x 2 widths x 2 langs x 2 engines = 64 shots |
| Every flow walk has a result | done (table below) |
| Sections 3.1 to 3.7 run | done for 3.1 to 3.7 in A: 3.2 (text and headings measured, placeholders checked: none on the form pages), 3.5 (empty states and both long lists), 3.7 keyboard and 200% zoom (emulated), receipt print. PDF, slow network and 800 x 360 landscape belong to B |
| Coverage table | done (below) |
| Findings in section 7 format, confidence tagged | done |
| High or Blocker findings that were code-only, re-checked in the browser | done: A-01 re-checked in the browser; no code-only High remains |
| Header status matches the checklist | PARTIAL, as above |

**Open items:** none.

**Closed in this round:**
- My modules long list (approved by Shahriar): 15 stub modules added to `coaching_center_qa_1` (labelled "Long-list stub", copied settings from an existing module) and rostered to a new test account. At 390 px in English all 15 rows show with no overflow. At 360 px in Bangla all 15 show; the page is 13 px wider than the screen because of the header (A-06), and the card titles are white on white (A-21). Snapshots `my-modules-long-list__390__en__light__student.png`, `my-modules-long-list__360__bn__light__student.png`.
- Session expiry: walked mid-session with mocked 401 responses for the refresh and the next API call. The student is sent to `/login?next=…&reason=expired` with "Your session has ended. Log in again." A reload after the cookies are dropped goes to login without that message, because no session existed to expire. Snapshot `session-expired-midsession__390__en__light__student.png`.
- Placeholder contrast: no placeholder text on the login, register, forgot, reset and contact forms (labels are used), in English and Bangla.
- Empty states and long list (open item of the previous round): empty states for my modules, notices, routine and results are done, reached by a new test account with no grants. My modules and routine need "Try again" first (A-08). Notices with 40 rows: 20 per page with a "load more" control, no overflow at 390 px.
- Set-phone for a phoneless account: reached by clearing the phone in the QA database (a stub). The form renders, a new number saves, and the stored number is normalised to `+880…`. A real Google-only account is still out of scope.
- Contrast beyond the headings: measured with a WCAG check on 13 guest and student pages (light theme, 390 px, English and Bangla). Findings A-17 to A-20. One element ("⚖️ BJS Exam", white on white) is measured in the DOM but not visible in the snapshot, so it is not counted until it is checked on screen.

## Flow walks (plan section 4)

| Walk | Result |
|---|---|
| 1. Guest browses MCQ and Written doors to exam | pass (no login; no questions or PDFs on the exam page) |
| 2. Guest buy returns to the same exam | **fail**, A-01 (confirmed again after the reset) |
| 3. Register, verify with the log code, login before verification, login | **pass**: register (success state); login before verification reaches `/`; verify on `/account` with the log code moves the account from "Not verified" to verified. Snapshots `flow-account-unverified__390__en__light__student.png`, `flow-account-verified__390__en__light__student.png` |
| 4. Forgot password: request, code from log, reset, login with new password | **pass**: the request shows "if an account exists, a code has been sent"; the code is in the log; reset submitted; login with the new password reaches `/`. Not tested: the old password is rejected. Snapshots `flow-reset-form__390__en__light__guest.png`, `flow-reset-result__390__en__light__guest.png` |
| 5. Google path | `/login/google` renders; without a code it shows "Login not done. Try again". The Google button is disabled ("Coming soon") unless `NEXT_PUBLIC_GOOGLE_ENABLED=true` (A-04). Real round trip: not run (out of scope). |
| 6. Student with access sees modules, notices, routine. Without access, empty states | with access (seeded test student): notices pass (3 unread); my modules and routine pass after "Try again" (A-08). Without access: no seeded student without grants was found, so this is not reached |
| 7. Purchase: button, summary, payment method, every return state | **pass with defects**: buy sheet and summary pass; gateway redirect stopped (external navigation blocked, nothing paid). Return states, each with its own order: pending (real INITIATED order), success (SUCCEEDED, module unlocked), failed, cancelled (the last three staged by status update in the QA database; the gateway callback is stubbed). Unknown transaction ID shows "We couldn't find this payment" (expected, see A-13). Defect: the raw key on the missing-reference button (A-09). Snapshots `purchase-result-pending__390__en__light__student.png`, `-success`, `-failed`, `-cancelled`, `purchase-result-unknown-tran__390__en__light__student.png` |
| 8. Landing against the two-source rule | `/` follows the landing layout at all widths. English batch names on the Bangla landing (A-11). Whether the landing and the mockup blend is a visual judgement, recorded in the snapshots, not decided here |

Requirement checks (section 4):
- Public catalog needs no login: pass.
- Module page is the exam list: pass.
- Routine is the same list: not verified. The routine page shows its error state on a direct load (A-08), and its content was not compared with the module list.
- Null `liveAt`: the module exam list shows "Date to be announced" and a dash for time; the exam page shows "Date to be announced" (snapshots `mcq-m-null-liveat__390__en__light__guest.png`, `exam-null-liveat-e0000000__390__en__light__guest.png`). Plan wording differs (section 4 "blank", section 3.5 "not announced yet"). **Decided:** the approved English copy is "Date to be announced" (`plans/ui-delivery/bangla-copy.md`, English copy list), which is what the page shows. The "blank" in decisions.md is the data state, and the page copy is correct. No finding.
- Take exam stays closed until the window: not in A (B's slice).
- Two doors lead to the right lists: pass.

## Coverage table (section 4 slice)

Status key: **audited** = snapshots at the widths the checklist names, plus a flow result where one applies. **partly audited** = snapshots exist, a named item is missing, with the reason.

| Route | Role(s) | Status | Evidence |
|---|---|---|---|
| `/` | guest, student | audited | guest 20 shots; student 14 shots |
| `/mcq`, `/mcq/c/[categoryId]`, `/mcq/m/[moduleId]` | guest; student (module) | audited | 20 each; student module 14 |
| `/written`, `/written/c/[categoryId]`, `/written/m/[moduleId]` | guest | audited | 20 each |
| `/exam/[examId]` | guest, student | audited | 20 + 14; null-date exam 390 px; buy walk |
| `/about`, `/contact`, `/help`, `/privacy`, `/refund`, `/terms` | guest | audited | 20 each |
| `/login` | guest; student (logged-in visit) | audited | 20 + 14 (A-14) |
| `/register` | guest | audited | 20; walk 3 |
| `/activate` | guest | partly audited | 20 shots; renders; the activation code belongs to admin-created accounts, none was created in this round |
| `/forgot-password` | guest | audited | 20; walk 4 |
| `/reset-password` | guest | audited | 20; walk 4 |
| `/login/google` | guest | audited | 20; error state; real sign-in not run (out of scope) |
| `/set-phone` | guest (redirects); student (has phone, redirects to `/`) | partly audited | 20 + 14; phoneless state reached by clearing the phone in the QA database (stub), save verified |
| `/account` | guest (redirects); student | audited | 20 + 14; verify walk done; change phone and password walked on a test account |
| `/account/payments`, `/account/payments/receipt` | guest (redirects); student | audited | 20 + 14 each; receipt prints to one A4 page |
| `/my-modules` | guest; student | audited | 20 + 14 (A-08, A-10) |
| `/notices` | guest (redirects); student | audited | 20 + 14 |
| `/routine` | guest; student | audited | 20 + 14 (A-03, A-08, A-10) |
| `/results` | guest (redirects); student | audited | 20 + 14; result states belong to B |
| `/purchase/result` | guest; student (four states + unknown ID) | audited | 20 + 14; four states + unknown ID shots |
| Buy controls (exam and module pages) | student | audited | flow shots; gateway stopped |
| not-found (shared) | guest, student | audited | 404 state, 20 + 14 |
| error (shared) | any | partly audited | not triggered in the browser; code only (A-02) |

## Section 3.8 expected-page table

Status values: `exists and works`, `exists but incomplete`, `MISSING`. "walked" means signed in and used; "rendered" means snapshotted only.

### Guest

| Expected | Status | Basis |
|---|---|---|
| Landing with both doors and sign-up | exists and works | rendered at all widths and languages |
| Public catalog: category, module, exam list, exam detail with price and access state | exists and works | rendered; buy controls present |
| About, contact, help, terms, privacy, refund | exists and works | rendered; footer links present |
| Login, register, forgot and reset password, email verification | exists and works | register, verify (signed-in account page), forgot and reset all walked |
| Google sign-in entry | exists but incomplete | disabled "Coming soon" unless the build flag is set (A-04) |
| Not-found page | exists and works | rendered |
| Generic error page with a way home | exists but incomplete | Retry only, no home link (A-02, code) |

### Student

| Expected | Status | Basis |
|---|---|---|
| Account and profile | exists and works | rendered; name and verify forms present |
| Change phone | exists and works | walked on a test account: new number shown on the account page, old number gone |
| Change password | exists and works | walked on a test account: dialog closes, new password logs in |
| My modules | exists but incomplete | works after "Try again"; false error on direct load (A-08) |
| Notices (reminders and result notices) | exists and works | rendered with 3 unread; not opened |
| Routine | exists but incomplete | not in the tab bar or menu (A-03); same load error (A-08) |
| Results and exam history | exists and works | rendered (state detail belongs to B) |
| Per-exam result and leaderboard | owned by B | not in A |
| Payment history and receipt | exists and works | history rendered; receipt prints to one A4 page |
| Purchase result (every state) | exists but incomplete | four states and unknown ID rendered and checked; raw key on the missing-reference button (A-09) |
| Buy exam and buy whole module | exists and works (exam walked to gateway) | whole-module button present, not walked |
| Take exam and the later MCQ attempt | owned by B | not in A |
| Written upload and replace | owned by B | not in A |
| Logout | exists and works | logout then Back shows the guest home with no account data |
| Language switch on every page | exists and works | present on every page snapshot; English leftovers noted (A-11) |
| Session-expired handling | exists and works | walked mid-session with mocked 401 responses: login with `reason=expired` and the expiry message (A-level walk, simulated) |

## Findings

Severity count (open findings): Blocker 0, High 7, Medium 7, Low 6. Total 20. A-13 is closed (no defect).

### A-01: Guest who registers from the buy prompt loses the return to the exam
- Route / role / width / language / theme / browser: `/exam/[examId]` → `/login` → `/register` → `/login` / guest / 390 / en / light / Chromium
- What the user sees: the buy button goes to `/login?next=/exam/<id>`. "Create account" goes to `/register` with no `next`. After registering, "Log in" goes to `/login` with no `next`. After signing in the student lands on `/`, not on the exam.
- Expected: plan section 4, walk 2: "sent to register or login, then returned to the same exam, not to `/`".
- Evidence: `web/src/app/(auth)/register/page.tsx:109` (`Button href="/login"`) and `:185` (`Link href="/login"`); `web/src/app/(auth)/login/page.tsx:28` reads `next` but register never passes it on; buy link `web/src/components/student/catalog/exam-page.tsx:142`. Browser walk (post-reset): `flow-buy-login__390__en__light__guest.png`, `flow-register-success__390__en__light__guest.png`.
- Severity: High
- Type: navigation
- Confidence: confirmed in browser (post-reset flow walk)

### A-02: Generic error page has no way home
- Route / role / width / language / theme / browser: any route that throws / all roles / n/a / en and bn / n/a / not run in browser
- What the user sees: Retry only. If Retry fails there is no link to home.
- Expected: plan 3.8, guest row: "a generic error page with a way home".
- Evidence: `web/src/app/error.tsx:24`. Compare `web/src/app/not-found.tsx:15`.
- Severity: Medium (workaround: browser back)
- Type: missing-feature
- Confidence: read in code only

### A-03: Routine is not in the student tab bar or account menu
- Route / role / width / language / theme / browser: `/routine` / student / all / en and bn / light and dark / Chromium
- What the user sees: the tab bar (Home, My exams, Results, Notices, Account) and the account menu do not list the routine. Reached from home "See all" and from the results hub.
- Expected: feature-scope.md lists the personal routine as a student screen. The client mockup puts the routine inside the batch pages, so the mockup does not settle the nav.
- **Decided (recommendation adopted):** keep the five tabs and add a "Routine" entry to the account menu. The tab bar is full, and the routine is reachable from there without a sixth tab.
- **Applied in the audit worktree only:** `F:\cc-wt\audit-a`, branch `ui/audit-a`, file `web/src/components/shell/student-shell.tsx` (new `MenuLink` to `/routine`, icon `CalendarDays`, label `catalog.routine.title`). Uncommitted. `tsc --noEmit` passes. Not yet verified in a browser: the 3001 build belongs to `qa-e2e`, and a separate build would be a fourth stack.
- Evidence: `web/src/components/shell/student-shell.tsx:23-27` (tabs), `:75-79` (menu); links at `web/src/components/student/home/home-page.tsx:225` and `web/src/components/student/result/results-hub.tsx:119`. Snapshot `routine__390__en__light__student.png`.
- Severity: Medium
- Type: navigation
- Confidence: confirmed in snapshot

### A-04: Google sign-in button is disabled unless the build enables it
- Route / role / width / language / theme / browser: `/login` / guest / all / en and bn / light / Chromium
- What the user sees: a greyed "Coming soon" button under the login form.
- Expected: plan 3.8, guest row lists "Google sign-in entry". The round trip is out of scope (plan section 9).
- Evidence: `web/src/lib/config.ts:60`, `web/src/app/(auth)/login/page.tsx:134-145`, `web/src/i18n/messages/en/auth.ts:13`.
- Severity: Low
- Type: missing-feature
- **Decided (recommendation adopted):** keep `NEXT_PUBLIC_GOOGLE_ENABLED` off for the handover, since the real Google round trip is manual QA (plan section 9). The disabled "Coming soon" entry is the expected state. The flag is confirmed off in the 3001 build (the snapshots show the disabled button).
- Confidence: flag state confirmed in snapshot (3001 build). The production build's flag is still to be set to match this decision.

### A-05: Exam page sends a module request with an empty ID
- Route / role / width / language / theme / browser: `/exam/[examId]` / guest and student / all / all / all / Chromium
- What the user sees: nothing visible. The network log shows a `404` for `/api/v1/public/live-exam-modules/` on every exam page load, because the module query runs before the exam has loaded (confirmed after the reset).
- Expected: no failed requests on a normal page load.
- Evidence: `web/src/components/student/catalog/exam-page.tsx:88` (`moduleId = data?.moduleId ?? ""`), `web/src/lib/api/exam/catalog.ts:133` (no `enabled` guard).
- Severity: Low
- Type: state
- Confidence: confirmed in browser network log

### A-06: On a signed-in page at 360 px, the header pushes the page wider than the screen
- Route / role / width / language / theme / browser: every student route / student / 360 / bn and en / light and dark / Chromium and Firefox
- What the user sees: the page is 364 to 369 px wide on a 360 px screen, so it scrolls sideways. The header (notification badge, language pill, theme button and the name button) is the widest element. Guests do not see it. WebKit did not overflow in the same pages (one engine, one run).
- Expected: plan 3.1, "no horizontal page scroll at 360 px".
- Evidence: `web/src/components/shell/student-shell.tsx:245-249` (bell, language pill, theme pill, user menu), `:65` (name button, `max-w-36`). Measured post-reset: `snap-student-results.jsonl` (sw 364 to 369, cw 360, all 14 student routes in all 4 Chromium combinations); Firefox signed-in 360 px overflow in 16 of 32 combinations (`xbrowser-student-results.json`). The overflow grows with the name: a 17-character name in Bangla at 360 px gives 373 px (13 px over), from the same header row (`my-modules-long-list__360__bn__light__student.png`).
- Severity: High (every signed-in page, first minute on a small phone)
- Type: overflow
- Confidence: confirmed in snapshot metrics (Chromium, Firefox)

### A-07: Section headings on the exam page are white on white, so they are invisible
- Route / role / width / language / theme / browser: `/exam/[examId]` and the Written exam page / guest and student / 390 (measured) / en and bn / light / Chromium
- What the user sees: the headings "About this exam", "Rules", "Paper language", "Starts" and "Price" have no visible text. The section bodies show, but the labels do not. On the null-date exam the Duration value "3 hours" is also white on a light card.
- Expected: plan 3.2, text meets 4.5:1 on every background.
- Evidence: DOM read (post-reset, `dom-exam.js`): each `h2` has computed colour `rgb(255,255,255)` on `rgb(255,255,255)`. Source: `web/src/components/student/catalog/exam-page.tsx:41-44` (`Section`, `h2` has no colour class). Snapshot `exam-null-liveat-e0000000__390__en__light__guest.png`.
- Severity: High (the price and start headings are missing on the screen where the client decides to buy)
- Type: contrast
- Confidence: confirmed in DOM and snapshot. Probable cause: an ancestor sets a white text colour; the fix belongs on the heading (set a text colour token).

### A-08: My modules and routine show a connection error on a direct load for a signed-in student
- Route / role / width / language / theme / browser: `/my-modules`, `/routine` / student / 390 / en / light / Chromium
- What the user sees: opening the page directly (or refreshing it) shows "Check your internet connection and try again." The list requests go out with no `Authorization` header and return 401. The refresh succeeds afterwards. "Try again" loads the list ("BJS Preliminary MCQ Package, 0 of 1 exams taken"). The notices page does not show this.
- Expected: a signed-in student sees their list on a direct load, with no false network error.
- Evidence: `race-check.js` (post-reset): requests with `auth=no`, then `auth=yes` after retry. Snapshots `my-modules__390__en__light__student.png`, `routine__390__en__light__student.png`. Probable cause: the list queries run before the session is restored, and the error branch treats the 401 as a network failure. Check `web/src/lib/auth/session-context.tsx` restore timing against `components/student/catalog/my-modules-page.tsx` and `routine-page.tsx`.
- Severity: High (a refresh on the core student page shows a false error in the first minute)
- Type: state
- Confidence: confirmed in browser (post-reset)

### A-09: Raw key `myModules.title` on the purchase result page
- Route / role / width / language / theme / browser: `/purchase/result` (no reference) / guest and student / 390 / bn and en / light / Chromium
- What the user sees: the button in the "no payment reference" state reads `myModules.title` in both languages.
- Expected: plan 3.4, no untranslated key names.
- Evidence: `web/src/components/student/purchase/purchase-result.tsx:127` (`t("myModules.title")`). The key exists only as `catalog.myModules.title` (`web/src/i18n/messages/bn/catalog.ts:122`, `en/catalog.ts:124`), not at the top level. Snapshots `purchase-result__390__bn__light__guest.png`, `purchase-result__390__en__light__student.png`.
- Severity: Medium
- Type: language
- Confidence: confirmed in snapshot (post-reset)

### A-10: Guest on my modules and routine is not sent to login
- Route / role / width / language / theme / browser: `/my-modules`, `/routine` / guest / all / en and bn / light / Chromium
- What the user sees: the guest stays on the page and sees "Check your internet connection and try again." Every other account page sends a guest to `/login?next=…`.
- Expected: plan 3.6, logged-out access redirects to a sensible page with a way back.
- Evidence: post-reset guest results: `/my-modules` and `/routine` have no redirect; `/account`, `/notices`, `/results`, `/account/payments`, `/account/payments/receipt`, `/set-phone` all redirect to `/login?next=`. Snapshot `my-modules__390__bn__light__guest.png`.
- Severity: Medium
- Type: navigation
- Confidence: confirmed in snapshot (post-reset)

### A-11: English labels on the Bangla landing page
- Route / role / width / language / theme / browser: `/` / guest / all / bn / light and dark / Chromium
- What the user sees: the batch list reads "BJS Foundation Batch", "BJS WRITTEN Exam Batch", "Bar Council Special Batch", "BAR WRITTEN Exam Batch", and the social labels read "Facebook", "YouTube", "LinkedIn".
- Expected: plan 3.4, no English leftovers on a Bangla page, except stored content.
- **Decided (recommendation adopted):** the generic words (Foundation, MCQ, WRITTEN, Special, Batch) are copy and should be translated. The proper names (BJS, Bar Council) and the brand labels (Facebook, YouTube, LinkedIn) stay as they are. **Not applied:** the Bangla wording for these words is not in `bangla-copy.md` and needs Shahriar's approval before it goes into the app.
- Evidence: `web/src/content/site-info.ts:40-47` (hard-coded English names). Snapshot `root__390__bn__light__guest.png`.
- Severity: Low
- Type: language
- Confidence: confirmed in snapshot. Decide whether these are stored content (keep) or copy (translate).

### A-12: Exam table clipped at 360 and 390 px, with no scroll cue
- Route / role / width / language / theme / browser: `/mcq/m/[moduleId]` / guest and student / 360 and 390 / en / light / Chromium
- What the user sees: the exam table ("Exam", "Date", "Time") is wider than its card. The Time column is cut off ("12:00 P", "9:35 AM") with no visible scroll hint. The page itself does not scroll: the table scrolls inside its box (`overflow-x: auto`; table 420 px, box 294 px at 360, 324 px at 390).
- Expected: plan 3.1, tables scroll inside their own box. A clipped value with no cue is a visible defect.
- Evidence: `tablecheck.js` (post-reset, same numbers). Snapshot `mcq-m-null-liveat__390__en__light__guest.png`.
- Severity: Medium
- Type: layout
- Confidence: confirmed in snapshot

### A-13: Purchase result for an unknown transaction ID (closed, no defect)
- Expected: a clear state for a payment that cannot be found. The page shows "We couldn't find this payment. If you paid from another account, log in with that account. Go home." The 404 from the status endpoint is the expected not-found path. Closed: not a finding. Snapshot `purchase-result-unknown-tran__390__en__light__student.png`.

### A-14: Logged-in student can open the login page
- Route / role / width / language / theme / browser: `/login` / student / all / all / light / Chromium
- What the user sees: a signed-in student who visits `/login` sees the sign-in form, not their home (post-reset: final path `/login` at all widths).
- Expected: plan 3.6, a signed-in user is not shown the login form.
- Evidence: `snapshots/A/login__390__en__light__student.png`.
- Severity: Low
- Type: navigation
- Confidence: confirmed in snapshot (post-reset)

### A-15: Signed-in header overlaps the logo at 390 px
- Route / role / width / language / theme / browser: every student route / student / 390 / en / light / Chromium
- What the user sees: the notification button sits over the end of the logo text ("ExamPort" reads as "ExamPor" with the bell on top of it). The header row is crowded once the name is shown.
- Expected: plan 3.2, nothing hides another element; plan 3.1, long names wrap or truncate without covering controls.
- Evidence: `snapshots/A/flow-account-unverified__390__en__light__student.png`, top of page. Header: `web/src/components/shell/student-shell.tsx:245-249`.
- Severity: Medium
- Type: layout
- Confidence: confirmed in snapshot (Chromium, 390 px)

### A-16: At 200% text size, the header pushes "Log in" off-screen and the login button runs past the edge
- Route / role / width / language / theme / browser: `/login` (also `/register` and `/contact` show the same header) / guest / 360 / en / light / Chromium (emulated)
- What the user sees: with the text doubled (approximated with CSS zoom 2 at 360 px, equivalent to 200% browser zoom on a 360 px phone), the page is 627 px wide. The header controls (language pill, theme button, "Log in") run past the screen, so "Log in" cannot be reached. The primary "Log in" button's right edge is at 405 px, so part of it is outside the screen.
- Expected: plan 3.7, 200% text zoom does not break cards or hide buttons.
- Evidence: `zoom-detail.js` (login page, 360 px): offending elements are the header row (right 627 px), the language button (392 px) and the theme button (481 px); submit button left 82 px, right 405 px.
- Severity: High (a guest cannot reach the login link at large text)
- Type: layout
- Confidence: emulated only, confirm on device (CSS zoom is an approximation of browser zoom)

### A-17: "Upcoming exams" section heading is white on cream, so it is nearly invisible
- Route / role / width / language / theme / browser: `/mcq` (and the exam page, which uses the same list) / guest and student / 390 / en / light / Chromium
- What the user sees: the heading above the exam list reads as a faint outline on the cream page. Measured contrast 1.1:1 (white text on `rgb(246,244,238)`).
- Expected: plan 3.2, text meets 4.5:1 (3:1 for large text).
- Evidence: WCAG check (`contrast-a.js`, English): element `h2` "Upcoming exams", 4 instances. Snapshot `mcq__390__en__light__guest.png` (top of the list).
- Severity: High (the main list heading on the door page; same cause family as A-07)
- Type: contrast
- Confidence: confirmed in snapshot and DOM. Probable cause: the same white heading colour as A-07.

### A-18: "Buy ›" links on exam cards are gold on white, below the contrast target
- Route / role / width / language / theme / browser: `/mcq/m/[moduleId]`, student results and payments / guest and student / 390 / en / light / Chromium
- What the user sees: the "Buy ›" link on each module exam card is gold (`rgb(200,150,46)`) on white at 14 px. Measured 2.67:1 (target 4.5:1).
- Expected: plan 3.2, links on cards meet 4.5:1. The buy action is the one the client is meant to press.
- Evidence: WCAG check, 14 instances across the module, results and payments pages. Snapshot `mcq-m-null-liveat__390__en__light__guest.png`.
- Severity: Medium (readable, but fails the contrast target on the purchase action)
- Type: contrast
- Confidence: confirmed in snapshot and DOM

### A-19: "Verified" label is green on white at 12 px
- Route / role / width / language / theme / browser: `/account`, `/account/payments` / student / 390 / en / light / Chromium
- What the user sees: the "Verified" label is green (`rgb(47,158,68)`) on white at 12 px. Measured 3.45:1 (target 4.5:1).
- Expected: plan 3.2, small text meets 4.5:1.
- Evidence: WCAG check, 6 instances. Snapshot `account__390__en__light__student.png`.
- Severity: Low
- Type: contrast
- Confidence: confirmed in DOM

### A-20: Unread notice count is white on orange at 14 px
- Route / role / width / language / theme / browser: every signed-in page (header bell) / student / 390 / en / light / Chromium
- What the user sees: the unread count badge on the bell is white (`#ffffff`) on orange (`rgb(232,89,12)`) at 14 px. Measured 3.58:1 (target 4.5:1). The token comment in `web/src/app/globals.css` says white reads on the orange in both themes; the measurement says otherwise for this size.
- Expected: plan 3.2, text on every background meets 4.5:1.
- Evidence: WCAG check, 24 instances across student pages. Snapshot `root__390__en__light__student.png` (header bell).
- Severity: Low
- Type: contrast
- Confidence: confirmed in DOM and snapshot

### A-21: Module titles on the My modules cards are white on white
- Route / role / width / language / theme / browser: `/my-modules` / student (with granted modules) / 360 and 390 / en and bn / light / Chromium
- What the user sees: each granted module card shows its title in white on the white card, so the module name is invisible. The status line below it ("0 of 1 exams taken") and the buttons show.
- Expected: plan 3.2, text meets 4.5:1. The module name is the first thing a student uses to find their paper.
- Evidence: DOM read on the seeded test student's real module, "BJS Preliminary MCQ Package" (`real-module-title.js`): at 390 px English and 360 px Bangla the title computes `rgb(255,255,255)` on `rgb(255,255,255)`, 16 px, weight 700. The same colour on the 15 stub cards (`long-measure.js`). Snapshots `my-modules-real-module__390__en__light__student.png` and `my-modules-real-module__360__bn__light__student.png`, and the stub snapshot `my-modules-long-list__360__bn__light__student.png`.
- Severity: High (the module name on the core student page is not visible)
- Type: contrast
- Confidence: confirmed in DOM on a real module, both languages

## Fix status (branch `ui/audit-a-fixes`, uncommitted)

Changes are in `F:\cc-wt\audit-a` on `ui/audit-a-fixes`, based on `c2cd843`. Nothing is committed or pushed. Checks run: `tsc --noEmit` passes; the full Vitest suite passes (one test was updated because the "ok" pill colour changed, `tests/pur-fnd/access-badge.test.tsx`). **Browser verification (build from `ui/audit-a-fixes`, served on 3004 against the QA API on 8081):** 19 of 19 checks pass in `verify-fixes.js`, and the 180 px reflow check passes on the login and MCQ pages (A-16). The 3001 build (owned by `qa-e2e`) was not rebuilt. The 8081 API was restarted by this agent to allow the 3004 origin (`WEB_ORIGIN=http://localhost:3001,http://localhost:3004`).

| Finding | Status | What changed |
|---|---|---|
| A-01 buy return lost at register | fixed | `register/page.tsx` reads `next` and carries it to the login links; `login/page.tsx` carries it to create-account |
| A-02 error page has no home link | fixed | `app/error.tsx` adds a home button (`common.guard.goHome`) |
| A-03 routine not in nav | fixed | "Routine" entry in the account menu (`student-shell.tsx`) |
| A-04 Google button disabled | no code change | decision: flag stays off for the handover |
| A-05 empty-ID module request | fixed | `usePublicModule` is disabled when the ID is empty |
| A-06 header overflows at 360 px | fixed in code | name hidden below 400 px; user icon shown in its place |
| A-07 exam-page headings invisible | fixed | root cause: `text-card` resolves to the card colour (white) as well as the size. Size token renamed to `text-card-title` in `globals.css`, and all 38 class uses updated |
| A-08 false error on direct load | fixed in code | `/my-modules` and `/routine` wrapped in `RequireStudent`, so the list queries start after the session is restored |
| A-09 raw key on purchase result | fixed | `catalog.myModules.title` |
| A-10 guest not sent to login | fixed | `RequireStudent` sends a guest to login with `next` |
| A-11 English batch labels | no change (finding withdrawn) | the names are the client's own poster names, kept in English on purpose (comment in `content/site-info.ts`, landing plan). Bangla labels would be new copy, so none was added. Ask only if Shahriar wants Bangla labels |
| A-12 clipped exam table | fixed, cue only | right-edge fade on phones (`module-page.tsx`). No "swipe" hint, because that copy is not approved |
| A-14 signed-in student sees login | fixed | `/login` redirects a signed-in student to their destination |
| A-15 header overlaps the logo at 390 px | fixed in code | same change as A-06 |
| A-16 header at 200% text | fixed, verified | below 400 px the language and theme switches move into one menu button (`SettingsMenu`), and the wordmark is hidden (`Logo compact`), for guests and students. The login page also lets its card and Google row shrink (`grid-cols-[minmax(0,1fr)]`, `flex-wrap`). At a 180 px layout (the reflow equivalent of 200% zoom on a 360 px phone) the login and MCQ pages fit with no sideways scroll, and "Log in" is on screen |
| A-17 "Upcoming exams" invisible | fixed | same token rename as A-07 |
| A-18 gold buy link | fixed | `text-text-accent` on the buy and "Sit now" links |
| A-19 green "Verified" label | fixed | new `--ok-text` token (light `#1f7a4d`, dark `#40c057`), used by the "ok" pill |
| A-20 white count on orange | fixed | badge uses `bg-warn` (`#c2410c`) |
| A-21 module titles invisible | fixed | same token rename as A-07 |

## Checks with no finding

- No `#` links found in `app/` or `components/`.
- No links from the `(site)`, `(auth)` or student components into the legacy `(admin)` group.
- Guest redirects to login with `next` for account, notices, results, payments, receipt and set-phone.
- Logout then Back shows the guest home with no account data (post-reset check).
- Receipt prints to one A4 page (post-reset check).
- Forgot password returns the generic message (no account enumeration on the screen).
- No horizontal overflow on guest pages at 360, 390, 768, 1024 or 1440 px, in any language or theme (post-reset: 600 shots, 0 overflow).
- Keyboard focus (post-reset, emulated short viewport at 360 px): on login, register, forgot, reset and contact, every tab stop stays on screen and shows a focus indicator. The on-screen keyboard itself was not tested (emulated only, confirm on device).
- No untranslated key names on guest pages other than A-09.
- The dark theme switches correctly (`data-theme` matched in every dark run).
- Email verification and password reset codes arrive in the API log and work end to end (walk 3 and 4). Real email delivery is not tested (manual QA).

## Not checked, and why

- The 800 x 360 landscape and slow-network checks: B's slice (section 11.3, B).
- Real devices (emulated only, confirm on device).
- The `phoneRequired` set-phone state with a real Google-only account: out of scope. The same state was reached with a stub (phone cleared in the QA database).
- Edge and Samsung Internet: covered by Chromium here, to be confirmed on device.

## Snapshots

- `snapshots/A/` (post-reset): 600 guest shots (30 routes x 5 widths x bn/en x light/dark), 210 student shots (14 routes; light at all widths, dark at 360 and 1440), flow and extra shots, the receipt print preview and the four purchase states.
- `snapshots/A/webkit/` (24 guest + 32 signed-in) and `snapshots/A/firefox/` (24 guest + 32 signed-in): key pages at 360 and 1440, both languages, light.
- `snapshots/A/pre-reset/`: the 822 pre-reset guest and student shots and the earlier WebKit and Firefox shots. Evidence only.
- This round's additions: `empty-my-modules`, `empty-notices`, `empty-routine`, `empty-results` (and `-after-retry` for my modules and routine), `notices-long-list`, `set-phone-phoneless`, `set-phone-saved`, `flow-change-password`, `flow-change-phone`.
- Names follow `<route>__<width>__<lang>__<theme>__<role>.png`. Role is `guest` or `student`. Flow and extra shots use a descriptive prefix.
