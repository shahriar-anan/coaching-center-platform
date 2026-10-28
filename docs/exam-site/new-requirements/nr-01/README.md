# NR-01 API plan: exam segment

Written 28 October 2026 on branch `demo/client-review` at `000e131`. Status: plan, waiting for Shahriar's go.

**Audience:** the orchestrator (Shahriar's main Claude session) and the five agents it gives work to.
**Source of truth:** [../NR-01-exam-segment.md](../NR-01-exam-segment.md), the requirement and every agreed decision. [../rules.md](../rules.md) applies to every slice. Where this folder and `NR-01-exam-segment.md` disagree, the requirement file wins: stop and ask the orchestrator.

## Scope

In scope: **API and data only.** This means Flyway migrations, entities, services, endpoints, permissions, audit, notices, `*IT` tests, the demo seed, and the API docs.

Out of scope: every web change, including the sidebar Exams page, the MCQ radio circle and the labels. The web gets its own plan later; this plan writes [web-impact.md](web-impact.md) as its input. **The current website must keep working against the new API**, under the compatibility rules in [contracts.md §7](contracts.md#7-compatibility-rules-the-current-website-keeps-working).

## Outcome

When this plan is done, the API does all of the following. Each item is covered by `*IT` tests against real PostgreSQL.

1. **Window and duration are separate.**
   - An exam has a window (`liveAt` to `endAt`) and a duration. The window is at least as long as the duration.
   - A sitting's deadline is its own start plus the duration.
   - A first attempt started inside the window is `LIVE`; one started after it is `ARCHIVED`.
   - Practice exams (`mode = PRACTICE`) have no window.
2. **Attempts.**
   - MCQ: unlimited retakes.
   - Written: one retake, and only with a grant.
   - Live or archived is decided at start and never changes.
3. **MCQ scoring.**
   - Multi-answer questions; negative marks per exam; decimal scores; the per-exam "answers can be changed" switch.
   - The result, the answers and the explanations come back at submit, with the current live position or the relative position.
4. **Leaderboard.**
   - Live attempts only (first attempts for practice), at or above the pass mark.
   - Students see only their own position until the window ends.
5. **Catalog.**
   - The exam's home is a category, and packages bundle exams.
   - One exam can be in several packages, or in none.
   - The instructor is set on the exam.
   - Popular flags, favourites, preview, and a staff list of exams across packages.
6. **Commerce.**
   - Percent discount with an optional end date, on exams and packages.
   - Free exams.
   - Per-exam access days or lifetime access, counted from the window end.
   - A student keeps an exam when it is removed from their package.
7. **Editing at any time.**
   - No schedule or content lock.
   - Published exams and packages take **staged edits**. The Master Admin reads a field-by-field change list, then publishes.
   - Publishing re-scores MCQ papers when scoring changed, and sends an "exam updated" notice when the time or the paper changed.
   - Written papers are versioned, so each student and their evaluator see the paper that student sat.
   - Questions and options cannot be added or removed after the first sitting.

## Files

| File | What it is | Who |
|---|---|---|
| [contracts.md](contracts.md) | **Frozen** shared contracts: schema, entity fields, enums, helper signatures, error codes, permissions, endpoint list, compatibility rules. | Everyone reads it first. Only the orchestrator changes it. |
| [f-foundation.md](f-foundation.md) | Stage 1: migrations, entities, pure rule helpers, and keeping current behaviour green. | Agent **F** |
| [a-authoring.md](a-authoring.md) | Exam authoring: new fields, no lock, multi-answer authoring, paper versions, staged edits and the change list, publish, re-score, the update notice, preview, the pending badge. | Agent **A** |
| [b-packages.md](b-packages.md) | Packages and catalog: membership, standalone exams, staff exam list, package staged edits, popular, favourites. | Agent **B** |
| [c-commerce.md](c-commerce.md) | Commerce and access: discount, free, access expiry, orders, grants, access lists. | Agent **C** |
| [d-sitting.md](d-sitting.md) | Sitting, scoring, results and marking: attempts, retakes, retake grants, answers, positions, leaderboard, instructor on the exam, paper version for evaluators, student status, counts. | Agent **D** |
| [integration.md](integration.md) | Stage 3: merges, the full test run, the demo seed, docs, `decisions.md`. | Orchestrator, or one agent it names |
| [web-impact.md](web-impact.md) and `web-impact/<agent>.md` | What the later UI plan must change. One file per agent, so there are no merge conflicts. | All |
| [tools/nr01-mvn.ps1](tools/nr01-mvn.ps1) | The only way to run Maven: heavy lock, per-agent database, credentials kept out of sight. | All |
| [prompts.md](prompts.md) | The text to paste when starting each agent. | Orchestrator |

## Stages and team

```
Stage 1  F ──────────────► merge into nr01/integration
Stage 2                    ├─ A authoring ─┐
                           ├─ B packages  ─┤  each merges nr01/integration often,
                           ├─ C commerce  ─┤  reports done, orchestrator merges
                           └─ D sitting   ─┘
Stage 3                    orchestrator: integration.md (seed, docs, full run) ─► Shahriar
```

- **Stage 1 is serial.** Agent F runs alone. A, B, C and D do not start until F is merged, because they build on F's schema, entities and helpers.
- **Stage 2 runs in parallel.** A, B, C and D start together from the merged F.
- **Stage 3** is done by the orchestrator, or by one agent it names.

Size guide: F about 1 day, A 1.5 days, B 1 day, C 0.75 day, D 1.5 days, integration 0.5 day.

**One person, one agent:** if Shahriar runs fewer agents, run them in the order F, D, A, C, B. The plan does not change; one agent simply takes several files in turn.

## Branches, worktrees, test databases

| Agent | Branch | Worktree | Test database |
|---|---|---|---|
| Orchestrator | `nr01/integration` (cut from `demo/client-review`) | `F:\coaching-center-platform` | `cc_test_nr01_int` |
| F | `nr01/f-foundation` | `F:\cc-wt\nr01-f` | `cc_test_nr01_f` |
| A | `nr01/a-authoring` | `F:\cc-wt\nr01-a` | `cc_test_nr01_a` |
| B | `nr01/b-packages` | `F:\cc-wt\nr01-b` | `cc_test_nr01_b` |
| C | `nr01/c-commerce` | `F:\cc-wt\nr01-c` | `cc_test_nr01_c` |
| D | `nr01/d-sitting` | `F:\cc-wt\nr01-d` | `cc_test_nr01_d` |

- **Branches.**
  - The orchestrator first commits this plan folder on `nr01/integration`, so every worktree has it.
  - F's branch is cut from `nr01/integration` first. A–D are cut from it only after F is merged.
- **Worktrees.** Each agent writes **only inside its own worktree**. It checks `git rev-parse --show-toplevel` before its first edit. The single exception is the heavy-lock file below. Nothing is written under `F:\coaching-center-platform\`, which is the orchestrator's checkout.
- **Running Maven: always through [tools/nr01-mvn.ps1](tools/nr01-mvn.ps1).**
  - This PC has **no Docker**, and the full suite **crashes when another heavy job runs beside it**. So every Maven run, for every agent, goes through the script.
  - The script takes `F:\coaching-center-platform\.heavy.lock`, waiting a minute between tries.
  - It uses the agent's own database, `cc_test_nr01_<agent>`, creating it if missing; `-ResetDb` recreates it after a Flyway checksum error.
  - It loads the PostgreSQL user and password from the main checkout's `api/.env` without printing them, and it always releases the lock.
  - **Never run `mvn` directly, and never run two at once.** Keep each run to the classes of your slice.
  - **The full suite (`-Full`) is run only once per agent**, at the end, and in the background, because it takes longer than a tool call. `BookOrderIT` and `CoursePurchaseIT` are a known pair that can fail on a reused database (from the earlier deploy records). If only they fail, rerun them alone with `-ResetDb`; if they pass then, report it.
  - If an agent finds a stale lock (its holder is not running), it tells the orchestrator. It never deletes another agent's lock.
- **Web.** Agents do not touch `web/`. The one exception: the orchestrator runs `npm run lint`, `npm test` and `npm run build` in `web/` during integration, under the heavy lock, to prove nothing broke.

## Ownership

Each production file has one owner in stage 2. An agent may **read** any file. To change a file it does not own, it asks the orchestrator, who either makes the change on `nr01/integration` or hands it over.

| Owner | Files (package `com.coachingcenter.api.exams` unless shown) |
|---|---|
| **A** | `LiveExamService`, `LiveExamController`, `LiveExamRemoval`, `LiveExamAnswerReleaseService`, `LiveExamAnswerReleaseController`. New: `LiveExamRevisionService`, `LiveExamRevisionController`, `ExamChangeListBuilder`, `LiveExamPreviewService`, `LiveExamPreviewController`, `PendingChangesController`. |
| **B** | `LiveExamModuleService`, `LiveExamModuleController`, `LiveExamStaffModuleService`, `LiveExamPublicService`, `LiveExamPublicController`, `LiveExamCatalogVisibility`, `LiveExamCategoryService`, `LiveExamCategoryController`, `LiveExamRosterService`, `LiveExamRosterController`, `ModuleCoverService`, `LiveExamTypeFilter`. New: `PackageMembershipService`, `PackageMembershipController`, `StaffExamListService`, `StaffExamListController`, `PackageRevisionService`, `PackageRevisionController`, `PopularController`, `FavouriteService`, `FavouriteController`. |
| **C** | `LiveExamAccess`, `LiveExamOrderService`, `LiveExamOrderController`, `LiveExamOrderTx`, `LiveExamOrderBundle`, `LiveExamOrderAdminService`, `LiveExamOrderHistoryService`, `LiveExamOrderDecisionController`, `LiveExamGrantAdminService`, `LiveExamGrantAdminController`, `StudentOwnAccessService`, `StudentAccessController`, `StudentLiveExamAccessService`, `StudentLiveExamAccessController`, `LiveExamPaymentRecheck`, `ExamMailService` (purchase mail only). |
| **D** | `LiveExamSittingService`, `LiveExamSittingController`, `LiveExamStudentStatus`, `LiveExamDeadlineJob`, `LiveExamEvaluationService`, `LiveExamEvaluationController`, `LiveExamResultsService`, `LiveExamResultsController`, `LiveExamParticipationService`, `LiveExamParticipationController`, `LiveExamSubmissionFeedService`, `LiveExamSubmissionController`, `LiveExamInstructorModules`, `StudentReminderJob`. New: `LiveExamLeaderboardService`, `RetakeGrantService`, `RetakeGrantController`, `LiveExamInstructorService`. D also owns the instructor's package list (`GET /live-exam-modules` for an instructor), through `LiveExamInstructorModules`. |
| **F only (frozen in stage 2)** | Every Flyway file, every entity and repository, `ErrorCode`, `Permissions`, `LiveExamPermissionContributor`, `AuditRecorder`'s action constants, `StudentNoticeWriter` (F adds `examUpdated` and `retakeAvailable`), and the helpers in [contracts.md §4](contracts.md#4-shared-helpers-built-by-f-frozen-afterwards). Stage 2 agents may **add** a repository query method to a repository. Each one is a separate method, so merges are simple. |
| **Orchestrator** | `DemoContentSeed*`, `docs/exam-site/current-api.md`, `docs/exam-site/decisions.md`, and conflict resolution in `AuditLogIT`. |
| **Shared, append-only** | `AuditLogIT.API_OPERATIONS`: it must equal the implemented routes, or the full suite fails. Each agent adds **its own** new and changed operations, in a block marked with its letter. The orchestrator resolves merge conflicts there. |

**Tests:**
- Each agent owns the existing `*IT` classes its file names. New `*IT` classes are named in each workstream file.
- An existing test that breaks because of a **decided** behaviour change is updated by the owner of that behaviour. It is never deleted just to go green.
- If an agent's change breaks a test it doesn't own, the agent tells the orchestrator which test failed and why.

## How agents work

1. Read, in order:
   - [../NR-01-exam-segment.md](../NR-01-exam-segment.md);
   - [../rules.md](../rules.md);
   - [contracts.md](contracts.md);
   - your workstream file;
   - [../../current-api.md](../../current-api.md) for the parts you touch.
2. Work slice by slice in the order of your file.
   - A slice is done when its named tests pass through `tools/nr01-mvn.ps1 -Agent <X> -Tests "<your classes>"`, and you've written a progress note in `docs/exam-site/new-requirements/nr-01/progress/<agent>-<slice>.md`. The note says what changed, which tests ran with their real result, and any deviation.
   - Commit each slice on your branch. Use the commit attribution line from the session, and never use `--no-verify`.
3. Merge `nr01/integration` into your branch at the start of each slice. If it conflicts outside your files, stop and ask.
4. **Never change a contract on your own.** If one is wrong or missing, stop and write the question to the orchestrator. Suggest the exact change and the reason. Keep working on slices that don't depend on it.
5. Append your API changes to `docs/exam-site/new-requirements/nr-01/api-changes/<agent>.md`, in the table format of `current-api.md`. The orchestrator folds them in. Write UI consequences to `docs/exam-site/new-requirements/nr-01/web-impact/<agent>.md`. Never edit another agent's file.
6. Before reporting done:
   - merge `nr01/integration` one last time;
   - run the **full** suite through the script (`-Full`, in the background);
   - report, with the real output for any failure:
     - slices done;
     - test command and counts;
     - files changed outside your ownership (should be none);
     - open questions;
     - anything skipped.
7. Do not push, deploy, touch staging or `client-vps`, or run the web dev server.

## Start order

1. **F alone.**
2. **When F is merged, C, D, B and A, in that order, a few minutes apart.** They may all run together, because ownership keeps them out of each other's files. Starting C first lets its access slice (C1) reach `nr01/integration` early. That order is also the merge order.

The ready-to-paste prompts are in [prompts.md](prompts.md).

## Orchestrator duties

- **Before stage 1:**
  - commit this plan on a new `nr01/integration` branch, cut from `demo/client-review`;
  - cut `nr01/f-foundation` and create its worktree;
  - start F with [prompts.md](prompts.md).
- **After F reports:**
  - review the diff against [contracts.md](contracts.md);
  - run the full suite (`tools/nr01-mvn.ps1 -Agent INT -Full`);
  - merge F into `nr01/integration`;
  - correct `contracts.md` wherever F's final code differs (F says where), and commit that **before** cutting A–D.
- **Stage 2:**
  - answer questions;
  - make cross-owner edits on `nr01/integration` and tell the affected agents to merge it;
  - merge each agent when it reports done and its full test run is green. Order: **C, D, B, A** (the same as the start order). Access is used by everyone, D's sitting by A's re-score, and A is the largest.
  - After each merge, run the full suite on `nr01/integration` and tell the others to merge it.
- **Stage 3:** [integration.md](integration.md).
- **Keep Shahriar informed.** Send a one-line status after each merge. Ask him before any decision not covered by `NR-01-exam-segment.md`.

## Done means

- Every slice in the five workstream files is done, or listed as skipped with Shahriar's OK.
- On `nr01/integration`:
  - `mvn test` is green against PostgreSQL;
  - `npm run lint`, `npm test` and `npm run build` in `web/` are green with no web source change, apart from what [web-impact.md](web-impact.md) marks as a required compatibility fix.
- `current-api.md`, `decisions.md`, the NR-01 record and the demo seed are updated.
- Shahriar has the merge summary and the list of things to click on staging after the later UI plan. Nothing is deployed by this plan.
