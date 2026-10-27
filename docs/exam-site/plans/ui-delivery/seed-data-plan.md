# Seed data plan: student flow, admin and test accounts

> **Superseded on 27 October 2026** for the client review by [../client-review-staging.md](../client-review-staging.md): a smaller seed (one staff, one instructor, three students, 20-minute exams, nightly 11 PM exams, insert once). Kept as history of the first design.

Status: analysis and plan only. No code changed. Written for the integrator and the SEED-DATA agent.
Checked against: `catalog-acquisition` at `d6506c9` (26 Oct), in code. The seed was not run.

## 1. What the request asks for

1. Three exam packages (modules): one with MCQ exams only, one with written exams only, and one with both. Each has 4 to 5 exams.
2. Each package has a real description, a routine (dates and times), exam descriptions and rules, prices, and the other information a package needs to look finished.
3. Three students with a password, marked in the seed:
   - the owner of a full package,
   - the owner of one exam,
   - a student who has an exam live at the moment the seed runs.
4. Seed data useful to admin users too (staff pages need data to show).
5. Skip the one-hour-before mail for now. Do not build or verify it on the website.
6. Every seed run must produce the same scenario, with the live exam live at that run.

## 2. What exists today

| Piece | Where | What it does |
|---|---|---|
| `DemoContentSeed` | `api/src/main/java/.../exams/DemoContentSeed.java` | The demo catalog (categories, modules, exams, questions, options), one instructor, eight students, eight submitted scores on one MCQ exam, two written sittings. Runs with `SEED_DEMO=true` on staging or dev. Fixed UUID prefixes per table. Inserts skip existing rows. |
| `DemoContentSeedRunner` | same folder | Calls the seed at startup when `SEED_DEMO=true`. |
| `StagingExamSeed` | same folder | A second seed, on `app.seed.enabled=true` (set in `application-staging.properties` and `application-dev.properties`). Creates a SYSTEM_ADMIN from `SEED_ADMIN_*`, a student `SEED-STUDENT` from `SEED_STUDENT_*`, and a 10 TK "Staging practice exam" with no questions. |
| `MasterAdminBootstrap` | `.../users/` | Creates the Master Admin from `MASTER_ADMIN_*` when none exists. The only real admin. |
| `demo-cleanup.sql` | `api/.cursor/progress/ui-delivery/` | Removes demo rows by UUID prefix, in dependency order. Keeps any non-demo row. |
| `demo-seed.md` | `api/.cursor/progress/ui-delivery/` | Run instructions and environment names. |
| `DemoContentSeedIT`, `StagingExamSeedTest` | tests | Refusal on prod and non-staging profiles, two runs without duplicates, cleanup removes demo rows only. |

Two seeds write to the same database with different environment names. The second one adds an exam that has no questions and cannot be taken.

## 3. Analysis of the current seed against the request

Status: **Yes** means the seed has it. **Partly** means it has some. **No** means it is missing.

### 3a. Packages (modules)

| Need | Now | Evidence in code |
|---|---|---|
| Three packages: MCQ only, written only, both | **No.** One mixed module (১৯তম বিজেএস: 3 MCQ + 2 written). ২০তম বিজেএস is MCQ only. There is no written-only package. | `DemoContentSeed.java:156-158`, `:160-175` |
| 4 to 5 exams per package | **Partly.** ১৯তম has 5. ২০তম has 2. Academic has 1. | `:160-175` |
| Real description (bn and en) | **No.** Placeholder "Demo module for the client review". | `:193` |
| Routine (dates) | **Partly.** Five dates, but they are scattered: one ended yesterday, one 30 minutes after the run, one undated, one written from yesterday, one tomorrow. No plan for an upcoming week. | `:160-169` and `Schedule` `:335-342` |
| Price per package | **Yes** for ১৯তম (2500) and ২০তম (2000). None for the academic module. | `:156-158` |
| Subcategories | **No.** All categories are roots. Bar Council is empty. | `:153-155`, `category(... null ...)` |
| Instructor assigned | **Yes**, one instructor on ১৯তম only. | `:108-110` |

### 3b. Exams

