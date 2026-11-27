# Workstream W: the web (phase 2)

Every accounting screen of plan 4.6, mobile first, in English and Bangla. Read [README.md](README.md), [contracts.md](contracts.md), and
plan sections 3, 4 (all), 4.9, 4.10. You work against the contract: tests mock `apiRequest` with fixtures, as
`web/tests/instructor-pay/fixtures.ts` does. The real API arrives in phase 3.

Follow the existing staff pages, do not invent a new style: `web/src/components/staff/finance/*` (cards, chart, filter bar, tabs),
`web/src/components/staff/instructor-pay/*` (forms, history, Master-Admin-only page), `web/src/components/ui/*`, the money and date
helpers those pages use, and the i18n pattern in `web/src/i18n/messages/{en,bn}` registered in `messages/index.ts`.

## Layout

| Path | What |
|---|---|
| `web/src/lib/api/accounting/types.ts` | Contracts section 2, copied as is. |
| `web/src/lib/api/accounting/*.ts` | React Query hooks per area (groups, types, accounts, payees, entries, reports), query keys starting `["accounting", …]`; mutations invalidate `["accounting"]` and `["finance"]`. |
| `web/src/i18n/messages/{en,bn}/accounting.ts` | Every string, including `errors.<key>` for each refusal key of contracts section 5. Bangla per plan section 3 and `natural-bangla-copy`. |
| `web/src/components/staff/accounting/**` | Components. |
| `web/src/app/(staff)/staff/accounting/**` | Routes of plan 4.6. |
| `web/tests/accounting/**` | Tests and fixtures. |

## W1. Foundation and Settings

- Types, hooks, i18n namespace, a money helper for `Money` strings (`৳1,250`, Bangla digits on the Bangla site as the site already does).
- Menu: replace the `instructorPayments` and `offlineInstructors` items in `staff-nav.ts` with one `accounting` item (group `money`,
  `MASTER_ADMIN: "full"`, a fitting lucide icon). Check `staff-sidebar.tsx`, `staff-shell.tsx` and `common.ts` for other references.
- Redirects (plan 4.6): `/staff/offline-instructors` → the group page of the group whose `systemKey` is `OFFLINE_INSTRUCTORS` (look it up
  with `GET /groups`, or use the fixed id `a0000000-0000-4000-8000-000000000001`); `/staff/offline-instructors/[id]` →
  `/staff/accounting/payees/[id]`; `/staff/offline-instructor-payments` → `/staff/accounting/entries?groupId=<that id>`.
- A shared accounting layout: section title, sub-navigation (Home, Entries, Reports, Settings, then one link per live group in order),
  the "for the Master Admin" message for anyone else (as the instructor pay pages do).
- **Settings** page, three tabs (plan 4.6): groups (add with mode and direction, rename en/bn, Count in Finance switch, order, archive
  and restore; Offline instructors has no archive), types per group (kind, unit en/bn shown for Counted, needs month, needs
  description; kind locked when `used`), money accounts.
- Tests `tests/accounting/settings.test.tsx`, `nav.test.tsx` (menu item for Master Admin only; old items gone; redirects).

## W2. Groups, payees and the entry forms

- **Group page** (both modes, plan 4.6): list, search, "Show archived", group totals; Payees: Add payee, Add for several payees, Copy
  last month; Log: entries with filters and total, Add spending or income, Copy last month.
- **Payee page**: header, summary (Owed/Advance in colour: owed amber, advance blue, zero muted), buttons, Entries tab with running
  balance, Details tab (other spellings only for Offline instructors; linked user picker with `GET /users/linkable`, and the
  "Also on Staff pay: balance ৳X" link to that person's Staff pay page; find its route under `app/(staff)/staff/pay`).
- **Entry forms** (one component, by kind): fields and rules of plan 4.4. Counted types show quantity and unit price with the unit
  ("answer sheets") and a live line "40 answer sheets × ৳50 = ৳2,000"; the amount field is hidden for Counted. Unit price and amount
  start from `GET /payees/{id}/last-prices`; the money account starts from the last one used (remember it in `localStorage`). "Paid
  now" switch on charges. Month field when the type needs it, defaulting to the date's month. Batch and online exam pickers (reuse the
  batch picker of the old record-payment form; exam picker from the existing staff exam list API).
