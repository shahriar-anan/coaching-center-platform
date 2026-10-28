# Stage 2: Packages and catalog (agent B)

**Covers** decisions A1, A3 (popular and favourites), A5/R1, E34, F35/F36 (API side), R2, R4 (membership side), R6, R7 and R21. Read them in [../NR-01-exam-segment.md](../NR-01-exam-segment.md).

Branch `nr01/b-packages`. Worktree `F:\cc-wt\nr01-b`. Test database `cc_test_nr01_b`. The files you own are in [README.md](README.md#ownership).

**First, in B1:** replace the `primaryModuleId` reads in your files (public listing and upcoming) per [contracts.md §8](contracts.md#8-several-packages-per-exam-how-each-primary-package-read-resolves). Use `LiveExamMemberships.attach` for adding. Do not write membership rows another way.

## B1: Package membership

- **`POST /live-exam-modules/{id}/exams/{examId}`** adds an existing exam.
  - The exam may be in any category; packages are not limited to one category.
  - It goes in at the next position. An active membership gives `EXAM_ALREADY_IN_PACKAGE`.
  - An archived package or exam gives the existing `*_ARCHIVED` errors.
  - Audit `LIVE_EXAM_PACKAGE_EXAM_ADD`.
- **`DELETE /live-exam-modules/{id}/exams/{examId}`** sets `removed_at`. Audit `LIVE_EXAM_PACKAGE_EXAM_REMOVE`.
  - Access is kept by R4, which C enforces through `LiveExamAccess`; your test asserts it end to end.
- **Membership changes are staged on a published package** (see B4). On a draft package they apply at once.
- **Order:** `PATCH /live-exam-modules/{id}/exams/order` with `{examIds:[…]}` sets the positions.
- **Package delete and archive** keep today's guards on purchases and grants. Deleting a package removes its memberships (`removed_at`); the exams stay in their categories. Archiving a package does not archive its exams.

**Tests:** new `PackageMembershipIT`:
- the same exam in two packages appears in both package reads;
- duplicate add is rejected;
- remove, then add again;
- a student who bought package P before X was removed still sits X (`take` succeeds); a student who bought P after the removal cannot;
- reorder.

## B2: Categories hold standalone exams

- A category read (staff and public) returns its packages, plus the exams whose home `category_id` is that category. Exams are listed **once**, each with `packages:[…]`.
- **`GET /public/live-exam-categories/{id}/exams`** (new) takes `type`, `mode`, `phase` and `popular`, with paging. It returns published exams in that category, under the existing visibility rules (hidden and archived branches excluded).
- Keep `GET /public/live-exam-modules/{id}/exams` working. It reads membership now, in membership position order.
- `moduleOnly` comes from `ExamPricing.view`. It is true when the exam has no own price, is not free, and is in at least one priced, published package.
- **`GET /public/live-exams?upcoming=true`** must include standalone exams. It joins through the category, not the package. Practice exams are not "upcoming", because they have no date.

**Tests:** update `LiveCatalogIT` and `LivePublicCatalogIT`. New cases:
- a standalone exam is listed under its category;
- an exam in two packages is listed once in the category read;
- a hidden branch hides the category's standalone exams too.

## B3: Staff exam list (for the later Exams page)

- **`GET /live-exams`** (staff) takes `packageId`, `categoryId`, `standalone=true` (no active membership), `type`, `mode`, `status`, `phase`, `q` (either-language title), `pending`, `popular`, `page`, `size` and `sort` (`liveAt`, `title`, `updatedAt`).
- Rows: `id`, both titles, `examType`, `mode`, `phase`, `status`, `liveAt`, `endAt`, `durationMinutes`, `pricing`, `popular`, `pendingChanges`, `packages[]`, `categoryId`, the content summary (`questionCount` or `hasPaper`) and `instructor`.
- An instructor gets only their assigned exams (`live_exams.instructor_id`).
- One query per page plus batched lookups. Never one query per row.
- With 10–15 packages and a few hundred exams, server paging is enough (F36).

**Tests:** new `StaffExamListIT`, covering each filter, instructor scoping, and an exam in two packages carrying both in `packages`.

## B4: Package fields and staged package edits

- `PATCH /live-exam-modules/{id}` accepts `discountPercent` and `discountEndsAt` (validated with `ExamPricing.validate`).
- **On a published package**, the PATCH and the membership changes write `live_exam_module_revisions.payload`: the full proposed package fields plus the proposed active membership list. Staff `GET` returns the working copy with `pendingChanges`.
- **`GET /live-exam-modules/{id}/pending-changes`** returns a `ChangeList` with `fields` and `packageExams`. `impact.notifiesStudents` is false; packages send no update notice.
- **`DELETE …/pending-changes`** discards. Audit `LIVE_EXAM_MODULE_REVISION_DISCARD`.
- **`POST /live-exam-modules/{id}/publish`** with `{revisionId}`, Master Admin only. It applies the fields and memberships, writes the change log and audit, and deletes the revision.
  - A first publish works as today.
  - [contracts.md §7](contracts.md#7-compatibility-rules-the-current-website-keeps-working) item 4 applies here too.

**Tests:** new `PackageRevisionIT`:
- student reads stay unchanged until publish;
- the change list is correct;
- discard;
- stale `revisionId` is rejected;
- a System Admin can stage but not publish;
- membership staged, then published, then grants access to package holders.

Tell the orchestrator when this is merged, so A can enable the package case of the pending badge.

## B5: Popular

- **`PUT /live-exams/{id}/popular`** and **`PUT /live-exam-modules/{id}/popular`** take `{popular}`. Master Admin only (`LIVE_EXAM_POPULAR`). Applied immediately, not staged. Audit `LIVE_EXAM_POPULAR_SET`.
- Public and student package and exam lists expose `popular` and accept `popular=true`.

**Tests:** new `PopularIT`, covering: only the Master Admin; the filter; a draft that is popular is still hidden from the public.

## B6: Favourites

- **`PUT/DELETE /me/favourites/exams/{id}`** and **`/me/favourites/packages/{id}`**: student only, idempotent.
  - Only published, visible items can be favourited, else `404`.
  - No purchase is needed (R7).
- **`GET /me/favourites`** returns `{exams, packages}` in the public row shape, with pricing. Items that have since become hidden are skipped, not deleted.
- Public and student exam and package rows **that you assemble** gain `favourite: true|false` when the caller is a student. `ExamView` (A's) does not carry it; the UI reads `/me/favourites` for the exam page.

**Tests:** new `FavouritesIT`, covering: add, idempotent add, remove, list; another student's favourites never show; a hidden item is skipped; staff get `403`.

## Not yours

- Exam fields and exam revisions (A).
- Access rules and orders (C).
- Instructor assignment routes, and the instructor's package list (D, through `LiveExamInstructorModules`).

## For web-impact

Write to `web-impact/b.md`:
- the sidebar Exams page fields and filters;
- package membership (add existing exam, remove, reorder);
- standalone exams on category pages;
- package staged changes;
- the popular toggles, section and badge;
- favourites.
