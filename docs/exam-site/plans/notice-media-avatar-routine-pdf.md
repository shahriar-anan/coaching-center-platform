# Plan: notice photos and PDF, student profile picture, package routine PDF

Status: planned, not started. Written 7 November 2026 for Shahriar and the agents who build it. The site is live and takes real money, so section 2 governs every workstream. The deployment plan is written separately, after the build, together with the staff payout encryption (V75). See section 9.

## 1. What is being built

| # | Feature | Who uses it | Where |
|---|---|---|---|
| F1 | A public notice can carry up to **6 photos** and **one PDF**, both optional | Admins attach; anyone reads | Staff: `/staff/notice-board`. Public: `/notice-board` and `/notice-board/[id]`, the "Notices" link in the landing menu |
| F2 | A student can set an optional **profile picture** | The student only | `/account` (the settings page) and the avatar in the student header menu. Staff never see it |
| F3 | A package can carry an optional **routine PDF**, which anyone can view and download | Staff upload; anyone reads | Staff: the package details screen. Public: the package page's **Routine** tab, above the existing table |

Decided by Shahriar on 7 November 2026:

- Up to 6 photos per notice.
- The routine PDF is public, like today's routine table.
- The avatar appears on the account page and in the header. It is private to the student.
- These features ship in one release together with V75. The deployment plan comes later.

Out of scope:

- The student inbox at `/notices` (announcements and personal notices) does not change. Neither do the package and exam announcements.
- The generated routine table stays as it is. The PDF is an addition, not a replacement.

## 2. Production safety rules (apply to every workstream)

Production on 7 November 2026, from read-only checks:

- Flyway is at **74**.
- There are 83 students, 3 instructors and 1 package. The package has a live 30% discount.
- There are **no `site_notices` rows**.
- There are 50 MB of files in `/var/lib/coaching-api/files`, with 90 GB of the 96 GB disk free.
- Nightly database and file backups run in `/var/backups/coaching/nightly`.
- nginx `client_max_body_size` is 105m. Spring multipart allows 100 MB.

1. **New tables only.** Migrations are `V76`, `V77` and `V78` (section 4). They use `CREATE TABLE` and `CREATE INDEX` only. They make no `ALTER`, `UPDATE` or `DELETE` on any existing table, and they backfill nothing. Every existing notice, student and package reads as "no photos, no PDF, no picture". `V75` (payout encryption) stays reserved and ships in the same release.
2. **Additive JSON only.** Existing response fields keep their names, types and meaning. The new fields are nullable or empty lists. The new website treats a missing field as "none", so it also works against an older API during the rollout.
3. **No change to existing rules.** Publishing a notice still needs the four texts and nothing else. Attachments never block a publish. Buying, access, pricing and the routine table are untouched.
4. **Files go where files already go.** They are stored through `LocalFileStorage` under the existing `FILE_STORAGE_DIR`, which the nightly file backup already covers. There is no new directory, environment variable, nginx change or service.
5. **Images are re-encoded.** Every photo and avatar goes through `ExamImageStorage` and `ExamImageProcessor`. That pipeline accepts JPEG, PNG and WebP, decodes, resizes to at most 1600 px and re-encodes. Re-encoding drops EXIF data, including the GPS location in phone photos. The F2 agent confirms in a test that no EXIF survives.
6. **PDFs are checked by content.** The first bytes must be `%PDF-`, as `LiveExamService.uploadQuestionPdf` checks. The file's name and declared type are not trusted. Downloads send `Content-Disposition` with an RFC 5987 file name (Bangla names survive) and `X-Content-Type-Options: nosniff`.
7. **Replace and remove are crash-safe.** Use `LocalFileStorage.commitReplacement`, as `ModuleCoverService` does. The old file is deleted only after the new row commits, and a rolled-back upload deletes its own file.
8. **Rollback stays possible.** The old jar ignores the three new tables. Flyway's default `ignoreMigrationPatterns` (`*:future`) lets the old jar start against a database at 78. The integration agent confirms that nothing in `application*.properties` overrides that default.
9. **Testing follows Shahriar's rule.** Agents write tests but do not run vitest, Maven or IT suites until Shahriar says so. Dev servers use `coaching_center_sample`. Do not clean it up or reload it without Shahriar's go. Never write to the production database. Production checks are read-only.

