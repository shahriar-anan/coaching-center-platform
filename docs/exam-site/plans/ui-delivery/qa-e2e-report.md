# QA-E2E report (26 Oct)

Branch `ui/qa-e2e`, worktree `F:\cc-wt\qa-e2e`, from `catalog-acquisition` at `44d10ef`.

**What was run:** the API-level checks, with the stack running locally: the API on `dev` against `coaching_center_qa`, the web production build on port 3000. The seed ran at API start.

**What was not run:** no browser tool was available in this session. Every browser check is **not run**, including the screens, the layout at 375 px and 1440 px, both themes, keyboard focus, and the copy. A person with a browser needs to run those, using the same database.

## Setup and environment

- `coaching_center_qa` was backed up to `scratchpad\qa-before-reset.dump` (134 KB), then recreated empty. The old rows were the 25 Oct demo seed: 12 `@example.com` accounts, 9 exams, and the "Staging practice exam" with its module, which the seed would refuse to write over. The seed now writes the new dataset. Flyway reached V40.
- Seed passwords were generated for the run and kept only in `scratchpad\qa-run.env`, which is not in the repo.
- `mail.configured` is **false** in this local environment (no mail host is set). Mail sending is not tested.

## Results

Result key: **pass**, **fail**, **partly** (the API part passed, the rest is not run), or **not run**.

| ID | Result | Evidence |
|---|---|---|
| QA-01 | partly | `b1` lists 5 MCQ exams, prices 300 / 300 / 350 / 300 and none. `e5` has no price. The browse and "buy goes to login" steps are not run. |
| QA-02 | partly | 5104 has `phoneRequired` true, and `e3` gives `buyable` false with `PHONE_REQUIRED`. The disabled button and set-phone link are not run. |
| QA-03 | partly | 5101 has module access `MODULE` on `b3`, not buyable, and all five `b3` exams show `MODULE`. Page copy not run. |
| QA-04 | partly | 5102 owns `e4` (`EXAM`, not buyable). `b1` is still buyable. |
| QA-05 | not run | Needs a restart with checkout off. Current status: `checkoutEnabled` true. |
| QA-06 | not run | Needs a sandbox payment and the bell. The seed gives 5101 a `PURCHASE_GRANTED` notice, which exists. |
| QA-07 | partly | Withheld branch passes: the `e10` review has no `correct` or `explanation` fields. **Released branch not run**: no login student has an `e1` sitting (see finding F1). |
| QA-08 | pass | `e13` for 5101 has a comment and `evaluatedPdf` true. |
| QA-09 | partly | `e2` is inside its live window at the check time, and 5103 has `EXAM` access. The start and the sitting were not exercised. |
| QA-10 | pass | Instructor A sees `e7` with `FAILED_TO_SUBMIT`, `AWAITING` and `DRAFT`, and `e13` with `PUBLISHED` and `DRAFT`. |
| QA-11 | pass | Instructor B gets 403 on `b2` submissions. |
| QA-12 | pass | Manual grant by System Admin: 201, and the student sees it. Duplicate: 409 `GRANT_ALREADY_ACTIVE`. System Admin revoke: 403. Master Admin revoke: 200, and the grant leaves the student's list. A second grant after the revoke works. Payments: 4 `SUCCEEDED`, 1 `FAILED`, 1 `CANCELLED`, 1 `INITIATED` (the stuck order), read through `GET /api/v1/live-exam-orders`. |
| QA-13 | partly | No outbound mail rows and no `HOUR_BEFORE` rows. System Admin cannot confirm the stuck order (403). Master Admin confirming it is **not run**: it needs a person to check the SSLCommerz payment first. |
| QA-14 | pass | System Admin: 403 on suspend and delete of a student, on suspend of an instructor, and on announcements. Master Admin suspends and restores 5104 (204 each). Instructor A: 403 on students and announcements. Master Admin deleting an instructor is not run. |
| QA-15 | partly | 5101, with a paid order, gets 409 `ACCOUNT_HAS_PAID_ORDERS`. Deleting a student with no orders was not run (it would change the QA data). |
| QA-16 | not run | Written upload at the deadline: needs the upload flow. Covered by the API test (ST-6). |
| QA-17 | pass | Master Admin's announcement reached all 13 students, each exactly once, and appears in the history. System Admin: 403 on send. |
| QA-18 | partly | 5105 has one `EXAM_STARTING_SOON` and one `EXAM_REMINDER` for `e3`. The second scan adding nothing is not run (the job runs every 15 minutes). |
| SQ-08 | pass | Same as QA-08. |
| SQ-10 | pass | Same as QA-10. |
| SQ-11 | pass | Same as QA-11. |
| SQ-12 | pass with note | Four paid orders, not three: the seed also writes a paid order for 5105 on `e3`. The seed plan's text is out of date (F2). |
| SQ-13 | pass | Same as QA-13 (mail). |
| SQ-14 | pass | 5105 `e3`: one `EXAM_REMINDER`, one `EXAM_STARTING_SOON`. 5103 has neither. 5102 has `EXAM_REMINDER` for `e4` only. |
| Bundle | pass | 5105 orders `e1` and `e2` in one request: 201, scope `BUNDLE`, `examIds` returned, sandbox redirect present. Cancelled afterwards: 200. |
| S-027 | pass | Each upcoming row carries `moduleOnly`. One module-only row found. |
| system status | pass | Seven checks, names with yes or no only. No secret value found in the response. |
| Admin copy (raw codes) | not run | Needs the browser. |
| S-008, S-009, S-010, S-011 | not run | Need the browser. |
| R-9 accessibility | not run | Needs the browser at 375 px and 1440 px, both themes, keyboard. |

