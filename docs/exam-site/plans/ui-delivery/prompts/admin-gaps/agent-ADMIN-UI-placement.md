# Prompt: ADMIN-UI, placement and the staff gaps (AG-05 part 2, plan section 5a)

You are agent **ADMIN-UI** again. Your components are merged, but no admin can use them yet, because they are not on any page. This run places them, and closes the other staff gaps listed in section 5a of the plan. Web only.

**The integrator's go:** OPS and AUT are merged (`d6506c9`). You may now edit the pages named below, and only the parts named.

## Setup

1. Reuse your worktree `F:\cc-wt\admin-ui` (branch `ui/admin-ui`, already merged). Check that it is clean, then run `git merge catalog-acquisition`. This should fast-forward.
2. Run `npm ci` in `web/` if `node_modules` is out of date.
3. Never write under `F:\coaching-center-platform\` except the lock file.

## Read first

- `docs/exam-site/plans/ui-delivery/student-purchase-lms-plan.md`: **section 5a** (every staff page and feature by role, with the gaps), section 5 (the access matrix), sections 6.1, 6.3, 6.6 and 6.7.
- Your components: `web/src/components/staff/access/` (`StudentAccessSection`, `AnswerReleaseSwitch`, `admin-error.ts`) and `web/src/components/staff/status/account-controls.tsx` (`AccountStatusToggle`, `DeleteAccountButton`).
- The pages below, in full, before you change them.

## Items

**P1. Exam page:** `web/src/app/(staff)/staff/exams/[examId]/page.tsx`. Replace the line `{/* SLOT: answer release (phase 2, STAFF-ADMIN-UI) */}` with `<AnswerReleaseSwitch examId={examId} />`. The switch already renders nothing for written papers. Change nothing else on the page.

**P2. Student page:** `web/src/app/(staff)/staff/students/[id]/page.tsx`.
- Remove the old access section (the `staffPeople.student.accessTitle` heading and the `AccessTable`). Put `<StudentAccessSection studentId={id} studentName={row.fullName} />` where the line `{/* SLOT: access list (phase 2, STAFF-ADMIN-UI) */}` is, above the exam history. One access list on the page, not two.
- The old table had a Master Admin link "add to roster" (`/staff/modules/{moduleId}?tab=roster`). Add it to `StudentAccessSection`, for Master Admin only. The payment link is already there.
- Replace the old `StatusToggle` (it shows for System Admin, and the API refuses with 403) with `<AccountStatusToggle kind="students" … />`. Add `<DeleteAccountButton kind="students" … />` next to it.
- `DeleteAccountButton` has no callback after a successful delete. Add an optional `onDeleted` prop in your file. Use it on this page to go back to `/staff/students`.
- Remove the functions and imports that are now unused (`AccessTable`, `StatusToggle`, `useSetStatus`, `useStudentAccess`, and so on). Remove only message keys that nothing uses any more, and check every literal and template key in `web/src` before you do.

**P3. Instructors page:** `web/src/app/(staff)/staff/instructors/page.tsx` (Master Admin only).
- In the `actions` cell: for an instructor who is active or suspended, show `<AccountStatusToggle kind="instructors" … />` and `<DeleteAccountButton kind="instructors" … />`.
- A pending instructor keeps the row menu with "reissue code".
- The delete refusal `ACCOUNT_HAS_MARKED_WORK` shows its own message and offers suspend.
- On a phone, the controls stay usable: stacked, or in the row menu.

**P4. Students list:** `web/src/app/(staff)/staff/students/page.tsx`.
- The row menu's suspend and restore item shows only for Master Admin. Today System Admin sees it, and the API refuses.
- Delete (Master Admin) uses the same refusal handling as `DeleteAccountButton`. On `ACCOUNT_HAS_PAID_ORDERS`, show "এই শিক্ষার্থীর পেমেন্ট বা অ্যাক্সেস আছে, তাই মুছে ফেলা যাবে না। অ্যাকাউন্ট নিষ্ক্রিয় করুন।" and offer suspend. Never show a raw code. Use `adminErrorText` for other errors.

**G1. Marked PDF on the marking page.** In the submission form or paper view under `web/src/components/staff/marking/`, add a "দেখুন" / "View" button when `evaluatedPdf` is true. It opens the uploaded marked PDF from `GET /api/v1/live-exam-participants/{id}/evaluated-pdf`, with the same blob and `PdfViewer` pattern as the answer PDF in `paper-views.tsx`. Add the call to `web/src/lib/api/exam/staff-marking.ts`. Add one key in `staffMarking` (bn and en). The button is for Master Admin, System Admin (read), and the assigned instructor, matching the API.

**T. Tests,** in `web/tests/staff-admin/`. Update the older tests these pages already have (`tests/ws6a`, `tests/ws6b`, `tests/ws9-ops`, `tests/ws9-aut`) where the markup changes. Cover:
- P1: an MCQ exam shows the switch, and a written paper does not.
- P2: one access list; grant shows for Master Admin and System Admin, and revoke for Master Admin only; the roster link for Master Admin only; System Admin sees no suspend and no delete; a delete refusal shows the message and offers suspend; a successful delete goes back to the list.
- P3: suspend, restore and delete per instructor; the marked-work refusal message.
- P4: System Admin has no suspend item; the paid-orders message on delete; no raw code.
- G1: the button shows only when `evaluatedPdf` is true, and it calls the right route.
- Extend your AG-07 access-matrix test to the students list, the student page and the instructors page.

## Rules

- Change only the parts named above. On `students/[id]/page.tsx`, the name card, facts, and exam history stay as they are.
- Tokens and the FND type scale only: no `text-[..]`, no `text-sm`, `text-xs` or `text-2xl`, no raw colours.
- Every changed page keeps its loading, empty, error and success states, works at 375 px and 1440 px, in both languages and both themes, and by keyboard. Dialogs take focus and return it.
- No API change. If a control needs something the API does not give, stop and report.
- One commit per item (P1, P2, P3, P4, G1, T). The message starts with the item ID and ends with `Co-Authored-By: Claude <your model name> <noreply@anthropic.com>`.
- **Lock.** `npm run build` only while `F:\coaching-center-platform\.heavy.lock` holds your line `ADMINUI <pid> <time>`. If the file is empty or missing, write it. If another name is there, wait, polling every 30 seconds. Never delete another agent's lock. Remove yours when the build ends, pass or fail.
- Tests in one process with `NODE_OPTIONS=--max-old-space-size=6144`.

## Checks

`npm run lint` (0 errors), `npx tsc --noEmit` (clean), `npx vitest run` (all pass), `npm run build` under the lock.

## Done

1. Add "Report — ADMIN-UI, placement" at the end of `docs/exam-site/plans/ui-delivery/ws9-gaps-and-polish.md`: each item done, partly or skipped, with its commit hash, and the test counts. Name each message key you removed.
2. Send:

```
READY TO MERGE: ui/admin-ui (agent ADMIN-UI, placement)
Last commit: <hash>
Checks: lint <…>, tsc <…>, tests <files and count>, build <…> under lock ADMINUI
Items done: P1, P2, P3, P4, G1, T | partly: <…> | skipped: <…>
Placed: AnswerReleaseSwitch (exam page), StudentAccessSection and account controls (student page), instructor controls (instructors page)
Removed: <old components and keys>
```

Stop after the READY block and wait for the integrator.