Size limits are constants in code, with no new properties:

| File | Accepted types | Upload limit | Stored limit (after processing) |
|---|---|---|---|
| Notice photo | JPEG, PNG, WebP | 8 MB | 2 MB (same as the package cover) |
| Notice PDF | PDF | 20 MB | 20 MB |
| Profile picture | JPEG, PNG, WebP | 8 MB | 1 MB |
| Routine PDF | PDF | 20 MB | 20 MB |

## 3. How the work is split

There are five agents. Agent 0 runs first and is small. Agents 1, 2 and 3 then run in parallel, one full-stack feature each, so that the API and the screen of a feature never disagree. Agent 4 integrates last.

```
Agent 0  Foundation (shared helpers, constants, shared web pieces)        ── first, ~half a day
           │
           ├── Agent 1  F1 Notice photos + PDF      (API V76 + web)       ┐
           ├── Agent 2  F2 Student profile picture  (API V77 + web)       ├ parallel
           └── Agent 3  F3 Package routine PDF      (API V78 + web)       ┘
                         │
Agent 4  Integration: merge, V75 alongside, review, contract check, QA list
```

Branching follows the existing `F:\cc-wt\*` worktree pattern:

- Agent 0 branches `feat/media-foundation` from `demo/client-review` and merges back.
- Agents 1 to 3 each branch from that merge into their own worktree: `feat/notice-media`, `feat/student-photo` and `feat/routine-pdf`.
- Agent 4 merges all three into `integ/media-features` and adds the V75 payout work.

The V75 files are still uncommitted in the main tree. Shahriar commits them on their own branch before Agent 4 starts.

### File ownership

Each file has one owner. A file not listed here is either new and created by its feature's agent, or not touched at all.

| Shared file | Owner | What changes |
|---|---|---|
| `common/audit/AuditRecorder.java` | Agent 0 | All new action constants for F1 to F3 at once (section 5) |
| `common/error/ErrorCode.java` | Agent 0 | `FILE_TYPE_NOT_ALLOWED`, `NOTICE_PHOTO_LIMIT`, only if the existing `VALIDATION_FAILED` and `FILE_TOO_LARGE` are not enough. Prefer the existing ones |
| `files/` (new helpers) | Agent 0 | `PdfUploads`, `FileResponses` (section 5) |
| `web/src/lib/api/exam/types.ts` | Agent 2 changes `Me`; Agent 3 changes `PublicModule` | Different interfaces. Each agent touches only its own interface |
| `web/src/components/ui/file/*` (new) | Agent 0 | `PdfAttachmentCard`, `Avatar` (section 5) |
| `notices/*`, `components/notice-board/*`, `components/staff/notice-board/*`, `lib/api/exam/site-notices.ts`, i18n `notices` | Agent 1 | |
| `auth/AuthService.java` (`MeView`), `auth/AuthController.java`, new `users/StudentPhoto*`, `app/(site)/account/*`, `components/shell/student-shell.tsx`, `lib/api/exam/account.ts`, i18n `account` | Agent 2 | |
| `exams/LiveExamModuleController.java`, `exams/LiveExamPublicController.java`, `exams/LiveExamPublicService.java` (`PublicModule`), `exams/LiveExamModuleService.java` (`ModuleView`), new `exams/ModuleRoutinePdf*`, `components/student/catalog/module-page.tsx`, `components/staff/catalog/module-details.tsx`, `lib/api/exam/staff-catalog.ts`, i18n `catalog` and `staffCatalog` | Agent 3 | |

## 4. Schema (three new tables, no existing table changed)

`V76__site_notice_files.sql` (Agent 1):

