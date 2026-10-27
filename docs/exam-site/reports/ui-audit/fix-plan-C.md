# Fix plan for Agent C findings (staff and admin)

Written 2026-10-26 from `findings-C.md`. This is a plan only. Agent C does not change app code (plan section 7). Shahriar decides which findings are fixed. Each fix is then retested by the affected audit route (plan section 10, step 5).

Totals: 21 findings. Blocker 2, High 3, Medium 12, Low 4.

## 1. Decisions needed first

These findings depend on what the requirements say. Nothing else should be changed for them until Shahriar decides.

| Finding | Question for Shahriar | Options |
|---|---|---|
| C-01 (Blocker) | Can a staff user confirm a pending exam order paid by hand? `decisions.md` and `feature-scope.md` say no. The R-8 plan says yes. | (a) Remove Confirm and Mark-as-failed from the UI and the API, and change R-8. (b) Keep them and amend `decisions.md`. |
| C-11 (Blocker) | Which fields are required to create an exam, and does the dialog ask for them? | (a) Add description, rules, paper language and duration to the New exam dialog. (b) Create the exam with titles only and fill the rest in the editor (the API must then accept it). |
| C-12 (High) | Are module descriptions required? The API says yes; the form does not show it. | (a) Make them required in the form. (b) Make them optional in the API. |
| C-02 (High) | Should assigned instructors open their module and exam (read-only)? | (a) Yes: open the module and exam pages to the assigned instructor, read-only. (b) No: the module list shows only titles, and the instructor works from the submissions list. |
| C-05 (Medium) | Can a System Admin publish an exam? | Yes (`feature-scope.md`) or no (the plan's matrix). |
| C-06 (Medium) | Who may release MCQ answers to students, and when? | Remove the switch, or write the rule into `decisions.md`. |
| C-07 (Medium) | Can a System Admin grant access by hand? | Yes (keep, and write it down) or no (remove). |
| C-04 (Medium) | Does System Admin get announcements? | Yes (add a menu entry) or no (remove the route for System Admin). |
| C-08 (Low) | Does System Admin get system status? | Yes (add a menu entry) or no (leave as is). |
| C-17 (Medium) | Can System Admin suspend and restore students? `feature-scope.md` says "Change status" for System Admin. | Yes (show the control, and check the API allows it) or no (correct `feature-scope.md`). |

## 2. Blocker and High fixes (after the decisions)

### C-11: exam cannot be created from the UI (Blocker)
- **Change:** depends on the decision above. Option (a): extend `NewExamDialog` in `web/src/components/staff/catalog/module-exams.tsx:23-80` to ask for description, rules, paper language and duration, and send them in `useCreateExam` (`web/src/lib/api/exam/staff-catalog.ts:308-315`). Option (b): relax `LiveExamService.create` (`api/src/main/java/com/coachingcenter/api/exams/LiveExamService.java:104-129`) for those fields.
- **Also:** the 400 response should name the field. Show the field error under the field, not "Check the details."
- **Test:** create an MCQ and a written exam from the dialog, then publish both. Re-run `findings-C` authoring walk without the API workaround.

### C-01: manual order confirm (Blocker)
- **Change, after decision (a):** remove the Confirm and Reject buttons (`web/src/components/staff/orders/pending-orders.tsx:92-102`) and the hooks (`web/src/lib/api/exam/staff-admin.ts:120-132`). Remove or disable `POST /api/v1/live-exam-orders/{id}/confirm` and `/reject` (`api/src/main/java/com/coachingcenter/api/exams/LiveExamOrderDecisionController.java`). Keep the read-only list. Check the R-8 plan is updated.
- **Change, after decision (b):** amend `decisions.md` in writing, then leave the control in place and record the audit result as accepted.
- **Test:** the Master Admin on `/staff/orders` has no confirm control, and the API returns 403 or 404 on the endpoint.

### C-02: instructor cannot open module or exam (High)
- **Change, after decision (a):** widen the gate on `web/src/app/(staff)/staff/modules/[id]/page.tsx:73` and `web/src/app/(staff)/staff/exams/[examId]/page.tsx:13` to include `INSTRUCTOR`, and render the read-only branches that already exist (`module-details.tsx:95,197`, `module-exams.tsx:129`). Make the module list rows open for instructors (`module-list.tsx:112-124`). The API already limits instructors to assigned modules.
- **Test:** Instructor 1 opens its module and exam (read-only); Instructor 2 gets the not-found page for Instructor 1's module.

### C-10: dashboard exam list fails (High)
- **Change:** the dashboard requests `sort=date`, which the API rejects. Use `sort=liveAt` in `web/src/components/staff/home/admin-home.tsx:191` and `web/src/components/staff/home/instructor-home.tsx:43`, and check the instructor counts after the change.
- **Test:** the dashboard shows the exam list for Master Admin and System Admin, and the instructor queue numbers match the submissions list.

### C-12: module create without descriptions (High)
- **Change, after decision:** mark the description fields required in `web/src/components/staff/catalog/module-details.tsx:214-217` and the create form, and map the API's validation error to the field name in `api/src/main/java/com/coachingcenter/api/exams/LiveExamModuleService.java:58-61`.
- **Test:** an empty description shows a field error, and a filled form saves.

## 3. Medium and Low fixes that need no decision

| Finding | Change | Where | Test |
|---|---|---|---|
| C-03 (Medium) | Forward `exam` in the redirect | `web/src/app/(staff)/staff/submissions/m/[moduleId]/page.tsx:24` | Written count lands on the exam's list, with the exam filter on |
| C-13 (Medium) | Add `on` and `off` under `common.states` in both locales | `web/src/i18n/messages/en/common.ts:89`, `bn/common.ts` | No raw keys on the exam editor in Bangla or English |
| C-14 (Medium) | Show a "not available to this account" message for 403 and 404 on the submission page; keep the generic copy for network and 500 errors | the submission detail page's error state; `web/src/i18n/messages/*/common.ts:91` | Instructor 2 sees the new message; a 500 still shows the generic one |
| C-15 (Medium) | Wrap the modules and payments tables in a horizontal scroll box, or collapse them to cards under 1024 px | `web/src/components/ui/table.tsx`, `web/src/components/staff/ops/payments-table.tsx`, module list | No page overflow at 768 and 1024 px |
| C-16 (Medium) | Set the section-heading and step-label colour to the text token on the light surface | `web/src/components/staff/home/admin-home.tsx` (Shortcuts); the exam editor step labels | 4.5:1 on the automated scan |
| C-09 (Medium) | At 360 px, stop the wordmark and the search icon colliding: move the search and the language and theme pills into the drawer under 480 px, or let the header wrap. Check the right-hand group at 200% text. | `web/src/components/shell/staff-shell.tsx` (header row around line 120), `student-shell.tsx:236-256`, `logo.tsx` | No overlap at 360 px in Bangla and English, no overflow at 200% |
| C-20 (Medium) | Minimum 44 px height on row menu buttons, name links and breadcrumbs at phone width | the table row and link classes, `web/src/components/ui/table.tsx`, `breadcrumbs` | Automated tap-target scan shows no targets under 44 px |
| C-18 (Low) | On a 409 `EXAM_WINDOW_CONFLICT`, show the API's message, which names the other exam | the publish handler in `exam-editor.tsx` | The message names exam A |
| C-19 (Low) | Add a short note for instructors: "The Master Admin publishes this result." | the marking page, `web/src/components/staff/marking/submission-form.tsx` | Note shows for instructors only |
| C-21 (Low) | Give roster rows a remove action on the student page, or link to the module roster tab | `web/src/components/staff/access/student-access-section.tsx` | Removing from the student page clears the row |

## 4. Order of work

1. **Shahriar's decisions** (section 1). Nothing in section 2 for those items starts until then.
2. **Quick wins** (section 3, no decision). These can go in any order. C-03, C-10, C-13, C-18 are small; C-09, C-15, C-16, C-20 are layout and take longer.
3. **Blocker and High fixes** (section 2), after the decisions.
4. **Decision-dependent Medium and Low** (C-04 to C-08, C-17), after the decisions.
5. **Retest.** Reset the QA database (plan 2.2a, with an approval), then re-run only the affected routes, in English and Bangla, light and dark, at 360 and 1440 px. Update the findings file: each finding is marked fixed, or open with the reason.

## 5. What the audit still needs after the fixes

- The Bangla and dark signed-in matrix, which is running now (see the findings file, Status).
- The empty-database state is done. The device checks (real iOS Safari, real Android, tablet, Edge, Samsung Internet, touch) need someone on the devices (plan section 9).

## 6. Decisions received (Shahriar, 2026-10-26)

- **C-01 (order confirm):** only the Master Admin can confirm an order paid. The control stays; the System Admin sees the list read-only. `decisions.md` ("Exam checkout is SSLCommerz only") and `feature-scope.md` ("A staff user cannot mark an exam order paid") must be amended to say this. Until then the documents disagree with the product.
- **C-12 (module descriptions):** required. Make both description fields required in the module form, with a field error.
- **C-02 (instructor):** instructors do not open module or exam pages like a student. They open the question paper inside the evaluation screen (read-only). Recommendation below.
- **C-11 (exam create):** the form asks for the fields listed in section 7.

## 7. Recommendations for the open items

**Instructor evaluation (C-02).**
- Keep the instructor's Modules item, but each module row links to that module's submissions list, not to a page that does not open. (The instructor home already does this with its module cards.)
- The question paper is shown read-only on the evaluation screen, with the marking watermark, and no download button. Reason: the decisions choose deterrence against copies of the paper, and students get no download. The instructor still downloads the student's answer PDF and uploads the marked PDF, as `feature-scope.md` allows.

**Exam creation form (C-11).** Ask on the create dialog, in this order:
1. Type (MCQ or written; cannot change after create).
2. English and Bangla titles.
3. English and Bangla descriptions.
4. English and Bangla rules.
5. Paper language (BN or EN).
6. Duration in minutes (at least 2; the API rejects 1).

Leave these to the editor, because they are publish gates or optional: pass percent (required before publish), start time (empty means "date to be announced"), price (optional), written maximum marks (required before publish for written exams), questions or the question PDF.

Reason: the API already requires items 2 to 6 at create, `feature-scope.md` lists paper language and duration as required on create, and asking them together matches the module decision in C-12. The editor items are checked by the publish checklist, so an admin is not blocked at create.

**Open items, with my recommendation.** Answer yes or no to each.
- **C-04, announcements for System Admin.** Recommend no: announcements go to every student and are not in the decisions for any role other than the Master Admin. Remove the System Admin route.
- **C-05, publish an exam by System Admin.** Recommend no: publish is when a paper goes on sale and becomes visible to students, and the decisions keep publishing with the Master Admin. This also matches the plan's matrix. `feature-scope.md` would need changing.
- **C-06, MCQ answer-release switch.** Recommend remove it. No document gives anyone a rule to release answers, and the decisions say the answers wait until the sitting ends. If Shahriar wants admin control, write the rule into `decisions.md` and make the switch Master-only.
- **C-07, manual grant by System Admin.** Recommend no: a manual grant bypasses the roster and payment rules, which are Master-only in `feature-scope.md`.
- **C-08, system status for System Admin.** Recommend no (keep as is): the page shows settings, and `feature-scope.md` does not list it for System Admin.
- **C-17, suspend and restore a student by System Admin.** Recommend yes: `feature-scope.md` lists "Change status" for System Admin, and suspending a student is not a payment or money change. The API needs checking before the control is shown.
