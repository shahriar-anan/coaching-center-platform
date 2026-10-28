# FC-W1 case 3: a change published mid-sitting

**Class:** `api/src/test/java/com/coachingcenter/api/exams/MidSittingIT.java`
**Run:** `tools/nr01-mvn.ps1 -Agent W1 -Tests "MidSittingIT"`: 1 test, 0 failures, 0 errors, 0 skipped.

## What is covered

One test, `aChangePublishedMidSittingKeepsTheDeadlineShowsTheNewWordingAndScoresTheOpenPaperAtSubmit`. The MCQ exam is standalone, window 10:00 to 12:00 Dhaka, duration 40, three questions (2, 1 and 1 marks).

1. S2 starts at 10:05 and answers A everywhere, then submits at 10:08, before the change.
2. S1 starts at 10:10: deadline 10:50.
3. At 10:20 the Master Admin changes the duration to 60 and rewords question 1, moving its key from A to B. The change is staged, read through `pending-changes`, and published with its `revisionId`.
4. S1 reloads at 10:25: the new stem shows, no option carries `correct`, and `sitting.deadlineAt` is still 10:50.
5. S1 answers B, A, A and submits at 10:30. Score 4 (question 1 earns its 2 marks).
6. S3 starts at 10:30: deadline 11:30, the new duration.
7. S2's result is re-scored by the publish: score 2 (question 1 now 0).

## Notes

- Not covered by an existing test. `AttemptsIT` covers the kind and deadline of an MCQ sitting, not a mid-sitting publish, and `StagedPublishGuardIT` (W2) is a different test.
- The first run failed to compile (request builders passed where `ResultActions` was expected). Fixed in the test only. The rerun passed.
- No production file was changed for this case.