```sql
-- Photos and the PDF of a public notice (notice media plan, F1). Optional: a notice with no rows here shows text only.
CREATE TABLE site_notice_files (
    id                 UUID PRIMARY KEY,
    notice_id          UUID NOT NULL REFERENCES site_notices (id),
    kind               VARCHAR(8) NOT NULL,
    position           SMALLINT NOT NULL DEFAULT 0,
    stored_name        VARCHAR(64) NOT NULL,
    original_name      VARCHAR(255) NULL,
    content_type       VARCHAR(64) NOT NULL,
    byte_size          BIGINT NOT NULL,
    created_by         UUID NULL REFERENCES users (id),
    created_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT site_notice_files_kind_check CHECK (kind IN ('PHOTO', 'PDF')),
    CONSTRAINT site_notice_files_position_check CHECK (position BETWEEN 0 AND 5)
);
CREATE INDEX site_notice_files_notice_idx ON site_notice_files (notice_id, kind, position);
CREATE UNIQUE INDEX site_notice_files_one_pdf_idx ON site_notice_files (notice_id) WHERE kind = 'PDF';
```

The six-photo limit is enforced in the service under a row lock on the notice. It is not a unique position index, because reordering would then need deferred constraints.

`V77__student_photos.sql` (Agent 2):

```sql
-- A student's optional profile picture (notice media plan, F2). One row per student; no row means no picture.
CREATE TABLE student_photos (
    user_id       UUID PRIMARY KEY REFERENCES users (id),
    stored_name   VARCHAR(64) NOT NULL,
    content_type  VARCHAR(64) NOT NULL,
    byte_size     BIGINT NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL
);
```

`V78__live_exam_module_routines.sql` (Agent 3):

```sql
-- A package's optional routine PDF (notice media plan, F3). The generated routine table is unchanged.
CREATE TABLE live_exam_module_routines (
    module_id      UUID PRIMARY KEY REFERENCES live_exam_modules (id),
    stored_name    VARCHAR(64) NOT NULL,
    original_name  VARCHAR(255) NULL,
    byte_size      BIGINT NOT NULL,
    uploaded_by    UUID NULL REFERENCES users (id),
    uploaded_at    TIMESTAMPTZ NOT NULL
);
```

Separate tables, rather than new columns on `site_notices`, `student_profiles` or `live_exam_modules`, keep three things unchanged:

- the live tables;
- their JPA entities;
- the package revision and staging code, which copies `LiveExamModule` fields.

Note that `ModuleCoverService` stores the cover in columns, but those columns predate the revision system.

## 5. Agent 0: Foundation

The goal is to land the shared pieces once, so that the three feature agents never edit the same file.

API (`api/src/main/java/com/coachingcenter/api/`):

1. `files/PdfUploads.java`. `store(MultipartFile, long maxBytes)` returns a `LocalFileStorage.StoredFile`:
   - It rejects an empty file and anything over the limit (`FILE_TOO_LARGE`, 413).
   - It rejects a file that does not start with `%PDF-` (`VALIDATION_FAILED`, 400).
   - It stores the file with content type `application/pdf`.
   - It does not change `LiveExamService`. The existing inline check stays.
2. `files/FileResponses.java`. `inline(...)` and `attachment(...)` build a `ResponseEntity<InputStreamResource>` with:
   - the content type and length;
   - `Content-Disposition` with `filename*=UTF-8''…`;
   - `X-Content-Type-Options: nosniff`;
   - a cache header passed in by the caller: `public, max-age=3600` for public files with a `?v=` version, `private, no-cache` for signed-in files.
3. `AuditRecorder` constants:
   - `SITE_NOTICE_FILE_ADD`, `SITE_NOTICE_FILE_REMOVE`, `SITE_NOTICE_FILE_REORDER`;
   - `MODULE_ROUTINE_UPLOAD`, `MODULE_ROUTINE_REMOVE`, with the entity `MODULE`, reusing the existing one if present.

   The profile picture is the student's own data, so it is not audited.

Web (`web/src/`):

4. `components/ui/file/pdf-attachment-card.tsx`. A card with:
   - a PDF icon, the file name, and the size in KB or MB;
   - a **View** button that opens the existing `PdfViewer` in a dialog (`allowDownload`, no watermark);
   - a **Download** link (`?download=1`, a plain `<a download>`, which works without JavaScript and on phones).

   Props: `url`, `fileName`, `byteSize`, `title` (for the dialog and aria labels). It renders nothing when `url` is null. Use tokens only (`bg-card`, `text-muted` and so on), so it works in dark mode.