| Need | Now | Evidence |
|---|---|---|
| Real exam description and rules (bn and en) | **No.** "Demo exam for the client review" and "Demo rules: read every question before you answer." | `:213-215` |
| Paper language, duration, pass % | **Yes.** Paper language is always BN (`ExamSpec.paperLanguage()`). | `:353-355` |
| Price per exam | **Partly.** Only ২০তম paper A is priced (300). All written exams have no price. | `:166`, `:168`, `:170-173` |
| Undated exam (state "তারিখ ঘোষণা হবে") | **Yes.** CPC written and ২০তম papers. | `:166`, `:170-173` |
| Live exam when the seed runs | **No.** The only near exam starts 30 minutes after the run (`now + 30 min`). By the time someone logs in it may not be live, and the dates never move again because existing rows are skipped. | `:164`, `:341`, `:215` |
| Question paper PDF for written exams | **Partly.** A one-line PDF "Demo question paper: <title>". | `storePdf`, `:303-321` |
| Written answer script | **No, and wrong.** The answer script is the question paper (the same stored file is reused as the answer). | `writtenMarks`, `:252-256` |
| Written marked PDF (evaluated PDF) | **No.** Published marks have no evaluated file, so the student's "download the marked script" button cannot be tested. | `insertWritten`, `:267-275` |
| MCQ questions | **Partly.** 10 for CrPC, 10 for Penal, 5 for Evidence, 5 for General. 30 in total. Good Bangla text and explanations. | `:366-440` |
| Negative marking | **Not in the API.** The mockup's stat row shows "নেগেটিভ মার্ক ০.২৫"; the spec says not to show it. Do not seed it. | `client-mockup.html:85`, `student-flow-prompts.md:281` |

### 3c. Students and results

| Need | Now | Evidence |
|---|---|---|
| Students who can log in | **No.** Eight demo students with `DEMO-NO-LOGIN` as the hash, so nobody can sign in. | `:45`, `:145-146` |
| Owner of a full package | **No.** | nothing writes `live_exam_grants` or `live_exam_orders` |
| Owner of one exam | **No.** | same |
| Student with an exam live now | **No.** | `:164` (30 minutes after the run, not live) |
| Results: MCQ scores on a past exam | **Yes.** Eight scores on the Penal paper with a shared rank. | `:240-248` |
| Results: written published and draft | **Yes**, but the published one has no marked PDF. | `:257-263` |
| Written awaiting marking (submitted, not evaluated) | **No.** The instructor's marking queue is empty. | `writtenMarks` has only published and draft |
| Notices for students | **No.** No `student_notices` rows. The bell and the tab badge are empty until the reminder job runs. | no insert in `DemoContentSeed` |
| Orders and payments for admin | **No.** The payments list and payment detail are empty. | no `live_exam_orders` insert |

### 3d. Admin users

| Need | Now | Evidence |
|---|---|---|
| Master Admin | **Yes**, from `MASTER_ADMIN_*`. Not created by the demo seed. | `MasterAdminBootstrap` |
| System Admin | **Partly.** Only `StagingExamSeed` creates one (`SEED_ADMIN_*`, when `app.seed.enabled`). The demo seed does not. | `StagingExamSeed.java:68` |
| Instructor with work | **Partly.** One instructor on ১৯তম, with the written results. No written awaiting work. | `:108-110` |
| A second instructor (assigned and unassigned cases) | **No.** | |
| Students list with data | **Yes**, 8 students (no login). | `:141-150` |
| Audit log with rows | **No.** Seed writes no audit rows. Optional. | |

### 3e. Re-run behaviour

- Seed is idempotent (`ON CONFLICT DO NOTHING`), so a re-run adds nothing and moves no dates. This is the root cause of the "live whenever the seed runs" gap.
- Cleanup covers the existing prefixes only. The new orders, grants and login students need prefixes the cleanup can match, and the cleanup must run before a reseed.

## 4. Findings

Severity: **Blocker** the request cannot be tested without it, **Major** a required item is missing or wrong, **Minor** polish.

