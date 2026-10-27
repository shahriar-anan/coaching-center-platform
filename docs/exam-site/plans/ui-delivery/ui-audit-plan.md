# UI audit plan: pre-QA sweep before the client handover

Written 2026-10-26 for Shahriar. Three agents run it at the same time, on local builds. The aim is to find visible and structural UI problems before Shahriar's manual QA, so that QA spends its time on data correctness and the SSLCommerz purchase, which an audit cannot prove.

The audit reads code, reads requirements, and takes page snapshots. It does not fix anything. Every output is a findings file.

## 1. Scope

In scope: the exam site routes under `(site)`, `(auth)` and `(staff)` in `web/src/app`.

Out of scope: the legacy `(admin)` group (`/courses`, `/books`, `/book-orders`, `/students`, `/payments`, `/admins`). Course and book screens stay as they are ([decisions.md](../../decisions.md), "Payments and the server"). If an exam-site page links into that group, report the link, not the target page.

Not tested by the audit (left for manual QA, section 9): a real payment round trip, live Google sign-in, real-device behaviour, and whether the numbers shown match stored data.

Sources of truth the agents check against:

| Source | Governs |
|---|---|
| [decisions.md](../../decisions.md) | Product rules, exam rules, who may publish, language behaviour |
| [client-brief.md](../../client-brief.md) | Original client requirement (decisions.md overrides it where they differ) |
| [ui/feature-scope.md](../../ui/feature-scope.md) | Features and expected pages per role |
| [ui/client-mockup.html](../../ui/client-mockup.html) | Look of every student page except `/` (760 px column) |
| [landing-page-plan.md](landing-page-plan.md) and the old symamlaw.com style | The landing page `/` only (wide layout) |
| [bangla-copy.md](bangla-copy.md) | Bangla wording |

## 2. Environment (one stack per agent)

### 2.1 Database rules

- Use only `coaching_center_qa` and its numbered copies (`coaching_center_qa_1`, `_2`, `_3`).
- Off limits, no reads and no writes: `coaching_center_dev` (5,437 users, real-looking records) and `coaching_center_test` (the integration tests write to it and clear it). If a connection string names either, stop.
- The existing `coaching_center_qa` is not clean: it holds two revoked grants, one cancelled bundle order, an extra announcement, and it was seeded before the F1 fix. Do not audit against it as it is.

### 2.2 Stack status and ownership (as reported by the main DB agent, 2026-10-26)

All three stacks are up. Check this table before you start; if your row does not match, stop and report.

| Agent | Database | API | Web | Who runs the web server |
|---|---|---|---|---|
| A | `coaching_center_qa_1` | 8081, up | 3001, up | the `qa-e2e` worktree (not yours) |
| B | `coaching_center_qa_2` | 8082, up | 3002, up | your `audit-b` worktree, already pointing at 8082 |
| C | `coaching_center_qa_3` | 8083, up | 3003, up | the `qa-c` worktree (not yours) |

Rules that follow from this:

- **Your database is yours.** Reset only the database in your row, and only between rounds. Never reset or touch another agent's database.
- **Servers you do not own:** for A and C, the web server belongs to another worktree. Do not stop, rebuild or restart it on your own. If a reset needs that API stopped and restarted, ask the main DB agent or Shahriar first, then wait for the go-ahead.
- **The API lock is free.** The web build takes the QA lock and releases it when the build finishes. Running servers hold no lock, so other agents can build while stacks run. Do not hold the lock while idle.
- **`api/.env` still points at `coaching_center_dev`.** Every API start must override `DB_URL` with your own database in the environment, with no exception. Before any API start, print the effective `DB_URL`. If it names `coaching_center_dev` or `coaching_center_test`, do not start.
- **Only the stacks above exist.** If you are asked to run a fourth stack, stop and report.

### 2.2a How to reset your database

