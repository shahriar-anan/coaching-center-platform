# Client review on staging: branch, seed, two hosts, deploy

Written 27 October 2026 on branch `demo/client-review` at `1d6ddb1`. Status: plan, approved by Shahriar on 27 October 2026. Nothing below is implemented yet.

**Audience:** the agent helping Shahriar. **Outcome:** the client opens a student site and an admin site on the new VPS, signs in with easy demo accounts, and finds every case already set up: bought packages, results, upcoming 11 PM exams, and papers to mark. He reports what needs changing.

[vps-staging-deploy.md](vps-staging-deploy.md) still governs the box: its hard stops apply here unchanged (never print env values, show every server write and wait for OK, build on the PC, one API process, staging profile only, sandbox only). This file adds to it and replaces its slice 7 (the seed).

## Decisions (27 October 2026)

| # | Decision |
|---|---|
| R1 | `demo/client-review` is the client approval branch. All uncommitted work is committed on it in three groups (API, web, docs). Shahriar pushes; the agent does not. |
| R2 | The seed is rebuilt to the shape in section 2. It replaces the current `DemoContentSeed` data (3 packages, 14 exams, 5 + 8 students). |
| R3 | **No scheduled refresh job.** The seed runs once at deploy and inserts only. It creates one 11 PM exam per day for the 7 days after the run, so "the next two days" always have an 11 PM exam during the review. A re-run changes nothing. A reset is cleanup plus a run. |
| R4 | Every seeded account has the password `root1234`, read from one env value `SEED_DEMO_PASSWORD`. The password is never written in code, tests or docs other than this decision line. Account identities (names, phones, emails) are fixed in code. |
| R5 | Two hosts. Student site: `https://srv2089893.hstgr.cloud` (certificate exists). Admin site: `https://admin.<ip-with-dashes>.nip.io`. Fallback if Let's Encrypt refuses nip.io: `admin.<ip-with-dashes>.sslip.io`. |
| R6 | Nginx basic auth on the **pages** of both hosts, plus a **gate cookie** on `/api/`, except the two SSLCommerz callbacks (section 4). The website sends `Authorization: Bearer …` on every API call, which replaces the browser's saved basic-auth credentials, so basic auth directly on `/api` would reject every signed-in request. Instead, a response that passed basic auth sets `review_gate=<secret>` (Secure, HttpOnly, 30 days) and `/api/` answers 401 without it. Basic auth on the website alone would leave the API, and so the `root1234` admin, open. |
| R7 | `WEB_ORIGIN` lists the student host first. The payment return (`LiveExamOrderController.websiteOrigin`) sends the browser to the first origin. |
| R8 | Every exam is 20 minutes, MCQ and written. All dated exams are at 23:00 Asia/Dhaka. |

## 1. Commit the working tree

1. Local gate first, as in deploy slice 0: `mvn test` from `api/` against local PostgreSQL, then `npm run lint` and `npm test` from `web/`. Record the results. If something fails, stop and report; do not commit around a failure.
2. Check that nothing still imports the deleted `web/src/components/staff/results/publish-results-button.tsx` (lint covers this).
3. Three commits:
   - **API:** `api/` (release workflow and V42–V44, archive, permissions, audit, error codes, tests).
   - **Web:** `web/src` and `web/tests` (staff UI, student UI, shared components, i18n, API clients).
   - **Docs:** `docs/` (plans, prompts, reports, this file).
4. Never stage `node_modules/`, `assets/`, `.claude/` or `.heavy.lock`. Add the last three to `.gitignore` in the docs commit (`node_modules/` at the root is not ignored today either; add `/node_modules/`). The `*.log` files are already ignored.
5. No push (R1).

**Exit:** `git status` is clean, and the test results are recorded in the report.

## 2. The seed

Code: `DemoContentSeed`, `DemoContentSeedQuestions`, `DemoContentSeedRunner` in `com.coachingcenter.api.exams`. Keep what works: the prod refusal, the staging/dev-only rule, fixed UUIDs with table prefixes, `storePdf`, notices through `StudentNoticeWriter`, one transaction, the announcement written per student. Reuse the existing Bangla question lists.

Change:

