# Agent OPS (WS9 stage 2): results, people and payments

You are agent **OPS**. You own the results report, the student history, the people lists (students, instructors, admins), participation, payments and audit. Read [README.md](README.md) first (rules, lock, order).

## Setup

1. `git -C F:/coaching-center-platform worktree add F:/cc-wt/w9-ops -b ui/w9-ops catalog-acquisition`
2. Work only inside `F:\cc-wt\w9-ops`. Run `npm ci` in `web/`.

## Read, in this order

1. `docs/exam-site/plans/ui-delivery/ws9-stages.md`: your row, the rules, the ready message.
2. `docs/exam-site/plans/ui-delivery/ws9-gaps-and-polish.md`: items **C1, C2, C3 (bulk publish on the results report), F4, UF8 (people and ops lists), P5 (people and ops pages)**, and the API items **G14, G15, G16** (the API is merged; read the "API additions" block).
3. `docs/exam-site/ui/admin-prompt.md` (the staff look), `docs/exam-site/plans/ui-delivery/bangla-copy.md`.
4. The FND contracts (`web/src/components/ui/`, `web/src/lib/hooks/`).
5. The current code you change: `web/src/app/(staff)/staff/students/**`, `instructors/**`, `admins/**`, `participation/**`, `payments/**`, `audit/**`, `account/**`, `exams/[examId]/leaderboard/**`, `components/staff/people/**` (except the submission form, which MRK moves), `components/staff/ops/**`.

## Owned files

- `web/src/app/(staff)/staff/students/**`, `instructors/**`, `admins/**`, `participation/**`, `payments/**`, `audit/**`, `account/**`
- `web/src/app/(staff)/staff/exams/[examId]/results/**` (new route for C1)
- `web/src/app/(staff)/staff/exams/[examId]/leaderboard/**`
- `web/src/components/staff/people/**` (except `submission-form.tsx`, which MRK moves)
- `web/src/components/staff/ops/**`
- `web/src/components/staff/results/**` (new)
- `web/src/lib/api/exam/staff-people.ts`, `staff-ops.ts`, `staff-results.ts` (new)
- `web/src/i18n/messages/{bn,en}/staffPeople.ts`, `staffOps.ts`, `staffResults.ts` (FND registered `staffResults` as an empty file)
- `web/tests/ws9-ops/**` (new)
- Your section in `ws9-gaps-and-polish.md`

Do not touch the catalog and admin home (AUT), `staff/submissions/**` and `components/staff/marking/**` (MRK), `people/submission-form.tsx` (MRK).

## Items

**C1. Exam results report (P0).** New route `/staff/exams/[examId]/results` (G14: `GET /api/v1/live-exams/{id}/results`, body `{ summary, rows }`). A summary strip on top (`SummaryStrip`: participants, submitted, failed to submit, average, highest, lowest, passed, pass rate). Then a table with the filters (kind, status, search) and the sort, all in the URL. Numbers only, no charts. Master Admin and System Admin see all exams; an assigned instructor sees written exams in their modules (the API enforces it, and the page shows what the API returns). The instructor never sees phone numbers.

**C2. Student exam history (P0).** On `/staff/students/[id]`, a section "পরীক্ষার ইতিহাস" under the access list, newest first (G15: `GET /api/v1/students/{id}/live-exam-results`), each row with the exam and module titles, the kind, status, score, passed, submitted time and the published flag, linking to the exam's results report. Leave a marked slot above it: `{/* SLOT: access list (phase 2, STAFF-ADMIN-UI) */}`, and build nothing there.

**C3 UI (Master Admin only). Bulk publish on the results report.** "সব খসড়া প্রকাশ করুন (N)" with a confirm dialog naming the exam and the count, calling `POST /api/v1/live-exams/{id}/publish-results` (G16). It shows the returned `{ published: n }`. Instructors and System Admins never see this control.

**F4. Summary strips on payments and participation (P1).** Payments: today's successful count and total, and the pending count (three `size=1` or status calls, cached for 60 s). Participation: the summary from `GET /api/v1/live-exam-participation/summary`. Use `SummaryStrip`. Loading and error states for each strip; a failed strip does not hide the table.

**UF8 (people and ops lists). Edit in context (P2).** On students, instructors, admins, participation, payments and the results report: a `RowMenu` with the likely next action for that row (open, edit, suspend, print the marksheet is not in scope, view results). Keep the actions that the role allows; show information for the rest, as AUT's U9 rule says.

**P5 (people and ops pages).** The page-header pattern (title, breadcrumbs, actions on the right), sticky table header, numbers right-aligned, status as pills, empty and loading states in the table body, the filter bar pattern, on every page you own.

**Bangla pass.** Every `staffPeople`, `staffOps` and `staffResults` string against `bangla-copy.md`. Bengali digits through the formatters.

**Unused message keys.** Remove keys in `staffPeople`, `staffOps` and `staffResults` that have no use in `web/src` (check every literal and template key). One commit. FND's report counts unused keys in the OPS namespaces; clear them.

## Coordination slots

- On `exams/[examId]/page.tsx` (AUT's file), do not edit. Once your results route is merged, the integrator asks AUT to add the link. Nothing else from you there.
- In `students/[id]`, the marked slot for the access section (see C2). Build nothing else for it.

## Done

- Each item has tests in `web/tests/ws9-ops/**`, in both languages where text shows. Test each role's view: Master Admin, System Admin, and the instructor's limited results.
- `npm run lint` (0 errors), `npx tsc --noEmit` (clean), `npm test` (single process), `npm run build` under lock `OPS`.
- The results route renders with and without rows, and with the failed-to-submit rows.
- No `text-[..]`, no old scale classes, no raw colours in your files.
- "Report — OPS" at the end of `ws9-gaps-and-polish.md`, then the ready message from `ws9-stages.md`.
