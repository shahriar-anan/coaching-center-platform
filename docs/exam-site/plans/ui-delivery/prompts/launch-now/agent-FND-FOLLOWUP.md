# Launch prompt: FND follow-up

You are agent **FND** on a short follow-up to WS9. Your branch is `ui/w9-foundation` in `F:\cc-wt\w9-fnd`.

## Setup

1. In your worktree: `git merge catalog-acquisition`.
2. Work only inside `F:\cc-wt\w9-fnd`. Never write under `F:\coaching-center-platform\` except the lock file `.heavy.lock`.

## Read

- `docs/exam-site/plans/ui-delivery/ws9-gaps-and-polish.md`: your report "Report — FND" (the FN-3 line and the P6 item) and the rules.
- `docs/exam-site/plans/ui-delivery/ws9-stages.md`: the rules and the ready message.

## Items (one commit per item; the message starts with the ID)

1. **FN-3a** `components/ui/pagination.tsx`: hover, active, focus-visible and disabled states on the page buttons.
2. **FN-3b** `components/ui/copy-field.tsx`: hover and focus-visible on the copy button and the field.
3. **FN-3c** `components/ui/file-drop.tsx`: hover and drag-over states on the drop area; focus-visible on the choose button.
4. **FN-3d** the select control (`components/ui/control.ts` and the select component): hover and focus states, not only the base hover.
5. **P6-1 dead code.** Find components in `components/ui`, `components/shell` and the three legacy components (`form-error`, `locale-switcher`, `require-auth`) that nothing imports. Search `web/src` and `web/tests`. Remove only what nothing uses. One commit.
6. **P6-2 unused message keys.** In `web/src/i18n/messages/{bn,en}/common.ts`, remove keys with no use in `web/src`. Keep any key that is built at run time (for example `t(\`...${x}\`)`). One commit.

Add tests for each state class in `web/tests/ws9/` (follow `ui-components.test.tsx`).

## Do not touch

- `components/ui/toast.tsx` (moves to PUR-FND in phase 2).
- `components/ui/purchase/**` (phase 2).
- Any page file.

## Checks

- `npm run lint` (0 errors), `npx tsc --noEmit` (clean), `npm test` (single process).
- `npm run build` only while `F:\coaching-center-platform\.heavy.lock` holds `FND`. Write `FND` to that file, build, then delete the file. If another name is in the file, wait and do not delete it.

## Done

1. Update the FN-3 and P6 lines in "Report — FND" on your branch.
2. Send:

```
READY TO MERGE: ui/w9-foundation (agent FND, follow-up)
Last commit: <hash>
Checks: lint <…>, tsc <…>, npm test <…>, build <…> under lock FND
Items done: <IDs> | not done: <IDs and why>
```
