# Stage 3: Integration (orchestrator)

Run this on `nr01/integration` once A, B, C and D are all merged. The orchestrator does it, or one agent it names. Work in the main worktree with test database `cc_test_nr01_int`.

## 1. Full check

1. Run the full suite through `tools/nr01-mvn.ps1 -Agent INT -Full`, in the background, while no agent is running. Every class must be green, apart from the known `BookOrderIT` / `CoursePurchaseIT` pair, which must then pass alone with `-ResetDb`.
2. Do a fresh database migration twice:
   - first an empty database;
   - then a database restored from `V44` with the current demo seed loaded. Run the seed on a `V44` checkout, then switch to `nr01/integration` and start the API with the staging profile **locally**.

   Both must migrate without error, and the startup `validate` must pass.
3. In `web/`, run `npm run lint`, `npm test` and `npm run build` (the build under the heavy lock), with no source change.
   - If something fails, check whether a compatibility rule ([contracts.md §7](contracts.md#7-compatibility-rules-the-current-website-keeps-working)) was broken. If one was, fix the API, not the web.
   - A web fix is allowed only if [web-impact.md](web-impact.md) lists it as **required now**, and Shahriar agrees.
4. Grep `api/src/main` for reads of `LiveExam.getModuleId()` and of the legacy `question_*` fields. List what is left.

## 2. AuditLogIT and the route inventory

Merge the route additions from all four branches into `AuditLogIT`'s route list. Make sure every new route from [contracts.md §6](contracts.md#6-endpoints) is in it.

## 3. Demo seed

Extend `DemoContentSeed` so the staging review shows every NR-01 case, insert-only and idempotent, as before. It needs:
- a **live MCQ** with a 12-hour window and a 40-minute duration; multi-answer questions; negative marks of 0.25; and "answers can be changed" off;
- a **practice MCQ** marked free and popular;
- an **ended live exam** with preview enabled and a mix of live and archived attempts, and one MCQ retake;
- an **exam in two packages**, and one **standalone** exam in a category;
- a **discounted** exam (30% with an end date next month) and a discounted package;
- an exam with **30 access days**, and a demo student whose access to it has expired;
- a **written exam** with a replaced paper (v1 sat by one student, v2 published), and one written retake grant;
- a **published exam with pending staged changes**, so the badge shows;
- **favourites** for the demo student.

Update `DemoContentSeedIT` to match. Also update the staging cleanup script `api/.cursor/progress/ui-delivery/demo-cleanup.sql`. F added the new tables in dependency order; add any rows the new seed cases create. Then delete the deprecated `LiveExamAccess.hasExam`, once no caller is left.

## 4. Cleanup migration (only if step 1.4 found no reads)

Assign `V52__nr01_drop_legacy_columns.sql` to drop `live_exams.module_id` and the legacy `question_*` columns, then update the entities. If reads remain, skip this, and list them as follow-ups in the record.

## 5. Docs

- **[../../current-api.md](../../current-api.md):** fold in `nr-01/api-changes/*.md`. Rewrite the schema rows for the changed tables, and mark superseded rows **Outdated (NR-01)** instead of deleting them.
- **[../../decisions.md](../../decisions.md):** add an "NR-01 (28 October 2026)" section with the agreed rules, and mark each superseded line **Superseded by NR-01**. The superseded lines are:
  - window = duration;
  - the take deadline is the shared end;
  - one later MCQ attempt, no written later attempt;
  - MCQ review after the sitting ends;
  - the leaderboard opens 5 minutes after the window;
  - the 5-minute clash rule;
  - no negative-mark field;
  - setting or clearing `liveAt` returns the exam to draft;
  - one instructor per module;
  - the README's "Leave these alone" sitting rules.
- **[../../README.md](../../README.md):** point "Leave these alone" at the new decisions.
- **[../NR-01-exam-segment.md](../NR-01-exam-segment.md):** fill in "Record" with the date, the merge commits, the deviations from the plan, and the follow-ups. Set the status to `done (API)`.
- **[../README.md](../README.md) register:** status `API done; UI plan next`.

## 6. Hand-over to Shahriar

Send a short summary covering:
- what changed;
- the test counts;
- any skipped slice;
- the open follow-ups;
- that **nothing is deployed**.

The staging deploy waits for the UI plan, or for Shahriar's explicit go. The `demo/client-review` merge waits for Shahriar.
