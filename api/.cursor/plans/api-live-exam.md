# api-live-exam

Stage 2. Start only after `api-live-catalog` exits. Spec Sections 2 and 4. Tests: `api/.cursor/tests/api-live-exam.md`. Rule: `api/.cursor/rules/api-live-exam.mdc`.

Do not build the roster, sittings, or evaluation. Do not attach an exam to the previous paper/component tables.

Next Flyway version after catalog.

## Slice 1 — One-type exams

- An exam belongs to one module and is `MCQ` or `WRITTEN`.
- Names, description, and rules are Bangla and English.
- Fields: live instant, duration, pass percent 0 through 100. Written exams also store a maximum and one question PDF. MCQ exams store questions instead of a PDF.
- An MCQ question has one language, a stem, options, one correct option, marks, an optional explanation, and a position. No shuffle.
- Create and edit store a draft. Final save, then publish. Publish without a pass percent is rejected. Publish of an MCQ exam with no questions is rejected. Publish of a written exam without a maximum and a question PDF is rejected.
- The stored MCQ maximum returned to clients is the sum of the question marks.
- Publishing is rejected when another published exam in the same module ends fewer than five minutes before this one starts, or overlaps it. The error names the other exam. A draft may keep the conflicting time. An exam in another module does not block.
- When the server time passes the live instant, the question PDF, the questions, and the schedule can no longer be edited. When the server time passes the end, the exam remains `PUBLISHED`.

## Exit

One module can hold a published MCQ exam and a published written exam whose windows are at least five minutes apart. The MCQ maximum equals the sum of its question marks. After the window the exam is still published.
