# WS9 in stages — several agents in parallel

The backlog is [ws9-gaps-and-polish.md](ws9-gaps-and-polish.md) (items R, G, C, U, UF, F, P). This file splits it across **six agents** with fixed file ownership, so they can run in parallel the way WS0–WS6 did. If only one agent runs, use [prompts/agent-G.md](prompts/agent-G.md) instead. The README rules for every agent still apply (worktree-only writes, the heavy lock, single-process tests, `Suspense` around guarded pages, Bangla by [bangla-copy.md](bangla-copy.md), links through `lib/config`, no hosts in code, tokens only).

## Agents and stages

At most three agents at a time on this PC.

| Stage | Agent | Branch | Worktree | Owns (summary) |
|---|---|---|---|---|
| 1 | **API** | `ui/w9-api` | `F:\cc-wt\w9-api` | `api/**`, `docs/exam-site/current-api.md`, `docs/exam-site/reports/ui-dev-qa-guide.md` |
| 1 | **FND** (foundation and design system) | `ui/w9-foundation` | `F:\cc-wt\w9-fnd` | `web/src/app/globals.css`, `web/src/app/layout.tsx`, `web/src/app/fonts/**`, `web/src/components/ui/**`, `web/src/components/shell/**`, `web/src/lib/hooks/**`, `web/src/i18n/messages/index.ts`, `common` messages, the three legacy components |
| 1 | **STU** (student site) | `ui/w9-student` | `F:\cc-wt\w9-stu` | `web/src/app/(site)/**`, `web/src/app/(auth)/**`, `web/src/components/student/**`, `lib/api/exam/{catalog,purchase,sitting,account,notices}.ts`, messages `auth`, `account`, `notices`, `legal`, `catalog`, `purchase`, `home`, `sitting`, `result` |
| 2 | **AUT** (exam authoring and admin home) | `ui/w9-authoring` | `F:\cc-wt\w9-aut` | `web/src/app/(staff)/staff/page.tsx`, `.../categories/**`, `.../modules/**`, `.../exams/[examId]/page.tsx`, `web/src/components/staff/catalog/**`, `web/src/components/staff/home/admin-home.tsx`, `lib/api/exam/{staff-catalog,staff-home}.ts`, messages `staffCatalog` |
| 2 | **MRK** (instructor dashboard and marking) | `ui/w9-marking` | `F:\cc-wt\w9-mrk` | `web/src/app/(staff)/staff/submissions/**`, `web/src/components/staff/home/instructor-home.tsx`, `web/src/components/staff/marking/**` (new; move `people/submission-form.tsx` here), `lib/api/exam/staff-marking.ts` (new), messages `staffMarking` (new) |
| 2 | **OPS** (results, people, payments) | `ui/w9-ops` | `F:\cc-wt\w9-ops` | `.../staff/students/**`, `.../instructors/**`, `.../admins/**`, `.../participation/**`, `.../payments/**`, `.../audit/**`, `.../account/**`, `.../exams/[examId]/results/**` (new), `.../exams/[examId]/leaderboard/**`, `web/src/components/staff/people/**` (except the moved submission form), `web/src/components/staff/ops/**`, `web/src/components/staff/results/**` (new), `lib/api/exam/{staff-people,staff-ops,staff-results}.ts`, messages `staffPeople`, `staffOps`, `staffResults` (new) |

Stage 1 starts together. **FND publishes its contracts as stubs in its first commit** (below) and is merged first. **API** is merged second. **STU** merges when done. Stage 2 starts when FND and API are merged, so AUT, MRK and OPS build on the real components and the real API. Merge order: FND, API, STU, then AUT, MRK, OPS as each reports. Shahriar's main session is the integrator: it merges, resolves anything outside ownership, and runs the full checks after each merge.

Each agent creates its own worktree from the main checkout with one command, then works only inside it:

```
git -C F:/coaching-center-platform worktree add F:/cc-wt/<worktree> -b <branch> catalog-acquisition
```

Stage-2 agents create theirs only after the integrator says FND and API are merged.

## Item assignment

