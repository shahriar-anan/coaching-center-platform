# Evaluation UI plan (written 2026-10-27)

**Scope:** the staff screens for written evaluation, for Master Admin, Instructor and System Admin. These are the marking list, the marking page, the results report, the leaderboard, the participation counts, and the evaluation parts of the two home screens. The student's result page is out of scope here.

**Method:** I read the code for each screen, and I checked the audit captures in `docs/exam-site/reports/ui-audit/snapshots/C/` (taken 2026-10-26, before the 2026-10-27 API changes). Those captures are the visual evidence below. A fresh capture pass is step 0 of the plan, because the marking page and the release flow have changed since.

**Industry reference points (general practice, used as the standard for each issue):**
- Canvas SpeedGrader: the submission on the left, the grade panel on the right, next and previous student in the same view, and a posting policy that controls when grades are released.
- Moodle grading table: a status per submission (not marked, marked, released), filters for "needs grading", and a bulk release action that shows how many are not ready.
- Blackboard Grade Center and Google Classroom: a "needs grading" queue that is the default view for the marker, and a clear "returned" or "released" state that the student sees.

## 1. Issues found

Severity: **B** blocks a task, **H** high (common task is slow or confusing), **M** medium, **L** low.

### 1.1 Results report (Master Admin, System Admin, Instructor)

| # | Issue | Evidence | Sev |
|---|---|---|---|
| R1 | The release button sits in the page header, away from the table it acts on. It shows only a count. It does not show why a release is refused (papers awaiting a mark, sittings still open). | `app/(staff)/staff/exams/[examId]/results/page.tsx` (header actions), `components/staff/results/publish-results-button.tsx` | H |
| R2 | A refused release (`409 EXAM_MARKING_INCOMPLETE`) is not handled. The button's `catch` throws a generic error, so the counts never reach the user. | `publish-results-button.tsx` (`confirm`) | H |
| R3 | On a 360 px screen the header wordmark "ExamPortal" runs under the language and theme buttons. | `staff-exams-examId-results__360__en__light__master.png` (the screenshot you sent); the same collision in `staff-submissions-participantId__360__en__light__instructor1.png` | H |
| R4 | At 360 px the results table turns into nine stacked cards of nine fields each. This is about 4,000 px of scrolling for an exam with nine papers. Nothing shows the status or the score at a glance. | `staff-exams-examId-results__360__en__light__master.png` | M |
| R5 | Date and time cells wrap onto two lines ("24 October, 12:00 / PM"), and "Not applicable" wraps too. The rows are taller than they need to be. | `staff-exams-examId-results__1440__en__light__master.png` | M |
| R6 | "Not passed" is shown in red for every row, including MCQ rows where a score of 0 is the first state. The red fill makes the page look like a list of failures. | same capture | L |
| R7 | The "Published" column shows "Not applicable" for MCQ. For written rows it does not show that a correction is waiting (see M3). | same capture | M |

### 1.2 Marking list (Master Admin, Instructor, System Admin)

