# Exam creation plan (written 2026-10-27, revised the same day)

**Scope:** module creation (categories, subcategories, module details, the module exam list) and exam creation (New exam dialog, exam editor, MCQ questions, the written question PDF, the publish checklist, publish, copy, delete, archive). Roles: Master Admin (full), System Admin (edit, no publish, no price, no delete or archive), Instructor (must not reach authoring). Out of scope: evaluation and marking, course and book admin, bKash and Nagad, staff access and payments.

**Status:** Phase 3. No product code has been changed in this work. Implementation waits for approval.

**Revision note.** The first version asked for decisions that the documents already make. This version uses the decisions in `docs/exam-site/decisions.md`, `ui/feature-scope.md` (Catalog authoring), `reports/ui-audit/fix-plan-C.md` (section 6, decisions of 2026-10-26) and `plans/ui-delivery/admin-ui-plan.md` (step 9, and the conflicts section). Where a decision is documented, the plan follows it and the code is the gap.

## 1. Context

**What I read.** `docs/exam-site/README.md`, `decisions.md`, `current-api.md`, `ui/feature-scope.md`, `plans/ui-delivery/admin-ui-plan.md`, `reports/ui-audit/findings-C.md` and `fix-plan-C.md`, `plans/evaluation-ui-plan.md`, and `plans/release-workflow-plan.md`.

**Code map (checked against the working tree on 2026-10-27).**
- API: `api/src/main/java/com/coachingcenter/api/exams/`. `LiveExamController` (exam create, get, patch, copy, final-save, publish, delete, questions, question PDF). `LiveExamModuleController` (module create, patch, final-save, publish, archive, delete). `LiveExamCategoryController` (create, patch, delete, archive). Permissions: `EXAM_MANAGE` (create and edit, and module publish), `EXAM_PUBLISH` (exam publish, Master Admin only), `EXAM_DELETE` (delete and archive, Master Admin only), `EXAM_PRICE` (price, Master Admin only). Migrations: V42 and V43 are new in the working tree and uncommitted. Next free version is V44.
- Web: `web/src/components/staff/catalog/`. Also present and new: `subcategory-section.tsx` (untracked).
- Pages: `web/src/app/(staff)/staff/categories/`, `modules/`, `exams/[examId]/`. The categories, module and exam pages are gated to `MASTER_ADMIN` and `SYSTEM_ADMIN` (`RequireStaff`). The module list page has no gate, so an Instructor reaches it.

**Working tree.** The exam-creation files are uncommitted and were edited by another agent during this work: `module-exams.tsx`, `exam-editor.tsx`, `exam-meta-form.tsx`, `module-details.tsx`, `module-list.tsx`, `errors.ts`, `staff-catalog.ts`, `category-form.tsx`, the `staffCatalog` and `common` message files (en and bn), and `staff-marking.ts` and `staff-results.ts`. `subcategory-section.tsx` is untracked. I did not create a git worktree, because a worktree from `HEAD` would not contain these uncommitted changes. Before implementation, either the other agent commits, or I branch a worktree from the working tree state.

**Environment for the screenshots.**
- API on port 8091, web on port 3100. Ports 3000, 5432 and 8080 belong to other agents and were not touched.
- Scratch databases: `coaching_center_excreate` (its staff passwords stopped matching during the run; not reset, not used), `coaching_center_excreate_2` (abandoned after an API restart), `coaching_center_excreate_3` (used for the captures).
- Login is limited to 5 failed attempts per identifier and IP in 15 minutes. Logins were reduced to one per role per run.
- Browser: `playwright-core` 1.64.1 in the scratchpad, channel `msedge`.

**Screenshots.** `docs/exam-site/reports/exam-creation-ui-2026-10-27/`: 512 PNG files and `capture-log.json`.
- Master Admin: 212 of 228 expected screens (16 failed: `row-menu` 4, `delete-confirm` 12).
- System Admin: 212 of 216 expected screens (4 failed: `row-menu` 4).
- Instructor: 72 screens (6 per size, language and theme).
- Sizes 360, 768 and 1440 px. English and Bangla. Light and dark.
- **Not captured:** the student and visitor views. The student pass was started by mistake and crashed; its visitor files were deleted. You asked for it not to run, so the student side is analysed from code only (F-07).
- **Not captured:** the no-description module save (C-12 evidence) and the module search empty state. `capture-extra.mjs` was written but not run.

