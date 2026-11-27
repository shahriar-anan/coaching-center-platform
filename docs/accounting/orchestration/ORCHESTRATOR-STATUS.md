# Accounting: orchestrator status and resume guide

Read this first if you are a new orchestrator session. Last updated 27 Nov 2026.

## Decisions at launch (Shahriar, 27 Nov)

- Go given. Accounting ships **before** the courses build: migrations stay `V107_1`, `V107_2`.
- Tests allowed (agents per slice, orchestrator gates).
- Agents run on **Haiku 5.5, high effort** (`model: haiku`, `effort: high`).
- Built on its own branch `acc/integration` (from `demo/client-review`); merged into `demo/client-review` only after full testing and
  Shahriar's browser check; deployed from there (plan section 7, README 5.2).

## Where things stand

| Phase | State |
|---|---|
| Plan | Reviewed for gaps and strengthened 27 Nov; committed on `acc/integration`. |
| Phase 1 (F) | Launched 27 Nov in `F:\cc-wt\acc-f`, branch `acc/f-foundation`. |
| Phase 2 (L, R, W) | Waits for gate 1. |
| Phase 3 (integration) | Not started. |
| Release | Not planned yet; needs Shahriar's go. |

## Worktrees

| Worktree | Branch | Use |
|---|---|---|
| `F:\cc-wt\acc-int` | `acc/integration` | Orchestrator: merges, gates. |
| `F:\cc-wt\acc-f` | `acc/f-foundation` | Agent F. |
| `F:\cc-wt\acc-l`, `acc-r`, `acc-w` | `acc/l-ledger`, `acc/r-reports`, `acc/w-web` | Phase 2 (create from `acc/integration` after gate 1). |

The main checkout `F:\coaching-center-platform` stays on `lms/integration` (courses build). Do not switch it.

## Merging an agent

1. Read its report and last progress note.
2. `git diff --stat acc/integration..acc/<branch>`: files outside its ownership; contract changes; deleted or weakened assertions,
   above all in `FinanceIT` and payment records tests (R).
3. In `F:\cc-wt\acc-int`: `git merge --no-ff acc/<branch>`. In `AuditLogIT`, keep every `// ACC-<agent>` block.
4. API full suite from the integration worktree:
   `Set-Location F:\cc-wt\acc-int; powershell -NoProfile -File docs\accounting\orchestration\tools\acc-mvn.ps1 -Agent INT -Full`
   (background). Copy `api\.env` into `F:\cc-wt\acc-int\api\` first.
5. Log the result below.

## Phase 3 (integration, orchestrator)

1. Start a dev API from the integration worktree on a **scratch copy** of the sample database (never the sample itself without
   Shahriar's go: `sample-db-qa`), and the web on it; walk every accounting screen; fix with W.
2. Remove the `OFFLINE_INSTRUCTOR_PAY` grant line (and the constant) if L and R report nothing checks it. **Keep**
   `OfflineInstructorPermissionContributor` and its `FINANCE_READ` grant.
3. Rehearse on a production copy (plan 7 step 1) and record the Finance comparison here.
4. Merge `demo/client-review` into `acc/integration`; gate 3; Shahriar's browser check (desktop and phone width).
5. On his word: merge into `demo/client-review` (README 5.2), suites again, update `docs/exam-site/current-api.md`, then the release.

## Log

- 27 Nov: plan and orchestration written; reviewed for gaps (test isolation on the shared database, `STALE_VERSION`, batches never
  deleted, `FINANCE_READ` in the instructor contributor, legacy counted charges without a price, archived rows on edits, copy-twice
  guard, linked-user uniqueness, payee of a moved class payment, migration numbering after hotfixes, branch flow to production).
