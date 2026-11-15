# Written answer scripts: draft on the server, submit, auto-submit (15 Nov 2026)

## Why
The answer script a student chooses during a written sitting (live or practice) stays in their browser until they press Submit. The
upload is the submission. "Taking the current file when time runs out" is done by the browser, a little before the deadline, only if
the page is still open. A closed tab, a sleeping phone or a network drop loses the script, and the student is marked "failed to
submit". See the analysis of 15 Nov for the details (points 1 to 5).

## The flow we want (Shahriar, 15 Nov)
1. During the sitting the student uploads the PDF. It is saved **on the server as a draft**.
2. Until they submit, and while time is left, **Replace** uploads another PDF, and it replaces the draft.
3. **Submit** makes the current draft the answer script. After that nothing can replace it.
4. When the time is over, **the server** submits the current draft by itself. With no draft, the sitting stays "failed to submit", as
   today.

## API
- **V99** adds draft columns to `live_exam_participants`: `draft_stored_file_name`, `draft_original_file_name`, `draft_byte_size`,
  `draft_uploaded_at`.
- **`PUT /api/v1/live-exams/{id}/answer-draft`** (multipart `file`, student):
  - It saves or replaces the draft of the student's open written sitting, and answers the draft (`fileName`, `byteSize`,
    `uploadedAt`).
  - It is refused after submit (`ANSWER_ALREADY_SUBMITTED`) or after the deadline (`DEADLINE_PASSED`).
  - The deadline is checked **when the request arrives**. Compressing a big PDF does not make an in-time upload late (fixes point 4).
  - The previous draft file is removed once the new one is saved.
- **`GET /api/v1/live-exams/{id}/answer-draft`** (student): the draft's bytes, so the student can check it. 404 when there is none.
- **`POST /api/v1/live-exams/{id}/submit`** for a written sitting before the deadline:
  - It promotes the draft to the answer script and posts the sitting.
  - With no draft and no script it answers 409 `NO_ANSWER_SCRIPT`.
  - A second submit answers the posted result, as today.
- **At the deadline,** `post()` (run by the deadline job, `settleParticipant` and `finalizeDue`) promotes a waiting draft before it
  decides "failed to submit". The script is then whatever was last uploaded (fixes points 1, 2 and 3).
- **The student exam view** gains `answerDraft` (`fileName`, `byteSize`, `uploadedAt`, or null), so a reload, or another device,
  shows the server's draft.
- **`POST /api/v1/live-exams/{id}/answer-pdf`** (the old upload-and-submit in one step) keeps working the same way for older clients.
  It saves the file as the draft and submits it, with the same in-time deadline check.
- **Marking is unchanged.** It reads the answer script, which the promotion fills in.

## Web (`written-sitting.tsx`)
- **Choosing a PDF uploads it as the draft** at once, with progress. It shows "Uploaded {time} Â· not submitted yet" and a View
  button that reads the draft from the server.
- **Replace** (the picker's button once a draft exists) uploads another PDF over it.
- **Submit**: a confirm dialog, then `POST /submit`, then the "submitted" screen.
- **The browser no longer keeps the file** and no longer sends it by itself before the deadline: the server submits the draft. A
  failed draft upload keeps the chosen file on screen with "Try again".
- **When the countdown ends,** the page goes to the result with "time up" (with a script, if a draft or script exists).
- **The copy says what happens:**
  - "Upload your answer script as one PDF. You can replace it until you submit or the time ends."
  - "When the time ends, the last file you uploaded is submitted for you."
  - "After you submit, the script cannot be changed."

## Tests
- **API (`LiveSittingIT`):**
  - A draft upload is not a submission. Replacing the draft keeps one draft file.
  - Submit promotes the draft; an upload after submit is refused.
  - At the deadline a waiting draft is submitted (SUBMITTED, not FAILED_TO_SUBMIT).
  - With no draft the sitting is FAILED_TO_SUBMIT.
  - Submit with no draft answers 409.
  - A draft upload after the deadline is refused.
- **Web:**
  - Choosing a file uploads the draft.
  - Replace uploads again.
  - Submit posts the submit and goes on.
  - A failed upload offers Try again.
  - A draft from the server shows after a reload.
- **Then the full API and web suites,** on a fresh test database.

## Build log
- 15 Nov: built as planned.
  - API: V99 draft columns; PUT/GET answer-draft; Submit promotes the draft (NO_ANSWER_SCRIPT when nothing is uploaded); post() promotes a waiting draft at the deadline; the deadline is checked when the request arrives; the sitting row is locked for upload, submit and settle.
  - Web: upload saves a draft at once; Replace; Try again on a failed upload; Submit with a confirm; the browser no longer keeps the file or sends it by itself.
- Tests: LiveSittingIT 41/41 (three new draft tests; the old "submit with nothing returns IN_PROGRESS" assertion changed to 409); the seven written-sitting web test files rewritten; full web suite passing.
- Browser check on a fresh dev database (14/14):
  - upload is a draft; Replace swaps it; a reload shows the server's draft; View opens it;
  - Submit makes it final, and a later Replace is refused;
  - a page closed after upload is submitted by the server at the deadline;
  - no upload ends as failed to submit.
  - Separately, with no API read at all, the deadline job alone turned IN_PROGRESS + draft into SUBMITTED + script.
- Fixed during the check: the raw key common.file.uploadedAt under the file name (added in English and Bangla).