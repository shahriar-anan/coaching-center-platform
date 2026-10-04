# web-catalog

Stage plan for Phase 2 admin management on the web. The binding spec is [phase-2-scope.md](../../../docs/phases/phase-2/phase-2-scope.md) Stage 8. This plan adds order and screen detail. It does not drop an exit check.

Required cases are in [web-catalog.md](../tests/web-catalog.md). The glanceable list is [phase-2-status.md](../../../docs/phases/phase-2/phase-2-status.md).

Start only after the `mobile-catalog` phone exit. Do not start a later slice until the previous slice's progress note exists.

**Agent:** run `npm run lint`, `npm run build`, `npm run check:openapi`, and mocked component tests when written (do not boot `api`). **Shahriar:** manual QA in the browser for the stage exit only. The agent must not open a browser or use browser automation.

The web app is for `MASTER_ADMIN` and `SYSTEM_ADMIN`. Do not add student browse, search, purchase, lecture playback, file download for students, or book ordering. Those stay on `mobile`. Do not add exams, batches, attendance, notices, push, a payment gateway, refunds, cancellation, or Cloudinary.

Reuse the Phase 1 shell from `web-admin`: memory-only access token, cookie refresh, `X-Client-Type: web`, English and Bangla catalogs, and role-aware navigation. A `STUDENT` session still never enters the shell.

If a field or status code is wrong, fix `api` and its tests before changing the client to compensate. The one API addition in this stage is `GET /api/v1/book-orders`, because cash orders have no payment and cannot be listed today.

When a slice's checks finish, write `web/.cursor/progress/web-catalog/slice-<number>-<name>.md` before the next slice.

## Who sees which control

Hide a control the role cannot call. Do not render it disabled.

| Action | Master Admin | System Admin |
|---|---|---|
| List and open courses and books | yes | yes |
| Edit titles, description, delivery, tree, YouTube, notes, materials, free flags, cover, publish | yes | yes |
| Replace a lecture file or a course file; clear a YouTube URL | yes | yes |
| Set or change `price` | yes | no |
| Archive or delete a course; delete a book or a book category; delete a section, lecture, file, or course category | yes | no |
| Close or reopen a course | yes | no |
| List pending payments and verify them | yes | no |
| Offline admission | yes | yes |
| Move a book order to ready, delivered, or collected when it is not a cash collection | yes | yes |
| Mark a cash order collected | yes | no |

Access type is chosen at create and is not editable after that. A free lecture or free course-level material has no control that sets `free` back to false.

## Course create and update

Create and update use two authoring screens, then the course view. A category groups courses. A section is a folder inside one course. Do not put lecture content on the details form. Do not use a browser prompt for a name, a file, or a move.

A course header stays on these screens: both titles, draft / published / archived, and whether it is open or closed, plus links for View, Details, and Content. Publish stays in that header. Archive, delete, close, and reopen stay role-gated actions in the header.

1. **Course details.** A form, not a raw field list. English title, Bangla title, content language, description, delivery mode, access type, price when this role may set it, the cover, and the course's category. The category control chooses a main category or adds one with both titles. Each chosen category then offers another subcategory, an added subcategory with both titles, or leaving the course on that category. The chain can be as deep as the admin needs: the course on the main category, under one subcategory, or under subcategory after subcategory. Done shows only this course and the categories above it, so other courses in the catalog are not listed. Save creates the draft, creates any new categories from the top of the chain downward, places the course, uploads the cover when one was chosen, and opens the course view. On an existing course, access type is read-only text. Master Admin can delete a category from this form. A category that still has a subcategory or a course shows the API error and stays. This form does not create a section or a lecture.
2. **Course view.** Shown after details are saved, from the course list, and from Save and view on Content. Tabs are Overview, Category, Curriculum, and Materials. Overview has the cover, description, and facts. Category shows only “Placed in” and the category path, such as Main Category / Sub Category-1. Curriculum lists sections and lectures; choosing a lecture shows its details. Materials lists course files and each lecture's files. Other courses are not listed.
3. **Content.** The outline is one card per section. The card title is the section number and name. Lectures inside it are a list: number, title, and short status such as Video, a file count, and Free. The lecture being edited is highlighted. Add lecture is a button at the bottom of that card. Add section is a button under the last card. Reorder and delete sit in an Actions menu on the section and on the lecture. Move up and move down are disabled at the ends. Tapping a saved lecture opens its details beside the outline. Edit opens the form for that lecture's title, notes, YouTube URL, and files. Save returns to the details. Saving an empty URL clears the video. Clear also sits in the video group. Each file row shows the name, Replace, and, for Master Admin, Delete. One Add file control adds a file to that lecture. A Section menu can place the lecture in another section or in no section, and saving sends that as `parentId`. Course files are their own card under the sections, always visible, with the same file list and one Add course file control. A file is not moved after upload, and a section row has no file input. A lecture that already has no section stays in a card titled Lectures on the course. Master Admin can delete a lecture; its files go with it. Master Admin can delete a section only when that section and its subsections have no lecture. If a lecture is still inside, the section stays and the screen shows the error.

