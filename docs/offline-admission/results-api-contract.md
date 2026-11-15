# Offline results: API contract (shared by the API and web work)

This file is the agreed shape of every route used by the results feature. The API implements it exactly; the web builds against it.
Rules and decisions are in `offline-results-plan.md`. Field names are camelCase JSON. Dates are `yyyy-MM-dd`; times are ISO instants.
Money-free: no fee fields here. Errors use the existing `ApiException` envelope (`code`, `message`, `fieldErrors`).

## Enums
- `examType`: `MCQ` | `MCQ_WRITTEN` | `WRITTEN`
- sheet `status`: `STAGED` | `PUBLISHED` | `REPLACED`
- batch exam `state`: `NONE` | `STAGED` | `PUBLISHED` | `PUBLISHED_WITH_STAGED`

## Permissions
- `OFFLINE_RESULT_UPLOAD`: MASTER_ADMIN and SYSTEM_ADMIN (every admin). Web flag: `canUploadOfflineResult`.
- `OFFLINE_RESULT_PUBLISH`: MASTER_ADMIN only. Web flag: `canPublishOfflineResult`.
- Both are registered the same way as `OFFLINE_BATCH_ROUTINE` (`Permissions.java` and the offline permission contributor), and exposed to
  the web the same way the other `can*` flags are.

## Staff routes

### Batch exam list
`GET /api/v1/offline-batches/{batchId}/results` (staff; `OFFLINE_BATCH_ROUTINE` or `OFFLINE_RESULT_UPLOAD` to read)
```json
[{ "classId": "uuid", "heldOn": "2026-11-10", "subject": "Constitution", "topic": "Part 1", "instructorName": "Name or null",
   "kind": "EXAM", "status": "SCHEDULED", "cancelled": false, "state": "PUBLISHED_WITH_STAGED", "publishedAt": "2026-11-12T04:00:00Z",
   "rowCount": 47 }]
```
Lists only exam classes of the batch (`kind` = `EXAM`), newest `heldOn` first. `rowCount` is the published sheet's rows, or 0.
`status` is the class's stored status: `SCHEDULED`, `PAID_PENDING`, `PAID`, `NOT_TAKEN_PENDING`, `NOT_TAKEN` or `CANCELLED`. A class
whose status is `NOT_TAKEN` or `NOT_TAKEN_PENDING` takes no result (the preview says so, and the Results tab disables Upload).

### Preview (saves nothing)
`POST /api/v1/offline-classes/{classId}/results/preview`, multipart: `file` (.xlsx), `examType`, `passMark` (optional decimal).
```json
{ "examType": "MCQ", "fullMarks": 50, "negative": 0.25, "passMark": 40,
  "counts": { "rows": 49, "matched": 47, "absent": 2, "mismatched": 3 },
  "rows": [{ "rowNumber": 6, "roll": "192001", "admissionId": "uuid or null", "studentName": "Name or null", "absent": false,
             "right": 20, "wrong": 5, "mcq": 18.75, "written": null, "total": 18.75, "rank": 2, "passed": false,
             "fileTotal": 18.75, "fileRank": 2, "mismatch": false, "maybeAbsent": false }],
  "problems": [{ "rowNumber": 23, "roll": "192099", "code": "ROLL_NOT_IN_BATCH", "params": { "roll": "192099" },
                 "message": "No student of this batch has this roll number." }],
  "warnings": [{ "rowNumber": 9, "roll": "123", "code": "ROLL_MATCHED_ZERO_PADDED", "params": { "roll": "123", "matched": "0123" },
                 "message": "Row 9: roll 123 matched 0123." }] }
```
- `rows` includes `absent: true` rows for expected students with no sheet row (marks, rank, passed are null).
- `maybeAbsent` is true when right, wrong and written are all 0 (rule R2). The rank is shared as usual.
- `problems` non-empty means the file cannot be saved (HTTP 200 for preview, so the dialog can show them; save answers 400).
- `warnings` holds only what the rows cannot show. The absent rows and the rows that differ from the file's own total or position are
  not in `warnings`; the web builds those lines from `rows`. A matched roll that had no exact match but matched a batch roll with the same
  number (leading zeros) is a warning with code `ROLL_MATCHED_ZERO_PADDED`, and the stored row keeps the batch's roll text.
