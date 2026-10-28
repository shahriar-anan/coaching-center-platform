# F2: entities and repositories (agent F)

Committed together with F1 ([f-f1.md](f-f1.md)): the entity mappings are what `ddl-auto=validate` checks the new schema
against, so `Nr01MigrationIT` cannot load without them.

## What changed

- **Existing entities** (`LiveExam`, `LiveExamModule`, `LiveExamQuestion`, `LiveExamParticipant`, `LiveExamOrder`, `LiveExamGrant`), with the fields from [contracts.md §3](contracts.md#3-entities-and-enums-written-by-f).
  - `LiveExam.moduleId` is nullable and kept for history. The new fields are `categoryId`, `instructorId`, `mode` (`MODE_LIVE` / `MODE_PRACTICE`), `endsAt`, `publishedAt`, `negativeMarks`, `answersChangeable`, `questionPaperId`, `free`, `discountPercent`, `discountEndsAt`, `accessDays`, `popular`, `previewEnabled`.
  - `LiveExamModule` gets `discountPercent`, `discountEndsAt`, `popular`. `LiveExamQuestion` gets `multipleAnswer`.
  - `LiveExamParticipant` gets `attemptNo` and `questionPaperId`; `score` and `pendingScore` are `BigDecimal` (8,2). Constants `ARCHIVED` and `PRACTICE` join `LIVE` and `RETAKE`.
  - `LiveExamOrder` gets `listPrice` and `discountPercent`, and a `BUNDLE` scope constant; `moduleId` is nullable. `LiveExamGrant.moduleId` is nullable.
  - `LiveExamAnswer` has a three-part key (participant, question, option).
- **New entities**: `LiveExamPackageExam`, `LiveExamQuestionPaper`, `LiveExamRetakeGrant`, `StudentFavourite`, `LiveExamRevision`, `LiveExamModuleRevision`, `LiveExamChangeLog`, `LiveExamAnswerConfirmation` (with its `Key` class).
- **New repositories**: one per new entity. Required methods are present:
  - `LiveExamPackageExamRepository`: `findActiveByExamId`, `findActiveByModuleId` (ordered by position), `findByExamId` (includes removed rows), `existsActive`.
  - `LiveExamParticipantRepository`: `findByExamIdAndStudentIdAndDeletedAtIsNullOrderByAttemptNoDesc`, `findOpen`, `findBoardAttempts`.
  - `LiveExamQuestionPaperRepository`: `findTopByExamIdOrderByVersionDesc`, plus `findByExamIdAndVersion`.
  - `LiveExamRetakeGrantRepository`: `findActive`.
- **Decimal scores** (mechanical, F2 in [contracts.md §3](contracts.md#3-entities-and-enums-written-by-f)): `score` is `BigDecimal` wherever it is read in the live-exam code:
  - `LiveExamSittingService`: `PostedResult`, `ReviewedResult`.
  - `LiveExamEvaluationService`: `SubmissionView`, `LeaderboardRow`, and the private `BoardRow` and `scoreOf`. The leaderboard tie test now uses `compareTo`, not `!=`.
  - `LiveExamResultsService`: `Row`, `HistoryRow`, `Summary.highestScore` and `lowestScore`, and the average, which now uses `doubleValue`.
- **`McqScoring.plain(BigDecimal)`** (new file): returns the score as the API shows it. A whole mark prints as `12`, not `12.00`, and `10.00` stays `10`, not `1E+1`. Every view that carries a score applies it, so the JSON the current web reads does not change. F3 adds the scoring rules to this class.

## Real test result

`Nr01MigrationIT`: 7 run, 0 failures, 0 errors (see [f-f1.md](f-f1.md)). The main and test sources compile (`-Goal test-compile`, EXIT 0 after the last change). The existing `*IT` classes have not been run yet (see f-f1).

## Deviations from contracts.md

- **Tables without `deleted_at` do not extend `BaseEntity`.** `BaseEntity` maps `deleted_at`, and these tables don't have it. The new entities declare their own `@GeneratedValue(strategy = GenerationType.UUID)` id, which is the same mechanism.
- **`LiveExamPackageExamRepository.existsActive`** is a default method that delegates to the derived `existsByModuleIdAndExamIdAndRemovedAtIsNull`. Behaviour is the same as the contract's signature.
- **`LiveExamRetakeGrantRepository.findActive`** is a `@Query`, not a derived name, because "active" is `revokedAt is null`.
- **Repository and entity names** follow the one-type-per-file rule. Four small repositories are separate files, not one shared file.
- **`LiveExamAnswerConfirmation`** is an entity with a `Key` class, not a `@Embeddable` id. This matches `LiveExamAnswer`.
- **The `answers_changeable` and `attempt_no` Java defaults** are `false` and `0` (Java defaults). The database defaults are true and 1. F4 must set both on every insert (see f-f1).

## For the orchestrator (the F4 call sites)

Compile errors were limited to score types, so `getModuleId()` and the legacy `question_*` columns are unchanged and still read in the places listed in f-done (F5). F4 will move those reads.
