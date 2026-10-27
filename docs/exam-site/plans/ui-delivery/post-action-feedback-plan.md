# After-action feedback and redirects: analysis and plan

Written 2026-10-26 for Shahriar. Covers what a student or staff member sees after an action finishes: a confirmation, a redirect, or nothing. No code has been changed for this plan.

> **How to read this plan:** sections 1 to 5 are the first analysis of the exam submit problem. Section 9 records the decisions. Section 10 is the full-app analysis and holds the current problem list (10.1) and the build order (10.3). Where an earlier section disagrees with section 9 or 10, the later section wins. Section 7 lists the checks.

## 1. The reported problem

After an MCQ exam is submitted, the student gets no confirmation and is not taken anywhere sensible. The screen stays on the paper with a score card and a button label that says the answers can still be changed.

## 2. Where it happens (verified in the code)

### 2.1 MCQ submit (`web/src/components/student/sitting/mcq-paper.tsx`)
- `doSubmit` (around lines 278 to 293): after a successful submit it stores the result with `setResult(posted)` and closes the dialog. It shows no toast, sends the student nowhere and gives no "submitted" message. The only feedback is the score card (`ScoreCard`, around line 339) and the button label changing to "you can still change your answers" (around line 409).
- Result: the student cannot tell from the page whether the submit worked. The score card looks like a result, but the rules say the answers can still change until the deadline.

### 2.2 Deadline auto-submit (same file, around lines 249 to 266; `web/src/components/student/sitting/sitting-entry.tsx` around lines 138 and 159)
- When the deadline passes, the paper posts the saved answers and calls `onClosed`, which runs `router.replace('/exam/<id>/result')`. There is no message saying time is up or that the answers were submitted. The only announcement goes to screen readers (`sitting.warn.ended`).

### 2.3 Written upload (`web/src/components/student/sitting/written-sitting.tsx`, around lines 58 to 75 and 120 to 140)
- A successful upload sets a "stored" line under the box. There is no toast and no other confirmation. The file name and time are not shown after a reload unless the server returns them (see also the earlier audit item B-01).
- Deadline: the countdown calls `onClosed` and the student goes to the result page with no message.

### 2.4 Exam page after a submit (`web/src/components/student/catalog/exam-page.tsx`, around line 174)
- When the status is SUBMITTED and the window is still open, the primary button says "Resume". The student does not see a "submitted" state, and "Resume" suggests that the exam is unfinished.

### 2.5 Other places that redirect silently
- Start exam (`start-confirm.tsx`, `sitting-entry.tsx` around lines 46 and 54): the URL is replaced after the take. That is correct, but nothing confirms the start.
- Login, register, set-phone and logout (`session-context.tsx`, `set-phone/page.tsx`): these redirect correctly. Checked: register signs the student in and goes home; set-phone goes to `next`.
- Single-exam and module purchase (`purchase-result.tsx`): the success page is the confirmation. Correct. Bundle purchase is not: see section 10.1, item 5.
- Notices (mark read), notice bell and other small actions: no feedback. Lower priority. Covered in section 10.1.

### 2.6 Root cause
- The app has a toast system (`components/ui/toast`) and 28 files use it, but the sitting flow, the notice actions and some other actions do not. Nothing carries a message across a navigation. A redirect after an action has nowhere to put a message, so the student sees either nothing or the next page with no explanation.

## 3. Target behaviour

| Case | After the action | Feedback |
|---|---|---|
| MCQ submit (student taps Submit and confirms) | Go to the exam page (`/exam/<id>`) | Toast: "Answers submitted. You can change them until the deadline." Exam page shows a Submitted state (see 3.1) |
| MCQ deadline (time runs out) | Go to the result page (`/exam/<id>/result`) | Notice: "Time is up. Your answers were submitted automatically." |
| Written upload succeeds | Stay on the upload page | Toast: "Script uploaded: <file name>." The stored line shows the file name and time |
| Written deadline | Go to the result page | Notice: "Time is up. Your script was not submitted." when no file was stored; "Your script was submitted." otherwise |
| Start exam | Stay on the paper (existing) | No change |
| Exam page, status SUBMITTED, window open | Stays on the exam page | Primary button "Review answers" (was "Resume"); status line "Submitted at <time>. You can change answers until <deadline>." |

