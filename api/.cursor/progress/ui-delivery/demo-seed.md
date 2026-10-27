# Demo seed (client review on staging)

The data the client reviews: one staff account, one instructor, three students, two root categories with four subcategories, four packages, nineteen exams of twenty minutes each, and the sittings, orders and notices that let every case be seen without setting anything up. Code: `DemoContentSeed`, `DemoContentSeedQuestions` and `DemoContentSeedRunner` in `com.coachingcenter.api.exams`. Cleanup: [demo-cleanup.sql](demo-cleanup.sql). Plan: `docs/exam-site/plans/client-review-staging.md`.

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

## Orders and notices

Three paid orders with grants (`DEMO-PKG-B3-A`, `DEMO-PKG-B2-B`, `DEMO-EXM-E6-C`) and one failed order (`DEMO-PKG-B1-C`). No order is pending, so the payment recheck job has nothing to query. Notices: a purchase notice for each owner, the MCQ score and written result for student A, and one announcement to all three students. Reminders for the upcoming exams are left to the reminder job.

## One run, no refresh

The seed only inserts, and only once. If the demo catalog is already there, a run does nothing, so a student's sitting, the instructor's marking and any purchase the client makes are never undone. Dates are set from the run date and do not move. The seven nightly exams cover D+1 to D+7; for a longer review, run the cleanup and the seed again (this also resets the review's own marks and purchases).

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

`DemoContentSeedIT` covers the refusals, a second run writing nothing and moving no date, the category and package shape, the seven nightly exams at 23:00, student A's results, the instructor's four papers, sign-in by phone and email for every account, the orders and grants, the notices, and the cleanup (demo and review rows removed, a real row kept). `DemoContentSeedGuardTest` covers the production refusal, the password rule, and that every exam is 20 minutes. `ExamMailHourGateTest` covers the hour-before mail gate.
