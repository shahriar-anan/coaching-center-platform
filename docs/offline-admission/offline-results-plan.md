# Offline exam results: plan (15 Nov 2026)

The routine's exams get results. An admin uploads a result sheet (Excel) for one exam of a batch. The Master Admin publishes it, and
every student onboarded to that batch sees their own result and the exam's leaderboard on their admission page. Each student has a
roll number, given at onboarding, which links a sheet row to a student.

Status: **plan, awaiting go.** No code is changed until Shahriar says so.

## 1. Decisions (Shahriar, 14 and 15 Nov)

| # | Question | Decision |
|---|---|---|
| R1 | Marks from the file or calculated? | **Calculated** from the raw columns (right, wrong, negative per wrong, written). The preview flags every row where the file's own Total or Position differs. |
| R2 | All-zero rows (0 right, 0 wrong, 0 written) | **Count as 0** and share the last rank, as the sheet does. The student screens say "might be absent". |
| R3 | Pass mark | **Entered by the admin per exam** at upload. No pass mark means no pass/fail is shown. |
| R4 | Roll number | **Required at onboarding. Unique across all batches.** The onboarding form suggests the next free roll. |
| R5 | First roll of a batch | **Each batch has a starting roll** (for example 192001), set on the batch. Later students get the next free roll after the highest in the batch. |
| R6 | Leaderboard | **Per exam only.** Rank, roll, name and marks for everyone in the sheet. Roll numbers are visible to other students. |
| R7 | Exam type | **The admin picks it**: MCQ, MCQ + Written, or Written. The sheet must have that type's columns. |
| R8 | Unmatched rows | A roll the batch does not have **blocks the upload**. An expected student with no row is shown as **absent** (no marks, no rank). |
| R9 | Who does what | **Upload: any admin (staff and the Master Admin). Publish: the Master Admin only.** |
| R10 | Fixing a published result | Uploading again makes a **staged replacement**. Students keep the published one until the Master Admin publishes the new one. |
| R11 | Notifications | **In-app notice only**, to the batch's students, when a result is published or republished. |
| R12 | Closed admissions | A student whose admission was closed **keeps seeing results published before the closing**, and stays on those leaderboards. |
| R13 | Classes and exams by hand | **Staff and the Master Admin can add a class or an exam** to a batch (name, topic, date, time, instructor, kind). A hand-made exam is paid like any class: the Master Admin sets the instructor's rate or marks it unpaid, as today. |
| O1 | Pass rule (default accepted 15 Nov) | **Pass when the total is at least the pass mark.** A pass mark of 40 passes 40. |
| O2 | Roll format (default accepted) | **Digits only, 4 to 12 digits.** |
| O3 | Changing a roll after a result is published (default accepted) | **Allowed for the Master Admin.** Published rows keep the roll they were matched with; the next upload uses the new one. |

### Rules added in the 15 Nov review (they follow from the decisions; say if any is wrong)

| # | Rule | Why |
|---|---|---|
| A1 | **An exam with a staged or published result is never cancelled or moved by a routine upload.** The routine preview lists it as kept, with "has a result". A hand cancel of such an exam is refused with that reason. | Routine uploads replace classes from the upload day on. Without this rule, a re-upload would orphan a published result. |
| A2 | **Expected students** are those whose admission to the batch was onboarded on or before the exam date and was not closed before it. Only they can be "absent". A student who joined after the exam is neither listed nor absent. | Otherwise a student who joined late shows as absent for exams held before they joined. |
| A3 | A roll from a **closed admission of the same batch** is still matched, if it is in the sheet. A roll from another batch blocks the upload. | A student who sat the exam and left later still has a result. |
| A4 | **A result can be uploaded only for an exam dated today or earlier** (Dhaka date). A cancelled exam takes no upload. | Results come after the exam. |
| A5 | **Full marks and negative marking come from the sheet.** The "Number" column gives the full marks; the "Negative" or "Deducted" column gives the deduction per wrong answer. Each must be the same on every row, otherwise the upload is blocked. The preview shows both. | The sheets carry them per row; one exam has one value. |
| A6 | **A roll given at onboarding to a join-code student is released** if the admission goes back to review, is sent back, or is closed before the student joins. The same happens to the join code. | The roll belongs to the onboarding decision. A cancelled onboarding must not keep a number taken. |
| A7 | **The roll suggestion** is the highest roll in the batch plus one, or the batch's starting roll for the first student. It skips any roll already used anywhere. Rolls compare as numbers. With no starting roll and no students, there is no suggestion: the admin types one, and the dialog links to the batch form to set the starting roll. | |
| A8 | **Marks**: right and wrong are whole numbers. Negative, written and totals may have two decimals. A total may be below zero (MCQ with heavy negatives), and it then ranks below 0. A total above the full marks blocks the upload. | |

## 2. What the sample sheets show (`docs/offline-admission/*.xlsx`)

Both sheets put a title block (brand, batch name, exam name) above one header row, or two header rows when MCQ is split into Right and
Wrong. One row per roll follows. About 100 rows per sheet.

| Type | Columns read | Calculation |
|---|---|---|
| MCQ | Roll, Number (full marks), Right, Wrong, Negative per wrong | MCQ = Right − Wrong × Negative; Total = MCQ |
| MCQ + Written | Roll, Number, Right, Wrong, Deducted (per wrong), Written | MCQ = Right − Wrong × Deducted; Total = MCQ + Written |
| Written | Roll, Number, Written | Total = Written |