5. `components/ui/file/avatar.tsx`. A round avatar that takes `src | null`, `name` and `size`. When there is no picture, or the image fails to load, it shows initials on `bg-accent/15`. It never shows a broken image.
6. i18n `common.file.*` (en and bn): "View", "Download", "PDF", "{size} KB", "{size} MB", "Opening…", "Couldn't open the file". The Bangla must be natural and match existing terms, not a literal translation.
7. Reuse the existing `components/staff/catalog/cover-uploader.tsx` pattern for the staff upload buttons. Do not create a generic uploader unless two features need the same one.

Done when: these files exist, compile in the agent's head (no build run unless Shahriar allows it), and are merged to `feat/media-foundation`.

## 6. Agent 1: F1, notice photos and PDF

### API (`notices/`)

- `SiteNoticeFile` entity, `SiteNoticeFileRepository`, `SiteNoticeFileService`. Permissions follow the text rules exactly:
  - A **draft**'s files need `SITE_NOTICE_EDIT`.
  - A notice that is **published or unpublished** needs `SITE_NOTICE_PUBLISH` (the Master Admin), and the change shows at once.
  - A deleted notice is 404.
- Staff routes:

  | Method and path | Body | Result |
  |---|---|---|
  | `POST /api/v1/site-notices/{id}/photos` | multipart `file` | `StaffRow`. 400 `VALIDATION_FAILED` with field `photos` when 6 already exist. The new photo goes last |
  | `DELETE /api/v1/site-notices/{id}/photos/{fileId}` | | `StaffRow`. Positions close up |
  | `PUT /api/v1/site-notices/{id}/photos/order` | `{ "ids": [..] }`, exactly the current photo ids | `StaffRow` |
  | `PUT /api/v1/site-notices/{id}/pdf` | multipart `file` | `StaffRow`. Replaces any earlier PDF |
  | `DELETE /api/v1/site-notices/{id}/pdf` | | `StaffRow` |
  | `GET /api/v1/site-notices/{id}/files/{fileId}` | | The bytes, for the staff preview of a draft. `private, no-cache` |

- Public routes. Only for a `PUBLISHED`, non-deleted notice; anything else is 404, the same as `publicNotice`:
  - `GET /api/v1/public/site-notices/{id}/photos/{fileId}`: inline, `public, max-age=3600`.
  - `GET /api/v1/public/site-notices/{id}/pdf[?download=1]`: inline or attachment.
- Response additions. These are additive and appear on `StaffRow` and `PublicRow`, in both the list and the single notice:

  ```json
  "photos": [ { "id": "…", "url": "/api/v1/public/site-notices/{id}/photos/{fileId}?v=…" } ],
  "pdf": { "url": "/api/v1/public/site-notices/{id}/pdf?v=…", "fileName": "routine.pdf", "byteSize": 482113 } | null
  ```

  On `StaffRow` the URLs point to the staff file route. `photos` is `[]` when there are none, never null.
- Unpublishing or soft-deleting a notice keeps its files, so a republish brings them back. Files are deleted from disk only when an admin removes them.
- Tests to write (not run yet): limits, wrong type, non-PDF bytes renamed to `.pdf`, the seventh photo refused, reorder with a wrong id set, the public 404 for draft and unpublished notices, the System Admin refused on a published notice, the response shape with no files, and a rolled-back upload leaving no orphan file.

### Web

- `lib/api/exam/site-notices.ts`:
  - add `photos?: NoticePhoto[]` and `pdf?: NoticePdf | null` to the types (optional, for an older API);
  - add the hooks `useAddNoticePhoto`, `useRemoveNoticePhoto`, `useReorderNoticePhotos`, `usePutNoticePdf` and `useRemoveNoticePdf`. Use the existing upload helper in `lib/api/client.ts` (`FormData` is supported) and invalidate `siteNoticeKeys.all`.
- Public list (`notice-board-list.tsx`):
  - When `photos[0]` exists, the card shows it as a 16:9 thumbnail (`object-cover`, lazy, `alt` = the title).
  - When `pdf` exists, the card shows a small "PDF" pill.
  - With neither, the card is exactly as today. There is no placeholder image and no empty space.