Do not run the cleanup script (`demo-cleanup.sql`) or any partial cleanup. It misses rows that the old staging seed created, so it does not give a clean state. If the seed stops with a message about an older catalog, that is not a cue to run the script. Do the full reset below instead. Reset only between rounds.

**Approved by Shahriar on 2026-10-26:** one full reset of each agent's own database (A `_1`, B `_2`, C `_3`) before that agent's next round. Anything walked before the reset is pre-reset evidence and is kept in a `pre-reset/` subfolder of the agent's snapshots. Findings cite post-reset snapshots for every walk they report as done.

Paths and the psql user come from [reports/ui-dev-qa-guide.md](../../reports/ui-dev-qa-guide.md). The password comes from the prompt or your secrets file, never from a command you paste into chat or a file in the repo. Replace `<db>` with your database name and `<pg-user>` with the local role.

1. **Stop your API.** Dropping a database fails while it has open connections. Stop only the API process you started for your stack.
2. **Back up** to a folder outside the repo (for example a dated folder in your scratchpad). Name the backup file after the database and the date. Do not commit it.
   ```
   "C:\Program Files\PostgreSQL\17\bin\pg_dump.exe" -h localhost -U <pg-user> -Fc -f "<backup-folder>\<db>-<yyyy-mm-dd>.dump" <db>
   ```
   If the backup fails, stop. Do not drop.
3. **Drop and recreate**, with the same name:
   ```
   "C:\Program Files\PostgreSQL\17\bin\psql.exe" -h localhost -U <pg-user> -d postgres -c "DROP DATABASE <db>"
   "C:\Program Files\PostgreSQL\17\bin\psql.exe" -h localhost -U <pg-user> -d postgres -c "CREATE DATABASE <db>"
   ```
   Check the database name twice before you press enter. The only names you may use are `coaching_center_qa_1`, `_2` and `_3`. Never `coaching_center_dev` or `coaching_center_test`.
4. **Start the API** on your port with `SPRING_PROFILES_ACTIVE=dev`, `DB_URL` set to `<db>`, and the seed variables from section 2.4 (`SEED_DEMO=true`, the `SEED_*` accounts, `DEV_EXPOSE_EMAIL_CODES=true`). Flyway runs the migrations and the seed runs on start. The seed logs nothing on success, so a clean start with no errors is the pass.

   **Stop at once** and do not continue if the startup log shows any of these:
   - `<NAME>_PHONE is already in use.`, for example `SEED_STUDENT_PHONE is already in use.` The database already holds a seed phone on another account. The seed does not overwrite accounts, so do not change the phone or delete the account by hand.
   - The seed's older-catalog stop. Its message tells you to run `demo-cleanup.sql` first. Do not run that script. Stop the API (if it is still running) and do the full reset in steps 1 to 3 above, then start again from step 4.
   - The seed stops because no Master Admin exists yet. Check that the `MASTER_ADMIN_*` values are set, then do the full reset and start again from step 4.
   - Any `ERROR` line from the seed (the `DemoContentSeed` classes) or a Flyway error. The seed runs in one transaction and rolls back on failure, so the demo data is missing and the database is not usable for the round.

   After a reset, if the same message appears again, stop and report it to the main DB agent. Do not retry in a loop.
5. **Confirm before the round:** the demo catalog is visible on the public catalog, and each seeded role can log in (System Admin, Instructor, Instructor 2, test student). Then start the round.
6. **Record the reset** in your findings header: the date and time, the backup file name, and the seed result.

If any step fails, stop, keep the backup, and report. Do not try a second reset on your own.

### 2.2b Your database must not touch another stack

- Your API process and your web server use your port pair and your database. Confirm the ports before you test: API 8081, 8082 or 8083, web 3001, 3002 or 3003, matching your row in 2.2.
- If your data is corrupted during a round, report it and stop. Do not clean up and carry on, because the findings after that point would be wrong.

### 2.3 Parallel agents must not share data