| # | Issue | Evidence | Sev |
|---|---|---|---|
| L1 | Rows open only on click. There is no link, no focus, and no keyboard action, so a keyboard user cannot open a script from the list. | `components/ui/table.tsx` (`onClick` on the `tr`) | H |
| L2 | The tree counts show only papers still awaiting a mark. A fully marked exam reads 0, with no label that says so. | `components/staff/marking/marking-queue.ts` (`buildBrowserTree`, `awaitingMarking`); `staff-submissions__1440__en__light__master.png` shows "All scripts 0" next to five scripts | H |
| L3 | "Publish all" uses the participation draft count. That count does not include a correction waiting for release (see API bug A1), so the button can show 0 while corrections wait. | `components/staff/marking/bulk-publish.tsx`; `api/.../LiveExamParticipationService.java` (`DRAFT`) | B |
| L4 | "Publish all" has no release gate. The counts for papers awaiting a mark and for open sittings are not shown. A refusal is not shown either. | `bulk-publish.tsx` (`BulkPublishExam`) | H |
| L5 | A category view reads only the first 100 papers of each module, and the list shows no pagination for it. The warning line is the only signal. | `submission-browser.tsx` (`byModule`, `truncated`) | M |
| L6 | The table has a teacher column for Master Admin only. The status and the submitted time share one column width, so the date wraps. | `submission-browser.tsx` (`columns`); `staff-submissions__1440__en__light__master.png` | M |
| L7 | The status words are "Awaiting marking", "Draft mark", "Published", "Not submitted". A correction waiting for release still reads "Draft mark", and that is wrong. | `i18n/messages/*/staffMarking.ts` (`statusWord`) | H |
| L8 | The "Publish selected" button is always visible, disabled, with "(0)". Most of the time it is noise. | `staff-submissions__1440__en__light__master.png` | L |
| L9 | "Publish selected" runs one request per paper. A failure shows only a count of failed papers, with no reason. | `bulk-publish.tsx` (`BulkPublishSelected`) | M |

### 1.3 Marking page (Instructor, Master Admin)

| # | Issue | Evidence | Sev |
|---|---|---|---|
| M1 | The copy still says that a save hides a published result. That is no longer true: a correction waits and the released result stays visible. | `staffMarking.ts` (`saveNote`, `publish.republishNote`) | H |
| M2 | The file field still reads as required ("Choose a PDF file"), even though a save with no new file now keeps the stored PDF. | `submission-form.tsx` (file section) | H |
| M3 | A released paper with a correction waiting shows "Published: Yes" and the marks it had before. Master Admin cannot see that a correction is waiting, except from the Publish button's state. | `submission-form.tsx` (facts, publish block) | H |
| M4 | A paper with no submission shows "Score 0 / 50". That reads as a mark of zero. It should show a dash. | `staff-submissions-participantId__1440__en__light__master.png` | H |
| M5 | On a phone the answer PDF sits above the marks. An instructor must scroll past a full page of PDF before reaching the mark field. There is no sticky marks panel. | `staff-submissions-participantId__360__en__light__instructor1.png` (the card stack) | H |
| M6 | The header on the marking page collides at 360 px (the same as R3). | `staff-submissions-participantId__360__en__light__instructor1.png` | H |
| M7 | The "not submitted" card puts a bordered box inside a bordered card, so there are two borders for one message. | `staff-submissions-participantId__1440__en__light__master.png` | L |
| M8 | Previous and next use the first 100 papers of the module. The progress count ("marked of total") uses the same first page, so it is wrong for a large module. | `app/(staff)/staff/submissions/[participantId]/page.tsx` (`queue`, `neighbour`, `progress`) | M |
| M9 | The keyboard hint (J and K) is shown as an icon with no label in the header. Most users never see it. | same capture, the icon at the top right | L |
| M10 | After "Save and next", the page leaves the paper before the success message is read. The toast is the only confirmation. | `submission-form.tsx` (`onSaved`) | L |

### 1.4 Participation and home screens

| # | Issue | Evidence | Sev |
|---|---|---|---|
| P1 | The home screens show "drafts" from the same count as the release button, so they inherit bug L3. | `components/staff/home/admin-home.tsx`, `instructor-home.tsx` | H |
| P2 | The instructor home has no count of corrections waiting. An instructor cannot see that a corrected paper is waiting for the Master Admin. | `instructor-home.tsx` | M |

### 1.5 System Admin

System Admin reads the list, the facts and the evaluator name, and cannot mark or release (matches the decisions). The problems are the same as for the list and the results report: the counts (L2) and the status words (L7) are confusing, and the results page shows a publish button only to Master Admin, which is correct. The gap is that System Admin has no way to see why a release is pending. A read-only status line on the results page would fix that.

