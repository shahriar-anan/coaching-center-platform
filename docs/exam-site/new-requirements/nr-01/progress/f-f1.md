# F1: migrations V45–V51 (agent F)

Committed together with F2, because `Nr01MigrationIT` needs the F2 entity mappings to load: `ddl-auto=validate` rejects the
`score` column as `numeric(8,2)` until it is mapped as `BigDecimal`. See [f-f2.md](f-f2.md).

## What changed

- `api/src/main/resources/db/migration/V45__nr01_exam_home_and_packages.sql`: `live_exams.category_id` (backfilled from the package, then NOT NULL), `module_id` nullable, `live_exam_package_exams` (membership, backfilled one active row per live exam), `live_exams.instructor_id` (backfilled from the package instructor).
- `V46__nr01_window_mode_attempts.sql`: `ends_at` (backfilled `live_at + duration`), `mode`, the window checks (D16), `published_at`, `attempt_no`, the open-attempt and per-attempt unique indexes (replacing `live_exam_participants_one_idx`), the kind check with `ARCHIVED` and `PRACTICE`, `RETAKE` rows renamed to `ARCHIVED`, `live_exam_retake_grants`.
- `V47__nr01_mcq_scoring.sql`: `multiple_answer`, the composite answer key, `negative_marks`, `answers_changeable`, decimal `score` and `pending_score`, `live_exam_answer_confirmations`. The one-correct-option index is dropped.
- `V48__nr01_question_papers.sql`: `live_exam_question_papers` (version 1 backfilled from each exam's `question_*` columns), `live_exams.question_paper_id`, `live_exam_participants.question_paper_id` (backfilled for written sittings).
- `V49__nr01_commerce_and_catalog.sql`: exam and package discount, discount end, access days, popular, preview, `is_free`; order `list_price`, `discount_percent`, nullable `module_id`, the new scope check; nullable grant `module_id`; `student_favourites`.
- `V50__nr01_revisions.sql`: `live_exam_revisions`, `live_exam_module_revisions`, `live_exam_change_log`.
- `V51__nr01_notice_kind.sql`: the V40 kind list plus `EXAM_UPDATED`.
- Entities and repositories for every new table and column, per [contracts.md §3](contracts.md#3-entities-and-enums-written-by-f). See [f-f2.md](f-f2.md).
- `api/src/test/java/com/coachingcenter/api/exams/Nr01MigrationIT.java`: the six constraint checks (a)–(f) and the backfill test.

## Real test result

Command: `powershell -NoProfile -File docs/exam-site/new-requirements/nr-01/tools/nr01-mvn.ps1 -Agent F -ResetDb -Tests "Nr01MigrationIT"` (`-ResetDb` because the database was new).

```
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 28.10 s -- in com.coachingcenter.api.exams.Nr01MigrationIT
```

The seven tests:
1. `aMultiAnswerQuestionStoresEveryCorrectOptionAndEveryChoice`: (a) two correct options accepted; two answer rows for one question accepted; `multiple_answer` defaults to false.
2. `aStudentHasOneOpenAttemptButSeveralSubmittedOnes`: (b) a second `IN_PROGRESS` attempt rejected; two `SUBMITTED` attempts with attempt numbers 1 and 2 accepted.
3. `theWindowIsAtLeastTheDurationAndPracticeHasNoWindow`: (c) `ends_at` shorter than the duration rejected; `PRACTICE` with a window rejected; only `live_at` set rejected; undated practice accepted.
4. `aFreeExamCannotCarryAPrice`: (d) free with a price rejected; free without one accepted.
5. `aFavouriteNamesExactlyOneTarget`: (e) both targets rejected, neither rejected, duplicate rejected; one target accepted.
6. `aPackageMembershipCanBeAddedAgainAfterItWasRemoved`: (f) re-added after `removed_at` accepted; a second active row rejected.
7. `theBackfillBringsAnExistingDatabaseToTheNewShape`: see below.

## Deviations

- **The backfill test does not use the existing services.** It builds the V44 shape with raw SQL in a separate schema, `nr01_backfill`, migrated to V44 by Flyway and then to the latest version. Using the services would mean using the entities that F2 changes. The constraint tests run in a second schema, `nr01_it`. Neither touches `public`, so the other tests' data is unaffected. Both schemas are dropped after the class.
- **`gen_random_uuid()`** is used by the backfills. It needs PostgreSQL 13 or later. The local server is 17; the staging version is not checked here.
- **`live_exam_participants.attempt_no`** keeps `DEFAULT 1`, as the contract says. The Java field defaults to 0, so F4 must set it on every insert.
- **`live_exams.answers_changeable`** has a DB default of true. The Java `boolean` defaults to false, so F4's create path must set it (P-c).
- **Order scope check**: `BUNDLE` is allowed (module id set, exam id null). An `EXAM` order may keep a `module_id`, which the contract leaves open.
- No other deviation from the contract's SQL.

## Backfill checks (test 7)

Starting from the V44 shape with two exams in a package, one written exam with a paper, one deleted exam, one undated draft, and three participants (one a `RETAKE`):
- every live exam has `category_id`, and the column is `NOT NULL`;
- every non-deleted exam has exactly one active membership, with positions 0, 1 and 2 in creation order; the deleted exam has none;
- `ends_at = live_at + duration` for the dated exams, and null for the undated one;
- `instructor_id` comes from the package instructor for the three non-deleted exams;
- `published_at` is the old `updated_at` for a published exam, and null for a draft;
- the `RETAKE` row is `ARCHIVED`, its `attempt_no` is 1, and the integer score 12 reads back as `12.00`;
- the written exam has version 1 of its paper, and its exam and its written sitting point at it.

## Not run yet

The existing `*IT` classes are not run against this schema, so they are expected to fail until F4 sets `category_id`, `mode`, `answers_changeable` and `attempt_no` on the write paths. F4 and F5 cover that. The full suite runs once, at F5.

## For you to click

None: this slice has no UI.
