# Launch now: three agents, and the order of everything after

## Start now (three agents, in parallel)

| Prompt | Agent | Worktree | Branch | Lock name |
|---|---|---|---|---|
| [agent-FND-FOLLOWUP.md](agent-FND-FOLLOWUP.md) | FND | `F:\cc-wt\w9-fnd` (existing) | `ui/w9-foundation` | `FND` |
| [agent-STU-FOLLOWUP.md](agent-STU-FOLLOWUP.md) | STU | `F:\cc-wt\w9-stu` (existing) | `ui/w9-student` | `STU` |
| [agent-API-ADMIN.md](agent-API-ADMIN.md) | API-ADMIN | `F:\cc-wt\api-admin` (new) | `ui/api-admin` | `API` |

Their files do not overlap. Only one holds the heavy lock at a time, so builds and Maven runs queue behind the lock file.

## Exact flow

**Step 1. Now.** Start the three agents above.

**Step 2. When FND reports READY.** The integrator runs `git merge --no-ff ui/w9-foundation`, then the gate: lint, `tsc`, `npm test`, and `npm run build` under the lock. Passing means it stays merged.

**Step 3. When STU reports READY.** The integrator merges `ui/w9-student` the same way.

**Step 4. When API-ADMIN sends `V39 READY: <hash>`.** The integrator merges that one commit with `git merge --no-ff <hash>`. This unlocks A1 and A2. Their prompts come later.

**Step 5. Start the next agents as slots free.** At most three run at once. As soon as one finishes, start the next, in this order:
1. **PUR-FND** (phase 2). Start it only after the FND follow-up is merged, because it owns the shared components folder.
2. **STU-LMS** (phase 2). Start it after the STU follow-up is merged.
3. **AUT** (WS9 stage 2, the staff authoring side). It can start any time a slot is free.
4. **STU-PUR** (phase 2). Start it after PUR-FND's stubs are merged.
5. **MRK** and **OPS** (WS9 stage 2).
6. **A1** and **A2** (phase 2 API). After the V39 merge.
7. **A4** (bundle order). After A3's grant merge.
8. **STAFF-ADMIN-UI** (phase 2). After OPS and the A-agents are merged.
9. **SEED-DATA**. After V39 and A2.
10. **QA-E2E** last, when every wave is merged.

**Step 6. Merge rule for every agent.** When an agent reports READY, the integrator merges it with `--no-ff`, runs the gate, and records the result in its report section.

## Rules that apply to all three

- Work only in your own worktree.
- Never write under `F:\coaching-center-platform\` except the lock file.
- One lock name at a time. If the lock holds another name, wait. Never delete someone else's lock.
- Do not run `next dev`. Do not open a browser. Do not start the API outside your own test run.
