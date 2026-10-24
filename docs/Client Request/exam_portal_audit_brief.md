# Exam Portal Fast-Track: Readiness Audit Brief

**Outdated (2026-10-23).** This audit brief is not the forward plan. Its scope excludes payments, notices, and a student purchase flow, and it targets the old shared server. The audit it asked for is finished. Do not start its phases or follow its "stop and wait" protocol. The later execution order is the public catalog, written-result publish, SSLCommerz, Google sign-in, website notifications, and the student website.

**Audience:** AI coding/ops agent
**Owner:** Developer working for the coaching-center client
**Goal of this document:** Tell you exactly what to inspect, what to verify, what to report, and what you must NOT touch, so we can produce an accurate delivery estimate for a minimal public exam website.

---

## 0. TL;DR

The client urgently needs a **minimal website** on their existing domain/VPS with exactly three things:

1. **Home page** (simple landing, login CTA)
2. **Login** (candidate authentication)
3. **Exams** (candidates take **MCQ** and **written** exams)

Nothing else is in scope for this fast-track (no payments, course purchase, attendance, admin UI, notices, etc.).

The developer was told the backend exam segment is "fully wired." **Do not trust that claim. Verify it.** Your job is to audit and report. Your job is **not** to build anything yet unless explicitly told to.

**Current working estimate (to be validated or corrected by you): 3-5 working days**, assuming the backend is stable. See Section 9.

---

## 0.5 Execution Protocol: Work Step by Step (MANDATORY)

This brief is long on purpose, but **you must not try to do it all at once.** Work in phases. Do one phase, report briefly, then **stop and wait for the human to say "continue."**

### Rules of the protocol
1. **Read only what the current phase needs.** Read Section 0, Section 0.5, Section 1 (hard rules), Section 2.2 (when told), plus the sections listed for the current phase. Do not load or re-read the whole brief every phase.
2. **Persist your work to disk** so nothing is lost between phases:
   - `audit/PROGRESS.md`: running log. One short entry per finished phase (status counts, blockers, estimate delta).
   - `audit/phase-N.md`: detailed findings and evidence for that phase.
   - At the start of every phase, read `audit/PROGRESS.md` first.
3. **Stop at every checkpoint.** After finishing a phase, post a **checkpoint message** (format below) and wait. Do not start the next phase on your own.
4. **Stop early on a `BLOCKER`.** If a finding makes later phases meaningless (for example, the backend will not start), end the phase there, report it, and ask the human how to proceed.
5. **Stay inside the phase.** If you notice something relevant to a later phase, note it in `audit/PROGRESS.md` under "Carry-forward notes" and move on.
6. **The human can reorder, skip, or repeat phases.** Follow their instruction.
7. Any phase that needs SSH or other access to the VPS requires the human's explicit go-ahead first (Phase 6).
8. All earlier rules still apply in every phase: read-only by default, no secrets, VERIFIED/INFERRED/UNKNOWN tagging.

### Phases

| Phase | Name | Do these sections | Stop condition / gate |
|---|---|---|---|
| 0 | Orientation + Rules Extract | 2.1, 2.2, 5.1 | Rules Extract table done; sources, conflicts, and open rules listed. **BLOCKER if no exam rules found.** |
| 1 | Backend runs + database | 5.2, 5.5 | Know whether it builds, starts, migrates, and has seed data |
| 2 | Auth | 5.3 "Auth" items | Know whether candidates can actually log in, and how |
| 3 | Exam list + MCQ | 5.3 "Exam listing & eligibility" and "MCQ" items | Each item judged against the Rules Extract |
| 4 | Written exam | 5.3 "Written" items | Upload, limits, status machine, storage, privacy |
| 5 | Security and cross-cutting | 5.3 "Cross-cutting", 5.4 | CORS, IDOR tests, error format, exposed endpoints |
| 6 | Environment: local, VPS, domain | 6.1, 6.2, 6.3 | **Needs human approval before any SSH.** Read-only commands only |
| 7 | Frontend readiness | 7 | What exists vs. what must be built |
| 8 | Final report | 3, 8, 9, 10, 11, 12 | Compile phase files into one report; risk register; estimate (best/likely/worst); ranked questions |

