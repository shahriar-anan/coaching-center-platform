# MCQ upload from a Word file: plan

Status: planned, not started. Owner: Shahriar Anan. The build follows this plan; a change to it is agreed first.

Sample file: `docs/Client Request/MCQ template/19th BJS Final Model Test 01 Ans.docx`.

## 1. Goal

Staff upload the client's Word file on an MCQ exam. The file's questions, options and correct answers replace the exam's
questions. When a student starts the exam, the top of the paper reads like the file's header, and the options are labelled
ক খ গ ঘ.

## 2. Decisions

| ID | Decision | Source |
|---|---|---|
| U1 | The file's header block is shown at the top of the sitting question view, as written in the file. | Shahriar |
| U2 | The exam's own titles (`titleEn`, `titleBn`) are not changed by an upload. | Shahriar |
| U3 | An upload replaces the exam's questions. It does not add to them. | Shahriar |
| U4 | If the exam already has questions, staff must confirm the replacement in a prompt that states both counts. | Shahriar |
| U5 | The correct answer is the bold option. No bold option, or more than one, is an error for that question. | Proposed |
| U6 | Marks are 1 per question. If the total differs from the header's full marks, the preview warns. | Proposed |
| U7 | The exam's duration is set from the header's time. | Proposed |
| U8 | Set code is shown because it is part of the header. It has no field of its own. | Proposed |
| U9 | An upload sets the paper language to Bangla, so option letters are ক খ গ ঘ in any interface language. | Proposed |
| U10 | Only `.docx` is accepted. A `.doc` file is refused with a message to save it as `.docx`. | Proposed |
| U11 | Question and option text is plain text, so underlines in the file are not kept. | Proposed |
| U12 | The paste tool stays. | Proposed |

## 3. What the sample file shows

- A header of five lines: `SYMAM LAW- সাইমাম ল’`, the exam name, `(ফাইনাল মডেল টেস্ট)`, full marks and time on one line, and test
  name and set code on one line.
- 100 questions numbered ১ to ১০০. Each has 4 options in ক খ গ ঘ order and exactly one bold option. No question text is bold.
- One to four options share a line, split by spaces or tabs.
- No tables, images, equations or explanations. Four underlined text runs.

These come from a throwaway parser run on this one file. Other files from the client may differ, so every check in section 4
reports a problem rather than guessing.

## 4. Reading the file (API)

New class `McqDocxParser` in `com.coachingcenter.api.exams`. No new library: a `.docx` is zipped XML.

- **Safety:** read only `word/document.xml`, cap its unpacked size, and turn off DTDs and external entities in the XML parser.
  A file that is not a valid `.docx` gets `QUESTION_FILE_INVALID` (400).
- **Text:** read each paragraph's runs in order. Tabs and line breaks count as spaces, and deleted tracked text is skipped.
- **Bold:** a run is bold when `w:b` or `w:bCs` is on. Bold set only through a Word style is not read in this version.
- **Header:** everything before question 1. A gap of two or more spaces splits a line into a left and a right part, as in the
  file. Full marks and time are read from the header for U6 and U7, after Unicode normalization, because `য়` has two encodings.
- **Question:** a paragraph starting with a Bangla or Latin number, then `.` or `।`.
- **Option:** `ক)` to `চ)` at the start of a line or after a space. Several on one line are split.
- **Correct option:** at least half of its non-space characters are bold. Less than half, but some, is a problem.

Blocking problems, each with its question number: no questions, numbers out of order, empty question, fewer than 2 options,
empty option, options out of order, option on the question line, stray text after the options, no bold option, several bold
options, partly bold option, or a duration that does not fit the exam's window. Warning only: total marks differ from the header.

## 5. Storing the header

- **V68:** `paper_header TEXT NULL` on `live_exams`, one header line per text line, with a tab between left and right parts.
- `ExamState` carries it, so a published exam stages it. A revision staged before the field existed keeps the exam's header, the
  same way the instructor field works.
- `McqAttemptSnapshot` stores it, so a student who already started keeps the header of their own paper.
- Copying an exam copies the header. A manual question edit leaves it as it is.
- Staff have no separate editor for it in this version. A new upload replaces it.

## 6. API routes

Both need `EXAM_MANAGE`, take the file as multipart, and work on MCQ exams only.

- `POST /api/v1/live-exams/{id}/questions/import/preview`: parses the file and saves nothing. Returns the header, duration, full
  marks, total marks, questions with their correct options, problems, warnings and the current question count.
- `POST /api/v1/live-exams/{id}/questions/import`: parses the file again, so the saved result comes from the file and not from the
  browser. Any blocking problem refuses the whole file with `QUESTION_FILE_HAS_PROBLEMS` (409), and nothing is saved. Otherwise it
  replaces everything in one transaction.
  - **Draft, or withdrawn and never published:** soft-deletes the old questions and options and writes the new ones.
  - **Published, or withdrawn after publishing:** stages the change for the Master Admin. Students who started keep their paper.

## 7. Web

- **Upload (`staff/questions`):** an "Upload Word file" button on the Questions page, for staff who can author. The paste tool stays.
- **Preview dialog:** the header as students will see it, question and mark totals, every question with its correct option
  highlighted, then problems and warnings by question number. Saving is disabled while a blocking problem exists.
- **Prompt (U4):** with existing questions, the button opens a confirmation: "This replaces the 40 current questions with 100." On a
  published exam it adds that the change waits for the Master Admin.
- **Change list:** a full replacement shows a summary, not 100 removed and 100 added rows.
- **Sitting (`paper-head.tsx`):** with a header, it shows those lines in place of the title and the time and marks line, with
  left and right parts on one row. The exam title stays available to screen readers. Negative marks, the student line and the
  rules stay. Without a header, the paper looks as it does today.
- **Strings:** English and Bangla. The Bangla is listed for Shahriar to check.

## 8. Build order

1. Parser, with its unit tests. Check it on the sample file: 100 questions, 4 options each, 1 correct each, 0 problems.
2. V68, entity, `ExamState`, snapshot and copy.
3. The two routes, with draft and staged replacement.
4. Student API field and the sitting header.
5. Upload dialog, confirmation prompt and change-list summary.
6. Tests, `current-api.md`, and a short guide for the client on formatting the file.

## 9. Tests

- **Parser:** the sample's shape in a small generated fixture, and one case per blocking problem. The client's real exam is not
  copied into the test folder.
- **API:** draft replace, staged replace, a started student keeping the old paper and header, a refused file saving nothing, and
  permissions.
- **Web:** the preview, blocked saving, the prompt's counts, and the sitting header with and without header lines.

Suites run only when Shahriar asks. V68 reaches `coaching_center_sample` only with his go, and the dev API needs a restart for it.

## 10. Out of scope

- Explanations, images, equations and tables in the file.
- Underlines and other text formatting.
- Multiple-answer questions from a file.
- Written exams.
- The header on the preview and result pages.
