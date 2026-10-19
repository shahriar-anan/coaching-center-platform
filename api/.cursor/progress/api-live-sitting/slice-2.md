# Slice 2 — Deadline post, question PDF, result after removal

Done. An early MCQ submit returns the score of the answers saved then and still accepts a later save. At the deadline the posted score is that later selection, and the review appears then. A retake follows the same rule on its personal timer.

A read after the deadline posts a paper the student never submitted. Saved MCQ answers are scored. A blank MCQ is score 0, fail, and `SUBMITTED`. A written exam with no stored PDF becomes `FAILED_TO_SUBMIT`. A written PDF stored before the deadline becomes `SUBMITTED`.

The question PDF bytes download only for that participant during the sitting. Before Take exam, for another student, and after the sitting deadline, the download is rejected. A retake downloads the same locked file until its timer ends.

Removing the roster row after Take exam leaves the participant, and that student still receives the result.

`mvn test -Dtest=LiveSittingIT` on PostgreSQL 17.9, database `coaching_center_test`. LiveSittingIT: 11 tests, 0 failures.

Next: `api-live-evaluation` slice 2.
