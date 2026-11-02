# Live exam changes: master plan

Status: planned, not started. Written for: the three agents who will carry it out, and Shahriar, who reviews it.
Owner: Shahriar Anan. Sign-off: Shahriar, after the verification phase.

Briefs (one per agent, plus verification):

- [agent-A-mcq-attempt-pinning.md](agent-A-mcq-attempt-pinning.md): API, MCQ sittings, scoring, leaderboard
- [agent-B-exam-rules-withdraw-api.md](agent-B-exam-rules-withdraw-api.md): API, authoring, withdraw, texts, written rules
- [agent-C-web.md](agent-C-web.md): web, staff and student screens, labels, tests
- [verification.md](verification.md): run only after Shahriar says go

---

## 1. Decisions (locked)

| ID | Decision | Why |
|---|---|---|
| D1 | The word "Withdraw" replaces "Archive" for **exams only**, in every user-facing label, dialog, toast, menu item, status pill, notice and error message. | Shahriar's request. The exam editor already says Withdraw in most places; the exam menu in the package list and the exam status pill still say Archive or Archived. |
| D2 | Internal names stay: the DB status value `ARCHIVED`, the routes `/archive` and `/restore`, the error code `EXAM_ARCHIVED`, and the permission `EXAM_DELETE`. | Renaming them would break API clients and the database for no user-visible gain. |
| D3 | Category and package "Archive" labels do not change. | Only exams are in scope. |
| D4 | "Archived" in the older sense (the live window has ended) is unchanged. Edits to these exams already work. | Shahriar confirmed the first meaning. |
| D5 | A withdrawn exam takes edits. If it was ever published (`publishedAt` set), edits are staged as a revision, the same as a published exam. If it was never published, edits apply directly. | Students cannot see a withdrawn exam, but a withdrawn exam's rows are the published content, so the staging rule still protects it. |
| D6 | The Master Admin can publish a staged revision of a withdrawn exam. It stays withdrawn. | Keeps content fixes possible without a restore. |
| D7 | Withdrawing is blocked while a student has an open sitting (in progress, not submitted, deadline not yet passed). The error is `EXAM_SITTING_OPEN`. | Gap 5 from the earlier list; Shahriar accepted the recommendation. |
| D8 | Restore returns the exam to draft, as today. The publish path after restore is unchanged. | Existing behaviour. |
| D9 | **MCQ attempt pinning.** At the start of each MCQ attempt, the question set (text, options, correct keys, marks, explanations) is copied onto that attempt. All reads, answer checks and scoring for that attempt use the copy. | Shahriar accepted the MCQ recommendation. Matches the written paper rule (E29). |
| D10 | Attempts already submitted keep their score. Publishing a key, marks or option change does not re-score anything. This replaces rule E28 for MCQ. | Shahriar: the student who sat v1 keeps the v1 result. |
| D11 | Questions and options can be added, removed or changed at any time, including after students have started. The structure lock (rule R16) is removed. | Shahriar: the question maker handles conflicts. Pinning makes this safe. |
| D12 | Live question and option rows are never hard-deleted. They are soft-deleted, so pinned answers still point at real rows. | Needed for D9 and D11. |
| D13 | A running MCQ sitting shows the questions it started with. Reloads do not change them. No polling. | Written rule, and avoids changing options under a student's answers. |
| D14 | Rules and description stay live. A reload shows the current text. They are not pinned. | They do not affect scoring. Shahriar accepted. |
| D15 | Leaderboard ranks each attempt by percentage of its own maximum, and shows the raw score next to it. Ties keep today's tie-break. | Maximums differ between versions, so raw marks are not comparable. |
| D16 | Written: a maximum cannot be set below the highest mark already given (saved or pending). Blocked on edit and on publish, with `WRITTEN_MAXIMUM_BELOW_MARK`. | Shahriar accepted. |
| D17 | Written: changing the pass percent does not re-judge posted results. The change list says so. | Shahriar accepted. |
| D18 | Rules and description are optional for exams and packages. Titles stay required. Publish no longer checks the four texts. | Client request 1 and 2. |
| D19 | No notice to students when questions change. | Shahriar dropped this recommendation. |

## 2. What does not change

- The DB status value `ARCHIVED`, the routes `/archive` and `/restore`, the error code name `EXAM_ARCHIVED`, the permission `EXAM_DELETE`.
- Category and package archive labels and behaviour.
- The "answers can be changed" switch. It stays live and is not pinned.
- Exam windows, durations and the mode rules.
- Written question PDF pinning (E29).
- Student-side label "archived exams" for exams whose window has ended (see open item O1).

## 3. Work split and file ownership

Three agents can work at the same time with no shared files. Each agent works in its own git worktree and branch.

