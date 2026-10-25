# Agent D — round 2: finish WS4 and report

State when this was written (12:44, 25 October): your branch `ui/ws4-student-sitting` in `F:\cc-wt\ws4` is up to date with `catalog-acquisition`, has "Finish WS4 on the real foundation…" committed, and has four uncommitted route pages where you were adding `Suspense` wrappers. Your Report section is still empty. You are **first in the merge order**: agents E and B reuse your PdfViewer.

Work only inside `F:\cc-wt\ws4` (check `git rev-parse --show-toplevel`). Read-only reference: your original prompt `docs/exam-site/plans/ui-delivery/prompts/agent-D.md` and brief `ws4-student-sitting.md`.

1. Finish the `Suspense` change in the four pages (`exam/[examId]/sit`, `result`, `leaderboard`, `results`) and commit.
2. Run the checks from `web/`:
   - `npm run lint` (0 errors).
   - `npm test` (single process by default now; `npm test -- tests/ws4` for yours only).
   - `npm run build` under the lock: create `F:\coaching-center-platform\.heavy.lock` containing `D`; if it exists, wait a minute and check again; delete it when the build ends.
   Fix anything that fails in your own files.
3. Confirm the PdfViewer layout: WS0's `components/pdf/pdf-viewer.tsx` (the lazy wrapper) is unchanged, and your viewer body is in `pdf-viewer-impl.tsx` with the props `{ load, watermark?, allowDownload?, fileName? }`.
4. If anything P0 in your brief is still missing, finish it before reporting. Otherwise send READY first, and do the `(P1)` and `(P2)` items afterwards on the same branch.
5. Fill in the **Report** section at the end of `docs/exam-site/plans/ui-delivery/ws4-student-sitting.md`: what is done, P1/P2 done or skipped, the PdfViewer props, notes for others. Commit, then send:

```
READY TO MERGE: ui/ws4-student-sitting (agent D)
Last commit: <hash>
Checks: lint 0 errors, tests <n> passed, build ok
PdfViewer: props unchanged | <change>
P1 done: <list> | P2 done: <list> | skipped: <list>
Contract issues: none | <list>
Notes for other agents: <anything they must know>
```

Do not run `next dev` or the API, do not open a browser, do not edit files outside your Owns list.
