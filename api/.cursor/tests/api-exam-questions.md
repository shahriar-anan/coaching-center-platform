# api-exam-questions tests

Required cases for Phase 3 Stage 2. Java `*IT` classes are written during implementation. PostgreSQL only. No manual check.

Shared setup: a Master Admin, a System Admin, and one module with a paper from Stage 1.

## ExamQuestionIT

Slice 1.

- Creating an MCQ component and a written component on the same paper succeeds. A second MCQ component on that paper returns `COMPONENT_EXISTS`.
- Creating a component on a section id returns `NODE_TYPE_REJECTED`.
- An MCQ question stores `stem`, two or more options, exactly one correct option, `marks`, and `subject`. The response and the `questions` table have no `stem_en`, `stem_bn`, or other second-language column.
- A written question stores `stem`, `marks`, and `subject`, and has no options. An explanation, when sent, is one text field.
- An MCQ body with zero correct options, two correct options, or one option returns `VALIDATION_FAILED`. Marks of 0 or below are rejected.
- Attaching an existing MCQ question to a second paper's MCQ component keeps one question row. Patching the stem changes the stem returned by both components. Patching options replaces them in the sent order and still requires exactly one correct option.
- Reorder changes question positions on that component only. The other paper's attachment keeps its own position.
- Attaching that MCQ question to a written component returns `QUESTION_TYPE_MISMATCH`.
- Detaching a question from one component leaves the question row and leaves it attached to the other component.
- A System Admin can create and attach a question. There is no question `DELETE` route in the generated OpenAPI document.
- Soft-deleting the paper hides its components and attachment rows. The question row remains for an admin question read.