**Measured in the capture log.** No horizontal overflow on any screen at any width. No raw translation keys (C-13 is fixed in the rendered pages). About 15 tap targets under 44 px per screen at 360 px, mostly the step rail, header icons and row menus.

**Audit items, checked against the code.**
- C-11 (New exam dialog): implemented in the working tree, uncommitted. It asks for the nine fields decided on 2026-10-26.
- C-12 (module descriptions): **open.** `module-details.tsx` `validate()` sends blank descriptions as `null`, and the API returns `400`.
- C-13 (raw keys): fixed in both locales.
- C-14 (refused submission message): the submission page uses the unavailable message (`staffMarking.unavailable`).
- C-18 (clash names the exam): implemented (`clashExamFor`).
- C-02 (instructor): the module list row click opens `/staff/submissions?module=…` (`module-list.tsx:184`), as decided. Typing a module URL still shows "Page not found" (F-10).

## 2. Findings

Severity: **B** blocks a task or breaks a decision; **H** high; **M** medium; **L** low. User type: MA = Master Admin, SA = System Admin, IN = Instructor. **Decided** names where the decision is written.

| ID | Sev | User | Finding | Decided by | Evidence | Code |
|---|---|---|---|---|---|---|
| F-01 | B | MA, SA | The answer-release switch is still on the exam page for every role. | Removed for every role (`admin-ui-plan.md` step 9 and conflicts, citing findings-C) | `master-1440-en-light-exam-published-mcq.png` (bottom, below Delete), `master-360-bn-light-exam-draft-mcq.png`, `system-360-en-light-exam-published-mcq.png` | `app/(staff)/staff/exams/[examId]/page.tsx:15` |
| F-02 | B | SA | A System Admin can publish a module. The module card shows to any author, and the module publish endpoint needs `EXAM_MANAGE`, which the System Admin holds. | "Publish is the Master Admin's only" (`feature-scope.md`, Catalog authoring, 2026-10-26) | `master-1440-en-light-module-details.png` (the publish card); `system` module-details capture shows the same card | `module-details.tsx` ~line 387 (`!readOnly && role.canAuthor`); `LiveExamModuleController.java:67` |
| F-03 | H | MA | Module create with blank descriptions fails with no field message. | Required (C-12, 2026-10-26) | Not captured (see section 1). The API returns `400` | `module-details.tsx` `validate()` ~lines 150-195 |
| F-04 | H | MA, SA | The date and time control is the browser's native `datetime-local` input. It shows `mm/dd/yyyy --:-- --` in English even in Bangla. Its label is the generic date key. The Dhaka zone is only a caption. Typing a time is slow; the AM/PM control differs by browser. The end time appears only after a date is set. | Your request: "needs a good UI change" (not a documented decision) | `master-1440-en-light-exam-published-mcq.png`, `master-360-bn-light-exam-draft-mcq.png`, `system-360-en-light-exam-published-mcq.png` (Schedule section) | `components/ui/date-time-input.tsx` |
| F-05 | H | MA, SA | On a published exam the checklist still lists "Final save the exam" and "Enter the pass percent" as open, while the card says "Everything is in place. You can publish now." The module card has the same contradiction. | Not a decision; this is a bug. The published state must show no open items | `master-1440-en-light-exam-published-mcq.png` (section 5); `master-1440-en-light-module-details.png` ("Before publishing") | `exam-editor.tsx` `examChecklist()`; `module-details.tsx` `checklist` |
| F-06 | H | MA | The path to one publishable MCQ exam is long: save the module, final-save it, open New exam (nine fields), then save five editor sections one by one, add each question (stem, marks, four options, one correct choice, explanation), final-save the exam, publish, and confirm in a summary dialog. The steps are right; the saves are many and the editor does not say what to do next. | Two-step publish is decided ("Final save, then publish", `feature-scope.md`). The number of saves is not | `master-360-en-light-exam-draft-mcq.png` (five sections, a Save on each), `master-1440-en-light-exam-published-mcq.png` | `exam-meta-form.tsx` (`saveRow` per section); `exam-editor.tsx` |
| F-07 | M | MA, SA | **Student side of a published module (your request: the page must exist).** The student module page exists in code and handles unannounced dates (`catalog.dateTbd`) and access states. It has not been seen rendered. Staff have no preview of the module as a student: "View as student" exists only on the exam, and the exam preview shows only the paper. | Your request | Not captured. Code: `components/student/catalog/module-page.tsx`, `app/(site)/mcq/m/[moduleId]/page.tsx` | `module-details.tsx` (no preview) |
| F-08 | M | MA | Written maximum marks sit under "Schedule and marks", not with the question PDF. | Not decided; the rule is that the maximum is stored on the exam (`feature-scope.md`) | Code only: `written-paper.tsx` has no maximum field; `exam-meta-form.tsx` shows it under Schedule | `exam-meta-form.tsx` (`isWritten` maximum field) |
| F-09 | M | MA | No exam archive in the UI or the API. Modules and categories have archive. | `feature-scope.md` says "Delete and archive are Master Admin only" but its rows name categories and modules only. Exams are not decided (see D-1) | `master-1440-en-light-module-exams.png` (row menu); `master-1440-en-light-exam-published-mcq.png` (Delete at bottom) | `module-exams.tsx` `rowMenu`; `LiveExamController.java` (no archive route) |
| F-10 | M | IN | Typing a module address as an Instructor shows "Page not found" rather than the denial copy. The Instructor's module list shows a blank Category on each row, and the rows look like plain cards with no link styling. | Denial copy: C-14 (unavailable message). Row click: C-02 (decided, implemented) | `instructor-360-en-light-module-open-attempt.png`, `instructor-360-en-light-modules.png` | `modules/[id]/page.tsx:73` (`RequireStaff` MA+SA); `module-list.tsx:184`, category display not traced |
| F-11 | M | MA, SA | The New exam dialog is long on a phone: nine fields in one dialog. | Nine fields decided (C-11, 2026-10-26). Layout is not | `master-360-en-light-new-exam-dialog-errors.png` (errors under each field; in a full-page shot the overlay does not dim the page below) | `module-exams.tsx` `NewExamDialog` |
| F-12 | M | MA, SA | Editor step rail and header actions are cramped at 360 px. Header actions wrap onto two lines. About 15 tap targets under 44 px per screen. | Not decided | `master-360-en-light-exam-draft-mcq.png`, `master-360-bn-light-exam-draft-mcq.png`; capture-log `smallTargets` | `exam-editor.tsx` header; `components/ui/section-nav.tsx` |
| F-13 | M | MA | Row menus are reached only by the `…` trigger. The capture selector failed on 4 screens per role, so Delete from the row menu was not captured. | Not decided | `capture-log.json` (`row-menu`, `delete-confirm` errors) | `module-exams.tsx` `RowMenu` |
| F-14 | M | ALL | Checklist wording differs across the exam card, the module card and the publish summary dialog. | Not decided | Screens above | `exam-editor.tsx`, `module-details.tsx`, `staffCatalog` messages |
| F-15 | L | ALL | The exam preview shows the paper, but no date, price or module context. | Not decided | `master-1440-en-light-exam-published-mcq.png` (preview button) | `exam-editor.tsx` `StudentPreview` |
| F-16 | L | MA | Blank price means "cannot be bought" is written as a note, not as a state of the field. | Not decided | `master-360-en-light-exam-draft-mcq.png` (Price section) | `exam-meta-form.tsx` |
| F-17 | L | MA | Module setup steps (Details, Price, Add exams, Publish, Teacher, Direct enrolment) sit beside the publish card as a second checklist. | Not decided | `master-1440-en-light-module-details.png` | `module-details.tsx` |