## Findings for owners

- **F1, medium, SEED-DATA.** The released MCQ review cannot be checked. The seed gives `e1` scores to 5001 to 5008, who have no login. No login student has an `e1` sitting, so SQ-07's "shows correct answers" cannot be seen. Fix: give 5101 an `e1` sitting with answers, or give one login student a sitting on `e1`.
- **F2, low, docs.** The seed plan's SQ-12 says three paid orders. The seed writes four (the 5105 `e3` order is the extra). Either the plan or the seed should change. Decide which.
- **F3, launch, API.** `springdoc` is on by default. The startup log warns that `/v3/api-docs` and `/swagger-ui.html` are enabled, and `application-prod.properties` does not turn them off. Add `springdoc.api-docs.enabled=false` and `springdoc.swagger-ui.enabled=false` to the prod profile, unless the prod security config already blocks them. Check that before the client environment check.
- **F4, environment, user/ops.** Mail is not configured here, so the mail sentence, the receipt copy and QA-06's email are not checked. The client decision (mail configured) must be carried into the staging and prod host values. The status page shows `mail.configured` false in this environment.
- **F5, process.** The stuck order is skipped by the recheck job's time window, but no run of the job has shown it. The reminder job's second scan (QA-18) is also not run. Both need a run that waits for the job.

## Not run, with reasons

- All browser checks (above): no browser tool in this session.
- QA-05: needs a restart with checkout off.
- QA-06 (sandbox payment) and QA-13 (confirm the stuck order): need a person to check the SSLCommerz payment.
- QA-16: needs the upload flow in a browser.
- QA-15 (delete a student with no orders): would remove QA data.
- QA-18 second scan: needs the reminder job to run.

## Changes made to the environment (not code)

- `coaching_center_qa` was reset and reseeded. Backup: `scratchpad\qa-before-reset.dump`.
- The API and the web app are running. The `QA` lock is held until the run is stopped.
- Test data created and undone: a manual grant on `e6` for 5104 (revoked, then granted again and revoked), a bundle order for 5105 (cancelled), an announcement to all students (kept, in history), and a suspend and restore of 5104.

Commit `QA-E2E: report (API-level run; browser checks not run)`.
