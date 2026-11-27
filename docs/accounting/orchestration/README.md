# Accounting: build orchestration

How [accounting-plan.md](../accounting-plan.md) is built by several agents at once. Written 27 Nov 2026 for the orchestrator session
and every agent. Read with [contracts.md](contracts.md) and your own workstream file. Status and resume steps:
[ORCHESTRATOR-STATUS.md](ORCHESTRATOR-STATUS.md).

## 1. Shape of the work

| Phase | What | Who | Gate |
|---|---|---|---|
| **1** | Schema, seed and the move of old payments (V107_1, V107_2); shared classes; permission; test helpers. | Agent **F** alone. | Gate 1 |
| **2** | Ledger API, reports and integration API, and the web, in parallel. | Agents **L**, **R**, **W**. | Gate 2 |
| **3** | Integration: the web on the real API, old code removed, rehearsal on a production copy, browser check, docs. | Orchestrator (**INT**), with W if needed. | Gate 3 |
| 4 | Release (plan section 7). | Shahriar's go only. | Live checks |

## 2. Workstreams and ownership

An agent edits only the files it owns. Anything else is a question to the orchestrator, sent in the agent's report.

| Agent | Spec | Owns (create or change) | Branch / worktree |
|---|---|---|---|
| **F** foundation | [f-foundation.md](f-foundation.md) | `db/migration/V107_1__accounting.sql`, `V107_2__accounting_move.sql`; `accounting/` root classes (`AccIds`, `AccRules`, `AccMoney`, `AccDates`, `AccRefusals`, `AccBalances`, `AccPermissionContributor`); `Permissions.java`; `ErrorCode.java` (one code); test helpers `accounting/AccTestData.java`; tests `accounting/AccSchemaIT`, `AccMoveIT`, `AccRulesTest`, `AccMoneyTest`. | `acc/f-foundation` in `F:\cc-wt\acc-f` |
| **L** ledger | [l-ledger.md](l-ledger.md) | `accounting/ledger/**` (groups, types, accounts, payees, entries) and its tests; `offline/OfflineInstructorPay*` (delete), `offline/OfflineInstructorController.java`, `offline/OfflineInstructorService.java` and their tests (`OfflineInstructorPayIT` delete, `OfflineInstructorDataIT`). | `acc/l-ledger` in `F:\cc-wt\acc-l` |
| **R** reports | [r-reports.md](r-reports.md) | `accounting/report/**` and its tests; `finance/**`; `offline/PaymentRecordService.java`, `PaymentRecordController.java`; tests `FinanceIT`, `PaymentRecord*IT`. | `acc/r-reports` in `F:\cc-wt\acc-r` |
| **W** web | [w-web.md](w-web.md) | `web/**`. | `acc/w-web` in `F:\cc-wt\acc-w` |

Shared file rules:
- `common/audit/AuditLogIT.java` route list: each API agent adds its routes in one block marked `// ACC-<agent>`, and removes only the
  routes its spec removes. The orchestrator keeps every block when merging.
- `AccTestData.java` (F): L and R may **add methods at the end** only.
- Shared classes from F (`AccRules`, `AccMoney`, `AccBalances`, …) are read-only for L and R. A needed change is a question.

## 3. Order and dependencies

- F starts alone. L, R and W start after **gate 1** passes and F is merged into `acc/integration`.
- L and R do not call each other's classes. Both read the `acc_` tables and use F's shared classes. R's tests insert entries through
  `AccTestData`, not through L's services.
- W works against the contract. Its tests mock `apiRequest` with fixtures (as `web/tests/instructor-pay/fixtures.ts` does). It moves to
  the real API in phase 3.
- Merge order after phase 2: **L, R, W**. Each merge is followed by the API full suite.

## 4. Branches and worktrees

- Integration branch: **`acc/integration`**, made from **`demo/client-review`** (production), not from `lms/integration`. The courses
  build ships separately (plan section 5.1 and 9).