### Checkpoint message format (keep it short, 15 lines max)

```text
PHASE <N> COMPLETE: <name>
Status counts: DONE-VERIFIED x | DONE-UNTESTED x | PARTIAL x | STUB x | MISSING x | BROKEN x | UNKNOWN x
Blockers: <none | list>
Key findings (max 5 bullets):
- ...
Estimate impact so far: <+0d | +Xd, reason, risk IDs>
Questions for the human (only if needed): ...
Files written: audit/phase-N.md, audit/PROGRESS.md
Next phase: <N+1 name>. Reply "continue" to proceed (or tell me to skip/change it).
```

Detail belongs in `audit/phase-N.md`, not in the chat message.

### Phase 8 note
Do not redo any investigation in the final phase. Assemble the final report from `audit/phase-*.md` and `audit/PROGRESS.md`, fill gaps only by asking the human, and mark anything never checked as `UNKNOWN`.

---

## 1. Hard Rules (read before running anything)

### 1.1 Mode of operation
- **Default mode is READ-ONLY.** Inspect, list, read, curl, and report. Do not modify, install, restart, delete, or deploy anything unless the human explicitly approves that specific action.
- If an action is ambiguous or potentially destructive, **stop and ask**.

### 1.2 Production VPS safety (this is a live server with other clients' apps)
The VPS hosts **active production applications**. Never:
- Delete, overwrite, disable, or broadly edit existing Nginx configs under `/etc/nginx/sites-available/` or `sites-enabled/`
- Restart or reload Nginx, MariaDB, PHP-FPM, PM2, or Supervisor without explicit approval (use `nginx -t` to validate only)
- Touch, migrate, rename, or drop MariaDB databases (`hatipara_ss_hospital_db`, `old_otonest_db`, `otonest_db_new`, `symamlaw_db`, etc.)
- Run `apt upgrade`, `apt dist-upgrade`, `do-release-upgrade`, or reboot (the server already reports "restart required")
- Run `next build` or any memory-heavy job on the VPS (0 swap, ~5.4 GiB available, multiple Next.js processes running)
- Kill processes on ports 3000, 3001, 3002
- Expose PostgreSQL (5432) publicly

### 1.3 Secrets
- **Never print, log, copy, or commit secrets** (DB passwords, JWT secrets, API keys, OAuth secrets, private keys, `.env` contents).
- When you inspect env/config files, report **which keys exist and whether they are set**, never their values. Example: `JWT_SECRET: present (value redacted)`.
- Do not write secrets into the report.

### 1.4 Honesty
- Mark every finding as **VERIFIED** (you ran it/saw it), **INFERRED** (you deduced it), or **UNKNOWN** (could not check). Never present an inference as verified.
- If a check cannot be run (no access, missing tool), say so. Do not guess.

---

## 2. Project Context

### 2.1 Product
A coaching-center platform being rebuilt from scratch:
- **Web:** Next.js (TypeScript)
- **Mobile:** Flutter (not in scope now)
- **Backend:** Spring Boot (Java 21 intended), REST API, serves both web and mobile
- **DB:** PostgreSQL
- **Auth:** Spring Security + OAuth 2.0 / Firebase Auth (per PRD; **verify what is actually implemented**)
- **Files:** S3-compatible object storage (**verify what is actually implemented**)
- **Notifications:** FCM (out of scope here)

### 2.2 Exam rules: source of truth is the PROJECT DOCS (not this file)

**This brief deliberately does not define the MCQ or written exam rules.** They have been updated in the project itself and may differ from older versions of the PRD. You must extract them yourself.

**Step 1: Locate the rule sources.** Search the project (repo root, `/docs`, `/mnt/project`, README files, PRD/spec/requirements `.md` files, OpenAPI spec, ADRs, issue/changelog notes) for the current exam rules. Typical candidates: `coaching_center_prd.md` and any newer file or section about examinations. List every file you used with its path and last-modified date.

**Step 2: Resolve conflicts.** If multiple documents disagree, prefer in this order: (1) the most recently updated document explicitly about exams, (2) the PRD's exam sections, (3) what the backend code actually implements. Do not silently pick one: record each conflict in the report and flag it for the human.

