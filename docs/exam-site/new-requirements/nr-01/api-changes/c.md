# NR-01 API changes: agent C (commerce and access)

Slices C1 (`2b22271`), C2 (`d58fa04`), C3 (this commit). Folded into `current-api.md` by the orchestrator. Field additions only (contracts §7.1).

| Route | Who | Change |
|---|---|---|
| `POST /api/v1/live-exam-orders` | Student | An `EXAM` order needs no `moduleId` (R5). The amount is the exam's effective price (discount applied when active). A free exam, or an exam with no price, gives `409 EXAM_NOT_FOR_SALE` (an unpriced exam gave `PRICE_REQUIRED` before). A holder who can still sit gets `409 ALREADY_PURCHASED`; an expired holder may buy again. |
| `GET /api/v1/live-exam-orders`, `GET /api/v1/live-exam-orders/{id}` | Master Admin, System Admin | Each row gains `listPrice` (the price before discount) and `discountPercent` (set only while the discount is active, else null). An exam order's `moduleId` and titles are its primary package, for display only. |
| `GET /api/v1/me/live-exam-orders` | Student | Each row gains `listPrice` and `discountPercent`, as above. |
| `POST /api/v1/live-exam-grants` | Master Admin | An `EXAM` grant may omit `moduleId`. A `moduleId` given must be a package the exam is in now (`400 VALIDATION_FAILED` otherwise). The grant stores no package. A student who can still sit the exam gets `409 GRANT_ALREADY_ACTIVE`; an expired grant is retired (reason `RETIRED`) and replaced. |
| `GET /api/v1/students/{id}/live-exam-access` | Master Admin, System Admin | Each row gains `grantedAt`, `endsAt` (null for lifetime) and `expired`. Expired rows stay listed. A free exam is not listed (a free exam is not a grant). An exam row's `moduleId` is the exam's primary package, for display only. Exam rows whose grant has no package are now listed. |
| `GET /api/v1/me/live-exam-access` | Student | Each `modules` row gains `grantedAt`, `endsAt` and `expired`. Each `exams` row gains `grantedAt`, `endsAt` and `expired`. Expired rows stay listed. A free exam is not listed. Exam rows whose grant has no package are now listed, with `moduleId` null. |
