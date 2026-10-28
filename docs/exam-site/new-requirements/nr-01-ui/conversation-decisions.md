# NR-01 UI: decisions and findings from the planning discussion

Recorded 29 October 2026 on `ui/nr01-ui`, from `e826aa0`. This file records what was discussed and decided, what is still open, and what the screens and the API actually do today. It is not the phase plan; the plan follows once the orchestrator's step choice is in.

Status words: **Decided** = Shahriar agreed. **Recommended** = proposed, not yet confirmed. **Open** = needs an answer. **Pending** = Shahriar said he will answer later.

## 1. Scope

| Item | Status |
|---|---|
| Written exam flow for all users: package creation, written exam creation and editing, staged-edit publish, question paper upload, student browse, access (purchase or admin grant), sitting, answer upload, instructor evaluation, master release, student result | Decided |
| Exam creation and editing for **both MCQ and written**, in the admin section, wired to every field the API now has. Layout and flow stay as they are; only the missing fields are added | Decided |
| Admin and instructor dashboard overhaul, and a new Exams page for the master admin | Decided. Details to come from Shahriar |
| Student pages: browsing packages and exams, and new tabs for monitoring exams, results and notices | Decided. Details to come from Shahriar |
| MCQ sitting UI: radio circles, multi-answer, confirm button, answers at submit, new position messages | Deferred to after the month. The current MCQ screens keep working through the compatibility rules |
| Retake grant for written exams in the UI | Out of scope this month |
| Access: where a student gets written access from (package page, exam page or both), and whether purchase uses the existing checkout | Pending (Shahriar will answer) |

## 2. Working rules

- **Testing.** Lint on the files each slice touches, unit tests only for logic that could break (publish gate, upload checks, change list, access state), and a build once per phase. No end-to-end tests. Shahriar decided this.
- **Layout checks.** A headless browser may report text such as "this element is wider than the screen" or "content overflows horizontally". No screenshots. Shahriar agreed.
- **Review.** Shahriar tests on the dev server and sends a fix list after each step, with a short helper guide. Small visual issues go to the agent with the fix list.
- **Backend.** The local API, which has the newest changes. Seeded data comes from the API's seed code, so the test accounts and written exams should come from there. To check before Phase 1.
- **Delivery.** After each phase the agent pushes to `ui/nr01-ui`. Shahriar reviews and merges.
- **Budget.** The weekly limit is at 71%. Phases stay small, one session per phase, Haiku for mechanical wiring and Sonnet for stateful flows, and no polling of background runs.
- **Wording.** "Module" becomes "package" everywhere in the screens, for admins and students. URLs keep `/staff/modules` and the other existing paths. Shahriar agreed.
- **Bangla.** The agent drafts new Bangla labels from `bangla-copy.md`, and Shahriar gives the terms to follow during implementation. Shahriar will say what to use for each new word.

## 3. Watermarks

### What is wrong today

- The written question paper carries the student's name and ID as `name · code`.
- It is drawn as a diagonal repeat over the whole page: 9 rows by 3 columns, bold text at large size, navy at 14% opacity (`web/src/components/pdf/pdf-watermark.tsx`).
- Shahriar's finding: it is too heavy and makes the question hard to read.
- It does not show the platform's identity (SYMAMLAW).

### Recommended design (not yet confirmed)

- **Layer 1, logo.** The site logo centred on each page, behind the text. Not rotated. About 40% of the page width. About 8% opacity.
- **Layer 2, traceability.** The student's name and ID on one small horizontal line in the top and bottom margins of each page. It never crosses the question text.
- **No diagonal text.** One traceable line does the job, and a repeated diagonal reads as noise.

### Logo

- The header logo is `web/public/brand/logo-light.png` (dark theme: `logo-dark.png`). It is the SYMAMLAW crown mark with the words "ExamPortal".
- Open: use this file for the watermark? Shahriar said "current logo beside exam portal", so this is the likely answer; confirm.
- Open: the site name. Decisions U4 set the student site's text logo as "এক্সাম পোর্টাল"; the staff header shows "ExamPortal". Do not change the site name in this plan; the watermark uses the logo image only.

### Surfaces

- **Question paper, student sitting:** the watermark applies (decided: it already does).
- **Answer sheet, staff marking viewer:** not decided. The question is whether it carries the same watermark as the marked script.
- **Marked script, student result page:** Shahriar asked for a watermark. This **reverses the 27 October decision** ("the marked PDF carries no watermark"). Record the reversal.

