# NR-01 orchestrator status and resume guide

Read this first if you are a new orchestrator session, whatever the model. Last updated 28 October 2026.

## Where things stand

| Stage | State |
|---|---|
| Plan | Committed on `nr01/integration` (`03a8723`, updated in `6e82924`). |
| F (foundation) | **Merged** into `nr01/integration`. Full suite on integration: 454 tests, 0 failures. |
| C (commerce), Haiku | Running in `F:\cc-wt\nr01-c` (branch `nr01/c-commerce`). |
| D (sitting), Sonnet | Running in `F:\cc-wt\nr01-d` (branch `nr01/d-sitting`). |
| B (packages), Haiku | Running in `F:\cc-wt\nr01-b` (branch `nr01/b-packages`). |
| A (authoring), Sonnet | Running in `F:\cc-wt\nr01-a` (branch `nr01/a-authoring`). |
| Integration (stage 3) | Not started. See [integration.md](integration.md). |

Merge order: **C, D, B, A.** Then stage 3.

## How to resume after a session or usage limit

1. **Find each agent's last finished slice.** For each worktree:
   - run `git -C F:\cc-wt\nr01-<x> log --oneline nr01/integration..HEAD`;
   - list `F:\cc-wt\nr01-<x>\docs\exam-site\new-requirements\nr-01\progress\`.

   A slice is done only if it has **both** a commit and a `<x>-<slice>.md` note.
2. **Look for half-done work.**
   - Run `git -C F:\cc-wt\nr01-<x> status --short`. Uncommitted changes are an interrupted slice. Do not commit them blind: the restarted agent finishes that slice or reverts it.
   - Delete `F:\coaching-center-platform\.heavy.lock` if it exists and no `java` or `mvn` test process is running. Check first with `Get-CimInstance Win32_Process`, looking for surefire or maven in the command line. VS Code's Gradle server is not a test run.
3. **Restart each unfinished agent** with the Agent tool: `subagent_type: general-purpose`, `run_in_background: true`.
   - Use the same model as before: **C and B on haiku, A and D on sonnet**.
   - Use the prompt in [prompts.md](prompts.md) for that agent, with the environment block below added at the top, plus this line: *"You are resuming. Read your progress notes and `git log nr01/integration..HEAD` in your worktree. Finish or revert any uncommitted change. Continue from the first slice without a progress note."*
4. **Agents that are already finished:** do not restart them. Review their report (last progress note, plus `git diff nr01/integration..nr01/<branch>`) and merge in order.

### Environment block for every agent prompt

```
- Your shell does NOT stay in your worktree between calls. Use absolute paths (F:\cc-wt\nr01-<x>\...).
  In Bash, prefix every command with `cd /f/cc-wt/nr01-<x> &&`; in PowerShell, start with `Set-Location F:\cc-wt\nr01-<x>;`.
  Never edit under F:\coaching-center-platform\ or another agent's worktree.
- First command: git rev-parse --show-toplevel and git branch --show-current must show your worktree and branch.
- You are a background subagent. Your final message IS your report to the orchestrator.
- Maven only via: Set-Location F:\cc-wt\nr01-<x>; powershell -NoProfile -File docs\exam-site\new-requirements\nr-01\tools\nr01-mvn.ps1 -Agent <X> -Tests "..."
  (use a 600000 ms timeout or run_in_background; -Full once, at the end).
