# W1: foundation and Settings (progress note)

Built (web only):
- `web/src/lib/api/accounting/`: `types.ts` (contract section 2, copied from the contract file as is), `ids.ts` (seeded group and account ids, plan 5.3), `query.ts` (query keys, write helper that invalidates `accounting` and `finance`), `groups.ts`, `account-types.ts`, `accounts.ts` (contract 3.1 routes for groups, types, accounts, and their orders).
- `web/src/i18n/messages/{en,bn}/accounting.ts`, registered in `messages/index.ts` as `accounting`. Every refusal key of contract section 5 has en and bn text. `common.staffNav.accounting` replaces the two old menu labels; `waiting.instructorPayments` removed (no nav key uses it now).
- `web/src/components/staff/accounting/`: `accounting-model.ts` (names, money, balance side, list moves, write error text), `accounting-frame.tsx` (Master Admin gate with the "for the Master Admin" message, title row, sub-navigation with one link per live group), `accounting-home.tsx` (placeholder list of groups until W3), `settings/` (groups, types and money accounts tabs).
- Routes: `app/(staff)/staff/accounting/page.tsx`, `.../accounting/settings/page.tsx`.
- Menu: `staff-nav.ts` drops `instructorPayments` and `offlineInstructors`; adds `accounting` (money group, Master Admin only, before Finance, lucide `Calculator`).
- Old addresses now redirect (server components): `offline-instructors/page.tsx` to the Offline instructors group, `offline-instructors/[id]/page.tsx` to `/staff/accounting/payees/<id>`, `offline-instructor-payments/page.tsx` to the entries of that group.
- Old instructor-pay components and their API file are still in place (removed in W4).

Tests:
- `tests/accounting/settings.test.tsx` (9), `nav.test.tsx` (7), `model.test.ts` (9): 25 passed.
- `tests/instructor-pay/nav.test.tsx` deleted: it asserted the two removed menu items. The other old tests in `tests/instructor-pay/` and the nav tests under `tests/s1-b`, `tests/staff-offline-admission`, `tests/staff-ui-clean` still pass (49 tests in those six files).
- `npx tsc --noEmit -p .`: passes.
- Not run yet: `npm run lint` and the full `npx vitest run` (planned for the end, W4).

Open questions for the orchestrator:
1. The contract gives no fields for money account writes. W1 sends `{ name, kind, note }` (plus `version` on a change) to `POST /accounts` and `PATCH /accounts/{id}`. Please confirm or send the shape.
2. The group links in the sub-navigation point at `/staff/accounting/groups/{id}`, which is the W2 page. Until W2 lands, those links and the Offline instructors redirect lead to a 404.
3. Bangla money account names: a seeded account (name equal to its seeded English name) shows the Bangla name for its kind; an account renamed by the admin shows the name as typed. In the Accounts list, the kind line is hidden when it would repeat the name.
4. Accounting sits before Finance at the top of the money group. The plan says "one Accounting item in the money group" without an order.

Routes called in W1: `GET /api/v1/accounting/groups`, `POST /groups`, `PATCH /groups/{id}`, `POST /groups/{id}/archive`, `/restore`, `POST /groups/order`, `GET /groups/{id}/types`, `POST /groups/{id}/types`, `PATCH /types/{id}`, `POST /types/{id}/archive`, `/restore`, `POST /groups/{id}/types/order`, `GET /accounts`, `POST /accounts`, `PATCH /accounts/{id}`, `POST /accounts/{id}/archive`, `/restore`, `POST /accounts/order`.
