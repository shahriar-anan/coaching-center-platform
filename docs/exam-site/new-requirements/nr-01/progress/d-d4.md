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

## Late tests (MultiAnswerScoringIT, LeaderboardPositionIT, WrittenRetakeGrantIT, PaperVersionIT, SittingCountsIT, AttemptsIT)

Final results: MultiAnswerScoringIT 5/5, LeaderboardPositionIT 4/4, WrittenRetakeGrantIT 3/3, PaperVersionIT 1/1, SittingCountsIT 2/2, AttemptsIT 9/9 (including the expired-access case, using C's expiry rule with the roster start moved back by SQL). LiveSittingIT 34/34 and LiveEvaluationIT 63/63 were green in the same invocation. Two invocations were needed for the red classes; each later run covered only the classes it changed.

- **MultiAnswerScoringIT, second correct option:** the question API on this branch rejects two correct options, because A's multi-answer authoring was not in it. The test sets the second correct option by SQL. Switch to the API after A's merge (A is now merged).
- **MultiAnswerScoringIT, legacy payload:** the test sends the array only; the save helper wraps it in nswers.
- **LeaderboardPositionIT, the fixture was wrong, not the code.** live() saved each answered question in its own bulk call, and each bulk call replaces the whole selection, so each student kept only the last answer. Scores fell below the pass mark and the board was empty. Fixed: one save per student, scores 10, 8, 6 and 4 (4 fails the pass mark of 5). A staff board check before the retake shows the scores the retake is ranked against. With those scores the retake (6) is rank 3 of 3, as the rule says. The code's rank rule matched the decided rule and was not changed.
- **LeaderboardPositionIT, new cases:** (a) negative marks change the order: same two correct answers, a wrong pick on the third question gives 5.75 and a blank gives 6, so the blank ranks first; (b) a written position is absent before release and appears as BOARD rank 1 of 1 after publish.
- **LeaderboardPositionIT, JSON:** null fields are left out of the response, so ank: null and position: null are checked as missing or null.