- Public detail (`notice-board-detail.tsx`). The order is: date, text, photos, PDF.
  - Photos:
    - 1 photo: full width, at most 70vh, `object-contain`.
    - 2 to 6 photos: a 2-column grid, or 3 columns from `md`.
    - Tapping opens the image full size in a dialog, with next and previous controls and Esc to close.
    - Alt text: "{title}, photo {n} of {m}".
  - The PDF uses `PdfAttachmentCard`.
  - Each block renders only when present. The page with no files is identical to today's.
- Staff editor (`notice-editor.tsx`). A new **Attachments** section under the texts:
  - Photos:
    - thumbnails with the order number;
    - move up and move down buttons, accessible by keyboard, rather than drag;
    - a remove button that asks for confirmation;
    - an "Add photo" button, hidden at 6, with "6 of 6" shown instead.
  - The PDF shows its name and size, with **Replace** and **Remove**.
  - On a new notice, the section says "Save the draft to add photos or a PDF" until the draft has an id.
  - On a published notice, a System Admin sees the files read-only, the same as the texts.
  - Error texts for too large, wrong type and too many come from the API codes.
- i18n `notices.board.*` and `notices.board.staff.*`, in en and bn.

## 7. Agent 2: F2, student profile picture

### API

- New `users/StudentPhoto` entity, repository and `StudentPhotoService`. It is for students only. Staff calling these routes get 403.
- Routes, all for the signed-in student:

  | Method and path | Result |
  |---|---|
  | `PUT /api/v1/me/photo` (multipart `file`) | `MeView`. Re-encoded by `ExamImageStorage`, at most 1 MB stored. Replaces the old file after commit |
  | `DELETE /api/v1/me/photo` | `MeView`. Removes the file after commit |
  | `GET /api/v1/me/photo` | The bytes, `private, no-cache`. 404 when there is none |

  Use the same path prefix as the existing `GET /me` and `PATCH /me` in `AuthController`, so the web client calls it the way it calls `useChangeName`.
- `AuthService.MeView` gains `photoUrl: string | null`, set to `"/api/v1/…/me/photo?v=<updatedAt epoch ms>"`. This is the only change to `/me`. It is additive.
- Privacy:
  - There is no public route, and the picture is never in any staff or public response.
  - EXIF is stripped by the re-encode. Write a test with a JPEG carrying GPS EXIF and assert that none comes back.
  - Account deletion, if it is ever added, must delete the row and the file. Note it in the service's Javadoc.

### Web

- `lib/api/exam/types.ts`: `Me.photoUrl?: string | null`.
- `lib/api/exam/account.ts`: `useUploadPhoto` and `useRemovePhoto`. Both call `refreshMe()` afterwards, so the header updates.
- The picture needs the bearer token, so a plain `<img src>` cannot load it. Add `useAuthorizedObjectUrl(path)`, which uses `apiBlob` from `lib/api/client.ts` and the object-URL lifecycle from `components/authorized-file.tsx`. It is keyed on the versioned path, so a new upload refetches. Feed the result to the `Avatar` component from Agent 0.
- `/account` (`app/(site)/account/page.tsx`). A **Profile picture** row at the top of the card:
  - the 80 px avatar, with initials when there is none;
  - **Upload** or **Change**, which opens a file input with `accept="image/jpeg,image/png,image/webp"` and shows a preview with **Save** and **Cancel**;
  - **Remove**, which asks for confirmation.

  Square cropping is done with `object-cover` in a circle. There is no cropping tool in this release.
- The header (`components/shell/student-shell.tsx`): the account menu trigger shows the 32 px avatar for a signed-in student. Initials appear when there is no picture, so the header looks the same for students without one, apart from initials replacing the current icon (if it is an icon). Keep the 44 px touch target.
- i18n `account.photo.*`, in en and bn.

## 8. Agent 3: F3, package routine PDF

### API (`exams/`)

