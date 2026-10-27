# WS9 stage 2: the staff side (AUT, MRK, OPS)

Source: [ws9-stages.md](../../ws9-stages.md) (stage 2 rows and the rules), [ws9-gaps-and-polish.md](../../ws9-gaps-and-polish.md) (the item text), and the API report in that file (the G-items are merged).

Stage 1 (FND, API, STU) is merged. Stage 2 can start: the staff agents build on the real components and the real API.

## Who runs

| Agent | Prompt | Branch | Worktree | Lock name |
|---|---|---|---|---|
| AUT (exam authoring and admin home) | [agent-AUT.md](agent-AUT.md) | `ui/w9-authoring` | `F:\cc-wt\w9-aut` | `AUT` |
| MRK (instructor dashboard and marking) | [agent-MRK.md](agent-MRK.md) | `ui/w9-marking` | `F:\cc-wt\w9-mrk` | `MRK` |
| OPS (results, people, payments) | [agent-OPS.md](agent-OPS.md) | `ui/w9-ops` | `F:\cc-wt\w9-ops` | `OPS` |

Create the worktrees from `catalog-acquisition`, which now includes all of stage 1:

```
git -C F:/coaching-center-platform worktree add F:/cc-wt/w9-aut -b ui/w9-authoring catalog-acquisition
git -C F:/coaching-center-platform worktree add F:/cc-wt/w9-mrk -b ui/w9-marking catalog-acquisition
git -C F:/coaching-center-platform worktree add F:/cc-wt/w9-ops -b ui/w9-ops catalog-acquisition
```

## Running them together, and the order

- **At most three agents at a time** on this PC (`ws9-stages.md`). The three staff agents fill that limit, so the other agents wait for one to finish:
  1. the follow-ups for FND and STU, and the phase 2 API-ADMIN agent, run first (they are small), or
  2. the three staff agents run first, and the follow-ups follow.
  Either way, do not run more than three at once.
- The staff agents do not overlap with each other. Each owns its own files, listed in its prompt.
- **Coordination points** (the only places two of them touch the same page):
  - `exams/[examId]/page.tsx` is AUT's. OPS adds a link to the new results report from it, once OPS's route exists. AUT leaves a marked slot for the answer-release switch, which phase 2 fills later.
  - `students/[id]` is OPS's. MRK does not touch it. OPS leaves a marked slot for the access section (phase 2).
  - `people/submission-form.tsx` moves to MRK's `marking/` folder. OPS must not edit it.
- Message keys: each agent fills its own namespace (`staffCatalog`, `staffMarking`, `staffPeople`, `staffOps`, `staffResults`). FND registered the last two as empty files. Nobody edits `messages/index.ts`.

## Rules for all three

- The **staff look** is the admin style in [admin-prompt.md](../../../../ui/admin-prompt.md). It is not the student mockup. No redesign, no renamed routes, no changed business behaviour.
- Tokens and the FND type scale only. No `text-[..]`, no `text-sm`/`text-xs`/`text-base`/`text-lg`/`text-xl`/`text-2xl`, no raw palette colours, no hex. Use FND's `Stepper`, `SummaryStrip`, `SplitBrowser`, `RowMenu`, `ShortcutsPopover`, `SectionNav`, `StickyActionBar`, `PrintArea` and the hooks `useUnsavedChangesGuard`, `useListPrefs`, `useDraftForm` as the contracts say.
- Every new list or page: loading, empty, error and success states; 375 px and 1440 px; both languages; both themes; keyboard.
- Use the API as merged. Read the G-items and the "API additions" in `ws9-gaps-and-polish.md`. Do not change any API response.
- Bangla copy: [bangla-copy.md](../../bangla-copy.md). Bengali digits through the formatters.
- Commit small: one commit per item ID, the message starts with the ID, ends with the attribution line.
- Lock: `npm run build` only while `F:\coaching-center-platform\.heavy.lock` holds your name. If another name is there, wait (poll every 30 s). Never delete another agent's lock. Delete yours when the build ends.
- No `package.json` change, except F9 (`@dnd-kit`), which needs the integrator's approval first.
- Do not run `next dev`, do not open a browser. Tests use mocked `fetch`.
- Report: "Report — <agent>" at the end of `ws9-gaps-and-polish.md` on your branch. Then the ready message from `ws9-stages.md`.
