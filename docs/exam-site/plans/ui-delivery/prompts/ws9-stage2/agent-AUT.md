# Agent AUT (WS9 stage 2): exam authoring and the admin home

You are agent **AUT**. You own the authoring side for Master Admin and System Admin: the admin home, the category, module and exam pages, and the MCQ and written editors. Read [README.md](README.md) first (rules, lock, order).

## Setup

1. `git -C F:/coaching-center-platform worktree add F:/cc-wt/w9-aut -b ui/w9-authoring catalog-acquisition`
2. Work only inside `F:\cc-wt\w9-aut`. Run `npm ci` in `web/`.

## Read, in this order

1. `docs/exam-site/plans/ui-delivery/ws9-stages.md`: your row, the rules, the ready message.
2. `docs/exam-site/plans/ui-delivery/ws9-gaps-and-polish.md`: items **U4, U5, U6, U7, U9, R2, UF6, UF7, UF8 (catalog lists), F3, F7, F9 (ask first), P5 (catalog pages)**, and the API items **G3, G4, G5, G6, G9, G13**. Read the full text of each. Read the "API additions" block.
3. `docs/exam-site/ui/admin-prompt.md` (the staff look), `docs/exam-site/plans/ui-delivery/bangla-copy.md`.
4. The FND contracts: `web/src/components/ui/` (`stepper.tsx`, `summary-strip.tsx`, `split-browser.tsx`, `row-menu.tsx`, `section-nav.tsx`, `sticky-action-bar.tsx`), and `web/src/lib/hooks/` (`use-unsaved-changes-guard.ts`, `use-list-prefs.ts`, `use-draft-form.ts`).

## Owned files