- New `ModuleRoutinePdf` entity, `ModuleRoutinePdfRepository` and `ModuleRoutinePdfService`. The uploads and the response follow `ModuleCoverService`.
- Permission: reuse `MODULE_COVER_MANAGE`, so the people who manage the package cover also manage its routine PDF. There is no new permission or role matrix change. If Shahriar wants System Admins to upload it too, that is a one-line change in the permission contributor later.
- The upload applies at once, like the cover. It does **not** go through the package revision and staging flow, because the routine PDF is not a priced or approved field.
- Staff routes, in `LiveExamModuleController`:
  - `PUT /api/v1/live-exam-modules/{id}/routine-pdf` (multipart `file`): returns `ModuleView`. Replaces after commit.
  - `DELETE /api/v1/live-exam-modules/{id}/routine-pdf`: returns `ModuleView`.
  - `GET /api/v1/live-exam-modules/{id}/routine-pdf`: `EXAM_READ`, so a draft package can be previewed.
- The public route, in `LiveExamPublicController`: `GET /api/v1/public/live-exam-modules/{id}/routine.pdf[?download=1]`. It is guarded the same way as the public cover (`catalog.module(id, null)` first), so an unpublished, archived or deleted package is 404.
- Response addition on `PublicModule` and the staff `ModuleView`:

  ```json
  "routinePdf": { "url": "/api/v1/public/live-exam-modules/{id}/routine.pdf?v=…", "fileName": "19th-BJS-routine.pdf", "byteSize": 391220, "updatedAt": "…" } | null
  ```

  Load it with one query per page of modules, not one per module.
- Tests to write: limits, non-PDF bytes, a public 404 for a draft package, permission refused, `routinePdf: null` for the existing package, and replace leaving one file on disk.

### Web

- `lib/api/exam/types.ts`: `PublicModule.routinePdf?: RoutinePdf | null`. `staff-catalog.ts`: the same on the staff module type, plus `useUploadRoutinePdf` and `useRemoveRoutinePdf`.
- Package page, Routine tab (`components/student/catalog/module-page.tsx`, around line 372):
  - When `data.routinePdf` exists, show `PdfAttachmentCard` at the top of the tab, titled "Routine (PDF)". The existing table or list follows unchanged.
  - Today the tab shows "no exams" when the package has no exams. When there is a PDF but no exams, show only the PDF card and not that empty text.
  - With no PDF, the tab is exactly as today.
- Staff package details (`components/staff/catalog/module-details.tsx`): a **Routine PDF** section next to the cover uploader. It shows the file name, size and upload date, and has **Upload**, **Replace**, **View** and **Remove** (remove asks for confirmation). Only users with the cover permission see the buttons. Others see the file read-only.
- i18n `catalog.routinePdf.*` and `staffCatalog.routinePdf.*`, in en and bn.

## 9. Agent 4: integration (the release itself is planned later)

1. Merge `feat/notice-media`, `feat/student-photo` and `feat/routine-pdf` into `integ/media-features`, together with Shahriar's committed V75 branch. Confirm the migration list is continuous: `…V74, V75, V76, V77, V78`.
2. Contract check. For each new field and route in sections 6 to 8, check that the API record and the web type agree on name, nullability and URL shape. Also check that every public file route returns 404, not 403, for anything that is not public.
3. Review against section 2: no `ALTER`, `UPDATE` or `DELETE` in V76 to V78; additive JSON only; `commitReplacement` on every replace and remove; magic-byte checks; no picture leaking into staff or public responses; dark-mode tokens only in new components.
4. Write `docs/exam-site/reports/media-features-qa.md`, the manual QA list for Shahriar. For each feature, cover:
   - a notice, package or student with no files (must look exactly as today);
   - a single file;
   - the maximum (6 photos);
   - a wrong type;
   - an oversized file;
   - Bangla file names;
   - phone width at 360 px, light and dark;
   - signed out versus signed in.
5. Hand over to the deployment planning step. This step is out of scope here, as Shahriar decided. It will cover the V75 key custody, the pre-release dump, the rehearsal on a copy of production, the quiet-hour restart with no open sittings, and the verification. The inputs it needs from this build are in section 2 and the migration list.

## 10. Open points (defaults chosen; Shahriar may change them)

| Point | Default in this plan |
|---|---|
| Who uploads the routine PDF | Holders of `MODULE_COVER_MANAGE` (the same people as the cover) |
| Notice photo captions | None. Alt text comes from the title |
| Avatar cropping tool | None. A centred circle crop by CSS |
| Lightbox on notice photos | Yes, a simple dialog with next and previous |
| Images over 1600 px | Resized by the existing pipeline |
