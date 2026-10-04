# mobile-catalog tests

Required cases for Phase 2 Stage 7. Widget tests are written during implementation. Mock the HTTP client. Do not boot `api`. Do not add a GitHub workflow.

API behavior stays in the `api` `*IT` classes. Do not re-implement those cases here.

## Who runs what

| Check | Who | Notes |
|---|---|---|
| Widget cases (sections below) | **Agent** | `flutter test`; also `dart analyze` / `flutter analyze` when Dart changes |
| Device exit (end of file) | **Shahriar** | Physical Android phone, live API; not the agent |

Slices 1 through 5 are done when their widget tests pass. Slices 6 through 8 are the follow-on in [mobile-catalog.md](../plans/mobile-catalog.md). Slices 9 through 11 correct entitlement, sibling order, and PDF handoff. Slices 12 through 14 are the course-screen UI pass. The phone exit is manual QA for Shahriar only, once, after slice 14. The agent does not install a system image, create an AVD, launch an emulator, or attempt device testing.

## Home

- The signed-in home shows the name from `GET /me` and the Courses and Library tabs.
- The home is absent when signed out.
- Logout still clears the session.

## Browse and search

- The Courses tab requests `GET /catalog/courses` and sends the search text as `q`.
- The Library tab does the same for `GET /catalog/books`.
- A Bangla query is sent as typed.
- An empty list renders the empty catalog string in the active locale.
- Every new user-facing string exists in both the English and Bangla catalogs. First launch is English.

## Course detail and content

- Detail renders the tree titles from the catalog payload and does not expect `video_ref` there.
- Opening a free lecture requests the lecture route and shows a player for `video_ref`.
- A paid lecture that returns `ENROLLMENT_REQUIRED` shows the locked state and does not show a video id.
- A full-free lecture opens without a purchase control.
- An allowed material requests `GET /files/{id}`. The widget does not build a public storage URL.

## Purchase

- Purchase sends `{ method, transactionRef }` and adds `deliveryMode` only for a hybrid course.
- The payment number from the response is visible, and the history row shows pending.
- `ALREADY_ENROLLED` and `COURSE_IS_FREE` render translated errors.
- The purchase screen has no cancel action.

## Library order

- Delivery shows address and optional area. Pickup hides them.
- Choosing cash hides the online method and reference, and the control does not offer delivery together with cash.
- Submit sends phone, and address only for delivery.
- History shows the order status from `GET /me/orders`.
- The order screen has no cancel action.

## Cover and outline

- The course list and the course detail request `GET /files/{id}` for `coverFileId` and show the image. A course with no cover does not request a file.
- A section card contains the lecture whose `parentId` is that section. A nested section renders inside its parent. A lecture with a null `parentId` renders in the course-level lecture group, not inside a section.
- The outline is not one list ordered by `position` across different parents.
- Slice 10: under one section, a nested section with a lower `position` than a sibling lecture renders before that lecture. A root lecture keeps its root `position` among root sections and is not inside a section card.

## Placed in

- A course detail with `categoryPath` shows those titles as chips, and the language control switches English and Bangla without showing both at once.
- An empty or absent path renders no placement block.
- The course list does not request `GET /course-categories`.

## Files

- A course file row uses `originalFileName`. The cover id is not rendered as a course file.
- Choosing an allowed image shows it. The widget does not build a public storage URL.
- Slice 9: when `GET /me/courses` lists this course, a paid lecture and a course file with `free: false` are openable, and the file request is sent. When it does not, those stay locked. A free lecture stays openable without that list. A failed `GET /me/courses` does not mark the student enrolled.
- Slice 11: choosing an allowed PDF hands the bytes to the external opener and does not build an in-app PDF page or a public storage URL. The leaf name keeps Bangla characters. The file id is the directory, so two Bangla names do not share one path.
- `ENROLLMENT_REQUIRED` and `MATERIAL_FORBIDDEN` on a course file leave it locked and do not show bytes.
- A locked lecture does not render lecture file names.

## Course screen UI

- A course with no `coverFileId` does not build a cover widget on the list or the detail.
- While a cover download is outstanding, the cover slot shows the loading key. After the bytes arrive, the image key is present.
- Cover bytes longer than 2 MB do not build an image. The too-large catalog string is shown. A second request for the same accepted cover id is not sent.
- With the app locale Bangla, the placement line starts in Bangla. The titles appear in path order, separated by ` › `, and not as a wrap of chips.
- A nested section is not a `Card`. Its parent section is. A course-file row shows a progress indicator while its download is outstanding.

## Device exit (manual QA — Shahriar only)

Shahriar runs these on his physical Android phone with `--dart-define=API_BASE_URL=http://<lan-ip>:8080`. The agent does not run this checklist. Record pass or fail in `mobile/.cursor/progress/mobile-catalog/phone-exit.md`.

- Sign in as a student. The home header shows that student's name. Switch between Courses and Library.
- Search for a course by its English title and again by its Bangla title. Both find it. Do the same for a book.
- Open a free lecture on a freemium course with no purchase, and confirm the video plays. Open a paid lecture and confirm it stays locked.
- Submit a purchase with a transaction reference. The history shows pending. After a Master Admin verifies it, the paid lecture opens.
- Place a delivery book order with a full address and see it in order history. There is no cancel control on the purchase or the order.
- Open a course that has a cover and confirm the cover is visible. Open a course with no cover and confirm the detail does not keep an empty cover band. Its outline shows a lecture inside its section, a nested section indented under that section, and a lecture that sits on the course rather than in a section.
- Confirm the placement line names only that course's categories, in order, and the language control shows either the English titles or the Bangla titles. With the app in Bangla, the line starts in Bangla.
- Open an allowed image in the app. Open an allowed PDF in another application. Open a paid course file with no purchase and confirm it stays locked. After a Master Admin verifies the purchase, that paid file and a paid lecture show as open.
