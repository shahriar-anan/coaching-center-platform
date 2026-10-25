# WS9 agent STU — stage 1 (student site)

You are agent **STU** in WS9. You own the student site and the auth pages. Keep the client mockup look and polish within it so it looks finished on a phone and a desktop. Shahriar's main session is the **integrator**. You never merge, push or deploy.

## Setup

1. `git -C F:/coaching-center-platform worktree add F:/cc-wt/w9-stu -b ui/w9-student catalog-acquisition`
2. Work only inside `F:\cc-wt\w9-stu` (check `git rev-parse --show-toplevel`). Never write under `F:\coaching-center-platform\` except the `.heavy.lock` file.
3. `npm ci` in `web/`.

## Read, in this order

1. `docs/exam-site/plans/ui-delivery/ws9-stages.md` — your row (owned files), your items, the FND contracts (type scale, `StickyActionBar`, `PrintArea`, `SummaryStrip`) and the API contracts, the rules and the ready message.
2. `docs/exam-site/plans/ui-delivery/ws9-gaps-and-polish.md` — U10, U11, U12, G7, F5, F6, UF1, UF2, UF3, Part 3 P1 (scale) and P4 (student details), F10.
3. `docs/exam-site/plans/ui-delivery/README.md`, `bangla-copy.md`, `ws0-foundation.md` (Report), the client mockup `docs/exam-site/ui/client-mockup.html`, and `docs/exam-site/ui/student-flow-prompts.md`.

## Order

P0 first: apply the type scale to every student page (content never under 16 px; replace all `text-[..]` and review every `text-sm`), P4 details, UF3, G7 (name edit; the API arrives from agent API; handle a 404 gracefully until then). Then P1: U10, U11, F5, F6, UF1, UF2, F10. Then P2: U12. Finish with a Bangla pass on your message files.

FND's components may not be merged when you start: import them by the exact contract paths and use a local stand-in in your own folder until the integrator says FND is merged; then `git merge catalog-acquisition`, delete the stand-ins, and fix what breaks in your files.

## Rules

- Only your files and routes. Copy only in your message namespaces, written by the copy guide. Links to the admin panel through `lib/config`.
- `npm run build` only under `F:\coaching-center-platform\.heavy.lock` (write `STU`); delete it after. Unit tests single-process.
- Do not run `next dev` or the API, do not open a browser.
- Commit small, one item per commit, naming the item id, ending with your attribution line.

## Done

Before reporting, `git merge catalog-acquisition`; then lint 0 errors, `tsc` clean, `npm test`, `npm run build` under the lock. Fill in "Report — STU" at the end of `ws9-gaps-and-polish.md`, then send the ready message from `ws9-stages.md`.