- **Quick add** (+ Add on home and every accounting page): kind → group (only fitting ones) → payee (search `GET /payees?q=`) → form.
- **Add for several payees**: type, date, month, description, note once; tick payees; a line each with quantity and price (or amount)
  prefilled from each payee's last price; running total; saves with `POST /entries/many`.
- **Copy last month**: `GET /groups/{id}/copy-source`, a ticked list with editable amounts, saves with `POST /entries/many`. Drafts
  with `alreadyThisMonth` start unticked with the note "Looks already added this month". Strip `sourceEntryId` and `alreadyThisMonth`
  before posting.
- **Archive a payee** with a balance that is not zero: confirm first ("Rahim is still owed ৳2,000. Archive anyway?").
- Mobile first (plan 4.6): sheets full height below 640 px, `inputMode="decimal"`, the Save bar fixed at the bottom, lists as cards.
- Refusals: show `t("errors." + key)` from the 409 message.
- Tests: `group-page.test.tsx`, `payee-page.test.tsx`, `entry-form.test.tsx` (counted maths shown; last price prefill; paid now; needs
  month and description; future date), `several.test.tsx`, `copy-month.test.tsx`.

## W3. Entries, entry page, home, reports

- **Entries page**: filters in the URL query (shareable), totals, paging, Download CSV (the same query on `/entries.csv`), downloaded
  the way `downloadFinanceEntriesCsv` in the Finance entries page does it (authenticated fetch, blob, file name from the header).
- **Entry page**: all fields, paired entry link, corrections list (before → after with field labels), Edit (the form plus a required
  reason), Void (reason; "Also void the payment made with it?" ticked when there is an active paired entry), Voucher button.
- **Home**: cards, groups row, owed the most, latest entries, 12-month chart (reuse the Finance chart component and its theme).
- **Reports**: three tabs (Owed now, By month, Group summary), each with Print and CSV (build the CSV in the browser from the answer,
  with a BOM).
- Tests: `entries.test.tsx`, `entry-page.test.tsx` (edit needs a reason; void with paired), `home.test.tsx`, `reports.test.tsx`.

## W4. Print pages, the rest of the site, clean-up

- **Voucher** and **Statement** print pages (plan 4.6): a print stylesheet (A5 voucher, A4 statement), no app chrome when printing, a
  Print button on screen. Amount in words in English (lakh and crore: "Twelve thousand five hundred taka only") and in Bangla
  ("বারো হাজার পাঁচশত টাকা মাত্র"), helper `amountInWords(amount, locale)` with unit tests. Centre name and logo as the fee receipt
  shows them.
- **Finance** web: labels, colours and filter entries for `ACCOUNTS_EXPENSE` and `ACCOUNTS_INCOME` (finance-model and i18n `finance`);
  the instructors table and batch cost keep working.
- **Payment records** web: remove the instructor kinds, their filter and their edit dialog paths (R's report says exactly what went).
- **Routine preview** text for unknown names (plan 4.10), in `staffOffline` en and bn.
- **Audit log page**: if it maps action names to labels, add the `ACC_*` actions of plan 6.3 (en and bn); otherwise nothing.
- **Other links** to the old pages (staff notifications, the offline batch Classes page's instructor links, the staff home): point them
  at the accounting pages; the redirects remain as a safety net. Find them with
  `git grep -n "offline-instructor" -- web/src`; the files on 27 Nov were `staff-nav.ts`, `payment-records-page.tsx`, `common.ts`
  (en, bn), `messages/index.ts`, `lib/api/exam/offline-batch-pay.ts` and the instructor-pay files.
- **Remove** the old instructor pay web code once nothing imports it: `components/staff/instructor-pay/*`, `lib/api/exam/instructor-pay.ts`,
  the `instructorPay` i18n namespace, `app/(staff)/staff/offline-instructor-payments` and `offline-instructors` pages (now redirects),
  `tests/instructor-pay/*`. Check `lib/api/exam/offline-batch-pay.ts` and `batchPay` i18n: remove only what serves pay; keep what the
  batch Classes page still uses. The offline instructor **picker** used by the routine and classes stays (L may have trimmed its
  answer; follow L's report).
- Final: `npm run lint`, `npx tsc --noEmit -p .`, full `npx vitest run` (if tests are allowed).
- Tests: `print.test.tsx` (voucher fields, VOID mark, statement running balance), `amount-words.test.ts`, Finance and payment records
  tests updated.

## Done when

W1 to W4 committed with progress notes; typecheck and lint pass; the accounting tests pass (if allowed); the report lists every place
the web still calls a route the contract does not have.
