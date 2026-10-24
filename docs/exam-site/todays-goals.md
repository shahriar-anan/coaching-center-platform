# Goals — 25 to 26 October 2026

Target: by the night of 26 October the exam site runs on the new VPS at `https://srv2089893.hstgr.cloud`, with a working UI and services, so the client can test it. `symamlaw.com` stays on the old VPS until a later cutover.

Plan each goal before code. `decisions.md` still wins where this file is silent.

## 1. API still to build

Status 25 October 2026: A1 to A6 are implemented and tested. See `plans/api-completion/` and `api/.cursor/progress/api-completion/`.

Found by reading the routes under `api/src/main/java` on 25 October 2026.

| # | Gap | Why the client test needs it | Size |
|---|---|---|---|
| A1 | **Student inbox** (step 5). A table, a scheduler for the reminder 1–2 days before a granted exam, a notice when the MCQ score posts, a notice when the Master Admin publishes a written result, `GET` own notices, mark one read, mark all read. | Dashboard bell and Notices page. No notification table exists. | ~1 day |
| A2 | **Instructor list**: `GET /api/v1/instructors` with search and status filter, plus the assigned module or none. Only create and reissue exist. | Instructor directory and the assign picker. | Small |
| A3 | **Participation counts** per exam: sitting, submitted, failed to submit, written drafts waiting for publish. Instructor sees assigned modules only. | Step 7 overview and instructor home. | Small |
| A4 | **Own status per exam** for the student (not sat, sitting, submitted, failed to submit, result ready). The window and the participant row are enough. | Status pills on the module list and dashboard. Still open in `decisions.md`; confirm first. | Small |
| A5 | **Authoring deletes**: there is no delete for a live exam or a live exam MCQ question. Only categories, modules, and roster rows have `DELETE`. | Admins fixing a wrong question or exam during QA. Confirm the rule (draft only?) first. | Small |
| A6 | **Instructor unassign**: one instructor per module, a second assign conflicts, and nothing removes the first. | Correcting a wrong assignment. Confirm first. | Small |

Notices for admins and instructors are not in `decisions.md`, which defines student notices only. Add them only if Shahriar decides to.

## 2. API that waits

| Gap | Why it waits |
|---|---|
| Refund (slice 10 of `plans/auth-and-mail.md`) | The refund rules are not confirmed. |
| Payment cancel or failure email | Decided against. The result page shows it. |
| Google sign-in live | Code exists. Needs `GOOGLE_*` and a registered callback. Google may not accept the Hostinger hostname for a public app. Optional for the client test. |

## 3. Server

| # | Goal |
|---|---|
| S1 | Deploy the current jar. The jar running now reads `EMAIL_HOST` and fails registration with HTTP 500. |
| S2 | Env: `WEB_ORIGIN=https://srv2089893.hstgr.cloud`, Brevo `MAIL_*`, sandbox `SSLCZ_*` with `SSLCZ_CALLBACK_BASE_URL=https://srv2089893.hstgr.cloud`, `SPRING_PROFILES_ACTIVE=staging`, `SEED_ENABLED=true` for the test exam. |
| S3 | Real mail check: one verification and one reset email to an inbox Shahriar can open. |
| S4 | Install Node and a process manager. Nginx sends `/` to Next.js and keeps `/api/` on 8080. Build on the PC, not on the box. |
| S5 | One sandbox payment on the VPS, including an IPN. |
| S6 | Apply the pending reboot. Start a nightly off-server `pg_dump` before the client enters data. |

## 4. Website

| # | Goal | API it needs |
|---|---|---|
| W1 | Public catalog: categories, module, exam page | Exists |
| W2 | Buy exam or module, cancel, result page (exists) | Exists |
| W3 | Dashboard and routine | Exists; pills need A4 |
| W4 | MCQ paper, submit, score, review | Exists |
| W5 | Written paper: PDF viewer with name and id overlay, answer PDF upload | Exists |
| W6 | Result and leaderboard | Exists |
| W7 | Notices bell and page | A1 |
| W8 | Instructor marking draft, Master Admin publish, counts (step 7) | Exists; counts need A3, picker needs A2 |

Catalog authoring screens come after W8. Until then, test data comes from the staging seed or Master Admin API calls.

## 5. Before the real launch on `symamlaw.com`

Switch to the `prod` profile. Turn off `MAIL_TEST_ENABLED` and `SEED_ENABLED`. Decide sandbox or live SSLCommerz. Point DNS, issue the certificate, and update `WEB_ORIGIN` and `SSLCZ_CALLBACK_BASE_URL`.
