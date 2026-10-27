# Launch prompt: API-ADMIN (phase 2, A3)

You are agent **API-ADMIN**. You build the admin access rules, the schema change V39, and the account rules. Your branch is `ui/api-admin`. Other API agents (A1, A2, A4) wait for your V39 commit.

## Setup

1. Create your worktree from `catalog-acquisition`:
   `git -C F:/coaching-center-platform worktree add F:/cc-wt/api-admin -b ui/api-admin catalog-acquisition`
2. Work only inside `F:\cc-wt\api-admin`. Never write under `F:\coaching-center-platform\` except the lock file.
3. Copy `F:\cc-wt\w9-api\api\.env` to `F:\cc-wt\api-admin\api\.env` (test database keys only; never print values).

## Read first

- `docs/exam-site/plans/ui-delivery/student-purchase-lms-plan.md`: sections 2 (decisions R-1 to R-11), 5 (who can do what), 6.1, 6.3, 6.4, 6.6, 6.7, 7 (the V39 spec), 8 (contract shapes), 9.2 (file ownership).
- `docs/exam-site/decisions.md`.
- `docs/exam-site/plans/api-completion/README.md` (API practices).

## Rules

- Never edit Flyway `V1` to `V38`. Only you write `V39`.
- Maven runs only while `F:\coaching-center-platform\.heavy.lock` holds `API`. Write `API` to the file. Use the full path to the wrapper: `& 'F:\cc-wt\api-admin\api\mvnw.cmd' -B clean verify -Dtest=<ClassName>`. Integration tests run through Surefire, so pass `-Dtest`. Delete the lock when the run ends.
- Add every new route to `AuditLogIT`.
- Document every new route and shape under "Added for the phase 2 UI (API-ADMIN)" in `docs/exam-site/current-api.md`.
- One commit per item ID. The message starts with the ID and ends with the attribution line.
- Do not write notices. A2 owns notices. Do not edit the review gating in `LiveExamSittingService.questionViews`. A1 owns that.

## Items, in order

1. **V39 (S-019).** Apply exactly the table in plan section 7. Commit it alone, first. Test: it applies from clean, and the demo seed still runs. Then send the integrator `V39 READY: <hash>` and wait for the merge before starting item 2.
2. **Manual grant (R-3).** `POST /api/v1/live-exam-grants` for Master Admin and System Admin. Body: student id, scope `MODULE` or `EXAM`, module or exam id, reason (up to 500 characters). Errors: `GRANT_ALREADY_ACTIVE` (409), `RESOURCE_NOT_FOUND` (404). Audit `ACCESS_GRANTED`. Tests for each role.
3. **Revoke (R-2).** `DELETE /api/v1/live-exam-grants/{id}`, Master Admin only. Sets `revoked_at`, `revoked_by`, `revoke_reason`. `LiveExamAccess` ignores revoked grants. A new grant after a revoke works. Audit `ACCESS_REVOKED`.
4. **Confirm or reject a pending order (R-8).** `POST /api/v1/live-exam-orders/{id}/confirm` and `/reject`, Master Admin only. Confirm sets `SUCCEEDED`, channel `MANUAL-CONFIRMED`, and creates a grant with source `ORDER`. Reject sets `FAILED` with `rejected_reason`. Both return `ORDER_NOT_PENDING` (409) when the order is not pending. Audit `ORDER_CONFIRMED` and `ORDER_REJECTED`.
5. **Answer release action (R-4).** `PUT /api/v1/live-exams/{id}/answers-released` with `{ "released": true|false }`, Master Admin and System Admin. Sets or clears `answers_released_at`. Audit `ANSWERS_RELEASED` and `ANSWERS_WITHHELD`.
6. **Student deletion blocked (R-10, S-021).** `AccountService.delete` refuses a student with `ACCOUNT_HAS_PAID_ORDERS` (409) when the student has any `SUCCEEDED` order or any grant, active or revoked. Test both sides.
7. **Permission narrowing (S-022).** In `IdentityPermissionContributor`, `STUDENT_STATUS` becomes Master Admin only. System Admin keeps read, create and update. Update `AuthorizationIT`.
8. **Instructor status and delete (S-023).** `PATCH /api/v1/instructors/{id}/status` and `DELETE /api/v1/instructors/{id}`, Master Admin only. Delete is refused with `ACCOUNT_HAS_MARKED_WORK` (409, new code) when the instructor has evaluated any submission.
9. **Announcements (R-5, S-020).** `POST /api/v1/announcements`, Master Admin only. Body: `titleEn`, `titleBn`, `bodyEn`, `bodyBn` (each may be blank, not all four), `audience` `ALL` or `MODULE` with `moduleId`. Write an `announcements` row and one `student_notices` row of kind `ANNOUNCEMENT` per target student, from a class you own. Do not add it to `StudentNoticeWriter`, which is A2's file. Audit `ANNOUNCEMENT_SENT`.
10. **Suspend and restore (R-2).** For students, system admins and instructors, Master Admin only. Audit `USER_SUSPENDED` and `USER_RESTORED`.

## Checks

- The touched `*IT` classes green.
- The full suite from clean, alone, under the lock. Only `BookOrderIT` and `CoursePurchaseIT` may fail.
- `AuditLogIT` covers every new route. `current-api.md` is updated.

## Done

1. Fill in "Report — API-ADMIN" at the end of `docs/exam-site/plans/ui-delivery/ws9-gaps-and-polish.md` on your branch.
2. Send:

```
READY TO MERGE: ui/api-admin (agent API-ADMIN)
V39 commit: <hash>
Last commit: <hash>
Checks: touched *IT <n green>; full suite <n run, failures list>
Items done: <IDs> | partly: <IDs and what is left> | skipped: <IDs and why>
Contract changes: <list>
Notes for A1, A2 and A4: <anything they need>
```