| Agent | Branch (suggested) | Owns (may edit) | Must not edit |
|---|---|---|---|
| A: MCQ attempt pinning | `feat/mcq-attempt-pinning` | `db/migration/V65__live_exam_attempt_snapshot.sql`, `LiveExamParticipant.java`, a new `McqAttemptSnapshot.java`, `LiveExamSittingService.java`, `LiveExamLeaderboardService.java` (and its DTO), new A tests | Everything else in the list for B and C |
| B: authoring, withdraw, texts, written rules | `feat/exam-rules-withdraw-api` | `LiveExamService.java`, `LiveExamRevisionService.java`, `LiveExamModuleService.java`, `ExamChangeListBuilder.java`, `LiveExamParticipantRepository.java`, `ErrorCode.java`, `McqRescorer.java` (delete), `ExamAuthoringStep1IT.java` and B tests | The migration folder, `LiveExamSittingService.java`, the leaderboard service |
| C: web | `feat/exam-rules-web` | everything under `web/src/` and `web/tests/` for exams, packages, leaderboards and results, plus both i18n message files | Any Java file |

Rules for everyone:
- If a change is needed in a file you do not own, stop and write it in your report. Do not edit it.
- Keep to the contracts in section 4. If a contract must change, say so in your report before you finish.
- Match the surrounding code's style, naming and comment density.

## 4. Contracts (agree before coding)

**Error codes (API `ErrorCode`, web `errors.ts`)**

| Code | Status | Use | Owner |
|---|---|---|---|
| `EXAM_SITTING_OPEN` (new) | 409 | Withdraw refused: a student has an open sitting. Message: "Students are sitting this exam now. Withdraw it after the sitting ends." | B adds; C maps |
| `WRITTEN_MAXIMUM_BELOW_MARK` (new) | 409 | Maximum below the highest mark given. Field error on `writtenMaximum`. | B adds; C maps |
| `EXAM_TEXT_REQUIRED` (remove) | | No longer thrown. | B removes; C removes |
| `QUESTION_STRUCTURE_LOCKED` (remove) | | No longer thrown after D11. Check that no other place still uses it before removing. | B removes; C removes |
| `EXAM_ARCHIVED` (keep code) | 409 | Message text changes to "withdrawn" wording (D1). | B |

**Leaderboard row (API → web)**
- Adds `percent`: number, 2 decimal places, `null` when the attempt's maximum is 0.
- `maximum` on each row is the attempt's own maximum (snapshot for MCQ, `writtenMaximum` for written).
- Existing fields keep their names.

**Change list (API → web)**
- `impact.submittedMcq` is always 0 for MCQ from now on. The field stays for compatibility.
- `rescored` in the change log is always 0 for MCQ.
- No new fields. The web shows the new copy from its own strings.

**Migration**
- Only Agent A adds a migration: `V65__live_exam_attempt_snapshot.sql`, adding `question_snapshot TEXT NULL` to `live_exam_participants`. Nobody else adds one.

**Attempt snapshot JSON (inside `question_snapshot`)**
- A list of questions in position order. Each question: `id` (the live question row id), `stem`, `marks`, `explanation`, `position`, `multipleAnswer`, and `options`.
- Each option: `id` (the live option row id), `text`, `correct`, `position`.
- Answers keep pointing at live row ids. This works only because of D12.
- A participant with a `null` snapshot (started before this change) uses the live rows, as today. No re-scoring.

## 5. Sequence

1. Start A, B and C at the same time, each in its own worktree.
2. Each agent writes its report (section 7 template) and leaves its changes uncommitted.
3. Merge. The expected conflicts are none. If any appear, they are in a file that section 3 gives to one agent only, so report it.
4. Shahriar says go. Then run [verification.md](verification.md).
5. Commit only when Shahriar asks. Attribution follows the current system reminder.

## 6. Hard rules for all agents

- **No tests, builds or test runs** until Shahriar says go. Write tests; do not run them. (From the saved memory "no tests unless asked".)
- **No migration runs** and **no reload of the `coaching_center_sample` database** without Shahriar's go.
- **Do not delete** `C:\Users\HP\AppData\Local\Temp\claude`.
- **Bangla strings** are proposals. Mark each one in your report so Shahriar can check it. The proposed Bangla for "Withdraw" is "প্রত্যাহার".
- Keep user-visible copy short and plain, in both English and Bangla.

## 7. Report template (each agent, when finished)

```
Agent: A | B | C
Branch and worktree:
Files changed (path, one line each):
Decisions taken that were not in this plan (should be none):
Contract changes (should be none):
Deviations from the brief and why:
Open questions for Shahriar:
Tests written (names only, not run):
Spike results (Agent A only, and Agent B's FK note in section 8):
```

## 8. Open items (not in scope unless Shahriar says so)

- **O1.** The student side still says "archived exams" for exams whose window has ended. This is easy to confuse with Withdraw. Recommendation: change it to "Ended exams" in a follow-up. Not in this plan.
- **O2.** The "answers can be changed" switch is not pinned. Recommendation: leave it live for now.

## 9. Acceptance summary

The plan is complete when all of these hold (details in [verification.md](verification.md)):
- Every decision D1 to D18 has a matching test or a manual check.
- No user-visible exam text says Archive or Archived, except where D3 and D4 allow it.
- A student who started an MCQ attempt before an edit still sees and is scored on the questions they started with.
- The leaderboard ranks by percent.
- Withdraw is refused while a sitting is open.
- Descriptions and rules can be left blank for exams and packages.