| Agent | Items |
|---|---|
| API | R1/G8, G1, G2, G3, G4, G5, G6 (find the real limits and expose them in `current-api.md`), G7, G10, G11, G12, G13, C1/G14, C2/G15, C3/G16, and Part 4 (the dev-server guide, boot verified) |
| FND | P1 (type scale), P2 and P3 in the shared components, P6 (legacy clean-up, dead code), F1 (tab-bar unread badge), F2 (`useUnsavedChangesGuard`), F8 (self-hosted fonts), U8 (staff quick search in the top bar), UF9 (`useListPrefs`), UF10 (`useDraftForm`), the new shared components below, and registering the new message namespaces `staffMarking` and `staffResults` |
| STU | U10, U11, U12, G7 (account name edit), F5 (text size), F6 (register helpers), UF1 (receipt page), UF2 (review filter), UF3 (student empty states), P4 (student polish), F10 (student-side tests), the Bangla pass on student messages |
| AUT | R2 with G9 (exam price), U4, U5, U6, U7, U9, UF6 (exam agenda on the admin home), UF7 (publish summary), UF8 on catalog lists, F3 (next-steps toast), F7 (paste questions), F9 (drag reorder, ask first), the UI for G3, G4, G5, G6 (question PDF limit) and G13 (copy exam), P5 for catalog pages, the Bangla pass on `staffCatalog` |
| MRK | U1, U2, U3, the UI for G1, G2, G8 (question paper while marking) and G11, C3 bulk publish in the submissions browser, UF4, UF5, P5 for marking pages, the Bangla pass on `staffMarking` |
| OPS | C1 (results report page), C2 (student history), C3 bulk publish on the results report, F4 (payments and participation strips), UF8 on people and ops lists, P5 for people and ops pages, the Bangla pass on `staffPeople`, `staffOps`, `staffResults` |

## FND contracts (frozen in its first commit)

Other agents import these by exact path and name before FND is merged; until then they may use a local stand-in in their own folder and delete it after the merge.

**Type scale** (utility classes from `@theme`, sizes per surface as in Part 3 P1): `text-display`, `text-title`, `text-section`, `text-card`, `text-body`, `text-meta`, `text-label`, `text-pill`. No arbitrary `text-[..]` sizes anywhere after WS9.

**New shared components** (`@/components/ui/<file>`, named exports):

| Export | Props |
|---|---|
| `Stepper` | `steps: { key; label; state: "done" \| "todo" \| "blocked" \| "info"; note?; href? }[]`, `orientation?: "vertical" \| "horizontal"` |
| `SummaryStrip` | `items: { label; value: string; tone?: "muted" \| "ok" \| "warn" \| "danger"; href? }[]`, `loading?` |
| `SplitBrowser` | `tree: TreeNode[]` (`{ id; label; count?; children? }`), `selectedId`, `onSelect(id)`, `children` (the right pane); one column with a tree drawer on phones |
| `RowMenu` | `items: { label; href? ; onClick?; tone?: "danger" }[]`, `label` (accessible name) |
| `ShortcutsPopover` | `shortcuts: { keys: string[]; label }[]` |
| `SectionNav` | `sections: { id; label; state: "done" \| "todo" \| "error" }[]` (sticky on desktop, collapsible headers on phones) |
| `StickyActionBar` | `children` (bottom bar on phones with safe-area padding, inline on desktop) |
| `PrintArea` | `children` (print styles: hides shell, shows only the area) |

**New hooks** (`@/lib/hooks/<file>`): `useUnsavedChangesGuard(dirty: boolean, message?: string)` (covers in-app links and tab close), `useListPrefs(key)`, `useDraftForm<T>(key, initial)`.

**Shell:** the tab bar's নোটিশ badge uses the existing unread-count hook from `lib/api/exam/notices.ts` (read only; STU owns that file and must keep its export). The staff top bar gets the quick search (U8).

## API contracts

The response shapes are the ones written in [ws9-gaps-and-polish.md](ws9-gaps-and-polish.md) Parts 1, 5 and 6 (G1–G16). Web agents build against those shapes with mocked `fetch` and handle `404`/`400` from an older API gracefully until API is merged. If API must change a shape, it tells the integrator first.

## Rules specific to WS9

- **Polish within the two looks**: student = the client mockup; staff = the admin style. No redesign, no renamed routes, no changed business behavior.
- After FND merges, every touched page uses the type scale; any `text-[..]`, raw palette color or hex in your files is a defect.
- New packages: only `@dnd-kit/core` and `@dnd-kit/sortable` for F9, added by FND on request from AUT after the integrator agrees. Nobody else edits `package.json`.
- Every new list or page has loading, empty, error and success states, works at 375 px and 1440 px, both languages, both themes, and by keyboard.
- Before reporting: `git merge catalog-acquisition`, then `npm run lint` 0 errors, `npx tsc --noEmit` clean, `npm test`, `npm run build` under the lock. API: touched `*IT`, then the full suite from clean, alone, under the lock.
- Fill in a section "Report — <agent>" at the end of `ws9-gaps-and-polish.md` with each assigned item marked done, partly done or skipped (with the reason). Only write your own section.

## Ready message

```
READY TO MERGE: <branch> (agent <API|FND|STU|AUT|MRK|OPS>)
Last commit: <hash>
Checks: <lint / tsc / tests / build; API suite for API>
Items done: <ids> | partly: <ids and what is left> | skipped: <ids and why>
Contract changes: none | <list>
Notes for other agents: <anything>
```