- `web/src/app/(staff)/staff/page.tsx`
- `web/src/app/(staff)/staff/categories/**`
- `web/src/app/(staff)/staff/modules/**`
- `web/src/app/(staff)/staff/exams/[examId]/page.tsx` (not the `leaderboard` or `results` subfolders, which are OPS's)
- `web/src/components/staff/catalog/**`
- `web/src/components/staff/home/admin-home.tsx` (MRK owns `instructor-home.tsx` in the same folder)
- `web/src/lib/api/exam/staff-catalog.ts`, `web/src/lib/api/exam/staff-home.ts`
- `web/src/i18n/messages/{bn,en}/staffCatalog.ts`
- `web/tests/ws9-aut/**` (new)
- Your section in `ws9-gaps-and-polish.md`

Do not touch `staff/submissions/**`, `components/staff/marking/**`, `components/staff/home/instructor-home.tsx` (MRK); `staff/students/**`, `instructors/**`, `admins/**`, `participation/**`, `payments/**`, `audit/**`, `account/**`, `exams/[examId]/results/**`, `exams/[examId]/leaderboard/**`, `components/staff/people/**`, `components/staff/ops/**`, `components/staff/results/**` (OPS).

## Items

**R2. Exam price in the staff UI (P0).** Master Admin sees and edits the exam price (G9: only Master Admin can set it). System Admin sees the price as read-only information, never as a control that would fail with 403 (the same rule as U9). Show the price on the exam row and in the exam editor's price field.

**U4. Setup-progress panel on the module page (P0).** At the top of `/staff/modules/[id]`, a stepper or checklist of the steps for that module, using FND's `Stepper`. Each step links to its section.

**U5. Exam editor as numbered sections (P0).** The exam editor becomes numbered sections (basics, schedule and price, rules and paper, questions or question PDF, review) with `SectionNav` on desktop and collapsible headers on phones. Keep the draft guard (`useUnsavedChangesGuard`) on the form.

**U6. Module exam table shows readiness (P0).** Columns: name, type, date and time (or "তারিখ পরে জানানো হবে"), price, questions or PDF, status pill. The readiness state of each exam is visible at a glance.

**U7. First-run onboarding (P1).** When the catalog is empty, the Master Admin and System Admin home shows the first three steps (category, module, exam) with links. Stop showing it once any category exists.

**U9. System Admin parity (P0).** Walk the whole authoring journey as a System Admin. Every Master-Admin-only step is shown as information with a sentence on who can do it, never as a dead control. Check every page you own.

**UF6. Exam agenda on the admin home (P2).** The next 14 days of dated exams across all modules, grouped by day, each with its module, time and status. Uses the existing list calls; no new API.

**UF7. Publish confirmation summary (P1).** Before publishing an exam, a dialog restates what students get: the title, the date, the price (or "no price"), the number of questions or the PDF, and which modules and doors it appears in. Confirm button names the action.

**UF8 (catalog lists). Row menus (P2).** On the module list, module exams and categories, a `RowMenu` with the likely next action (edit, publish, copy, archive, roster).

**F3. Next-steps toast after publishing (P1).** After publishing a module or an exam, the toast names the next step with glossary terms: "সরাসরি ভর্তি করুন" (Master Admin) and "অংশগ্রহণ দেখুন". Use the existing toast; do not change `toast.tsx` (FND's shared file, PUR-FND owns it in phase 2).

**F7. Paste many MCQ questions (P2).** A plain-text paste box with the documented format, a parsed preview with per-block errors, sequential creation with progress, and stop on the first failed create with the error shown.

**F9. Drag reorder of MCQ questions (P2, ask first).** Needs `@dnd-kit/core` and `@dnd-kit/sortable`. Ask the integrator before you add them. Keep the up and down buttons for keyboard and phones. If the integrator says no, skip F9 and say so in the report.

**G3 UI. Clash conflict (409).** The 409 body carries `details` (`id`, `titleEn`, `titleBn`, `liveAt`, `endAt`). Show which exam clashes and when, with a link to it.

**G4 UI. Instructor on the module.** Show the assigned instructor (name) on the module page, with assign and remove for Master Admin and System Admin. Phone is staff-only; show it only where the API sends it.

**G5 UI. Module list search and status.** Server search (`q`) and status filter in the URL, rows show the counts `mcqCount` and `writtenCount`. Server paging stays.

**G6 UI. Question PDF limit.** The helper text and the over-limit message use the new API limit (100 MiB) and the 413 `FILE_TOO_LARGE` code.

**G13 UI. Copy an exam.** A "কপি করুন" action on the exam row for Master Admin and System Admin. It creates a draft in the same module with no date and no price, then opens it. Show the copy title suffix.

**P5 (catalog pages).** The page-header pattern (title, breadcrumbs, actions on the right, stacked on phones), table rules (sticky header, numbers right-aligned, status as pills), and the filter bar pattern, on every catalog page you own.

**Bangla pass.** Every `staffCatalog` string against `bangla-copy.md`.

**Unused message keys.** Remove keys in `staffCatalog` and any other namespace you own that have no use in `web/src` (check every literal and template key). One commit. FND's report counts unused keys in the AUT namespace; clear them.

## Coordination slots (leave these as marked placeholders; do not build them)

- In `exams/[examId]/page.tsx`, a clearly marked slot `{/* SLOT: answer release (phase 2, STAFF-ADMIN-UI) */}` next to the exam status. Nothing else in that slot.
- A link to `/staff/exams/[examId]/results` in the module exam table. OPS builds that route. Add the link only when OPS's route is merged; until then, leave a comment.

## Done

- Each item has tests in `web/tests/ws9-aut/**`, in both languages where text shows.
- `npm run lint` (0 errors), `npx tsc --noEmit` (clean), `npm test` (single process), `npm run build` under lock `AUT`.
- No `text-[..]`, no old scale classes, no raw colours in your files.
- "Report — AUT" at the end of `ws9-gaps-and-polish.md`: each item as done, partly or skipped, with the reason, and the coordination slots you left.
- The ready message from `ws9-stages.md`.