**Evidence gaps.**
- The student module, exam and routine pages (F-07). Needs the student pass, which you asked not to run.
- The module no-description save and the module search empty state (C-12). Needs `capture-extra.mjs`.
- The row menu and delete confirm (F-13). The selectors need fixing before a re-run.

## 3. Target design

### 3.1 Principles
- One path. Create, fill and publish with the checklist saying what is missing and what the next action is.
- Publish stays two-step (Final save, then Publish), as decided. Each section still saves on its own button, so the editor says which section is unsaved.
- A published record shows no open items. An edit returns it to draft and the checklist says so.
- Dates and times read the way the client writes them: Dhaka time, Bangla numerals on Bangla pages, and a visible end time.

### 3.2 Date and time (F-04)
Replace the native input with a `DhakaDateTimePicker` in `components/ui/`:
- A **date** field: text `dd/mm/yyyy` with a calendar button; accepts Bangla digits on Bangla pages; an invalid value is marked on blur.
- A **time** field: an hour select (1 to 12), a minute select (00, 05, 10 … 55), and an AM/PM segmented control. Labels in the current locale.
- Under it, one line: "Starts Sat 20 Nov 2026, 10:00 AM. Ends 11:00 AM (60 minutes)." Shown as soon as a date and time are set.
- Keep the **Date to be announced** switch.
- The stored value is unchanged (ISO UTC from Dhaka parts), so the API contract does not change. `dhakaParts` and `fromDhakaParts` are reused.
- Tests: Vitest for the round trip (Dhaka to UTC and back, across midnight, at the five-minute gap), and for the end-time line.

