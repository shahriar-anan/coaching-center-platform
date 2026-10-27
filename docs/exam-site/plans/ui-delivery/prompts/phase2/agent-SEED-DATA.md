# Prompt: SEED-DATA (phase 2, the client demo data)

You are agent **SEED-DATA**. You build the demo seed that the client and QA use: three packages, their exams, the test students, their orders and grants, results, notices, one announcement, and the staff accounts. You work on the API only. There is no web change.

## Setup

1. `git -C F:/coaching-center-platform worktree add F:/cc-wt/seed-data -b ui/seed-data catalog-acquisition`
2. Work only in `F:\cc-wt\seed-data`. Never write under `F:\coaching-center-platform\` except the lock file.
3. The tests need test database settings, and `api/.env` has none. In the shell, read `DB_USERNAME` and `DB_PASSWORD` from `F:\coaching-center-platform\api\.env`, then set `TEST_DB_URL=jdbc:postgresql://localhost:5432/coaching_center_test`, `TEST_DB_USER` and `TEST_DB_PASSWORD`. Never print the values. Never run the seed against `coaching_center_dev` or `coaching_center_qa`. Those databases are for the live run, and only after the user names one.

## Read first

- `docs/exam-site/plans/ui-delivery/seed-data-plan.md`: all of it. Section 5 is the target dataset, section 6 the students, orders and results, section 7 the mail rule, section 8 the re-run rules, section 9 the environment names, section 10 the tests, section 11 the steps. Section 12 lists the checks QA runs against your data. Its IDs are `SQ-01` to `SQ-14`.
- `docs/exam-site/plans/ui-delivery/student-purchase-lms-plan.md`: section 2 (decisions D-d5 to D-d11, all decided except D-d10), section 6.2 (notice kinds), section 6.4 (announcements), section 6.3 (answer release), section 9.2 (your file ownership), and section 10 (the QA checks, which your data must support).
- The current seed: `api/src/main/java/com/coachingcenter/api/exams/DemoContentSeed.java`, `DemoContentSeedRunner.java`, `StagingExamSeed.java`, `api/.cursor/progress/ui-delivery/demo-seed.md`, and `demo-cleanup.sql`.

## Current state (checked 26 Oct)

- Done already: the one-hour-before mail gate (`app.exam-mail.hour-before.enabled`, default off, merged in `64ac138`). Check it with a test. Change the code only if the test is missing.
- Merged and ready to use: V39 (grant `source`, `answers_released_at`, `live_exam_order_exams`, `announcements`), the notice kinds in V40 (`EXAM_OPEN`, `EXAM_STARTING_SOON`, `PURCHASE_GRANTED`, `ANNOUNCEMENT`, and the rest), and the announcement and grant APIs.
- Not done: the seed's new catalog, students, orders, notices, and the stuck order `DEMO-STUCK-E9-5001`. `StagingExamSeed` still writes the old "Staging practice exam" and the `SEED_ADMIN_*` and `SEED_STUDENT_*` names.

## Owned files

- `api/src/main/java/com/coachingcenter/api/exams/DemoContentSeed*.java`
- `api/src/main/java/com/coachingcenter/api/exams/StagingExamSeed.java`: retire the exam part (decision D-d7). Move the System Admin into `DemoContentSeed`.
- `api/src/main/resources/application-dev.properties` and `application-staging.properties`: seed keys only.
- `api/.cursor/progress/ui-delivery/demo-seed.md` (rewrite) and `demo-cleanup.sql`
- The seed tests: `DemoContentSeedIT`, `StagingExamSeedTest`, and a cleanup test if one is needed
- Your report section in `docs/exam-site/plans/ui-delivery/ws9-gaps-and-polish.md`

Do not touch: `ExamMailService` (the gate is done), the notice writers and the reminder job, `LiveExamOrder*` (API-BUNDLE owns them), V1 to V40, and the web.

## Items (follow seed-data-plan section 11, steps 2 to 8)