Grants, suspensions, announcements, orders and exam publishing change shared rows, and agents running together would interfere. Decision: **one database per agent**, because each slice needs to write (buy, grant, publish, sit).

| Agent | Database | API port | Web port |
|---|---|---|---|
| A: public and student entrance | `coaching_center_qa_1` | 8081 | 3001 |
| B: sitting, results, leaderboard | `coaching_center_qa_2` | 8082 | 3002 |
| C: staff and admin | `coaching_center_qa_3` | 8083 | 3003 |

The single-stack defaults are API 8080 and web 3000. The offsets exist only so three stacks run on one PC.

Each stack has its own web worktree, because `next build` writes `.next` into the folder and two builds in one folder collide. The API can run from one checkout if the ports, databases and file-storage directories differ (`FILE_STORAGE_DIR` per stack, so uploaded PDFs do not mix). The worktree owners for each stack are in section 2.2.

Fallback if the PC cannot hold three stacks: one agent writes data and the other two are strictly read-only on a snapshot of that database. State it in the findings header if used. The main DB agent has not set this up; it applies only if Shahriar asks for it.

### 2.4 Settings per stack

API:

- `SPRING_PROFILES_ACTIVE=dev`
- `DB_URL` pointing at that agent's database. Environment variables override `api/.env`.
- `WEB_ORIGIN` equal to that stack's web origin exactly (for A, `http://localhost:3001`). A mismatch makes every browser API call fail with a CORS error, which looks like a broken UI and is not.
- `SEED_DEMO=true`, `SEED_SYSTEM_ADMIN_*`, `SEED_INSTRUCTOR_*`, `SEED_INSTRUCTOR2_*`, `SEED_TEST_STUDENT_PASSWORD`
- `DEV_EXPOSE_EMAIL_CODES=true`, so verification and reset codes appear in the API log

Web:

