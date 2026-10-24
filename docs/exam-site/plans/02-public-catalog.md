# Step 2 — Public catalog access

API only. Shahriar confirmed this plan on 2026-10-23. Land it after step 1. Flyway `V30`. Do not edit `V14`–`V29`.

This is the access-rule portion of `../steps/02-public-catalog.md`. The public page structure is still unspecified, so this plan does not add one. Price columns are step 3. This step does not grant a sitting.

## Slice 1 — Paper language

`V30__live_exam_paper_language.sql` adds `paper_language VARCHAR(2) NOT NULL` on `live_exams`, with a check of `BN` or `EN`. Rows already in the table are set to `BN` before the column becomes required, so the migration can run on a database that already has exams. A staff edit can change that value until the live window starts.

`POST /api/v1/live-exam-modules/{moduleId}/exams` requires `paperLanguage` of `BN` or `EN`. A missing or other value is `400` `VALIDATION_FAILED`. There is no second column for stems, options, or the written PDF.

`PATCH /api/v1/live-exams/{id}` accepts `paperLanguage` while the server clock is before `live_at`. That edit returns the exam to draft, the same way a title edit does. At or after `live_at`, the field returns `409` `CONTENT_LOCKED`.

`paperLanguage` is added to the staff `ExamView` and to `StudentExam`. The sitting read carries it so the question page can choose a typeface. Question stems stay a single text column.

## Question page language

Shahriar confirmed this on 2026-10-23. Step 6 follows it. This step only stores the flag.

- The site language covers the shop and the chrome on the question page: timer, save, submit, marks labels, title, description, and rules.
- The question body is the stored MCQ text or the written PDF. `BN` selects the Bangla typeface. `EN` selects the Latin typeface.
- The site language does not replace that body. A paper in the other language is allowed.
- Rows already in `live_exams` are set to `BN`. Staff can change the flag until `live_at`. After that it stays locked.

Every live-exam create body in `LiveExamIT`, `LiveSittingIT`, `LiveEvaluationIT`, and `LiveRosterIT` sends `"paperLanguage": "BN"` unless the test is asserting the flag itself.

## Slice 2 — Public read

New `LiveExamPublicController`. These routes are `GET` only and `permitAll` in `SecurityConfig`. A missing token and a valid student bearer token call the same methods and return the same JSON. The handler does not branch on role or on a roster row.

```
GET /api/v1/public/live-exam-categories
GET /api/v1/public/live-exam-modules
GET /api/v1/public/live-exam-modules/{id}
GET /api/v1/public/live-exam-modules/{id}/exams
GET /api/v1/public/live-exams/{id}
```

A category, module, or exam is on this read only when it is published, not deleted, and its category branch is not archived. An archived ancestor hides the branch, as `LiveExamCatalogVisibility` does today. Drafts stay on the staff reads. `GET /api/v1/live-exam-categories`, the roster-gated module and exam lists, and `LiveExamSittingService.studentExam` stay as they are. Take exam still requires a roster grant.

The access payload is the shop copy the step already requires, plus the schedule columns already stored on the exam. It is not a page layout.

| Record | Fields |
|---|---|
| Category node | `id`, `parentId`, `titleEn`, `titleBn`, `position`, `children`, `modules` |
| Module | `id`, `categoryId`, `titleEn`, `titleBn`, `descriptionEn`, `descriptionBn`, `position` |
| Exam | `id`, `moduleId`, `examType`, `titleEn`, `titleBn`, `descriptionEn`, `descriptionBn`, `rulesEn`, `rulesBn`, `paperLanguage`, `liveAt`, `endAt`, `durationMinutes` |

The category tree omits an archived node and its descendants. Its `modules` list contains published modules only. Module and exam lists use the existing page envelope. A draft, deleted, or archived-branch module or exam returns `404` on the single-item routes.

The exam record has no `questions` field, no stem, no option, no correct flag, no explanation, no file name, and no PDF bytes. There is no public question or PDF route. `GET /api/v1/public/live-exams/{id}/question-pdf` is unmapped and returns 404. The existing sitting PDF route stays authenticated and unchanged.

No price column is added. `access_mode` stays `MANUAL`.

## Tests

New `LivePublicCatalogIT` on PostgreSQL. Do not use H2. Run `mvn test "-Dtest=LivePublicCatalogIT,LiveCatalogIT,LiveExamIT"`.

- An anonymous `GET` of a published category, module, and exam returns 200 and the access payload, including `paperLanguage`.
- The same calls with a student bearer token and no roster row return that same JSON.
- A draft module and a draft exam are absent. The staff read still returns them.
- An archived category hides its branch from the public tree, the module read, and the exam read.
- The public exam body has no `questions`, `stem`, `options`, `correct`, or file name, and the response is not a PDF.
- `GET /api/v1/public/live-exams/{id}/question-pdf` returns 404.
- `GET /api/v1/live-exams/{id}` with no token stays 401. A student with no roster row still does not receive that exam on the existing route.
- Create without `paperLanguage`, and create with a value other than `BN` or `EN`, return 400. A patch before `live_at` changes the flag and returns the exam to draft. A patch at `live_at` returns 409.
- One create uses `EN` and the public exam read returns `EN`.

## Exit

A visitor with no account, and a student who has bought nothing, can read the published catalog. The payload has no questions and no PDF bytes. Each exam has one paper language, set on create and editable before the window. Take exam still requires a grant.

## Out of this step

Prices, SSLCommerz, order tables, Google, notifications, the website, and any public field beyond the table above.
