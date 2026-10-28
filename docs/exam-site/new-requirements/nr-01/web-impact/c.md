# NR-01 web impact: agent C (commerce and access)

Each line: what the UI must do, and the API field or route it uses. See [../../web-impact.md](../../web-impact.md).

- Checkout shows the struck-through list price and the amount paid. Uses `listPrice`, `discountPercent` and `amount` on `POST /live-exam-orders` and the order views.
- A free exam shows no buy button and no price. Uses the `EXAM_NOT_FOR_SALE` error on order, and the catalog's buy state (`FREE`).
- The student's access list shows the expiry date, or "lifetime" when `endsAt` is null. Uses `endsAt` and `expired` on `GET /me/live-exam-access` (`modules` and `exams`).
- An expired exam offers a re-buy, not "already bought". Uses `expired` on the access list and the catalog's buy state (`NONE` once expired).
- Staff access list (`GET /students/{id}/live-exam-access`) shows `endsAt` and `expired` per row. A free exam no longer appears there.
- Staff grant form: an exam grant no longer needs a package. `moduleId` may be left out on `POST /live-exam-grants`.
- Staff orders list and detail show `listPrice` and `discountPercent`.
- An exam order or exam row can show a package that is no longer its own, because the package is only the primary one for display. Do not use it for access.
