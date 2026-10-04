# mobile-catalog

Stage plan for the student home, catalog, purchase, and content screens. The binding spec is [phase-2-scope.md](../../../docs/phases/phase-2/phase-2-scope.md) Stage 7, using the API from Stages 3 through 6. This plan adds order only. It does not drop an exit check.

Required cases are in [mobile-catalog.md](../tests/mobile-catalog.md).

Start only after `api-content-access` exits. Keep the session, secure tokens, catalogs, and Bangla font from Phase 1. `HomeScreen` in `mobile/lib/screens/home_screen.dart` is the place the Courses and Library tabs replace the placeholder body. The header still shows the student name from `GET /me`.

Do not build exams, attendance, notices, push, web screens, or an admin UI. Do not add a mobile CI workflow.

**Agent:** run `flutter test` (mocked HTTP; do not boot `api`) and `dart analyze` / `flutter analyze` on mobile changes. **Shahriar:** manual QA on a physical Android phone for the stage exit only. The agent must not install an Android system image, create an AVD, launch an emulator, or test on a device.

If a field, status code, or error code is wrong, fix `api` and its tests before changing the client to compensate.

UI chrome strings go in both the English and Bangla catalogs. First launch stays English. Course and book body text is shown as the API sends it. Titles are searched as the student types them, in either language.

When a slice's checks finish, write `mobile/.cursor/progress/mobile-catalog/slice-<number>-<name>.md` before the next slice.

## Contract

Base path `/api/v1`. Authenticated calls send `Authorization: Bearer`. Login, refresh, and logout keep `X-Client-Type: mobile`.

| Call | Use |
|---|---|
| `GET /catalog/courses?q=` | Course search and list |
| `GET /catalog/courses/{id}` | Detail and tree titles, plus each lecture's `free` flag |
| `GET /catalog/courses/{id}/lectures/{lectureId}` | Video id and notes when allowed |
| `GET /catalog/courses/{id}/materials/{materialId}` | A course-level material when allowed |
| `GET /files/{id}` | Cover or an allowed material |
| `POST /courses/{id}/purchases` | `method`, `transactionRef`, and `deliveryMode` when the course is hybrid |
| `GET /me/purchases` | The caller's course payments |
| `GET /me/courses` | Enrollments that still grant access |
| `GET /catalog/books?q=` | Book search and list |
| `GET /catalog/books/{id}` | Book detail |
| `POST /books/{id}/orders` | Fulfillment, payment, phone, and address fields |
| `GET /me/orders` | The caller's book orders |

There is no cancel call.

## Slice 1 — Home tabs

- Header shows the student name.
- Two tabs, Courses and Library. The signed-out tree still has no home.
- Logout stays available.

## Slice 2 — Browse and search

- Each tab lists the catalog and has a search field. The field submits `q` unchanged.
- Empty results and a load failure use catalog strings.
- A closed course never appears, because the API does not return it. Do not add a client-side archive.

## Slice 3 — Course detail and content

- Detail shows both titles, the description, price, delivery mode, and the tree.
- A free lecture opens its YouTube video and notes for any signed-in student. Play the video inside the app from `video_ref`.
- A paid lecture shows a locked state until the API returns the content. `ENROLLMENT_REQUIRED` and `MATERIAL_FORBIDDEN` stay locked. They do not reveal the video id.
- A full-free course opens every lecture.
- Materials that the API allows can be opened from the lecture or from the course. The client does not guess a file URL.

## Slice 4 — Purchase and history

- A freemium course offers purchase: bKash or Nagad, the transaction reference, and delivery mode only when the course is hybrid.
- The screen shows the payment number from the purchase response and then the pending row from `GET /me/purchases`.
- `ALREADY_ENROLLED` and `COURSE_IS_FREE` render translated messages. A full-free course has no purchase button.
- There is no cancel button.

## Slice 5 — Library order

- Book detail shows cover, both titles, author, description, and price.
- Order form: pickup or delivery, online or cash, phone, and address plus optional area when delivery is chosen. Cash cannot be combined with delivery; the form does not send that pair.
- After submit, the order appears in the caller's history with its status.
- There is no cancel button.

## Stage exit (manual QA)

Shahriar runs this on his physical Android phone against the running API, once, after slice 14. Phone command: `flutter run --dart-define=API_BASE_URL=http://<lan-ip>:8080`. The agent hands over that command and does not run the exit checklist.

The checklist is in [mobile-catalog.md](../tests/mobile-catalog.md) under **Device exit**. Shahriar's pass is recorded in `mobile/.cursor/progress/mobile-catalog/phone-exit.md` and in [phase-2-status.md](../../../docs/phases/phase-2/phase-2-status.md).

