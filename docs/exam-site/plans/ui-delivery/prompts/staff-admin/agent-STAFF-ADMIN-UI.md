# Prompt: STAFF-ADMIN-UI (phase 2, the admin screens)

You are agent **STAFF-ADMIN-UI**. You build the Master Admin and System Admin screens for access, orders, answers, announcements, instructor accounts, and the configuration status page. The API for every screen is merged on `catalog-acquisition`. You build web only.

## Setup

1. Create your worktree from `catalog-acquisition`:
   `git -C F:/coaching-center-platform worktree add F:/cc-wt/staff-admin -b ui/staff-admin catalog-acquisition`
2. Work only inside `F:\cc-wt\staff-admin`. Run `npm ci` in `web/`.
3. Never write under `F:\coaching-center-platform\` except the lock file.

## Read first

- `docs/exam-site/plans/ui-delivery/student-purchase-lms-plan.md`: sections 5 (access matrix: who can do what), 6.1 (grant, revoke and confirm), 6.3 (answer release), 6.4 (announcements), 6.6 (blocked deletion), 6.7 (suspend and restore), 8 (contracts), 9.1 (your row).
- `docs/exam-site/ui/admin-prompt.md` (the staff look). Staff screens use the admin style, not the student mockup.
- `docs/exam-site/plans/ui-delivery/bangla-copy.md`. Bengali digits through the formatters.
- The FND components in `web/src/components/ui/` (`dialog.tsx`, `table.tsx`, `pill.tsx`, `row-menu.tsx`, `summary-strip.tsx`, `section-nav.tsx`, `sticky-action-bar.tsx`, `split-browser.tsx`) and the hooks in `web/src/lib/hooks/`.

## Owned files

- `web/src/app/(staff)/staff/system-status/**` (new page)
- `web/src/app/(staff)/staff/announcements/**` (new page)
- `web/src/components/staff/access/**` (new folder: the access section, the grant form, the revoke dialog)
- `web/src/components/staff/orders/**` (new folder: the pending-orders list and the confirm and reject dialogs)
- `web/src/components/staff/announcements/**` (new folder)
- `web/src/components/staff/status/**` (new folder)
- `web/src/lib/api/exam/staff-admin.ts` (new: the typed client for every route below)
- `web/src/i18n/messages/{bn,en}/staffAdmin.ts` (new namespace; add it to the messages index only through the integrator)
- `web/tests/staff-admin/**` (new)

**Placement, not ownership:** two existing pages take a slot from you. Build only the slot, as a component you import:
- `web/src/app/(staff)/staff/students/[id]/page.tsx` (OPS): put `StudentAccessSection` (from `components/staff/access/`) where the marked comment `SLOT: access list` says.
- `web/src/app/(staff)/staff/exams/[examId]/page.tsx` (AUT): put `AnswerReleaseSwitch` (from `components/staff/access/`) where the marked comment `SLOT: answer release` says.

Do not change anything else on those two pages. Do not touch the catalog, the marking, or the results folders.

## The routes you use (all merged)

| Need | Route | Who | Notes |
|---|---|---|---|
| A student's access rows | `GET /api/v1/students/{id}/live-exam-access` | Master Admin, System Admin | Each row has `via` (`ROSTER`, `MODULE_PURCHASE`, `EXAM_PURCHASE`, `MANUAL`), `grantId` (null for roster rows), `orderId`, `tranId`, `exams`. |
| Grant access by hand | `POST /api/v1/live-exam-grants` | Master Admin, System Admin | Body: `studentId`, `scope` (`MODULE` or `EXAM`), `moduleId` or `examId`, `reason`. Errors: `GRANT_ALREADY_ACTIVE`, `RESOURCE_NOT_FOUND`. |
| Revoke a grant | `DELETE /api/v1/live-exam-grants/{id}?reason=` | Master Admin only | Use the row's `grantId`. Shows a reason box. |
| Pending orders | `GET /api/v1/live-exam-orders?status=INITIATED` | Master Admin, System Admin | Filter on `status` and `tranId`. |
| Confirm a pending order | `POST /api/v1/live-exam-orders/{id}/confirm` | Master Admin only | Body: `reason` (required, 1 to 500). Errors: `ORDER_NOT_PENDING`. |
| Reject a pending order | `POST /api/v1/live-exam-orders/{id}/reject` | Master Admin only | Body: `reason`. Same error. |
| Answer release | `PUT /api/v1/live-exams/{id}/answers-released` | Master Admin, System Admin | Body: `{ "released": true \| false }`. The exam view now carries `answersReleasedAt` (null when withheld) for staff. |
| Announcement | `POST /api/v1/announcements` | Master Admin only | Body: `titleEn`, `titleBn`, `bodyEn`, `bodyBn` (each may be blank, not all four), `audience` (`ALL` or `MODULE`), `moduleId` when `MODULE`. |
| Instructor suspend or restore | `PATCH /api/v1/instructors/{id}/status` | Master Admin only | |
| Instructor delete | `DELETE /api/v1/instructors/{id}` | Master Admin only | Refused with `ACCOUNT_HAS_MARKED_WORK` (409) when the instructor has evaluated work. Show that message, not a generic one. |
| Student suspend or restore | `PATCH /api/v1/students/{id}/status` | Master Admin only | |
| Student delete | `DELETE /api/v1/students/{id}` | Master Admin only | Refused with `ACCOUNT_HAS_PAID_ORDERS` (409). Show: "এই শিক্ষার্থীর পেমেন্ট বা অ্যাক্সেস আছে, তাই মুছে ফেলা যাবে না। অ্যাকাউন্ট নিষ্ক্রিয় করুন।" and offer suspend. |
| Configuration checks | `GET /api/v1/system-status` | Master Admin only | Returns `checks: [{ name, ok, note }]`. Names and yes or no only; never show a value. |

## What to build

1. **Student access section** (`StudentAccessSection`, in the student page slot). A table of access rows: the source label (roster, module purchase, exam purchase, manual grant), the module and exam titles, the order's `tranId` when there is one. For each row with a `grantId`, a revoke action (Master Admin only). A grant form (Master Admin and System Admin): choose the scope, the module or exam, and a reason. Hide revoke from System Admin; show a sentence saying who can revoke.
2. **Answer release switch** (`AnswerReleaseSwitch`, in the exam page slot). A switch with the current state from `answersReleasedAt`: "উত্তর প্রকাশিত" or "উত্তর এখনো প্রকাশ হয়নি". Changing it calls the PUT route. A confirm dialog names the exam. Written papers do not show it.
3. **Pending orders page** (`/staff/orders` or a section on the payments page, as the existing navigation allows; add the link in the staff navigation through the integrator). Lists orders with status `INITIATED`, their `tranId`, the student, the amount, and the age. Master Admin sees Confirm and Reject, each with a required reason. System Admin sees the list and information only, with a sentence on who can confirm.
4. **Announcements page** (`/staff/announcements`, Master Admin only). A form: title and body in Bangla and English (each optional, not all four blank), audience (all students or one module, with a module picker). A confirm dialog states the audience and the count when the API gives one. System Admin sees the page as information only, never a form.
5. **Instructor accounts.** On the instructors page (OPS's page): add a status control (suspend and restore) and a delete action, Master Admin only. Delete shows the `ACCOUNT_HAS_MARKED_WORK` message when refused. Fix the status call so it uses `PATCH /api/v1/instructors/{id}/status` (the staff people page currently calls `/api/v1/${kind}/${id}/status` for instructors, which the API now serves).
6. **System status page** (`/staff/system-status`, Master Admin only). One row per check: the name in plain words, a Yes or No pill, and the note. A failed check shows what to change, in one sentence. System Admin is redirected to the staff home.

## Rules

- Role gates in the UI match the API. A control the API would refuse is never shown as active. Show information instead, with a sentence about who can do it.
- Every new list or page has loading, empty, error and success states, works at 375 px and 1440 px, both languages, both themes, and by keyboard.
- Tokens and the FND type scale only. No `text-[..]`, no `text-sm` or `text-xs` or `text-2xl`, no raw colours.
- Use the typed client in `staff-admin.ts` for every call. Map each error code to a message from `staffAdmin` in both languages. A code without a message shows the generic message, never a raw code.
- Lint 0 errors, `tsc` clean, the tests passing, and the production build passing under the lock.
- Lock: write `STAFFADMIN <pid> <time>` to `F:\coaching-center-platform\.heavy.lock`, only when the file is empty. Remove only your own lock.
- Commit one commit per item, with the item ID at the start of the message.

## Done

1. Fill in "Report — STAFF-ADMIN-UI" in `docs/exam-site/plans/ui-delivery/ws9-gaps-and-polish.md` on your branch. List each item as done, partly or skipped, with the reason.
2. Send:

```
READY TO MERGE: ui/staff-admin (agent STAFF-ADMIN-UI)
Last commit: <hash>
Checks: lint <…>, tsc <…>, tests <…>, build <…> under lock STAFFADMIN
Items done: <IDs> | partly: <IDs and what is left> | skipped: <IDs and why>
Slots filled: StudentAccessSection, AnswerReleaseSwitch, instructor status fix (yes or no)
Navigation links needed from the integrator: <list>
```

## Stop and report

Stop after the READY block and wait for the integrator. Do not start on the marking button (G1), which is a separate item for MRK.
