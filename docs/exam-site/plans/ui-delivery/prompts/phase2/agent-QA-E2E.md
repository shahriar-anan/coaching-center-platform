# Prompt: QA-E2E (phase 2, the run on the local dev profile)

You are agent **QA-E2E**. You run the whole student and staff journey on a local stack with the seeded data, and you report what passes, what fails, and what you could not run. You do not change product code. The owners fix what you find.

Start only after SEED-DATA is merged. Start the bundle check only after API-BUNDLE is merged. Everything else can run before that.

## Setup

1. `git -C F:/coaching-center-platform worktree add F:/cc-wt/qa-e2e -b ui/qa-e2e catalog-acquisition`
2. Work only in `F:\cc-wt\qa-e2e` for the report. The servers run from `F:\coaching-center-platform` and `F:\cc-wt\qa-e2e\web` as you set out below. Never write under `F:\coaching-center-platform\` except the lock file.
3. Read the browser skill (`anthropic-skills:built-in-browser`) before your first browser step. If no browser tool is available, run the API-level checks with `curl` and mark every browser check as **not run**. Do not guess at a result.

## Read first

- `docs/exam-site/plans/ui-delivery/student-purchase-lms-plan.md`: section 10 (QA-01 to QA-18), section 11 (open items), and section 4 (the findings marked "check in QA").
- `docs/exam-site/plans/ui-delivery/seed-data-plan.md`: section 12 (SQ-01 to SQ-14), and section 14 (the live window).
- `docs/exam-site/plans/ui-delivery/ws8-final-qa.md`: how the web and API are started.
- `docs/exam-site/plans/ui-delivery/ws9-gaps-and-polish.md`, Part 4: the local dev guide.

## Database: confirm first

The user chose `coaching_center_qa` (26 Oct). On that day it held 12 users, all `@example.com` accounts from the old demo and staging seeds, and its schema was at V38; Flyway applies V39 and V40 when the API starts. Before you seed, count its users again: if any account is not `@example.com`, stop and ask. Never use `coaching_center_dev`, which holds local data (5,437 users, 617 exams and 114 orders).

Never run anything against `coaching_center_test`. That database is for the integration tests.

## Start the stack

1. **API.** Build the jar once under the lock `API` (`./mvnw -B -DskipTests package` in `api/`), then release `API`. Start the jar under your `QA` lock with `SPRING_PROFILES_ACTIVE=dev`. The environment, by name, from `api/.env` for the values and never printed:
   - `DB_URL` = the database you were given, `DB_USERNAME`, `DB_PASSWORD`
   - `WEB_ORIGIN=http://localhost:3000`
   - `SEED_DEMO=true`, the `SEED_INSTRUCTOR_*`, `SEED_INSTRUCTOR2_*` and `SEED_SYSTEM_ADMIN_*` names from section 9 of the seed plan, and `SEED_TEST_STUDENT_PASSWORD`
   - `DEV_EXPOSE_EMAIL_CODES=true` (local only; codes appear in the API log)
   - `MASTER_ADMIN_*` (already in `api/.env`)
   - the `SSLCZ_*` and `PAYMENT_*` sandbox names (already in `api/.env`)
   - `EXAM_MAIL_HOUR_BEFORE_ENABLED` unset, so the default off applies
   - `NEXT_PUBLIC_*` are web names only; do not set them for the API
2. **Web.** Create `web/.env.local` (git ignores it) with `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`, `NEXT_PUBLIC_SITE_URL=http://localhost:3000`, and `NEXT_PUBLIC_STAFF_URL=http://localhost:3000`. Build with `npm run build` under your `QA` lock, then `npm run start` on port 3000. Do not run `next dev`.
   - Add `NEXT_PUBLIC_BUNDLE_ORDERS=true` only for the bundle check, in a separate build, and say so in the report. Keep it off otherwise.
3. **Smoke.** `curl` the API health and `/api/v1/public/live-exam-categories`, and the web `/`, `/mcq`, `/written` and `/staff`. Stop and report if one fails.
4. **Live window.** `e2` is live for 30 minutes, starting 5 minutes before the seed run. Run the live checks (SQ-09 and the sitting) inside that window. If you are late, re-run the seed on the same database to refresh the schedule, then start again.

## Checks

Run every check in student plan section 10 (QA-01 to QA-18) and every check in seed plan section 12 (SQ-01 to SQ-14). Also run these, which the plan says to check in QA:

- S-008: the receipt shows the student's name and code, and the print view hides the page shell.
- S-009: the name-save error copy on the account page.
- S-010: the upcoming strip copy for an exam sold only inside its module.
- S-011: search results follow the door (MCQ or written).
- S-018: MCQ review shows no correct option or explanation for `e10` (withheld), and shows them for `e1` (released).
- Accessibility, R-9: each page you open at 375 px and 1440 px, both themes, both languages. Check no horizontal scroll (`document.documentElement.scrollWidth` equals the viewport width). Check keyboard reach to the main actions and that a dialog takes focus and closes with Escape.
- The admin copy: each staff page you open has no raw error code (for example `ACCOUNT_HAS_PAID_ORDERS`) on screen.

If API-BUNDLE is merged, also check the bundle path with the flag on: select two or three exams in one module, pay in the sandbox, and see one order and one grant per exam.

## Payments

Use the SSLCommerz sandbox only. Never a real payment. If a check needs a payment that the sandbox cannot make, mark it **not run** and say why.

## Report

1. Create `docs/exam-site/plans/ui-delivery/qa-e2e-report.md` on your branch. One table: ID, result (**pass**, **fail**, or **not run**), what you saw, the URL or API call, and the evidence (a screenshot or a log line, saved in your scratchpad, with its full path).
2. A "Findings for owners" list. For each failure: the ID, a short repro, the suspected file, and the owner from the plan (section 9.1). Name the owner, do not fix the code.
3. A "Not run" list with the reason for each item.
4. Commit the report. The message starts with `QA-E2E:` and ends with `Co-Authored-By: Claude <your model name> <noreply@anthropic.com>`.

## Rules

- **Lock.** `npm run build` and the API jar build each take their own lock (`QA` for the web, `API` for Maven) for the length of the build only. Hold `QA` for the whole run of the servers. Release `QA` when you stop the servers. Never delete another agent's lock.
- No product code changes. If you find a blocker, stop the run and send the finding to the integrator at once.
- Re-running the seed is allowed on the database you were given, and only there.
- Run the servers in the background, keep their output in your scratchpad, and stop them at the end.

## Done

Send:

```
READY TO MERGE: ui/qa-e2e (agent QA-E2E)
Last commit: <hash>
Checks: QA-01 to QA-18 <pass/fail/not run counts>, SQ-01 to SQ-14 <counts>, extras <counts>
Blockers: <IDs and owners, or none>
Not run: <IDs and why>
Database used: <name>; bundle flag on for the bundle check: yes or no
```

Stop after the READY block and wait for the integrator.