- Worktrees: `git worktree add F:\cc-wt\acc-<x> -b acc/<branch> acc/integration`. Copy `F:\coaching-center-platform\api\.env` into the
  worktree's `api\` (git-ignored). Web worktree: junction `web\node_modules` to the main checkout's
  (`cmd /c mklink /J F:\cc-wt\acc-w\web\node_modules F:\coaching-center-platform\web\node_modules`). The main checkout is on
  `lms/integration`; the junction only shares packages, which are the same on both branches. If `package.json` differs between the
  branches, run `npm ci` in the worktree instead and tell the orchestrator.
- Agents commit each finished slice on their branch and write a progress note `progress/<x>-<slice>.md` (built, tests run and their
  result, open questions). A slice counts as done only with both.
- Nothing is pushed. Nothing is merged into `demo/client-review` without Shahriar's go.

## 5. Tests and gates

The standing rule is **no test runs unless Shahriar says so** (`no-tests-unless-asked`). For the courses build he lifted it ("run the
API and web suites after each major stage"). **Shahriar allowed tests for this build too (27 Nov)**: agents run their test
classes per slice, and the orchestrator runs the gates.

API, always through the runner (own database per agent `cc_test_acc_<x>`, shares the machine-wide slots and lock with the courses
runner, full runs exclusive):

```
powershell -NoProfile -File docs\accounting\orchestration\tools\acc-mvn.ps1 -Agent <X> -Tests "ClassA,ClassB"
powershell -NoProfile -File docs\accounting\orchestration\tools\acc-mvn.ps1 -Agent INT -Full
```

- Agents run their own new and touched test classes once per slice, in one call, and rerun only failing ones. Agents do **not** run
  `-Full` (F: once, at the end of phase 1). Use a 600000 ms timeout or run in the background.
- After a migration file changes, rerun with `-ResetDb` (Flyway checksum).
- Web (W only, in its worktree): `npx tsc --noEmit -p .` and `npx vitest run tests/accounting` per slice; `npm run lint` and the full
  `npx vitest run` at the end.

| Gate | Run by | Must pass |
|---|---|---|
| **Gate 1** (after F) | Orchestrator on `acc/integration` | API full suite; web full suite, lint, typecheck (baseline). |
| **Gate 2** (after L, R, W merged) | Orchestrator | API full suite after each merge; web full suite, lint, typecheck, `npm run build`. |
| **Gate 3** (after integration) | Orchestrator | API full suite; web full suite, lint, typecheck, build; the production-copy rehearsal (plan section 7 step 1); Shahriar's browser check of every accounting screen on the real API, on a phone width too. |

### 5.1 Test isolation (read before writing any IT)

The test database is **shared by every test class and context and never emptied** (`PostgresIntegrationTest`: no truncation). So:

- Every test makes **its own** groups, types, payees and accounts, with names carrying a random suffix (`"Printing " + UUID`), and
  filters by them. Never rely on a name being free.
- The seeded rows (fixed ids) are shared: a test may **use** group 1 (Offline instructors) and the seeded types and accounts, but never
  archives, renames or reorders them. Tests of archive, rename and order use groups they made.
- Figures that span everything (home, owed report, by month, Finance totals) are asserted as a **difference**: read the figure, add the
  test's entries, read again, compare the change. Or use dates in a year no other test uses (each test class picks its own, e.g. 2031
  for `AccDashboardIT`, 2032 for `AccReportsIT`) when the figure takes a date range.
- "Today" comes from the `Clock` bean; follow how existing ITs fix the clock if a test needs "this month".

### 5.2 Branch flow to production (Shahriar, 27 Nov)

Build and test on `acc/integration` only. After gate 3 and Shahriar's browser check: merge `demo/client-review` into `acc/integration`
once more and rerun the suites, then merge `acc/integration` into `demo/client-review` (`--no-ff`), run every suite again there, and
release from that (plan section 7). If `demo/client-review` gets a hotfix mid-build, merge it into `acc/integration` at once and check
the migration-number rule (plan 5.1).

