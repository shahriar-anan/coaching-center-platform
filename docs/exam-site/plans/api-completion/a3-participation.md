# A3 — Participation counts

Rules from [../../steps/07-operate-screens.md](../../steps/07-operate-screens.md): a read of participants that already exist, not an analytics product. No migration.

## Call

```
GET /api/v1/live-exam-participation?moduleId=&type=&from=&to=&q=&sort=&page=&size=
```

| Rule | Value |
|---|---|
| Who | Master Admin and System Admin need `EXAM_READ`. An instructor needs `LIVE_EXAM_EVALUATE` and sees assigned modules only. A student receives `403`. |
| `moduleId` | Optional. An instructor passing an unassigned module receives `404`. |
| `type` | `MCQ` or `WRITTEN`. |
| `from`, `to` | ISO instants compared with `live_at`. An exam with a null `live_at` is kept only when neither is given. |
| `q` | Contains match on either exam title. |
| `sort` | `liveAt` (default, newest first), `title`, or `sitting`. |
| Rows | Published exams that are not deleted. Archived modules stay, so past results can be counted. |
| Body | `PageResponse<ParticipationRow>` |

```
ParticipationRow {
  examId, moduleId, moduleTitleEn, moduleTitleBn, examType, titleEn, titleBn, liveAt, endAt,
  sitting,            // submitted_at null and now < deadline_at
  submitted,          // status SUBMITTED (MCQ posted, or written PDF posted)
  failedToSubmit,     // status FAILED_TO_SUBMIT
  awaitingMarking,    // WRITTEN, SUBMITTED, evaluated_at null; null for MCQ
  draftsAwaitingPublish, // WRITTEN, evaluated_at set, published_at null; null for MCQ
  published,          // WRITTEN, published_at set; null for MCQ
  retakes             // kind RETAKE, included in the counts above
}
```

A row past its deadline that is not posted yet is counted as it will post: MCQ as `submitted`, written with a file as `submitted`, written without a file as `failedToSubmit`. The A1 slice 1 job closes that gap within a minute. The read itself does not write.

Compute all counts in one grouped SQL query with `COUNT(*) FILTER (WHERE …)` over the exam ids of the page, not one query per exam.

## Menu badges (D7)

The website shows `awaitingMarking` to the instructor and `draftsAwaitingPublish` to the Master Admin as numbers on the menu and the overview. There is no stored staff notice. The same call serves them: no `moduleId` filter gives the total, and an instructor is limited to assigned modules by the rule above. Add `GET /api/v1/live-exam-participation/summary` returning `{ awaitingMarking, draftsAwaitingPublish }` for the caller's scope, so the menu does not page through every exam. It uses the same permissions and the same grouped query. A student receives `403`.

## Tests: `LiveParticipationIT`

- One MCQ exam with an in-progress sitting, a posted paper, and a retake. One written exam with an unmarked paper, a draft, a published paper, and a failed-to-submit. Each count is exact, and the MCQ-only fields are null.
- A sitting past its deadline with no read counts as posted before the job runs, and the same after it runs.
- Filters: `moduleId`, `type`, `from`/`to`, `q`. Each sort order. Paging.
- Draft and deleted exams are absent.
- An instructor sees only assigned modules and gets `404` for another module's id. A System Admin sees all. A student gets `403`.
- The summary matches the sum of the list: an instructor's total covers assigned modules only, the Master Admin's covers all, a student gets `403`.
- Add both routes to `AuditLogIT`.