### Marked script: how

- **Option A, viewer overlay (recommended for now).** The same overlay as the question paper, drawn by the website. Quick and needs no API change. A determined student can still screenshot or crop it.
- **Option B, stamped into the file.** The API stamps the PDF when it serves it. Stronger, but it is API work and adds scope.

Open: Option A now and Option B only if leaks become a problem? Shahriar's answer decides this.

### Viewer fault found while reading the code

- The horizontal scroll bug is in `web/src/components/pdf/pdf-viewer-impl.tsx`. The page column uses `flex flex-col items-center`. When a zoomed page is wider than its box, centring pushes the left edge out of reach, so horizontal scroll cannot reach it.
- Fix direction: start the column at the left edge and let it grow to the full page width, so the page scrolls both ways. Verified by the headless width check and Shahriar's review.

## 4. Exam creation and editing (admin, MCQ and written)

### What the screens do today (from the screenshots and code)

- **Create dialog ("New exam").** Exam type (MCQ or Written, locked after creation), English and Bangla titles, English and Bangla descriptions, English and Bangla rules, paper language, duration. Dark theme. The dialog is `NewExamDialog` in `web/src/components/staff/catalog/module-exams.tsx`.
- **Editor.** Five numbered sections: 1 Details, 2 Schedule and marks, 3 Question paper, 4 Price, 5 Check and publish. A status pill (Draft). A "Student view" button. Breadcrumbs.
  - **Schedule and marks:** duration (minutes), pass percent, maximum marks, "Set a start time" or "Date to be announced", a date, time and AM or PM, and the computed end time.
  - **Question paper (written):** "View", and the text "Students cannot download it."
  - **Price:** price in ৳, and the hint "Leave blank and this exam cannot be bought on its own."
  - **Check and publish:** the publish checklist, then "Exam saved. What next?" with "Create another exam" and "Assign an instructor".
  - **Footer:** Archive and Delete exam.
- **Lock banners.** Each section shows "The exam has started, so nothing can be changed now" and "The start time has passed, so the file cannot be replaced now." **These must go.** The API stages edits instead (E27, E31).
- **Sidebar.** Catalog: Categories, Modules. Exams and marking: Participation, Marking, Announcements. People, Money, Admin. There is no Exams page yet; it goes under Catalog, after Packages.

### Fields the API has now that the screens do not show

