# NR-01: what the later UI plan must change

This is the input for the NR-01 UI plan. It is not done in the API plan.
- This file holds the items known before stage 2.
- Each agent writes its own items to `web-impact/<agent>.md` (`a.md`, `b.md`, `c.md`, `d.md`), one line per item, naming the API field or route the UI needs.
- Separate files avoid merge conflicts.

**Required now** would mark something the current web needs for the API change to be safe. There should be none: the compatibility rules in [contracts.md §7](contracts.md#7-compatibility-rules-the-current-website-keeps-working) exist to avoid them.

## Known before stage 2 (orchestrator)

- **Staff sidebar Exams page** (F35): grouped by package, with filters, "also in…" (R21) and the pending badge. Uses `GET /live-exams` and `GET /live-exam-pending-changes`.
- **Exam editor:**
  - drop the client-side lock (`exam-editor.tsx` `locked`);
  - add the window end, mode, negative marks, answers changeable, preview, free, discount, access days (or lifetime) and per-exam instructor;
  - add the staged-changes banner, the change-list dialog before publish, and discard;
  - send `revisionId` on publish, then remove compatibility rule 4.
- **Rename** Module → Package in every staff and student string (R1). Rename the staff "Archive" action to "Withdraw" (E26). Rename Participation to "Results & marking" (F35).
- **MCQ paper:**
  - a radio circle on the left as the only answer control (C14);
  - "select all that apply" for multi-answer, with a confirm button when answers cannot be changed (R9).
- **Student "Archived exams"** grouping (phase `ENDED`), "Popular" section and badge, favourites page or tab, preview page.
- **Practice exams** have no date, so today's web shows them as "date to be announced". They need their own card and start button. Until the UI plan lands, do not publish practice exams on staging except the demo one.
- **Move from the legacy fields** (`kind`, `myStatus`) to `attemptKind` and `myStatusDetail`. Then compatibility items 7–9 can be retired.
- **Answers-released switch:** remove it from the staff UI (P-d).
