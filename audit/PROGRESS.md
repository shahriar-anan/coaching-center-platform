# Exam portal audit progress

Read this file at the start of every phase. Detail lives in `audit/phase-N.md`.

The Rules Extract to use is in `audit/phase-1.md`. The extract in `audit/phase-0.md` is the replaced exam model. Do not score endpoints against it.

## Phase log

### Phase 0 — Orientation + Rules Extract — complete (2026-10-19)

Status counts (section 5.1 only): DONE-VERIFIED 5 | PARTIAL 1 | others 0

Blockers: none. Exam rules exist.

Estimate delta: +0d.

Notes: Repo map and the "web/ has no student exam screens" finding still stand. The 2026-10-19 rules extract, the "no live-exam code" claim, the `api-exam-*` acceptance reference, and the `e9f57b0` / "48 Java files" notes do not. A pointer was added at the top of `audit/phase-0.md` on 2026-10-22.

### Phase 1 — Backend runs + database — complete (2026-10-22)

Status counts (sections 5.2 and 5.5, 10 items): DONE-VERIFIED 2 | PARTIAL 5 | MISSING 1 | BROKEN 1 | UNKNOWN 1 | others 0

Blockers: none.

Estimate delta: +0d. The API context starts and 47 of 48 live-exam tests pass. The one failure matches the content lock. No feature gap for the website estimate.

Notes: Jar builds (Spring Boot 4.1.1, Java 21). `coaching_center_test` is at Flyway V28. `coaching_center_dev` is still at V23, and the HTTP server was not started. No live-exam seed. Swagger was not fetched. Rules Extract rebuilt from `phase-3-scope.md` and the five `api-live-*` stages.

## Carry-forward notes

- Phase 2 (auth): candidate login is not scored yet. Self-registration and activation are Phase 1 identity, not re-tested here. Do not start the dev profile unless Shahriar approves it. Startup on `api/.env` would apply V24–V28 to `coaching_center_dev`.
- Phase 3: score exam endpoints against `audit/phase-1.md`, not phase 0. `LiveRosterIT.markedStudentSeesPublishedExamsIncludingOnePublishedLater` did not reach its assertion (wall clock vs `2026-10-20T12:00:00+06:00`, `409 CONTENT_LOCKED`). The "marked student sees a later published exam" rule is unverified. Risk R6 (does a GET during an MCQ sitting return the saved answers?) was not scored as an endpoint.
- Two exam APIs are in the tree. The live routes are `/api/v1/live-exams` and `/api/v1/live-exam-*`. The old `/api/v1/exam-modules` controllers and `V14`–`V23` tables are the replaced model.
- Phase 5: Swagger and actuator were not requested on a running profile. `SecurityConfig` permits `/v3/api-docs/**` and `/swagger-ui/**` in every profile. Local PostgreSQL listens on `0.0.0.0:5432`. Do not call either exposed until checked.
- Phase 6: SSH only after an explicit go-ahead. Java and PostgreSQL on the VPS were not re-checked. The dev database has six student rows and four system-admin rows outside `example.com` / `example.org`. Addresses were not read. Do not wipe that database.
- Phase 7: `web/` is an admin app (Next 16, App Router, Tailwind, hand-written `fetch`). No exam screens. A student exam site is new work. There is no demo student or demo live exam to log into.
- `docs/phases/README.md` still says slice 2 is open. `phase-3-status.md` is newer and says it passed. The slice 2 tests passed on 2026-10-22.
- CI `BootstrapIT` was not re-run. The shared test database still holds many `MASTER_ADMIN` rows (268 on `coaching_center_test`). Do not fix that by deleting rows.
- Untracked audit notes and `docs/Client Request/` are local. Mobile catalog files are dirty and out of scope.
