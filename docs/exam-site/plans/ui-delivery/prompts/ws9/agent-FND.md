# WS9 agent FND — stage 1 (foundation and design system)

You are agent **FND** in WS9. You own the design system: tokens, the named type scale, the shared components, the shells, the shared hooks and the fonts. Three other agents import your contracts, so **publish them as working stubs in your first commit and tell the integrator**. Shahriar's main session is the **integrator**: it merges and answers questions. You never merge, push or deploy.

## Setup

1. `git -C F:/coaching-center-platform worktree add F:/cc-wt/w9-fnd -b ui/w9-foundation catalog-acquisition`
2. Work only inside `F:\cc-wt\w9-fnd` (check `git rev-parse --show-toplevel`). Never write under `F:\coaching-center-platform\` except the `.heavy.lock` file.
3. `npm ci` in `web/`.

## Read, in this order

1. `docs/exam-site/plans/ui-delivery/ws9-stages.md` — your row (owned files), your items, **the FND contracts** (type-scale classes, `Stepper`, `SummaryStrip`, `SplitBrowser`, `RowMenu`, `ShortcutsPopover`, `SectionNav`, `StickyActionBar`, `PrintArea`, the hooks), the rules and the ready message.
2. `docs/exam-site/plans/ui-delivery/ws9-gaps-and-polish.md` — Part 3 (P1, P2, P3, P6), F1, F2, F8, U8, UF9, UF10.
3. `docs/exam-site/plans/ui-delivery/README.md` and `ws0-foundation.md` (its Report has today's real contracts).
4. `docs/exam-site/plans/ui-delivery/bangla-copy.md`, the client mockup `docs/exam-site/ui/client-mockup.html` (student look) and `docs/exam-site/ui/admin-prompt.md` (staff look).

## Order

1. First commit: the type-scale classes in `globals.css`, every new component and hook as a working stub with the exact props, and the `staffMarking` and `staffResults` message namespaces registered (empty files in both languages). Tell the integrator.
2. Then: P1 (apply the type scale inside the shared components and shells), P2 and P3 in the shared components (heights, spacing, states, skeleton fidelity, empty and error states), the real implementations of the new components (with a test each), F1, F2, U8, P6, UF9, UF10, then F8 (self-hosted fonts).
3. Do not change the props of existing components; add optional props only. Do not touch page files: the page agents apply the scale to their own pages.

## Rules

- Tokens only; no hex or raw palette colors. Keep the student mockup look and the staff admin look.
- Only you edit `web/package.json` (and only for `@dnd-kit` if the integrator approves AUT's request).
- `npm run build` only under `F:\coaching-center-platform\.heavy.lock` (write `FND`); delete the lock after. Unit tests single-process (`npm test`).
- Do not run `next dev`, do not open a browser.
- Commit small, one item per commit, naming the item id, ending with your attribution line.

## Done

`npm run lint` 0 errors, `npx tsc --noEmit` clean, `npm test`, `npm run build` under the lock. Fill in "Report — FND" at the end of `ws9-gaps-and-polish.md`, then send the ready message from `ws9-stages.md`.