Update opens Details for the record and the category, Content for lectures, and View to read the saved course. The admin can return to an earlier screen without losing the later ones.

## Contract the screens call

Base path `/api/v1`. Same session rules as `web-admin`. OpenAPI is the running app's `/v3/api-docs`. Regenerate the client types for the operations below.

| Call | Who | Notes |
|---|---|---|
| `GET /courses`, `GET /courses/{id}` | both | Page envelope on the list |
| `POST /courses`, `PATCH /courses/{id}` | both | System Admin must not send `price` |
| `POST /courses/{id}/archive`, `DELETE /courses/{id}` | Master Admin | |
| `POST /courses/{id}/cover`, `POST /courses/{id}/materials` | both | Multipart. A material names a lecture or the course |
| `PUT /courses/{id}/materials/{materialId}` | both | Multipart replace. Same id and lecture link |
| `PATCH /courses/{id}/materials/{materialId}` | both | Free flag |
| `DELETE /courses/{id}/materials/{materialId}` | Master Admin | Not the cover |
| `POST/PATCH /courses/{id}/nodes`, `POST .../nodes/reorder`, `DELETE .../nodes/{nodeId}` | manage vs delete as the API defines | Node delete is Master Admin. A section that still has a lecture returns `409` |
| `POST /courses/{id}/close`, `POST /courses/{id}/reopen` | Master Admin | |
| `GET /books`, `GET /books/{id}` | both | |
| `POST /books`, `PATCH /books/{id}` | both | `available` and, for Master Admin, `price` |
| `POST /books/{id}/cover` | both | |
| `DELETE /books/{id}` | Master Admin | |
| `GET /payments?status=&payableType=` | Master Admin | `PENDING`, `SUCCESS`, or `FAILED`; course or book |
| `POST /payments/{id}/verify` | Master Admin | `{ result: SUCCESS \| FAILED }` |
| `POST /courses/{id}/admissions` | both | `studentId`, and `deliveryMode` when the course is hybrid |
| `GET /book-orders` | both | Added in slice 5. Page envelope of admin order rows |
| `PATCH /book-orders/{id}` | both, except cash `COLLECTED` | `{ status }` |
| `GET/POST/PATCH /course-categories`, `DELETE /course-categories/{id}` | read, manage, or delete as the API defines | Follow-on category slice. Not a content node |
| `PATCH /courses/{id}` with `categoryId` | both | Places the course under a category |

`GET /files/{id}` is how an admin previews a cover or material they uploaded. Do not build a public file URL.

## Slice 1 — Course record

Courses nav item for both roles.

- Paginated list of admin courses, including draft, published, archived, and closed. Show both titles, access type, delivery mode, status, and whether it is closed.
- The detail fields are the course record and its category: English title, Bangla title, content language (`BN` or `EN`), description, delivery mode (`ONLINE`, `OFFLINE`, `HYBRID`), access type (`FREEMIUM` or `FULL_FREE`), cover, and a category chain. A course can sit on the main category or under any number of nested subcategories. Done previews that tree before save. No price field for System Admin. Master Admin may set price on a freemium course. A full-free course has no price field. After save, access type is read-only, and the course view shows the category tree.
- Publish is a status change the API already accepts on patch. Do not invent a second publish route.
- Archive and delete appear for Master Admin only.

## Slice 2 — Content tree and lecture content

These actions are the content screen. They are inside one course. A category is not a section.

- Add a section or a lecture. A lecture can sit at the course root or under a section. A section can contain sections or lectures. A lecture cannot contain children.
- Reorder among siblings.
- The lecture form stores whatever the API accepts (`youtube.com/watch`, `youtu.be`, `youtube.com/embed`).
- On a freemium course, a lecture can be marked free. Once free, that control is gone.
- A course-level material on a freemium course can be marked free. Once free, that control is gone.
- Delete a node for Master Admin only. A section that still has a lecture, or a subsection that has a lecture, has no successful delete. Deleting a lecture removes the files attached to that lecture.
- A lecture file and a course-level file can be replaced or deleted. Delete is Master Admin only. A file is not moved from one lecture to another, or between a lecture and the course, after upload. A file is not attached to a section.
- The lecture form can clear the YouTube URL.
- A full-free course does not show free-flag controls.

## Slice 3 — Close and reopen

On a published course, Master Admin sees close when it is open, and reopen when it is closed. System Admin sees the closed state as text and no button.

## Slice 4 — Library

Library nav item for both roles.

- Paginated book list: both titles, author, price, available.
- Create and edit: English title, Bangla title, author, description, available. Price only for Master Admin.
- Cover upload.
- Delete for Master Admin only.

## Slice 5 — Payments and fulfillment

