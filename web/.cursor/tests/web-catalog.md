# web-catalog tests

Required cases for Phase 2 Stage 8. Component tests, if written during implementation, use the same runner as `web-admin` and mock `fetch`. Do not boot `api` for those tests. Do not add a GitHub workflow.

## Who runs what

| Check | Who | Notes |
|---|---|---|
| Sections below (component / contract) | **Agent** | `npm run lint`, `npm run build`, `npm run check:openapi`, and mocked tests when present |
| Browser exit (end of file) | **Shahriar** | Browser against live API; not the agent |

Slices 1 through 5 are done when their automated cases pass. Slices 6 and 7 are done when their automated cases pass. The browser exit is manual QA for Shahriar only, once, after slice 7. The agent does not open a browser or use browser automation.

API behavior stays in the `api` `*IT` classes, plus one new list test for `GET /api/v1/book-orders`. Do not re-implement those cases here.

## Course record

- The course list requests `GET /courses` with paging and shows a draft and a closed course.
- Create sends both titles, content language, description, delivery mode, and access type. It does not send a second description.
- System Admin create and edit JSON omit `price`. Master Admin may send `price` for a freemium course. A full-free form has no price input.
- After create, access type is shown and is not an editable control.
- Cover upload calls `POST /courses/{id}/cover`.
- Archive and delete are in the DOM for Master Admin and absent for System Admin.

## Content tree and lecture content

- Adding a lecture sends `nodeType` `LECTURE`. Adding a section sends `SECTION`. A lecture may omit `parentId`.
- Reorder calls `POST /courses/{id}/nodes/reorder`.
- Lecture save sends a YouTube URL and notes.
- A freemium lecture that is not free can be marked free. A lecture that is already free has no control that sends `free: false`.
- A course-level material upload does not set a lecture id. Its free control follows the same one-way rule.
- A full-free course does not render free-flag controls.
- Node delete is present for Master Admin and absent for System Admin.

## Course management screens

Slice 6. Create and update use Details, the course view, and Content. A category is not a section. Tests mock `fetch`. Assert the request and what the admin sees.

- The header shows View, Details, and Content. Details includes a category chain: a main category, then as many nested subcategories as the admin adds. The course can sit on the main category or under the last subcategory. Done shows only this course and the categories above it. Other courses are not listed. After save, create has called `POST /courses`, `PATCH` with `categoryId` when a category was chosen, and, when a cover file was chosen, `POST /courses/{id}/cover`, and the course view is what is on screen. An existing cover is shown. Access type is not an editable control.
- The course view shows this course under its own categories only. Other courses in the catalog are absent. Adding a subcategory from Details sends `parentId` and both titles. A deeper subcategory sends the previous new category as `parentId`. Placing the course sends `categoryId`. Details does not send `nodeType`. An empty tree shows empty text. A failed category delete stays on the tree and shows the error.
- Content shows one card per section. Add lecture on that card sends `parentId`, and the new lecture stays in the card. Tapping a saved lecture opens its details. Edit opens the form, and Save returns to the details. Save and view stores an open lecture, then opens the course view. A lecture file includes `lectureNodeId`. Add course file does not. No section card has a file input. Saving an empty YouTube URL sends `videoUrl` null. The Section menu saves `parentId`, including null. A lecture that already has no section remains under Lectures on the course. Course files are visible without an extra toggle.
- Lecture save sends title and notes. Clear sends `videoUrl` null. Replace sends `PUT` to that material. Delete of a file and of a node is in the DOM for Master Admin and absent for System Admin.
- Move lecture sends the selected section id, and a separate action sends `parentId` null.
- A section delete that returns `SECTION_NOT_EMPTY` leaves that section and its lecture on screen and shows the error.
- Update of an existing course opens Details, with the category chain already selected. Done shows that place in the tree. The course view shows the category tree on the Overview tab. Curriculum and Materials are the other tabs. Back from Content returns to Details, and Save and view opens the view with the course still placed.

## Close and reopen

- Master Admin on an open published course can submit close. On a closed course they can submit reopen.
- System Admin sees the closed or open state and has neither button.

## Library