| ID | Sev | Finding | Fix |
|---|---|---|---|
| D-001 | Blocker | No login-capable students. The demo students cannot sign in, and no student owns a package, an exam, or a live exam. | Three named login students with a real password hash from env (`SEED_TEST_STUDENT_PASSWORD`). Owners written as paid orders and grants (section 6). |
| D-002 | Blocker | No three-package structure (MCQ, written, both). | Replace the catalog with the three packages in section 5. |
| D-003 | Blocker | The live exam is not live at the run, and dates never refresh. | On every run, refresh the schedule of the demo rows (relative to run time), and build a live exam with `liveAt = run − 5 min` and `durationMinutes = 30` (sitting and window are the same in this API, see section 5.3). |
| D-004 | Major | Placeholder descriptions and rules on modules and exams. | Real Bangla and English copy for each package and exam (section 5.3). Client review of the copy (D-010). |
| D-005 | Major | Packages have 2 exams (২০তম) and 1 exam (academic). Requirement is 4 to 5. | Each of the three packages has 4 or 5 exams. |
| D-006 | Major | No subcategories. | Category tree: root BJS with three subcategories (section 5.1). |
| D-007 | Major | No prices on written exams. | Priced written exams, plus one exam per package with no price to test the "no price" state. |
| D-008 | Major | Written answer script is the question paper. | Generate a real answer script PDF per submission. |
| D-009 | Major | Published written marks have no marked PDF. | Generate a marked PDF for the published mark, stored as `evaluated_stored_file_name`. |
| D-010 | Major | Question content has not had client legal review. Existing 30 questions are good drafts; 50 more are needed. | Client review of every question and explanation before the client demo (decision D-d10 in section 13). |
| D-011 | Major | No notices, orders, grants or payments for the admin and student lists. | Seed them (section 6). |
| D-012 | Major | No awaiting written work for the instructor queue. | One awaiting, one draft, one published on the written exams (section 6.3). |
| D-013 | Minor | Two seeds, two sets of names (`SEED_DEMO_*`, `SEED_ADMIN_*`), and an exam with no questions in the catalog. | Retire the exam part of `StagingExamSeed`. Keep one seed entry point (decision in section 9). |
| D-014 | Minor | Question PDFs are one line of text. | Multi-line paper: title, duration, marks, instructions, three to four questions. |
| D-015 | Minor | Seed writes no audit rows, so the audit page is empty. | Optional: a few audit rows for the demo staff actions. |
| D-016 | Major (skip, per the request) | The hour-before mail (`ExamMailService.scan`, every 5 minutes, `ExamMailQueue.enqueueHourBefore`) would send mail to demo students. The request says skip it. | Gate it off with a property (default off outside prod), and make the seed write no `exam_outbound_mail` rows. Section 7. |

## 5. Target dataset

Run-relative: every date below is computed from the run time `R` (Asia/Dhaka). `N` is noon of the given day in Dhaka, which is how the existing seed keeps routine dates readable.

### 5.1 Category tree

| Id (prefix a) | Title (en / bn) | Parent | Packages |
|---|---|---|---|
| a1 | BJS Exam / বিজেএস এক্সাম | root | none (container) |
| a2 | Preliminary MCQ / প্রিলিমিনারি (এমসিকিউ) | a1 | package P-MCQ |
| a3 | Written / রিটেন | a1 | package P-WRT |
| a4 | 19th BJS full / ১৯তম বিজেএস (পূর্ণ) | a1 | package P-BOTH |
| a5 | Bar Council Exam / বার কাউন্সিল এক্সাম | root | none, "শীঘ্রই আসছে" (kept as the mockup shows) |

### 5.2 Packages

| Module id (prefix b) | Package title (en / bn) | Category | Composition | Price | Instructor | Exams |
|---|---|---|---|---|---|---|
| b1 | BJS Preliminary MCQ Package / বিজেএস প্রিলিমিনারি এমসিকিউ প্যাকেজ | a2 | 5 MCQ | 1500.00 | Instructor B | 5 |
| b2 | BJS Written Package / বিজেএস রিটেন প্যাকেজ | a3 | 4 written | 1800.00 | Instructor A | 4 |
| b3 | 19th BJS Full Package / ১৯তম বিজেএস পূর্ণ প্যাকেজ | a4 | 3 MCQ + 2 written | 2500.00 | Instructor A | 5 |

The mixed package appears on both doors, because the door lists a module when it has an exam of that door's type (`findPublishedInCategoryWithExamType`). Confirm this is the intended behaviour (decision D-d5).

### 5.3 Exams

Id prefix `e`, numbers 1 to 14. Question ids use `f` with `examNo*100 + n`. Options use `9`.