### 3.3 Publish (F-05, F-14)
- One `PublishChecklist` component for exams and modules. States: "Missing before publish" (open items linked to their field), "Ready to publish" (all done, Publish enabled), "Published" (no open items; a note if there are unsaved edits, which return the record to draft on save).
- The publish summary dialog stays and lists the same items.
- The module setup steps (F-17) become the same checklist, not a second list.

### 3.4 Written maximum (F-08)
Move the maximum field into the written paper section next to the PDF. Schedule keeps duration, pass percent and the date.

### 3.5 Role gates (F-01, F-02, F-10)
- Remove `AnswerReleaseSwitch` from `exams/[examId]/page.tsx`. The API endpoint stays; no screen calls it.
- Module publish: the publish card and the publish call are for the Master Admin only. A System Admin sees the card read-only with "Only the Master Admin publishes."
- Instructor: a typed module or exam address shows the denial copy ("This is not available to this account."), not "Page not found". The module list row shows the category path and is a clear link.

### 3.6 Student view of a module (F-07)
Add "Preview as student" on module details. It opens the existing student module page in a dialog, read-only, with a switch for "no access" and "has access". No grant is written. This is a new component; no decision is needed because you asked for the page.

### 3.7 Dialog, tap targets, row menu (F-11, F-12, F-13)
- New exam dialog: the nine decided fields, grouped under three headings (Type and titles; Descriptions and rules; Paper language and duration). The fields do not change.
- Tap targets: 44 px minimum for the step rail, header icons, row menu triggers and checklist links.
- Row menu: add Delete (Master Admin only) and, if D-1 says yes, Archive.

### 3.7a Student view inside the admin panel (review 2, 2026-10-27)

**Constraint found in code.** The student module page (`components/student/catalog/module-page.tsx`, `ModulePage`) reads only the public APIs: `usePublicModule` and `usePublicModuleExams`. Those return published modules and exams only (`LiveExamPublicService`). So a draft module or exam cannot be shown to a student by the existing page, and an admin preview of a draft cannot reuse it without a second data source.

**Options.**
- **A. Link after publish (recommended for now).** On a published module, "View as student" opens the public page for each door the module has: MCQ (`/mcq/m/{id}`) and written (`/written/m/{id}`). Before publish, the section shows "Students will see this module after it is published" with the checklist beside it. No new data path, no risk of showing a draft as live. Small change.
- **B. Draft preview from staff data.** Split `ModulePage` into a presentational part and a data part, and feed the presentational part from the staff module and exam reads (`useModule`, `useModuleExams`). It shows drafts exactly as students would see them, with no grant or price written. Larger change, and it needs the presentational part to ignore the signed-in student's access. Needed only if you want to check drafts before publish.
- Recommendation: A now, with a note on each door. B only if you want the preview before publish.

**Where it goes.** Module details, in the setup card (next to the checklist), as one row: "Student view: MCQ · Written" with the two links. The Exams tab row menu gets the same link per exam (`/exam/{id}`) where the exam is published.

**Other UI points from this review (not yet built).**
- The setup card and the publish card show the same checklist. Keep one (F-17).
- "Teacher: No one yet" is a setup step, but it is not required to publish. Mark it as optional so it does not read as a gap.
- The Instructor's module rows have no link styling, so they look static, although the row click works. Give them a visible link or an arrow (F-10).
- The Category value is blank on the Instructor's list. The API returns `categoryPath` for staff rows (`current-api.md`), so the cause is in the web row. Not traced yet (F-10).
- The Exams tab does not say how many exams are published, drafted, or without a date. The setup card has the counts; the tab should use the same numbers in its header.