Slices 1–5 stay as written. The follow-on does not replace them and does not drop a device check.

## Follow-on — Course screen matches the catalog

Slices 1–5 are implemented. The course screen still ignores the cover the catalog already returns, draws every node in one position-sorted list, and does not open a file. Categories and course files were added on the API after those slices. This follow-on is the student screen for that model. It is not an admin app and not a new stage.

The app stays student-only. Do not call `GET /course-categories` or `GET /book-categories`. Do not add a category browser to the course list or the library. Book list and book detail stay title, author, cover, price, and the order flow from slice 5. A book category is admin-only.

Start slice 6 now. Start slices 7 and 8 only after `CatalogCourseReadIT` passes. See [api-catalog-course-read.md](../../../api/.cursor/plans/api-catalog-course-read.md).

### Slice 6 — Cover and outline

Uses the catalog payload as it is today. `coverFileId` is already on the course list and the course detail. `contentTree` nodes already have `parentId` and `position`.

- The course list and the course detail show the cover through `GET /files/{id}`, the same way a book cover is shown. A missing cover leaves the space empty.
- The outline is a tree, not one list sorted by `position` across the whole course. `position` orders siblings that share a parent.
- Each root section is a card. A nested section renders inside its parent section. A lecture is not a container.
- Slice 10 is the sibling order. Do not draw every lecture in a card and then every nested section. `position` is one sequence for the children of one parent.
- A lecture whose `parentId` is null is a root sibling. It is never drawn inside a section. It is not collected into a trailing group that ignores root `position`.
- A section row does not open the lecture screen. A lecture row does. Free and full-free lectures stay openable. A paid lecture stays locked until the lecture route allows it.
- A category is not drawn as a section.

### Slice 7 — Placed in

Uses `categoryPath` from the catalog course detail.

- When the path is present, the course detail shows it as chips from the main category down to this course's category. One control switches the English titles and the Bangla titles. Only one language is visible.
- The path does not list another course or a sibling category.
- When the path is empty or absent, the placement block is omitted.
- The Courses tab stays a title search. It is not grouped by category.

### Slice 8 — Files

Uses `courseMaterials` on the course detail and `materials` on an allowed lecture. Bytes still come from `GET /files/{id}` with the bearer token. The client does not build a public file URL.

- Course files are listed on the course screen by `originalFileName`. The cover is not in that list.
- An allowed image is shown in the app. Slice 11 replaces any in-app PDF screen: a PDF is saved and handed to another application.
- A paid course file that returns `ENROLLMENT_REQUIRED` or `MATERIAL_FORBIDDEN` stays locked and does not show bytes.
- An allowed lecture lists its files by name and opens them the same way. A locked lecture does not show those names.

Widget cases for slices 6–8 are in [mobile-catalog.md](../tests/mobile-catalog.md). There is no separate phone pass for these slices. They are part of the one device exit above.

## Corrections — access, sibling order, and PDF handoff

Slices 6–8 are implemented. These three slices correct them. They do not change the API, the purchase flow, or the content gate. The server stays the authority for bytes. Do not add a PDF widget package.

Start slice 9 now. Then 10, then 11. The UI pass is slices 12–14. The phone exit runs after slice 14.

### Slice 9 — Freemium entitlement

`free` on a lecture or a course file means “any signed-in student may open this while the course is open.” It does not mean “a paid item is locked for a student who already has access.”

The API already encodes that split.

- `FULL_FREE`: every lecture and every material is open. No enrollment.
- Freemium lecture with `free: true`, and the files on that lecture: open with no enrollment.
- Freemium course file with `free: true`: open with no enrollment. A course file’s flag is its own. It does not follow a lecture.
- Anything else on a freemium course: open only when `GET /me/courses` lists this course. That list is an active enrollment for the current opening. A pending purchase is absent. An enrollment from the previous opening is absent.
- `GET /files/{id}` still decides. `ENROLLMENT_REQUIRED` and `MATERIAL_FORBIDDEN` show the locked message and no bytes. A client hint must not skip that call for an item the student is allowed to open, and must not draw bytes from a refusal.

On each open of the course screen, load `GET /catalog/courses/{id}` and `GET /me/courses` together. Match `courseId` on that list. Do not cache the match across visits. If `GET /me/courses` fails, keep the free and full-free hints and still let the lecture route and the file route decide. Do not mark the student enrolled because the call failed.

The outline affordance is open when the course is full-free, or the lecture is free, or this course is in `GET /me/courses`. Otherwise it is locked. The row still opens the lecture screen. Paid titles stay visible.

A course file uses the same rule with that file’s `free` flag. Lecture files do not get a second check: their names appear only after the lecture route succeeds, which already applies the lecture rule.

