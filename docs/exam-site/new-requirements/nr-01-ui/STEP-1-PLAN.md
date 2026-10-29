# NR-01 UI, Step 1: exam and package creation, Exams page, sidebar, MCQ authoring

Written 29 October 2026 on `ui/nr01-ui` (worktree `F:\cc-wt\nr01-ui`). Status: **approved with the confirmations in section 2; agents started.**
Audience: Shahriar (review), then the two implementation agents (Claude Haiku 5.5, high effort) and the coordinator (the main session).

## 1. Goal

Bring the staff exam pages, package pages, sidebar and the MCQ paper up to the current API (NR-01, Phase 4), keep the existing creation flow, and add what is missing:

- A new **Exams** page in the sidebar, where exams are created, searched, filtered and opened. Exams are grouped by package.
- The staff **exam page** and **package page** show every new API field, without the old client-side lock.
- **Description and rules** move from the create dialog to the exam page and become rich text.
- **Date and time** use a calendar with scrolling time columns.
- A new **sidebar** in the old admin's look and layout, driven by our own roles and routes.
- **MCQ authoring** supports multi-answer questions, and the **student MCQ sitting and review** change just enough to answer and review them.
- One small **API slice** (paper language "Both", optional paper language, blank description and rules on create, HTML sanitizing).

Out of scope for Step 1 (later steps): the written flow screens (Evaluated tab, retake grant, grant form fix, paper versions UI), watermark redesign, dashboards, student attempts list and retake button, student access for written exams (open item 11.5), and the answers-released switch removal on the results screens.

## 2. Decisions this plan rests on

Decided by Shahriar (29 October 2026):

| # | Decision |
|---|---|
| D1 | Keep the existing create-then-edit flow: dialog, then a dedicated exam page, then Save, Final save, Publish. |
| D2 | The create dialog holds: type, English and Bangla titles, paper language (optional), duration. On the Exams page it also asks where the exam goes (a package, or a category only). Everything else moves to the exam page. |
| D3 | Description and rules are rich text on the exam page. Dates use a calendar with scrolling hour and minute columns. |
| D4 | The Exams page groups exams by package, with a layout that follows common data-table practice (section 7). |
| D5 | "Both" paper language is added. Paper language is optional: when not chosen, nothing is sent and nothing is shown (no "undefined", no blank label). |
| D6 | The sidebar follows the theme (light and dark), with tuned colours. |
| D7 | MCQ authoring is in scope, including the student pages it affects. |

Taken from the recommendations Shahriar accepted:

| # | Decision |
|---|---|
| R1 | The API accepts blank description and rules on create. **Publish still requires them.** |
| R2 | Rich text toolbar: headings, bold, italic, underline, bullet and numbered lists, alignment, link, clear formatting. **No images.** |
| R3 | The API sanitizes HTML on save. Students see the sanitized result. Old plain-text rows keep rendering as they do now. |
| R4 | Editor: Tiptap (not Quill). |
| R5 | Date and time: calendar, scroll columns for hour, minute and AM or PM, plus a typed field beside it. No seconds. |
| R6 | Sidebar structure: Overview (Dashboard); Catalog (Categories, Packages with All and Create, Exams with All and Create); Exams and marking (Results & marking, Submissions, Announcements); People, Money, Admin unchanged. |
| R7 | Sidebar collapse is a click toggle, remembered per browser. No hover-expand. |

Confirmed by Shahriar on 29 October 2026 (after the first review):

- C1. The **package description is rich text** too, like the exam's.
- C2. When an exam has **no paper language** (not chosen at creation), the student never sees a language label, badge or "undefined" anywhere: it is omitted completely. The answer letters then follow the viewer's interface language (ক খ গ ঘ in Bangla, A B C D in English). The same fallback applies to "Both".
- C3. The student attempts list and the retake button stay out of Step 1. The student review changes only as listed in 7.5.
- C4. Agents commit on their own branches when a step's checks pass. Nothing is pushed. Nothing is merged into `ui/nr01-ui`, `demo/client-review` or `main` without Shahriar's go.

## 3. What changed in the API that the UI must follow

Source of record: `current-api.md` ("Added 2026-10-28 NR-01", "Added 2026-10-29 Phase 4"), `nr-01/contracts.md`, `nr-01/web-impact/*.md`. The points that drive this step:

