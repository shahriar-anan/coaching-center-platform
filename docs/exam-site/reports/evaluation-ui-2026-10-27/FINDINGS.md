# Evaluation screens: findings from the screenshots (2026-10-27)

Captured from the production build (`next start`) against the screenshot database `coaching_center_shots`. Data: one written exam, three students. Rahim draft mark (7/10), Nasrin released at 4/10 then a correction waiting (5/10), Karim not submitted. 26 screenshots in this folder, named `<role>-<width>-<lang>-<theme>-<screen>.png`.

## Fixed during this review

| # | Finding | Where | Screenshot | Status |
|---|---|---|---|---|
| F1 | Results table showed "Published" for every row, including unreleased papers. The API leaves out null fields, so `publishedAt` is missing, and `undefined !== null` is true. | `components/staff/results/results-table.tsx` | `master-1440-en-light-results-release.png` (before fix) | Fixed; the screenshot after fix shows "Not published" |
| F2 | Marking form: an unmarked paper with its score left out could fill the mark box with the text "undefined". Same bug class in the facts panel and in the "marked today" count. | `submission-form.tsx`, `staff-marking.ts` | none (no unmarked submitted paper in the demo) | Fixed by a type guard `isMissing` (`lib/api/missing.ts`) and a regression test. The jsdom test cannot prove the fix, because jsdom empties a number box anyway. |
| F3 | Raw key on the System Admin marking page: `staffMarking.publish.instructorNote`. The key lived under `bulk`, not `publish`. | `i18n/messages/{en,bn}/staffMarking.ts` | `sysadmin-1440-en-light-marking-read-only.png` | Fixed (key added under `publish`, both languages) |

## Open, found in the screenshots

| # | Severity | Finding | Where | Screenshot |
|---|---|---|---|---|
| O1 | Resolved | The answer-script viewer was empty in the captures because the screenshot was taken before the PDF rendered. With an 8-second wait the answer text appears, and the console shows no errors. The capture script now waits longer. | `components/pdf/` viewer | `master-1440-en-light-marking-draft.png` (before), `scratchpad/probe-viewer.png` (after) |
| O2 | Medium | Results summary tiles count only released results (Passed 0, Pass rate 0.0%), while the table shows Rahim as Passed. The two are both correct under the rule, but the difference is not explained on the page. | `results-report` summary, `results-copy.ts` | `master-1440-en-light-results-release.png` |
| O3 | Medium | The marking page's right column is long: the ready card, the publish card, the comment, the file box, and the buttons stack into about 1,800 px. The publish action is far below the marks. | `app/(staff)/staff/submissions/[participantId]/page.tsx`, `submission-form.tsx` | `master-1440-en-light-marking-draft.png` |
| O4 | Medium | The "Ready for release" and "Publish result" controls are two separate cards with similar text. Users may not see which one releases the result. | `submission-form.tsx` | `master-1440-en-light-marking-draft.png` |
| O5 | Low | The keyboard hint on the marking page is an icon with no label. | `submission-form.tsx`, `shortcuts-popover.tsx` | `master-1440-en-light-marking-draft.png` |
| O6 | Low | Student-facing copies of the same bug class (`=== null` on API fields) remain in `components/student/result/result-page.tsx`, `results-hub.tsx`, and `app/(site)/notices/page.tsx`. Out of the staff scope; needs a decision before changing student screens. | student result and notices | not captured |

## Done after the first review

- O5: the marking page's shortcut control shows the word "Shortcuts" next to the icon.
- The instructor home shows "Changes waiting for release" as a total. It comes from a new per-exam field, `correctionsWaiting`, in the participation rows (test added in `LiveEvaluationIT`).

## Still open

- Previous and Next on the marking page, and the progress count, only see the first 100 papers of a module. Fixing this needs an API endpoint that returns the neighbours of one paper, not a web change.
- The category view shows only the first page of each module. The warning line says so; it is not paged.
- "Save and next" confirms with a toast only. The toast appears after navigation, and it has not been checked on a phone.
- Bangla strings added during this work need a native review: the release panel, the ready flag, the status words, the phone tabs, "Changes waiting for release", and the shortcut label.
- The student-side null checks listed as O6 need a decision before any change.
