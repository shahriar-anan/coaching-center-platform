# Step 2 — Public catalog

Do not plan the field list or the page structure until Shahriar specifies them. He said he will. This file is the access rule and the constraints around that choice.

## Outcome

A visitor with no account can read the published exam catalog: categories, modules, and exams, including enough to feature a price per exam and a price for the whole module. The response has no question stems, options, correct answers, or PDF bytes.

A logged-in student who has not bought anything sees that same public catalog. Purchase state is step 3.

## Bind these

- Published only. Drafts stay on the staff read. An archived category hides its branch, as it does today.
- Shop copy stays bilingual: category titles, module titles and descriptions, exam titles, descriptions, and rules.
- Each exam gains one language flag, `BN` or `EN`, for the paper. Do not add a second column for stems, options, or the written PDF. The flag is part of this step even though the public field list is still open, because the column is required before the website can follow the admin's paper language.
- Questions stay hidden on `GET /api/v1/live-exams/{id}` until Take exam, for a student who already has access. The public read is a different payload and never includes them.
- `access_mode` is constrained to `MANUAL` in `V24`. Do not edit `V24`. A later migration can add prices and the language flag. Leave the roster in place.
- Staff catalog writes (create, draft, final save, publish, archive, delete) stay as they are.

## Where the code is

- `LiveExamCategoryController` requires `EXAM_READ` for the tree.
- `LiveExamModuleService.list` and `getPublished` require a roster row for students.
- `LiveExamService.list` requires a roster row for students. `view(exam, !student)` already omits questions on the student list.
- `LiveExamSittingService.studentExam` is the authenticated exam read. Do not reuse it as the public payload.

## Plan must name

- The public routes, and that they reject a request for questions or a PDF.
- How a logged-in student and an anonymous visitor share that payload.
- The language-flag column and where it is set (create and edit, before publish).
- Price columns only if Shahriar has included price in the public field list. Otherwise leave prices to step 3 and say so in the plan.
- The `*IT` cases: anonymous read of a published module, hidden draft, hidden archived branch, no stems, no PDF bytes.

## Stop and ask

If the public field list or the page structure is still unspecified, write only the access-rule portion and stop. Do not invent sections such as routine text, rules, or a featured layout.

## Out of this step

SSLCommerz, order tables, Google, notifications, and the website itself. Take exam still requires a grant. Step 2 does not grant one.
