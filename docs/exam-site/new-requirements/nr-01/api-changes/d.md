# NR-01 agent D: API changes

| Method | Route | Change | Who | Notes |
|---|---|---|---|---|
| POST | /api/v1/live-exams/{id}/take | changed | Student | Kind and deadline from ExamWindow; `attemptKind`, `attemptNo` added; ACCESS_EXPIRED; SITTING_NOT_OPEN for upcoming |
| POST | /api/v1/live-exams/{id}/retake | changed | Student | MCQ unlimited; written needs a grant; ATTEMPT_IN_PROGRESS; `attemptNo` |
| PUT | /api/v1/live-exams/{id}/answers | changed | Student | `optionIds` accepted with old `optionId`; ANSWER_LOCKED; refused after submit (ANSWER_ALREADY_SUBMITTED) |
| PUT | /api/v1/live-exams/{id}/answers/{questionId} | new | Student | `{optionIds, confirm?}` |
| POST | /api/v1/live-exams/{id}/submit | changed | Student | MCQ result, key and position at submit |
| GET | /api/v1/live-exams/{id}/result | changed | Student | `?attempt=`; key shown at submit; `position`; `retakeGrant` |
| GET | /api/v1/live-exams/{id}/attempts | new | Student | Own attempts |
| GET | /api/v1/live-exams/{id}/leaderboard | changed | Student, staff | Board attempts only; pass filter (D24); rows empty during the window for students (D12) |
| POST | /api/v1/live-exams/{id}/retake-grants | new | Master Admin, System Admin | `{studentId, reason?}` |
| DELETE | /api/v1/live-exams/{id}/retake-grants/{grantId} | new | Master Admin, System Admin | Revoke unused |
| POST | /api/v1/live-exams/{id}/instructor | new | Master Admin | `{instructorId}` |
| DELETE | /api/v1/live-exams/{id}/instructor | new | Master Admin | |
| POST | /api/v1/live-exam-modules/{moduleId}/instructor | changed | Master Admin | `applyToExams` (default true) |
| DELETE | /api/v1/live-exam-modules/{moduleId}/instructor | changed | Master Admin | `applyToExams` query param |
| GET | /api/v1/live-exam-participants/{id}/question-pdf | new | Staff who read the exam | The paper the participant sat (E29) |
| GET | /api/v1/live-exams/{id}/question-pdf | changed | Staff, student | `?participantId=` |
| GET | /api/v1/live-exams/{id}/evaluated-pdf | changed | Student | `?attempt=` |
| GET | /api/v1/live-exams/{id}/results | changed | Staff | `kind` keeps legacy values, adds ARCHIVED and PRACTICE; `attemptKind` filter; rows carry `attemptNo` and `retakeGrant`; summary adds `live`, `archived`, `retake`, `practice` |
| GET | /api/v1/live-exam-participation | changed | Staff | Counts split per kind; instructor scope by exam |
| GET | /api/v1/live-exam-submissions | changed | Staff | Instructor scope by exam; rows carry `attemptNo` |
| GET | /api/v1/live-exam-modules/{moduleId}/submissions | changed | Staff | Rows carry `attemptNo` and `retakeGrant` |

Compatibility (contracts section 7): no field removed; legacy `kind` (LIVE or RETAKE) kept on existing rows; `myStatus` keeps its nine values; `myStatusDetail` is new.