### 1.6 API bug found during this review (must fix first)

**A1.** `LiveExamParticipationService` defines a draft as `evaluated_at is not null and published_at is null`. After the 2026-10-27 change, a released paper with a correction waiting is not counted as a draft, but it is counted as published. The release count is therefore too low. This is a regression from my change, not a design issue.

Fix: the draft condition becomes `(evaluated_at is not null and published_at is null) or pending_evaluated_at is not null`. The published condition becomes `published_at is not null and pending_evaluated_at is null`. Add a test: a released paper with a correction counts as a draft.

## 2. What the screens should do (target design)

### 2.1 One marking workspace (SpeedGrader pattern)

- A single page per exam: the list on the left (collapsible), the paper in the centre, and the mark panel on the right. Next and previous stay in the same view.
- On a phone, the mark panel is a sticky bottom bar with the mark field and "Save". The PDF opens in a full-height sheet. The instructor is one tap from the mark field.
- The status of the paper is shown in the panel header: awaiting mark, draft mark, released, correction waiting for release.

### 2.2 Queue (Moodle "needs grading" pattern)

- Default view for instructors: "Needs marking" (the status `AWAITING`). Master Admin's default: "Needs release" (drafts and corrections).
- Filters are status tabs with counts, not a dropdown: Needs marking, Draft, Released, Not submitted. The counts are the real counts for the tab.
- Rows are real links. A row opens with Enter or a click. Focus is visible.
- The tree shows totals and a second number for "needs marking", with a legend.

### 2.3 Release (Moodle "release grades" pattern)

- One release panel per exam, at the top of the results report and the queue. It shows: papers still awaiting a mark (with a link to them), sittings still open, drafts ready, corrections waiting, and failed-to-submit zeros (not blocking). The release button is enabled only when the first two are zero.
- A refused release shows the counts from the `409` body, with a link to the papers.
- Per-paper release is kept as a secondary action in the marking panel.

### 2.4 Status words

| Server state | Label | Tone |
|---|---|---|
| Submitted, no mark | Needs marking | warn |
| Marked, not released | Draft mark | muted |
| Released, correction waiting | Published, change waiting for release | warn |
| Released | Published | ok |
| Failed to submit | Not submitted (zero) | danger |

### 2.5 Copy

- `saveNote`: "Students keep seeing the released mark. Your change waits until the Master Admin releases it." (for a released paper) and "Students cannot see this yet." (for a draft).
- `republishNote`: remove the sentence about turning the mark back into a draft.
- File field: "Leave the file out to keep the stored script. Add a PDF only to replace it." Show "Required" only when no PDF is stored.

### 2.6 Facts

- A paper with no submission shows "—" for the score, not 0. The status says why.
- The "Published" fact shows the release time, or "Change waiting since …".

### 2.7 Mobile and header

- Fix the 360 px header collision (R3, M6): the wordmark becomes the logo only below 400 px, with the language and theme controls in the menu.
- Results on a phone: a compact row (student, score, status), with the details in a sheet. No nine-field cards.

### 2.8 Visual

- Dates in the table use one line (short format: "24 Oct, 12:00 PM"). Column widths are set so the date does not wrap.
- "Not passed" uses the danger tone only in the pass column, not the whole row.
- The "Publish selected" button appears only when at least one row is ticked.

## 3. New pages and components

- **Release panel** (new component): used on the results report and the queue header. Props: counts, the release action, the refusal details.
- **Marking workspace** (new layout): one page for the list and the paper and the mark panel. The current `/staff/submissions/[participantId]` route stays, so links keep working; the list moves into a collapsible side panel.
- **Status tabs** (new component) replacing the status dropdown in the queue.
- **Sticky mark bar** for phones (new component).
- No new routes are needed. The marking workspace reuses the existing routes and query parameters.

## 4. Order of work

