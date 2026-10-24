# A6 — Remove the module instructor

`POST /api/v1/live-exam-modules/{moduleId}/instructor` returns `409` `INSTRUCTOR_ALREADY_ASSIGNED` when a row exists, and nothing removes it. `live_exam_instructors` is keyed on `module_id`. No migration.

## Call

```
DELETE /api/v1/live-exam-modules/{moduleId}/instructor
```

| Rule | Value |
|---|---|
| Who | `LIVE_EXAM_ASSIGN`, Master Admin only, the same as assign. System Admin, instructor, and student receive `403`. |
| Missing module | `404` |
| No instructor assigned | `404`. Do not add a new code for it. |
| Effect | Deletes the `live_exam_instructors` row. Saved drafts, `evaluated_by`, and published results stay as they are (D5). The removed instructor loses the module's submission list, answer PDF, evaluation, and leaderboard reads immediately, because `requireReader` and `requireMarker` read that row. |
| Response | `204` |
| Audit | `LIVE_EXAM_INSTRUCTOR_UNASSIGN` with the module id. |

To reassign, call delete and then the existing assign. There is no separate replace call.

## Tests: add to `LiveEvaluationIT`

- Assign, unassign, then assign a second instructor: `204`, then `200`. The second instructor sees the submissions.
- After unassign, the first instructor gets `403` on the submission list and on evaluate, because `requireReader` and `requireMarker` return `ACCESS_DENIED` for a non-assigned instructor.
- A draft saved by the first instructor is still there, and the second instructor can save over it. The Master Admin publish still works.
- Unassign on a module with no instructor is `404`. On an unknown module, `404`.
- System Admin and instructor receive `403`.
- Add the route to `AuditLogIT`.
