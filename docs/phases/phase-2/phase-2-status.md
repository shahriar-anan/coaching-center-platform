# Phase 2 status

Status: in progress (`mobile-catalog` slices 1–14 done; phone exit open. `web-catalog` slices 1–7 implemented, browser exit open).

The binding spec is [phase-2-scope.md](phase-2-scope.md). This file is the glanceable step list. It does not repeat the spec. A stage plan adds order and test checkpoints. It does not drop an exit check, reorder these stages, or pull in a later-phase domain.

`web-catalog` is Stage 8. It is admin management only. Student browse, purchase, and content stay on `mobile`.

## Stages

```text
api-course-catalog → api-library-catalog → api-catalog-browse → api-purchase → api-payment-access → api-content-access → mobile-catalog → web-catalog
```

| Stage | App | Plan | Tests | Progress |
|---|---|---|---|---|
| `api-course-catalog` | `api/` | [plan](../../../api/.cursor/plans/api-course-catalog.md) | [tests](../../../api/.cursor/tests/api-course-catalog.md) | [progress](../../../api/.cursor/progress/api-course-catalog/) |
| `api-library-catalog` | `api/` | [plan](../../../api/.cursor/plans/api-library-catalog.md) | [tests](../../../api/.cursor/tests/api-library-catalog.md) | [progress](../../../api/.cursor/progress/api-library-catalog/) |
| `api-catalog-browse` | `api/` | [plan](../../../api/.cursor/plans/api-catalog-browse.md) | [tests](../../../api/.cursor/tests/api-catalog-browse.md) | [progress](../../../api/.cursor/progress/api-catalog-browse/) |
| `api-purchase` | `api/` | [plan](../../../api/.cursor/plans/api-purchase.md) | [tests](../../../api/.cursor/tests/api-purchase.md) | [progress](../../../api/.cursor/progress/api-purchase/) |
| `api-payment-access` | `api/` | [plan](../../../api/.cursor/plans/api-payment-access.md) | [tests](../../../api/.cursor/tests/api-payment-access.md) | [progress](../../../api/.cursor/progress/api-payment-access/) |
| `api-content-access` | `api/` | [plan](../../../api/.cursor/plans/api-content-access.md) | [tests](../../../api/.cursor/tests/api-content-access.md) | [progress](../../../api/.cursor/progress/api-content-access/) |
| `mobile-catalog` | `mobile/` | [plan](../../../mobile/.cursor/plans/mobile-catalog.md) | [tests](../../../mobile/.cursor/tests/mobile-catalog.md) | [progress](../../../mobile/.cursor/progress/mobile-catalog/) |
| `web-catalog` | `web/` | [plan](../../../web/.cursor/plans/web-catalog.md) | [tests](../../../web/.cursor/tests/web-catalog.md) | [progress](../../../web/.cursor/progress/web-catalog/) |

Do not start a stage until the previous stage's exit passes. Do not start a later slice until that slice's progress note exists.

## How a step is marked done

1. The cases for that slice in the stage's test file pass.
2. **Automated checks (agent).** API slices: run the matching `*IT` against real PostgreSQL. Mobile slices: run `flutter test` with a mocked HTTP client (do not boot `api` for widget tests). Also run `dart analyze` / `flutter analyze` when mobile Dart changes. Web slices: run `npm run lint`, `npm run build`, `npm run check:openapi`, and mocked component tests when present (do not boot `api` for those). Do not use H2. Do not add a mobile or web CI workflow. The existing API workflow already runs the `*IT` suite.
3. **Manual QA (Shahriar only).** API stages have no manual check. For `mobile-catalog`, slices 1–14 do not require manual QA. The **phone exit** (physical Android, live API) is manual: Shahriar runs it once after slice 14; the agent does **not** install an emulator, launch an AVD, test on a device, or run that checklist. The progress note and `phone-exit.md` record Shahriar's result. For `web-catalog`, slices 1–7 do not require manual QA. The **browser exit** is manual: Shahriar runs it once after slice 7 in [web-catalog.md](../../../web/.cursor/tests/web-catalog.md) against the running API; the agent does **not** open a browser or use browser automation. Record that pass in `web/.cursor/progress/web-catalog/browser-exit.md`.
4. Write `progress/<stage>/slice-<number>-<name>.md` before the next slice. Record what was done, what was tested and the result, any failure and why, and what is next.

## Exits

