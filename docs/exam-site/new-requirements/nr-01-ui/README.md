# NR-01 UI plan (the website and staff screens)

Set up 29 October 2026 on branch `ui/nr01-ui`, worktree `F:\cc-wt\nr01-ui`, cut from `demo/client-review` at `e826aa0` (the NR-01 API, verified: 603 tests green).

**Status:** set up. **Scope not yet written.** Shahriar will describe the UI work; the scope goes into this file before any worker starts.

**Audience:** the orchestrator (the main session) and the workers it starts.

## Ground truth to read first

1. [../NR-01-exam-segment.md](../NR-01-exam-segment.md): the decisions, including the UI items listed under "UI" in [../nr-01/web-impact.md](../nr-01/web-impact.md) and `../nr-01/web-impact/*.md`.
2. [../nr-01/contracts.md](../nr-01/contracts.md), section 7: the compatibility rules the website still relies on (legacy `kind`, `myStatus`, the `revisionId` fallback). Retire a compatibility rule only in the UI step that stops using it.
3. [../../current-api.md](../../current-api.md), section "Added 2026-10-28 (NR-01...)".
4. [../rules.md](../rules.md): look, copy, accessibility and safety rules. They apply to the UI too.

## Token rules (apply every time)

- **One session per slice.** Start a fresh session for each slice. Don't continue a long one.
- **Workers are short-lived.** Each worker gets one bounded slice, finishes it, commits, and reports in a few lines. No waiting around on a lock or a background run: start the run, wait for its completion notice once, then read the result.
- **Cheap model for implementation** (Haiku), stronger model for review and decisions (Sonnet or Opus). The orchestrator keeps decisions; workers keep to the brief.
- **Search before reading.** Grep for the symbol, then read a 50 to 100 line window. Don't read whole files over 500 lines.
- **Run only what changed.** Lint and the component tests for the touched folders during a slice. The full web check (lint, all unit tests, build) once per merge, not per edit.
- **Status in one line.** Report decisions and blockers; keep progress chatter out.
- **Batch questions.** One numbered list per round.

## Checks

- Web: `npm run lint`, `npm test`, `npm run build` in `web/` (the same three as the API's web checks). Run them under the heavy lock, through the same method used for Maven, so two heavy jobs never overlap.
- No API change is expected. If a UI slice needs one, stop and raise it: API work goes through the NR-01 rules, not this plan.
- Accessibility and responsive checks follow [../rules.md](../rules.md). Browser passes are Shahriar's.

## Workers

Before a worker starts, `web/node_modules` must exist in its worktree (`npm ci` once, in `F:\cc-wt\nr01-ui\web`, or per new worker worktree). Each worker works on its own branch cut from `ui/nr01-ui`, merged back in order.

Brief template (paste at the start of each worker):

```
You are a worker on the NR-01 UI plan. Worktree: <path>, branch <branch>, cut from ui/nr01-ui.
Your slice: <slice name and the exact files it may change>.
Read only: docs/exam-site/new-requirements/nr-01-ui/README.md, the slice section, and the files named in it.
Do not change files outside your slice. Do not change API code. Do not push.
Run only the tests for the files you changed. Commit when green, with a short progress note in
docs/exam-site/new-requirements/nr-01-ui/progress/. Report in five lines: slice, commit, tests run and result, files changed, blockers.
```

## Progress

- Baseline: [progress/baseline.md](progress/baseline.md) records the starting checks on `ui/nr01-ui`.
