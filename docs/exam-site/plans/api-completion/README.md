# API completion — A1 to A6

Written 25 October 2026 from the code on branch `catalog-acquisition`. Shahriar confirmed it on 25 October 2026 with every proposed default below, and added the staff badge rule in D7. Goals come from [../../todays-goals.md](../../todays-goals.md). [../../decisions.md](../../decisions.md) still wins where this folder is silent.

| Goal | File | Migration | Size |
|---|---|---|---|
| A1 | [a1-student-inbox.md](a1-student-inbox.md): deadline settle job, notices table, reminder, inbox calls | `V36` (+ `V35` index) | ~1 day |
| A2 | [a2-instructor-directory.md](a2-instructor-directory.md): list instructors | none | 2–3 h |
| A3 | [a3-participation.md](a3-participation.md): counts per exam | none | 3–4 h |
| A4 | [a4-student-status.md](a4-student-status.md): own status per exam | none | 3 h |
| A5 | [a5-authoring-deletes.md](a5-authoring-deletes.md): delete exam, delete MCQ question | none | 3 h |
| A6 | [a6-instructor-unassign.md](a6-instructor-unassign.md): remove the module instructor | none | 1–2 h |
| A7 | [a7-staff-payments.md](a7-staff-payments.md): staff list of exam payments, payment channel (added 25 October 2026 after the VPS deploy) | `V37` | 3 h |

## Slice order

Each slice ends with its `*IT` class green and a progress note in `api/.cursor/progress/api-completion/slice-<n>-<name>.md`. Do not start the next slice before that.

| # | Slice | Why here |
|---|---|---|
| 1 | A1 slice 1: deadline settle job | Today a score or failed-to-submit result posts only when someone reads that sitting. A3, A4, and the A1 notices all depend on posting at the deadline. |
| 2 | A2 instructor directory | Unblocks the assign picker. Read only. |
| 3 | A6 unassign | Small. It fixes QA mistakes. |
| 4 | A5 deletes | Same: QA needs a way to fix wrong content. |
| 5 | A3 participation counts | Step 7 overview. |
| 6 | A4 student status | Status pills on the student website. |
| 7 | A1 slices 2–3: notices table, MCQ and written notices, inbox calls | Largest change. The website can show an empty bell until it lands. |
| 8 | A1 slice 4: reminder scheduler | Last, because it uses the slice 7 table. |

## Practices that apply to every slice

**Layout and schema**
- Package `com.coachingcenter.api.exams` for A1, A3, A4, A5, and A6. A2 lives in `users`, beside instructor create.
- Flyway only, with `ddl-auto=validate`. A1 adds `V36__student_notices.sql`; the deadline job added `V35`. Do not edit `V1`–`V35`.
- Soft delete uses the existing `deleted_at` columns. Every read keeps `…AndDeletedAtIsNull`.

**HTTP**
- All paths are under `/api/v1`.
- Lists take `page` and `size` and return `common.web.PageResponse`.
- Errors use the shared body: `code`, `message`, optional `fieldErrors`, `timestamp`, `requestId`. One `ErrorCode` per condition. Reuse an existing code when the condition is the same.
- A resource the caller may not see returns `404`, not `403`. That covers another student's notice and a module outside the instructor's assignment, so ids are not leaked.
- Times are ISO-8601 UTC strings, the same as `liveAt` and `endAt` today.
- Bilingual titles always return both `titleEn` and `titleBn`.

**Security**
- The controller calls `require(Permissions.X)`. The service still checks the role or scope where the rule is narrower, the same way `publish` checks `MASTER_ADMIN`.
- No payload contains another student's row, a phone number of another user, or the evaluator, except the staff reads that already expose `evaluatorName`.
- Never log a code, token, or secret.

**Data and time**
- Read time from the injected `Clock`. Tests use `MutableClock` and never sleep.
- Reads that list many exams load participants in one query per page, never one query per row.
- Insert-once rows use PostgreSQL `INSERT … ON CONFLICT DO NOTHING` through a native query. A caught `DataIntegrityViolationException` inside a running PostgreSQL transaction aborts that transaction. This is also a latent risk in `ExamMailQueue.insert`. Fix it there in A1 slice 2 by using the same helper.
- Schedulers are single-flight (`AtomicBoolean`) like `ExamMailService.scan`. They process each row in its own transaction, so one bad row does not roll back the batch. Only one API process runs on the server.

**Audit and inventory**
- Destructive staff actions write an `AuditRecorder` row: exam delete, question delete, instructor unassign.
- Every new route goes into the route list in `api/src/test/java/com/coachingcenter/api/common/audit/AuditLogIT.java`.

## Tests

- `*IT` classes extend `PostgresIntegrationTest` and run against real PostgreSQL. No H2.
- Mail stays on the capturing senders. Nothing calls Brevo or SSLCommerz.
- Run the touched classes, then the full suite before the last slice is reported done: `mvn test`.
- A test asserts the response status, the response body, and the database row. A status alone is not enough for a write.
- Each goal file lists its cases. Every goal also needs these cases:
  - Each role that must be refused gets its `403` or `404`.
  - An unknown id returns `404`.
  - A deleted parent row behaves as missing.

## Decisions

Confirmed by Shahriar on 25 October 2026.

| # | Question | Proposed default |
|---|---|---|
| D1 | Status pills (A4) are "still open" in `decisions.md`. Build them now? | Yes, using the A4 status list. |
| D2 | Who deletes a live exam? | Master Admin only (`EXAM_DELETE`), the same as module and category delete. |
| D3 | When is exam delete refused? | When any participant row exists, or when a `SUCCEEDED` order or a grant for that exam exists. Open `INITIATED` orders for it are cancelled in the same transaction. |
| D4 | Who deletes an MCQ question? | Anyone who can edit it (`EXAM_MANAGE`). It follows the same lock as edit: refused once `liveAt` has passed, and it returns the exam to draft. |
| D5 | Unassign when the instructor has saved drafts? | Allowed. Drafts and `evaluated_by` stay. The next instructor can save over them. Only the Master Admin publishes either way. |
| D6 | Reminder band (A1) | Created from 48 h before `liveAt`, inside a 48 h–24 h band. A student who gains access later still gets one up to the start. One per student, exam, and `liveAt`. |
| D7 | Notices for staff | No stored staff notices and no staff email. Staff see work-queue counts from A3 as menu badges: the instructor sees papers awaiting marking, the Master Admin sees drafts awaiting publish. A count is not a notice, so `decisions.md` is unchanged. Staff notices for assignment, failed mail, and unmatched payments wait until the client asks. |
| D8 | A written result saved again after publish | The notice stays. Its marks are hidden while the result is back in draft. The next publish adds a new notice. |

## Exit

- All six goals pass their `*IT` classes, and the full suite is green.
- `AuditLogIT` lists every new route.
- The progress notes exist.
- [../../current-api.md](../../current-api.md) gains the new calls.
- The `todays-goals.md` rows A1–A6 are marked done.