- **Columns read for checking only:** Total / Total marks obtained, Merit List / Position, and Result.
- **Rank** is competition ranking, the same as Excel's `RANK`: 1, 2, 2, 4. The leaderboard is sorted by rank, then roll.
- **Pass/fail**: rule O1.

### Reading rule
- The header row is the first row that has a cell starting with "Roll".
- Columns are found by header text, ignoring case and spaces. A second header row ("Right", "Wrong" under "MCQ") is read with the row
  above it. The order of the columns does not matter.
- Header names accepted:

| Column | Header text |
|---|---|
| Roll | Roll, Roll No |
| Full marks | Number, Full marks |
| Right | Right, Right Answer |
| Wrong | Wrong, Wrong Answer |
| Negative | Negative, Deducted, Deducted Mark |
| Written | Written |
| Total | Total, Total Number, Total marks |
| Position | Position, Merit List, Merit |
| Result | Result |

- Rows with an empty roll are skipped, and so is the title block above the header. Only the first sheet of the workbook is read.
- Cells are read as stored values. A formula cell gives its last calculated value, and that value is used only for the mismatch check.
- `.xlsx` only, up to 5 MB and 2,000 rows. An old `.xls` file gets "Save the file as .xlsx and upload it again".

## 3. Data (Flyway V96)

- `offline_admissions.roll_no` varchar(12), digits only (check constraint). It is **unique across the whole table** where not null,
  so no two students ever share one (rule A6 frees it before then if needed).
- `offline_batches.roll_start` varchar(12), digits only, optional.
- `offline_result_sheets`: one row per upload.
  - `class_id` (an EXAM class of the batch) and `batch_id`.
  - `exam_type` (MCQ, MCQ_WRITTEN or WRITTEN), `full_marks`, `negative`, `pass_mark` (nullable).
  - The stored file: name, type and size.
  - `status` STAGED, PUBLISHED or REPLACED.
  - `uploaded_by` / `uploaded_at` and `published_by` / `published_at`.
  - Partial unique indexes allow at most one PUBLISHED and at most one STAGED sheet per class.
- `offline_result_rows`: one row per student of the sheet.
  - `sheet_id`, `admission_id`, `roll_no`, `right_count`, `wrong_count`, `mcq`, `written`, `total`, `rank`, `passed`.
  - `file_total` and `file_rank`, kept for the mismatch flag.
  - `absent` (true for an expected student with no row; the marks are then null).
- **Permissions:** `OFFLINE_RESULT_UPLOAD` goes to the Master Admin and System Admin roles; `OFFLINE_RESULT_PUBLISH` to the Master
  Admin only. On the web these become `canUploadOfflineResult` and `canPublishOfflineResult`.
- **Notice kinds:** V96 widens the `student_notices` kind check with `OFFLINE_RESULT_PUBLISHED` and `OFFLINE_RESULT_UPDATED`.
- **Audit:** results uploaded, published and discarded; roll set and changed.

## 4. Roll numbers (API)

- Onboarding (`POST /api/v1/offline-admissions/{id}/onboard`) takes `{ "rollNo": "192001" }`.
- It refuses a missing, malformed or taken roll with a field error on `rollNo`. Two onboardings racing for one roll are caught by the
  unique index and answered as the same field error, never a 500.
- `GET /api/v1/offline-admissions/{id}/next-roll` answers `{ "suggestion": "192036" | null }` (rule A7).
- `PATCH /api/v1/offline-admissions/{id}/roll` (Master Admin) sets or changes the roll of an onboarded admission (O3, and students
  onboarded before this feature).
- The batch create and edit routes take `rollStart`. `BatchView` sends `rollStart` and `studentsWithoutRoll`.
- The admin detail, the queue row, the batch student list and the student's `AdmissionView` send `rollNo`.
- The admission queue search also matches the roll.

## 5. Results (API)

| Route | Who | What |
|---|---|---|
| `GET /api/v1/offline-batches/{id}/results` | staff | The batch's exam classes with result state and counts |
| `POST /api/v1/offline-classes/{id}/results/preview` (multipart: file, examType, passMark) | upload | Read the sheet, answer the preview, save nothing |
| `POST /api/v1/offline-classes/{id}/results` (same body) | upload | Read again and save as the staged sheet (replaces an earlier staged one) |
| `GET /api/v1/offline-classes/{id}/results` | staff | The published and staged sheets, each with its rows, flags and who acted |
| `GET /api/v1/offline-classes/{id}/results/{sheetId}/file` | staff | The uploaded file as it was |
| `DELETE /api/v1/offline-classes/{id}/results/staged` | upload | Discard the staged sheet |
| `POST /api/v1/offline-classes/{id}/results/publish` | publish | Publish the staged sheet; the old published one becomes REPLACED; notices go out |
| `GET /api/v1/me/admissions/{id}/results` | student | The published results of that admission's batch (rule R12/A2), each with the student's own row |
| `GET /api/v1/me/admissions/{id}/results/{classId}` | student | One exam's leaderboard |

### The preview answer
- The type, the full marks and the negative read from the sheet, plus the pass mark.
- Counts: rows, matched, absent, mismatches.
- Every row: roll, the matched student's name, the calculated marks and rank, pass/fail, and the file's total and position.
- `problems` (blocking), each with the row number, the roll and the reason.
- `warnings`: mismatch rows and absent students.

### Notices
- Publishing sends one in-app notice to each student whose admission to the batch is ONBOARDED at that moment. The key is the sheet, so
  a notice is never sent twice.
- A republish sends `OFFLINE_RESULT_UPDATED`.