1. **Catalog.** Categories, packages `b1` (5 MCQ), `b2` (4 written), `b3` (3 MCQ and 2 written), exams `e1` to `e14` with the copy in section 5, and the questions. Use the 80 MCQs in the plan, of which 30 exist. Mark all copy as a draft for the content review (D-d10). Write no legal claims you cannot support.
2. **Schedule refresh.** On every run, reset the schedule of the demo exams and the demo sittings, results and orders relative to the run time (section 8). `e2` must be live at every run, with the live window of 30 minutes starting 5 minutes before the run.
3. **Students 5101 to 5104, and 5105.** Login students with the one env password `SEED_TEST_STUDENT_PASSWORD`. 5104 has no phone. Owners: 5101 has a MODULE grant on `b3` with a SUCCEEDED order; 5102 an EXAM grant on `e4`; 5103 an EXAM grant on `e2`, and `e2` is live. 5105 has `e3` starting soon.
4. **Orders and the stuck order.** The orders in section 6.2, with the fixed `DEMO-` tran ids. `DEMO-STUCK-E9-5001` is INITIATED, created two days before the run, and has no grant. The recheck job must skip it.
5. **Results.** MCQ scores for the login students, the written results, the marked PDFs and the answer script PDFs (generated as the code does today), the awaiting submission and draft on `e7`, the published mark on `e13`, and 5005 on `e7` with `FAILED_TO_SUBMIT`, score 0, no answer file.
6. **Answers released.** `e1` has `answers_released_at` set. `e10` does not.
7. **Notices.** `MCQ_SCORE` and `WRITTEN_RESULT` for the login students. `PURCHASE_GRANTED` for each grant. `EXAM_OPEN` where the window is open. `EXAM_REMINDER` for `e4` and `EXAM_STARTING_SOON` for `e3`: prefer running the existing reminder scan from the seed over writing those rows by hand, and say which you chose.
8. **Announcement.** One `ANNOUNCEMENT` written as the Master Admin to all students with login, linked to its notice rows by `announcement_id`.
9. **Staff.** The System Admin (`SEED_SYSTEM_ADMIN_*`), Instructor A (existing names), and Instructor B (optional, `SEED_INSTRUCTOR2_*`) with the assignments in section 6.4.
10. **Mail.** No `exam_outbound_mail` rows, and no `HOUR_BEFORE` rows (section 7).
11. **Refusals.** The seed refuses `prod` and any profile other than `staging` or `dev`. `SEED_DEMO=true` on prod still fails at startup.
12. **Cleanup and re-run.** Extend `demo-cleanup.sql` for every new row: the `DEMO-` orders, grants on `b1` to `b3` and `e1` to `e14`, the 5101 to 5105 students, the announcement, `live_exam_order_exams`, and the released flags. Run cleanup before reseeding the old catalog (D-d7, D-d8). Two runs must give no duplicates. Rewrite `demo-seed.md` with the environment names from section 9 and the new run steps.
13. **Tests.** Every test in section 10 of the seed plan, plus: cleanup removes every seeded row and keeps a real row. The seed writes no mail rows.

## Rules

- Commit messages end with `Co-Authored-By: Claude <your model name> <noreply@anthropic.com>`.
- One commit per table group or item. The message starts with the item ID.
- Write no password, token or secret into code, docs or the report. Use the env names only. Passwords are hashed with the same code the app uses.
- **Lock.** Maven runs only while `F:\coaching-center-platform\.heavy.lock` holds your line `API <pid> <time>`. If the file is empty or missing, write your line. If it holds another name, wait. Poll with a short check every 30 seconds, not a sleep loop. Remove only your own line when the run ends.
- Run only the seed and cleanup tests, plus `ExamMailIT` and `ExamMailHourGateTest`, not the full suite: `./mvnw -B -Dtest=DemoContentSeedIT,StagingExamSeedTest,ExamMailIT,ExamMailHourGateTest verify`. The integrator runs the full suite after the merge.
- **Do not run the seed against any database the user has not named.** A live run is a separate step for the user to approve. Section 11 of the student plan lists the choice.

## Done

1. Add "Report — SEED-DATA" at the end of `ws9-gaps-and-polish.md`: each item done, partly or skipped, with its commit hash, and the test counts. State which choice you made for step 7 (the reminder scan or direct rows).
2. Send:

```
READY TO MERGE: ui/seed-data (agent SEED-DATA)
Last commit: <hash>
Checks: <classes and results>
Items done: <IDs> | partly: <IDs and what is left> | skipped: <IDs and why>
Seeded accounts (names and ids only, no passwords): <list>
Live run: not done; the user chose coaching_center_qa, and QA-E2E runs it there
```

Stop after the READY block and wait for the integrator.
