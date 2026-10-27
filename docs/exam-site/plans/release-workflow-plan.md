# Written result release plan (written 2026-10-27)

**Approved by Shahriar on 2026-10-27:** the three recommendations below. They change one earlier decision (in `decisions.md`, "Written results": "A later save returns the result to draft until the Master Admin publishes again"). The new rule replaces it.

## What the recommendations mean

1. **Release per exam is the normal action.** Master Admin releases the exam's drafts together. Per-paper release stays for single cases and corrections.
2. **Release is blocked until the exam is complete.** A release is refused while any paper is still awaiting a mark or any sitting is still open. A failed-to-submit zero needs no mark and does not block. The release screen shows the counts before the button is enabled.
3. **A correction to a released result keeps the released result visible.** The student keeps seeing the released mark. The new mark waits as a pending change until Master Admin releases it. This is the Canvas and Moodle pattern.

Not included, because it is a new state the approval did not cover: an instructor "ready for release" flag. Ask first.

## Data model (Flyway V42)

Add nullable columns to `live_exam_participants`:

- `pending_score integer`
- `pending_passed boolean`
- `pending_comment text`
- `pending_evaluated_by uuid`
- `pending_evaluated_at timestamptz`
- `pending_stored_file_name varchar(255)`
- `pending_original_file_name varchar(500)`
- `pending_byte_size bigint`
- `pending_content_type varchar(255)`

The pending values exist when `pending_evaluated_at` is set. The existing columns (`score`, `passed`, `comment`, `evaluated_*`, `published_at`) keep the released result. For a paper that was never released, they hold the draft, as today.

## Rules

- **Save (evaluate):**
  - Paper not released: write the existing columns (as today).
  - Paper released: write the pending columns, and leave the released columns and `published_at` alone.
  - No file sent: the pending file (or, if none, the released file) is kept. A pending file that shares the released file's name is not deleted.
  - File sent: store it. Delete the previous pending file only if it is not the released file.
- **Release (publish one paper):**
  - Needs an effective mark (pending if present, else the existing one).
  - If a pending change exists: copy it into the released columns, clear the pending columns, set `published_at` to now (a new notice is written, because the notice key uses the release time), and delete the previous released file if it is no longer used.
  - If no pending change and already released: no-op.
- **Release (publish all, per exam):**
  - Refused with `409 EXAM_MARKING_INCOMPLETE`, with `details: { awaiting, open }`, while any submitted paper has no mark (`awaiting`) or any sitting is still `IN_PROGRESS` (`open`).
  - Otherwise releases every paper with a draft or a pending change. It returns `{ published: n }`, as today.
  - Failed-to-submit zeros are not released, as today.
- **Student reads:** only the released columns, with `published_at` (unchanged code). The student sees the released result, and the pending change is hidden.
- **Staff reads:** show the effective values (pending if present). Add `releasePending` (true when a pending change waits for release). `published` stays true only when the released result equals the effective one.
- **Staff evaluated PDF:** the effective file (pending if present).
- **Feed statuses:**
  - `AWAITING`: submitted, no mark (unchanged).
  - `DRAFT`: marked but not released, or released with a pending change. These need a release.
  - `PUBLISHED`: released, no pending change.
  - `FAILED_TO_SUBMIT`: unchanged.
- **Leaderboard and results report:** read the released columns (unchanged code).

## Error code

Add `EXAM_MARKING_INCOMPLETE` to `ErrorCode` (409), with details `{ awaiting, open }`.

## Tests

- Update the tests that assert the old rule. Known: `aWrittenNoticeAppearsOnlyOnPublishAndAResaveHidesItsMarksUntilThePublishAgain` and `aMasterAdminSaveStaysDraftUntilThatAdminPublishes`. Check the rest by running the suite.
- New: a correction keeps the released mark for the student, shows `releasePending` for staff, and is released by publish (a new notice). A release with an unmarked paper is refused with the counts. A failed-to-submit zero does not block a release. A released file is kept when a pending change without a file is released.

## Web (after the API)

- Results page: the release button shows the counts and is disabled while `awaiting` or `open` is non-zero, with the reason. A `409` shows the counts.
- Marking page and submission browser: a paper with `releasePending` shows "change waiting for release" with a release action. The student copy stays unchanged.
- Feed label for `DRAFT`: "needs release".

## Docs

- `decisions.md`: replace the old "later save returns to draft" line with the new rule.
- `current-api.md`: the evaluation table and the publish rows.