- `rank` is competition ranking (1, 2, 2, 4). A negative total ranks below zero.

### Codes and the English message
Every problem and warning has `code` (a stable key, for the web's translation), `params` (the values the message names, as strings or
numbers), and `message` (English, for logs and older clients). The web shows `staffOfflineResults.problems.<code>` in its language and
falls back to `message` for a code it does not know. Codes:
- Problems: `ROLL_NOT_IN_BATCH {roll}`, `ROLL_IN_OTHER_BATCH {roll}`, `ROW_DUPLICATE_ROLL {roll, first}`, `TOTAL_ABOVE_FULL {total, full}`,
  `FULL_MARKS_DIFFER {value, first}`, `FULL_MARKS_MISSING {}`, `NEGATIVE_DIFFER {value, first}`, `NEGATIVE_MISSING {}`,
  `NOT_A_NUMBER {label, value}`, `NOT_WHOLE {label, value}`, `BELOW_ZERO {label, value}`, `COUNT_TOO_LARGE {label, value}`,
  `NOT_TAKEN {}`, `CANCELLED {}`, `NOT_EXAM {}`, `FUTURE_DATE {heldOn}`, `PASS_MARK_ABOVE_FULL {pass, full}`, `NO_ROWS {}`,
  `NO_FULL_MARKS {}`, `TOO_MANY_ROWS {max}`.
- Warnings: `ROLL_MATCHED_ZERO_PADDED {roll, matched}`.
- `label` is the column's name in English (for example "Right answers").
- Refused files (HTTP 400, a field error on `file`) carry `code` and `params` too: `FILE_REFUSED {}` (not a readable .xlsx, or an .xls),
  `NO_HEADER {}`, `MISSING_COLUMN {column, examType}`, `TOO_MANY_ROWS {max}`. A file whose parts expand past the cap is refused as
  `FILE_REFUSED {}` with the message "The file is too large to read.".
- A field error with no code is written as `field` and `message` only.

### Save as staged
`POST /api/v1/offline-classes/{classId}/results` (same multipart). Answers 400 with the problems when there are any, as field errors on
`file`, each with `code` and `params` (see Codes above). On success it replaces any earlier staged sheet and answers the sheet view below
(HTTP 201). 409 `RESULT_CHANGED` when a racing request for the same exam wins (see Publish).

### Sheet view (both sheets of one exam)
`GET /api/v1/offline-classes/{classId}/results`
```json
{ "classId": "uuid", "batchId": "uuid", "subject": "...", "topic": "...", "heldOn": "2026-11-10", "cancelled": false,
  "published": Sheet or null, "staged": Sheet or null }
```
`Sheet` = `{ "id", "status", "examType", "fullMarks", "negative", "passMark", "fileName", "uploadedBy", "uploadedAt",
"publishedBy", "publishedAt", "rows": [Row], "notifyCount": 3, "changedSinceSave": [Change] }` where `Row` is the same object as in the
preview `rows` plus `"id"`. `uploadedBy`/`publishedBy` are display names or null.
- `notifyCount` is the number of distinct ONBOARDED students with a row in the sheet (present or absent): the students a publish tells.
  The publish confirm uses it, not the batch's student count.
- `changedSinceSave` is set on the staged sheet only (it is `[]` on the published one). It lists each matched row whose admission is now
  CLOSED, or whose roll has changed since the save: `{ "admissionId", "name", "oldRoll", "newRoll", "closed" }`. `newRoll` is the roll the
  admission has now (or null). Publishing still goes ahead; the list tells staff what changed.

