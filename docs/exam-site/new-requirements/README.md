# New requirements (after client approval of the design)

Opened 28 October 2026 on branch `demo/client-review`. The client approved the overall design and then raised difficulties and new requirements. This folder holds one file per requirement, the rules every one of them follows, and the register below.

**Audience:** Shahriar, and the agent working a requirement with them. [../decisions.md](../decisions.md) and [../current-api.md](../current-api.md) win where this folder is silent. A requirement that changes a decision there says so in its own file and is added to `decisions.md` only after Shahriar agrees.

## How a requirement moves

1. **Capture.** Shahriar states it. The agent adds a row to the register and a file `NR-nn-short-name.md` from [_template.md](_template.md). Status: `captured`.
2. **Analyse.** The agent reads the current screens and API, and fills in "Today" and "Proposal" in the file. Short, with the open questions. Status: `proposed`.
3. **Agree.** Shahriar answers the open questions. The file's "Decisions" table is filled. Status: `agreed`. No code before this.
4. **Build.** The agent follows the file's slices and [rules.md](rules.md). Status: `building`.
5. **Verify.** The checks in the file pass; Shahriar does the browser pass. Status: `done`, with the date and commit.

One requirement at a time unless Shahriar says otherwise. Do not start NR-n+1 while NR-n is `building`.

## Register

| ID | Requirement | Status | File |
|---|---|---|---|
| NR-01 | Exam segment: sidebar **Exams** page grouped by module; old-site rules (price, discount, free); live window separate from duration; archived participants with a relative position; editing after the window with a change summary on publish | API done 28 October 2026 on `nr01/integration` (see the Record in the file); UI plan next | [NR-01-exam-segment.md](NR-01-exam-segment.md) |

Add new rows at the bottom. Never renumber. A dropped requirement stays in the table with status `dropped` and a reason.

## Files in this folder

- [rules.md](rules.md): the rules every requirement follows (scope, roles, look, copy, tests, safety).
- [_template.md](_template.md): the shape of a requirement file.
