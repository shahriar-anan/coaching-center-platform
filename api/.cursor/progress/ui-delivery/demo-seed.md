# Demo seed (client review on staging)

The data the client reviews: one staff account, one instructor, three students, two root categories with four subcategories, four packages, twenty-five exams of twenty minutes each, and the sittings, orders and notices that let every case be seen without setting anything up, including the admin's own cases: draft packages and exams awaiting publish, papers marked but not released, and payments in every state. Code: `DemoContentSeed`, `DemoContentSeedQuestions` and `DemoContentSeedRunner` in `com.coachingcenter.api.exams`. Cleanup: [demo-cleanup.sql](demo-cleanup.sql). Plan: `docs/exam-site/plans/client-review-staging.md`.

The seed refuses `prod` and any profile other than `staging` or `dev`. `ProfileStartup` also refuses `SEED_DEMO=true` on prod. It writes no `exam_outbound_mail` rows, so no mail goes out for demo accounts.

## Environment names

No values are written here. Set them in the server environment, not in code.

| Name | Used for |
|---|---|
| `SEED_DEMO` | `true` turns the seed on at startup. Any other value, or unset, does nothing. Set it back to `false` after the run. |
| `SEED_DEMO_PASSWORD` | One password for all five demo accounts, 8 to 72 characters. |

The Master Admin comes from `MASTER_ADMIN_*` (the bootstrap, not this seed). The seed stops with a message if no Master Admin exists yet. The earlier `SEED_SYSTEM_ADMIN_*`, `SEED_INSTRUCTOR*_*`, `SEED_TEST_STUDENT_PASSWORD` and `SEED_STUDENT_EMAIL` names are no longer read.

## Accounts

Sign-in accepts the phone or the email. All accounts are active with a verified email.

| Role | Name | Phone | Email | What it has |
|---|---|---|---|---|
| System Admin | ডেমো স্টাফ | 01711000001 | staff@example.com | Students, payments, packages and exams in the admin site. |
| Instructor | ডেমো ইন্সট্রাক্টর | 01711000002 | instructor@example.com | Packages b2 and b3. Four written papers waiting to be marked. |
| Student A | সুমন আহমেদ | 01711000011 | student1@example.com | Owns b3. Published MCQ result (e10, 35/50, answers released) and published written result (e11, 32/50, comment and marked PDF). One written paper waiting for marking (e12). An 11 PM exam each of the next seven days. |
| Student B | রিনা বেগম | 01711000012 | student2@example.com | Owns b2. Two written papers waiting for marking (e6, e7). |
| Student C | করিম উদ্দিন | 01711000013 | student3@example.com | Owns exam e6 only, and has handed it in. A failed order for b1, so buying it again is possible in the sandbox. |

## Categories, packages and exams

| Category | Subcategory | Package | Price | Exams |
|---|---|---|---|---|
| BJS Exam | Preliminary MCQ | b1 BJS Preliminary MCQ Package | 1500.00 | e1 (D+2), e2 (D+5), e3 (undated, no price). All MCQ. |
| BJS Exam | Written | b2 BJS Written Package | 1800.00 | e6 (D-1), e7 (D-2), e8 (D+4), e9 (undated). All written. |
| BJS Exam | Full mock | b3 BJS Full Mock Package | 2500.00 | e10 MCQ (D-3), e11 written (D-2), e12 written (D-1), and e20 to e26: one exam each night at 11 PM for D+1 to D+7, MCQ on odd days and written on even days. |
| Bar Council Exam | MCQ practice | b4 Bar Council MCQ Package | 1200.00 | e30 (D+3), e31 (D+6). Both MCQ. |

`D` is the run date in Asia/Dhaka. Every dated exam is at 23:00 and lasts 20 minutes, so it is open from 23:00 to 23:20. Every MCQ paper has ten questions at five marks (full marks 50). Every written paper has full marks of 50 and a question PDF. Pass mark is 40 percent.

b1 and b4 belong to nobody, so the buy flow can be tried. Package and exam copy and the MCQ questions are drafts for the client's content review (decision D-d10); the reviewer is not named yet.

## The admin's cases (the top-up)

Added by a second, separate step with its own sentinel (the draft package b5), so a database seeded before it existed gets it without a reset.