### 3.8 New or changed pieces
| Area | Needed | What |
|---|---|---|
| Date and time | New component | `components/ui/dhaka-date-time-picker.tsx` replaces `date-time-input.tsx` in the exam and module forms |
| Publish checklist | New component | `components/staff/catalog/publish-checklist.tsx` replaces `exam-checklist.tsx` and the module checklist |
| Module student preview | New component | `components/staff/catalog/module-student-preview.tsx` |
| Editor header and steps | Layout | `exam-editor.tsx` header, `section-nav.tsx` 44 px targets |
| Written maximum | Layout | `written-paper.tsx` gets the field; `exam-meta-form.tsx` loses it |
| New exam dialog | Layout | `module-exams.tsx` `NewExamDialog` grouping |
| Role gates | Rule (module publish) and copy | `module-details.tsx`, `module-list.tsx`, `modules/[id]/page.tsx` |
| Answer-release switch | Removal | `exams/[examId]/page.tsx` |
| Copy | Style and copy | `staffCatalog` en and bn; `common` for the denial line |

### 3.10 After an exam is created: next step (review 3, 2026-10-27)

**What happens today (from code and the screenshot `7.png`).**
- The New exam dialog creates the exam and opens its editor at `/staff/exams/{id}`. Nothing says what comes next.
- The editor saves each section on its own button: Details, Schedule and marks (including the written maximum), and Price. A value typed in a section and not saved is lost when the page is left. The unsaved-changes guard covers the Details, Schedule and Price forms (`exam-meta-form.tsx`), so a link or back step should ask first. The exam list's back step still needs checking.
- Questions and the question PDF are saved the moment they are added or uploaded (their own API calls). The checklist in `7.png` shows the PDF as stored, so it cannot be lost by leaving the page. If the PDF looks gone after going back, that needs a reproduction, not a guess.
- The instructor is set on the module (`/staff/modules/{id}?tab=instructor`), and the editor reaches it only through a warning line ("Assign a teacher"). An exam author has no direct next step.