| Exam no. | Package | Type | Title (en / bn) | Schedule (run-relative) | Duration (min) | Pass % | Full marks | Price | Question PDF | Questions |
|---|---|---|---|---|---|---|---|---|---|---|
| e1 | b1 | MCQ | CPC MCQ / সিপিসি এমসিকিউ | ended, R − 2 days at N | 40 | 40 | 50 | 300.00 | no | 10 |
| e2 | b1 | MCQ | CrPC MCQ / সিআরপিসি এমসিকিউ | **live now**: `liveAt = R − 5 min`, window 30 min | 30 | 40 | 50 | 300.00 | no | 10 |
| e3 | b1 | MCQ | Penal Code MCQ / পেনাল কোড এমসিকিউ | upcoming, `R + 50 min` (tests the 1-hour in-app reminder); owned by 5105 | 40 | 40 | 50 | 350.00 | no | 10 |
| e4 | b1 | MCQ | Evidence MCQ / সাক্ষ্য আইন এমসিকিউ | upcoming, `R + 36 h` (inside the 48-hour reminder lead) | 40 | 40 | 50 | 300.00 | no | 10 |
| e5 | b1 | MCQ | Constitution MCQ / সংবিধান এমসিকিউ | **undated** (no `liveAt`) | 40 | 40 | 50 | **none** (no-price state) | no | 10 |
| e6 | b2 | WRITTEN | CPC written / সিপিসি রিটেন | **undated** | 180 | 40 | 50 | 500.00 | yes | — |
| e7 | b2 | WRITTEN | CrPC written / সিআরপিসি রিটেন | ended, R − 1 day 14:00 with the instructor queue (1 awaiting, 1 draft) | 180 | 40 | 50 | 500.00 | yes | — |
| e8 | b2 | WRITTEN | Penal written / পেনাল কোড রিটেন | upcoming, R + 10 days at N | 180 | 40 | 50 | 500.00 | yes | — |
| e9 | b2 | WRITTEN | Constitution written / সংবিধান রিটেন | upcoming, R + 21 days at N | 180 | 40 | 50 | 500.00 | yes | — |
| e10 | b3 | MCQ | Penal MCQ (full) / পেনাল কোড এমসিকিউ (পূর্ণ) | ended, R − 3 days at N | 40 | 40 | 50 | 350.00 | no | 10 |
| e11 | b3 | MCQ | Evidence MCQ (full) / সাক্ষ্য আইন এমসিকিউ (পূর্ণ) | upcoming, R + 4 days at N | 40 | 40 | 50 | 300.00 | no | 10 |
| e12 | b3 | MCQ | General law MCQ / সাধারণ আইন এমসিকিউ | upcoming, R + 8 days at N | 40 | 40 | 50 | **none** (no-price state inside a priced package) | no | 10 |
| e13 | b3 | WRITTEN | CPC written (full) / সিপিসি রিটেন (পূর্ণ) | ended, R − 1 day 14:00 with a published mark and a draft | 180 | 40 | 50 | 500.00 | yes | — |
| e14 | b3 | WRITTEN | CrPC written (full) / সিআরপিসি রিটেন (পূর্ণ) | upcoming, R + 12 days at N | 180 | 40 | 50 | 600.00 | yes | — |

Notes:
- Written exams carry a question PDF because the written door needs one. Their MCQ question list is empty.
- The API uses `liveAt + durationMinutes` as the end of the window (`LiveExamPublicService.end`). For written exams the 180-minute duration is the time to write, and the window is the same length. For the live MCQ `e2` the window is 30 minutes (decision D-d9); say so in the copy (`rules`) and in `demo-seed.md`.
- Every exam has real Bangla and English description and rules (D-004). Copy is written in the glossary's terms (bangla-copy.md), with no negative-marking words.
- Ids: the ended exams are `e1`, `e7`, `e10`, `e13`; live is `e2`. The IDs are fixed, so the QA scripts can use them.

### 5.4 Module and exam copy (what the seed writes)

Each package gets: a title (en and bn), a two-sentence description in each language (what it covers, who it is for, how it is delivered), a category, a price, an instructor, and a position. Each exam gets: a title, a description (one to two sentences), rules (three short bullets: read every question, time limit, no change after submit for MCQ; for written: script format, upload deadline), paper language (BN), duration, pass percent, full marks, price (or none), and a question PDF for written exams. Copy is drafted by the agent and reviewed by the client (D-010).

## 6. Students, staff and the data around them

### 6.1 Students

