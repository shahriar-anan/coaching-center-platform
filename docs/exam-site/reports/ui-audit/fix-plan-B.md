# Fix plan for the Agent B findings

Source: `findings-B.md` (post-reset round). Implementation is in `F:\cc-wt\audit-b` on branch `ui/audit-b` (from `c2cd843`), uncommitted, so the main checkout is not touched. Nothing is pushed or merged.

## Decisions needed from Shahriar (not implemented)

| Finding | Question | Why it waits |
|---|---|---|
| B-07 routine and null `liveAt` | Should exams with no start time appear on the routine (decisions line 10), or stay off it (feature-scope line 73)? | The two sources disagree. Decisions overrides feature-scope, but the choice changes what students see. |
| B-18 correct options held until release | Should the correct option and explanation show when the sitting ends (decisions line 23), or only after staff release (current API, R-4)? | The rule in decisions and the R-4 release control conflict. Removing the gate changes the API and the staff switch. |

## Implemented (in order)

| # | Finding | Change | Where | Check |
|---|---|---|---|---|
| 1 | B-01 stored script lost on reload | API adds `answerStored` to the student exam read. The written sitting shows the stored line with the existing `storedEarlier` copy after a reload. | `api/.../LiveExamSittingService.java` (record and read), `web/src/lib/api/exam/sitting.ts`, `web/src/components/student/sitting/written-sitting.tsx`, `sitting-entry.tsx` | API compile and sitting tests; browser after API restart |
| 2 | B-16 reload hides or wrong action | Public exam and module queries are keyed on the signed-in state, so they refetch once the session is restored. | `web/src/components/student/catalog/exam-page.tsx`, `web/src/lib/api/exam/catalog.ts` | Browser: reload shows Continue or Start, not Buy |
| 3 | B-17 start button does not switch on | The student's exam status refetches every 15 s while the status is UPCOMING. | `web/src/lib/api/exam/catalog.ts` (`useMyExam`) | Browser: page left open through the window's start shows Start |
| 4 | B-10 resume after the deadline | When the sitting's deadline has passed, the exam page offers the result, not Resume. | `exam-page.tsx` | Logic check and browser |
| 5 | B-02 watermark invisible in dark theme | The watermark uses a fixed ink colour on the white page, with a higher opacity. | `web/src/components/pdf/pdf-watermark.tsx` | Dark snapshot |
| 6 | B-03 marked script without watermark | The evaluated PDF gets the same watermark (name and code). Implemented on the reading that decisions line 13 covers any viewer. Shahriar to confirm. | `result-page.tsx` | Browser: marked script shows the watermark |
| 7 | B-04 Passed count on the Marking tab | Marks are fetched for all result rows, not only on the Results tab. | `results-hub.tsx` | Logic check |
| 8 | B-05 Bangla digit on English page | Zero is formatted with the page locale. | `result-page.tsx` | Logic check |
| 9 | B-06 40 px jump links | Jump links are 44 px. | `result-page.tsx` | Snapshot |
| 10 | B-08 `/results` does not list missed and open exams | A third tab lists missed, open and submitted-but-not-due exams, with status pills. | `results-hub.tsx`, `web/src/i18n/messages/{bn,en}/result.ts` | Browser for 5103 |
| 11 | B-14 guest on `/routine` | The routine page is wrapped in `RequireStudent`, like `/results`. | `web/src/app/(site)/routine/page.tsx` | Browser as guest |
| 12 | B-15 header overflows at 360 px in Bangla | Below 380 px the header drops the mark image and the first name, and the language icon, and uses tighter gaps. | `web/src/components/shell/student-shell.tsx`, `pills.tsx`, `logo.tsx` (prop) | Probe: scrollWidth equals 360 in Bangla |
| 13 | B-19 "৮০.০ MB" on the Bangla page | Size text is locale-aware: Bangla digits and unit, decimals only when needed. | `web/src/components/ui/file-drop.tsx` | Probe in Bangla |
| 14 | B-09 breadcrumbs with no way back | Breadcrumbs link to `/results` and to the exam page. | `result-page.tsx`, `leaderboard-page.tsx` | Snapshot |
| 15 | B-11 no-access `/sit` | A clear "no access" message with a link to the exam page, instead of the retry error. | `sitting-entry.tsx`, bn and en `sitting.ts` | Browser as 5104 |
| 16 | B-12 dialog focus | The submit dialog moves focus to its Cancel button on open. | `mcq-paper.tsx` | Browser |
| 17 | B-13 notice with no link | An unread notice without a link can be opened to mark it read. | `web/src/app/(site)/notices/page.tsx` | Browser |

## Verification

1. `web`: type check (`tsc --noEmit`), lint and `vitest run`, then `npm run build`.
2. `api`: compile, then the sitting tests that exist.
3. Restart the API from the worktree (this re-runs the demo seed, which resets the B exam windows, as it did at 11:36). Then re-check the fixed items in the browser (Chromium; Firefox and WebKit on the key pages), and capture new snapshots.
4. The findings file gets a fix status per finding. The original findings stay as written.

## Not in this plan

- The staff comparison pages and the shared header on A's and C's pages. B-15 is fixed in the shared student shell, so the same fix applies to the other agents' pages. Those agents should check their own pages.
