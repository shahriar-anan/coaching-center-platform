# NR-01 web impact: agent B (packages and catalog)

One line per item: what the UI must do, and the API field or route it uses.

## Sidebar Exams page (staff)
- Use `GET /api/v1/live-exams` with the filters `packageId`, `categoryId`, `standalone`, `type`, `mode`, `status`, `phase`, `q`, `pending`, `popular`, `sort`. Rows show `packages[]` ("also in…" note, R21), `pricing`, `phase`, `pendingChanges`, `questionCount`/`hasPaper`, `instructor`.
- The filters live in the URL (`useListParams`). Every list needs loading, empty, error and filtered-empty states.
- Instructors call the same route and see only their own exams; hide the page for students.

## Package membership (staff)
- Add an existing exam: `POST /api/v1/live-exam-modules/{id}/exams/{examId}`. Remove: `DELETE …/exams/{examId}`. Reorder: `PATCH …/exams/order` with `{examIds}`.
- On a published package these are staged; show the "changes waiting" state from the response's `pendingChanges`.
- `EXAM_ALREADY_IN_PACKAGE` (409): show "already in this package". `EXAM_ARCHIVED` and `MODULE_ARCHIVED` (409): show the existing archived messages.

## Package staged changes (staff)
- A published package edit now stays `PUBLISHED`. Show the "changes waiting" badge from `pendingChanges`.
- The change list is `GET /api/v1/live-exam-modules/{id}/pending-changes` (`fields`, `packageExams`, `impact`). Show each change; packages send no student notice.
- Discard: `DELETE …/pending-changes`. Publish: `POST …/publish` with `{revisionId}` from the change list. Show `PENDING_CHANGES_STALE` as "changed since you read it, read again".
- A System Admin sees the Publish button disabled (the route returns 403 `ACCESS_DENIED`). Only the Master Admin publishes a revision.
- Compatibility path: the web's publish button may still send no `revisionId`; the API accepts that for the Master Admin only. Remove this once the UI sends `revisionId` (contracts §7 item 4).
- A publish of a published package with nothing staged returns `NO_PENDING_CHANGES` (409). Do not show the publish button in that state.

## Discount and popular (staff)
- Package editor: `discountPercent` (1–99) and `discountEndsAt` (optional end), with the original price crossed out on student pages (B5, B6).
- Popular toggle (Master Admin only): `PUT /api/v1/live-exams/{id}/popular` and `PUT /api/v1/live-exam-modules/{id}/popular` with `{popular}`. Applied at once, not staged. A draft that is popular stays hidden.

## Standalone exams on category pages (student and public)
- Category pages list `exams` from the category tree, or `GET /api/v1/public/live-exam-categories/{id}/exams` (filters `examType`, `mode`, `phase`, `popular`). Each exam appears once, with its `packages[]` as badges.
- A standalone exam (`moduleId` null) has no package to open; open it from the category list.
- Upcoming list (`GET /api/v1/public/live-exams?upcoming=true`) now includes standalone exams.

## Popular section and badges (student)
- Student home: a "Popular" section from `GET /api/v1/live-exam-modules?popular=true` and `GET /api/v1/public/live-exam-modules?popular=true`. Badge on cards when `popular` is true.
- Package exam list: `…/exams?popular=true` for a popular filter.

## Favourites (student)
- Heart or star on each exam and package card, and on the exam page. Set with `PUT`/`DELETE /api/v1/me/favourites/exams/{id}` and `…/packages/{id}`.
- Favourites page or tab: `GET /api/v1/me/favourites`, which returns `exams` and `packages` with `access`, `pricing` and `favourite: true`. Items that have become hidden are not shown.
- The flag on public rows is `favourite` (true/false for a signed-in student, absent for a visitor). The button needs no purchase (R7).

## Pricing shown on package and exam rows
- Use `pricing` (`price`, `discountPercent`, `discountEndsAt`, `effectivePrice`, `free`, `moduleOnly`). The top-level `price` is still there for the current page.
- Free exams show no price (`pricing.free`).

## Things to click on staging after the UI plan
- Staff: add, remove and reorder an exam in a published package; the change list appears; a System Admin cannot publish it.
- Staff: discount and popular toggles; the staff exam list filters and "also in…" note.
- Student: the Popular section, favourites on cards and the favourites page, and the standalone exams on a category page.