1. **A1** (API bug) with its test. Blocks the correct release count.
2. **Copy and status words** (M1, M2, L7, 2.5). Small, no layout change.
3. **Release gate** (L3, L4, R1, R2, 2.3). Uses the `409` details from the API.
4. **Facts and zero scores** (M3, M4).
5. **Keyboard and links in the list** (L1), and status tabs (2.2).
6. **Tree counts** (L2) with a legend.
7. **Mobile header and results compaction** (R3, M6, R4).
8. **Marking workspace and sticky mark bar** (M5, 2.1, 2.7).
9. **Visual cleanup** (R5, R6, L6, L8, M7, M9).

Each step is checked on the captured screens at 360, 768 and 1440 px, in English and Bangla, light and dark, for each role.

## 5. Questions (answered in section 7)

1. Should an instructor see a "ready for release" flag? It was held back until you approved it. The queue would show which papers the instructor has finished, and the Master Admin would release those first.
2. Should a correction notify the student again, or only when the Master Admin releases it? The current code notifies on each release.
3. The MCQ results report shows "Published: Not applicable". Should MCQ rows show a release state at all?
4. On a phone, should the marking workspace show the PDF first, or the mark panel first?

## 6. Evidence

- `docs/exam-site/reports/ui-audit/snapshots/C/staff-submissions__1440__en__light__master.png`
- `docs/exam-site/reports/ui-audit/snapshots/C/staff-submissions-participantId__1440__en__light__master.png`
- `docs/exam-site/reports/ui-audit/snapshots/C/staff-submissions-eval-2-instructor1-draft__1440__en__light__instructor1.png`
- `docs/exam-site/reports/ui-audit/snapshots/C/staff-exams-examId-results__1440__en__light__master.png`
- `docs/exam-site/reports/ui-audit/snapshots/C/staff-exams-examId-results__360__en__light__master.png`
- `docs/exam-site/reports/ui-audit/snapshots/C/staff-submissions-participantId__360__en__light__instructor1.png`

Each capture predates the 2026-10-27 API changes. Re-capture is step 0 of any UI work.

## 7. Decisions (2026-10-27)

Shahriar asked for the recommended approach on each question. The reasons below follow common LMS practice: Moodle's marking workflow (Not marked, In marking, Ready for release, Released), Canvas SpeedGrader (the document and the grade side by side, with a posting policy), Google Classroom (a returned state, with a notice to the student), and Blackboard Grade Center (a needs-grading queue).

### 7.1 Ready-for-release flag for instructors: yes

- The instructor marks a paper "Ready for release". It is available on a paper with a mark, a draft or a correction. Any later save clears the flag.
- The instructor cannot release. The Master Admin sees a "Ready for release" queue tab with its count.
- The release panel shows two actions: "Release ready papers (N)" and "Release all (M)". The release-all gate stays the same: no paper awaiting a mark and no open sitting. The flag is a signal, not a gate. The Master Admin can release a paper that is not flagged.
- Implementation needs:
  - Flyway V43: `live_exam_participants.ready_for_release_at timestamptz`.
  - API: `POST` and `DELETE /api/v1/live-exam-participants/{id}/ready`, a `ready` filter on the feed, the count in the participation summary, and a release action that takes only the ready papers.
  - Web: the flag button in the marking panel, the queue tab, and the instructor home count.
- Why: Moodle's marking workflow has this state, and it lets the Master Admin release the reviewed papers first.

### 7.2 Notify the student again on a correction: yes, on each release that changes the mark

- A notice is sent each time a release changes the mark the student sees. A save sends nothing. A release that changes nothing sends nothing (the current code already does this).
- The second notice says the result was updated, and it shows the new mark.
- Why: the student can learn of a changed mark only from the notice. A silent change is the usual complaint in grade forums, so the change should always be announced.

### 7.3 MCQ rows: no release state