- Exam: `categoryId`, `packages[]`, `mode` (LIVE or PRACTICE), `liveAt`, `endAt`, `durationMinutes`, `negativeMarks`, `answersChangeable`, `previewEnabled`, `popular`, `accessDays`, `pricing`, `instructor`, `pendingChanges`. Price fields (`price`, `free`, `discountPercent`, `discountEndsAt`, `accessDays`) are Master Admin only; a System Admin gets 403.
- No edit lock. A published exam or package edit is **staged**; students keep the published version until the Master Admin publishes. Flow: `GET /live-exams/{id}/pending-changes` (change list with `revisionId`), `DELETE` to discard, `POST /live-exams/{id}/publish` with `{revisionId}`. Same pattern for `live-exam-modules/{id}`. Sidebar badge: `GET /live-exam-pending-changes`.
- Create: `POST /live-exams` (standalone, with `categoryId`) and `POST /live-exam-modules/{id}/exams` (inside a package). Staff list: `GET /live-exams` with filters `packageId`, `categoryId`, `standalone`, `type`, `mode`, `status`, `phase`, `q`, `pending`, `popular`, `sort`.
- Package membership: `POST` and `DELETE /live-exam-modules/{id}/exams/{examId}`, `PATCH .../exams/order`. Package: `discountPercent`, `discountEndsAt`, `popular` (`PUT .../popular`, Master Admin), default instructor with `applyToExams`.
- Per-exam instructor: `POST` and `DELETE /live-exams/{id}/instructor` (Master Admin).
- MCQ questions: `multipleAnswer`; one correct option when off, at least one when on; 2 or more options. `QUESTION_STRUCTURE_LOCKED` when options or questions are added or removed after the first student started; `ANSWER_LOCKED`.
- New error codes to map: `EXAM_WINDOW_INVALID`, `QUESTION_STRUCTURE_LOCKED`, `ANSWER_LOCKED`, `PENDING_CHANGES_STALE`, `NO_PENDING_CHANGES`, `EXAM_ALREADY_IN_PACKAGE`.
- Student sitting (compatibility rules in `contracts.md` section 7 stay until the web stops using them): `PUT /live-exams/{id}/answers` accepts `{questionId, optionIds:[...]}`; `PUT .../answers/{questionId}` with `{optionIds, confirm?}`; an answer cannot change after submit; the MCQ result, key and explanations come at submit with `position` `{type, rank, of, belowPass}`.

## 4. Findings in the current code that this step fixes

1. `exam-editor.tsx` locks every section once `liveAt <= now`. The API has no such lock. Lock banners come out.
2. `publishable` is false for any published exam, so staged edits can never be published. Replaced by the change-list flow.
3. `StaffExam`, `ExamInput`, `StaffModule`, `ModuleInput` miss the fields in section 3. Nothing calls pending-changes, instructor or package-membership routes.
4. The schedule section computes the end from the duration; the API has a separate `endAt` that must be at least the duration.
5. The create dialog requires six text fields the API also requires; moving description and rules off the dialog needs the API change R1.
6. The API has no HTML sanitizer, and the web renders text as plain text only. Rich text needs both (R3).
7. `paper_language` is `VARCHAR(2)` with check `BN`/`EN` (V30). "Both" does not fit; the column must be widened and allowed `NULL`. The next free migration is **V55** (V54 exists).
8. The MCQ question editor sends one correct option, never `multipleAnswer`; paste rejects several `*`; the list shows one correct mark.
9. The student MCQ screens hold one option per question (`selection: Record<questionId, optionId>`), save with the old single-option body, and tell the student they can change an answer after submit, which the API now refuses.
10. Only `exam-meta-form.tsx` uses `DateTimeInput`; it is a typed field plus a clock face.
11. The package exam list opens an exam only by clicking its title. No visible Open button.

## 5. Agents, worktrees and ownership

Two background agents, Claude Haiku 5.5, high effort. Each works in its own worktree and branch, cut from `ui/nr01-ui` after the coordinator has done step 0.

