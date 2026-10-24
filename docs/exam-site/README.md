# Exam website — planning input

**Audience:** the next agent. **Job:** plan the execution. **Do not write feature code from this folder.** Shahriar agreed the order and the decisions on 2026-10-23. The deadline in the client brief is 2 November 2026.

## Read this first

1. [decisions.md](decisions.md) — what is already decided. Do not reopen these. Before handover, read [Master Admin at delivery](decisions.md#master-admin-at-delivery).
2. [current-api.md](current-api.md) — what the live exam API already does.
3. The one step file you are planning. Plan step 1 before the later steps.
4. [client-brief.md](client-brief.md) — the client meeting. Use it where `decisions.md` is silent.

Phase 3 (`docs/phases/phase-3/`) is the record of the API that shipped. Sentences marked **Outdated** there are not requirements. The old `api-exam-*` plans are superseded. Do not extend them.

## Order

| Step | File | Kind | Starts when |
|---|---|---|---|
| 1 | [steps/01-written-publish.md](steps/01-written-publish.md) | API | Now |
| 2 | [steps/02-public-catalog.md](steps/02-public-catalog.md) | API | After Shahriar names the public fields and the page structure |
| 2a | [steps/02a-written-no-practice.md](steps/02a-written-no-practice.md) | API | Next. No Flyway version |
| 2b | [steps/02b-unannounced-date.md](steps/02b-unannounced-date.md) | API | After 2a. Flyway `V31` |
| 3 | [steps/03-purchase-sslcommerz.md](steps/03-purchase-sslcommerz.md) | API | After 2b. The next Flyway version after `V31` |
| 4 | [steps/04-google-sign-in.md](steps/04-google-sign-in.md) | API | Can be planned beside step 3. Ship with the account pages in step 6 |
| 5 | [steps/05-notifications.md](steps/05-notifications.md) | API | After step 1 publish and step 3 access, because the written notice and the reminder depend on them |
| 6 | [steps/06-student-website.md](steps/06-student-website.md) | Web | Browse can start once step 2 is specified. Sitting can use the current API before purchase exists, with the roster as the grant |
| 7 | [steps/07-operate-screens.md](steps/07-operate-screens.md) | Web | After step 1. Catalog authoring screens come after a student can sit an exam that already exists |

Language flag and participation counts are not separate steps. The language flag lands with the exam record in step 2. The participation counts land with the operate read in step 7.

## What planning produces

For the step you were asked to plan, add a plan file at `docs/exam-site/plans/<step>.md`. The plan adds slice order, endpoints, schema, and the test list. It does not drop a decision from `decisions.md` or from the step file.

Stop after that plan. Wait for Shahriar before the next step's plan, and before any feature code.

API plans name `*IT` classes and run them against real PostgreSQL. Do not use H2. Web plans name `npm run lint`, `npm run build`, and component tests with mocked `fetch`. Do not boot `api` for those tests. Do not add a mobile or web CI workflow. Shahriar does the browser pass. Do not open a browser, install an emulator, or run a device check.

## Leave these alone

- Sitting rules: one live sitting. One later MCQ attempt only if the student never tapped Take exam. That later attempt stays off the live leaderboard. A written exam has no later attempt.
- Immediate MCQ score, MCQ review after the sitting ends, and the MCQ leaderboard opening five minutes after the window.
- MCQ as structured text. Written question paper as one PDF.
- One instructor per module.
- Bilingual category, module, and exam titles, descriptions, and rules.
- Master Admin roster as a grant. System Admin does not edit it.
- Flyway `V14`–`V28`. New exam tables start at `V29`. Package `com.coachingcenter.api.exams`.
- The mobile app. This delivery is the website.
- Course and book payments, including the existing manual bKash/Nagad verification. They are out of this delivery. Do not change those rows, screens, or credentials. Exam checkout is SSLCommerz only.
- Phase 1 password rules: no actor sets, sees, or resets another user's password.

## Local sandbox payment

The browser test on this PC is planned in [SSL Sandbox/README.md](SSL%20Sandbox/README.md). It does not change the steps above. Do not create the practice exam until that plan's last data slice.