### Publish
`POST /api/v1/offline-classes/{classId}/results/publish` (`OFFLINE_RESULT_PUBLISH`). Makes the staged sheet PUBLISHED, marks the old
published sheet REPLACED, and notifies every ONBOARDED student with a row in the sheet, present or absent (`OFFLINE_RESULT_PUBLISHED` for a
first publish, `OFFLINE_RESULT_UPDATED` for a republish). A student onboarded after the exam has no row, so is not told. Answers the sheet
view. 409 when nothing is staged. 409 with code `RESULT_CHANGED` ("Someone else changed this result first. Reload and try again.") when a
racing publish or save for the same exam is refused by the database.

A save, a publish and a discard lock the exam class first, so two of them for one exam run one after the other. The second one sees the
first one's result.

### Discard staged
`DELETE /api/v1/offline-classes/{classId}/results/staged` (upload permission). 204. Never touches the published sheet.

### Download the uploaded file
`GET /api/v1/offline-classes/{classId}/results/{sheetId}/file` (staff). The original bytes, with the file name.

## Student routes (the student's own admission only; another student's id is 404)

### The admission's results
`GET /api/v1/me/admissions/{admissionId}/results`
```json
{ "batchNameEn": "...", "batchNameBn": "...", "roll": "192001 or null",
  "exams": [{ "classId": "uuid", "heldOn": "2026-11-10", "subject": "...", "topic": "...", "examType": "MCQ_WRITTEN",
              "fullMarks": 100, "passMark": 40, "publishedAt": "...",
              "absent": false, "maybeAbsent": false,
              "total": 50.75, "mcq": 18.75, "written": 32, "rank": 2, "rankOf": 47, "passed": true }] }
```
- Only PUBLISHED sheets of the admission's batch. For a CLOSED admission, only sheets published before the close (rule R12).
- `absent` is true when this student has no row in that sheet. Then `total`, `rank`, `passed` are null.
- `rankOf` is the number of ranked students (absent excluded).
- `mcq` is null for a WRITTEN exam; `written` is null for an MCQ exam.

### One exam's leaderboard
`GET /api/v1/me/admissions/{admissionId}/results/{classId}`
```json
{ "classId": "uuid", "subject": "...", "topic": "...", "heldOn": "2026-11-10", "examType": "MCQ", "fullMarks": 50,
  "passMark": 40, "publishedAt": "...", "myRoll": "192001 or null",
  "rows": [{ "roll": "192001", "name": "Full name", "rank": 1, "total": 49.5, "mcq": 49.5, "written": null,
             "passed": true, "absent": false, "maybeAbsent": false, "mine": false }] }
```
- Sorted by rank, then roll. Absent students come last with `rank: null`, `absent: true`.
- Every student of the batch with a row is listed; roll numbers are visible to other students (decision R6).
- A student whose admission is closed after publish keeps access; access rule as above.

## Notices
- Kinds `OFFLINE_RESULT_PUBLISHED` and `OFFLINE_RESULT_UPDATED`, written to `student_notices` with a dedupe key of the sheet id and
  the student id. The web opens them at `/offline-activities#results`.

## Routine and class rules touched by the results
- A routine upload never cancels or moves an exam class that has a sheet (STAGED or PUBLISHED). The routine preview marks such an exam
  as kept (`keptForResult: true`). Hand-cancelling such a class is refused with code `OFFLINE_CLASS_HAS_RESULT` and a message.
- Changing the date (`heldOn`) of an exam class that has a sheet (STAGED or PUBLISHED) is refused with `OFFLINE_CLASS_HAS_RESULT` (409,
  "This exam has a result, so its date cannot change. Discard the staged result first, or keep the date."). Keeping the same date is
  allowed, and the other fields (instructor, rate, times, subject, topic) stay editable.
- The class view (`PATCH /api/v1/offline-classes/{id}` and the class lists) carries `hasResult` (boolean), true when the class has a
  STAGED or PUBLISHED sheet.
- Results can be uploaded only for classes with `heldOn` on or before today (Dhaka). Otherwise the preview refuses with a problem.
- Classes are added by hand with the existing `POST /api/v1/offline-batches/{batchId}/classes` (unchanged).

## Roll search on the admission queue
- `GET /api/v1/offline-admissions?q=` also matches the roll number. The queue row carries `rollNo`.
