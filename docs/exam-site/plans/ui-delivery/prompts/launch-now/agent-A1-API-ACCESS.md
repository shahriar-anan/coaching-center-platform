# Launch prompt: A1 API-ACCESS (phase 2)

You are agent **A1 API-ACCESS**. You build the student-facing access endpoints and the access state on public responses, and the answer-release gating on the MCQ review. Your branch is `ui/api-access`.

## Setup

1. Create your worktree from `catalog-acquisition` (which has V39 merged):
   `git -C F:/coaching-center-platform worktree add F:/cc-wt/api-access -b ui/api-access catalog-acquisition`
2. Work only inside `F:\cc-wt\api-access`. Never write under `F:\coaching-center-platform\` except the lock file.
3. Copy `F:\cc-wt\w9-api\api\.env` into `F:\cc-wt\api-access\api\.env`. Test database keys only; never print values.

## Read first

- `docs/exam-site/plans/ui-delivery/student-purchase-lms-plan.md`: sections 2 (decisions), 4 (S-003, S-005, S-012, S-013, S-018, S-024, S-027), 5 (access matrix), 6.3 (answer release), 7 (V39, already merged), 8 (contract shapes), 9.2 (file ownership).
- `docs/exam-site/plans/api-completion/README.md` (API practices).

## Rules

- Never edit Flyway `V1` to `V39`. You do not write a migration.
- Do not edit `LiveExamAccess`. API-ADMIN is changing it to ignore revoked grants. Read it only.
- Do not edit the notice classes (`StudentNoticeWriter`, `StudentInboxService`, `StudentReminderJob`). A2 owns them.
- Maven runs only while `F:\coaching-center-platform\.heavy.lock` holds `API`. Write `API` to the file. Use the full path to the wrapper: `& 'F:\cc-wt\api-access\api\mvnw.cmd' -B clean verify -Dtest=<ClassName>`. Integration tests run through Surefire, so pass `-Dtest`. If another agent holds the lock, wait; never delete another agent's lock. Delete yours when the run ends.
- Add every new route to `AuditLogIT`.
- Document every new route and shape under "Added for the phase 2 UI (API-ACCESS)" in `docs/exam-site/current-api.md`.
- One commit per item ID. The message starts with the ID and ends with the attribution line.

## Items, in order

1. **Student access endpoint (S-003).** `GET /api/v1/me/live-exam-access` for students: `{ modules: [{ moduleId, via, orderId }], exams: [{ examId, moduleId, via, orderId }] }`. `via` is `ROSTER`, `MODULE`, `EXAM` or `MANUAL`. Source: roster rows, module and exam grants (`source` from V39: `ORDER`, `MANUAL`, `BUNDLE`), ignoring revoked grants. Read those tables; do not change the access service. Test each `via`, a revoked grant gives nothing, other students' rows never appear.
2. **Access object on public responses (S-003).** For a signed-in student only, the public module and public exam responses carry `access: { state, buyable, blockedReason }`. `state` is `NONE`, `MODULE`, `EXAM`, `ROSTER` or `MANUAL`. `blockedReason` is `null`, `PHONE_REQUIRED`, `PRICE_REQUIRED`, `ALREADY_HAS_ACCESS` or `CHECKOUT_OFF`. Signed-out responses have no `access` field. Test each state and reason.
3. **Checkout status (S-005).** `GET /api/v1/public/exam-site/status` returns `{ checkoutEnabled }` from the existing gateway configuration. It exposes no values. Test enabled and disabled.
4. **Category path on public modules (S-012).** Add `categoryPath` (root first, `{ id, titleEn, titleBn }`) to the public module view, the same shape as G10. Test.
5. **Upcoming exams: module-only flag (S-027).** Each row of `GET /api/v1/public/live-exams?upcoming=true` gains `moduleOnly: boolean`, true when the exam has no price of its own and its module is priced. Test both values.
6. **MCQ answer release in the review (S-018).** In `LiveExamSittingService.questionViews` and its callers, `correct` and `explanation` are `null` in every review unless `answers_released_at` is set for the exam and the student's sitting is over. The shape does not change. Written papers are not affected. Test: withheld before release, shown after release, not shown while the sitting is still open.
7. **Written deadline on the server (S-024).** Tests only, no behaviour change: an upload at the deadline is refused with `DEADLINE_PASSED`; the latest upload before the deadline counts when the deadline passes without a submit click; no upload gives `FAILED_TO_SUBMIT` with score 0.

## Checks

- The touched `*IT` classes green (`-Dtest=<Class>`).
- Full suite from clean, alone, under the lock. Only `BookOrderIT` and `CoursePurchaseIT` may fail.
- `AuditLogIT` covers every new route. `current-api.md` is updated.

## Done

1. Fill in "Report — API-ACCESS" at the end of `docs/exam-site/plans/ui-delivery/ws9-gaps-and-polish.md` on your branch.
2. Send:

```
READY TO MERGE: ui/api-access (agent A1 API-ACCESS)
Last commit: <hash>
Checks: touched *IT <n green>; full suite <n run, failures list>
Items done: <IDs> | partly: <IDs and what is left> | skipped: <IDs and why>
Contract changes: <list>
Notes for STU-PUR, STU-LMS and API-ADMIN: <anything they need>
```