| Id (prefix 5) | Name (bn) | Email | Login | Owns | Notes |
|---|---|---|---|---|---|
| 5101 | সুমন আহমেদ | `demo-owner-package@example.com` | yes (`SEED_TEST_STUDENT_PASSWORD`) | **full package** b3 (MODULE grant) | Has an MCQ score on e10 (past), a published written mark on e13 with a marked PDF and a comment, notices for both. |
| 5102 | রিনা বেগম | `demo-owner-exam@example.com` | yes | **one exam** e4 (EXAM grant) | e4 starts in 36 hours, inside the 48-hour lead, so the reminder job writes `EXAM_REMINDER` on its own. |
| 5103 | করিম উদ্দিন | `demo-live@example.com` | yes | **exam live now**: e2 (EXAM grant) | Window open at run time. No sitting yet, so Start is enabled. |
| 5104 | (no phone) | `demo-no-phone@example.com` | yes | nothing | Phone not set (allowed since V33). Shows the `phoneRequired` gate on every buy button. |
| 5105 | (reminder test) | `demo-reminder@example.com` | yes | **one exam** e3 (EXAM grant) | e3 starts 50 minutes after the run, so the job writes `EXAM_STARTING_SOON` (1 hour) and `EXAM_REMINDER` (48 hours). Added for the 1-hour reminder; not one of the three requested students. |
| 5001–5008 | the eight existing names | `demo-student-N@example.com` | no (`DEMO-NO-LOGIN`) | — | Keep for the leaderboard and the instructor queue. |

Phones: the three login students get `+8801700000101`, `+8801700000102`, `+8801700000103`, so the phone gate is already satisfied for them. The `phoneRequired` path is tested with a fourth login student, `5104`, with no phone (its password is the same env value). `5105` (phone `+8801700000105`) is a fifth login student for the 1-hour reminder. `5104` and `5105` are additions beyond the three requested; approve or drop them. Sign-in needs the email verified (`email_verified_at` set).

Password: one env value (`SEED_TEST_STUDENT_PASSWORD`, 8 to 72 characters, never written in code or docs). Hashed with the app's encoder; the seed stores only the hash.

### 6.2 Orders and grants

| Order `tran_id` (≤ 30 chars) | Student | Scope | Target | Amount | Status | Grant |
|---|---|---|---|---|---|---|
| `DEMO-PKG-B3-5101` | 5101 | MODULE | b3 | 2500.00 | SUCCEEDED | MODULE b3 |
| `DEMO-EXM-E4-5102` | 5102 | EXAM | e4 | 300.00 | SUCCEEDED | EXAM e4 |
| `DEMO-EXM-E2-5103` | 5103 | EXAM | e2 | 300.00 | SUCCEEDED | EXAM e2 |
| `DEMO-EXM-E3-5105` | 5105 | EXAM | e3 | 350.00 | SUCCEEDED | EXAM e3 |
| `DEMO-STUCK-E9-5001` | 5001 | EXAM | e9 | 500.00 | INITIATED, created 2 days before the run, no grant | none. For the Master Admin confirm test (student plan QA-13). Created more than 24 hours before the run, so the recheck job skips it. |
| `DEMO-PKG-B2-5001` | 5001 | MODULE | b2 | 1800.00 | FAILED | none |
| `DEMO-EXM-E9-5002` | 5002 | EXAM | e9 | 500.00 | CANCELLED | none |

Each paid order has `method = SSLCOMMERZ`, `currency = BDT`, `payment_channel = 'DEMO'`, and a `val_id` starting `DEMO-VAL-`. The amount equals the price. The grant rows use the order ids (`order_id` is unique).

Marking: the `DEMO-` prefix in `tran_id` and `payment_channel` tells a tester this is seed data. The payment status endpoint returns PAID for these, and the recheck job does not touch them (it reads only INITIATED).

### 6.3 Results, marking and notices

