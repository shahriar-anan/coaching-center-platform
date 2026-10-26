# Demo seed (phase 2, client demo and QA)

The demo data the client and QA use: three packages (MCQ only, written only, both), fourteen exams, login students 5101 to 5105, orders and grants, sittings and marks, notices, one announcement, and the staff accounts. Code: `DemoContentSeed`, `DemoContentSeedQuestions` and `DemoContentSeedRunner` in `com.coachingcenter.api.exams`. Cleanup: [demo-cleanup.sql](demo-cleanup.sql). Plan: `docs/exam-site/plans/ui-delivery/seed-data-plan.md`.

The seed refuses `prod` and any profile other than `staging` or `dev`. `ProfileStartup` also refuses `SEED_DEMO=true` on prod. It writes no `exam_outbound_mail` rows, so no mail goes out for demo accounts.

## Environment names

No values are written here. Set them in the server environment, not in code.

| Name | Used for |
|---|---|
| `SEED_DEMO` | `true` turns the seed on at startup. Any other value, or unset, does nothing. |
| `SEED_SYSTEM_ADMIN_PHONE`, `_EMAIL`, `_NAME`, `_PASSWORD` | System Admin (required). |
| `SEED_INSTRUCTOR_PHONE`, `_EMAIL`, `_NAME`, `_PASSWORD` | Instructor A (required). Assigned to the written and full packages. |
| `SEED_INSTRUCTOR2_PHONE`, `_EMAIL`, `_NAME`, `_PASSWORD` | Instructor B (optional; no phone means no account). Assigned to the MCQ package. |
| `SEED_TEST_STUDENT_PASSWORD` | One password for the login students 5101 to 5105, 8 to 72 characters. |
| `SEED_STUDENT_EMAIL` | Optional. The existing student with this email gets a roster mark on the MCQ package. |

The Master Admin comes from `MASTER_ADMIN_*` (the bootstrap, not this seed). The seed stops with a message if no Master Admin exists yet.

## Accounts

| Id | Name in the seed | Login | What it has |
|---|---|---|---|
| 5101 | owner of the full package | yes | MODULE grant on the full package (paid order `DEMO-PKG-B3-5101`); MCQ score on e10; published written mark on e13 with a marked PDF and comment. |
| 5102 | owner of one exam | yes | EXAM grant on e4 (`DEMO-EXM-E4-5102`). |
| 5103 | live student | yes | EXAM grant on e2 (`DEMO-EXM-E2-5103`). e2 is live, and no sitting exists, so Start is enabled. |
| 5104 | no phone | yes | Nothing. The phone gate shows on every buy button. |
| 5105 | reminder student | yes | EXAM grant on e3 (`DEMO-EXM-E3-5105`), which starts 50 minutes after the run. |
| 5001 to 5008 | demo students | no (`DEMO-NO-LOGIN`) | Leaderboard scores on e1, the written queue on e7 (5005 failed to submit), and the draft on e13 (5002). |

Phones: 5101, 5102, 5103 and 5105 get `+8801700005101`, `+8801700005102`, `+8801700005103` and `+8801700005105`. These differ from the plan's `+8801700000101` style because those numbers are already used by 5001 to 5003. 5104 has no phone.

Stuck order: `DEMO-STUCK-E9-5001` (student 5001, e9, 500.00) is INITIATED, created two days before the run, with no grant. The recheck job reads only INITIATED orders older than 10 minutes, so it will look at this one; the gateway lookup has to be stubbed or sandboxed for QA. Failed `DEMO-PKG-B2-5001` and cancelled `DEMO-EXM-E9-5002` are there for the admin lists.

## Packages and exams

| Package | Category | Exams | Price |
|---|---|---|---|
| b1 BJS Preliminary MCQ Package | Preliminary MCQ | e1 to e5 (MCQ; e5 has no price) | 1500.00 |
| b2 BJS Written Package | Written | e6 to e9 (written) | 1800.00 |
| b3 19th BJS Full Package | 19th BJS full | e10 to e12 (MCQ; e12 has no price), e13 and e14 (written) | 2500.00 |

Every MCQ paper has ten questions at five marks each, so full marks are 50 and the pass mark is 40 percent. Written papers have full marks of 50 and a 180-minute time to write, with a question PDF.

Run-relative schedule (Asia/Dhaka, refreshed on every run): e1 ended two days ago; e2 live (started five minutes before the run, a 30-minute window); e3 starts in 50 minutes; e4 in 36 hours; e5, e6 undated; e7 and e13 ended yesterday at 14:00; e10 ended three days ago; e8, e9, e11, e12, e14 upcoming. e1's answers are released; e10's are not.

## Copy and questions are drafts

Every module and exam title, description and rule, and every MCQ question and explanation, is a draft for the client's content review (decision D-d10). The reviewer is not named yet. Do not show the copy to the client as final until it is reviewed. 30 questions came from the earlier seed; 50 are new in `DemoContentSeedQuestions`.

## Re-run

Each run:

1. Sets the exam schedule again (`live_at`, duration and the answers-released flag), so e2 is live at every run.
2. Upserts the sittings, orders and grants by their fixed ids, so the times and scores are set again and nothing doubles.
3. Deletes and writes the demo notices again (students 5xxx and the announcement), so reminder and window-open notices do not repeat with the new start times.
4. Writes marked and answer PDFs only the first time a sitting is seen; later runs reuse them.

If the database holds an older demo catalog (titles from the earlier seed), the run stops with "run demo-cleanup.sql first". Cleanup is therefore needed once before the first run of this seed on any database that had the earlier one.

## How to run (staging or dev only)

1. Set the environment names above and `SEED_DEMO=true`. Start the API with the `staging` or `dev` profile.
2. If the database holds an earlier demo catalog, run the cleanup first, in one transaction:
   ```
   psql -1 -v ON_ERROR_STOP=1 -f api/.cursor/progress/ui-delivery/demo-cleanup.sql
   ```
3. Watch the log until startup finishes. The seed runs in one transaction; a failure rolls everything back.
4. Set `SEED_DEMO` back to false after the run. Leaving it on only refreshes the schedule.

The database for the live run is the user's choice (the phase 2 plan, section 11). The seed has not been run against any database.

## How to undo

Run the cleanup script once in one transaction. It deletes by the demo id prefixes and the `DEMO-` tran ids, so it removes demo rows and nothing else. Stored PDFs stay on disk; the script's header lists how to find them.

## Reminders

The 48-hour `EXAM_REMINDER`, the 1-hour `EXAM_STARTING_SOON` and the window-open `EXAM_OPEN` are written through `StudentNoticeWriter`, the same writer the reminder job uses, for the demo exams only. The seed does not call the job's global scan, because that scan covers every exam in the database. Expected: 5102 has `EXAM_REMINDER` for e4; 5105 has `EXAM_STARTING_SOON` and `EXAM_REMINDER` for e3; 5103 has `EXAM_OPEN` for e2 and no reminder.

## Tests

`DemoContentSeedIT` covers the refusals, two runs with no duplicates and no mail rows, the package shape, e2 live at each run, the owners and orders, the login password and the no-phone student, the reminders and notices written once, the announcement, the written queue and released answers, and the cleanup (demo rows removed, a real row kept). `DemoContentSeedGuardTest` covers the production refusal and the env helpers. `ExamMailHourGateTest` covers the hour-before mail gate.
