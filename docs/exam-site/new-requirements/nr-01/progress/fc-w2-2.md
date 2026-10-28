# NR-01 final check, W2: case 2 (staged publish guard)

**Rule:** R16, A4. Once a student has started, a staged change that adds or removes questions or options cannot be published. A wording-only edit still publishes.

**New class:** `api/src/test/java/com/coachingcenter/api/exams/StagedPublishGuardIT.java`

| Test | What it checks |
|---|---|
| `aStagedAddedQuestionCannotBePublishedOnceAStudentHasStarted` | Staged add before any start; student takes; publish gives 409 `QUESTION_STRUCTURE_LOCKED`; published question count stays 1; revision stays (`pendingChanges: true`); discard, then a wording-only edit stages and publishes after the start |
| `aStagedRemovedOptionCannotBePublishedOnceAStudentHasStarted` | Staged removal of one of three options; student takes; publish gives 409; published option count stays 3; discard, then a wording-only edit (same three options) publishes |

**Run:** `nr01-mvn.ps1 -Agent W2 -Tests "StagedPublishGuardIT"`: 2 run, 0 failures, 0 errors, 0 skipped.

**Already covered:** none of the case 2 steps was covered before. `ExamRevisionIT` covers staged edits and discard, but not the start lock.

**Production code:** no change. The guard is `LiveExamRevisionService.publish` (around line 205) and the start-time guard in `LiveExamService.requireStructureOpen`, and both behaved as the decisions say.