- MCQ scores: the eight existing demo students on e1 (past), with the shared rank. Keeps the leaderboard.
- Owner 5101: an MCQ score on e10 (past) with `MCQ_SCORE` notice; written e13 published (38/50, comment, marked PDF, `WRITTEN_RESULT` notice).
- Written e7 (instructor queue): one awaiting (submitted, answer script stored, no evaluation) for 5003; one draft (evaluated, not published) for 5004.
- Written e13: one draft for 5002 (evaluated, not published) and the published one for 5101.
- Written e7, no upload: 5005 has no answer script and status `FAILED_TO_SUBMIT`, score 0, "could not finish" (SQ-08, staff view).
- Notices written by the seed: `MCQ_SCORE` and `WRITTEN_RESULT` for 5101. `EXAM_REMINDER` for 5102 comes from `StudentReminderJob` on its own, because e4 starts inside the 48-hour lead. The test is that the job writes it, not the seed. `EXAM_REMINDER` and `EXAM_STARTING_SOON` for 5105 (e3 is inside 48 hours and inside 1 hour) also come from the job. 5103 gets neither, because its exam has already started.
- New kinds from A2 (once merged): `PURCHASE_GRANTED` for 5101, 5102, 5103, written by the grant path or by the seed; `EXAM_OPEN` for 5103 (the window is open now).
- Announcement (from A3 and A2, once merged): one `ANNOUNCEMENT` sent by the seed as the Master Admin, to all students with login (5101–5105). It appears once in each notice box (QA-17).
- Answers (R-4): `e1` has its answers released, so the review shows correct options. `e10` is withheld, so the release test has both states (SQ-07).

### 6.4 Staff users

| Account | Role | How created | Work it has |
|---|---|---|---|
| Master Admin | MASTER_ADMIN | `MASTER_ADMIN_*` (existing bootstrap, not the seed) | Everything. |
| System Admin | SYSTEM_ADMIN | `SEED_SYSTEM_ADMIN_*` (new) | Students, payments (sees the seeded orders), audit. |
| Instructor A | INSTRUCTOR | `SEED_INSTRUCTOR_*` (existing) | Assigned to b2 and b3. Marking queue: e7 (1 awaiting, 1 draft), e13 (1 draft; published one). |
| Instructor B | INSTRUCTOR | `SEED_INSTRUCTOR2_*` (new, optional) | Assigned to b1 only. Its written queue is empty; it cannot mark e7 or e13 (test of the 404 for an unassigned instructor). |

## 7. Mail: skip the one-hour-before mail

Request: the API has a mail system that sends mail one hour before an exam; skip it for now and do not put it on the website.

What the code does (checked):
- `ExamMailService.scan` runs every 5 minutes and calls `ExamMailQueue.enqueueHourBefore`, which queues `HOUR_BEFORE` mail for students with access to an exam starting 15 to 70 minutes from now.
- Other mail (receipts, module and exam publish, roster add, activation and password codes) is separate.
- The in-website `EXAM_REMINDER` notice is written by `StudentReminderJob` 48 hours before an exam, and is not mail.

Plan:
1. Gate the hour-before mail behind a property, `app.exam-mail.hour-before.enabled`, default `false`, so the scan skips `enqueueHourBefore` and no `HOUR_BEFORE` rows are written. Keep the code, so it can be turned back on. (owner: API, one small commit with a test.) **Done: `64ac138`.**
2. The seed writes no `exam_outbound_mail` rows.
3. The website keeps the in-app `EXAM_REMINDER` (48 hours) and adds `EXAM_STARTING_SOON` (1 hour), both as notices in the notice box. Decision D-d6 is confirmed. Neither is mail.
4. The receipt copy (S-006) stays neutral until mail is confirmed on staging (D3 in the student plan).

## 8. Re-run, refresh and cleanup

- **Refresh on every run.** On each run the seed updates the schedule of the demo exams (`live_at`, `duration_minutes`) and the times on demo sittings, results and orders, relative to the run time. The live exam `e2` is therefore live at every run. This replaces the rule "dates are fixed at the first run" in `demo-seed.md`.
- **Idempotent.** Inserts keep `ON CONFLICT DO NOTHING`, updates are by demo id, and the fixed `tran_id` values stop a second order from being added.
- **Refusals.** Still refuses `prod`, and refuses any profile other than `staging` or `dev`. `ProfileStartup` still refuses `SEED_DEMO=true` on prod.
- **Cleanup.** Extend `demo-cleanup.sql` for the new rows: orders with `DEMO-` tran ids, grants on b1–b3 and e1–e14, the 5101–5105 students (already matched by the `5` prefix), the system admin and instructor B (prefix 4 and 6 with `n = 2`), notices and mail by user prefix (already covered). Run cleanup before reseeding the catalog from the old set (D-d7).
- **PDFs.** Stored PDFs stay on disk (existing rule). The new generated PDFs are listed by the same query `demo-seed.md` gives.