### 3.1 Exam page Submitted state
- Header pill: Submitted (existing `ExamStatusPill`).
- Status line under the title: "Submitted at <time>. You can change answers until <deadline>."
- Primary button: "Review answers" (goes to the paper).
- Secondary text link: "Result after the exam ends" (plain text, not a button) when the result is not published.

## 4. Mechanism

### 4.1 Carrying a message across a redirect
- Use a query parameter: `?notice=<code>`, for example `?notice=submitted`, `?notice=timeUp`, `?notice=timeUpNoFile`.
- A small hook `useNotice()` reads the code on mount, shows the matching toast from the i18n catalog, and removes the parameter with `router.replace` so a reload does not repeat it.
- Why a query parameter: it survives a reload and needs no storage. It only works on the pages that read it, which are the exam page, the result page and the written page.
- Codes live in one list (`lib/notices/codes.ts`) so each code has one place for its copy in English and Bangla.

### 4.2 Toast consistency
- The MCQ and written sittings use the same toast API as the rest of the app.
- Every redirect that follows an action gets a notice code. A redirect with no message is a bug.

## 5. Other items to check (sweep)

**Done.** The sweep was carried out as the full-app analysis in section 10. Its results replace the list that was here. The account, staff publish, final save, archive and delete actions were confirmed in the code to show toasts (section 10.2). Bundle purchase was found to be broken (section 10.1, item 5).

## 6. Build order

**Superseded by section 10.3.** Kept for the history of the plan.

1. Notice codes and the `useNotice` hook, with English and Bangla copy.
2. MCQ submit: go to the exam page with `?notice=submitted`; exam page reads it and shows the toast.
3. Deadline redirect for MCQ and written: append `?notice=timeUp` (or `timeUpNoFile`) to the result page URL.
4. Written upload: toast on success; stored line shows the file name and time.
5. Exam page Submitted state and the "Review answers" label.
6. Sweep of the items in section 5.
7. Tests: unit tests for `useNotice` and the copy keys; the existing sitting tests updated for the new redirect.

## 7. Checks

- Browser, with a seeded exam: take an MCQ, submit, confirm the toast appears, the URL is the exam page, and a reload does not show the toast again.
- Browser: let an MCQ run out of time; confirm the result page and the time-up notice.
- Browser: upload a written script; confirm the toast and the stored line.
- Browser: an exam in the submitted state shows "Review answers" and the submitted status line.
- Browser: a bundle purchase that returns from payment (staged order) shows the title, amount and a "Go to" button on the success page; a bundle order with no payment URL shows an error.
- Browser: "Mark all read" on the notices page shows a toast; a failed request shows an error toast.
- Browser: a staff status change (activate or deactivate) and an admin delete each show a toast; a paste of questions shows "N questions added".
- Browser: from a page that sends the user to login with `next`, going through forgot password and reset returns the user to that page after login.
- No horizontal overflow at 360, 390, 768 and 1440 px, light and dark, for the pages changed.

## 8. Decisions needed

**Answered:** all three were decided on the recommended option. See section 9.

1. **MCQ submit destination:** the exam page with a toast (recommended), or stay on the paper with a banner?
2. **Written upload:** stay on the page with a toast (recommended), or go to the exam page?
3. **Button label in the submitted state:** "Review answers" (recommended) or "Change answers"?

## 9. Decided approach (recorded 2026-10-26)

1. **MCQ submit:** go to the exam page with a toast ("Answers submitted. You can change them until the deadline."). The exam page then shows the Submitted state.
2. **Written upload:** stay on the upload page and show a toast with the file name. The stored line shows the file name and time.
3. **Submitted-state button:** "Review answers". It opens the paper, where the answers can still be changed until the deadline.
4. **Deadline (both MCQ and written):** go to the result page with a time-up notice, as in section 3.

## 10. Full-app analysis (2026-10-26)

Method: every component in the exam site that runs an action (a mutation) was listed with its number of toasts and redirects. Each one with neither was read to see whether it shows an on-page result instead. The legacy course and book screens are out of scope.

### 10.1 Problems found

