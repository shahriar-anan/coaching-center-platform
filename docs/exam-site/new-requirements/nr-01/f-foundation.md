# Stage 1: Foundation (agent F)

**Goal:** the schema, entities, repositories, error codes, permissions and pure rule helpers from [contracts.md](contracts.md) §2–§5 are in place, **and the API behaves exactly as it does today**. Every existing test stays green. Stage 2 agents then change behaviour on top.

Branch `nr01/f-foundation`. Worktree `F:\cc-wt\nr01-f`. Test database `cc_test_nr01_f`.

You own every file you touch in this stage. Stage 2 has not started, so there is no one to conflict with. Keep behaviour changes at zero except where a slice says otherwise.

## F1: Migrations V45–V51

- Write the seven migrations exactly as [contracts.md §2](contracts.md#2-schema-flyway-written-by-f) specifies.
- Each migration is **re-runnable on staging data**: the backfills work when tables are empty, and when there are many exams, participants, grants and orders.
- Use `INSERT … SELECT` for the backfills, and no procedural loops.
- Copy the `V40` notice kind list exactly into `V51`.

Test: new `Nr01MigrationIT`. Running against the migrated schema, it inserts rows through JDBC and asserts that each of these is accepted or rejected:
- (a) two correct options on one question are accepted;
- (b) two `IN_PROGRESS` attempts for the same student and exam are rejected; two `SUBMITTED` attempts with different `attempt_no` are accepted;
- (c) `ends_at < live_at + duration` is rejected; `PRACTICE` with `live_at` is rejected; exactly one of `live_at` and `ends_at` set is rejected;
- (d) `is_free` with a price is rejected;
- (e) a favourite with both `exam_id` and `module_id` is rejected;
- (f) a membership can be added again after `removed_at` is set.

Also prove the backfill:
- seed a pre-V45 shape through the existing services **before** your entity changes. In practice this is the current `DemoContentSeedIT` data;
- then assert that every exam has a `category_id`, exactly one active membership, `ends_at = live_at + duration`, written participants have `question_paper_id`, and former `RETAKE` rows are now `ARCHIVED`.

## F2: Entities and repositories

- Add the fields, entities and repository methods in [contracts.md §3](contracts.md#3-entities-and-enums-written-by-f).
- `ddl-auto=validate` must pass.
- `LiveExamAnswer` becomes a composite key, so update its id class and every `answers.*` call site so the code compiles and still writes one option per question.
- `score` becomes `BigDecimal` everywhere it is read: leaderboard, results, history, participation, mail texts, notices and the seed. This is a mechanical change. JSON output stays numeric; the integer scores of today serialise as `12` or `12.00`, so check what the existing tests expect and keep them passing (prefer `stripTrailingZeros` in views if needed).

## F3: Pure helpers with unit tests

Build `ExamWindow`, `ExamPricing` (with `PriceView`), `McqScoring`, `McqRescorer` and `AccessWindow`, and the `ChangeList` records, with the exact signatures in [contracts.md §4](contracts.md#4-shared-helpers-built-by-f-frozen-afterwards). Each helper gets a unit test class covering the edge cases below.

| Test class | Edge cases |
|---|---|
| `ExamWindowTest` | Each phase at the exact boundary instants; practice; unannounced; a start at `endsAt - 1s` is `LIVE`, at `endsAt` is `ARCHIVED`; deadline beyond the window. |
| `ExamPricingTest` | No discount; active discount; expired discount (at `discountEndsAt` it is inactive); rounding (199 at 33% is 133.33); free; null price. |
| `McqScoringTest` | Single and multi answer; exact match; superset; subset; empty; negative null, 0 and 0.25; negative total allowed (P-a); `passed` at the boundary. |
| `AccessWindowTest` | Lifetime; days counted from the window end when granted before it; from the grant when granted after it; practice anchored on `publishedAt`; undated live exam never expires; free never expires. |
| `McqRescorerIT` | Change a correct option, re-score, assert the scores and `passed` changed and the return count is right. Attempts still `IN_PROGRESS` are untouched. |

## F4: Keep today's behaviour on the new schema

Wire the new schema into the existing code with **no visible behaviour change**:

- **Create and copy.**
  - Creating an exam in a package also sets `categoryId` (the package category), `endsAt = liveAt + duration` when `liveAt` is set, `mode = LIVE`, and the membership row.
  - Updating `liveAt` or `durationMinutes` recomputes `endsAt` the same way.
  - Copying sets the same fields.
- **Publish** sets `publishedAt` the first time.
- **Reads.** Every read of `exam.getModuleId()` used for access, listing or counting moves to the membership table.
  - Today each exam has exactly one membership, so results are identical.
  - `LiveExamAccess` implements the contract signatures with membership plus the R4 removal rule. There is no free or expiry logic yet; `endsAt` is always null and `expired` always false.
  - Keep `hasExam(…)` as a deprecated delegate to `canSit` if call sites need it, and list them for stage 2.
- **Sittings.**
  - `take` sets `attemptNo = 1`.
  - `retake` keeps today's rule (only when the student has no attempt at all) and creates `attemptNo = 1` with kind `ARCHIVED`. The rule is unchanged; only the label is new, because the backfill renamed `RETAKE` to `ARCHIVED`.
  - Use `LiveExamParticipantRepository` methods that do not assume one row.
- **Written paper upload** creates a `live_exam_question_papers` version, sets `questionPaperId`, and mirrors it into the legacy `question_*` columns. The take path stores `participant.questionPaperId`.
- **MCQ posting** uses `McqScoring` (single answer, no negative marks, so the results are the same numbers).
- **Demo seed**: update `DemoContentSeed`'s SQL inserts for the new NOT NULL columns (`category_id`, `mode`) and the new tables (membership, paper version), so `DemoContentSeedIT` stays green. Content stays the same; the integration stage adds NR-01 demo cases.
- **Error codes and permissions**: add them (contracts §5). Grant the permissions in `LiveExamPermissionContributor`. Raise none of them yet, except where a slice above needs one.
- **Audit actions**: add the contracts §5 constants to `AuditRecorder`. Nothing records them yet.
- **Notices**: add `StudentNoticeWriter.examUpdated(UUID examId, Collection<UUID> studentIds)` (kind `EXAM_UPDATED`, one notice per student per publish, no mail) and `StudentNoticeWriter.retakeAvailable(UUID examId, UUID studentId)` (kind `RETAKE_AVAILABLE`). Follow the existing insert-once pattern and message keys. Both are unused until A and D call them. Test them in `Nr01MigrationIT`, or in a small `StudentNoticeWriterNr01IT`.
- **Running Maven:** only through `docs/exam-site/new-requirements/nr-01/tools/nr01-mvn.ps1 -Agent F` (see README, "Running Maven").

Test: the **full** `mvn test` is green with no assertion changed. If an assertion must change because of the decimal score format, change only that and say so in the report.

## F5: Hand-over

- Write `docs/exam-site/new-requirements/nr-01/progress/f-done.md` containing:
  - every place where your code differs from [contracts.md](contracts.md), with the reason;
  - every call site still reading `exam.getModuleId()` or the legacy `question_*` columns, with file and line, for the stage 2 owners;
  - the list of existing `*IT` classes, each with its owner as given in [README.md](README.md) ownership;
- the full-suite result through the script (`-Full`), with real counts.
- Report to the orchestrator.

## Not yours

Any new behaviour: windows separate from duration, retakes, multi-answer saving, staged edits, discounts in orders, expiry, endpoints. Leave the old lock code (`CONTENT_LOCKED`, the window clash check) in place; A removes it.