- **Insert only.** Every insert is `ON CONFLICT DO NOTHING`. Remove `refreshSchedule`, the sitting and order upserts, and the delete-and-rewrite of notices. A second run writes nothing (R3). The "older catalog, run cleanup first" refusal stays.
- **Schedule** is computed from the run date `D` (today in Dhaka at run time): `at23(days)` = `D + days` at 23:00 Asia/Dhaka. Remove `noon`, `afternoon` and the "live now" exam.
- **Accounts** in code, password from `SEED_DEMO_PASSWORD` (8–72 characters, required). Remove `SEED_SYSTEM_ADMIN_*`, `SEED_INSTRUCTOR_*`, `SEED_INSTRUCTOR2_*`, `SEED_TEST_STUDENT_PASSWORD`, `SEED_STUDENT_EMAIL`. Remove the 8 no-login students.
- **Duration** 20 minutes for every exam (R8). Written full marks 50, MCQ 10 questions at 5 marks (full marks 50), pass 40 percent.

### 2.1 Accounts

All `ACTIVE`, email verified, password `SEED_DEMO_PASSWORD`. Sign-in works with the phone or the email. Before writing, the seed refuses if any of these phones or emails belongs to a non-seed account (existing `ensureUser` check, extended to email).

| Id | Role | Name | Phone | Email |
|---|---|---|---|---|
| `4…1` | SYSTEM_ADMIN | ডেমো স্টাফ | `01711000001` | `staff@example.com` |
| `6…1` | INSTRUCTOR | ডেমো ইন্সট্রাক্টর | `01711000002` | `instructor@example.com` |
| `5…1` | STUDENT A | সুমন আহমেদ | `01711000011` | `student1@example.com` |
| `5…2` | STUDENT B | রিনা বেগম | `01711000012` | `student2@example.com` |
| `5…3` | STUDENT C | করিম উদ্দিন | `01711000013` | `student3@example.com` |

The Master Admin is the client's own account from the 25 October handover. The seed does not create or change it, but needs it to exist (announcement author). Releasing written results, corrections and archiving are Master Admin actions, so the client uses his own login for those.

### 2.2 Categories and packages

| Id | Category (en / bn) | Parent |
|---|---|---|
| a1 | BJS Exam / বিজেএস এক্সাম | root |
| a2 | Preliminary MCQ / প্রিলিমিনারি (এমসিকিউ) | a1 |
| a3 | Written / রিটেন | a1 |
| a4 | Full mock / ফুল মক (এমসিকিউ + রিটেন) | a1 |
| a5 | Bar Council Exam / বার কাউন্সিল এক্সাম | root |
| a6 | MCQ practice / এমসিকিউ প্র্যাকটিস | a5 |

| Id | Package | Category | Price | Instructor | Owned by |
|---|---|---|---|---|---|
| b1 | BJS Preliminary MCQ Package | a2 | 1500.00 | none | nobody (C can buy it in the sandbox) |
| b2 | BJS Written Package | a3 | 1800.00 | Instructor | B (package) and C (one exam, e6) |
| b3 | BJS Full Mock Package | a4 | 2500.00 | Instructor | A (package) |
| b4 | Bar Council MCQ Package | a6 | 1200.00 | none | nobody |

Every package and exam gets a title, a two-sentence description and rules in Bangla and English, reusing the current drafts. They are drafts for the client to review (D-d10).

### 2.3 Exams

All 20 minutes. `D` is the run date.

| Id | Package | Type | When | Price | Purpose |
|---|---|---|---|---|---|
| e1 | b1 | MCQ | `at23(2)` | 300.00 | upcoming, buyable |
| e2 | b1 | MCQ | `at23(5)` | 300.00 | upcoming |
| e3 | b1 | MCQ | undated | none | "date to be announced" and "no price" states |
| e6 | b2 | Written | `at23(-1)` | 500.00 | ended; B and C submitted, awaiting marking |
| e7 | b2 | Written | `at23(-2)` | 500.00 | ended; B submitted, awaiting marking |
| e8 | b2 | Written | `at23(4)` | 500.00 | upcoming |
| e9 | b2 | Written | undated | 500.00 | undated written |
| e10 | b3 | MCQ | `at23(-3)` | 300.00 | ended; A's MCQ result, published, answers released |
| e11 | b3 | Written | `at23(-2)` | 500.00 | ended; A's written result, published, marked PDF and comment |
| e12 | b3 | Written | `at23(-1)` | 500.00 | ended; A submitted, awaiting marking |
| e20–e26 | b3 | MCQ on odd days, Written on even days | `at23(1)` … `at23(7)` | 300.00 / 500.00 | the daily 11 PM exams (R3). e20 tomorrow is MCQ, e21 the day after is written. |
| e30 | b4 | MCQ | `at23(3)` | 250.00 | second category, upcoming |
| e31 | b4 | MCQ | `at23(6)` | 250.00 | |