- MCQ results are scored when the paper posts (decision of 2026-10-23). MCQ rows show "Auto-scored" and no release action. The "Published" column is hidden for MCQ.
- Conflict to record: the client brief (section 4.6) says MCQ results appear "after the admin publishes". The decisions (2026-10-23) say the score is returned when the paper posts, and the decisions win. Shahriar should confirm with the client if the brief is still their position.

### 7.4 Phone layout: the script first, with the mark one tap away

- Below 768 px the marking page has two tabs: "Script" (the default, full height) and "Mark" (the form). The current mark shows as a chip on the Script tab, for example "31 / 50 · draft", and the save bar stays at the bottom of the Mark tab.
- Desktop stays side by side.
- Why: the instructor reads the script before marking, so the script comes first. A form first hides the script, and a script with no mark chip makes the instructor scroll back to see the mark. Grading tools generally show the document first.

### 7.5 Effect on the plan

- Step 3 (release gate) includes the two release actions in 7.1.
- Step 8 (workspace) uses the tabs in 7.4 on a phone, not a sticky mark-first bar.
- Copy for the second notice is added to step 2.
- A1 (the participation count bug) is still the first step, and it needs your go-ahead before I change the code.

## 8. Progress (2026-10-27, after the API and web changes)

**Done**
- **A1** (participation count): a released paper with a correction waiting counts as a draft, not as published. Test added.
- **Step 2** (copy and status words): the save note and the republish note describe the new rule, in both languages. Status words: "Needs marking", "Draft mark", "Ready for release", "Change waiting for release", "Published", "Not submitted". The file field shows "Required" only when no marked PDF is stored.
- **Step 3** (release gate): the results page has one release panel. It shows the counts, releases the ready papers or all results, and shows the server's counts when a release is refused. The browser's "Publish all" control is now a link to that panel. The ready flag (V43) is set and cleared by the marker, and the Master Admin can release the ready papers alone (no completeness gate). The release status endpoint is `GET /live-exams/{id}/release-status`.
- **Step 4** (facts): a paper never submitted shows a dash for the score. A correction waiting shows "Yes, change waiting for release".
- **Step 5** (keyboard and filters): the student name is a link, so a keyboard user can open a paper. The status dropdown is replaced by tabs with counts for "Needs marking" and "Waiting for release". The "Ready for release" tab is for the Master Admin.
- **Step 6** (tree): a note says the tree counts papers still awaiting a mark. "Publish selected" is hidden when nothing is ticked.
- **Step 7** (phone): the 360 px header collision is fixed in the code (the wordmark is hidden below 400 px). Your screenshot predates that fix, so it needs a fresh capture to confirm. On a phone the results list hides the kind, maximum and started columns, and the dates no longer wrap.
- **Step 8** (marking page on a phone): below 768 px the script and the mark are two tabs, as decided in 7.4. The script is the default, and it shows the current mark.
- **Step 9** (visual, part): MCQ rows say "Auto-scored" and have no release state. The no-answer note no longer nests inside a card.

**Not done yet**
- The queue's previous and next buttons, and the "marked of total" count, use only the first 100 papers of a module (M8). A category view still shows only the first page of each module (L5).
- The keyboard hint on the marking page is an unlabelled icon (M9). The "Save and next" confirmation is only a toast (M10).
- The instructor home does not count corrections waiting for release (P2).
- Done after the first report: the results report rows show "Change waiting" for a correction waiting for release (R7, M3). The student's second notice says "Written result updated" and the newer notice is marked `updated` (decision 7.2). The release-all button and its refusal message are covered by tests in both the API and the web.
- Fixed while checking: `LiveExamIT` published exams as the System Admin, which the decision of 2026-10-26 no longer allows. Those tests now publish as the Master Admin.
- The status tabs show counts for "Needs marking" and "Waiting for release" only. "Ready for release" has no count.
- Every Bangla string added in this step needs a native check.
- All screenshots are from 26 October. A fresh capture is still needed to check the layout changes (step 0).