**Plan (this change).**
1. After a successful create, go to the editor with `?created=1`. The editor shows a "Exam saved. What next?" card with two choices: **Create another exam in this module** (opens the New exam dialog on the module's exam list, `?tab=exams&new=1`) and **Assign an instructor** (`?tab=instructor`). For a System Admin, the second choice says only the Master Admin assigns the instructor.
2. The same two choices stay at the foot of the editor, so they are there after the card is dismissed.
3. A **Save all changes** button in the editor header saves every section that has unsaved values, so a single action keeps the schedule, marks and details together. It is shown only for drafts. Published exams keep their per-section saves, because a save returns them to draft.
4. The unsaved-changes guard stays as it is. Nothing new is saved automatically.

## 4. Decisions

**Already decided (no question; the plan follows them):**
- Only the Master Admin publishes (exams and modules). `feature-scope.md`, Catalog authoring. Source of F-02.
- The answer-release switch is removed for every role. `admin-ui-plan.md` step 9 and conflicts. Source of F-01.
- Publish is two-step: "Final save, then publish" for exams, "DRAFT, then final save, then PUBLISHED" for modules. `feature-scope.md`. This is kept, not removed.
- Nine fields in the New exam dialog (C-11, 2026-10-26). Kept.
- Module descriptions required (C-12, 2026-10-26). Source of F-03.
- Instructors do not open module or exam pages like a student; an instructor's module row opens that module's submissions (C-02, 2026-10-26; `module-list.tsx:184`). Denial copy follows C-14.
- Delete and archive are Master Admin only (categories and modules; `feature-scope.md`).
- Each edit of a published record returns it to draft (`decisions.md`).

**Decided by Shahriar (2026-10-27):**
- **D-1. Exam archive.** An archived exam stays off the student website. Master Admin only. Staff still see it, with an "Archived" status, and its results stay readable (the same rule the category archive uses).
- Focus for this step is the UI. The status set currently has only `DRAFT` and `PUBLISHED` (`V25__live_exams.sql`), and there is no exam archive route in `LiveExamController`. The UI work below needs that route. Check with Shahriar before writing it, because the exam status and migration are server work.

**Your direction (no question; the plan builds it):**
- The date and time control needs a good UI change (F-04). Section 3.2 is the proposal. Say if you want a different control.
- A student view of a published module must exist (F-07). Section 3.6 is the proposal.
- The authoring flow should be easier to follow (F-06). The plan reduces repeated saves and makes the checklist the guide. Publish stays two-step, as decided.

## 5. Order of work

Each step ends with tests, a screenshot pass for the screens it changes, and an update to section 8.

1. **Role gates and removals** (F-01, F-02, F-10). Remove the answer-release switch; module publish for Master Admin only; the denial copy for typed addresses. Checks: Vitest for the publish gate; screenshots for SA and IN.
2. **Module descriptions required** (F-03). Field errors on both descriptions; map the API's `VALIDATION_FAILED` field to them. Checks: Vitest; `capture-extra.mjs`.
3. **Date and time picker** (F-04). New component in the exam schedule. Checks: Vitest for the Dhaka round trip; screenshots at 360 px, English and Bangla.
4. **Checklist states and copy** (F-05, F-14, F-17). One `PublishChecklist`; a published record shows no open items. Checks: Vitest for the states; the editor's screenshots in each state; `LiveExamIT` still passes.
5. **Written maximum and editor layout** (F-08, F-12, F-16). Move the field; header, step rail and tap targets. Checks: screenshots at 360 px; no editor control under 44 px.
6. **Row menu and dialog** (F-11, F-13). Row-menu Delete; the New exam dialog grouping. Checks: the capture selectors fixed; `row-menu` and `delete-confirm` screenshots.
7. **Module student preview** (F-07). Read-only preview. Checks: Vitest; screenshots.
8. **Full verification.** Full screenshot pass for all roles, then `tsc`, `vitest`, `eslint`, and the API classes below.

## 6. Test plan

**Web (Vitest in `web/tests/ws9-aut/` and `web/tests/ws5/`, plus new files named when each step starts):**
- `dhaka-date-time-picker`: round trip, midnight, five-minute gap, end-time line.
- `publish-checklist`: missing, ready, published, and the draft-after-edit note.
- `module-details` validation: both descriptions required, field errors.
- `module-exams` dialog: field groups and errors.
- Module list row: opens the submissions page for an Instructor.
- Answer-release switch absent from the exam page for every role.

**Commands:** `cd web && npx tsc --noEmit -p .`; `cd web && npx vitest run` (629 tests in 105 files on 2026-10-27, keep green); `cd web && npx eslint <files touched>`.

**API (`*IT`, one class at a time; `TEST_DB_URL=jdbc:postgresql://localhost:5432/coaching_center_test`; credentials from `api/.env`, never printed):**
- `LiveExamIT`: publish as System Admin refused (`403`); publish as Master Admin works; publish still needs the final save.
- `LiveExamModuleIT` (new name): module publish refused for System Admin; module publish by Master Admin works; module create with blank descriptions returns `400` naming the field.
- `AuthorizationIT`: System Admin and Instructor permissions on module and exam endpoints.
- `AuditLogIT`: only if an endpoint changes.

Command: `./mvnw -B -DargLine="-Xmx1g" -Dtest=<Class> -Dsurefire.failIfNoSpecifiedTests=false test`, one class at a time.

**Known trap:** tests that publish an exam as System Admin fail after decision 2026-10-26. Use a Master Admin token.

## 7. Bangla strings to review

New or changed Bangla copy that needs a native review before it ships. Current copy is in `web/src/i18n/messages/bn/staffCatalog.ts` and `common.ts`.
- Date and time labels: "তারিখ", "ঘণ্টা", "মিনিট", "AM", "PM", "শুরু", "শেষ", and the start and end line.
- Checklist: "প্রকাশের আগে বাকি", "প্রকাশের জন্য প্রস্তুত", "প্রকাশিত", and the draft-after-edit note.
- Denial: "এই অ্যাকাউন্টের জন্য এটি উপলব্ধ নয়।" (the C-14 copy, reused).
- Student preview: "শিক্ষার্থী যেভাবে দেখবে", "অ্যাক্সেস নেই", "অ্যাক্সেস আছে".
- Row menu: "মুছে ফেলুন" and, if D-1 is yes, "আর্কাইভ করুন".
- Module description required: the field message.

## 8. Progress

| Step | Status | Notes |
|---|---|---|
| 1 Role gates and removals | not started | Waiting for approval |
| 2 Module descriptions required | not started | C-12 |
| 3 Date and time picker | not started | |
| 4 Checklist states and copy | not started | |
| 5 Written maximum and layout | not started | |
| Exam archive (D-1, decided 2026-10-27) | done in code, tests green; screenshot not yet taken | API: `V44`, `POST /live-exams/{id}/archive`, `409 EXAM_ARCHIVED` on edit, publish and final save. UI: Archive in the editor and the row menu (Master Admin), archived notice, read-only editor. Tests: `LiveExamArchiveIT` (2 pass), `exam-archive.test.tsx` (3), full Vitest 639 pass. Open: question routes, PDF upload and copy are not yet guarded on archived exams. |
| 6 Row menu and dialog | partly done | Archive in the row menu is done. The New exam dialog grouping and the capture selectors are not started |
| 7 Module student preview | not started | |
| 8 Full verification | not started | |
