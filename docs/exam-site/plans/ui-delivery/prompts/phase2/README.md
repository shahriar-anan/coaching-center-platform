# Phase 2 student pages: three agents in parallel

Source plan: [student-purchase-lms-plan.md](../../student-purchase-lms-plan.md) (version 2), sections 4, 6, 8 and 9. Seed data: [seed-data-plan.md](../../seed-data-plan.md).

## Who runs

| Agent | Prompt | Branch | Worktree |
|---|---|---|---|
| PUR-FND | [agent-PUR-FND.md](agent-PUR-FND.md) | `ui/pur-fnd` | `F:\cc-wt\pur-fnd` |
| STU-PUR | [agent-STU-PUR.md](agent-STU-PUR.md) | `ui/stu-pur` | `F:\cc-wt\stu-pur` |
| STU-LMS | [agent-STU-LMS.md](agent-STU-LMS.md) | `ui/stu-lms` | `F:\cc-wt\stu-lms` |

All three start from `catalog-acquisition` (at `a6984f7`, with every WS9 branch merged). The three run at the same time. This is allowed by `ws9-stages.md` (at most three at a time on this PC), and none of the three runs an API agent.

## Why they can run at the same time

The API agents (A1, A2, A3, A4) have not started yet. So the three web agents build against the contract shapes in plan section 8, with mocked responses in their tests. Each page must keep working when the endpoint is missing (older API): it falls back to the current behaviour and says so in the report. When an API lands, the fallback is removed in a named commit.

Ownership is split so that no two agents edit the same file:

- PUR-FND owns `web/src/components/ui/**` and the new purchase components. It does not touch student pages.
- STU-PUR owns the catalog and purchase pages, with one exception (`my-modules-page.tsx`, owned by STU-LMS).
- STU-LMS owns notices, results, my modules, and the written-paper copy.

Message keys are split the same way (see each prompt). Nobody edits `web/src/i18n/messages/index.ts`. If a new namespace is needed, the agent asks the integrator.

## Order inside the run

1. Every agent: `git worktree add`, `npm ci` in `web/`, read the prompt and the reading list, and write a short "started" line to the report.
2. PUR-FND: first commit is the stubs with the exact props (in its prompt). It tells the integrator when the stubs are committed. STU-PUR and STU-LMS can use their own stand-ins in their own folder until PUR-FND merges.
3. Each agent builds its items, one commit per item ID, each commit naming the ID.
4. Builds are serial. See the lock rule below.
5. Each agent sends READY TO MERGE. The integrator merges PUR-FND first, then STU-PUR and STU-LMS (STU-LMS can merge any time after its own checks, since it shares no component with STU-PUR).

## Rules for all three

- Work only inside your worktree (check with `git rev-parse --show-toplevel`). Never write under `F:\coaching-center-platform\` except the `.heavy.lock` file, and never edit the other agents' worktrees.
- Do not run `next dev`, do not open a browser, do not start the API. Test with mocked `fetch`.
- **Lock.** `npm run build` only while `F:\coaching-center-platform\.heavy.lock` holds your name. Names: `PURFND`, `STUPUR`, `STULMS`. If the file exists and holds another name, wait (poll with a Monitor that ends when the file is gone, every 30 seconds). Never delete another agent's lock. Delete your own lock when the build ends, pass or fail.
- Single-process tests: `npm test`.
- Tokens only: no hex, no raw palette colours, no `text-[..]`, no `text-sm`. Use the type scale and the FND tokens. Look at [client-mockup.html](../../../../ui/client-mockup.html) for the look (navy and gold, Hind Siliguri, 16 px cards, 12 px buttons).
- Every new or changed screen has loading, empty, error and success states, works at 375 px and 1440 px, in Bangla and English, in light and dark, and by keyboard.
- Copy: read [bangla-copy.md](../../bangla-copy.md) first. Use the glossary terms. Bengali digits through the formatters.
- Commit small: one commit per item ID, the message starts with the ID, and ends with the attribution line from this session's instructions.
- Do not change any API response, route, or schema. If you need a change, write it in your report under "Contract questions" and stop that item.
- Report: fill in a section "Report — <agent>" at the end of `docs/exam-site/plans/ui-delivery/ws9-gaps-and-polish.md` on your branch. Do not edit other sections. Then send the ready message.

Ready message:

```
READY TO MERGE: <branch> (agent <PUR-FND|STU-PUR|STU-LMS>)
Last commit: <hash>
Checks: lint <0 errors>, tsc <clean>, npm test <n passed in n files>, build <passed under lock NAME>
Items done: <IDs> | partly: <IDs and what is left> | skipped: <IDs and why>
Contract questions: none | <list>
Stand-ins still in use: none | <list and the file>
Notes for other agents: <anything>
```

## Launching

The integrator runs these three commands once, from `F:\coaching-center-platform`:

```
git -C F:/coaching-center-platform worktree add F:/cc-wt/pur-fnd -b ui/pur-fnd catalog-acquisition
git -C F:/coaching-center-platform worktree add F:/cc-wt/stu-pur -b ui/stu-pur catalog-acquisition
git -C F:/coaching-center-platform worktree add F:/cc-wt/stu-lms -b ui/stu-lms catalog-acquisition
```

Then each agent is given its prompt file as its task.
