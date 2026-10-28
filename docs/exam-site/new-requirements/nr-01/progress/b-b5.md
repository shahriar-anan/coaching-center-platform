# B5: popular (agent B)

Committed together with B1 to B4 and B6 (shared files).

## Changed
- `PopularService` and `PopularController`: `PUT /live-exams/{id}/popular` and `PUT /live-exam-modules/{id}/popular`, Master Admin only (`LIVE_EXAM_POPULAR`), applied at once, audit `LIVE_EXAM_POPULAR_SET`.
- Package list `popular=true` (`findPublishedCatalog`, `findVisibleToStudentCatalog`); exam list `popular=true`.

## Tests: `PopularIT` 3/3 (green). `PublicAccessIT` 4/4, `ModuleCoverIT` 3/3 (green).
