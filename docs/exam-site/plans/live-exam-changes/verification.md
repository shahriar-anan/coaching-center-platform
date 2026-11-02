# Verification: after implementation

Run this only after Shahriar says go. Until then, nothing in this file is run.

Owner: the verification agent (or Shahriar). Input: the three agent reports and the merged branch.

## V0. Before running anything

- Confirm Shahriar's go, in writing.
- Confirm the dev API and web servers are off, or use the start steps in the saved memory "dev server start" (API on 8092 through `SERVER_PORT`, started with `Start-Process mvnw.cmd`).
- Do not reload `coaching_center_sample` without his go. The migration V65 is applied to the sample database only with that go.

## V1. API build and tests

1. Compile the API.
2. Run the exam tests that the three agents wrote, and the existing exam tests they touched:
   - `ExamAuthoringStep1IT` (updated by B);
   - the new attempt-snapshot tests (A);
   - the new authoring and withdraw tests (B);
   - the leaderboard tests (A);
   - any existing test that referenced `McqRescorer`, `EXAM_TEXT_REQUIRED` or `QUESTION_STRUCTURE_LOCKED` (should be none after B).
3. Record the pass and fail counts. Any failure is reported with its output.

## V2. Web checks

1. Type check the web project.
2. Lint the web project.
3. Run the web tests that the agents wrote or changed:
   - `web/tests/s1-a/exam-form-state.test.ts`;
   - the change-list, leaderboard and text-preview tests (C);
   - `web/tests/reviews` and `web/tests/ws3` tests that use empty descriptions.
4. Build the web app. Before the build output is used, check that the API base variable is empty, and check the built chunks for the API base (saved memory "web build API base": an absolute URL logged admin out on refresh).

## V3. Scenario walkthrough (manual, dev servers)

Each scenario has an expected result. Record pass or fail, with a screenshot or the API response for any failure.

| # | Scenario | Expected |
|---|---|---|
| S1 | Create an MCQ exam, leave both descriptions and both rules blank, add a question, final save, publish. | Publish succeeds. Student page shows no description or rules section. |
| S2 | Create a package with a blank description, then edit it, then clear an existing description. | Both save. Student package page shows no description. |
| S3 | Publish an MCQ exam with one question. A student starts the first attempt. Staff change the question wording and publish. Reload the student's running attempt. | The student still sees the original wording. |
| S4 | The same student submits. Staff change the correct key and publish. Reload the student's result. | The score and pass result are unchanged. |
| S5 | A second student starts after the S3 publish. | Sees the new wording and the new question set. |
| S6 | Staff add a question, and separately change the number of options on another question, both on a published exam with students sitting. Publish. | Both publish. No `QUESTION_STRUCTURE_LOCKED`. The first student's answers still count against their own paper. |
| S7 | A student has an answer on an option that a later version removed. The student reloads and submits. | The answer scores against the student's own paper, not the new one. |
| S8 | A student retakes after a publish. | The retake uses the latest version. |
| S9 | Leaderboard for an exam with two versions and different maximums. | Ranking by percent. Each row shows its own maximum. |
| S10 | Withdraw a published exam while a student has an open sitting. | Refused with `EXAM_SITTING_OPEN`, with the message in English and Bangla. |
| S11 | Withdraw the same exam after the sitting's deadline has passed. | Allowed. The exam is hidden from students. Posted results stay readable. |
| S12 | Edit the withdrawn exam's description. | Saved as a staged revision. The change list shows it. Students still cannot see the exam. |
| S13 | Publish the staged revision while the exam stays withdrawn. | Publishes. Still withdrawn. |
| S14 | Restore the withdrawn exam. Final save. Publish. | Restore returns it to draft. Publish makes it visible again with the new content. |
| S15 | Written exam with two marked papers, highest mark 30. Set the maximum to 25. | Refused with `WRITTEN_MAXIMUM_BELOW_MARK` on the maximum field, on edit and on publish. |
| S16 | Written exam: change the pass percent after results are posted. | Posted results are unchanged. The change list says so. |
| S17 | Navigate every exam archive control in the staff UI (editor, package exam menu, confirm dialog, status pill, toasts). | Every control and message reads Withdraw (en and bn). No exam text reads Archive, except where the README allows it. |
| S18 | Written exam: a student whose paper was issued before a PDF change. | Still sees the paper they were issued. (Regression check for E29.) |
| S19 | An MCQ attempt started before this change (no snapshot). | Falls back to the live rows. Score is not changed by the deploy. |
| S20 | Change list for an MCQ edit with a key change. | Copy says submitted scores do not change. No "re-scored" line. |

## V4. Grep audit

Run these searches and record the result of each.

1. Exam archive wording in the web messages (en and bn), restricted to the exam keys. The only allowed hits are the package and category keys and the O1 label.
2. `EXAM_TEXT_REQUIRED` and `QUESTION_STRUCTURE_LOCKED`: no hits in the API or the web, except in the test that asserts their absence (if one exists).
3. `McqRescorer`: no hits.
4. `hardDelete` patterns: `deleteAll(` on question or option repositories in the exam code: no hits, except on attempt-owned tables if any.
5. `requireTexts`, `requireStructureOpen`, `structureChanged`, `hasExamText`: no hits.

## V5. Decision checklist (sign-off)

For each decision, record the evidence: a test name, a scenario number, or a grep result.

| ID | Decision | Evidence |
|---|---|---|
| D1 | Withdraw wording for exams | S17, V4.1 |
| D2 | Internal names unchanged | V4.1 (no route or code renamed); diff review |
| D3 | Package and category labels unchanged | V4.1 |
| D4 | Ended-window edits still work | Regression test, or S12 |
| D5 | Withdrawn edit routing | Authoring test (B10), S12 |
| D6 | Publish a withdrawn revision | S13 |
| D7 | Withdraw refused during open sitting | S10, S11 |
| D8 | Restore unchanged | S14 |
| D9 | MCQ attempt pinning | S3, S7, A10 tests |
| D10 | No re-score | S4, S20, grep V4.3 |
| D11 | Structure lock removed | S6 |
| D12 | Soft delete only | V4.4, S7 |
| D13 | Running sitting shows its start questions | S3 |
| D14 | Rules and description live | Manual check: edit a rule on a running exam and reload |
| D15 | Leaderboard ranks by percent | S9 |
| D16 | Written maximum floor | S15 |
| D17 | Written pass percent does not re-judge | S16 |
| D18 | Optional texts | S1, S2 |
| D19 | No question-change notice | Grep: no new notice type added |

## V6. Final report

Write the final report with these sections: what was built, the test and build results with counts, each failing scenario with its output, each deviation from the briefs and why, open items (O1 and O2 from the README, plus anything new), and the decision checklist.

Commit only when Shahriar asks. Attribution follows the current system reminder.