- `api-course-catalog`: a course with both titles, one content language, a multi-level tree, and lecture content can be created, edited, and reordered. A freemium course can mark any set of lectures free, including none, and can mark a course-level material free. A file over the configured limit is rejected. The default limit is 100 MB. Only the Master Admin can close and reopen. The tree rejects anything other than sections and lectures.
- `api-library-catalog`: a book with both titles can be created, edited, and toggled available.
- `api-catalog-browse`: a student can search and browse published, open courses and available books by either title, and cannot see a closed course or open content yet.
- `api-purchase`: a student can start a course purchase or a book order, including a delivery address, and can see the pending record. A second purchase during the same opening is refused. There is no cancel route.
- `api-payment-access`: an admin verifies a payment and the student gains access or the book order advances. Offline admission grants access with no payment. Either admin can advance delivery. Only the Master Admin records cash and verifies online payment. A successful payment does not grant access when the course is closed.
- `api-content-access`: free lectures, free course-level materials, a full-free course, and paid content for the current opening behave as the spec states. A closed course is hidden and serves nothing. An enrollment from the previous opening does not grant access after reopen.
- `mobile-catalog`: on a physical Android phone, a student opens the app, lands on the home page, and can search, browse, purchase, and open the content the API allows, in English or Bangla UI chrome. The course screen shows its cover, the nested outline, its own category path, and files the student may open.
- `web-catalog`: in the browser, a Master Admin can manage courses, lecture content, books, payment verification, offline admission, and book-order fulfillment. A System Admin can edit content and advance delivery, and does not see price, delete, archive, close, reopen, payment verification, or cash-collected controls. Students do not use these screens.

## Slices

Progress files are written when the slice finishes. Until then the slice is not started.

### api-course-catalog

| Slice | Check |
|---|---|
| 1 Course record | `CourseCatalogIT` |
| 2 Content tree | `CourseTreeIT` |
| 3 Files and lecture content | `CourseContentIT` |
| 4 Close and fixed access rules | `CourseCloseIT` |
| 5 Course categories | Follow-on, not part of the stage exit. `CourseCategoryIT` |
| 6 Material record | Follow-on, not part of the stage exit. `CourseMaterialRecordIT` |
| 7 Lecture summary | Follow-on, not part of the stage exit. `LectureSummaryIT` |
| 8 Progress | Follow-on, not part of the stage exit. `LectureProgressIT` |
| 9 Routine | Follow-on, not part of the stage exit. `CourseRoutineIT` |
| 10 Roster | Follow-on, not part of the stage exit. `CourseRosterIT` |
| 11 Instructors | Follow-on, not part of the stage exit. `CourseInstructorIT` |

### api-library-catalog

| Slice | Check |
|---|---|
| 1 Book record | `BookCatalogIT` |
| 2 Book categories | Follow-on, not part of the stage exit. `BookCategoryIT` |

### api-catalog-browse

| Slice | Check |
|---|---|
| 1 Student browse and search | `CatalogBrowseIT` |
| 2 Student course read | Follow-on, not part of the stage exit. `CatalogCourseReadIT` |

### api-purchase

| Slice | Check |
|---|---|
| 1 Course purchase | `CoursePurchaseIT`. Re-check open: local `coaching_center_test` already has pending `trx-open-1`, so the rerun returned `409`. |
| 2 Book order | `BookOrderIT`. Re-check open: local `coaching_center_test` already has pending `book-trx-1`, so the rerun returned `409`. |

### api-payment-access

| Slice | Check |
|---|---|
| 1 Verify payment | `PaymentVerifyIT` |
| 2 Offline admission | `OfflineAdmissionIT` |
| 3 Fulfillment and cash | `OrderFulfillmentIT` |

### api-content-access

| Slice | Check |
|---|---|
| 1 Content gate | `ContentAccessIT` |

### mobile-catalog

| Slice | Check |
|---|---|
| 1 Home tabs | `flutter test` for the home shell |
| 2 Browse and search | `flutter test` for both catalogs |
| 3 Course detail and content | `flutter test` for the tree, player, and files |
| 4 Purchase and history | `flutter test` for purchase |
| 5 Library order | `flutter test` for the order form |
| 6 Cover and outline | Follow-on. `flutter test` for the cover and nested outline |
| 7 Placed in | Follow-on, after `CatalogCourseReadIT`. `flutter test` for the category path |
| 8 Files | Follow-on, after `CatalogCourseReadIT`. `flutter test` for opening a file |
| 9 Freemium entitlement | Correction. `flutter test` for `GET /me/courses` plus the free flag |
| 10 Sibling order | Correction. `flutter test` for mixed children ordered by `position` |
| 11 PDF handoff and file names | Correction. `flutter test` for an external PDF open and a Bangla leaf name |
| 12 Cover presence | UI. `flutter test` for a missing cover, a loading slot, the 2 MB decode cap, and cover reuse |
| 13 Placement path | UI. `flutter test` for path order and the locale’s starting language |
| 14 Nested outline | UI. `flutter test` for an indented nested section and a file-row progress indicator |
| Phone exit | Shahriar, physical Android phone, after slice 14 |

### web-catalog

Slices 1–7 implemented. Browser exit open (Shahriar).

| Slice | Check |
|---|---|
| 1 Course record | Course list, create, edit, cover, price, archive, delete |
| 2 Content tree and lecture content | Tree, YouTube, notes, materials, free flags |
| 3 Close and reopen | Master Admin only |
| 4 Library | Book list, create, edit, cover, availability, price, delete |
| 5 Payments and fulfillment | Verify, offline admission, order status, cash collected |
| 6 Course management screens | Details with category, view shows the tree, content, files |
| 7 Library management screens | Book view, details with one category, cover, price, delete |
| Browser exit | Shahriar, browser against the running API |