| Agent | Worktree | Branch | Owns |
|---|---|---|---|
| **A: authoring** | `F:\cc-wt\s1-a` | `ui/s1-a` | The API slice; staff exam page; staff package page; the shared "New exam" dialog; MCQ authoring; `lib/api/exam/staff-catalog.ts`; `components/staff/catalog/*`; `i18n/messages/{en,bn}/staffCatalog.ts`; `components/staff/catalog/errors.ts` |
| **B: shell and student** | `F:\cc-wt\s1-b` | `ui/s1-b` | Date and time input; rich text editor and viewer; sidebar and header; Exams page; student MCQ sitting and review; student description rendering; `components/ui/*`; `components/shell/*`; `i18n/messages/{en,bn}/common.ts`; new `staffExams.ts`, `sitting.ts` message files; `lib/api/exam/sitting.ts`, new `lib/api/exam/staff-exams.ts` |

Rules for both agents:

- Touch only owned files. A file the other agent owns is changed only by a request in the final report.
- A new i18n key goes into both `en` and `bn` with the same key. No hard-coded text. New Bangla is a draft.
- Staff wording: "module" becomes "package", "Archive" becomes "Withdraw", "Participation" becomes "Results & marking". URLs keep `/staff/modules`.
- Colours are tokens only. Touch targets at least 44 px (`min-h-11`). Every list has loading, empty, error and filtered-empty states (`Skeleton`, `EmptyState`, `ErrorState`). Filters live in the URL (`useListParams`).
- Read `web/AGENTS.md` and the Next.js guide in `web/node_modules/next/dist/docs/` before route or data code.
- No progress, review or notes files. The final report goes in the chat reply. The coordinator writes the records.
- Do not edit env files. Do not start servers. Do not push. Do not kill processes that are not yours.

### Dependency order and runs

```
0  coordinator: worktrees, node_modules, runner value S1
Run 1 (both agents start together)
   A: A1 API slice, then A2 web foundation + dialog
   B: B1 date/time, B2 rich text, B3 sidebar, B5 student MCQ, B6 student text
CP1  coordinator: reviews A2 and B2 diffs, merges each branch into the other
Run 2 (both agents continue, same context, by message)
   A: A3 exam page, A4 package page, A5 MCQ authoring
   B: B4 Exams page
8  coordinator: merge, full checks, one build, report
```

- Each agent stops at the end of its run and reports; it does not wait. An agent is continued with a message for run 2 so it keeps its context and does not re-read the plan.
- **CP1:** A3 and A4 need B1 and B2 (picker, editor, viewer). B4 needs A2 (the New exam dialog and the types). Nothing else crosses between the agents.
- A1 runs first on A's branch, because A2 to A5 depend on the API behaviour. B1 to B3, B5 and B6 do not depend on A.

## 6. Test and token budget (applies to every step)

The owner's weekly limit is the constraint. These rules are part of the plan.

1. **Lint only what changed:** `npx eslint <changed files>`. Show only errors; ignore the two known warnings.
2. **Tests only for the step's own files:** `npx vitest run <the new or changed test files> --reporter=dot`, output piped through `tail -n 15`. Never the whole suite.
3. **API:** only the named IT classes, through `docs/exam-site/new-requirements/nr-01/tools/nr01-mvn.ps1` with value `S1` (its own database, `cc_test_nr01_s1`). Never bare `mvn`. Never the full API suite in an agent.
4. **One run per gate.** A gate is the end of a major step (the list in section 8). If it fails, fix, then re-run only the failing file.
5. **No polling.** A long run is started once and waited for once. No sleep loops, no re-reading logs.
6. **No full builds and no heavy-lock jobs in agents.** The coordinator runs the full web suite, the build and the API suite once, at the end, under `.heavy.lock`.
7. **Report in numbers.** Pass or fail counts and the names of failing tests with the first error line. No pasted logs, no progress files, no screenshots.
8. **Read narrowly.** Grep for the symbol, then read 50 to 100 lines. Do not read whole files over 500 lines. Do not re-read a file just edited.
9. **Final report:** at most 15 lines: steps done, commits, checks run with results, files changed (count and folders), things not verified, blockers.
10. **Unit tests are written only for logic that can break:** the publish gate, change-list handling, error mapping, paper-language handling, date and time parsing, nav filtering by role, group-by-package, multi-answer selection, paste parser. Presentational layout gets lint and the coordinator's one overflow report.

## 7. Design decisions per area

### 7.1 Principles (from common admin and ed-tech practice)

