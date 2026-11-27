# W2: groups, payees and the entry forms (progress note)

Built (web only):
- API hooks: `web/src/lib/api/accounting/payees.ts` (group payees, one payee, save, archive or restore, last prices, linkable users) and `entries.ts` (entries list, create one, create several, copy source). Routes exactly as contract 3.1 and 3.2 name them.
- `web/src/components/staff/accounting/entries/`: `entry-form.tsx` (one form for the four kinds: counted line live, last-price prefill, paid now, month and description rules, future date refused, server refusals shown), `quick-add.tsx` (kind, group, person, form; `groupsFor` filters the groups a kind can go into), `several.tsx` (B6), `copy-month.tsx` (B7; `draftInput` strips `sourceEntryId` and `alreadyThisMonth`), `exam-picker.tsx` (B4, from `/live-exams`).
- `web/src/components/staff/accounting/payees/payee-form.tsx`: add or change a person (name, phone, note, other spellings for the Offline instructors group only, linked site user from `/users/linkable`).
- `web/src/components/staff/accounting/group-page.tsx`: Payees mode (totals, search, Show archived, Add person, Add for several, Copy last month, table of people) and Log mode (filters, paging, total, Add spending or income, Copy last month).
- `web/src/components/staff/accounting/payee-page.tsx`: header, summary, buttons (Add charge, Record payment, Statement link to the W4 page, Edit, Archive or Restore with the owed or advance warning), Entries tab with the balance after each entry (computed from the current balance, only when every entry is loaded), Details tab (other spellings, linked user, "Also on Staff pay" link to `/staff/pay/<userId>`).
- `balance-text.tsx`: Owed (amber), Advance (blue), Settled (muted).
- `accounting-model.ts` additions: `parseAmount`, `parseQuantity`, `countedAmount` (half up to the paisa, shown live only), `monthOf`, `shiftMonth`, `monthLabel`, `unitWithCount`.
- The frame now shows a "+ Add" button on every accounting page (B14 header button; no fixed bottom bar yet).
- Routes: `app/(staff)/staff/accounting/groups/[groupId]/page.tsx`, `payees/[payeeId]/page.tsx`.
- i18n: `en` and `bn` `accounting.ts` rewritten in full, with all W1 keys and the W2 keys. Every literal `accounting.*` key used by the code exists in both files (checked by script; the only unlisted ones are dynamic `kind.${…}` prefixes).

Fixes found by the tests (for the record):
- The payment form had no amount field (the amount only showed once a type was chosen). Fixed: a payment always shows the amount.
- The paid-now amount fell back to the charge amount when cleared, so it could not be typed over. Now it is set once when the switch turns on.

Tests (`web/tests/accounting`): `entry-form.test.tsx` (9), `group-page.test.tsx` (4), `payee-page.test.tsx` (5), `several.test.tsx` (2), `copy-month.test.tsx` (3). With W1: 48 tests in 8 files pass (`npx vitest run tests/accounting`). `npx tsc --noEmit -p .` passes.

Open questions for the orchestrator:
1. Contract 3.1 says `GET /payees/{id}/last-prices` answers one row per type. The form reads `unitPrice` (null for a moved legacy charge; the form then leaves the price empty). No change needed, noted.
2. The payee page's Entries tab reads `GET /entries?payeeId=&size=200` (contract 3.2, R's list). It runs the running balance only when the list is complete (total at most 200).
3. The Staff pay link targets `/staff/pay/<userId>`, the existing person page (`app/(staff)/staff/pay/[userId]`).
4. Statement link `/staff/accounting/payees/<id>/statement` is built in W4; until then it is a 404.
5. Sheets (quick add, several, copy) use the shared `Sheet` component. Full height below 640 px depends on that component; not checked on a phone yet (browser check is at gate 3).

Routes called in W2 (new to this slice): `GET /groups/{id}/payees`, `GET /payees/{id}`, `POST /groups/{id}/payees`, `PATCH /payees/{id}`, `POST /payees/{id}/archive|restore`, `GET /payees/{id}/last-prices`, `GET /users/linkable`, `POST /entries`, `POST /entries/many`, `GET /groups/{id}/copy-source`, `GET /entries` (R, for the group Log list and the payee Entries tab). Also the existing `GET /api/v1/offline-batches` (batch picker) and `GET /api/v1/live-exams` (exam picker) from the exam API, not the accounting contract.
