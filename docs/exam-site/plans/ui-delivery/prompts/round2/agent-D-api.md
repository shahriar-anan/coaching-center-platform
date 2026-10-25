# Agent D — round 3: close the three sitting API gaps you found (WS4b)

WS4 is merged into `catalog-acquisition` (`76f068e`; lint, 134 tests and build green). Thank you. You reported three API gaps; you know this code best, so you close them now. This is a small API change plus the web follow-up in your own WS4 files.

Work only inside `F:\cc-wt\ws4` (check `git rev-parse --show-toplevel`). Never write under `F:\coaching-center-platform\` except the lock file.

## Setup

```
git switch -c ui/ws4b-sitting-api catalog-acquisition
```

`api/.env` has been copied into `F:\cc-wt\ws4\api\` by the integrator. Integration tests need `TEST_DB_URL=jdbc:postgresql://localhost:5432/coaching_center_test`, and `TEST_DB_USER` / `TEST_DB_PASSWORD` taken from `api/.env` (`DB_USERNAME`, `DB_PASSWORD`); never print the password. Follow `docs/exam-site/plans/api-completion/README.md` practices (one `ErrorCode` per condition, `*IT` on real PostgreSQL, no Flyway change expected; if one is needed it is `V39`, never edit `V1`–`V38`).

## API changes (package `com.coachingcenter.api.exams`)

1. **Open sitting on the exam read.** For a student, `GET /api/v1/live-exams/{id}` gains `sitting`: `{ "kind": "LIVE" | "RETAKE", "startedAt": ISO, "deadlineAt": ISO, "submittedAt": ISO | null }` from the student's own participant row, or `null` when there is none. Never another student's row. Staff reads are unchanged.
2. **"This is you" on the leaderboard.** Each row of `GET /api/v1/live-exams/{id}/leaderboard` gains `mine: boolean`, true only for the requesting student's own row (always false for staff). No ids or phones added.
3. **Kind on the result.** `GET /api/v1/live-exams/{id}/result` gains `kind`: `"LIVE"` or `"RETAKE"`.

Tests: extend the existing `*IT` classes that cover these reads (find them with a search for the endpoints). Cases: `sitting` null before take, filled during and after, absent for another student's row; `mine` true for own row and false for others and for staff; `kind` for a live sitting and a later attempt. Add a line for each to `docs/exam-site/current-api.md` under "Added for the UI delivery".

## Web follow-up (your WS4 files only)

- Read the deadline from `sitting.deadlineAt` and remove the session-storage workaround, so a reload, a new tab or a second phone keeps the countdown.
- Highlight the own leaderboard row from `mine`, and show the student's rank on the result page from that row.
- Label the later attempt on the result page and in `/results` from `kind`.
- Update your tests and mocks for the new fields.

## Checks and report

- API: Maven under the lock (`F:\coaching-center-platform\.heavy.lock` containing `D`; wait while it exists; delete after): the touched `*IT` classes, then the full suite from clean, alone (`mvn -q clean test`); only `BookOrderIT` and `CoursePurchaseIT` may fail.
- Web from `web/`: `npm run lint` 0 errors, `npm test`, `npm run build` under the lock.
- Add a "WS4b report" section at the end of `ws4-student-sitting.md`, commit, then send:

```
READY TO MERGE: ui/ws4b-sitting-api (agent D)
Last commit: <hash>
Checks: touched ITs green; full API suite <n> run, only BookOrderIT and CoursePurchaseIT failing; web lint 0 errors, tests <n> passed, build ok
Contract additions: sitting on GET /live-exams/{id}; mine on leaderboard rows; kind on result
Notes for other agents: <anything>
```

Do not run `next dev` or the API, do not open a browser.
