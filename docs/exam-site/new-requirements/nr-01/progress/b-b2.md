# B2: categories hold standalone exams (agent B)

Committed together with B1 and B3 to B6 (shared files).

## Changed
- `LiveExamCategoryService.tree`: each category lists its packages and its exams (`exams`, each once, with `packages[]`).
- `LiveExamPublicService`: `categories` gains `exams`; `categoryExams` (new); `upcoming` joins through the home category (`findUpcomingInCatalog`) and skips practice exams; `moduleOnly` comes from `ExamPricing.view`; `exams()` reads membership in position order.
- `LiveExamPublicController`: `GET /public/live-exam-categories/{id}/exams` (new); `popular` and `favourite` added.
- `LiveExamRepository`: category and upcoming queries.

## Tests
- `LiveCatalogIT` 7/7, `LivePublicCatalogIT` 9/9 (last full B run).
- Test fixes (not weakened): a stale anonymous snapshot after the package price change (`moduleOnly` is now correct); `moduleId` is absent, not null (the app omits null fields).