| # | Area | Where | What happens now | Severity |
|---|---|---|---|---|
| 1 | MCQ submit | `mcq-paper.tsx` `doSubmit` | No confirmation, no redirect (section 2.1) | High |
| 2 | MCQ and written deadline | `mcq-paper.tsx`, `written-sitting.tsx`, `sitting-entry.tsx` | Silent redirect to the result page (section 2.2) | High |
| 3 | Written upload | `written-sitting.tsx` `submit` | No toast; only a small line (section 2.3) | Medium |
| 4 | Exam page after submit | `exam-page.tsx` | Button says "Resume" for a submitted paper (section 2.4) | Medium |
| 5 | Bundle purchase (several exams) | `bundle-selection.tsx` `buy` | Does not save the order details the result page reads, so after payment the success page has no title, amount or "Go to exam" button. If the API returns no payment URL, the button does nothing and says nothing | High |
| 6 | Notices | `notices/page.tsx` | "Mark read" and "Mark all read" give no feedback, and a failure is silent (no error handling) | Low |
| 7 | Staff: activate or deactivate a student or admin | `staff/students/page.tsx`, `staff/admins/page.tsx` | The status changes with no toast, and a failure is silent. (Correction 2026-10-26: the instructors page does not change status; its status changes go through account-controls, which already toast) | Medium |
| 8 | Staff: delete an admin | `staff/admins/page.tsx` | No toast after the delete | Low |
| 9 | Staff: paste questions | `paste-questions.tsx` | The dialog closes after adding questions, with no "N questions added" message | Low |
| 10 | Password reset and account activation | `reset-password/page.tsx`, `activate/page.tsx` | Success links to the login page without the `next` destination, so the user lands on the home page instead of where they started. This applies only when the user arrived with a `next` (for example from a buy prompt through "forgot password"); activation of an admin-created account usually has none | Low |

### 10.2 Checked and correct

Login (with `next`), register (signs in, then verify step), set-phone (returns to `next`), logout, session expiry, start exam, single-exam and module purchase (sheet, then the result page), purchase result states, account name, phone and password changes, staff category, module and exam create, edit, delete and archive (toast and redirect), cover upload, marking and result publishing, order confirm and reject, student access grant and revoke, announcements.

### 10.3 Revised build order (replaces section 6)

1. Notice codes and the `useNotice` hook (section 4.1), English and Bangla copy.
2. Student exam flow: items 1 to 4 (the decided approach in section 9).
3. Bundle purchase: save the order details before redirecting, and show an error when there is no payment URL (item 5).
4. Notices: a short toast on "Mark all read", and an error toast on failure (item 6).
5. Staff: toasts for activate, deactivate and delete, with error toasts (items 7 and 8); a "N questions added" toast after a paste (item 9).
6. Auth: carry `next` through reset and activation to the login link (item 10).
7. Tests for each item, then the browser checks in section 7, extended to the bundle return page and one staff status change.

**Progress (2026-10-26).** Built and committed in `2d0b84d`:
- Items 1 to 6 and the staff part of item 7 are in code. Item 10 is partly done: `next` is carried through forgot and reset password; activation is not changed.
- Checks run: `tsc` clean; full vitest 104 files, 625 tests pass (run on the staged tree too); eslint clean on changed files.
- Browser: the MCQ submit toast and the time-up toast on the result page both show, in Bangla (the default locale). Mark-all read shows its success toast and an error toast on a failed call. A student status change shows its success toast and the error toast on a failed call, and the status was put back. Admin delete and paste "Questions added" show their toasts. The bundle error case shows the payment-start error; the bundle success page shows the saved title, amount and transaction ID.
- Mocked where a real change would touch demo data: admin delete (DELETE), paste (question create), notice list (one unread notice injected), bundle order and payment status.
- Covered by a unit test, not a browser run: the written upload toast (no live written exam for the demo student).
- Not checked: the `next` path through the reset-password success button (the forgot and reset pages keep `next` in their links; the success step needs a real reset).
- Seen during the checks, not part of this plan: the client signs the user out when a request returns 401 and the refresh retry fails. Two concurrent refresh calls can race, and the second one can fail, which sends the user to login. Seen once in about three runs. Not fixed; it lives in `web/src/lib/api/client.ts`.