## 6. Rules every agent follows

- Read `contracts.md`, the plan sections your spec names, and your spec before writing code. Do not change a shape.
- Java style as `offline/` (records for DTOs, `@Transactional` services, `JdbcTemplate`, controllers under `/api/v1/...`, the permission
  check the offline controllers use). Comments in plain English, like the code around them.
- Every write is audited through `AuditRecorder` (plan 6.3).
- Dates are Dhaka days (`AccDates`); instants UTC.
- Bangla text follows `natural-bangla-copy`: natural Bangla, the site's existing terms, never a word-for-word translation.
- Never touch production, the sample or dev databases, the running dev servers, or another agent's worktree. Tests use only the
  runner's database.
- Your branch starts from `demo/client-review` (production). It has **no** course (`lms`) code; do not copy from `lms/integration` or
  the main checkout, which is on that branch. Read code only in your own worktree.
- Before you finish a slice: `git status` shows nothing left over, and the code compiles.
- Unsure about a rule? Pick the safer reading, write it down in the progress note, and continue. Do not stop and wait.

## 7. Agent prompts

Launch each agent with the Agent tool (`subagent_type: general-purpose`, `run_in_background: true`, `model: haiku`, `effort: high`:
Haiku 5.5 at high effort, Shahriar 27 Nov). The prompt is the environment block, then the agent's own line.

```
You are agent <X> of the accounting build. Your worktree is F:\cc-wt\acc-<x>, branch acc/<branch>.
- Your shell does NOT stay in your worktree between calls. Use absolute paths. In Bash start every command with
  `cd /f/cc-wt/acc-<x> &&`; in PowerShell start with `Set-Location F:\cc-wt\acc-<x>;`.
  Never edit under F:\coaching-center-platform\ or another agent's worktree.
- First command: `git rev-parse --show-toplevel` and `git branch --show-current` must show your worktree and branch.
- Read docs\accounting\orchestration\README.md, contracts.md and <your spec file> first, then the plan sections they name.
- Edit only the files your spec says you own (README section 2). Anything else: ask in your report.
- Tests: <"Run tests as README section 5 says" | "Do not run tests; compile and typecheck only">.
  Maven only through: Set-Location F:\cc-wt\acc-<x>; powershell -NoProfile -File docs\accounting\orchestration\tools\acc-mvn.ps1 -Agent <X> -Tests "ClassA,ClassB"
  (600000 ms timeout or in the background). Compile only: the same with -Goal test-compile and no -Tests.
- Commit each finished slice on your branch with a progress note docs\accounting\orchestration\progress\<x>-<slice>.md (built, tests and
  results, open questions). End commit messages with: Co-Authored-By: <your model name> <noreply@anthropic.com>
- Never push, never merge, never touch production, the dev or sample databases, or the running dev servers.
- You are a background subagent. Your final message IS your report to the orchestrator: slices done with commit ids, test totals,
  contract questions, files you needed outside your ownership.
```

- **F:** "Build docs\accounting\orchestration\f-foundation.md, slices F1 to F3, in order. Copy F:\coaching-center-platform\api\.env into
  your worktree's api\ first."
- **L:** "Build docs\accounting\orchestration\l-ledger.md, slices L1 to L4. Copy api\.env as F did."
- **R:** "Build docs\accounting\orchestration\r-reports.md, slices R1 to R3. Finance and Payment records are real-money pages: every
  existing FinanceIT and payment records assertion keeps its meaning. Copy api\.env as F did."
- **W:** "Build docs\accounting\orchestration\w-web.md, slices W1 to W4. First junction web\node_modules (README section 4). No Maven."

Resuming an agent: add "You are resuming. Read your progress notes and `git log acc/integration..HEAD`. Finish or revert any uncommitted
change, then continue from the first slice without a progress note."