### Slice 10 — Sibling order

`content_nodes.position` orders every child of one `parent_id`, sections and lectures together. Admin reorder writes `0..n-1` across that whole sibling set. A lecture cannot be a parent. A section can contain sections and lectures at any depth. The catalog sends that flat list with `parentId` and `position`. Materials are not nodes: a course file has `lecture_node_id` null, a lecture file points at that lecture, and neither has a `position`. Leave file lists in the API’s order.

For each parent, including the course root (`parentId` null), take the nodes with that parent, sort by `position` ascending, and break ties by `id`. Render that sequence once. A section is a container whose body is the same rule. A lecture is a row. Do not render all lectures and then all nested sections.

A root lecture stays outside every section card, in its root position. Do not append those lectures in a group after the root sections. A course whose root is only lectures still lists them in `position` order.

### Slice 11 — PDF handoff and file names

An allowed image still opens in the app. An allowed PDF does not. Save the bytes from `GET /files/{id}` and hand the file to another application (`ACTION_VIEW` / `open_filex`). Do not keep a PDF page in the app. If no application can open it, show a catalog string in both languages. Do not show the bytes in a widget. The Android manifest must query `ACTION_VIEW` for `application/pdf` so Android 11 and later can see a viewer. The widget test stubs the handoff and asserts there is no in-app PDF page and no public file URL.

The name on screen stays `originalFileName`, including Bangla. The temp path must not strip letters to ASCII. Put each download in its own directory named with the file id, and use the original name as the leaf after removing only path characters (`\ / : * ? " < > |`), ASCII controls, and `..`. If nothing remains, use `file` plus the extension from the content type. Two different files then cannot overwrite each other, and the other application still receives the Bangla name.

## UI — course screen density

Slices 1–11 are implemented. This pass changes only how the course list and the course screen draw what those slices already load. It does not change entitlement, sibling order, purchase, PDF handoff, or the API. There is no thumbnail route. Do not add an image-cache package. Book list and book detail stay as slice 5 left them.

Start slice 12 only after slice 11. Then 13, then 14. Chrome strings go in both catalogs.

### Slice 12 — Cover presence, wait, and size

`CatalogCover` today always returns a sized empty box when `coverFileId` is null, while the bytes are in flight, and when the download fails. The course list wraps that box in a 56×56 leading slot on every row. A missing cover and a failed cover look the same, and a list of courses decodes every cover into memory with no reuse. The file route allows up to 100 MB. That is a cover download, not a list thumbnail.

- When `coverFileId` is null or empty, do not build `CatalogCover` on the course list or the course detail. The list tile has no leading slot. The detail body starts at the title block. Do not reserve 200px or 56px.
- When an id is present, show a progress indicator with key `course-cover-loading` on the detail and `course-list-cover-loading-{id}` on the list until the bytes arrive or the call fails.
- A failed download shows the catalog string `catalog.coverFailed` in that same slot. It does not show an image.
- After a successful download, if the byte length is above 2 MB, drop the bytes and show `catalog.coverTooLarge`. Do not pass them to `Image.memory`. The 2 MB cap is only for on-screen decode. It does not change what `GET /files/{id}` returns, and it does not apply to a PDF or a course file the student opens on purpose.
- Keep one in-memory map from file id to the bytes that were accepted for display, for the process lifetime. The course list and the course detail read that map before calling `GET /files/{id}`. A second row or a rebuilt list for the same id does not download again. Do not write covers to disk. Do not cache a failure or an oversized file as a successful image.

### Slice 13 — Placement reads as a path

`categoryPath` is already ordered from the main category down to this course’s category. The screen draws those titles as equal chips in a wrap, so the order is not visible, and the language control always starts in English.

- Keep the one control that switches English and Bangla. Only one language is visible.
- The control’s first value follows the app locale: Bangla when `locale.code` is `bn`, otherwise English. Changing the app locale while the screen is open updates that value.
- Replace the chip wrap with one line, in path order, titles separated by ` › `. The line may wrap. It does not become a tappable category browser.
- An empty or absent path still omits the block. The Courses tab stays a title search.

### Slice 14 — Nested sections stay readable

A root section stays a card. `CourseOutline` currently wraps every nested section in another card, so a deep tree is a stack of cards inside cards. Sibling order from slice 10 does not change.

- A section whose parent is another section is a title plus its children, indented under that parent. It is not a `Card`.
- A lecture stays a row. A root lecture stays outside every card, in its root position.
- While `GET /files/{id}` is in flight for a course file or a lecture file, that row shows a progress indicator and does not show bytes. The indicator clears when the image opens, the PDF handoff returns, or the error text is set.