Written exams get a multi-line question PDF. MCQ exams get 10 questions from the existing lists. The daily MCQs may reuse lists.

### 2.4 Sittings and results

| Id | Exam | Student | State |
|---|---|---|---|
| `7…1` | e10 | A | MCQ submitted, score 35/50, passed, published. `answers_released_at` set on e10. |
| `7…2` | e11 | A | Written submitted (answer PDF), marked 32/50, comment, marked PDF, `published_at` set, no pending revision. |
| `7…3` | e12 | A | Written submitted (answer PDF), not marked. Shows "awaiting result" to A. |
| `7…4` | e6 | B | Written submitted, not marked. |
| `7…5` | e6 | C | Written submitted, not marked. |
| `7…6` | e7 | B | Written submitted, not marked. |

The instructor's queue therefore holds **4 papers** across b2 and b3 (`7…3` to `7…6`). After the instructor marks one, the client as Master Admin can release it, which shows the whole release workflow live.

Check while building: the MCQ result page for `7…1` with a score but no per-question answer rows (the current seed does the same). If the page needs answer rows, seed them in the sittings answer table from V27.

### 2.5 Orders, grants, notices

| Order `tran_id` | Student | Scope | Target | Amount | Status | Grant |
|---|---|---|---|---|---|---|
| `DEMO-PKG-B3-A` | A | MODULE | b3 | 2500.00 | SUCCEEDED | yes |
| `DEMO-PKG-B2-B` | B | MODULE | b2 | 1800.00 | SUCCEEDED | yes |
| `DEMO-EXM-E6-C` | C | EXAM | e6 | 500.00 | SUCCEEDED | yes |
| `DEMO-PKG-B1-C` | C | MODULE | b1 | 1500.00 | FAILED | no |

No INITIATED order: the 10-minute recheck job would query the sandbox for it. Notices: `PURCHASE_GRANTED` for the three grants, `MCQ_SCORE` (e10) and `WRITTEN_RESULT` (e11) for A, and one announcement to A, B and C. The 48-hour and 1-hour reminders come from `StudentReminderJob` on its own, so the seed does not write them.

### 2.6 Cleanup and tests

- `demo-cleanup.sql`: delete by the same prefixes plus `DEMO-` tran ids. It must also remove rows that point at demo rows but were created during the review (sittings on demo exams, orders and grants on demo packages, notices to demo students, audit rows by demo actors), so a reset after the review is clean. Non-demo rows stay.
- `DemoContentSeedIT`: refusals; a second run inserts nothing; the shape in 2.2 and 2.3; every exam 20 minutes; e20 at 23:00 Dhaka tomorrow and e21 the day after; A owns b3, sees two published results and one awaiting; the instructor's queue has 4 papers and no access to b1 or b4; sign-in with phone and with email; no `exam_outbound_mail` rows; cleanup removes demo and review rows and keeps a real row.
- `DemoContentSeedGuardTest`: the prod refusal and the `SEED_DEMO_PASSWORD` length rule.
- Rewrite `api/.cursor/progress/ui-delivery/demo-seed.md` and update section headers in `seed-data-plan.md` to point here.

## 3. Env on the box

Shahriar edits `/etc/coaching-api.env` himself. The agent checks names only (deploy slice 3 commands).

| Name | Value | Note |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `staging` | unchanged |
| `WEB_ORIGIN` | `https://srv2089893.hstgr.cloud,https://admin.<ip-with-dashes>.nip.io` | student host first (R7), no spaces, no trailing slash |
| `SSLCZ_CALLBACK_BASE_URL` | `https://srv2089893.hstgr.cloud` | unchanged |
| `SSLCZ_*` | sandbox pair | unchanged |
| `SEED_DEMO` | `true` for the seed start only, then `false` | |
| `SEED_DEMO_PASSWORD` | the R4 password | new |
| `SEED_SYSTEM_ADMIN_*`, `SEED_INSTRUCTOR*_*`, `SEED_TEST_STUDENT_PASSWORD`, `SEED_STUDENT_EMAIL`, `SEED_ADMIN_*`, `SEED_STUDENT_*`, `SEED_ENABLED` | delete | no longer read |

Web unit `coaching-web.service` gains `Environment=SITE_URL=https://srv2089893.hstgr.cloud` and `Environment=STAFF_URL=https://admin.<ip-with-dashes>.nip.io` (read by `proxy.ts` at request time).

## 4. Two hosts, certificate, basic auth