### The student routes
- They take the admission id even for a student with one admission; the web always has it as `admission.id`.

## 6. Classes and exams by hand (API)

- `POST /api/v1/offline-batches/{id}/classes` already exists. It needs `OFFLINE_BATCH_ROUTINE`, which the Master Admin and System Admin
  already hold, so no permission change is needed.
- The only API work is rule A1: exams with results are kept by routine uploads and cannot be cancelled.

## 7. Screens

All new text is in English and natural Bangla, in the existing message files. Every screen works at phone width (360 px) with no
sideways page scroll, uses the existing components (`Tabs`, `SectionCard`, `ConfirmDialog`, `Pill`, `EmptyState`, `Skeleton`, the
form fields), and shows the API's field errors under their fields.

### 7.1 Staff: batch form (`batch-form-dialog.tsx`)
- A new field, **Starting roll number** (optional, digits), placed after the monthly fee.
- Help text: "The first student onboarded to this batch gets this roll. Later students get the next free one."
- A malformed or already-used number shows the API's field error.

### 7.2 Staff: Onboard dialog (`admission-dialogs.tsx`, `OnboardDialog`)
- The dialog already takes content, so it gets a **Roll number** input above the summary. It is filled with the suggestion from
  `next-roll` while the dialog opens, and shows a small skeleton until the suggestion arrives.
- Under the input: "Suggested: next after the highest roll in this batch." With no suggestion, it says "This batch has no starting
  roll yet" and links to the batch form.
- **Onboard** stays off until the roll is 4 to 12 digits. A taken roll shows the API's error under the field, and the dialog stays
  open.
- For a join-code student, the line under the field says the roll is kept only if the student joins (rule A6).

### 7.3 Staff: admission detail (`admission-detail.tsx`, `after-onboarding.tsx`)
- **Header:** after the name, a `Pill` "Roll 192001", or a warning `Pill` "No roll" for an onboarded admission without one.
- **Overview tab:** a "Roll number" row with **Change** for the Master Admin. Change opens a small dialog with the same input and
  checks; it explains that published results keep the old roll.

### 7.4 Staff: batch detail tabs (`batch-detail.tsx`)
- **A new tab, Results,** after Routines. It is kept in the URL as `?tab=results`.
- **Students tab:** a Roll column. Above the list, a warning line "3 students have no roll number. Results cannot be matched to them."
  links to the first of them.

### 7.5 Staff: Results tab (new `batch-results.tsx`)
- **A list of the batch's exam classes, newest first.** Each row shows the exam's date, subject and topic, instructor, and a state
  `Pill`:
  - "No result" (muted);
  - "Staged" (warn);
  - "Published" (ok), with the publish date;
  - "Published · new version staged" (warn).
- **Row actions** depend on the role and the state:
  - **Upload result**, or **Upload new version** on a published row (any admin). It is off for a future or cancelled exam, with the
    reason as a tooltip and as text on phones.
  - **Review**, which opens the sheet view (7.7).
  - **Publish**, for the Master Admin, on a staged row.
- **Empty state:** "This batch has no exams yet. Exams come from the routine, or add one on the Classes tab." with a button to the
  Classes tab.
- At phone width the rows become cards: date and title on top, the state pill and the actions below.

### 7.6 Staff: upload dialog (new `result-upload-dialog.tsx`)
**Step 1, Choose**
- Exam type: three radio cards (MCQ, MCQ + Written, Written), each naming the columns it needs.
- Pass mark: optional number. "Leave empty to show no pass or fail."
- File: `.xlsx`, chosen or dropped.
- **Check file** sends the preview.