| Field | Where it goes (recommended) | Notes |
|---|---|---|
| Exam mode (LIVE or PRACTICE) | Schedule | Practice exams have no window |
| Window end (`endAt`) | Schedule | Replaces the computed end from duration. The API requires the window to be at least as long as the duration (D16) |
| Negative mark | Schedule and marks (MCQ) | Default 0.25 (proposed) |
| Answers can be changed | Schedule and marks (MCQ) | Default on (proposed) |
| Preview enabled | Details | Off by default (proposed) |
| Discount percent and end date | Price | Optional. Master admin only (the price permission) |
| Free exam | Price | Master admin only |
| Access days, or lifetime | Price or Access | Per exam; counted from the window end (A2') |
| Instructor (per exam) | Check and publish, or a row in Details | Defaults to the package's instructor |
| Popular | Master admin only, on the package or the exam | Not in the editor for admins |
| Paper language "Both" | Details | **Needs an API change** (see section 7) |
| Question paper versions and staged replacement | Question paper | Replacing the file after the start is allowed now, with a new version |

### Decisions on the editor

- **Same layout for MCQ and written.** Only the missing fields are added (Shahriar).
- **Staged-changes banner** on any published exam with a pending change. It appears in every section, not only the top. Shahriar agreed.
- **"Publish changes"** appears only for the master admin. Shahriar agreed.
- **Published exams use the change-list flow now.** Load the pending changes, show the list, publish with the `revisionId`. Drafts keep the simple path. The compatibility path is removed once the new flow is live. Shahriar agreed.
- **Comment fix.** `web/src/lib/api/exam/staff-catalog.ts` still says a published module "returns to draft" on save. The API no longer does that. The copy and the status display must be checked. Shahriar agreed.

### Open on creation

- The create dialog: should it also take the window, price and instructor, or keep only the minimum plus pass percent and maximum marks for written? Shahriar's latest message says the flow and layout stay the same and the remaining necessary fields are added. Exact placement is still open.
- Defaults: negative mark 0.25, answers changeable on, preview off. Shahriar asked for these to be confirmed.

## 5. Student written flow

- **How a student sits today.** The question paper is shown inline in the page with a zoom control, and students cannot download it (`written-sitting.tsx`, `allowDownload={false}`). The answer upload sits below it. Shahriar says the submission works well and should stay.
- **Upload messages and limits.** Keep as they are. The limits are PDF only, 80 MB, compressed by the server.
- **Viewer fix.** The horizontal scroll fault in section 3.
- **Access.** Pending (Shahriar said he will answer).

## 6. Staff evaluation

- **Keep the existing evaluation screens.** Shahriar agreed; they work.
- **Add an "Evaluated" tab** after the existing "Answer" and "Question paper" tabs. Shahriar agreed. It shows the marked script for an exam once a paper has been marked.
- **Open:** show the tab only once a paper is marked, or greyed out before that? The student's result page already shows the evaluated paper, with no watermark yet (section 3).

## 7. API changes the UI depends on

These are not all done. Two of them are small API work that the UI cannot do without.

| Need | Status in the API | What it takes |
|---|---|---|
| Paper language "Both" | Not supported. Only `BN` and `EN` are accepted (`LiveExamService`, and the `V30` check constraint) | An API change plus a migration that widens the check constraint. Small, but it adds an API slice |
| Replacing a written question paper after the start | Supported: a new version, staged on a published exam | UI change only |
| Window end independent of duration | Supported (`endAt`) | UI change only |
| Staged changes, change list and publish with `revisionId` | Supported (`GET …/pending-changes`, `DELETE …/pending-changes`, publish with `revisionId`) | UI change only; the web does not call these yet |
| Per-exam instructor | Supported (`POST`/`DELETE /live-exams/{id}/instructor`) | UI change only |
| Evaluated paper for staff | Supported (`/live-exam-participants/{id}/evaluated-pdf`, used by the student result page) | UI change only |
| Discount, free, access days, preview | Supported | UI change only |

## 8. Open questions

1. **Logo.** Use `web/public/brand/logo-light.png` for the watermark? Should the watermark say SYMAMLAW anywhere else?
2. **Marked script watermark.** Option A (viewer overlay) now, Option B (stamped file) later, or B now?
3. **Answer sheet watermark.** The same watermark as the marked script, or none?
4. **Create dialog.** Which of the new fields go into it, and which stay in the editor?
5. **Defaults.** Negative mark 0.25, answers changeable on, preview off: confirm?
6. **"Both" paper language.** Approve an API change (a small slice and a migration) to accept it?
7. **Access (item 10).** Where students get written access from, and whether purchase uses the existing checkout. Pending.
8. **Evaluated tab.** Hidden until marked, or greyed out?
9. **Test accounts.** Which seeded written exam, question paper and student should I use on the local API? I'll check the API seed code before Phase 1.
10. **Dashboards and the Exams page, and the student tabs.** Shahriar will give details later.

## 9. Next step

Shahriar chooses the steps and phases. After that the phase plan is written into this folder, and Phase 0 starts.

## 10. Second analysis (29 October 2026): what the web actually does

Read from the code on `ui/nr01-ui`. Corrects two earlier statements: "everything else the UI needs already exists" was wrong, and the answer-sheet watermark was not checked before.

### Exam creation and editing (MCQ and written)

- The editor has five sections in `exam-meta-form.tsx`: Details, Schedule and marks, Question paper, Price, and Check and publish.
- `StaffExam` and `ExamInput` in `lib/api/exam/staff-catalog.ts` lack the new fields: mode, package list, instructor, negative marks, answers changeable, preview, popular, free, discount, access days, pending changes. None is read or sent.
- The lock is client-side: `locked = liveAt <= now` (`exam-editor.tsx`). It disables every section, and is the cause of the "exam has started" banners. It must go, and the staged-edit flow replaces it.
- `publishable` is `… && exam.status !== "PUBLISHED"`. A published exam with staged edits therefore cannot be published at all. The change-list flow has to replace this condition.
- The schedule computes the end from the duration. The API has a separate window end, which must be at least the duration.
- The MCQ question editor supports one correct option only (`question-editor.tsx`: `correct: number | null`). Multi-answer questions cannot be authored in the UI. Creating MCQ exams "fully integrated" therefore includes this, unless you decide to keep multi-answer authoring out.
- `errors.ts` maps retired codes (EXAM_WINDOW_CONFLICT, EXAM_WINDOW_ENDED, CONTENT_LOCKED) and none of the new ones (QUESTION_STRUCTURE_LOCKED, PENDING_CHANGES_STALE, EXAM_WINDOW_INVALID, ANSWER_LOCKED, NO_PENDING_CHANGES, EXAM_ALREADY_IN_PACKAGE).
- Paper language drives the answer letters (ক, খ, গ, ঘ or A, B, C, D) in the MCQ paper, the result page and the exam page. The editor sets `lang` from it. "Both" needs a rule for the letters and for the typeface, and the API needs the change.

### Dashboards and navigation

- Staff home (`app/(staff)/staff/page.tsx`) shows `InstructorHome` for instructors and `AdminHome` for admins.
- `AdminHome` has summary cards, a 14-day agenda, and four quick links (New package, New exam, Payments, Students). "New exam" links to the package list, not to a creation form.
- The master admin has no Exams list. The only route under `staff/exams` is `[examId]`. The new Exams page is a new route.
- `InstructorHome` lists the instructor's waiting marks and recent submissions, grouped by package. The per-exam instructor needs the package grouping to change.
- The sidebar's "Catalog" group is Categories and Modules. The new Exams entry goes there.

### Student browsing and access

- The home (`home-page.tsx`) has four sections: next exam, now, upcoming, and my modules. The results hub has three tabs: results, marking, other. Notices live at `app/(site)/notices`. The student tabs request starts from these.
- Doors are `mcq` and `written`, reached through `modulePath`.
- The exam page's buy button uses `data.price`, the raw price. The API returns a separate `pricing` object with the effective price, and the order charges that. A discounted item would show the list price and charge less, with no explanation. No web file reads `pricing`, `discountPercent`, `free` or `popular`.
- `AccessInfo` has only `state`, `buyable` and `blockedReason`. The API's `blockedReason` values match the web's four. Expiry is not on the exam's access object, so an expired student sees the exam as owned. Expiry does appear in the "my access" list (`endsAt`, `expired`), and the web does not show it there.
- Admin grants exist: `components/staff/access/grant-access-form.tsx` and `student-access-section.tsx`. The grant form calls `live-exam-grants`. It may not handle an exam grant with no package, and needs checking when the written flow is planned.
- The preview endpoint, favourites, retake grants, attempts, and pending changes are called by no web file.

### Written sitting, marking and result

- The sitting shows the question paper inline with `allowDownload={false}` (`written-sitting.tsx`). The answer upload sits below it.
- The marking tabs are Answer and Question paper only (`paper-views.tsx`: `type View = "answer" | "question"`). The Evaluated tab is missing.
- The student result page (`result-page.tsx`) already shows the evaluated paper through `PdfViewer` with no watermark.

### Watermarks, every surface

| Surface | Watermark today |
|---|---|
| Student question paper in the sitting | student name · code |
| Student marked script on the result page | none |
| Staff answer sheet viewer (`paper-views.tsx`) | SYMAMLAW, diagonal |
| Staff marked script (`marked-pdf-view.tsx`) | SYMAMLAW, diagonal |
| Staff question paper panel (`question-paper-panel.tsx`) | SYMAMLAW, diagonal |
| Staff editor preview (`written-paper.tsx`, `exam-editor.tsx`) | SYMAMLAW, diagonal |

The staff answer sheet and the staff marked script already carry the diagonal SYMAMLAW watermark. The redesign has to cover every one of these, not only the student's question paper.

### Module to package wording

- About 160 English strings contain "module". The nav labels are in `common.ts` and `staffCatalog.ts`. Student copy such as "Buy whole module" and "My modules" is in `catalog.ts` and `account.ts`. Staff copy such as "My modules" is in `staffMarking.ts`.
- URLs keep `/modules/`. Only the words change.

### Open items from this analysis

1. The exam page and purchase sheet must show the effective price, with the list price struck through. The purchase sheet needs the same fix.
2. Expiry must reach the exam page's access object, or the web must read the "my access" list for expiry. The API change is small.
3. Multi-answer authoring: include it in the MCQ creation work, or keep it out?
4. "Both" paper language: the letters and the typeface rule are needed, and the API change is needed.
5. The staff answer sheet and marked script watermarks need the new design, the same as the question paper.
6. Access and grants for written exams: the grant form needs checking, and the purchase path for a written exam needs confirming with you (item 10).
