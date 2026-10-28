# NR-01 agent D: web impact

For the later UI plan. Nothing here changes `web/` in this plan.

- **Answer control:** a radio circle on the left of each option. Multi-answer questions (`questions[].multipleAnswer`) show "select all that apply" and a confirm button that sends `PUT …/answers/{questionId}` with `confirm: true`.
- **Answers after submit:** refused with ANSWER_ALREADY_SUBMITTED. Hide the answer controls once `submittedAt` is set.
- **Attempts list:** `GET …/attempts`, one row per attempt with `attemptNo` and `attemptKind`.
- **Retake button:** shown when `retakeAvailable` is true. MCQ: after any finished attempt while access holds. Written: only with an unused grant (`retakeGrant`).
- **Result page:** the key and explanations appear at submit, even during the window. Show `position`: CURRENT ("rank N of M live"), BOARD, RELATIVE ("your score would have ranked #N of M live participants"), or belowPass ("Below pass mark, not ranked"). Written `position` appears only after release.
- **Leaderboard:** during the live window a student sees `rows: []` and their own `me`. Staff see `belowPass` counts.
- **Staff retake grant:** a grant/revoke control on written results rows (`retakeGrant {id, used}`).
- **Instructor on the exam:** a per-exam instructor control (`POST/DELETE …/live-exams/{id}/instructor`).
- **Split counts:** results and participation show live, archived, retake and practice counts.

Click-through for the owner on staging: answer radio circles; multi-answer confirm; attempts list; retake button after a submit; result page with key and position text; empty leaderboard during the window.