- Payments page for Master Admin only. Two tabs, course payments and book payments. Each tab has Pending, Paid, and Failed. There is no cancelled filter. A card opens a dialog with the student, the course or book, the amount, method, transaction reference, and status. Confirm is in that dialog, and only while the payment is pending. Verify still sends `SUCCESS` or `FAILED`. System Admin has no payments nav item and cannot list or confirm payments.
- Offline admission on a published open freemium course: pick a student and, when delivery is hybrid, online or offline. Both roles. A full-free course has no admission action.
- Book orders page for both roles, backed by `GET /api/v1/book-orders`. Show status, fulfillment, payment method, phone, and address when present.
- Status actions follow the API: online `PAID` → `READY` → `DELIVERED` or `COLLECTED` matching fulfillment; cash `PLACED` → `READY` → `COLLECTED`. `COLLECTED` on a cash order is Master Admin only and is absent for System Admin.
- Add `GET /api/v1/book-orders` in `api` with an integration test before the screen depends on it. Both admin roles. Students receive `403`. Soft-deleted orders are absent. Do not add cancel.

## Slice 6 — Course management screens

Replace the single course editor with Details, the course view, and Content. Start only after the API course-category follow-on exists. Build the screens an admin uses to manage a course. Do not add a control whose only purpose is to satisfy a test.

- Details saves the draft with `POST /courses`, creates each new category in the chain with `POST /course-categories` (a main category omits `parentId`; each deeper one sends the parent just created or chosen), places the course with `PATCH /courses/{id}` and `categoryId` when a category was chosen, then `POST /courses/{id}/cover` when a cover was chosen, and opens the course view. Done, before that save, shows the tree and where this course will sit. Update patches the course, including `categoryId` when the place changed, and can replace the cover. The cover already on the course is visible. Both titles are required for a new category. This form does not send `nodeType`.
- The course view reads `GET /course-categories` and shows only this course and the categories above it. Other courses are not listed. An empty tree shows empty text. A failed category delete on Details leaves that category in place and shows the error.
- Content is a card per section. Add lecture on that card sends `parentId`. The lecture row stays in the card. Tapping a saved lecture opens its details. Edit opens the form. Save returns to the details and sends title, notes, and `videoUrl` (null when the field is empty). Clear sends `videoUrl` null. A lecture file sends `lectureNodeId`. Add course file uploads a file with no `lectureNodeId`. Replace sends `PUT` with the file. Delete of a file or a node is absent for System Admin. A section card has no file input. The Section menu saves `parentId`, including null for no section.
- A section delete that the API rejects with `SECTION_NOT_EMPTY` leaves the outline unchanged and shows the error. A lecture delete refreshes the outline and that lecture's files are gone.
- Move lecture sends the new `parentId`, including null for the course itself. There is no file-move control.
- Every new label exists in the English and Bangla catalogs. Empty category tree, empty outline, and a failed save each have visible text.

## Slice 7 — Library management screens

Start only after the API book-category follow-on exists. Follow the course shell: a top bar with the Library link, View book, and Details, and Delete on that bar for Master Admin only. There is no Content step. A book category is one flat label. One book has at most one category. Do not build a category tree, and do not send `parentId`.

Routes: `/books` list, `/books/new` create, `/books/{id}` view, `/books/{id}/details` editor.

- The list shows both titles, the category's English name, and Available or Unavailable. Each row has a View book control. The price column is present for Master Admin and absent for System Admin.
- Create uses the same two-column details form. It sends both titles, author, description, and price only for Master Admin, places a category when one is chosen, uploads a cover when one is chosen, then opens the book view.
- On the view step the hero owns the cover and titles. Details shows a compact cover and titles under the toolbar. The view shows the cover, both titles, author, availability, the description, and one "Placed in" chip. English title and Bangla title buttons switch that one category name. It does not list other books. A book with no category shows that it is not placed. Price is on this view for Master Admin only.
- Details is two columns. The left side is titles, author, description, available, price for Master Admin, and save. Save opens the view. The right side is one category choice and the cover. The choice lists existing categories or adds one. Adding a category is grouped as Add category and sends `POST /book-categories` with both titles and no `parentId`, then places the book with `PATCH` and `categoryId` only when the choice changed. Clearing the choice sends `categoryId` null. The cover control is the same upload button as a course cover and calls `POST /books/{id}/cover`. An existing cover is shown.
- Delete of a book is on the shell, Master Admin only. Delete of a category is on Details, Master Admin only. A category delete that the API rejects with `CATEGORY_NOT_EMPTY` leaves the category in place and shows that the category still has a book.
- Every new label exists in the English and Bangla catalogs.

## Stage exit (manual QA)

Shahriar runs the browser checklist in the test file against the running API at `http://localhost:3000`. The agent does not run this checklist. Record pass or fail in `web/.cursor/progress/web-catalog/browser-exit.md`. The stage is not done until that note says pass.