- End commit messages with: Co-Authored-By: <your model name> <noreply@anthropic.com>
```

## Merging an agent (orchestrator checklist)

1. **Read the report and the last progress note.**
2. **Review the diff.** Run `git diff --stat nr01/integration..nr01/<branch>`, then check:
   - files outside the agent's ownership (the README table);
   - contract changes, which are not allowed;
   - deleted or weakened assertions;
   - the compatibility rules (contracts §7).
3. **Merge on the main checkout** (`F:\coaching-center-platform`, branch `nr01/integration`): `git merge --no-ff nr01/<branch>`. In `AuditLogIT.API_OPERATIONS` conflicts, keep every agent's block.
4. **Run the full suite:** `powershell -NoProfile -File docs\exam-site\new-requirements\nr-01\tools\nr01-mvn.ps1 -Agent INT -Full` (in the background). Then count the surefire reports in `api\target\surefire-reports`. `BookOrderIT` and `CoursePurchaseIT` may fail on a reused database; rerun those alone with `-ResetDb`.
5. **Update this file.** If others are still running, use `SendMessage` to tell them to merge `nr01/integration`, or note it for their restart.
6. **After B merges,** tell A to re-enable the package badge test.

## Model choice (Shahriar, 28 October 2026, after the first limit)

The orchestrator runs on **Sonnet 5.5**, and **every agent runs on Haiku 5.5**. Relaunched agents use `model: haiku`, whatever they ran on before.

## Test-run rule (Shahriar, 28 October 2026)

To cut runs, agents test **once per slice**, in one invocation (the slice's new classes plus the existing classes it touched), and rerun only failing classes. Agents **do not run `-Full`**. The orchestrator runs the full suite on `nr01/integration` after each merge, and again at the end. Any resumed agent prompt must say this.

## Log

- 28 Oct: F merged, plus orchestrator fixes (`ApiExceptionHandler` field errors, primary package by `added_at`, contracts §8). A, B, C and D launched.
- 28 Oct, about 18:40: all four agents stopped on the account session limit.
  - C had committed C1 (`2b22271`) and C2 (`d58fa04`), with C3 uncommitted.
  - D, B and A had committed nothing, but had 22, 35 and 10 uncommitted files.
- 28 Oct, 19:05: all four relaunched on Haiku, told to continue their uncommitted work and commit each slice as soon as it is green.
- 28 Oct: A committed A1 (`72e0279`). Orchestrator answers to A:
  - **EXAM_UPDATED notice.** It fires on a change to `liveAt`, `endAt`, the duration, the written paper version, or MCQ question content (stem, options, key, marks, `multipleAnswer`, explanation). For MCQ the questions are the paper.
  - **Copy** takes the staff working copy, not the published rows.
  - **`myStatusDetail`** may mirror `myStatus` until D merges.
  - **A's edit to D's `LiveEvaluationIT`** clash test is accepted (D17). Resolve it at D's merge.
- 28 Oct, about 20:10: **C merged** into `nr01/integration` (`a44668e`). C's own full run: 475 tests, 0 failures. Full suite on the merged result: running.
  - C's follow-ups, for integration:
    - `AnnouncementService.TO_MODULE` matches grants by `g.module_id`. An exam grant now has no package, so an exam buyer no longer receives package announcements. This is a small behaviour change; decide in integration whether to include exam buyers through the memberships.
    - Deleting a package keeps its buyers' exam grants (R5). That is intended.
  - Next merge: D, then B, then A.
- 28 Oct, about 20:25: **full suite after C's merge** was 98 of 100 classes green. The two failures were the known pair (`BookOrderIT`, `CoursePurchaseIT`, 409 on a reused database). Rerun on a fresh database (`-ResetDb`): both pass. C's merge is confirmed.
- 28 Oct: **ruling, archived package.** An archived or deleted package gives no access through it; other routes still count and results stay readable. Implemented in `LiveExamAccess` on integration (`a16beae`). Contract §8 is about an exam's visibility, not access through a package.
- 28 Oct, about 21:05: **B merged** (`6fbf4da`). Full suite on a fresh database: 104 of 105 classes green. The one failure is `LiveExamIT.anUndatedExamPublishesAndADatedPublishStillKeepsTheGap`, which asserts the five-minute window gap that D17 removes. A already rewrote it on its branch (`anUndatedExamPublishesAndAnEditedScheduleKeepsTheExamPublished`), so it clears when A merges. Do not "fix" it on integration.
  - To do when A merges: re-enable `PackageMembershipIT.aStudentWhoHeldThePackageBeforeARemovalKeepsTheExamAndALaterHolderDoesNot` (B disabled it, "needs A").
  - Rulings given: package and exam discount fields and the free toggle are Master Admin only (EXAM_PRICE). Both A and B were told.
  - D owes: `MultiAnswerScoringIT`, `LeaderboardPositionIT` green. Rank rule given to D: rank = 1 + count of board rows with a strictly higher score; ties share the better rank; below the pass mark means no rank.
  - A state: A1 to A5 committed (`a74b0bc` is A3+A4, `346c1a2` is A5); A6 awaiting its test rerun; A7 is a note only.