## 9. Environment names (names only, no values)

| Name | Used for | New? |
|---|---|---|
| `SEED_DEMO` | turns the seed on at startup | existing |
| `SEED_INSTRUCTOR_PHONE`, `_EMAIL`, `_NAME`, `_PASSWORD` | Instructor A | existing |
| `SEED_INSTRUCTOR2_PHONE`, `_EMAIL`, `_NAME`, `_PASSWORD` | Instructor B (optional) | new |
| `SEED_SYSTEM_ADMIN_PHONE`, `_EMAIL`, `_NAME`, `_PASSWORD` | System Admin | new |
| `SEED_TEST_STUDENT_PASSWORD` | password for the login students 5101–5105 | new |
| `SEED_STUDENT_EMAIL` | optional roster link to an existing student | existing |
| `MASTER_ADMIN_*` | Master Admin (bootstrap, unchanged) | existing |
| `app.exam-mail.hour-before.enabled` | hour-before mail, default off | new property |

`StagingExamSeed`'s `SEED_ADMIN_*` and `SEED_STUDENT_*` are retired in favour of the names above (D-d7). Until D-d7 is decided, both stay and the `Staging practice exam` is left out of new runs.

## 10. Tests the seed must pass

- `DemoContentSeedIT`: prod and other profiles refused; two runs with no duplicates; schedule refresh moves `e2` to live at each run (`live_at ≤ now < live_at + duration`).
- Package shape: b1 has 5 exams, all MCQ; b2 has 4 written; b3 has 5 (3 MCQ, 2 written); each has a description (bn and en), an instructor, and a category that is a subcategory of a1.
- Owners: 5101 has a MODULE grant on b3 with a SUCCEEDED order; 5102 has an EXAM grant on e4; 5103 has an EXAM grant on e2 and e2 is live.
- Login: 5101 signs in with the env password, and its `me` shows `phoneRequired = false`; 5104 shows `phoneRequired = true`.
- Reminder: after a job scan, 5102 has an `EXAM_REMINDER` for e4 and 5103 has none. 5105 has `EXAM_STARTING_SOON` and `EXAM_REMINDER` for e3, and a second scan adds no duplicates.
- No `exam_outbound_mail` rows written; `HOUR_BEFORE` is off.
- Staff: instructor A sees the awaiting submission on e7 and the published mark on e13; instructor B gets 404 on e7.
- Cleanup removes every seeded row and keeps a real row (extend the existing test). This includes the grants with `source` set, the announcement, `live_exam_order_exams`, and the released flags.
- Stuck order `DEMO-STUCK-E9-5001` is pending and is not picked up by the recheck job.
- `e1` answers are released and `e10` answers are not.
- The 5005 written participant has `FAILED_TO_SUBMIT`, score 0, no answer file.

## 11. Implementation plan

Owner: **SEED-DATA**. Branch `ui/seed-data`, worktree `F:\cc-wt\seed-data`. Owns `api/.../exams/DemoContentSeed*.java`, `StagingExamSeed.java` (retire the exam part), `ExamMailService.java` (the gate only), `api/.cursor/progress/ui-delivery/demo-seed.md`, `demo-cleanup.sql`, the seed tests. No `web/` changes.

Order:
1. Gate the hour-before mail (section 7). Done (`64ac138`).
2. Catalog: categories, packages, exams with real copy, questions (80 MCQ, of which 30 exist), question PDFs (multi-line). One commit per table group.
3. Schedule refresh on each run (section 8). Live exam `e2` at every run.
4. Students 5101–5104 with the env password hash, orders, grants.
5. Results, marked PDFs, answer script PDFs, the awaiting submission, the draft and published marks.
6. Notices for the login students (`MCQ_SCORE`, `WRITTEN_RESULT`). `PURCHASE_GRANTED` and `EXAM_OPEN` wait for A2.
7. Staff accounts (System Admin, optional Instructor B), assignments.
8. Cleanup update, `demo-seed.md` rewrite, tests (section 10).
9. Run on the database the user names (open item in the student plan, section 11), and check the list in QA (section 12).

Dependencies: step 1 is done. V39 (A3) and the notice kinds (A2) are merged, so steps 4 to 6 can start now. The seed needs nothing from API-BUNDLE.