1. Read the box's public IPv4 (`curl -4 -s https://ifconfig.me` on the box). Admin host = `admin.<a-b-c-d>.nip.io`. Check it resolves from the PC.
2. `sudo cp /etc/nginx/sites-available/coaching /etc/nginx/sites-available/coaching.prev`.
3. Add the admin host as its own `server` block with the same `location /` (to `127.0.0.1:3000`) and `location /api/` (to `127.0.0.1:8080`), the same `client_max_body_size`, and `proxy_set_header Host $host` plus `X-Forwarded-Host $host` and `X-Forwarded-Proto $scheme` in both blocks, so `host-routing.ts` sees the real host.
4. `sudo certbot --nginx -d admin.<a-b-c-d>.nip.io`. If refused for rate limits, use `sslip.io` (R5) and redo step 3 for that name.
5. Basic auth and the gate (done 27 October 2026, files in the scratchpad `nginx/`): `apache2-utils`; user `review` with a random password (written to `/etc/nginx/coaching-review.htpasswd`, `640 root:www-data`; the password is handed to Shahriar once and kept in his password manager); `/etc/nginx/conf.d/coaching-review-gate.conf` holds the gate secret and `map_hash_bucket_size 128` (the 48-character secret overflows the default); `snippets/coaching-review.conf` holds `auth_basic` and the open callback location, included at server level of both 443 blocks. In both sites `location /api/` gets `auth_basic off;` and `if ($review_gate_ok = 0) { return 401; }`, and `location /` gets `add_header Set-Cookie "review_gate=…"` **without** `always`, so a 401 never sets the cookie. Backups: `coaching.prev`, `*.pre-auth`. Rotate the gate and password by rerunning the script.
6. `sudo nginx -t`, then reload. Check: no password gives 401 on both hosts for `/` and `/api/v1/me`; with it, 200 and 401 `UNAUTHENTICATED` respectively; `POST …/ipn` without a password reaches the API (an API error, not Nginx 401).

## 5. Build and deploy

Follow deploy slices 0, 2, 4 and 6 with these differences:

- Web build env: `NEXT_PUBLIC_SITE_URL=https://srv2089893.hstgr.cloud`, `NEXT_PUBLIC_STAFF_URL=https://admin.<a-b-c-d>.nip.io`, `NEXT_PUBLIC_API_BASE_URL=` (empty), `NEXT_PUBLIC_GOOGLE_ENABLED=false`. The two URLs are baked in, so the build happens after step 4.1 fixes the admin host name.
- Before the first seed start: back up the DB (slice 2), then count demo-prefixed rows. If any exist, run `demo-cleanup.sql` in one transaction (with Shahriar's OK).
- Seed: set `SEED_DEMO=true`, restart the API, watch the log until started, set `SEED_DEMO=false`, restart again.

## 6. Check on the live hosts

| Who | Where | Expected |
|---|---|---|
| anyone | both hosts | basic auth prompt |
| signed out | student `/` | both categories, four packages |
| A | student | b3 owned; e10 and e11 results visible, e11 marked PDF downloads; e12 awaiting; e20 at 23:00 tomorrow, e21 the day after |
| B | student | b2 owned; e6 and e7 submitted, awaiting |
| C | student | e6 owned; buying b1 opens the SSLCommerz sandbox; a sandbox payment grants it (IPN 200 in the log) |
| staff | `/staff` on the admin host | students, payments (3 paid, 1 failed), packages |
| instructor | admin host | b2 and b3 only; 4 papers to mark; marked PDF upload works |
| Master Admin | admin host | release of a paper the instructor marked reaches the student |
| staff on the student host `/staff` | | redirected to the admin host |

## 7. Hand-over to the client

A one-page sheet (written for the client, in plain language, Bangla headings if Shahriar wants): the two URLs, the basic-auth step, the five logins (password given by Shahriar, not in the sheet), what to try with each account (section 6 as tasks), that the 11 PM exams are open only from 23:00 to 23:20, that written exams must be uploaded inside those 20 minutes, and how to report changes.

## Risks

- **Cutover on 29 October.** The demo rows live in the staging DB. Before the box becomes production, run the cleanup (or rebuild the DB) and confirm `SEED_DEMO` is gone; the code refuses the seed on `prod`.
- **20-minute written exams** are tight for write, scan and upload. As requested; the client may ask to change it.
- **Exams after day 7** do not exist. If the review runs longer, run cleanup and the seed again (this also resets the review's marks and purchases), or add days.
- **Shared nip.io quota** can refuse the certificate; sslip.io is the fallback.
- **Staff signing in on the student host** get their session on that host only (cookies are host-only). The sheet tells staff to use the admin URL.
