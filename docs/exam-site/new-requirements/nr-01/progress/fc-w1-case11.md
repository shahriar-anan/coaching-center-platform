# FC-W1 case 11: archived and retake written papers through marking

**Class:** `api/src/test/java/com/coachingcenter/api/exams/ArchivedAndRetakeMarkingIT.java`
**Run:** `tools/nr01-mvn.ps1 -Agent W1 -Tests "ArchivedAndRetakeMarkingIT"`: 1 test, 0 failures, 0 errors, 0 skipped.

## What is covered

One standalone written exam, window 12:00 to 12:40 Dhaka. S1 sits live (LIVE), S2 starts after the window (ARCHIVED), S1 gets a written retake and sits it (RETAKE). The instructor marks all three (6, 7 and 9), the Master Admin releases them, and then:

1. The submission feed has LIVE:1, ARCHIVED:1 and RETAKE:2.
2. The leaderboard holds only S1 live paper (score 6).
3. S2 and S1 retake are RELATIVE; S1 first sitting is BOARD.
4. The student history for S1 has two rows for the exam, kinds LIVE and RETAKE.
5. The staff results summary counts live 1, archived 1, retake 1.

## Notes

- Not covered by an existing test. `WrittenRetakeGrantIT` covers grant and revoke, not the feed, positions or counts.
- No production file was changed for this case.