Web search found only thin guidance (vendor help pages and component docs, nothing authoritative). These principles are used as judgement, and are consistent with those sources: [row grouping with count in the header](https://apexcharts.com/apexgrid/docs/enterprise-grouping/), [collapse by default for large data](https://help.qrvey.com/docs/composer/Creating%20Charts/Chart%20Types/chart-type-expandable-table), [visible expand state with `aria-expanded`](https://accessible-data-interfaces.pages.dev/accessible-data-tables-grid-systems/expandable-rows-nested-data/).

- Show system status: status pill, pending-changes marker and save state are always visible.
- Make the next action obvious: one primary action per state; an explicit **Open** button, never a hidden click target.
- Progressive disclosure: fields appear only when they apply (no dates for a practice exam; negative mark field only when negative marking is on).
- Prevent errors: the window-versus-duration rule is checked in the form before saving; Master-Admin-only fields are shown disabled with a reason for a System Admin, not hidden.
- Recognition over recall: exam rows show type, mode, window, price, status, packages.
- Errors are shown under the field they belong to, using the API `fieldErrors`.
- Consistency: one shared create dialog, one shared status pill, one shared change-list dialog for exams and packages.

### 7.2 Exam page (`/staff/exams/[examId]`)

Layout stays: breadcrumbs, status pill, sections, "Student view", footer actions. Sections, in the order the work is done:

1. **Details:** English and Bangla titles, category (read-only for a package exam, editable for a standalone exam), paper language (optional, with "Both"), preview on or off, popular (Master Admin), instructor (Master Admin).
2. **Description and rules:** four rich text fields (English and Bangla for each), each with a plain preview.
3. **Schedule and marks:** mode (LIVE or PRACTICE); for LIVE, the window start and end (calendar pickers) and a clear "undated" option; duration; pass percent; maximum marks (written); for MCQ, negative marking (switch, suggests 0.25) and "answers can be changed" (default on). The end-before-duration rule is shown inline.
4. **Questions / Question paper:** MCQ list with multi-answer support (section 7.5); written paper upload as today.
5. **Price and access** (Master Admin edits): price, free, discount percent and end date, access days or lifetime. A System Admin sees the values read-only with a reason.
6. **Check and publish:** the checklist (now includes description and rules, and a valid window), then the next-step choices as today.

Header actions: one primary action that follows the state:

| State | Primary | Others |
|---|---|---|
| Draft, unsaved changes | Save all changes | Student view |
| Draft, saved | Publish (Master Admin) | Student view |
| Published, no staged changes | none | Withdraw, Student view |
| Published, staged changes | **Publish changes** (Master Admin; opens the change list) | Discard changes, Student view |

- A **"Changes waiting to be published"** banner shows on every section of a published exam with `pendingChanges`. Text for a System Admin: "waiting for the Master Admin".
- The **change-list dialog** shows every field before and after (both languages), the question changes, the paper version, and the impact lines (papers to re-score, students to notify). Rich text fields show as plain text in this dialog. `PENDING_CHANGES_STALE` closes it with "changed since you read it, read again".
- The client-side lock and its banners are removed. The only disabling left: archived exam, and `QUESTION_STRUCTURE_LOCKED` handling inside the question editor.
- Unsaved-changes guard stays.
- The answers-released switch is not shown on the exam page.

### 7.3 Package page (`/staff/modules/[id]`)

Tabs stay (Details, Exams, Instructor, Students). Changes:

- **Details:** titles, category, rich description, price, discount percent and end date, popular (Master Admin), cover (as now). Staged-changes banner, change-list dialog, discard, and Publish changes, with the same shared components as the exam page.
- **Exams tab (the main UX fix):**
  - A visible **Open** button on every row, plus the title as a link, plus the row menu.
  - Columns: title, type, mode, window, duration, price (list price struck through when discounted), status, a pending dot, "also in N packages".
  - **Add existing exam** (search dialog) and **Create exam** (the shared dialog, preset to this package).
  - Remove (with confirm) and reorder (move up and down; keyboard friendly).
  - On a published package, add, remove and reorder are staged, with the same banner.
- **Instructor tab:** package default instructor with an "apply to existing exams" choice (`applyToExams`).
- The setup checklist and the "Student view" preview stay.

### 7.4 Exams page (`/staff/exams`)

- Sidebar: **Catalog → Exams** (All exams, Create). Visible to Master Admin and System Admin; an Instructor sees only their own exams (the API already limits the list).
- Header: title, **New exam** button, search box (`q`).
- Filters (URL): package, category, type, mode, status, phase, popular, pending changes, and a "View" switch.
- **View: Grouped by package (default) | Grouped by category | Flat list.**
- Group header (a subtle tinted row, with a chevron and `aria-expanded`): package name, status pill, exam count, a pending dot, and an **Open package** link. A final group, **Not in a package**, holds exams with no package and shows their category.
- An exam in several packages appears under each, with an "Also in N packages" note.
- Groups start collapsed when there are more than 8; otherwise expanded. The open or closed state is remembered per browser.
- Exam row: title, type, mode, window, price, status, pending dot, an **Open** button, and a row menu (Results, Leaderboard, Copy, Withdraw or Delete as the role allows).
- Paging for long groups ("Load more" per group). A phone shows each row as a stacked card under its group header.
- The sidebar shows a **pending-changes count** badge on Exams (Master Admin and System Admin), from `GET /live-exam-pending-changes`.

### 7.5 MCQ authoring and the student pages it touches

Staff:

- Question dialog: a **"Select all that apply"** switch. Off: radio buttons, one correct option. On: checkboxes, at least one correct option. Switching from multi to single with several ticked asks which one to keep.
- The list shows every correct option and a "multiple answers" tag.
- Paste: several `*` allowed and marks the question multi-answer; no `*` still errors.
- Reorder: stays, using `position`. When the exam has started, add and remove (questions and options) are disabled with an explanation; wording, key, marks and explanation stay editable.
- Exam-level: negative marking and "answers can be changed" (in the schedule section).

Student (old site used a checkbox for multi-answer and a radio for single; we follow NR-01 decision C14: a radio circle on the left of each option, not a whole-row click):

- **Sitting:** the selection becomes a set per question; save with `optionIds`; a multi-answer question shows "select all that apply" and, when the exam does not allow answer changes, a **Confirm** button that sends `confirm: true`; a single-answer question on such an exam is final at the first tap. `ANSWER_LOCKED` shows a clear message. The "you can still change your answer after submitting" text is removed because the API now refuses it.
- **Review (result page):** all correct options are marked; the key and explanations show as soon as the paper is submitted; the **position** line is shown (current, board, or "your score would have ranked #N of M live participants", or "Below pass mark, not ranked").
- **Out of this step:** the attempts list and the retake button (A3).

### 7.6 Rich text

- Editor: Tiptap with the R2 toolbar; Bangla input works in the contenteditable; paste is cleaned to the allowed set.
- Stored as HTML in the existing `TEXT` columns. The API sanitizes on every save of an exam or package text field; allowed: `p br h2 h3 strong em u ul ol li a` plus text alignment. Links: `http`, `https`, `mailto` only, with `rel="noopener nofollow"`.
- Viewer: one shared component. It renders the stored value as sanitized HTML when it has tags, and as before (plain text with line breaks) when it has none. Cards, rows, search results and notices show a **plain-text excerpt**, never HTML.
- Everything that already reads these fields as plain text (student exam and package pages, preview, quick search, emails, notices, the staff change list) is checked by grep and fixed where HTML would leak through.

### 7.7 Date and time input

- The existing `DateTimeInput` (same props, same Dhaka time handling through `dhakaParts` and `fromDhakaParts`) is reimplemented: a popover with a month calendar, and next to it three scroll columns (hour 1 to 12, minute 00 to 59, AM or PM), plus the typed field kept for keyboard users. Bangla digits through `useFormat`. Clear and "Now" shortcuts. The old clock face is removed when nothing else uses it.
- Because the props stay the same, nothing in the exam form changes for the swap.

### 7.8 Sidebar and header

Match the old admin's style and layout, with our own roles and routes:

- Fixed left rail, **240 px**, collapsing to an **80 px** icon rail. Toggle button in the header, remembered per browser. On a phone it is a drawer behind the menu button, as now.
- Brand block at the top (logo and name), uppercase small **section labels** (Overview, Catalog, Exams and marking, People, Money, Admin), an **icon on every item**, **accordion sub-items** (a chevron, one open at a time, the active route's group open), active item shown bold with a highlighted background.
- Header bar over the content with the menu toggle, quick search, notifications entry if it already exists, language and theme pills, and a profile menu (name, role chip, log out). Gradient is not copied; the header uses theme tokens.
- Colours: new sidebar tokens in `globals.css` that follow the theme (light and dark), tuned for contrast (WCAG AA), not hard-coded hex in components.
- Logic stays ours: `STAFF_NAV` gains `children` and `icon`; `staffNavFor(role)` still hides what a role cannot open; the active route and the open group come from the path.

## 8. Steps

Each step ends with a **gate**: lint on changed files, then only the named tests. Commit on the agent's branch when the gate is green.

### Step 0: coordinator (before any agent)

1. `git worktree list` and `git status`; confirm nothing else is working in `ui/nr01-ui`.
2. Create worktrees `F:\cc-wt\s1-a` (branch `ui/s1-a`) and `F:\cc-wt\s1-b` (branch `ui/s1-b`) from `ui/nr01-ui`. `npm ci` once in each `web/`.
3. Add value `S1` to the `ValidateSet` of `nr01-mvn.ps1` (own database `cc_test_nr01_s1`), as P4 did. Confirm V55 is free: `ls api/src/main/resources/db/migration`.
4. Baseline is already recorded (`progress/baseline.md`); no new baseline run.

### Agent A

**A1: API slice** (files: `api/src/main/java/com/coachingcenter/api/exams/*`, `api/src/main/resources/db/migration/V55__*.sql`, `api/pom.xml`, `docs/exam-site/current-api.md`)

1. `V55__exam_paper_language_both.sql`: widen `paper_language` to `VARCHAR(8)`, allow `NULL`, check `IN ('BN','EN','BOTH')` (null passes). Drop the old check by name (`live_exams_paper_language_check`).
2. `LiveExamService`: `readPaperLanguage` accepts `BN`, `EN`, `BOTH`, or absent or null (create and patch); an explicit `null` on patch clears it. The exam view returns `paperLanguage` as the value or `null`.
3. Create accepts blank or missing `descriptionEn/Bn` and `rulesEn/Bn` (stored as empty string, because the columns are `NOT NULL`); titles stay required. The publish checks refuse an exam whose description or rules are blank, with a field error naming the field. Check the existing checklist in `ExamChecklist` or the publish path and reuse it.
4. HTML sanitizer: add the OWASP Java HTML Sanitizer dependency (exact version, at least two weeks old). A small `RichTextSanitizer` component applied on save to the six exam text fields and the two package description fields, in both the draft path and the staged-revision path. Plain text without tags passes through unchanged (newlines kept).
5. Check other readers of paper language (sitting, preview, result views, change list, copy) accept `null` and `BOTH`.
6. `current-api.md`: add an "Added 2026-10-29 (NR-01 UI Step 1)" section.
- **Gate A1:** new `ExamAuthoringStep1IT` (paper language null, BN, EN, BOTH, invalid; blank text on create; publish refused with blank text; sanitizer strips script and event handlers and keeps allowed tags; staged-revision path sanitizes too) and a unit test for the sanitizer. Plus the existing exam authoring and staged-edit IT classes that touch create and publish (the agent names them from `nr-01/api-changes/a.md`). Runner value `S1`. No full suite.

**A2: web foundation and the shared New exam dialog** (files: `lib/api/exam/staff-catalog.ts`, `components/staff/catalog/errors.ts`, a new `new-exam-dialog.tsx`, `module-exams.tsx`, `i18n/.../staffCatalog.ts`, `lib/api/exam/types.ts` for `PaperLanguage`)

1. Types and hooks for the section 3 fields on exam and package; pending-changes hooks (read, discard, publish with `revisionId`) for both; membership, instructor and popular hooks; `paperLanguage: PaperLanguage | null` with `"BOTH"`.
2. Error mapping for the six new codes. Remove the stale "returns to draft" comments and copy.
3. `NewExamDialog` extracted into its own file. Fields: type, English and Bangla titles, paper language (optional select, "Not set" is the default and sends nothing), duration. Props choose the target: `{moduleId}` (package page) or a target picker (Exams page): "In a package" (package select) or "In a category only" (category select). Success goes to `/staff/exams/{id}?created=1`.
- **Gate A2:** unit tests for the pending-changes publish gate logic, the error mapping, and the dialog's request body (paper language omitted when unset, title-only validation, which endpoint is called for each target). Lint.

**CP1 (coordinator):** merge A1, A2 and B1, B2 into both branches.

**A3: exam page** (files: `exam-editor.tsx`, `exam-meta-form.tsx` and its new section files, `exam-checklist.tsx`, a new shared `pending-changes-banner.tsx`, `change-list-dialog.tsx`)

1. Remove the client-side lock and its banners; the `canEdit` rule becomes role and archived only.
2. Sections and fields per 7.2, using the picker (B1) and the rich text editor (B2). Master-Admin-only fields disabled with a reason for the System Admin.
3. Window end separate from duration; inline `EXAM_WINDOW_INVALID` check; mode switch hides dates for PRACTICE.
4. Staged-changes banner, change-list dialog, discard, publish with `revisionId`, `PENDING_CHANGES_STALE` handling.
5. Per-exam instructor control; popular control; free and discount fields.
6. Checklist updated; the `publishable` condition fixed; the answers-released switch no longer shown.
- **Gate A3:** unit tests for the publish gate (draft, published clean, published staged, System Admin), the change-list rendering of fields and impact, section validation (window, discount range, free excludes price). Lint.

**A4: package page** (files: `module-details.tsx`, `module-exams.tsx`, `module-setup.tsx`, `instructor-tab.tsx`, `module-list.tsx` wording, shared banner and change-list from A3)

1. Details fields: discount, popular, rich description; staged-changes banner, change list, discard, publish.
2. Exams tab per 7.3: Open button, columns, Add existing exam dialog, Create exam, remove, reorder, staged state.
3. Instructor tab: `applyToExams`.
4. Wording: module to package in the files it touches.
- **Gate A4:** unit tests for the exam row model (price and discount display, "also in", pending dot), add and remove and reorder request shapes, `EXAM_ALREADY_IN_PACKAGE` message. Lint.

**A5: MCQ authoring** (files: `question-editor.tsx`, `mcq-questions.tsx`, `paste-questions.tsx`, `staff-catalog.ts` question types)

1. `multipleAnswer` in types and requests; the switch and checkbox behaviour; the multi-to-single confirmation.
2. List shows all correct options and the multi-answer tag.
3. Paste parser: several `*`, marks `multipleAnswer`; update `PASTE_FORMAT_EXAMPLE` and its hint.
4. Structure-lock handling: when the API answers `QUESTION_STRUCTURE_LOCKED`, show a clear message and disable add and remove; wording, key, marks and explanation remain editable.
5. Negative marking and "answers can be changed" fields already added in A3; check they are wired.
- **Gate A5:** unit tests for the paste parser (single, multi, none, bad marks), the draft-to-request mapping for single and multi, the multi-to-single switch rule. Lint.

### Agent B

**B1: date and time input** (files: `components/ui/date-time-input.tsx`, remove `time-clock.tsx` if unused, a new `components/ui/calendar.tsx` and `time-columns.tsx`, `i18n/.../common.ts` keys)
- Per 7.7. Same props as today.
- **Gate B1:** unit tests for parse and format (typed 12-hour and 24-hour, AM or PM, Dhaka conversion, invalid dates, Bangla digits), and for picking from the calendar and columns. Lint.

**B2: rich text editor and viewer** (files: `package.json` and lockfile, `components/ui/rich-text-editor.tsx`, `rich-text-view.tsx`, `lib/format/rich-text.ts`)
- Tiptap packages at exact versions at least two weeks old, checked against React 19.2 and Next 16 before installing.
- Editor per 7.6 with an accessible toolbar (labels in both languages); value in and out as an HTML string. Viewer per 7.6; `plainExcerpt(html, max)` helper.
- **Gate B2:** unit tests for the viewer (plain text with and without line breaks, HTML with allowed tags, script content never executes), `plainExcerpt`, and the editor's toolbar output for bold and a list. Lint.

**B3: sidebar and header** (files: `components/shell/staff-shell.tsx`, `staff-nav.ts`, new `staff-sidebar.tsx`, `app/globals.css` tokens, `i18n/.../common.ts`, and a wording sweep of "module" in the other staff message files)
- Per 7.8; `STAFF_NAV` gets `icon` and `children`; items per R6; the Exams entry route `/staff/exams`; labels in both languages; pending-count badge (the data hook from B4's file, or a stub that B4 fills if B3 runs first).
- **Gate B3:** unit tests for `staffNavFor` by role (no leaked items; Instructor sees only the allowed ones), the open group from the path, the collapse state persistence (with storage failing), active item. Lint.

**B4: Exams page** (files: `app/(staff)/staff/exams/page.tsx`, new `components/staff/exams/*`, `lib/api/exam/staff-exams.ts`, `i18n/.../staffExams.ts`; uses A2's dialog after CP1)
- Per 7.4; filters in the URL; the three views; per-group paging; pending badge hook (`GET /live-exam-pending-changes`).
- **Gate B4:** unit tests for the grouping (package, category, none; one exam in two packages appears in both), the filter-to-query mapping, collapse defaults (over 8 groups), and the empty and filtered-empty states. Lint.

**B5: student MCQ sitting and review** (files: `components/student/sitting/mcq-question.tsx`, `mcq-paper.tsx`, `lib/api/exam/sitting.ts`, `components/student/result/result-page.tsx`, `i18n/.../sitting.ts`, `result.ts`)
- Per 7.5. Keep the compatibility fields in use; add `multipleAnswer`, `answersChangeable`, `optionIds`, `position`.
- Paper language (C2): `paperLanguage` becomes `BN | EN | BOTH | null` in the student types. `null` and `BOTH` show no language label or badge anywhere (sitting header, result page); the letters and the typeface follow the interface language.
- The agent first reads `docs/exam-site/new-requirements/nr-01/api-changes/d.md` and `current-api.md` for the exact bodies and read shapes, and reports any gap in the API as a blocker instead of guessing.
- **Gate B5:** unit tests for selection toggling (single and multi), the save body, confirm behaviour when answers cannot change, `ANSWER_LOCKED` message, and the review marking for multi-correct and each `position` type. Lint.

**B6: student rich text** (files: student catalog and preview components that show description and rules)
- Use the shared viewer on the exam page, package page and preview page; use `plainExcerpt` in rows, cards, search results.
- Paper language (C2): on the exam page and the preview page, the language line or badge shows only when the exam has a language of BN or EN; for `null` it is removed completely (no empty row, no "undefined", no placeholder). `BOTH` shows the "Both" label.
- **Gate B6:** unit tests that a card shows an excerpt without tags and that a detail page renders sanitized HTML. Lint.

### Step 8: coordinator

1. Merge `ui/s1-a` and `ui/s1-b` into a Step 1 integration branch off `ui/nr01-ui` (or into `ui/nr01-ui` on Shahriar's go). Resolve conflicts (expected only in the message files and `common.ts`).
2. Under `.heavy.lock`: full `npm run lint`, full `npm test`, one `npm run build` (with `NEXT_PUBLIC_API_BASE_URL` on the command line, under Git Bash), and the API runner for the exams package once.
3. One headless overflow text report at 320, 375, 768 and 1280 px for: sidebar open and collapsed, Exams page, exam page (draft and published with staged changes), package page (Exams tab), a student MCQ sitting. No screenshots.
4. Write the records: `progress/step1-review.md` (what changed, how to click through, open items, Bangla drafts), the decisions into `conversation-decisions.md`, and `current-api.md` is already updated by A1.

## 9. Click-through for Shahriar after the run

1. Sidebar: open, collapse, remember after reload, accordion groups, phone drawer, dark theme, each role.
2. Exams page: grouped view, collapse state, search and filters, an exam in two packages, "Not in a package", New exam into a package and into a category only.
3. Create dialog: only type, titles, language (leave empty and "Both"), duration.
4. Exam page: every new field; rich text in both languages; calendar and time columns; practice mode; window shorter than duration (inline error); edit a published exam, see the banner, open the change list, discard, publish as Master Admin, try as System Admin.
5. Package page: discount, popular, staged edit and publish; Exams tab with Open, Add existing, Create, remove, reorder.
6. MCQ: create a multi-answer question, paste with several `*`, try to add an option after a student has started.
7. Student: sit a multi-answer MCQ; confirm button when answers cannot be changed; review shows all correct options and the position line; description rendering on exam and package pages.
8. Bangla labels: all new strings are drafts.

## 10. Risks

| Risk | Handling |
|---|---|
| Tiptap or the sanitizer library does not fit the stack | B2 and A1 check compatibility first and report a blocker before building on it. |
| Rich text leaks tags into a screen that expects plain text | The grep in 7.6, and the B6 tests. |
| Two agents edit `common.ts` or the message files | Ownership table in section 5; conflicts are expected only there and resolved at CP1 and step 8. |
| Haiku misses a staged-edit corner (section 3) | Gates A2 to A5 test the publish gate and request shapes; the coordinator reviews A2's diff before CP1. |
| Student sitting change breaks the current flow | B5 keeps the compatibility fields; its gate covers both single and multi answers. The coordinator reviews B5's diff before step 8. |
| Token cost | Section 6. |
