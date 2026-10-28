# F3: pure helpers with unit tests (agent F)

## What changed

Helpers with the signatures in [contracts.md §4](contracts.md#4-shared-helpers-built-by-f-frozen-afterwards). All are package-private in `com.coachingcenter.api.exams`.

- `ExamWindow`: `phase`, `canStartFirstAttempt`, `firstAttemptKind`, `deadline`, `lastLiveDeadline`, `validate`.
- `ExamPricing`: `effective`, `view` (for an exam and for a package), `validate`. Plus the `PriceView` record.
- `McqScoring` (started in F2 with `plain`): `questionScore`, `total`, `maximum`, `passed`, and the nested `QuestionKey` record.
- `McqRescorer` (`@Component`): `rescore(examId)`. It re-scores every submitted MCQ attempt from the current key, updates `passed`, and returns the number of scores that changed. Attempts still in progress are not touched.
- `AccessWindow`: `end`, `expired`.
- Change-list records, each in its own file: `ChangeList`, `FieldChange`, `QuestionChange`, `PaperChange`, `MembershipChange`, `Impact`.
- `common/error/ErrorCode`: the eight codes from [contracts.md §5](contracts.md#5-error-codes-and-permissions-added-by-f). They are added here because `ExamWindow` raises `EXAM_WINDOW_INVALID`. Nothing raises the others yet.

## Real test result

Command: `nr01-mvn.ps1 -Agent F -Tests "ExamWindowTest,ExamPricingTest,McqScoringTest,AccessWindowTest,McqRescorerIT"`.

```
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- ExamWindowTest
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- ExamPricingTest
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 -- McqScoringTest
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- AccessWindowTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 25.34 s -- McqRescorerIT
```

The first run failed to compile on `EXAM_WINDOW_INVALID`, which was fixed by adding the error codes. The run after that was the one above, and it is the real result.

Edge cases covered, against [f-foundation.md §F3](f-foundation.md#f3-pure-helpers-with-unit-tests):
- **ExamWindowTest**: each phase at its boundary (`liveAt - 1 s`, `liveAt`, `endsAt - 1 s`, `endsAt`); unannounced and practice; a start at `endsAt - 1 s` is LIVE and at `endsAt` is ARCHIVED; a deadline past the window end; `validate` for a practice window, one date only, a window shorter than the duration, and the exact-duration case.
- **ExamPricingTest**: no discount; an active discount; an expired discount (inactive at `discountEndsAt`); 199 at 33% = 133.33; free; null price; package view; `validate` for a free item with a price, price 0, discount 0 and 100, and an end without a discount.
- **McqScoringTest**: single and multi answer; exact match; superset; subset; blank; negative mark null, 0, 0.25; negative total; pass at the boundary (40 of 100 passes, 39.99 fails); `plain` for 12.00, 10.00, 12.50.
- **AccessWindowTest**: lifetime; free; a grant before the window end counts from the window end; a grant after it counts from the grant; exclusive end; undated live exam never expires; practice anchored on publish; unpublished practice has no end.
- **McqRescorerIT**: a key change re-scores two submitted papers (2.00 → 0.75 and -0.25 → 1.00); `passed` flips with the score; the open attempt keeps null score and pass; the return value is 2; a second run returns 0. The test is `@Transactional` and rolls back.

## Deviations and decisions

- **`ExamPricing.validate` puts its field errors in `ApiException.details`**, as a `List<FieldErrorDetail>`. `ApiExceptionHandler` writes an `ApiException`'s `details` only into the `details` field of the error body, and `fieldErrors` stays null. Contracts §5 says "VALIDATION_FAILED with fieldErrors". The shape is the same, but the field name differs. **Question for the orchestrator:** should the handler copy a list-valued `details` into `fieldErrors`? Stage 2 would need the same, so I did not change the handler (it is shared code, and changing it is not in F's slices). Recorded in [f-done.md](f-done.md) when written.
- **`ExamWindow.firstAttemptKind` throws `IllegalStateException`** for a live exam with no window, which the contract does not cover. Nothing should reach it: a first attempt cannot start without a window.
- **`AccessWindow.end` returns null for an unpublished practice exam.** It cannot be sat before publish, so there is no anchor yet.
- **`McqScoring.passed` with a null pass percent uses 0**, as the current code does. A missing bar means every score passes.
- **`PriceView.discountEndsAt` is an ISO-8601 instant string** (`Instant.toString()`), as the contract's `String` type implies, and as the other times in the API are UTC.
- **`PriceView.effectivePrice` is null for a free item.** The contract does not say; a free item has no price to show.
- **`ExamPricing.view(module, now)`** always sets `moduleOnly = true`. For an exam, the caller passes `moduleOnly`.
- **`McqScoring.QuestionKey`** is nested in `McqScoring`, not top-level, to keep the scoring names together. Its shape is the contract's.
- **`McqRescorer.rescore` sets `passed = answered && passed(...)`**, the same rule as the current `scoreMcq`, so a blank paper is never a pass.
- **The rescorer normalises the score to two decimal places** (`setScale(2, HALF_UP)`), matching the column. Scores read back as `12.00`; the views print them through `plain`.

## Not done here

`AccessState` and the `LiveExamAccess` changes belong to F4 (they need the membership table and the grant lookups). `LiveExamPermissionContributor`, the `Permissions` constants, the audit constants and the notice writer methods are also F4.

## For you to click

None: this slice has no UI.
