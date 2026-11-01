# One sample seed for the dev database and the client review

Status: plan for Shahriar's approval, 1 November 2026. Nothing is built or deleted yet. Section 14 lists what I need from you before work starts.

## 1. Goal

One small, realistic data set that the client can explore without being buried in it, and that you can test against. It replaces both seeds that exist today, so your PC and the client's server show the same thing. Every feature has at least one example in each of its states, and each example has a named place (section 8) so the QA checklist can say "open exam 12" instead of "find an exam that is marked".

Principles:
- **Small on purpose.** 8 categories, 3 packages, 20 exams, 6 students. Every row is there because a feature needs it.
- **Accounts do not change.** Same phones, emails, ids, roles and password as today.
- **Dates move with the run day.** Everything is placed relative to the day the seed runs, so a fresh run always has open, upcoming and finished exams.
- **Insert once, touch nothing else.** The client's marking and purchases are never undone by a restart. A reset is a clean database.
- **No new feature code.** Only seed code, tests and docs.

## 2. What exists today (analysis)

| Item | Finding |
|---|---|
| Dev seed (`DevSeed`, `SEED_DEV=true`) | 2 System Admins, 2 teachers, 2 students; 4 categories; 6 exams; 1 package. Small, used on your PC. |
| Demo seed (`DemoContentSeed`, `SEED_DEMO=true`) | 1 staff, 1 teacher, 4 students; 7 categories; 6 packages; about 23 exams including an 11 PM exam each of 7 days; sittings, orders, grants, a notice and one announcement; question banks of 10 Bangla law questions per topic; generated PDFs. Used on the staging server. Has `DemoContentSeedIT` (496 lines) and a guard test. Both seeds share fixed UUID prefixes and some account ids, so they already overlap. |
| Dev database now | 8 accounts (the 7 seeded ones plus your own student `+8801744628303`), 4 categories, 6 exams plus the 100 sample exams I added, 1 package, 11 sittings, 8 reviews, no orders, no announcements, no question bank. |
| QA checklist | Mentions **no** exam id; it depends only on the accounts. So keeping the accounts unchanged keeps the checklist valid. |
| Tests that use a seed | `DemoContentSeedIT`, `DemoContentSeedGuardTest`, `CategoryBranchExamsIT` (uses the demo seed's category constants). The web tests use mocks and need no seed. |
| Databases on your PC | 28 of this project (13 `cc_test_*`, 15 `coaching_center_*`), about 480 MB, plus `cholobd_db` and `task-manager-sb`, which belong to other projects and are never touched. |
| Prod | Both seeds refuse to run on the prod profile. The new one keeps that rule. |

## 3. Decisions made

1. **One seed for both** the dev PC and the client's staging server.
2. **Accounts stay the same;** 4 fictional classmates are added (students 3 to 6) so leaderboards, review averages and the staff Students list are not empty.
3. **About 60 days** of schedule after the run day, then re-run. No rolling job.
4. **Cleanup later, as a separate step after sign-off:** the old databases and the old seed code, tests and sample scripts.

## 4. Accounts

| Role | Phone | Email | Id | Story |
|---|---|---|---|---|
| Master Admin | +8801700000001 | master@example.com | created at startup from `MASTER_ADMIN_*` | unchanged |
| System Admin 1 | +8801711000001 | staff1@example.com | `40000000-0000-4000-8000-000000000001` | unchanged |
| System Admin 2 | +8801711000002 | staff2@example.com | `...04000-...02` (as today) | unchanged |
| Teacher 1 | +8801711000003 | instructor1@example.com | `60000000-...-0001` | marks the CrPC, Penal and CPC written papers |
| Teacher 2 | +8801711000004 | instructor2@example.com | `60000000-...-0002` | marks the Constitution and Criminal Law mock |
| Student 1 | +8801711000011 | student1@example.com | `50000000-...-0001` | the main explorer: holds the Bar Council package, has results in every state |
| Student 2 | +8801711000012 | student2@example.com | `50000000-...-0002` | holds the Written pack; one paper not submitted; an access that expires in 3 days |
| Student 3 | +8801711000013 | student3@example.com | `50000000-...-0003` | holds the BJS package and (by admin enrolment) the Written pack; failed a paper and has a retake |
| Student 4 | +8801711000014 | student4@example.com | `50000000-...-0004` | holds the Bar Council package; a low score; one hidden review |
| Student 5 | +8801711000015 | student5@example.com | `50000000-...-0005` | holds the Bar Council package; started the free tests; one failed payment |
| Student 6 | +8801711000016 | student6@example.com | `50000000-...-0006` | almost empty: shows the empty states; one pending payment |

Names, as today, are Bangla. Everyone except the Master Admin uses the one shared password (`root1234` today, from the seed's password setting). Your own student `+8801744628303` is not part of any seed; on a rebuilt database it has to register again.

## 5. Catalog

**Categories** (8, three levels deep, one empty):

```
Bangladesh Judicial Service (BJS)
  Assistant Judge Recruitment Exam
  BJS Preliminary
Bar Council Enrolment
  Advocate Enrolment Exam
    Preliminary
    Written
  Short Courses            (empty on purpose: shows "Coming soon")
```

**Packages** (3):

| No | Title | Category | Price | Notes |
|---|---|---|---|---|
| P1 | BJS Preliminary Package | BJS Preliminary | 1,500, 25% off until 30 days after the run | discount case; MCQ only |
| P2 | Bar Council Complete Preparation Package | Advocate Enrolment Exam | 2,500 | **popular**; MCQ and written together |
| P3 | Advocate Written Pack | Written | 800 | written only; shares one exam with P2 |

**Exams** (20: 14 MCQ, 6 written). `D` is the run day. "Live now" means open from yesterday to 59 days ahead. A scheduled exam is 11 PM on its day, 20 minutes. Every exam lasts 20 minutes per attempt; MCQ papers have 10 questions of 5 marks (full marks 50, pass 40%).

| No | Category | Type | Title | When | Price | Package | Teacher | What it shows |
|---|---|---|---|---|---|---|---|---|
| 1 | BJS Preliminary | MCQ | Civil Procedure | finished, D-14 | free | none | | finished exam, 5 results, leaderboard, answers released, reviews with an average |
| 2 | BJS Preliminary | MCQ | Penal Code | live now | free | none | | open now, negative marking (0.25) |
| 3 | BJS Preliminary | MCQ | Evidence | D+3 | 300 | P1 | | upcoming, countdown |
| 4 | BJS Preliminary | MCQ | Constitution | D+10 | 300 | P1 | | upcoming |
| 5 | BJS Preliminary | MCQ | CrPC | live now | 300 | P1 | | a paid exam a holder can take now |
| 6 | BJS Preliminary | MCQ | CPC Revision | D+1 | 400, 25% off for 30 days | none | | discounted single exam, tomorrow |
| 7 | Assistant Judge | Written | Civil Law Written | D+7 | 500 | none | T1 | question paper PDF, preview on |
| 8 | Preliminary | MCQ | Penal Code | finished, D-7 | 300 | P2 | | result passed and failed |
| 9 | Preliminary | MCQ | Evidence | live now | 300 | P2 | | open for holders |
| 10 | Preliminary | MCQ | General Law | D+5 | 300 | P2 | | **popular** exam |
| 11 | Preliminary | MCQ | Free Sample Test | live now | free | none | | free exam: free-announcement case |
| 12 | Written | Written | CrPC Written | finished, D-10 | 500 | **P2 and P3** | T1 | in two packages; published result; failed to submit; retake |
| 13 | Written | Written | Penal Code Written | finished, D-8 | 500 | P3 | T1 | marked but not released; awaiting marking |
| 14 | Written | Written | Constitution Written | finished, D-5 | 500 | P2 | T2 | awaiting marking |
| 15 | Written | Written | CPC Written | live now | 500 | P3 | T2 | upload open now |
| 16 | Bar Council Enrolment | MCQ | General Law Test | finished, D-3 | free | none | | free finished: "sit once more" |
| 17 | Bar Council Enrolment | Written | Criminal Law Mock | finished, D-27 | 500, access 30 days | none | T2 | question paper PDF, **access ends in 3 days** |
| 18 | Assistant Judge | MCQ | General Knowledge | date to be announced | free | none | | "not announced" state |
| 19 | Advocate Enrolment Exam | MCQ | Practice Set | practice mode (no window) | 300 | none | | practice exam |
| 20 | Preliminary | MCQ | Draft Paper | **draft** | 300 | none | | staff sees it, students never |

## 6. Activity

**Sittings** (22). MCQ results are visible at once; the scores are fixed.

| Exam | Students and result |
|---|---|
| 1 | S1 40 pass, S2 15 fail, S3 35 pass, S4 10 fail, S5 30 pass |
| 2 | S2 32 pass, S4 18 fail |
| 5 | S3 35 pass |
| 8 | S1 38 pass, S5 15 fail, S4 30 pass |
| 11 | S5 30 pass |
| 16 | S1 25 pass, S2 31 pass, S5 18 fail |
| 12 | S1 submitted, marked, **published** 32/50 pass; S3 published 18/50 fail; S2 **failed to submit** |
| 13 | S2 marked, **not released** (28/50); S3 **awaiting marking** |
| 14 | S1 **awaiting marking** |
| 17 | S2 **awaiting marking** (27 days old) |

An unfinished (in progress) sitting is **not** seeded: it would expire 20 minutes after the seed ran. The client starts one himself.

**Orders and grants.** Paid orders get a receipt number and a grant, as a real payment does.

| Order | Student | What | Status |
|---|---|---|---|
| O1 | S1 | P2 | paid |
| O2 | S5 | P2 | paid |
| O3 | S4 | P2 | paid |
| O4 | S2 | P3 | paid |
| O5 | S3 | P1 | paid |
| O6 | S6 | P3 | **pending** (staff queue) |
| O7 | S5 | P1 | failed |
| O8 | S6 | exam 7 | cancelled |

Manual grants (by staff): S3 on P3 (direct enrolment), S2 on exam 17 (ends in 3 days). Retake grant: S3 on exam 12.

**Reviews** (7): exam 1: S1 five stars with a comment, approved, **featured**; S2 four stars with a comment, **waiting**; S3 five stars only; S4 two stars with a comment, **hidden** (average 4.7 from 3 shows). P2: S1 five stars, approved, featured; S5 four stars approved; S4 five stars **waiting** (average 4.7 shows).

**Announcements** (6): to all students; to P2; to exam 17; to selected students S1 and S2; to the free exam 11 (recipient: S5, who started it); and one to P1 that is **withdrawn**. Recipients come from the same holder rule the product uses, not from a copy of its SQL.

**Other.** Notices for purchases, scores and published results (through the existing notice writer). Wish list: S1 has exam 2 and P2; S5 has exam 11. One question bank of 12 items for the staff "create exam" screens. Popular: P2 and exam 10. Instructors assigned to exams as in the table.

## 7. Size

About 8 categories, 3 packages, 20 exams, 150 questions and 600 options, 22 sittings with about 150 answer rows, 8 orders, 7 grants, 7 reviews, 6 announcements, 40 notices, 1 question bank, 6 generated PDFs of a few kilobytes each. Loads in a few seconds.

## 8. Where to see each feature (becomes `docs/exam-site/qa/seed-map.md`)

| Feature | Open |
|---|---|
| Door, subcategory switch, empty category | `/mcq`, Written, "Short Courses" |
| Exam states: finished, live, upcoming, no date, practice, draft | exams 1, 2, 3, 18, 19 (staff only: 20) |
| Free, paid, discounted, popular, preview | 1; 3; 6 and P1; P2 and 10; 7 |
| Package in two places | exam 12 |
| Leaderboard | exam 1 (5 students) |
| Results hub: Results, Being marked, In progress | S1 (published, awaiting), S2 (failed to submit, not released) |
| Review average and moderation | exam 1 and P2; staff Reviews page |
| Pending, failed, cancelled payments; receipts | staff Pending orders; S5, S6, S1 payments |
| Direct enrolment, access that ends | S3 on P3; S2 on exam 17 |
| Retake | S3 on exam 12 |
| Marking queues | T1 (exam 13), T2 (exams 14, 17) |
| Announcements of every audience, withdrawn | Master Admin Announcements; S1 and S5 package and exam pages and Notices |
| Empty states | S6 |

## 9. Technical design

- **One new seed class** `SampleSeed` with a runner switched by `SEED_SAMPLE=true`, built from the best parts of `DevSeed` and `DemoContentSeed`: their JDBC insert helpers, the question banks (`DemoContentSeedQuestions`), the generated-PDF helper and the notice writer. All data lives in one readable spec (the tables in sections 4 to 6), not scattered through methods.
- **Fixed ids** in the same scheme as today (the first hex digit names the table: categories `a`, packages `b`, exams `e`, questions `f`, options `9`, sittings `7`, orders `c`, grants `d`, announcements `8`, students `5`, teachers `6`, staff `4`, package membership `1`, question papers `2`; reviews `3`, retake grants `0`). A cleanup script deletes by those prefixes.
- **Guards, as today:** refuses the prod profile; only runs on dev and staging; refuses a database that already holds another seed's rows (so no half-merged data); a second run does nothing.
- **Time:** a `Schedule` anchored on the run day in Dhaka time, with the clock injected so the test fixes the day.
- **Master Admin** is found, not created, by the seed (it is created at startup from the environment).
- **PDFs** are written to the normal upload folder; the cleanup script also lists them.
- **Order of work depends on the announcements and reviews features** being in (the seed writes both): the seed starts after steps 1 and 2 of the admin plan.

## 10. Tests

- `SampleSeedIT` (replaces `DemoContentSeedIT`): after a run with a fixed day, assert the counts and the states in sections 5 and 6 (20 exams, 14 and 6 by type, 19 published and 1 draft; each state of the table present); the accounts are exactly those in section 4; ids are unique; a second run changes nothing; the prod guard refuses. Then through the real endpoints: the public catalog and category tree (counts, empty category, discount, popular), Student 1's results list in every state, exam 1's leaderboard has 5 rows, exam 1's and P2's review averages are visible and the hidden review is not, the pending-orders queue has 1, each teacher's marking queue count, the announcement of each audience reaches the right students, S6 sees empty lists.
- `SampleSeedGuardTest` (replaces `DemoContentSeedGuardTest`).
- `CategoryBranchExamsIT` moves to the new seed's constants.
- A script `build-sample-db.ps1` (documented in the QA folder) that creates a database, starts the API against it with the seed on, and checks the key endpoints with curl, so the same check runs on your PC and on staging.

## 11. Docs and QA

- `QA-Checklist.md` section 0b: add Students 3 to 6; add "load a fresh database" steps.
- New `docs/exam-site/qa/seed-map.md` (section 8, with the real ids).
- `seed-100-exams.sql`, `seed-reviews.sql` and their removal scripts are deleted in the cleanup step.

## 12. Rollout

1. **Build the new seed** (agent). Starts when steps 1 and 2 of the admin plan are done.
2. **Build the tests and docs** (second agent, at the same time as 1 for the docs and scripts; the integration test follows).
3. **Side by side:** I create a new database `coaching_center_sample`, run the API on it, run the check script, and you look in the browser. Your current dev database is not touched.
4. **Switch:** when you approve, I stop the servers, rename `coaching_center_sample` to `coaching_center_dev` (so `.env` does not change), and start again. The old dev database is renamed to `coaching_center_dev_old`, not dropped.
5. **Staging (separate approval):** the staging database has the client's marking and purchases in it. Loading the new seed there needs the old demo rows cleared first. I will not do this without your explicit go on the day.
6. **Cleanup (separate approval, after sign-off):** section 13.

## 13. Cleanup (nothing is deleted until you approve this list again)

- **Databases to drop (28):** `coaching_center_dev_old`, `coaching_center_demo`, `coaching_center_excreate`, `_excreate_2`, `_excreate_3`, `coaching_center_fresh`, `coaching_center_p4_review`, `coaching_center_qa`, `_qa_1`, `_qa_2`, `_qa_3`, `coaching_center_review`, `coaching_center_shots`, `coaching_center_test`, and the 13 `cc_test_*` ones. **Kept:** the new `coaching_center_dev` and `coaching_center_gate` (the test database). **Never touched:** `cholobd_db`, `task-manager-sb`.
- **Before dropping:** I list the names with sizes and check free space; I ask for the go.
- **Code to delete:** `DevSeed`, `DevSeedRunner`, `DemoContentSeed`, `DemoContentSeedRunner`, `DemoContentSeedIT`, `DemoContentSeedGuardTest`; keep `DemoContentSeedQuestions` (renamed) because the new seed uses it. The settings `SEED_DEV`, `SEED_DEV_PASSWORD`, `SEED_DEMO`, `SEED_DEMO_PASSWORD` go from `.env` and the staging environment file (you write that file; I only tell you what to change).
- **Docs and scripts to delete:** the two sample-data scripts and their removal scripts; the old seed parts of `client-review-staging.md` marked as replaced.
- **Uploaded sample PDFs:** orphaned files from the old seeds are listed for you to approve, not removed with the databases.

## 14. Risks, drawbacks and what I will do about them

| # | Risk | Plan |
|---|---|---|
| 1 | The staging seed rebuild would erase the client's own marking and purchases. | Separate approval on the day; offer a database dump first. |
| 2 | After about 60 days the "upcoming" exams are in the past. | Documented re-run; the QA checklist says to reload before a review session. |
| 3 | The seed depends on features still being built (announcements audiences, reviews). | Seed starts after steps 1 and 2; the integration test fails loudly if a table is missing. |
| 4 | A 20-minute scheduled exam at 11 PM cannot be taken in the afternoon. | Five exams are open now (2, 5, 9, 11, 15), including a written one with upload. |
| 5 | An "in progress" sitting cannot be pre-made. | Documented; one click to start. |
| 6 | The classmates are fictional people who can sign in. | Plain fake names, `example.com` emails, documented as sample accounts. |
| 7 | Two seeds share ids; removing the old ones could break a test that still imports their constants. | The tests move first; the old code is deleted only after the test suite passes. |
| 8 | Question content is Bangla only and re-used across exams. | Same as today's seeds; the client reviews copy separately (REQ-011). |
| 9 | Your own student account is lost when the dev database is rebuilt. | Re-register; or tell me and I add it to the seed (it has a real email, so I will not). |

## 15. What I need from you

1. Approve the exam table in section 5 and the activity in section 6, or tell me what to change (for example more or fewer written exams).
2. Confirm the order of work: finish the announcements and reviews screens first, then the seed.
3. Confirm the classmate numbering (phones `+8801711000013` to `...16`, students 3 to 6) and that students 3 and 4 may reuse the names the demo seed already gave them.
