# Participation page: a task-first redesign (2026-10-27)

Status: approved by the user on 2026-10-27; built the same day (web only). Not yet seen in a browser by the author; the user is checking the screens.
Files: `web/src/app/(staff)/staff/participation/page.tsx`, `web/src/components/staff/participation/exam-step.ts` (the next-step rules), texts under `staffPeople.participation`, tests in `tests/ws9-ops/` and `tests/ws6a/`.
Scope: `/staff/participation` (Master Admin and System Admin). No API change.

## Problem

The page is a 13-column table of counts. The Master Admin cannot tell what to do next:

1. Too many columns, so the table scrolls sideways and the row menu is off screen.
2. "Awaiting publish" does not say who acts, or whether the exam is still open (which blocks release).
3. Release is hidden: row menu, then "Open the results", then the release panel.
4. The count links go through an old redirect and land on an unfiltered list.
5. MCQ rows print "undefined" (the API leaves out null fields).

## What other LMSs do

- Moodle: marking workflow with "In marking / Ready for release / Released"; one status word per submission.
- Canvas: a "Needs Grading" to-do list; grades stay hidden until "Post grades", which shows how many are hidden.
- Google Classroom: grades stay drafts until "Return"; the list shows Graded and Returned counts.

The common idea: organise the list by the next action, with the action button on the row.

## Design

- **Tabs with counts** act as the "needs your action" strip: All exams, To release, To mark, Changes waiting. The counts are papers, summed over all written exams.
- **Five columns:** Exam (title, module and type underneath), When (start, end, and a Upcoming/Live/Closed chip), Progress (submitted, marked, released), Next step (one plain-language status), and an action button that is always visible.
- **Next step**, in this order: not started, exam still open, to mark (with ready-to-release alongside), to release, all released, no submissions. MCQ shows "Auto-scored".
- **Action button:** "Release results" (Master Admin, something to release) opens the exam's results page at the release panel. Otherwise "Open marking" or "View results". The System Admin never sees "Release results"; the step says the Master Admin releases.
- **Row menu** only for secondary links: results, leaderboard, scripts.
- **Wording:** "Awaiting marks" is "To mark"; "Awaiting publish" is "Marked, not released"; "Published" is "Released to students".
- **Links:** the dashboard's awaiting-publish tile opens `?focus=release`. Counts open the marking list with the right status filter.
- **Less-used numbers** (sitting, not submitted, later attempts) leave the table; "not submitted" stays in the progress line when above zero.
- Missing numbers are read with `isMissing`, so no "undefined".

## Decisions taken (defaults, user may redirect)

- The row button opens the results page; it does not release from the table.
- MCQ exams stay in the table, with no marking numbers.
- A tab filter other than "All exams" loads up to 100 exams and filters in the browser, so paging is hidden while it is on.

## Checks

Unit tests for the next-step rules; page tests for each role and tab; full suite, type check and lint.
