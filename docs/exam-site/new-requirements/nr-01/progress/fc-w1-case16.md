# FC-W1 case 16: two retake requests at the same moment

**Class:** `api/src/test/java/com/coachingcenter/api/exams/RetakeRaceIT.java`
**Run:** `tools/nr01-mvn.ps1 -Agent W1 -Tests "RetakeRaceIT"`: 5 repetitions (`@RepeatedTest(5)`), 0 failures, 0 errors, 0 skipped.

## What is covered

A standalone MCQ with one finished first attempt. Two threads call `POST /retake` together, released by a latch. Each repetition is a fresh student and exam.

Per repetition: no response is a 500; a losing response is 409 `ATTEMPT_IN_PROGRESS`, or a 200 with `attemptNo` 2; at least one succeeds; and exactly one attempt row with `attempt_no` 2 exists.

## Notes

- Not covered by an existing test. `AttemptsIT` covers the sequential case (a second retake while one is open is 409), not two requests at once.
- No flakiness seen in the five repetitions of this run.
- No production file was changed for this case.
