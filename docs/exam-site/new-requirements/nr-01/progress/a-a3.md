# A3: question paper versions (agent A)

Delivered in the same commit as A4. `ExamRevisionIT` covers both. See [a-a4.md](a-a4.md) for the run, the results and the changes to existing tests.

## What changed

- `uploadQuestionPdf` creates a new `live_exam_question_papers` row (version n+1) with `created_by` set to the acting user.
- On a draft exam, `live_exams.question_paper_id` and the legacy columns are updated at once.
- On a published exam, the new version becomes the revision's `paperId`. The published pointer and the legacy columns move only at publish.
- A sitting keeps the paper version it started with (`live_exam_participants.question_paper_id`).
- No paper version is ever deleted.

## Test

`ExamRevisionIT.aReplacedPaperReachesOnlyTheSittingsThatStartAfterThePublish`: a written student starts before the publish, a second one before the publish sits version 1, and a third after it sits version 2. The first participant keeps version 1, and version 1 still exists. Passed in the run recorded in a-a4.md.
