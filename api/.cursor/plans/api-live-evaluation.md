# api-live-evaluation

Stage 5. Start only after `api-live-sitting` exits. Spec Sections 7 and 8. Tests: `api/.cursor/tests/api-live-evaluation.md`. Rule: `api/.cursor/rules/api-live-evaluation.mdc`.

Adding `INSTRUCTOR` reuses the System Admin create-and-activate path. The new role constant may live next to the existing role enum. Do not redesign activation or passwords.

## Slice 1 — Instructor and written marks

- A Master Admin creates an instructor with name, phone, and email, and no password. The response includes a one-time activation code. The instructor activates with phone, code, and a password they choose.
- A Master Admin assigns one instructor to a module. A second assignment to the same module is rejected. A System Admin receives `403` on assign and on evaluate.
- The instructor can list and open written submissions for assigned modules only. Another module returns `403`. The Master Admin can open any module.
- A no-submit row is visible and cannot be marked.
- A mark saves marks obtained from 0 through the exam maximum, an optional comment, and one evaluated PDF under the 80 MB compressed-PDF rule. The student then sees the marks, pass or fail, the comment when present, and the evaluated PDF. The student payload has no evaluator id or name.
- The staff payload shows who saved the latest evaluation.
- A later save by that instructor or the Master Admin replaces the marks, comment, and PDF, deletes the previous evaluated file, and recalculates pass or fail and rank.
- The MCQ live leaderboard is empty until five minutes after the live window, then lists live participants only. A retake score is absent from it. Rank is highest score first, ties share a rank, and the next rank skips. The board shows name, rank, score, maximum, and pass or fail. It does not show a phone number or an evaluator.
- A written live board includes no-submit zeros from the deadline. A submitted PDF appears on it when the mark is saved, and moves when the mark is replaced.

## Exit

One instructor is assigned to a module and can mark its written papers. The student sees the mark without the evaluator. A second save changes the rank. The MCQ board opens five minutes after the window and excludes the retake.

Slice 1 of this exit has passed. Slice 2 below starts after `api-live-sitting` slice 2 exits.

## Slice 2 — Assigned modules, answer PDF, deadline rows

- An instructor listing modules receives only the modules assigned to that instructor. A module with no assignment is absent.
- The assigned instructor, and the Master Admin, can download the student's answer PDF bytes. A System Admin, another instructor, and a student cannot.
- A System Admin can read the submission, including the evaluator name, and a mark from that account is still rejected.
- A saved comment is on the student result. A blank comment stays absent.
- A written retake is marked the same way as a live paper and does not appear on the live leaderboard.
- Opening the submission list after the deadline posts papers that the student never submitted. A missing PDF is failed to submit and cannot be marked. A stored PDF is submitted and can be marked.

## Slice 2 exit

The instructor sees assigned modules only and can download the answer PDF. A System Admin can read the evaluator and cannot mark. A comment reaches the student. A written retake stays off the live board. The submission list shows the deadline post.