- Build with `npm run build`, start with `npm run start`. Never `next dev`: dev mode hides production-only problems and its overlay pollutes snapshots.
- `.env.local` sets `NEXT_PUBLIC_API_BASE_URL` (that stack's API), `NEXT_PUBLIC_SITE_URL` and `NEXT_PUBLIC_STAFF_URL`. These are baked in at build time, so a changed value needs a rebuild.

Secrets:

- Each agent uses only its own secrets file, outside the repo:
  - A: `C:\Users\HP\qa-secrets\qa_1.env`
  - B: `C:\Users\HP\qa-secrets\qa_2.env`
  - C: `C:\Users\HP\qa-secrets\qa_3.env`
- Each file holds `SEED_TEST_STUDENT_PASSWORD` (students 5101 to 5105), `SEED_SYSTEM_ADMIN_PASSWORD`, `SEED_INSTRUCTOR_PASSWORD`, `SEED_INSTRUCTOR2_PASSWORD`, `MASTER_ADMIN_PASSWORD` and `DB_PASSWORD`.
- Load the values into the process environment. Never print them, copy them into a repo file, or put them in a findings file, a snapshot name, a commit or a chat summary.
- `DB_PASSWORD` is the local Postgres password for the QA databases only. Never use it on `coaching_center_dev` or `coaching_center_test`.
- `MASTER_ADMIN_PASSWORD` is the same value in all three files.
- Do not rely on file permissions to protect these files. On Windows `chmod` does nothing, so any process running as the same user can read them.
- Quote every value when loading. An unquoted value with a space broke the shell sourcing in an earlier run.
- Never write a password, token or the file's contents into a findings file, a snapshot name or a commit.

Reference for the commands and the variable table: [reports/ui-dev-qa-guide.md](../../reports/ui-dev-qa-guide.md). Where it disagrees with this section on the names above, this section follows the QA reset instructions Shahriar gave on 2026-10-26.

### 2.5 Snapshot tooling

`web/` has no browser-automation dependency (only Vitest). Do not add one to `web/package.json`. Install the tool in a folder outside the repo, or use the built-in browser. Snapshots are saved to `docs/exam-site/reports/ui-audit/snapshots/<agent>/` and named `<route>__<width>__<lang>__<theme>__<role>.png`.

## 3. Standard checks (every agent runs these on every page it owns)

### 3.1 Layout and overflow
- No horizontal page scroll at 360 px.
- Long Bangla and English titles, names and emails wrap or truncate with a visible full value, and never push a button off the card.
- Tables scroll inside their own box or collapse to cards. They never widen the page.
- Fixed or sticky elements (header, timer, bottom bar) never cover content or the on-screen keyboard's field.
- 760 px column pages stay in the column on wide screens. Only `/` is wide.

### 3.2 Visibility and contrast
- Text on every background meets 4.5:1 (3:1 for large text). Check status pills, disabled buttons, placeholders, links on cards.
- Disabled looks different from enabled. Focus rings are visible.
- Nothing is hidden by `overflow`, `z-index` or a clipped container: dropdown menus, dialogs, toasts, row menus.

### 3.3 Theme
- Light and dark are both checked where a theme switch exists. If only one theme exists, record that, and check that the page background and text colours are set explicitly so a browser dark mode does not invert half the page.
- Colours match the mockup tokens on student pages and the old-site style on `/`. The two styles must not leak into each other.

### 3.4 Language
- Switch between Bangla and English on every page. No untranslated key names, no English leftovers on a Bangla page and no Bangla on an English page, except stored content (titles, question bodies).
- Bangla glyphs are not clipped (line height), and the numerals follow the project's convention.
- The question page: the chrome (timer, save, submit, labels) follows the site language, while the paper body follows `paperLanguage`.

### 3.5 States
Every list and detail page: loading, empty, error (API down or 4xx/5xx), long list, and the null `liveAt` case ("date and time not announced yet" on the module exam list, with Take exam closed).

### 3.6 Navigation and flow
- Every link and button leads somewhere real. Record dead links, `#` links, and links into the legacy `(admin)` group.
- Back button, refresh and a deep link on each page behave correctly.
- Wrong role or logged-out access redirects to a sensible page, with a way back.
- After login, the user lands where they started or on a role-correct home.
- Logout leaves no previous-user data on screen (use Back after logout).

### 3.7 Device compatibility
Width matrix, all emulated: 360, 390, 768, 1024, 1440 px. Phone landscape (for example 800 x 360) on the sitting page.

| Check | Detail |
|---|---|
| Browser engines | Chromium, WebKit (Safari, for `100vh`, sticky and date input quirks), Firefox. Edge and Samsung Internet are covered by Chromium and flagged "confirm on device". |
| Touch | Tap targets of at least 44 px. Submit and Pay cannot be hit by accident (gap or confirm step). |
| Keyboard | The field being typed in stays visible above the on-screen keyboard (sign-up, login, set-phone, contact). |
| Text zoom | 200% browser zoom or large text does not break cards or hide buttons. |
| PDF | Written question PDF and watermark on phone and tablet widths. Upload control works with the file picker. |
| Network | Throttled slow connection on the sitting page: loading state shows, autosave and timer behave. |
| Print | Receipt page (`/account/payments/receipt`) prints on one clean page. |

Real iOS Safari PDF behaviour, real touch feel and Bangla font rendering on real Android phones are marked "emulated only, confirm on device".

### 3.8 Missing-page and missing-feature check (every agent, per user type)

An audit that only visits pages that exist cannot find a page that is missing. So each agent starts from the expected-page list below, marks each row `exists and works`, `exists but incomplete`, or `MISSING`, and files every non-working row as a finding of type `missing-page` or `missing-feature`. Severity: Blocker if a required journey in decisions.md or the brief cannot be finished without it, High if a user of that type would expect it in the first session, otherwise Medium. The list is a starting point. An agent adds any page a journey needs that is not listed.

| User type | Expected pages and controls | Owner |
|---|---|---|
| Guest | Landing with both doors and sign-up; public catalog (category, module, exam list, exam detail with price and access state); about, contact, help, terms, privacy, refund; login, register, forgot and reset password, email verification, Google sign-in entry; a not-found page and a generic error page with a way home | A |
| Student | Account and profile; change phone (every password account may); change password; my modules; notices (including reminders and result notices); routine; results and exam history; per-exam result; per-exam leaderboard; payment history and receipt; purchase result; buy exam and buy whole module; Take exam and the later MCQ attempt; written upload and replace; logout; language switch on every page; session-expired handling | A (entrance, account, purchase), B (sitting, result, leaderboard, history) |
| Instructor | Staff home; own modules and exams; exam view; submissions list for own module; evaluate a paper (marks, comment, evaluated PDF, save as draft); view results and leaderboard; own account with phone and password change; clear message that publishing is the Master Admin's | C |
| System Admin | Staff home; students list and detail; grant, revoke and suspend; categories, modules and exams management; orders and payments with detail; announcements; participation; system status; own account. No evaluate and no publish controls | C |
| Master Admin | Everything above, plus admins and instructors management; audit log; publish exam; publish written marks; read-only email and editable phone and password on first login | C |

Also checked by every agent on its own pages: the shared shell for that user type (header, navigation, footer, language switch, logout) lists every page the user type is meant to reach, and shows no link the type may not use. A page that exists but has no navigation entry or link to it counts as `missing-feature` (unreachable page).

## 4. Agent A: public site and student entrance

Database `coaching_center_qa_1`, ports 8081 and 3001. Roles used: guest, a new student (registered during the run), and the seeded test student.

Routes:

| Group | Routes |
|---|---|
| Landing | `/` |
| Catalog | `/mcq`, `/mcq/c/[categoryId]`, `/mcq/m/[moduleId]`, `/written`, `/written/c/[categoryId]`, `/written/m/[moduleId]`, `/exam/[examId]` (the pre-sitting exam page, as guest and as student) |
| Static | `/about`, `/contact`, `/help`, `/privacy`, `/refund`, `/terms` |
| Auth | `/login`, `/register`, `/activate`, `/forgot-password`, `/reset-password`, `/login/google`, `/set-phone` |
| Student account | `/account`, `/account/payments`, `/account/payments/receipt`, `/my-modules`, `/notices`, `/routine`, `/results` |
| Purchase | The buy controls on the module and exam pages, `/purchase/result` (every order state: pending, success, failed, cancelled) |

Flow walks:

1. Guest: land on `/`, take the MCQ door, browse category, module, exam. Confirm no login is needed and that questions and PDFs are not shown. Take the Written door and do the same.
2. Guest clicks buy: sent to register or login, then returned to the same exam, not to `/`.
3. New student: register, see the verification code in the API log, verify. Confirm login works before verification.
4. Forgot password: request, read the code from the log, reset, log in.
5. Google path: the button and the `/login/google` and `/set-phone` screens render and handle their error states. The real Google round trip is not run.
6. Student with access sees their modules, notices and routine. Student without access sees empty states that say what to do next.
7. Purchase screens: the button, the order summary (exam only vs whole module and the "covers later exams" wording), the payment method choices, and each return state on `/purchase/result`. The audit stops at the redirect to SSLCommerz and uses stubbed return states. It does not pay.
8. Landing against the two-source rule: `/` follows the old site, and the student pages follow the mockup. Record any page that blends them.

Requirement checks: the public catalog needs no login; the module page is the exam list; the routine is that same list; null `liveAt` shows blank date and time; Take exam stays closed until the window; two doors lead to the right lists.

## 5. Agent B: sitting, results, leaderboard

Database `coaching_center_qa_2`, ports 8082 and 3002. Roles used: the seeded test student (roster or purchase grant), a second student with no access, and one staff account to publish exams and marks.

Routes: `/exam/[examId]`, `/exam/[examId]/sit`, `/exam/[examId]/result`, `/exam/[examId]/leaderboard`, `/results`, `/routine`, `/notices` (result and reminder notices), plus the same-purpose staff views `/staff/exams/[examId]/leaderboard` and `/staff/exams/[examId]/results` for comparison.

Flow walks (each needs the exam's live window, so Agent B sets `liveAt` values in its own database to put exams before, inside, and after the window):

1. MCQ before the window: Take exam closed. Inside: opens. After: closed, with the one later attempt offered only if the student never tapped Take exam.
2. MCQ sitting: timer visible and not covering Q1 on 360 px; answer selection, change before deadline, save state, submit; deadline auto-post; blank paper scores zero and shows it.
3. MCQ result: the score shows at once. The chosen option, correct option and explanation stay hidden until the sitting ends, then appear.
4. MCQ leaderboard: closed until five minutes after the window, live participants only, ties share a rank, the later attempt is absent.
5. Written sitting: PDF viewer with the student's name and id watermark over it (legible but not blocking, present at every width, and still there in a fullscreen or new-tab view). Upload: wrong file type, over 80 MB, the latest file replaces the old one, no file at the deadline is "failed to submit" with zero.
6. Written result: a draft mark is invisible to the student (no marks, comment, evaluated PDF, leaderboard row). After the Master Admin publishes, it appears. A later save returns it to draft. The student never sees the evaluator.
7. `/results` and exam history list every state, including failed-to-submit and not-yet-published.
8. Account with no access opens `/sit` by URL: a clear blocked state, not a blank page or a stack trace.

Highest device risk: the sitting page on 360 px portrait, phone landscape, and WebKit; the PDF view; slow connection. Treat any control that is unreachable during a live sitting as High.

## 6. Agent C: staff and admin

Database `coaching_center_qa_3`, ports 8083 and 3003. Roles used: Master Admin, System Admin, Instructor (the first one), and Instructor 2 (to test module ownership).

Routes: `/staff`, `/staff/account`, `/staff/admins`, `/staff/announcements`, `/staff/audit`, `/staff/categories` (+ `/new`, `/[id]`), `/staff/modules` (+ `/new`, `/[id]`), `/staff/exams/[examId]` (+ `/leaderboard`, `/results`), `/staff/instructors`, `/staff/orders`, `/staff/participation`, `/staff/payments` (+ `/[tranId]`), `/staff/students` (+ `/[id]`), `/staff/submissions` (+ `/m/[moduleId]`, `/[participantId]`), `/staff/system-status`.

Role matrix: for each route, record what each of the three staff roles sees, and what a student or guest gets on the same URL. Expected from decisions.md:

| Capability | Master Admin | System Admin | Instructor |
|---|---|---|---|
| Publish an exam | yes | no | no |
| Evaluate a written paper | save as draft | no | own module only, draft only |
| Publish written marks | yes | no | no |
| Manage admins and instructors | yes | per the role rules | no |
| Set or reset another user's password | no | no | no |
| Change own phone | yes | yes | yes |

Any control that shows for a role that should not have it, even if the API would refuse the call, is a finding.

Flow walks:

1. Master Admin first login with the bootstrapped account: forced or offered phone and password change, and the email shown as read-only.
2. Exam authoring: create category, module, exam (MCQ and written), add questions or the PDF, set `liveAt` and `durationMinutes`, publish. Includes the five-minute spacing rule error, the null `liveAt` publish, and "set or clear `liveAt` returns the exam to draft".
3. Evaluation: instructor opens a submission, saves marks, comment and evaluated PDF as draft. Master Admin publishes. Instructor 2 cannot see or open Instructor 1's module submissions.
4. Student management: list, filters, a student's detail, grant (roster mark), revoke, suspend. Row menus open fully and are not clipped at the table edge.
5. Payments and orders: list, filters, detail by transaction, and each status colour.
6. Announcements, audit log, participation, system status: render with seeded rows, with an empty database state, and with a long list.

Density checks for this slice: tables at 768 and 1024 px, row menus and dialogs on touch, summary strips wrapping, long Bangla names in table cells, and the date formats.

## 7. Findings format

Each agent writes `docs/exam-site/reports/ui-audit/findings-<A|B|C>.md`. Header: agent, database, build commit, date, browsers and widths covered, and whether any fallback from section 2.3 was used. One entry per finding:

```
### <ID>: <short title>
- Route / role / width / language / theme / browser:
- What the user sees:
- Expected (cite decisions.md, feature-scope.md, mockup or brief section):
- Evidence: snapshot filename, file:line
- Severity: Blocker | High | Medium | Low
- Type: layout | overflow | visibility | contrast | theme | language | state | navigation | role | device | missing-page | missing-feature
- Confidence: confirmed in snapshot | read in code only | emulated only, confirm on device
```

Severity:

- **Blocker**: the client cannot finish a required journey (cannot sign up, buy, sit, submit, or see a result), or a role sees a control the rules forbid.
- **High**: the client sees it in the first minute, or it hurts a live sitting (broken landing, unreadable button, dead link, timer covering a question).
- **Medium**: a visible defect with a workaround (clipped text, a tight tap target, a stray English string).
- **Low**: polish.

Agents do not fix findings and do not edit app code. They may note a probable cause, with a file and line, to help the later fix.

## 8. Deliverables and merge

1. `findings-A.md`, `findings-B.md`, `findings-C.md` and the snapshots folders.
2. A coverage table in each file: every route in the agent's slice, marked audited, partly audited (say why), or not reachable.
3. The coordinator merges them into `docs/exam-site/reports/ui-audit/summary.md`: counts by severity, the cross-cutting issues that appear in more than one slice (for example one shared component causing overflow on three pages), a **page inventory** (route, role, purpose, exists or missing), and a **feature coverage by role** table against feature-scope.md.
4. A "requirements with no UI" list: anything in decisions.md or the brief that no screen supports.
5. A **missing pages by user type** table built from section 3.8: one row per user type, listing each missing or incomplete page or control with its severity, so Shahriar can see at a glance what the client will notice is absent before the client does.

## 9. What stays for Shahriar's manual QA

The summary ends with this list, adjusted to what the audit found:

- A real SSLCommerz sandbox payment: redirect, bKash and Nagad choices, success, fail and cancel returns, and access appearing after payment.
- Data correctness: the numbers, marks, ranks, names and dates shown match what is stored.
- The real Google sign-in round trip.
- Real devices: iPhone Safari (PDF and watermark), a mid-range Android phone (Bangla text, sitting page), one tablet.
- Email delivery on the real SMTP account (the audit reads codes from the log).
- Anything marked "emulated only, confirm on device".

## 10. Run order

1. All three stacks are up (section 2.2). Each agent checks its own row before starting.
2. Each agent resets its own database once, before its first round, using section 2.2a. Do not reset while another agent's round is running on a shared API.
3. Agents A, B and C run at the same time. Each does a code and requirements pass first (inventory and expected behaviour), then the snapshot pass, then the flow walks.
4. Coordinator merges, shows the summary to Shahriar, and Shahriar decides which findings are fixed before the client sees the site.
5. After fixes, reset your database again (section 2.2a) and rerun only the affected routes.
6. Each agent works until its completion checklist in section 11 is done. It does not end early.

## 11. Completion rule (applies to A, B and C)

An agent does not stop, and does not report "done", until every item in its checklist below is ticked. A stop is allowed only when the coordinator has accepted a written reason for a specific item. "A password file was not found" is not a reason to stop. It is a blocker to report while the rest of the work continues.

### 11.1 Blockers: what to do instead of stopping

- **Missing passwords:** resolved for all three agents by the secrets files in section 2.4. Read only your own file. If a key is missing or a login fails, that is a blocker: report the key name (never the value) and the item it blocks.
- **Missing stack or a missing reset approval:** write the blocker at the top of your findings file with the exact item it blocks. Send it to the coordinator. Keep doing everything that does not need that item: code pass, guest snapshots, dark theme, browsers, the 3.8 table, the coverage table.
- **Your own server crashes:** restart your own web or API process from your own worktree, using section 2.2 and 2.4. Note the restart in the header.
- **Someone else's server is down, or needs a restart:** do not touch it. Report it, and continue with items that do not need it.
- **Unexpected data corruption:** section 2.2b applies. Report it, and stop only the items that need that database. Continue the rest.
- **When a blocker clears** (passwords arrive, reset approved, server back): go back to the items it blocked. Do not leave them marked "blocked" if they can now be done.
- **Still blocked at the end:** the final report lists each open item with its blocker. The item stays open. It is not marked done or audited.

### 11.2 Checklist for every agent

- [ ] Stack row checked against section 2.2. Effective `DB_URL`, API port and web port printed and matched.
- [ ] Reset in section 2.2a done and recorded in the header (backup file name, seed result), or a coordinator-accepted reason is written.
- [ ] Code and requirements pass done for every route in the slice (section 4, 5 or 6).
- [ ] Section 3.8 expected-page table filled for every user type the slice touches, each row marked `exists and works`, `exists but incomplete` or `MISSING`.
- [ ] Snapshots for every route in the slice at 360, 390, 768, 1024 and 1440 px, in Bangla and English, light theme.
- [ ] Dark theme snapshots at 360 and 1440 px, in Bangla and English, for every route in the slice (the theme switch exists, so this is required).
- [ ] Every role in the slice has been signed in and walked (section 3.8 rows and the flow walks in the slice's section). Each role's screens are snapshotted.
- [ ] Browser coverage: Chromium at all widths. WebKit and Firefox at 360 px and 1440 px for each key page of the slice. Each one not run is listed as "not run" with a reason, not left out.
- [ ] Every flow walk in the slice has a result: pass, fail (a finding) or blocked (with the blocker).
- [ ] Section 3.1 to 3.7 checks run on the snapshots, with a note for each page that passed.
- [ ] Coverage table: every route in the slice is `audited` (has snapshots and a flow result), `partly audited` (with the missing item named), or `not reachable` (with the reason). No route is left as "code only" if it has a snapshot.
- [ ] Every finding uses the section 7 format, with its confidence tag. A finding that is "read in code only" and rated Blocker or High is re-checked in the browser before it is marked final.
- [ ] Findings file header status line updated to match the checklist. "Complete", "partial" or "blocked" must be true.

### 11.3 Extra checklist by agent

- **A:** Register, verify, forgot-password, reset-password and set-phone walks completed with codes from the API log. The buy path for a guest is walked to the redirect (stop before SSLCommerz). Student home and the routine page walked as a student. Google entry checked as shown and disabled, with the build flag noted.
- **B:** Student with access: sitting (MCQ and written), result, leaderboard, `/results`, `/routine`, `/notices` walked in the live window and outside it. Sitting page at 800 x 360 landscape. Slow network throttle on the sitting page. A student with no access on `/sit`. Written upload (wrong type, over 80 MB, replace). Draft mark hidden and published mark shown (with a Master Admin publish, done through the staff account in B's own database).
- **C:** Each of the four roles (Master Admin, System Admin, Instructor 1, Instructor 2) signed in. Role matrix from section 6 filled by a real session, not by code. Each of the 25 staff routes snapshotted for every role that can open it. Exam authoring walk (create, publish, the five-minute rule, the null `liveAt` publish). Evaluation walk (instructor draft, Master Admin publish). Instructor 2 cannot open Instructor 1's submissions. Student management row menus and dialogs at the table edge.

### 11.4 What the final report must say

- The checklist for the agent, with each item ticked, blocked or not run, and the reason.
- Counts by severity, the top five findings, and any route not reached.
- A plain statement: "complete" only when every checklist item is ticked.
