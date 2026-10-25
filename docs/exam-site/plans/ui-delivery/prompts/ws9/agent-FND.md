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

## Follow-up from the integrator's audit (25 Oct, branch at `62de3e2`)

Your report says "not ready", and stage 2 (AUT, MRK, OPS) waits on you. Continue on `ui/w9-foundation` in `F:\cc-wt\w9-fnd`. Do these in order, one commit per id, then update "Report — FND" and send the ready message.

**Must fix before merge**
- [ ] **FN-1 (Major) F2 in-app link guard.** The frozen contract in `ws9-stages.md` says `useUnsavedChangesGuard(dirty, message?)` covers in-app links as well as tab close. Today it guards tab close only. When `dirty` is true, catch clicks on same-origin `<a>` links (capture phase), and stop browser back (`popstate`). Show the shared `ConfirmDialog` with "সেভ না করে চলে যাবেন?" (copy in `common`), and navigate only on confirm. Keep the signature. Add tests for: link blocked, confirm navigates, cancel stays, guard off when clean. AUT and MRK build their editors on this.
- [ ] **FN-2 (Major) `StickyActionBar` hidden behind the student tab bar.** On phones both are `fixed bottom-0`. The tab bar is `z-30` and comes later in the DOM, so on `/exam/[id]` a signed-in student cannot see or tap the primary button. STU's stand-in has the same bug. Fix: when `showTabs` is true, `StudentShell` sets an offset (for example `--tabbar-h: calc(3.5rem + env(safe-area-inset-bottom))` on the surface root). `StickyActionBar` sits at `bottom: var(--tabbar-h, 0px)` and keeps its own safe-area padding only when there is no tab bar. Also, `<main>` must reserve the bar's height, so the last content and the footer are not covered (for example a `data-sticky-bar` flag that adds bottom padding). Add a test for both cases (with and without the tab bar).
- [ ] **FN-3 (Major) P2 and P3 in the shared components (P0).** Still open: button and control heights, skeleton fidelity (same card count and heights as the final layout), and hover, active, focus-visible and disabled styles from tokens on every control in `components/ui`. Finish these, or list in your report exactly which components remain, so the page agents know.
- [ ] **FN-4 (Minor) Merge conflict in `components/student/notice-bell.tsx`.** That file is STU's (`components/student/**`), and both branches changed the same line. Revert your edit to it (`git checkout catalog-acquisition -- web/src/components/student/notice-bell.tsx`). STU's version (`text-meta font-bold`) stays.

**Should fix**
- [ ] **FN-5 (Minor) U8 raw status.** `staff-quick-search.tsx` shows a module's `status` as the raw enum (`DRAFT`, `PUBLISHED`). Show the translated status word, as the staff lists do (a `Pill` or the existing status messages).
- [ ] **FN-6 (Minor) U8 keyboard.** The input has `role="combobox"`, but ↓/↑/Enter do not move through the results. Add `aria-activedescendant` navigation, or drop the combobox and listbox roles so screen readers are not told something false.
- [ ] **FN-7 (Minor) U8 on phones.** The search is `hidden md:block`, so phones have no search. Add a search icon button in the phone top bar that opens the same search in a `Dialog`.
- [ ] **FN-8 (Minor) F1 accessible count.** The tab-bar badge is `aria-hidden`, so the নোটিশ tab's name never includes the unread count. Add sr-only text (for example "৩টি অপঠিত নোটিশ") via `common`.
- [ ] **FN-9 (Minor) UF10 draft overwrite.** In `useDraftForm`, `setValue` writes over the stored draft and clears `hasDraft`. If the user types before tapping "ফিরিয়ে আনুন", the saved draft is lost. Keep the restorable draft under its own key until `restore()` or `clear()`. Also read `localStorage` in an effect, not in the `useState` initializer, so the server and client first renders match. The same applies to `useListPrefs`.
- [ ] **FN-10 (Minor) Tokens.** `text-white` on both badges (use a token such as `text-on-accent`, or add one), and `text-base` left on the phone logo in `staff-shell.tsx:100`.
- [ ] **FN-11 F8 fonts (P2).** Do it now if time allows (the WOFF2 files can be fetched). Otherwise mark it "skipped, deferred to after WS9" in your report, so the integrator can decide.

**Status at the 25 Oct check (head `ce8fa5e`):** FN-1 to FN-11 are committed, each in its own commit, and checked in code: the guard covers links and back, the sticky bar sits above the tab bar, the quick search shows translated status and works by keyboard and on phones, the tab badge is read by screen readers, the draft hook keeps drafts, the tokens are in place, and the fonts are self-hosted. **FND is merged into `catalog-acquisition` by the integrator.** Left open, Minor, not blocking: the P3 hover and active states on pagination, `copy-field`, `file-drop` and the select. Do them as a small follow-up commit if you still have time, and say so in the report. Also, STU's badge in `notice-bell.tsx` should use `text-on-warn`, the token you added. That is STU's file, so STU does the change.