**Step 3: Build a "Rules Extract" table** (include it in the report, see Section 11). For each rule, record the rule, the source file and section, and whether the backend implements it. At minimum, cover these topics **if the docs address them** (mark `NOT SPECIFIED` if they don't; never invent a rule):

| Topic | MCQ | Written |
|---|---|---|
| Exam types, states, and lifecycle | | |
| Eligibility (who may take it) and attempt/retake limits | | |
| Availability window, duration, deadlines | | |
| Timer authority and auto-submit behavior | | |
| Pre-exam information shown to the candidate | | |
| In-exam behavior (navigation, answer changes, autosave, review) | | |
| Scoring (marks, negative marking, partial credit) | | |
| Submission rules (finalization, resubmission, locking) | | |
| Submission statuses / state machine | | |
| Upload rules (image count, size, formats), written only | n/a | |
| Evaluation flow (auto vs. manual) | | |
| Result visibility and publication rules | | |
| Answer/feedback visibility after submission | | |
| Security and integrity rules (what students must never see or access) | | |
| Any rule marked as an open decision or "TBD" | | |

**Step 4: Use the extract as the acceptance reference.** Every exam-related check in Sections 5 and 7 must be judged against the Rules Extract, not against assumptions in this brief or your own defaults. Where a section below mentions an exam behavior, it is a *prompt for what to check*, and the project docs decide what the correct behavior is.

**Do not:**
- Copy rules from memory or from this brief.
- Treat an unresolved/open decision as settled. List it in the questions for the human (Section 10).
- Continue if no exam rules can be found at all: stop and report that as a `BLOCKER`.

### 2.3 Known VPS state (last verified 2026-09-23, **re-verify everything**)

| Item | Recorded state |
|---|---|
| Host | `srv633348`, Ubuntu 24.04.4 LTS, 2 vCPU, 7.8 GiB RAM, ~82 GB free disk, **0 swap** |
| Reverse proxy | Nginx 1.24.0 on :80/:443 with ~10 existing vhosts |
| Existing Next.js (PM2) | ports **3000, 3001, 3002** (`symamlaw-frontend`, `symamlaw-admin`, `nginx-test`) |
| Existing DB | MariaDB 10.11.14 on 127.0.0.1:3306 (belongs to other apps) |
| PHP | php8.2-fpm, Laravel queue workers via Supervisor |
| **Java** | **Not installed** at last check |
| **PostgreSQL** | **Not installed** at last check (5432 free) |
| Docker | Not installed (not required) |
| Port 8080 | Free at last check (planned for Spring Boot) |
| Planned layout | `/opt/coaching-api/coaching-api.jar`, `coaching-api.service` (systemd), new Nginx server block |

**Important implication:** as of that document, the Spring Boot backend and PostgreSQL were **not yet deployed to the VPS**. The claim "backend is wired" may mean "works on a developer machine." Determine which.

The client's **domain is already configured** (per the human). Verify DNS, which hostname(s) to use, and whether TLS exists for it.

---

## 3. What You Must Deliver

Produce **one final report** (Markdown), assembled in Phase 8 from your per-phase files (see Section 0.5), containing:

1. **Executive summary** (5-10 lines): overall readiness, biggest risks, recommended estimate
2. **Rules Extract** (Section 2.2): the current MCQ and written exam rules, with source file/section for each, conflicts, and open decisions
3. **Status matrix** for every checklist item in Section 5 using the status codes in Section 4, judged against the Rules Extract
4. **Endpoint inventory** (Section 5.3): actual routes found vs. what the frontend needs
5. **Environment findings** (Section 6): local vs. VPS reality
6. **Risk register** (Section 8) with severity and estimate impact
7. **Recalculated estimate** (Section 9) with per-task adjustments and a confidence level
8. **Blocking questions for the human** (Section 10), ranked by how much they affect the schedule
9. **Recommended next steps** in order

Keep it factual and scannable. No marketing language. Put evidence (file paths, command names, HTTP status codes) next to each claim.

---

## 4. Status Codes (use these exactly)

| Code | Meaning |
|---|---|
| `DONE-VERIFIED` | Exists and you proved it works (ran it / got a real response) |
| `DONE-UNTESTED` | Code exists, but you did not or could not run it |
| `PARTIAL` | Exists but incomplete (say what is missing) |
| `STUB` | Endpoint/class exists but returns mock or empty data |
| `MISSING` | Not found |
| `BROKEN` | Exists but fails (include error) |
| `UNKNOWN` | Could not determine (say why) |
| `N/A` | Not needed for this scope |

Also tag each row with an **estimate impact**: `none`, `+0.5d`, `+1d`, `+2d`, or `BLOCKER`.

---

## 5. Audit Checklist

Work through these in order. Stop early and flag a `BLOCKER` if you find one that makes later steps meaningless (e.g., backend does not run at all).

### 5.1 Repository & project discovery
- [ ] Locate the backend repo, the Next.js repo (if one exists yet), and any shared docs
- [ ] Identify Spring Boot version, Java version in `pom.xml`/`build.gradle`, build tool
- [ ] Identify package structure (modular monolith?) and where exam-related modules live
- [ ] Check git state: current branch, uncommitted changes, last commit date, any open TODO/FIXME in exam code
- [ ] Check for existing frontend code (Next.js version, App Router vs Pages Router, API client, UI library)
- [ ] Look for README/runbooks/OpenAPI spec/Postman collection

### 5.2 Can the backend actually run?
- [ ] Does it build? (`./mvnw -q -DskipTests package` or equivalent; report result only)
- [ ] Does it start locally against PostgreSQL? Which profile/config is required?
- [ ] Are DB migrations present (Flyway/Liquibase)? Do they apply cleanly on an empty DB?
- [ ] Is there seed/demo data (a test student, a test MCQ exam, a test written exam)?
- [ ] Do the tests pass? How many exist for the exam module? (report count and result)
- [ ] Is Swagger/OpenAPI available and accurate?

### 5.3 Endpoint inventory (the core of the audit)
**Judge every exam-related item against the Rules Extract from Section 2.2.** The bullets below say *what to look at*; the project docs say *what correct looks like*. If a bullet refers to a behavior the docs do not require (or contradict), mark it `N/A` or flag the conflict.

For each endpoint below, record: **method + path, auth required, request shape, response shape, status code, status code from the matrix**. Test with real requests where possible.

**Auth**
- [ ] Login (email/password): what is returned? (JWT access token? refresh token? cookie?)
- [ ] Token lifetime and refresh mechanism
- [ ] Logout / token revocation
- [ ] Get current user / profile
- [ ] Password reset (needed? ask human)
- [ ] Google/Facebook/Firebase login: implemented or not? (flag: affects estimate)
- [ ] How are student accounts created? (admin-created, self-register, offline-linked) and can the test candidates actually log in today?

**Exam listing & eligibility**
- [ ] List exams available to the logged-in student (available / upcoming / completed)
- [ ] Exam detail returns the fields the docs say candidates must see before starting
- [ ] Eligibility enforcement: course/batch membership, enrollment status, time window, attempt limit
- [ ] Are exams in non-student-visible states (per the docs lifecycle) hidden from students?

**MCQ**
- [ ] Start attempt (creates attempt, returns server start time and deadline, per the docs on timer authority)
- [ ] Fetch questions (are correct answers **excluded** from the payload? **critical**)
- [ ] Save answers (autosave/partial) and restore after refresh
- [ ] Submit attempt (idempotent? double-submit safe?)
- [ ] Auto-submit on expiry, if the docs require it (server-side job or only client-side?)
- [ ] Late/after-deadline submission behavior
- [ ] Result retrieval respects the docs on result visibility and publication
- [ ] Concurrency behavior: what happens if many students start at once? any obvious N+1 queries or per-click writes?
- [ ] Scoring rules implemented exactly as the project docs define them (including any negative marking or partial credit)
- [ ] Question/option ordering and randomization: implemented as the docs require (or not required)

**Written**
- [ ] Fetch written exam instructions/deadline
- [ ] Create/get submission: status machine matches the statuses defined in the project docs
- [ ] Upload image: endpoint, multipart or pre-signed URL; enforced limits (size, MIME types, image count) match the docs
- [ ] Delete/reorder images before final submission
- [ ] Final submit and lock, following the docs on finalization and resubmission
- [ ] View own submission status and (later) marks/feedback
- [ ] Where do files go? (local disk, S3/MinIO, other) and are URLs private/signed?
- [ ] Image processing: any server-side resize/compression? (affects client work)

**Cross-cutting**
- [ ] Consistent error format (for frontend error handling)
- [ ] Pagination on exam lists
- [ ] Rate limiting present?
- [ ] Input validation present?
- [ ] Authorization: can student A read/modify student B's attempt, answers, files, result? **Test explicitly (IDOR check).**
- [ ] Server time endpoint or `Date` header usable for client clock-skew correction

### 5.4 Security spot checks (non-invasive)
- [ ] CORS: which origins are allowed? Will the production exam domain be allowed? Credentials mode?
- [ ] Token storage approach compatible with Next.js (httpOnly cookie vs bearer in memory)? CSRF implications?
- [ ] Are secrets externalized (env vars), not committed? (check for secrets in git history only by key name pattern, never print values)
- [ ] HTTPS-only assumptions, secure cookie flags
- [ ] Are Swagger/actuator/debug endpoints exposed unauthenticated in prod profile?
- [ ] File access: can private answer-sheet URLs be guessed or accessed without auth?

### 5.5 Database
- [ ] PostgreSQL version expected vs. what is available locally/on VPS
- [ ] Schema covers: user, student, course, enrollment, exam, question, option, exam_attempt, student_answer, result, written_submission (+ images)
- [ ] Indexes on hot paths (exam lookup by student/course, attempt by student+exam)
- [ ] Is there already real student/exam data that must be preserved, or is everything test data?

---

## 6. Environment Audit

### 6.1 Local (developer machine)
- [ ] Java version, Maven/Gradle, Node version, PostgreSQL version
- [ ] Is a local PostgreSQL running with the project's schema?

### 6.2 VPS (READ-ONLY checks only; request approval before any SSH if access policy requires it)
Run only inspection commands. Suggested set:

```bash
# resources
free -h; df -h; nproc; uptime
# listeners (look for 8080, 5432, 3000-3003)
ss -tulpn
# existing apps (do not modify)
pm2 list
systemctl list-units --type=service --state=running | head -50
supervisorctl status
# tooling
java -version 2>&1 | head -2
psql --version 2>&1 | head -1
node -v; npm -v
# nginx: validate only, never reload
nginx -v; nginx -t
ls /etc/nginx/sites-enabled/
# firewall
ufw status 2>&1 | head -20
# TLS
ls /etc/letsencrypt/live/ 2>/dev/null
which certbot
```

Record:
- [ ] Is Java 21 installed now? Is PostgreSQL installed now? (re-verify; the context doc may be stale)
- [ ] Is the Spring Boot API already deployed anywhere? (`/opt/coaching-api`, a systemd unit, a process on 8080)
- [ ] Which ports are free now for: Next.js (suggest 3003+) and Spring Boot (8080)?
- [ ] Which existing Nginx vhost, if any, already points at the intended exam domain? (read config, do not edit)
- [ ] Is there a valid TLS certificate for the target (sub)domain? Expiry date?
- [ ] Nginx `client_max_body_size` in effect for the exam vhost (written-exam photo uploads need roughly 10-50 MB headroom)
- [ ] Proxy timeouts adequate for uploads
- [ ] Node version on VPS vs. what the new Next.js app needs (existing apps run Next 14.x; check compatibility)
- [ ] Free memory headroom; swap status; whether a 2 GB swap is advisable (recommend, do not create)
- [ ] Backups: any in place for PostgreSQL once introduced?

### 6.3 Domain & DNS
- [ ] Exact domain/hostname for the exam site (apex, `exam.`, `app.`, other?)
- [ ] Where is DNS managed and does the A/AAAA record already point to `88.222.215.187`?
- [ ] Is an API hostname needed (e.g., `api.<domain>`) or same-origin path proxying (`/api` → :8080)? Recommend the simpler option and state tradeoffs (CORS vs. extra cert).

---

## 7. Frontend Readiness (Next.js)

Determine what exists vs. what must be built.

- [ ] Existing Next.js project for the new system? If none, scaffold is part of the estimate
- [ ] Router type, TypeScript, styling approach, component library
- [ ] Auth integration pattern (middleware route protection, session handling)
- [ ] API client layer (typed? generated from OpenAPI?)
- [ ] Design assets: logo, brand colors, copy for home page. **Who provides them?** (a common hidden delay)
- [ ] Mobile-web responsiveness requirement: candidates will likely use phones. Confirm.
- [ ] Browser support targets (low-end Android Chrome? in-app browsers?)

**Frontend screens required (and only these).** Screen contents and flows must follow the exam rules in the project docs (Section 2.2); the outline below is only a scope boundary:
1. Home (public)
2. Login
3. Exam list (available / upcoming / completed)
4. MCQ: instructions → exam runner (navigator, timer, autosave, review, submit) → result
5. Written: instructions → capture/select images → preview/reorder/remove → upload progress → confirm → status
6. Minimal shell: header, logout, error and loading states, session-expired handling

---

## 8. Risk Register (fill in with your findings)

Seed list of risks to confirm or dismiss. Add others you discover.

| # | Risk | How to check | Typical impact |
|---|---|---|---|
| R1 | Backend only works locally; not deployed on VPS | Section 6.2 | +1 to +2d (Java, PostgreSQL, systemd, Nginx, TLS, migrations) |
| R2 | Auth is Firebase/social-only or has no working email/password flow | 5.3 Auth | +0.5 to +1.5d |
| R3 | Candidate accounts don't exist / no way to create them | 5.3 Auth | BLOCKER for testing; +0.5d if a seed/import is needed |
| R4 | MCQ payload leaks correct answers | 5.3 MCQ | Security BLOCKER; backend fix |
| R5 | Timer is client-only; no server deadline/auto-submit | 5.3 MCQ | +0.5 to +1d |
| R6 | No server-side autosave/restore; refresh loses answers | 5.3 MCQ | +0.5 to +1d |
| R7 | Written upload not truly implemented (local disk only, no limits, public URLs) | 5.3 Written | +1 to +2d |
| R8 | Nginx body-size limit or proxy timeouts block photo uploads | 6.2 | +0.25d (small, but a classic launch-day bug) |
| R9 | CORS not configured for production domain; cookie/SameSite issues | 5.4 | +0.25 to +0.5d |
| R10 | IDOR: student can access others' attempts/results/files | 5.3 Cross-cutting | Security BLOCKER |
| R11 | No tests / no seed data; slow to verify flows | 5.2 | +0.5d |
| R12 | Memory pressure on VPS when adding Java + PostgreSQL + builds | 6.2 | Operational; recommend swap and off-box builds |
| R13 | Missing design assets/copy for home page | 7 | +0.5d wait time |
| R14 | Exam rules still open, ambiguous, or conflicting across project docs | Rules Extract (2.2): every `NOT SPECIFIED`, `TBD`, or conflict | Scope creep; get decisions in writing |
| R18 | Backend behavior differs from the updated project-doc rules (stale implementation) | Rules Extract vs. endpoint tests | +0.5 to +2d depending on how many rules differ |
| R15 | Load: exam start spike (hundreds of concurrent students) | 5.3 MCQ | Needs a quick load test; +0.5d |
| R16 | Existing Next 14 apps + new app share Node/PM2; port clashes | 6.2 | Low; use a new port |
| R17 | Stale `reboot required` flag; surprise restart affects all client sites | 6.2 | Operational; never reboot unannounced |

---

## 9. Estimate Model

### 9.1 Baseline (assumes backend is complete, stable, and already deployed)

| Work item | Baseline |
|---|---|
| Project setup, API client, auth/session, layout | 0.5 d |
| Home page | 0.5 d |
| Login + route protection + logout | 0.5 d |
| Exam list | 0.5 d |
| MCQ flow (instructions, runner, timer, autosave, submit, result) | 1.0-1.5 d |
| Written flow (multi-image upload, preview/reorder/remove, status) | 1.0 d |
| Deployment (Nginx block, TLS, PM2 or systemd) + real-phone testing | 0.5-1.0 d |
| **Baseline total** | **~4-5.5 d (3-5 working days when parallelized/streamlined)** |

### 9.2 What you must output
For each line above, give:
- **Adjusted estimate** after applying audit findings
- **Reason** for any change (reference risk IDs)
- **Confidence:** High / Medium / Low

Then provide three totals:
- **Best case** (everything in Section 5 turns out DONE-VERIFIED)
- **Likely case**
- **Worst case** (R1, R5, R7 all true)

Add: **the single largest uncertainty** and **what answer from the human would collapse it**.

### 9.3 Estimation rules
- Do not pad silently. Every added day must trace to a finding or a risk ID.
- Separate **backend-side work** from **frontend-side work**; the developer may not own the backend.
- Include **testing on real Android + iOS browsers** as an explicit line.
- Include **a rollback plan** line (how to take the exam site down without affecting other apps).
- State assumptions explicitly (e.g., "assumes TLS cert can be issued via Let's Encrypt without DNS delay").

---

## 10. Questions to Return to the Human

List the questions you cannot answer from the code or server. Rank by schedule impact. Likely items:

1. Is the backend already running on the VPS, or only locally? If local, who sets up Java/PostgreSQL there and when is a maintenance window available?
2. Which exact domain/subdomain should the exam site live on? Is DNS under our control?
3. How do candidates log in today (email/password, phone, Google)? Are accounts already created?
4. Are there real exams/questions in the database, or do we need to enter test data?
5. How many candidates will sit the first exam, and when? (drives load testing and deadline)
6. Any MCQ rule that the project docs leave open, contradict, or that conflicts with what the backend does (list each one specifically). **Only ask about rules the docs do not already answer.**
7. Any written-exam rule that the project docs leave open, contradict, or that conflicts with the backend (e.g., upload limits, resubmission). **Only ask about rules the docs do not already answer.**
8. Who provides logo, branding, and home page copy?
9. Does the client expect admin-side evaluation of written scripts now, or only candidate-side submission? (Assumed: candidate-side only.)
10. Is a swap file and PostgreSQL installation on the production VPS approved?
11. Are there compliance or privacy expectations for answer-sheet photos (retention, access)?

---

## 11. Report Template

Use this structure for your output:

```markdown
# Exam Portal Readiness Report
Date: <date> | Auditor: <agent> | Mode: read-only

## 1. Executive Summary
- Overall readiness: <Green/Amber/Red>
- Recommended estimate: <X-Y working days> (confidence: <H/M/L>)
- Top 3 risks: ...
- Blockers: ...

## 2. Rules Extract (from project docs)
Sources used: <path, last modified>
| Topic | Type (MCQ/Written) | Rule | Source (file + section) | Backend implements? |
|---|---|---|---|---|
Conflicts between documents: ...
Open / unspecified rules: ...

## 3. Status Matrix
| Area | Item | Status | Evidence | Estimate impact |
|---|---|---|---|---|

## 4. Endpoint Inventory
| Method | Path | Auth | Status | Notes |
|---|---|---|---|---|

## 5. Environment Findings
### Local
### VPS
### Domain/DNS

## 6. Risk Register (confirmed / dismissed / new)

## 7. Estimate (baseline vs adjusted; best/likely/worst)

## 8. Questions for the Human (ranked)

## 9. Recommended Next Steps (ordered)

## 10. Appendix: commands run, files read (no secrets)
```

---

## 12. Definition of Done for This Audit

The audit is complete when:
- The Rules Extract exists, cites its sources, and lists every conflict and open rule
- Every checklist item has a status code (no blanks)
- Every `BLOCKER` and `+1d` or larger item has evidence attached
- The estimate has best/likely/worst cases with traceable reasoning
- No secrets appear anywhere in the report
- No changes were made to the VPS, databases, or existing apps
- The human can read the Executive Summary alone and decide whether to commit to a date

---

## 13. After the Audit (do not start without approval)

Once the human approves the estimate, the likely sequence will be:

1. Resolve blockers (backend deploy, auth, CORS, uploads)
2. Scaffold/finish the Next.js app on a **new port** (e.g., 3003) with its **own** Nginx server block
3. Build locally or in CI; ship build output to the VPS (never build on the VPS)
4. Deploy to a staging hostname first; test on real phones
5. Cut over to the final hostname; keep a rollback path
6. Monitor logs, memory, and upload failures during the first live exam

Wait for explicit instruction before starting any of this.
