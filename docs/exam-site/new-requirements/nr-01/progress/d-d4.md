# NR-01 D progress: D1 to D4, with D5 and D8 in part

Agent D, branch `nr01/d-sitting`. Commits: `99502bf` (WIP, the first snapshot of all D code, before its tests were green). The commit that closes this note follows it.

Slices D1 to D4 share `LiveExamSittingService`, `LiveExamSittingController`, `LiveExamStudentStatus` and the MCQ result path, so they were built and tested together and are committed together. D5 (`LiveExamLeaderboardService`) and D8 (`LiveExamStudentStanding`, `StudentReminderJob`) are in the same tree, and their tests ran in the same invocation.

## Tests run (one invocation for this group)

Command: `nr01-mvn.ps1 -Agent D -Tests "LiveSittingIT,LiveEvaluationIT,LiveExamStudentStatusTest,AttemptsIT,AuditLogIT"`

Result: see the closing message of this note (counts pasted there after the run).

## What changed (decided behaviour, D1 to D8)

- D1 first attempt: `ExamWindow.firstAttemptKind` decides LIVE or ARCHIVED from the start instant, once. Practice is PRACTICE. The deadline is start plus duration (D15). Upcoming gives SITTING_NOT_OPEN. Expired access gives ACCESS_EXPIRED (C's expiry rule is not merged; see the questions).
- D2 retakes: MCQ unlimited; a retake while an attempt is open gives ATTEMPT_IN_PROGRESS. A student with no attempt who calls retake after an MCQ window ended gets the ARCHIVED first attempt (contracts 7.9). Written: one retake, only with an active grant (`RetakeGrantService`, `RetakeGrantController`).
- D3 answers: `optionIds` accepted with the old `optionId`. Single-answer cap. Locked single answers (`ANSWER_LOCKED`) and multi confirm via `confirmations`.
- D4 result: the key and explanations come back as soon as the attempt is submitted or posted (D21). `position` comes from `LiveExamLeaderboardService` (CURRENT, BOARD, RELATIVE, belowPass).
- D5 board: students see rows once the window ends (D12). Only results at or above the pass mark are listed (D24).
- D8 status: `myStatusDetail` carries ARCHIVED_OPEN, PRACTICE_OPEN and EXPIRED; `myStatus` maps them to the legacy nine (contracts 7.8).

## Decided behaviour changes that changed existing test assertions

Each changed test below names the rule that explains the change.

LiveSittingIT:
- `aStudentWhoNeverTapped…` (was `…CanRetakeOnceAndALiveParticipantCannot`): an open attempt is returned (D1); a never-started student gets an ARCHIVED first attempt (D20, contracts 7.9); an open attempt refuses retake with ATTEMPT_IN_PROGRESS.
- `anEarlySubmitShowsTheKeyAtOnceAndRefusesALaterSave` (was `…StillAcceptsALaterSave…`): D21 key at submit. Decision 2 of the orchestrator: answers are refused after an MCQ submit (ANSWER_ALREADY_SUBMITTED), because the key is visible and a later edit would be a loophole.
- `anArchivedSittingShowsItsKeyOnSubmitAndAMcqRetakeIsAFreshAttempt` (was `…HidesTheReviewUntilThePersonalTimerEnds`): D21, D19 unlimited retakes.
- Times moved from the window end to the sitting's own deadline (D15) in: `aReadAfterTheDeadlinePostsAnMcq…`, `aReadAfterTheWrittenDeadline…`, `theDeadlineJobPostsMcq…`, `theDeadlineJobMarksAWritten…`, `aWrittenUploadAtTheDeadline…`, `aWrittenPdfReplaces…`, `theQuestionPdfDownloads…`, `theRoutineDropsAnEndedExam…`, `rosterRemovalAndArchive…`, `anMcqStatusFollows…` (also: MCQ retakes available after a finished attempt, D19; the `myStatusDetail` check reads the detail field, not the legacy helper).
- `sittingScore` reads NUMERIC(8,2) as a decimal (V47).
- `anOpenSittingReturnsTheSavedSelection…`: the key appears only after the sitting's deadline (D21, D15).

LiveEvaluationIT:
- Window-end clock values `06:40` and `06:46` moved to `06:50`, and `06:45` moved to `06:50`: the sitting's deadline (D15).
- `theMcqBoardWaitsFiveMinutesAndSkipsTiedRanks` renamed `theMcqBoardOpensAtTheWindowEndAndSkipsTiedRanksAndBelowPass`. The old five-minute wait is dropped (D12). Its last check now expects two rows and no blank paper, because the zero is below the pass mark (D24).
- `aWrittenExamRejectsALaterAttempt`: the no-submit zero is below the pass mark, so the board has no rows (D24). The student's own position shows `belowPass`.
- `aFailedToSubmitRowIsListedAndCannotBeMarked`: the no-submit row is off the board (D24).
- `aLaterMarkReplacesThePdfAndChangesTheRank`: the 4-of-10 paper is below pass, so it is not listed (D24).
- `theBoardMarksOnlyTheReadersOwnRowAndStaffGetNone` (orchestrator decision 3): both no-submit zeros are below pass, so neither is listed. The reader's position shows `belowPass`, and staff see `belowPass` = 2.
- `anInstructorListsOnlyAssignedModules`: a package lists for an instructor only once they hold one of its exams (R3). The test creates an exam first.

## Deviations and things not done

- Not done (still owed by D): `WrittenRetakeGrantIT`, `MultiAnswerScoringIT`, `LeaderboardPositionIT`, `PaperVersionIT`. Their cases are covered in part by the tests above, not in full.
- The "answers after MCQ submit" refusal is a behaviour change from HEAD (it is decided, see decision 2).
- `LiveExamStudentStatusTest` keeps the original assertion that a submitted MCQ before its deadline reads `SUBMITTED`. That is an accepted decision (decision 1).
- The fixture helper that set an exam's instructor from its package was removed (decision 4). `LiveExamService` already inherits the package instructor on creation.
- Deadline job: `findDueIds` uses each sitting's own deadline (decision 5). The written never-submitted notice test was checked against that rule.
