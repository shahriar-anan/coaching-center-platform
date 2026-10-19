# api-live-evaluation tests

PostgreSQL only. No manual check.

Shared setup: a module with one instructor assignment, one written submission with a stored PDF, one failed-to-submit row, and one MCQ exam whose window has ended.

## LiveEvaluationIT

- Creating an instructor accepts name, phone, and email, rejects a password field, and returns an activation code once. Activate with phone, code, and a new password signs the instructor in.
- Assigning a second instructor to the same module is rejected. A System Admin assign and a System Admin mark both return `403`.
- The instructor can open the assigned module's submission and cannot open another module's submission. The Master Admin can open both.
- The failed-to-submit row is listed and a mark on it is rejected.
- Saving marks 7 against a maximum of 10, with no comment, and an evaluated PDF lets the student read 7, the pass or fail result, and the PDF. The student body has no evaluator. The staff body names the instructor.
- Saving again with marks 4 and a new PDF deletes the previous evaluated file. The student sees 4. Rank uses the new mark.
- The MCQ leaderboard requested one minute after the window is empty. The same request six minutes after the window lists the live participant and omits a retake score for that exam. Two equal scores share a rank and the next rank skips.

Slice 1 of these cases passed. Slice 2 starts after `api-live-sitting` slice 2 exits.

## Slice 2

- The instructor's module list contains the assigned module and omits a module that has no assignment.
- The assigned instructor downloads the student's answer PDF. The Master Admin downloads it. A System Admin, an instructor assigned elsewhere, and a student are rejected.
- A System Admin read of the submission includes the evaluator name. A mark from that account returns `403`.
- A mark saved with a comment returns that comment on the student result. The student body still has no evaluator.
- A written retake with a stored PDF can be marked. That retake is absent from the live leaderboard. A live no-submit zero on the same exam remains on the board.
- After the written deadline, the submission list shows failed to submit for a participant who stored no PDF and never called submit, and a mark on that row is rejected. A participant who stored a PDF and never called submit is submitted and can be marked.
