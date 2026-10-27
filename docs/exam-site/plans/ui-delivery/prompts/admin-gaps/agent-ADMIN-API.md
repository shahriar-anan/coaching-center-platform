# Prompt: ADMIN-API (admin gaps AG-01 to AG-04)

You are agent **ADMIN-API**. Work on the API only. Another agent, ADMIN-UI, works on the web at the same time and doesn't touch the API.

## Setup
1. `git -C F:/coaching-center-platform worktree add F:/cc-wt/admin-api -b ui/admin-api catalog-acquisition`
2. Work only in `F:\cc-wt\admin-api`. Never write under `F:\coaching-center-platform\` except the lock file.
3. For the test run, read the database keys from `F:\coaching-center-platform\api\.env` into the environment. Never print values.

## Read first
- `docs/exam-site/plans/ui-delivery/student-purchase-lms-plan.md`: section 2b (AG-01 to AG-04), section 5 (who can do what), section 6.4 (announcements), section 8 (contracts).
- `api/src/main/java/com/coachingcenter/api/exams/AnnouncementService.java` and its controller.
- `docs/exam-site/current-api.md`: the announcement and staff access sections.

## Rules
- Never edit Flyway `V1` to `V40`. This task needs no migration. If you think it does, stop and report.
- Owned files: `AnnouncementService`, the announcement controller, a new read method for announcements, the tests `LiveGrantAdminIT`, `PublicAccessIT`, `AuthorizationIT`, `LiveSittingIT`, a new `SystemStatusIT`, `current-api.md`, and `AuditLogIT`.
- Maven runs only under the lock. Write `ADMIN-API <pid> <time>` to `F:\coaching-center-platform\.heavy.lock`, only when the file is empty. Run the wrapper by its full path: `& 'F:\cc-wt\admin-api\api\mvnw.cmd' -B clean verify -Dtest=<Classes>`. When your run ends, remove only your own lock.
- Don't run the full suite. The integrator runs it once, after the merge.
- One commit per item, with the item ID first in the message and the attribution line at the end.

## Items
1. **AG-01 announcement history.** `GET /api/v1/announcements?page=&size=`, Master Admin only, newest first, paged with `PageResponse`. Each row has `id`, `titleEn`, `titleBn`, `audience`, `moduleId`, `moduleTitleEn`, `moduleTitleBn` (for MODULE only), `createdAt`, and `recipients` (the count of `student_notices` rows linked by `announcement_id`). The list carries no body. Test: Master Admin gets 200 with the rows; System Admin gets 403; a student gets 403; an announcement with no recipients shows 0.
2. **AG-02 answers released.** Assert `answersReleasedAt` on the staff exam view: null when withheld, set after release, null again after withdrawing. Assert the student view is null in both states. Put the test in `LiveSittingIT` or `LiveExamIT`.
3. **AG-03 system status role gate.** New `SystemStatusIT`. Master Admin gets 200, with check names and no value strings. System Admin, Instructor and Student each get 403.
4. **AG-04 grant rows.** Assert that a roster row has `grantId` null, and that a revoked grant doesn't appear in the access list at all.
5. Add `GET /api/v1/announcements` to `AuditLogIT`, and document it under "Added for the admin gaps (ADMIN-API)" in `current-api.md`.

## Checks
- The touched test classes are green, under the lock.
- The new endpoint and the new assertions are documented.

## Done
Fill in "Report — ADMIN-API" in `docs/exam-site/plans/ui-delivery/ws9-gaps-and-polish.md` on your branch, then send:

```
READY TO MERGE: ui/admin-api (agent ADMIN-API)
Last commit: <hash>
Checks: <classes and results>
Items done: AG-01, AG-02, AG-03, AG-04 | partly: <…> | skipped: <…>
Contract: GET /api/v1/announcements shape, one line
```