Ready message: `READY TO MERGE: ui/seed-data (agent SEED-DATA)`, with the test counts, the run log summary, and the list of seeded accounts (names and ids, no passwords).

## 12. What QA can check with this seed

| QA | Account and page | Expected |
|---|---|---|
| SQ-01 | signed out, `/mcq/m/b1` | 5 MCQ exams listed with prices, one undated, e5 without price. |
| SQ-02 | 5104 (no phone), `/exam/e3` | buy disabled, set-phone link. |
| SQ-03 | 5101, `/mcq/m/b3` | "অ্যাক্সেস আছে", no buy buttons on b3 or its exams. |
| SQ-04 | 5102, `/exam/e4` | owned, no buy button; `/mcq/m/b1` still buyable. |
| SQ-05 | (checkout off) | `ConfigNotice`, buy disabled. |
| SQ-06 | 5101 and 5102 bell | 5101: `WRITTEN_RESULT`, `MCQ_SCORE`. 5102: `EXAM_REMINDER` (job-written for e4). |
| SQ-07 | 5101, `/exam/e1/result` and `/exam/e10/result` | e1 shows correct answers (released). e10 shows none (withheld). |
| SQ-08 | 5101, `/exam/e13/result` | comment and marked PDF. (The draft on e13 belongs to 5002, who cannot log in; it is visible to staff only.) |
| SQ-09 | 5103, `/exam/e2` | live, Start enabled. |
| SQ-10 | Instructor A, submissions | e7 has 1 awaiting and 1 draft; e13 has the published mark. |
| SQ-11 | Instructor B, `/staff/submissions/m/b2` | 404 or empty for unassigned work. |
| SQ-12 | System Admin, live-exam orders (`GET /api/v1/live-exam-orders`) | four paid (5101 package, 5102 `e4`, 5103 `e2`, 5105 `e3`), one failed (`DEMO-PKG-B2-5001`), one cancelled (`DEMO-EXM-E9-5002`), and the stuck order pending (`DEMO-STUCK-E9-5001`). |
| SQ-13 | any staff | no `HOUR_BEFORE` rows; no outbound mail for demo accounts. |
| SQ-14 | 5105, bell, after a job scan once e3 is inside 1 hour | `EXAM_STARTING_SOON` and `EXAM_REMINDER` for e3, each once. 5102 has `EXAM_REMINDER` for e4 only. 5103 has neither. |
| student plan QA-15 to QA-18 | see `student-purchase-lms-plan.md` section 10 | The seed provides the stuck order `DEMO-STUCK-E9-5001` (student plan QA-13), the released `e1` and withheld `e10` (SQ-07), the 5005 failed written participant (student plan QA-16), and the announcement (student plan QA-17). |

## 13. Decisions for the user

- **D-d5. Mixed package on both doors.** Decided: yes (student plan, section 2).
- **D-d6. In-website reminder. Decided:** the 48-hour `EXAM_REMINDER` notice stays, and an in-app 1-hour `EXAM_STARTING_SOON` notice is added. Only the hour-before mail is skipped.
- **D-d7. Retire `StagingExamSeed`.** Decided: yes (student plan, section 2). Keep one seed entry point, and move the System Admin into `DemoContentSeed`. Existing staging data is removed by the cleanup first.
- **D-d8. Replace the catalog or add beside it.** Decided: replace (student plan, section 2). The old 19th BJS, 20th BJS and academic modules are removed by the cleanup, and the three packages are the test set. Adding beside them makes the QA scenario harder to follow.
- **D-d9. The live window. Decided:** 30 minutes, starting 5 minutes before the run.
- **D-d10. Content approval.** The 80 MCQs and the written copy need client review for legal accuracy before the client demo. Confirm who reviews. **Open: a name is needed.**
- **D-d11. Login students and password.** Decided: one env password for 5101–5104 (student plan, section 2).

## 14. Risks

- The live exam window is short by design (30 minutes from 5 minutes before the last run). QA must start within it; if not, re-run the seed to refresh it.
- Refreshing dates on every run changes the times on results shown in the UI. QA must not cache a list across runs.
- Seed content in Bangla must be checked for spelling and legal accuracy (D-010). Errors in the current 30 questions are not known; they are drafts.
- The written answer PDFs and marked PDFs are generated by PDFBox and stored on disk. Disk cleanup is manual (existing rule).
