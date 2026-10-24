# A2 — Instructor directory

Today only `POST /api/v1/instructors` and `POST /api/v1/instructors/{id}/activation-code` exist. The assign picker and the instructor page in `../../ui/feature-scope.md` ("In the UI now, API later") need a list. No migration.

## Call

```
GET /api/v1/instructors?q=&status=&assigned=&page=&size=
```

| Rule | Value |
|---|---|
| Permission | `ADMIN_MANAGE`, the same as instructor create. Master Admin only. System Admin, instructor, and student receive `403`. |
| `q` | Case-insensitive contains match on full name, email, or phone. Blank is ignored. A phone in `01…` form is normalized before matching. |
| `status` | `ACTIVE`, `PENDING_ACTIVATION`, or `INACTIVE`. Anything else is `400` `VALIDATION_FAILED`. |
| `assigned` | `true` keeps instructors with at least one module, `false` keeps those with none. Omitted means both. |
| Rows | Role `INSTRUCTOR`, `deleted_at IS NULL`. Sorted by full name, then id. Default size 20, maximum 100. |
| Body | `PageResponse<InstructorView>` |

```
InstructorView {
  id, fullName, phone, email, status, createdAt,
  modules: [ { id, titleEn, titleBn, status } ]   // empty when unassigned
}
```

`modules` is a list. The schema is one instructor per module, but nothing stops one instructor from holding several modules. Deleted modules are left out. Load the modules for the whole page in one query.

Put the controller method in `AccountController` and the query in `AccountService`. The module titles come from a read-only repository method in `exams`, exposed through a small public query service. `users` must not depend on `exams` internals beyond that one interface.

## Tests: `InstructorDirectoryIT`

- The Master Admin lists active and pending instructors, sorted by name, paged, with correct totals.
- `q` matches by name, email, and phone, in both `01…` and `+8801…` forms.
- `status=PENDING_ACTIVATION` returns only pending rows. An invalid status is `400`.
- `assigned=true` and `assigned=false` split correctly. An assigned row shows the module's `titleEn` and `titleBn`.
- A deleted instructor and a deleted module are absent.
- Students, admins, and the Master Admin never appear.
- System Admin, instructor, and student receive `403`.
- Add the route to `AuditLogIT`.
