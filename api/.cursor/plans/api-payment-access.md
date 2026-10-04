# api-payment-access

Stage plan for verification, offline admission, and book fulfillment. The binding spec is [phase-2-scope.md](../../../docs/phases/phase-2/phase-2-scope.md) Sections 5.1, 6, and 7.3, and Stage 5. This plan adds order only. It does not drop an exit check.

Required cases are in [api-payment-access.md](../tests/api-payment-access.md).

Start only after `api-purchase` exits. Do not start `api-content-access` until this stage exits.

Do not build a refund, a cancellation, or a gateway. Do not revoke an enrollment from a payment route. Ending everyone's access is the Master Admin close action, already built.

System Admins do not handle money. Online verification and cash received are Master Admin only. Offline admission is not a payment, and both admin roles can grant it. Either admin can update delivery status.

When a slice's checks finish, write `api/.cursor/progress/api-payment-access/slice-<number>-<name>.md`.

## Permissions

| Permission | Master Admin | System Admin |
|---|---|---|
| `PAYMENT_VERIFY` | yes | no |
| `CASH_RECEIVE` | yes | no |
| `ENROLLMENT_GRANT` | yes | yes |
| `ORDER_FULFILL` | yes | yes |

## Slice 1 — Verify payment

`GET /api/v1/payments?status=PENDING` under `PAYMENT_VERIFY`, paged.

`POST /api/v1/payments/{id}/verify` with `result` `SUCCESS` or `FAILED`, under `PAYMENT_VERIFY`. A second verify of a payment that is no longer pending returns `409`, code `PAYMENT_NOT_PENDING`.

- Success on a course enrollment sets the enrollment `ACTIVE` and the payment `SUCCESS`, and audits `PAYMENT_SUCCESS`, only when the course is still published, open, and the enrollment's `opening_number` is still the course's current number. If the course is closed, the payment stays pending and the body uses `COURSE_NOT_OPEN`. Nothing is activated.
- Success on a book order sets the payment `SUCCESS` and the order `PAID`.
- Failure sets the payment `FAILED`. A course enrollment becomes `PAYMENT_FAILED` and grants nothing. An online book order becomes `PAYMENT_FAILED` and does not move to paid, ready, or delivered. Audit `PAYMENT_FAILED`.
- `price_paid` is not rewritten if the course price changed after the purchase was started.

## Slice 2 — Offline admission

`POST /api/v1/courses/{id}/admissions` with `studentId` and, for a hybrid course, `deliveryMode`. Permission `ENROLLMENT_GRANT`.

- The course must be published and open. A full-free course is rejected with `COURSE_IS_FREE` because it needs no enrollment.
- Creates an `ACTIVE` enrollment for the current `opening_number`, `source` `OFFLINE`, no payment row.
- If that student already has an active enrollment for this opening, return `ALREADY_ENROLLED`.
- An admission from an earlier `opening_number` does not block a new admission and does not itself become active again.

## Slice 3 — Fulfillment and cash

Either admin with `ORDER_FULFILL` may move:

- Online: `PAID` → `READY` → `DELIVERED` or `COLLECTED`.
- The caller picks `DELIVERED` for a delivery order and `COLLECTED` for a pickup order. The other choice returns `409`, code `FULFILLMENT_MISMATCH`.

Cash orders have no payment. Status path is `PLACED` → `READY` → `COLLECTED`.

- `READY` on a cash order is `ORDER_FULFILL`.
- `COLLECTED` on a cash order is `CASH_RECEIVE` only. That single action records the cash and audits `CASH_RECEIVED`. A System Admin receives `403`.

There is still no cancelled status and no cancel route.

## Follow-on — Payment list filters

Not part of the stage exit above. `GET /api/v1/payments` stays under `PAYMENT_VERIFY`, so only a Master Admin can list or confirm a payment. A System Admin still receives `403`.

`status` may be `PENDING` (the default), `SUCCESS`, or `FAILED`. `payableType` may be `COURSE_ENROLLMENT` or `BOOK_ORDER`. Omitting `payableType` returns both. Any other status or type returns `400`. There is no cancelled status.

Each row includes the student name and phone, the course or book titles, and `createdAt`, along with the amount, method, transaction reference, and status.

## Stage exit

A student purchase can be verified into access while the course is open, or into a failed payment that grants nothing. A book order advances when payment succeeds. An offline admission grants access with no payment. Either admin can advance delivery. Only the Master Admin records cash and verifies online payment. A successful course payment does not grant access when the course is closed.
