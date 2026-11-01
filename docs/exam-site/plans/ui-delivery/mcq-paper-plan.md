# MCQ sitting page as a printed exam paper

Status: plan, 1 November 2026, written from the old site's source and the current code. Implementation starts when the agent that changes the submit rules (which also edits `mcq-paper.tsx`) has finished, so two agents never edit the same file.

## 1. Goal

When a student sits an MCQ exam the screen should look and feel like a real paper: white page, black text, plain numbered questions, options marked like an answer sheet. No app colours, cards, gold buttons or dark theme. Every rule the current sitting page already enforces stays.

## 2. How the old site did it (from its source)

`components/Dashboard/ExamSection/OpenExam/MCQSection.jsx` and `MixedSection.jsx`, page `.../start-exam`.

| Part | Old site |
|---|---|
| Frame | The site header and footer are **hidden** on this page (its hide-routes rule). The page is the paper. |
| Top | A solid band with the exam title on the left and the countdown `HH:MM:SS` on the right. |
| Questions | One long page, no cards. Each question is the number ("1.") then the stem, in large plain text, with the options beneath as a vertical list of a small radio or checkbox and the text. Hairline spacing only. |
| Floating counter | A small translucent box at the bottom right: "Answered: N, Unanswered: M" and the timer. |
| Submit | One plain SUBMIT button at the end; a yes/no confirmation. |
| After | A plain thank-you page with right, wrong and total marks. (This site's result page replaces it.) |
| Colours | White or off-white page, black text; the only colour is the title band. |
| Rules | Answers lock when "answer changeable" is off; copy and paste are blocked in short-answer boxes; the timer ends the paper by itself. |

## 3. The current page (inventory)

Files: `sitting-entry.tsx` (entry, routes the student), `mcq-paper.tsx` (646 lines: autosave, timer, grid, submit), `mcq-question.tsx` (one question; **also used by the result page's review**), `paper-text-size.tsx` (A− and A+), `warnings.ts` (10, 5 and 1 minute warnings and the final minute tone), `selection.ts` (answers, local backup, retry delays).

What it does today, and must keep doing:
1. Autosave of every tap, one save at a time, retry with delays, a local backup that survives a reload, offline notice, a save badge (saved, saving, failed with retry).
2. Server time countdown; warnings at 10, 5 and 1 minutes with a spoken (aria-live) announcement; the final minute changes the bar tone; the paper closes by itself at the deadline and sends the student on.
3. "Answers cannot change" rule: a saved single answer or a confirmed multi-answer question is final; a Confirm button for multi-answer; "final" note; a refusal message when a locked answer is tapped.
4. Multi-answer questions: square boxes instead of circles, "Select all that apply".
5. Flag a question for later; flagged numbers listed in the submit dialog; flagged ring in the jump grid.
6. A jump grid of question numbers (answered, flagged); smooth scroll to a question.
7. Text size A− and A+ (16, 18, 20 px), remembered on the device.
8. Paper language (Bangla or English faces, Bangla or English option letters).
9. A note for a practice (retake) attempt and a message when the paper was closed.
10. Submit confirmation with answered and unanswered counts and the flagged list; submit failure message with retry.
11. Accessibility: radiogroup and group roles, 44 px tap targets, focus outlines, the row itself does not take taps (only the circle), a "selected" label.

## 4. Gaps against the goal

| # | Gap |
|---|---|
| 1 | The app theme leaks in: navy and gold colours, dark mode makes the paper dark, cards with rounded borders. A printed paper is white whatever the theme. |
| 2 | Each question is a bordered card and each option a bordered pill with a coloured border; it reads as an app form, not as a paper. |
| 3 | The chosen option is shown by a gold filled circle and a gold border; a paper uses a filled bubble in black. |
| 4 | The site header (logo, menus, language and theme switches) stays above the paper. It is a distraction and a way to leave a timed exam. The old site hid it. |
| 5 | The sticky bar is a rounded card with a shadow and a coloured progress strip; the submit bar is gold. |
| 6 | There is no paper heading: no title block with duration and full marks, no student line, no instructions. |
| 7 | Marks per question are not shown. |
| 8 | The question label ("Question 1") is a grey caption above the stem, not a number on the same line. |
| 9 | The text size control and the grid sit in different places. |

## 5. Target design

A single white sheet, centred, 820 px wide at most (full width with 20 px gutters on a phone), in a full-screen layer that covers the site header and footer and ignores the theme. Black on white only; grey for secondary text; no gold, navy, red or green.

```
┌─ sticky bar (white, 1 px black line below) ─────────────────────────────┐
│ BJS Prelim: Penal Code          Answered 4 / 10        00:17:42  [Qs] A−A+│
└──────────────────────────────────────────────────────────────────────────┘

            BJS PRELIMINARY MODEL TEST                (bold, centred)
            Time: 20 minutes     Full marks: 50     Wrong answer: −0.25
            Name: Suman Ahmed · Code: STU-0001
            ─────────────────────────────────────────
            ▸ Instructions                       (collapsible, open at the start)

  1.  Who normally issues a warrant of arrest?                    [5]   ⚑ Mark
        ( A )  The police diary writer
        ( B )  A magistrate or a court                          ← filled bubble when chosen
        ( C )  The district commissioner
        ( D )  A jailer
  ──────────────────────────────────────────────────────────────────────
  2.  ...

                     ── End of paper ──
┌─ fixed bottom bar (white, 1 px black line above) ───────────────────────┐
│ Answered 4, unanswered 6                               [  SUBMIT  ]      │
└──────────────────────────────────────────────────────────────────────────┘
```

Details:
- **Number and stem** on one line: the number in bold ("1." or "১."), the stem beside it (hanging indent), the marks in square brackets at the right end of the line. A hairline grey rule between questions, no card, no radius, no shadow.
- **Options:** a vertical list. Each is a bubble (28 px circle with the letter A, B, C or ক, খ, গ inside, 1.5 px black outline, white fill) followed by the text. **Chosen:** the bubble fills black with a white letter and the text turns bold. A multi-answer question uses square bubbles and the line "Select all that apply" in italics. The bubble has a 44 px tap area; the row does not take taps (unchanged rule).
- **Mark for review:** a small text button "⚑ Mark" at the end of the number line; flagged shows "⚑ Marked" and a bold "⚑" beside the number.
- **Locked and confirm:** the Confirm button is an outlined black button; the final note is grey italic.
- **Timer:** bold, tabular digits at the right of the bar; the final minute inverts (black band, white digits). The 10, 5 and 1 minute warnings still announce.
- **Progress:** text only ("Answered 4 / 10"); the coloured strip is removed.
- **Jump grid:** a panel of numbered squares with a black outline; answered squares are filled black with white digits; a flagged square has a thick outline and a "⚑" mark.
- **Submit:** a solid black rectangular button with white text; the confirmation is a white box with a black border and two buttons (outlined Cancel, solid black Submit). Focus still starts on Cancel.
- **Fonts:** Bangla papers keep the Bangla face; English papers use a serif face (Georgia, Times) for the stem and options so the page reads as print.
- **Paper head:** the title, the time, the full marks and the negative-marking note when the exam has one; the student's name and code; "Instructions" with the exam's rules, in a collapsible block that is open the first time.
- **Frame:** the paper layer is fixed over the whole screen (above the site header), so the header, the language and theme switches and the menus are not reachable during the sitting, as on the old site. The browser Back button and the existing unsaved-change protection still work, and the paper keeps its own title in the bar.
- **Dark theme:** the paper is white in both themes (a printed paper does not go dark).

## 6. Rules that do not change

All eleven behaviours in section 3, the API calls, the local backup keys, the text size storage key, the message keys for every existing string, the roles and labels that tests and screen readers use. The result page's review of an MCQ keeps today's look (`McqQuestion` stays as it is); a paper-style review with a tick and a cross in black is a possible later step. The written exam page is not part of this work.

## 7. Implementation

New files, so the result page is untouched:
- `paper-theme.ts`: the one place for the paper's colours and type scale (white, black, grey, serif stack), exposed as CSS variables on the paper layer.
- `paper-question.tsx`: one question as laid out above; the same props and behaviour as `McqQuestion` (pick, flag, confirm, locked, editable), memoized the same way.
- `paper-head.tsx`: the title block, student line and instructions.
- In `mcq-paper.tsx`: only the frame, the bar, the grid, the submit bar and the dialog change (markup and classes). The autosave, timer, backup, warning and submit logic is not edited.
- The text size control gets a plain variant (black outline, no radius) for the bar.
- Strings: a few new keys in both languages (marks label, instructions, end of paper, mark and marked, time, full marks, wrong-answer note), keys identical in `en` and `bn`; Bangla uses "পাতা" never "পেজ".

## 8. Tests

- Update the existing tests of the paper and the question where markup changed (find them with a search for `McqPaper`, `McqQuestion` and `sitting`), and say which.
- New tests: the paper layer covers the page and hides the site header; no theme classes (no `bg-card`, `border-accent`, `text-accent` in the paper); chosen option shows a filled bubble and `aria-checked`; multi-answer uses a checkbox role; numbering uses Bangla digits for a Bangla paper; marks and the negative note show; the final minute inverts the timer; the heading shows the student's name and code; instructions collapse; the grid marks answered and flagged; locked answers still refuse taps and show the final note; the submit dialog and counts; keyboard order (Tab reaches the bubbles, the bar, the Submit button).
- Behaviour tests already present (autosave, retry, backup, warnings, close at the deadline) must pass unchanged.

## 9. Risks

| Risk | Handling |
|---|---|
| Two agents edit `mcq-paper.tsx` at once. | The implementation starts only after the submit-rules agent has finished. |
| A student cannot reach the site menu during a sitting. | Intended (as on the old site); the Back button and the existing leave protection remain. |
| White paper in a dark room is bright. | Accepted for exams; a larger text size and the grey rules keep it calm. Not a theme option. |
| Serif for English could look odd next to Bangla. | Only the paper's own text uses it; Bangla keeps its face. |
| Existing tests assert old classes. | Updated on purpose and listed in the report. |

## 10. Open choices (defaults used unless you say otherwise)

1. English paper text in a serif face (default yes).
2. Hide the site header during the MCQ sitting (default yes, as the old site did).
3. Show the exam's rules as "Instructions" at the top (default yes, collapsible).
4. Written sitting page keeps its current look for now.