| Case | Rows | Where the admin sees it |
|---|---|---|
| Draft package, saved and ready to publish | b5 "BJS Mains Package (draft)", price 2000, with e50 (written, saved, with a question PDF) and e51 (MCQ, ten questions, not final saved) | Participation page, drafts panel |
| Draft package just started | b6 "Judicial Service Mock (draft)", no exams, no price, not saved | Participation page, drafts panel |
| Draft exam ready to publish, inside a published package | e52 "Penal MCQ (new)" in b1, ten questions, final saved | drafts panel, and the b1 exam list |
| Draft exam needing work, inside a published package | e53 "Evidence written (new)" in b2, no PDF, no date, not saved | drafts panel |
| Written papers marked but not released | sitting 7 (student B, e40, 36/50, **flagged ready**) and sitting 8 (student A, e41, 28/50) | Dashboard "awaiting publish" 2; Release results panel |

Four written papers still wait for a mark, as before.

## Payments

Eleven orders. Every state is present, so the Payments and Pending payments pages and the dashboard count show something.

| Order `tran_id` | Student | What | Amount | State | Grant |
|---|---|---|---|---|---|
| `DEMO-PKG-B3-A` | A | package b3 | 2500.00 | SUCCEEDED | yes |
| `DEMO-PKG-B2-B` | B | package b2 | 1800.00 | SUCCEEDED | yes |
| `DEMO-EXM-E6-C` | C | exam e6 | 500.00 | SUCCEEDED | yes |
| `DEMO-EXM-E30-B` | B | exam e30 | 250.00 | SUCCEEDED | yes |
| `DEMO-EXM-E2-A` | A | exam e2 | 300.00 | SUCCEEDED | yes |
| `DEMO-PKG-B1-C` | C | package b1 | 1500.00 | FAILED | no |
| `DEMO-PKG-B4-B` | B | package b4 | 1200.00 | FAILED | no |
| `DEMO-EXM-E31-A` | A | exam e31 | 250.00 | CANCELLED | no |
| `DEMO-PKG-B1-C2` | C | package b1 | 1500.00 | **INITIATED**, 3 days old | no |
| `DEMO-EXM-E1-B` | B | exam e1 | 300.00 | **INITIATED**, 26 hours old | no |
| `DEMO-PKG-B4-A` | A | package b4 | 1200.00 | **INITIATED**, 2 days old | no |

The three pending orders are older than a day on purpose. The payment recheck job reads only open orders from the last 24 hours, so it never queries the sandbox for these; they stay for the Master Admin to confirm or mark as failed. Only a paid order carries a grant.

## Notices

A purchase notice for each paid order (students A and B get two each, C one), the MCQ score and written result for student A, and one announcement to all three students. Reminders for the upcoming exams are left to the reminder job.

## One run, no refresh

The seed only inserts, and only once. If the demo catalog and the top-up are already there, a run does nothing, so a student's sitting, the instructor's marking and any purchase the client makes are never undone. Dates are set from the run date and do not move. The seven nightly exams cover D+1 to D+7; for a longer review, run the cleanup and the seed again (this also resets the review's own marks and purchases).

If the database holds the earlier fourteen-exam demo (exam e4, e5, e13 or e14 exists), the run stops with "run demo-cleanup.sql first".

## How to run (staging or dev only)

1. Set `SEED_DEMO=true` and `SEED_DEMO_PASSWORD`. Start the API with the `staging` or `dev` profile.
2. If the database holds an earlier demo catalog, run the cleanup first, in one transaction:
   ```
   psql -1 -v ON_ERROR_STOP=1 -f api/.cursor/progress/ui-delivery/demo-cleanup.sql
   ```
3. Watch the log until startup finishes. The seed runs in one transaction; a failure rolls everything back.
4. Set `SEED_DEMO` back to `false` and restart.

## How to undo

Run the cleanup script once in one transaction. It deletes by the demo id prefixes and the `DEMO-` tran ids, so it removes demo rows and what the review added on them (sittings, orders, grants and notices of the demo students) and nothing else. Audit rows stay (the table is append-only). Stored PDFs stay on disk; the script's header lists how to find them.

## Tests

`DemoContentSeedIT` covers the refusals, a second run writing nothing and moving no date, the category and package shape, the seven nightly exams at 23:00, student A's results, the instructor's four papers, sign-in by phone and email for every account, payments in every state with only paid ones granting, pending payments older than a day, the draft packages and exams, the two papers marked but not released, the top-up applied to a database seeded before it (the client's marking is kept), the notices, and the cleanup (demo and review rows removed, a real row kept). `DemoContentSeedGuardTest` covers the production refusal, the password rule, and that every exam is 20 minutes. `ExamMailHourGateTest` covers the hour-before mail gate.