**Step 2, Preview**
- **Summary line:** "Full marks 100 · Negative 0.25 per wrong · 49 rows · 47 matched · 2 absent · 3 differ from the file."
- **Problems first**, if any, in a danger box: row number, roll and reason (for example "Row 23, roll 192099: no student in this batch
  has this roll"). **Save** is off while there are problems, and **Choose another file** goes back to step 1 with the type and pass mark
  kept.
- **Warnings** in a warn box:
  - the rows that differ, as "Total 50.75 in the file, 50.50 calculated";
  - the absent students, by name and roll.
- **The table:** rank, roll, name, Right, Wrong, MCQ, Written, Total, pass/fail. Rows that differ are tinted warn, with the file's value
  beside the calculated one. Zero rows carry a muted "might be absent" tag.
- The table scrolls inside its own box with a sticky header. At phone width it becomes compact rows: roll and name, then the total and
  rank.
- **Save as staged** saves the sheet and closes, with the toast "Result saved. The Master Admin can publish it." For the Master Admin,
  a second button **Save and publish** saves and publishes in one step, after a confirm.

### 7.7 Staff: sheet view (new route `/staff/batches/[id]/results/[classId]`)
- **Header:** the exam's title and date, the state, who uploaded it and when, and who published it and when.
- **When a published and a staged version both exist,** two tabs, Published and Staged, so the Master Admin can compare them before
  publishing.
- **The same table as the preview,** with the mismatch flags kept, and a **Download file** link to the uploaded sheet.
- **Actions:**
  - **Publish** (Master Admin). Its confirm says "N students will be told in the app."
  - **Discard staged** (any admin). Its confirm says "The staged version is removed. The published one stays."
  - **Upload new version.**

### 7.8 Staff: Classes tab, Add class (`batch-classes.tsx`, `class-dialogs.tsx`)
- An **Add class** button at the top right of the Classes tab. It shows for `canUploadRoutine` on a batch that is not archived.
- It opens a dialog with the same fields and layout as the existing **Edit class** dialog:
  - kind (Lecture, Exam, Other);
  - date;
  - start and end time (the end defaults to start plus the batch's class length);
  - subject;
  - topic;
  - instructor (the batch's instructors, or "No instructor").
- The edit form is shared, not copied.
- On save, the toast "Class added" appears and the list refreshes on that month. A hand-made exam appears in the Results tab at once.
- **Cancel class** on an exam with a result is off, with the reason "This exam has a result" (rule A1).

### 7.9 Staff: routine preview (`routine-preview.tsx`)
- An exam kept because it has a result (rule A1) shows the `Pill` "Has a result · kept", and it is not counted as removed or moved.

### 7.10 Student: admission page (`my-admission-page.tsx`, `admission-overview.tsx`)
- **The admission card** shows "Roll 192001" beside the batch name once onboarded. The roll is copyable, and it is shown large enough to
  read out at the desk.
- **A new Results section** (`results-section.tsx`, with a new `results` section tint) goes after the Routine section, for ONBOARDED and
  CLOSED admissions.
  - The batch's published exams, newest first, as rows: exam title and date, the student's total out of full marks, rank (for example
    "5th of 47"), and a pass/fail `Pill` when there is a pass mark.
  - A zero result has a muted "might be absent" tag. A student with no row shows "Absent".
  - Each row opens the leaderboard.
  - **Empty state:** "No results yet. They appear here when the office publishes them."
- **The glance strip** gets "Last result: 41.75 / 50 · 3rd" when there is one.

### 7.11 Student: leaderboard (new route `/offline-activities/results/[admissionId]/[classId]`)
- **Header:** the exam title, its date and the batch name, with Back to Offline activities.
- **A "Your result" card first:** total out of full marks, rank of N, MCQ (right, wrong, deducted) and written parts, and pass/fail.
- **Then the list:** rank, roll, name, total, and pass/fail when there is one. It is sorted by rank, then roll.
  - The student's own row is highlighted, with `aria-current="true"`.
  - Absent students come last, with "Absent" and no rank.
  - Zero rows carry "might be absent".
- At phone width each row is one line: rank, name over roll, then the total on the right. The MCQ and written parts show only in "Your
  result".
- The page is open only to the admission's own student (the API answers 404 otherwise).

### 7.12 Notices (`notice-kind.ts`, `notices.ts`)
- `OFFLINE_RESULT_PUBLISHED` ("Exam result published" / "পরীক্ষার ফল প্রকাশিত হয়েছে") and `OFFLINE_RESULT_UPDATED` ("Exam result updated" /
  "পরীক্ষার ফল হালনাগাদ হয়েছে") open `/offline-activities#results`.

## 8. Stages

1. **Data and rolls:** V96; roll on the batch, onboarding, the suggestion and the roll routes; rule A6. Web: batch form, Onboard dialog,
   roll on the admission detail and student card, Students tab column.
2. **Add class and rule A1:** the web dialog; the routine upload keeps exams with results; the cancel refusal.
3. **Sheet reading and the preview:** the POI reader (`poi-ooxml`, a new dependency), the three types, rules A4, A5 and A8, the
   calculation and the ranking. Unit tests on both sample files.
4. **Staged, publish, replace and discard,** with the notices and rule A2.
5. **Staff Results tab, upload dialog and sheet view.**
6. **Student Results section, leaderboard and notices,** in English and Bangla.
7. **Tests and checks:** API integration tests and web tests on a new empty test database; the full API and web suites; then a browser
   check on a new dev database.

## 9. Tests

- **Reader:**
  - Both sample sheets parse.
  - The sample MCQ + Written sheet gives the same totals and positions as the file.
  - Two-row headers and reordered columns work.
  - An `.xls` file and a missing column are refused with clear messages.
- **Preview:**
  - An unknown roll, a duplicate roll, another batch's roll, marks above full, a non-number, and full marks or negative changing between
    rows each block.
  - Mismatches and absent students are warned.
  - A student who joined after the exam is not absent (A2). A closed student's roll is matched (A3).
  - A future or cancelled exam is refused (A4).
- **Ranking:** ties share a rank (1, 2, 2, 4); zero rows share the last rank; a negative total ranks below zero.
- **Publish:**
  - Only the Master Admin can publish; staff are refused.
  - A republish replaces the old sheet and sends the update notice once.
  - Discard removes only the staged sheet.
- **Student access:**
  - An onboarded student sees published results only.
  - A closed student sees only results published before the closing.
  - Another batch's student gets 404.
- **Roll:**
  - Onboarding without a roll, with a malformed roll, or with a taken roll is refused.
  - The suggestion follows A7.
  - A roll given with a join code is released on back to review (A6).
  - The Master Admin can change a roll; staff cannot.
- **Classes:**
  - Staff can add a class or an exam, and it appears in the pay screens and the Results tab.
  - A routine upload keeps an exam with a result, and cancelling it is refused (A1).
- **Web:**
  - The Onboard dialog's roll field (suggestion, off until valid, server error).
  - The upload dialog's steps (problems block Save, warnings show).
  - The Results tab states per role.
  - The student Results section and the leaderboard (own row highlighted, absent last, "might be absent").
  - The notice labels and links.

## 12. Build log

**Stage 1 (data and roll numbers), built 15 Nov.**
- Done (API):
  - V96 adds `offline_admissions.roll_no` and `offline_batches.roll_start`, both digits only, 4 to 12 long, with a unique index on rolls.
  - Onboarding requires a roll, checks it is not used by another admission, and returns a field error otherwise (`checkedRoll`).
  - `GET /api/v1/offline-admissions/{id}/next-roll` suggests the next free roll for the batch, skipping taken rolls (A7).
  - `PATCH /api/v1/offline-admissions/{id}/roll` lets the Master Admin change a roll once onboarded (O3); audited as `OFFLINE_ROLL_SET`.
  - Batch create and update take `rollStart`; it cannot change once any student in the batch has a roll.
  - A roll given with a join code is released on back-to-review, send-back or close, unless the student already joined (A6).
  - A student sees their roll only once onboarded or closed.
  - Tests: `OfflineOnboardingIT` (17 tests, including the four new roll tests) and `AuditLogIT` (the two new routes added to the route list) pass; the other 20 onboard call sites now send a roll.
- Done (web):
  - The Onboard dialog has a roll field, filled with the suggestion, and Onboard stays off until the roll is valid.
  - The batch form has a starting roll field.
  - The roll shows as a pill on the staff admission header and on the student's admission card.
  - `ConfirmDialog` gets a `confirmDisabled` option.
  - English and Bangla copy added.
  - Tests: the onboard dialog has two new roll tests; the full web suite passes (300 files, 2,031 tests).
- Not done in stage 1 (moved to the next stage):
  - The Change-roll dialog on the admission overview (7.3).
  - The Roll column and the "N students have no roll" warning on the batch Students tab (7.4).
  - The roll in the admission queue search.
  - A race between two onboardings for the same roll is caught by the unique index, but it surfaces as a server error rather than a field error. Fix by catching the constraint at onboarding.

**API stage (stages 1 gaps, 2 to 4, and the API part of 6 and 8), built 15 Nov.**
- Done (API):
  - Stage 1 gaps: the queue search matches the roll and the queue row carries `rollNo`. Two onboardings racing for one roll are answered with the
    field error on `rollNo` (the roll is flushed right after the check, so the unique index decides). The Master Admin's roll change uses the same.
  - V97 creates `offline_result_sheets` and `offline_result_rows` (partial unique indexes: one PUBLISHED and one STAGED per class), and re-creates
    `student_notices_kind_check` from its V94 list with `OFFLINE_RESULT_PUBLISHED` and `OFFLINE_RESULT_UPDATED` added.
  - `OFFLINE_RESULT_UPLOAD` (Master and System Admin) and `OFFLINE_RESULT_PUBLISH` (Master Admin) are registered like `OFFLINE_BATCH_ROUTINE`.
    The API's `/me` returns the role only, so the web names the flags in its role table (`guards.tsx`).
  - The `.xlsx` reader uses the JDK only (zip and namespace-aware DOM). It reads the first sheet, shared, inline and formula cells, two-row
    headers and any column order. An `.xls` file is refused with the save-again message. Limits: 5 MB, 2,000 rows.
  - Calculation and ranking follow rules R1, R2, A5 and A8, with the file's Total and Position compared (0.01 on totals).
  - Preview, save as staged, publish (REPLACED, notices), discard staged, file download, the batch exam list and the sheet view, under the
    contract's routes. Each change is audited (`OFFLINE_RESULT_UPLOADED`, `_PUBLISHED`, `_DISCARDED`).
  - Student routes: the admission's results and one exam's leaderboard. A closed admission sees, per exam, the latest sheet published before its
    close (R12). An exam shows only where the student has a row, so a later joiner is not listed (A2).
  - Rule A1: the routine's confirm and preview keep an exam with a sheet (`keptForResult: true` on the preview's settled entries). A PATCH of a
    class to `CANCELLED` is refused with `OFFLINE_CLASS_HAS_RESULT` when it has a sheet, and with a validation error otherwise, since no hand
    cancel exists yet.
  - Notices: `OFFLINE_RESULT_PUBLISHED` (first publish) and `OFFLINE_RESULT_UPDATED` (a new version), keyed by sheet and student, to ACTIVE
    onboarded students only.
  - Tests: `OfflineResultFileTest` (both samples, ties, zero rows, negative totals, two-row headers, the refusals), `OfflineRoutineMatcherTest`
    (A1), `OfflineResultsIT` (12 tests: preview, save, publish by role, republish, discard, student access, absent and late joiner, closed
    student, future exam, hand cancel, file). The existing offline suites, `AuditLogIT` and `ActivationIT` pass, and so do `OfflineRoutineIT`,
    `OfflineClassSettleIT`, `OfflineClassNoticesIT` and `OfflineInstructorDataIT`, which the routine and class changes touch.
- Not done in this stage:
  - A hand cancel of an exam without a sheet has no route; the refusal above is all that the API does for it.
  - The onboarding race is covered by the code path (the flush and the mapping), not by a test that races two requests.

**Stages 2 to 7, built 15 Nov (by parallel agents, integrated and tested by the lead).**
- API: V97 (results tables, student_notices kinds), permissions, the .xlsx reader (no new dependency), preview and save, publish, discard, download, the staff and student routes, notices, rules A1 to A5 and A8, roll search in the queue, the roll race as a field error, and the batch student roll.
- Web: the batch Results tab, the three-step upload dialog, the sheet view, Add class, Change roll, the Students roll column and warning, the routine "kept" mark, the student Results section, the leaderboard page, and the notice kinds.
- Tests: full API suite 1,246 tests passing; full web suite 310 files and 2,109 tests passing; type check and lint clean.
- Still open:
  - No browser check on a new dev database.
  - Bangla copy for the results screens needs a native review.
  - Problem and warning messages from the preview API are English.
  - The web permission flags come from the role table, not from the API permission names.
  - No cancel-class action exists on the web, so the A1 cancel refusal is API-only.
  - The race on a duplicate roll is covered by code, not by a test that races two requests.


## 13. Review fixes (15 Nov review of the built code)

A read-through of the results code (API services, the sheet parser and reader, the staff and student screens) found the issues below.
Each has where it is, why it happens, the fix, and the test that proves it. They are grouped by severity. **Status: plan, awaiting go.**

### 13.1 High

**F1. The upload dialog hides why a file was refused.**
- *Where:* `web/src/components/staff/offline/result-upload-dialog.tsx` (`checkFile`, `saveAsStaged`); API `OfflineResultFile.parse` and
  `OfflineResultService.save`.
- *Why:* structural refusals answer 400 with field errors on `file`. Those are: no Roll header, a missing column for the chosen type,
  over 2,000 rows, or not a real .xlsx. A save after a race answers 400 with the problems as field errors. The dialog passes every error
  to `offlineErrorText`, which turns a 400 into "Check the highlighted fields". No field is highlighted, so staff cannot tell what is
  wrong with the sheet.
- *Fix:*
  - When the error is an `ApiError` with `fieldErrors`, show each message:
    - a `file` error under the file input;
    - an `examType` error under the type cards;
    - a `passMark` error under the pass mark;
    - any other field in the form alert, as a list.
  - Keep `offlineErrorText` for errors without field errors.
  - When a save fails with problems (the data changed since the preview), go back to the preview step. Show those problems in the
    problems box, with "Check the file again".
- *Test (web):*
  - A preview that answers 400 with `fieldErrors: [{field: "file", message: "The sheet has no Written column..."}]` shows that message
    under the file input.
  - A save that answers 400 with two problems lists both and keeps Save off.

**F2. Concurrent saves or publishes give a 500.**
- *Where:* `OfflineResultService.save`, `publish` and `discardStaged`.
- *Why:* nothing locks the exam.
  - Two admins saving at the same moment both delete "no staged sheet" and both insert one, so the unique index
    `offline_result_sheets_staged_key` fails.
  - Two publishes race the same way on `offline_result_sheets_published_key`.
  - A publish that runs while a save replaces the staged sheet can publish the old sheet's rows, or find nothing.
- *Fix:*
  - Each of the three methods first takes a row lock on the exam class, through a new `OfflineClassRepository.findForUpdate`
    (`select ... from offline_classes where id = ? for update`). They then run one after another per exam.
  - A unique-index violation that still slips through answers 409 ("Someone else changed this result first. Reload and try again."),
    not a 500.
- *Test (API):*
  - Two saves in sequence: the second replaces the first, and exactly one STAGED sheet exists.
  - A unit test maps the index violation to 409.
  - A two-thread test is optional; the lock is the fix.

### 13.2 Medium

**F3. The notice reaches students who have no result in the sheet.**
- *Where:* `OfflineResultService.publish` (`onboardedStudents`).
- *Why:* every ONBOARDED student of the batch is notified. A student who joined after the exam (rule A2) has no row, so the exam is not in
  their Results section, and the notice leads nowhere. The publish confirm's "N students will be told" uses the batch's student count, so
  it is wrong for the same reason.
- *Fix:*
  - Notify only ONBOARDED students whose admission has a row (present or absent) in the sheet being published.
  - Add `notifyCount` to the sheet view's staged sheet: the matched or absent rows whose admission is ONBOARDED. The sheet view and the
    publish confirm show it.
  - The web confirm uses `notifyCount` instead of `batch.studentCount`.
- *Test (API):* a student onboarded after the exam date gets no notice; a student in the sheet does.
- *Test (web):* the confirm shows `notifyCount`.

**F4. The notice link does not reach the Results section, and the section's id repeats.**
- *Where:* `results-section.tsx` (`id="results"`), `my-admission-page.tsx`, `notice-kind.ts` (`/offline-activities#results`).
- *Why:*
  - The section renders after its data loads, so the browser has no `#results` element to scroll to when the page opens.
  - A student with two batches has two sections with `id="results"`. That is invalid HTML, and the jump goes to whichever renders first.
- *Fix:*
  - Give each section `id={"results-" + admission.id}`.
  - When the address hash is `#results`, the Offline activities page scrolls to the first results section once it has rendered (an
    effect that runs after the results query succeeds), then clears the hash.
  - Keep the notice link `/offline-activities#results`.
- *Test (web):*
  - With two admissions, the two sections have different ids.
  - With the hash `#results`, `scrollIntoView` is called on the first section after its data arrives (mock
    `Element.prototype.scrollIntoView`).

**F5. Warnings are listed twice in the preview.**
- *Where:* `result-upload-dialog.tsx` (`previewContent`), API `OfflineResultService.analyse` (`warnings`).
- *Why:* the API puts "N rows differ..." and one "Absent: name, roll X." line per absent student in `warnings`. The dialog also builds a
  line for each differing row and each absent row from `rows`, then prints the API's `warnings` as well. Every absent student appears
  twice, and the differing count appears beside the differing lines.
- *Fix:*
  - The API stops putting absent and mismatch lines in `warnings`, and keeps the list for any other warning.
  - The dialog builds those lines from `rows` only.
  - The differing lines also cover rank-only mismatches ("Position 5 in the file, 4 calculated").
- *Test (web):* a preview with one absent row and one mismatch shows each exactly once.
- *Test (API):* `warnings` is empty for that case.

**F6. A new upload silently replaces another admin's staged sheet.**
- *Where:* `result-upload-dialog.tsx`, `batch-results.tsx`.
- *Why:* rule R10 replaces the staged sheet, which is intended. But the person uploading is not told that a staged version exists, who
  uploaded it, or that it will be lost.
- *Fix:* when the exam's state is STAGED or PUBLISHED_WITH_STAGED, step 1 of the dialog shows a warn line: "A staged version uploaded by
  {name} on {date} will be replaced." The name and date come from the sheet view (`useResultSheet`). Save stays allowed.
- *Test (web):* opening Upload on a staged exam shows the line with the uploader's name.

**F7. An exam with a result can be moved to another date, including the future.**
- *Where:* `OfflineClassService.update`.
- *Why:* rule A1 stops routine uploads and cancels, but a hand edit can still change `heldOn`. The absent list (rule A2) and the "held,
  not future" check (rule A4) were judged on the old date, and students would see a result for an exam "on" a later date.
- *Fix:*
  - When the class has a STAGED or PUBLISHED sheet, refuse a change to `heldOn` with `OFFLINE_CLASS_HAS_RESULT` ("This exam has a
    result, so its date cannot change. Discard the staged result first, or keep the date."). Other fields (instructor, rate, times,
    topic) stay editable.
  - The web Edit class dialog shows the date read-only, with that reason, for an exam with a result.
  - Add a `hasResult` flag to the class view, so the batch classes list knows which classes have one.
- *Test (API):* changing the date of an exam with a published sheet answers 409; changing its topic works.
- *Test (web):* the date is read-only in the Edit dialog for such an exam.

**F8. A "not taken" exam can still get a result.**
- *Where:* `OfflineResultService.analyse`.
- *Why:* only CANCELLED is checked. An exam whose instructor pay was marked NOT_TAKEN, or is waiting for that approval, did not happen.
- *Fix:*
  - Add a blocking problem for status NOT_TAKEN and NOT_TAKEN_PENDING: "This exam is marked as not taken, so it takes no result."
  - The batch exam row sends `status`, `uploadBlockReason` gains `notTaken`, and the Results tab disables Upload with the same reason.
- *Test (API):* the preview of a NOT_TAKEN exam has the problem.
- *Test (web):* Upload is off, with the reason.

**F9. Rolls with leading zeros, or stored as numbers, do not match.**
- *Where:* `OfflineResultFile.roll`, `OfflineResultService.rollsOf`.
- *Why:* rolls are text of 4 to 12 digits and may start with 0 (for example 0123). Excel stores a typed 0123 as the number 123, so the
  sheet says 123 and no student matches. A roll typed as text keeps its zeros.
- *Fix:*
  - Match exactly first.
  - If there is no exact match and the sheet's roll is all digits, match the batch roll with the same numeric value, when exactly one
    batch roll has it. The row keeps the batch's roll text.
  - The preview shows a warning: "Row N: roll 123 matched 0123."
- *Test (unit):* a batch roll 0123 matches a sheet value "123", with the warning.

**F10. The .xlsx reader can be made to use too much memory.**
- *Where:* `OfflineResultReader.parts`.
- *Why:* every part of the zip is read into memory, up to 20 MB each, with no limit on the number of parts or on the total. A crafted
  5 MB file can expand to hundreds of MB. Only admins can upload, so the risk is low, but the reader also wastes memory on images and
  other parts it never uses.
- *Fix:*
  - Read only the parts the reader needs: `xl/workbook.xml`, `xl/_rels/workbook.xml.rels`, `xl/sharedStrings.xml`, and the first
    sheet's part. Skip all others without reading them.
  - Cap the total expanded bytes at 50 MB and the entries at 1,000. Over a cap, refuse with "The file is too large to read."
- *Test (unit):* a zip with many large dummy parts is refused; the two sample sheets still read.

### 13.3 Low

**F11. A pass mark above the full marks.**
- *Fix:* the preview adds a blocking problem when `passMark > fullMarks` ("The pass mark 60 is above the full marks 50."), shown in the
  problems box.
- *Test (unit):* pass mark 60 on the 50-mark sample is refused.

**F12. A published sheet can hold rows for students whose state changed after the save.**
- *Why:* rows are matched at save time. Between save and publish, a matched student may be closed or have their roll changed (O3).
  Publishing is still correct, because each row keeps its admission and roll, but staff do not see the change.
- *Fix:* at publish, compare the staged rows with the batch. If any matched admission is now CLOSED, or its roll differs from the row's,
  include a `changedSinceSave` list in the publish confirm (name, old roll, and new roll or "closed"). Publishing still goes ahead.
- *Test (API):* a matched student whose roll changes after the save appears in `changedSinceSave`.

**F13. The Add class instructor list is not the batch's.**
- *Where:* `add-class-dialog.tsx`.
- *Why:* it lists every active instructor. The plan (7.8) says the batch's instructors.
- *Fix:* list the batch's instructors (from the batch rates list) first, then "Other instructors" for the rest, plus "No instructor".
- *Test (web):* the batch's instructors come first.

**F14. A failed "Save and publish" leaves a staged sheet without saying so.**
- *Where:* `result-upload-dialog.tsx` (`saveAndPublish`).
- *Why:* when the save succeeds and the publish fails, the confirm shows the publish error, but the dialog stays on the preview as if
  nothing was saved. Pressing Save again uploads it a second time.
- *Fix:* when the save succeeds and the publish then fails, close the confirm and the dialog, with the toast "Saved as staged.
  Publishing did not work: {reason}. Publish it from the Results tab."
- *Test (web):* a successful save followed by a publish 409 shows that toast and closes the dialog.

**F15. The preview's problem and warning texts are English only.**
- *Why:* the API composes the sentences, so a Bangla screen shows English problem lines.
- *Fix:*
  - Each problem and warning gets a `code` and `params`, for example `ROLL_NOT_IN_BATCH {roll}`, `TOTAL_ABOVE_FULL {total, full}`,
    `NOT_A_NUMBER {column, value}`.
  - The web shows `staffOfflineResults.problems.<code>` in English or Bangla, and falls back to `message` for an unknown code.
  - The API keeps `message` for logs and older clients.
- *Test (web):* a Bangla render shows the Bangla line for `ROLL_NOT_IN_BATCH`.

**F16. No test races two onboardings for one roll.**
- *Fix:* add an API test that onboards two admissions with the same roll on two threads. It asserts one 200, and one 400 with the field
  error on `rollNo`.

### 13.4 Edge cases checked and found correct
- XML external entities are disabled in the reader (secure processing, no DOCTYPE).
- A file saved in a failed transaction is deleted (`commitReplacement` cleans up on rollback).
- A closed student sees only sheets published before the close, including one that was later replaced (rule R12).
- Absent rows never get a rank, a total or pass/fail (a database check enforces it).
- Students of another batch get 404 on the student routes.
- Two-row headers and reordered columns read correctly; formula cells use their cached value; ties rank 1, 2, 2, 4.

### 13.5 Order of work
1. F1 and F2 (High).
2. F3 to F10.
3. F11 to F16.
4. Run the targeted API tests (`OfflineResultsIT`, `OfflineResultFileTest`, `OfflineOnboardingIT`, `OfflineClassSettleIT`,
   `OfflineRoutineIT`, `AuditLogIT`) and the web tests for staff results, student results and batch pay. Then run the full API and web
   suites on a fresh test database.
5. Check in a browser on a new dev database: upload both sample sheets, publish, view the result as a student, and follow the notice
   link.

**Review fixes applied (section 13), 15 Nov.**
- Done: F1 to F16. The API side and the web side were built in parallel against the same contract (results-api-contract.md, updated with codes and params).
- Translation check: the web placeholders now match the API parameter names (label, value, heldOn, pass, full, roll, matched). Four codes added to the web (FULL_MARKS_MISSING, NEGATIVE_MISSING, BELOW_ZERO, COUNT_TOO_LARGE). FILE_REFUSED and TOO_MANY_ROWS are field errors on the file and show the API English message.
- Tests: full API suite 1,260 tests passing. Full web suite 315 files and 2,136 tests passing. Type check and lint clean.
- Still open: a browser check on a new dev database; a native Bangla review of the new strings; a direct unit test for the index-violation-to-409 mapping.

## 14. WhatsApp group links (15 Nov)

Admins set a WhatsApp group or channel link for each batch and each package. Students get it only when they are onboarded to the batch, or hold live access to a published package.

- **Accepted links:** only a group invite (https://chat.whatsapp.com/...) or a channel (https://whatsapp.com/channel/...). Anything else is refused, so a link cannot send a student elsewhere (WhatsAppLinks).
- **Batches:** a WhatsApp field on the batch form. It is set with the batch and changed with it. The batch's public list never carries it. An onboarded student's admission carries it.
- **Packages:** a WhatsApp card on the package editor, for the admins who manage the cover. It is saved on its own, outside the revision and publish steps. Students get it only while the package is published and their access is live.
- **Student:** a "Join the WhatsApp group" button opens the link in a new tab. It appears on the admission card and on the owned package card.
- **Migration:** V98 adds whatsapp_url to offline_batches and live_exam_modules.
- **Tests:** API StudentAccessIT (package link: staff only to set, a draft package withholds it, a published one gives it) and OfflineOnboardingIT (batch link: refused if not a WhatsApp link, not on the public list, given after onboarding). Web tests/whatsapp (set, refuse, batch form, join button).
- **Full suites:** API 1,262 tests passing; web 316 files and 2,141 tests passing.
- **Not done:** a browser check on a dev database; Bangla copy needs a native review.

## Browser check of result updates (15 Nov, after the build)
Run on coaching_center_review with the client's two real sheets (flow 4, 34/34), screens reviewed.
- Exam-01 (MCQ + Written, 50 students, rolls 191011-191060): reads 50 of 50; its one "differs" flag is real (roll 191011 is
  position 16 in the file, 8 by its zero total). Staged by the System Admin, published by the Master Admin; the student sees 50.75.
- Update (R10): a corrected sheet is a staged replacement and the student keeps 50.75; Discard staged keeps the published one; the
  corrected one published gives 58.75, rank 1, and the "Exam result updated" notice.
- The Constitution sheet as the client made it is refused: rolls 191017, 191025, 191034, 191040 and 191054 belong to another batch
  (rule A3). Without those rows it saves and publishes; Rahim (192001) sees it and the 100-row leaderboard on a phone.
- The client ranks ties as 1, 2, 2, 4, the same as the reader.

Fixed during the check:
- The warning that another admin's staged sheet will be replaced showed only before the file was checked; it now shows beside Save too.
- Offline result notices were filed under the Exams tab; they are now under Results (NoticeGroup, with its test).
- Every notice link said "Open exam" (results, admission and fee notices too); each now says where it goes (View result, Open offline
  activities, View payment, Enter the join code).
