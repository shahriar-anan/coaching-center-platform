# Agent MRK (WS9 stage 2): instructor dashboard and marking

You are agent **MRK**. You own the instructor side: the home as a marking dashboard, the submissions browser, the marking screen, and the bulk publish for Master Admin. Read [README.md](README.md) first (rules, lock, order).

## Setup

1. `git -C F:/coaching-center-platform worktree add F:/cc-wt/w9-mrk -b ui/w9-marking catalog-acquisition`
2. Work only inside `F:\cc-wt\w9-mrk`. Run `npm ci` in `web/`.

## Read, in this order

1. `docs/exam-site/plans/ui-delivery/ws9-stages.md`: your row, the rules, the ready message.
2. `docs/exam-site/plans/ui-delivery/ws9-gaps-and-polish.md`: items **U1, U2, U3, UF4, UF5, C3 (bulk publish in the submissions browser), P5 (marking pages)**, and the API items **G1, G2, G8, G11**. Read the full text of each. Read the "API additions" block.
3. `docs/exam-site/ui/admin-prompt.md` (the staff look), `docs/exam-site/plans/ui-delivery/bangla-copy.md`.
4. The FND contracts (`web/src/components/ui/` and `web/src/lib/hooks/`), as listed in AUT's prompt.
5. The current marking code, which you will move: `web/src/app/(staff)/staff/submissions/**`, `web/src/components/staff/people/submission-form.tsx`, `web/src/components/staff/home/instructor-home.tsx`.

## Owned files

- `web/src/app/(staff)/staff/submissions/**`
- `web/src/components/staff/marking/**` (new folder; move `web/src/components/staff/people/submission-form.tsx` here, with its tests)
- `web/src/components/staff/home/instructor-home.tsx` (AUT owns `admin-home.tsx` in the same folder)
- `web/src/lib/api/exam/staff-marking.ts` (new)
- `web/src/i18n/messages/{bn,en}/staffMarking.ts` (FND registered this namespace as an empty file)
- `web/tests/ws9-mrk/**` (new)
- Your section in `ws9-gaps-and-polish.md`

Do not touch the catalog, the admin home, `staff/modules/**`, `staff/exams/**` (AUT), or `staff/students/**`, `participation/**`, `payments/**`, `exams/[examId]/results/**` and the other OPS folders.

## Items

**U1. Instructor home becomes a marking dashboard (P0).** The instructor's home shows: the work waiting (count and the modules it is in), today's line (UF5), and the next three actions. Master Admin and System Admin do not see this; they see AUT's admin home.

**UF5. Today's work line (P2).** On the dashboard: "আজ N টি খাতা মূল্যায়ন করেছেন" (counted from the marking actions of today, Dhaka time, through the existing submissions list). If the count cannot be read, hide the line; do not show zero as a fact.

**U2. A browsable submissions area (P0).** `/staff/submissions`: a list across the instructor's assigned modules (the API's `GET /api/v1/live-exam-submissions` already scopes it), with the status filter, the search (`q`) and the sort in the URL, and each row linking to the marking screen. Written exams only.

**U3. The marking screen (P0).** A `SplitBrowser`-style layout on desktop: the student's answer PDF on one side (the PDF viewer already exists in `components/ui/pdf` or `components/pdf`), the marking form on the other. On phones, one column, with the answer PDF first. Use `SectionNav` for "মার্ক", "মন্তব্য", "মার্কড PDF". Saving keeps the draft (`useDraftForm`) and the unsaved guard.

**G1 UI. Saved comment and marked PDF.** The form prefills the marks and the comment from the saved draft (the API returns `comment` and `evaluatedFileName`). It shows the stored marked PDF name with "দেখুন" and "বদলান". The API still requires a new marked PDF on every save (the current rule). The form says so, in the copy for that field, before the instructor starts.

**G2 UI. Titles and maximum.** The submission read carries the exam and module titles and the maximum. Show them in the screen header and in the rows.

**G8 UI. The question paper while marking.** The question paper (`GET /api/v1/live-exams/{id}/question-pdf`) is shown in a collapsible panel beside the answer, for assigned instructors. An unassigned instructor gets 404; show "এই প্রশ্নপত্র দেখার অনুমতি নেই।" with no error stack.

**G11 UI. The cross-module feed.** The submissions browser uses the feed (`/live-exam-submissions`) with the module filter. Open sittings are not listed; the copy says a sitting appears once its deadline has passed.

**C3 UI (Master Admin only). Bulk publish in the browser.** On the submissions browser, "সব খসড়া প্রকাশ করুন (N)" for the exam selected in the filter, with a confirm dialog naming the exam and the count. Row checkboxes and "নির্বাচিতগুলো প্রকাশ করুন" call the single publish per row with progress and a stop button. The "সব" action calls `POST /api/v1/live-exams/{id}/publish-results`, which returns `{ published: n }`. The instructor never sees these controls.

**UF4. Comment snippets (P2).** Save frequently used comments on the device (`useListPrefs`-style storage, per device) and insert them with one click on the marking form. Keep up to 20; the user can delete one.

**P5 (marking pages).** The header pattern, sticky table header, numbers right-aligned, status pills, empty and loading states in the table body, the filter bar pattern.

**Bangla pass.** Every `staffMarking` string against `bangla-copy.md`.

**Unused message keys.** Remove keys in `staffMarking` and any other namespace you own that have no use in `web/src` (check every literal and template key). One commit. FND's report counts unused keys in the MRK namespace; clear them.

## Coordination slots

- None in other agents' pages. The results report link and the access section are OPS's and phase 2's.

## Done

- Each item has tests in `web/tests/ws9-mrk/**`, in both languages where text shows. Test the instructor and the Master Admin views separately: the bulk publish is never shown to an instructor.
- `npm run lint` (0 errors), `npx tsc --noEmit` (clean), `npm test` (single process), `npm run build` under lock `MRK`.
- The moved `submission-form.tsx` has no import left in `people/`.
- No `text-[..]`, no old scale classes, no raw colours in your files.
- "Report — MRK" at the end of `ws9-gaps-and-polish.md`, then the ready message from `ws9-stages.md`.
