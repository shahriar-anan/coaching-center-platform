# A1 — Student inbox

Rules from [../../steps/05-notifications.md](../../steps/05-notifications.md) and `decisions.md` "Accounts and notices" stay in force. Notices are rows the website reads. No email, SMS, push, or websocket for these three events. Flyway `V36`.

## Slice 1 — Deadline settle job

**Problem in the code today.** `LiveExamSittingService.postIfDue` runs only inside a read: `studentExam`, `submit`, `result`, `settleParticipant`, and `finalizeDue` from the staff list and the leaderboard. A student who closes the tab never has a score posted, and a written paper with no PDF is never marked `FAILED_TO_SUBMIT`, until someone opens it. The MCQ notice, A3 counts, and A4 status would all be wrong until then.

**Change.** Add `LiveExamDeadlineJob` in `exams`.

- `@Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = MINUTES)`, single-flight.
- Select the ids of participants with `deleted_at IS NULL AND submitted_at IS NULL AND deadline_at <= :now`, oldest first, limit 500 per run.
- Call `LiveExamSittingService.settleParticipant(id)` for each id in its own transaction. Catch and log each failure with the id and the exception class only.
- The partial index on `live_exam_participants (deadline_at)` is `V35`. Done.

The scoring rules do not change. The job only calls the existing post path earlier.

**Tests: `LiveExamDeadlineJobIT`**
- An MCQ sitting with saved answers, deadline passed, no reads: one run sets `submitted_at` and the score.
- A blank MCQ scores 0 and `passed` false.
- A written sitting with no PDF becomes `FAILED_TO_SUBMIT` with score 0. A written sitting with a PDF becomes `SUBMITTED`.
- A sitting before its deadline is untouched.
- A second run changes nothing.
- A deleted participant is skipped.

**Untested:** One failing sitting does not roll back the others in the same run; the 500-per-run limit is enforced.

## Slice 2 — Notices table and the two result notices

`V36__student_notices.sql`:

```sql
CREATE TABLE student_notices (
    id             UUID PRIMARY KEY,
    user_id        UUID NOT NULL REFERENCES users (id),
    kind           VARCHAR(32) NOT NULL,
    message_key    VARCHAR(220) NOT NULL,
    exam_id        UUID NOT NULL REFERENCES live_exams (id),
    participant_id UUID NULL REFERENCES live_exam_participants (id),
    created_at     TIMESTAMPTZ NOT NULL,
    read_at        TIMESTAMPTZ NULL,
    CONSTRAINT student_notices_kind_check CHECK (kind IN ('EXAM_REMINDER', 'MCQ_SCORE', 'WRITTEN_RESULT')),
    CONSTRAINT student_notices_key_unique UNIQUE (kind, message_key)
);
CREATE INDEX student_notices_user_idx ON student_notices (user_id, created_at DESC);
CREATE INDEX student_notices_unread_idx ON student_notices (user_id) WHERE read_at IS NULL;
```

The row stores no title, score, or text. The view is built from current rows at read time, the same way exam mail is.

`StudentNoticeWriter` has one method per kind. Each inserts with a native `INSERT … ON CONFLICT (kind, message_key) DO NOTHING` and joins the caller's transaction. A conflict is not an error. Move `ExamMailQueue.insert` to the same `ON CONFLICT` pattern in this slice.

| Kind | Fires from | Key | Not written when |
|---|---|---|---|
| `MCQ_SCORE` | `LiveExamSittingService.post` for an MCQ, which covers both an early submit and the deadline post, for `LIVE` and `RETAKE` | participant id | The student is not `ACTIVE`, or the participant is deleted |
| `WRITTEN_RESULT` | `LiveExamEvaluationService.publish`, only when `published_at` moves from null to a time | participant id + `:` + `published_at` epoch millis | The repeat publish that does not move `published_at`. An instructor or Master Admin save. `FAILED_TO_SUBMIT`, which has no publish. |

A notice is never written from a save, a read, or a failed post.

## Slice 3 — Inbox calls

Student only: `LIVE_EXAM_BROWSE`, and the service checks `Role.STUDENT`. Staff receive `403`.

| Call | Result |
|---|---|
| `GET /api/v1/me/notices?page=&size=` | `PageResponse<NoticeView>`, newest first. Default size 20, maximum 50. |
| `GET /api/v1/me/notices/unread-count` | `{ "count": n }` for the bell. |
| `POST /api/v1/me/notices/{id}/read` | `200` with the `NoticeView`. Sets `read_at` once. Repeating it keeps the first time. Another student's id, or an unknown id, is `404`. |
| `POST /api/v1/me/notices/read-all` | `{ "updated": n }`. One `UPDATE … WHERE user_id = :me AND read_at IS NULL`. |

```
NoticeView {
  id, kind, createdAt, readAt,
  exam { id, moduleId, examType, titleEn, titleBn, liveAt, endAt },
  score, maximum, passed        // MCQ_SCORE and WRITTEN_RESULT only
}
```

Read-time rules:
- `score`, `maximum`, and `passed` come from the participant row now.
- For `WRITTEN_RESULT`, they are null while that participant's `published_at` is null (D8).
- A notice whose exam or module is deleted is left out of the list and the count.
- The view never has the evaluator, a comment, another student, or a phone number.

**Tests: `StudentInboxIT`**
- An MCQ submit writes one `MCQ_SCORE`. A second submit, and the deadline job, do not write another.
- A retake score writes its own notice.
- An instructor save writes nothing. The Master Admin publish writes one `WRITTEN_RESULT`. A repeat publish writes nothing. Save, then publish again, writes a second notice. Between them the first notice shows null marks.
- The list is newest first and paged. The unread count matches. Read sets `read_at` once. Read-all returns the number changed.
- Student B reading or marking student A's notice gets `404`. An instructor, System Admin, and Master Admin get `403` on every inbox call.
- A notice for a deleted exam drops out of the list and the count.
- The body has no `evaluatorName`, no `evaluatedBy`, no `comment`, and no phone number.
- `ExamMailIT` stays green after the `ON CONFLICT` change.

## Slice 4 — Reminder scheduler

`StudentReminderJob`: `@Scheduled(fixedDelay = 15, initialDelay = 2, timeUnit = MINUTES)`, single-flight, takes the `Clock`.

- Select published, not deleted exams in a published module whose category branch is not archived (reuse `LiveExamCatalogVisibility`), with `now < live_at <= now + 48h`.
- For each, run `LiveExamRepository.findActiveStudentsWithAccess(moduleId, examId)`. That covers the roster, a module grant, and an exam grant.
- Insert `EXAM_REMINDER` with key `studentId:examId:liveAt epoch millis`.
- No reminder when `live_at` is null.
- A `liveAt` change makes a new key. The exam is back in draft until it is republished, and the job only reads published exams.
- The first run inside 48 h writes the reminder (D6). A student who gains access after that still gets one on the next run, up to the start. After `live_at` nothing is written.

**Tests: add to `StudentInboxIT`**
- Exam at now + 36 h, a roster student and a purchase student: one reminder each. A second run writes none.
- A student with no grant gets none. An exam grant for another exam in the same module gets none.
- An exam at now + 72 h gets none until the clock moves to 47 h before.
- A null `liveAt` gets none. A draft or archived exam gets none.
- An exam that has already started gets none.
- Changing `liveAt` and republishing writes a new reminder for the new time.

## Out of this goal

Email for these events, staff notices (D7), broadcasts, deleting a notice, and the website bell and page (step 6, wave 7).