- The book list requests `GET /books`.
- Create sends both titles, author, and description.
- System Admin edit JSON omits `price`. Master Admin may send `price`.
- Available can be turned on and off by both roles.
- Cover upload calls `POST /books/{id}/cover`.
- Delete is present for Master Admin and absent for System Admin.

## Library management

Slice 7. Start only after `BookCategoryIT` passes. Shahriar checks these on the library screens during the browser exit. There is no mocked book-management test. A book category is one label.

- The book list shows both titles, the category name, and Available or Unavailable, and each row can open the book. The price column is present for Master Admin and absent for System Admin.
- Create sends both titles, author, and description. Master Admin may send `price`. System Admin JSON omits `price`. The next screen is the book details screen.
- Details can add a category. That request is `POST /book-categories` with both titles and no `parentId`. Placing the book sends `PATCH` with `categoryId`. Clearing the category sends `categoryId` null. The cover control uploads with `POST /books/{id}/cover`.
- The book view is `/books/{id}` and details is `/books/{id}/details`. The bar shows View book and Details, and no Content step. The view shows that one category under Placed in. English title and Bangla title switch that name. It does not list another book. A book with no category shows that it is not placed. Price is visible for Master Admin and absent for System Admin. A rejected category delete says the category still has a book.
- Delete book and delete category are present for Master Admin and absent for System Admin. A failed category delete stays on the list and shows the error.

## Payments and fulfillment

- System Admin has no payments navigation.
- Master Admin pending list requests `GET /payments?status=PENDING`.
- Verify sends `{ result: "SUCCESS" }` or `{ result: "FAILED" }` and nothing else.
- Offline admission sends `studentId`. A hybrid course also sends `deliveryMode`. A full-free course has no admission control.
- The orders page requests `GET /book-orders`.
- A delivery order offers delivered, not collected. A pickup order offers collected, not delivered.
- Cash collected is present for Master Admin and absent for System Admin.
- No screen offers cancel.

## Catalogs

- Every new user-facing string exists in both the English and Bangla catalogs.

## Browser exit (manual QA — Shahriar only)

Shahriar runs these in a browser against the running API at `http://localhost:3000`, with the API CORS origin unchanged. The agent does not run this checklist. Record pass or fail in `web/.cursor/progress/web-catalog/browser-exit.md`.

- Sign in as Master Admin. Open Courses. Create a freemium online course: on Details, both titles, a price, a cover, a main category, and a subcategory. Press Done and confirm the tree shows where the course will sit. Add another subcategory under that one, press Done again, and confirm the course sits under the deeper subcategory. Save. The course view Overview tab shows that same tree. Open Curriculum and Materials and confirm the sections and files are on those tabs. On Content, add a section, then a lecture. Confirm the section card still shows that section and lecture. Add more lectures in that section. Open the lecture and add a YouTube URL, notes, and a file. Mark that lecture free. Add a course file from the Course files card. Replace the lecture file, then delete it. Clear the YouTube URL. Try to delete the section that still has the lecture and confirm the section and its lectures remain. Press Save and view and confirm the course view shows that content. Publish the course.
- On that course, move the section lecture onto the course, then move it back under the section. Confirm a file never has a control that sends it to a different lecture.
- Sign in as System Admin. Open the same course. Edit the description and a lecture note. Confirm price, archive, delete course, delete section, delete lecture, delete file, close, and reopen are not on the page. Confirm replace file and clear YouTube URL are available. Confirm Payments is not in the nav.
- Close that course, then reopen it.
- Create a full-free course. Confirm there is no price field and no purchase-style control.
- Sign in as System Admin. Edit a course description. Confirm price, archive, delete, close, and reopen are not on the page. Confirm Payments is not in the nav.
- As Master Admin, create a book with both titles, a price, and a cover. On details, add one category and place the book there. Confirm the view shows that category and no other book. Turn available on. As System Admin, edit the description and turn available off. Confirm delete, delete category, and price are absent.
- As Master Admin, open pending payments and mark one successful and, if a second is available, one failed.
- As either admin, admit a student to an open freemium course with no payment step.
- Open book orders. Advance a delivery order and a pickup order using only the status that matches fulfillment. As Master Admin, mark a cash pickup order collected. As System Admin, confirm that cash-collected action is absent.
- Confirm the panel never shows a student catalog, a buy button, or a cancel button.
