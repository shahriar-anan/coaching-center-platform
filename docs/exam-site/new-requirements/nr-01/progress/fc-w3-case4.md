# NR-01 final check, W3, case 4: expired access

**Outcome:** new test, green. No production change needed.

**Test:** `api/src/test/java/com/coachingcenter/api/exams/ExpiredAccessIT.java`, 1 test:
`anEndedAccessRefusesANewAttemptAndThePaperButKeepsTheResultsAndTheMarkedPdf`.

What it asserts, over HTTP, with exams granted before the window and `accessDays` 30:
- Retake on the MCQ and on the written exam (with an unused retake grant) gives `ACCESS_EXPIRED` (409).
- A student who holds a grant but never sat cannot take the MCQ (`ACCESS_EXPIRED`).
- `/result` and `/attempts` stay 200 with the student's data; the written marked PDF (`/evaluated-pdf`) stays readable after release.
- Student `/question-pdf` is 404 for both exams once the sitting is over.
- `/me/live-exam-access` shows `expired: true` and `endsAt` 2026-11-19T06:40Z; before expiry it is false.

**Already covered (for reference):** `AccessExpiryIT` covers the access rules at service level (`theAccessListsShowWhenAccessEndsAndHideFreeExams`, `thirtyDaysBoughtBeforeTheWindowRunsFromTheWindowEnd`). `AttemptsIT.aStudentWhoseAccessHasEndedCannotStartAnAttempt` covers the first take only. This test adds the HTTP-level retake, paper and read rules.

**Test-setup notes (not code bugs):** `accessDays` is a commercial field and needs `EXAM_PRICE` (Master Admin only), so the exams are created with the master token. Student uploads use real PDFs, because a hand-made stub fails the server's PDF compression.
