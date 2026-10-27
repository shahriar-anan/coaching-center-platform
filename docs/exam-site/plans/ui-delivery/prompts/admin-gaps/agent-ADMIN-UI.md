# Prompt: ADMIN-UI (admin gaps AG-05 to AG-10)

You are agent **ADMIN-UI**. Work on the web only. Another agent, ADMIN-API, works on the API at the same time. Until its branch merges, mock the announcement history contract given below in your tests.

## Setup
1. `git -C F:/coaching-center-platform worktree add F:/cc-wt/admin-ui -b ui/admin-ui catalog-acquisition`
2. Work only in `F:\cc-wt\admin-ui`. Run `npm ci` in `web/`. Never write under `F:\coaching-center-platform\` except the lock file.

## Read first
- `docs/exam-site/plans/ui-delivery/student-purchase-lms-plan.md`: section 2b (AG-05 to AG-10), section 5 (access matrix), section 6 (rules).
- `docs/exam-site/plans/ui-delivery/prompts/staff-admin/agent-STAFF-ADMIN-UI.md`: the original brief for these screens.
- The components already built: `web/src/components/staff/access/`, `orders/`, `announcements/`, `status/`, and the typed client `web/src/lib/api/exam/staff-admin.ts`.

## Rules
- Owned: `web/src/components/staff/access/**`, `orders/**`, `announcements/**`, `status/**`, `web/src/app/(staff)/staff/announcements/**`, `system-status/**`, `orders/**`, `web/src/lib/api/exam/staff-admin.ts`, `web/src/i18n/messages/{bn,en}/staffAdmin.ts`, and `web/tests/staff-admin/**`.
- Placing components on other pages is a separate step (AG-05, second part). Don't edit `students/[id]/page.tsx`, `exams/[examId]/page.tsx` or the instructors page until the integrator says OPS and AUT are merged.
- Tokens and the FND type scale only. No `text-[..]`, no `text-sm`, `text-xs` or `text-2xl`, no raw colours.
- The contract to mock for the history (AG-06): `GET /api/v1/announcements?page=&size=` returns a page of `{ id, titleEn, titleBn, audience, moduleId, moduleTitleEn, moduleTitleBn, createdAt, recipients }`, newest first.
- Build lock: write `ADMINUI <pid> <time>` to `F:\coaching-center-platform\.heavy.lock`, only when the file is empty. Remove only your own lock.
- One commit per item, with the item ID first in the message and the attribution line at the end.

## Items
1. **AG-06 announcement history.** On the announcements page, a list of sent announcements under the form: the title in the student's language, with the fallback, the audience (all students, or the module's name), the created time, and the recipients. Include loading, empty, error and success states. Test it with the mocked contract.
2. **AG-07 access matrix.** A test per page and role, checking that every control the API would refuse is shown as information, not as a control. System Admin never sees suspend, restore, delete, confirm, reject, revoke, or the announcement form. Instructors see none of the admin pages. Fix any control that fails the test.
3. **AG-08 dialogs.** Focus moves into each confirm dialog when it opens, and returns to the trigger when it closes. Escape closes it. Cover revoke, confirm, reject, release, announcement and delete.
4. **AG-09 error copy.** Every error code the admin routes can return has a message in both languages. A test lists the codes and fails if any has no message. An unknown code shows the generic message, never the raw code.
5. **AG-10 status page.** A test with a fake secret value in the status response: the page must not show it anywhere.
6. **AG-05, second part (after the integrator's go).** Place the components on their pages. `StudentAccessSection` and the account controls (`AccountStatusToggle` and `DeleteAccountButton`, with `kind="students"`) go on the student page. `AnswerReleaseSwitch` goes on the exam page. The instructor status and delete controls (`kind="instructors"`) go on the instructors page. Change only those components, and only in the places the integrator names.

## Checks
- Lint has 0 errors, `tsc` is clean, the tests pass, and the production build passes under the lock.
- The keyboard and focus tests pass.

## Done
Fill in "Report — ADMIN-UI" in `docs/exam-site/plans/ui-delivery/ws9-gaps-and-polish.md` on your branch, then send:

```
READY TO MERGE: ui/admin-ui (agent ADMIN-UI)
Last commit: <hash>
Checks: lint <…>, tsc <…>, tests <…>, build <…> under lock ADMINUI
Items done: AG-06, AG-07, AG-08, AG-09, AG-10 | placed: <yes or no, and where> | partly: <…>
Mocked until merge: <the announcement history contract>
```
