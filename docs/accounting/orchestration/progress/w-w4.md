# W4: print pages, the rest of the site, clean-up (progress note)

Built (web only):
- Voucher (`components/staff/accounting/print/voucher.tsx`, route `entries/[entryId]/voucher`): A5 sheet on A4 (`@page` rule), centre name and the site logo as the fee receipt shows them, title ("Payment voucher", "Spending voucher", "Money receipt"), number, date, paid to, group, type, quantity × price, account and reference, description, "Corrected on" if any, the amount in figures and in words, "Received by" and "Paid by" lines. A voided voucher carries VOID across it. A charge has no voucher (a message, not a blank page). Print button hidden when printing.
- Statement (`print/statement.tsx`, route `payees/[payeeId]/statement`): A4, the payee, the range (URL `from` and `to`, default this month; the fields are not printed), the opening balance, each active entry with the balance after it, totals, closing balance, "Printed on". Hook `usePayeeStatement` in `lib/api/accounting/payees.ts`.
- `amount-words.ts`: `amountInWords(amount, locale)`, Indian grouping (lakh, crore), paisa after the taka, "only" and "মাত্র". English and Bangla word tables.
- Finance: two sources, `ACCOUNTS_INCOME` (in the income list, after the online sources) and `ACCOUNTS_EXPENSE` (in the expense list, after the offline instructors), in `lib/api/exam/finance.ts`; labels in `finance.ts` (en and bn, source names and the `accounts` stream group); colours `--fin-src-7` and `--fin-src-8` in all three palette blocks (light, dark media, data-theme dark) and in `SOURCE_COLOR`; the entries page lists the accounting streams in their own group. The instructors table and batch cost are unchanged.
- Payment records: `CLASS` and `OTHER` removed from the kinds (`PaymentKind` is now `FEE` | `DIRECT`; `RecordKind` keeps `STAFF`); the instructor filter, the instructor list query and the instructor request parameter are removed; a System Admin sees fees and online purchases only; the batch filter applies to fees only. `payment-records.ts` shapes are otherwise unchanged.
- Routine preview text for unknown names (plan 4.10): the sentence is in the `batchPay` namespace (`batchPay.preview.newInstructors`), not `staffOffline` as the spec says. Both languages now point to Accounting → Offline instructors.
- Audit log: the page maps a fixed set of action codes to labels, so the 20 `ACC_*` actions of plan 6.3 are added to that set and to `staffOps.audit.action` in en and bn.
- Old instructor pay web code removed: `components/staff/instructor-pay/*` (7 files), `lib/api/exam/instructor-pay.ts`, the `instructorPay` i18n namespace (en and bn files and their registration in `messages/index.ts`), `tests/instructor-pay/*`. The old `offline-instructors` and `offline-instructor-payments` pages are the redirects from W1. `tests/instructor-pay/notifications.test.ts` moved to `tests/staff-notifications/offline-classes-text.test.ts` (its notification text lives in `staffNotifications`, which stays); its last block compared the removed namespace with itself, so it is gone.
- `waiting.instructorPayments` removed from the nav labels in W1.

Left in place on purpose:
- `web/src/lib/api/exam/offline-batch-pay.ts` and the `batchPay` namespace: they serve the batch Classes and routines pages (class statuses such as PAID and NOT_TAKEN, the routine upload, the instructor picker `GET /offline-instructors`). Nothing in them is only for pay that I could safely remove; left as is.
- `GET /api/v1/offline-instructors` (the routine and class picker) is still called by `offline-batch-pay.ts`; the contract keeps it.
- `paymentRecords.kind.CLASS` and `OTHER` labels and `paymentRecords.item.*` keys remain in the message files (harmless; the rows no longer come).

Tests (`web/tests/accounting`): `print.test.tsx` (4: voucher fields and amount in words, VOID mark, charge refusal, statement running balance and range), `amount-words.test.ts` (5). Accounting total: 75 tests in 14 files, all pass.
Updated for W4: `tests/staff-offline-admission/payment-records.test.tsx` (instructor rows and filter removed; the money-out source list is the staff option only). 10 of 10 pass.

Checks:
- `npx tsc --noEmit -p .`: passes.
- `npx eslint` on every accounting file and every file I changed (`src/components/staff/accounting`, `src/lib/api/accounting`, `src/app/(staff)/staff/accounting`, `tests/accounting`, `tests/staff-notifications`, `src/components/staff/finance`, `src/components/staff/payment-records`, `src/components/shell/staff-nav.ts`, `src/i18n/messages`, `src/components/staff/ops/ops-copy.ts`): clean.
- `npm run lint` on the whole web project: 4 errors and 12 warnings, all in files this branch does not touch (`components/staff/catalog/roster-tab.tsx`, `components/student/catalog/my-exams-page.tsx`, `components/staff/notifications/notifications-page.tsx`, `components/staff/offline/admission-photo.tsx`, the student admission photo files, `tests/stu-lms/written-states.test.tsx`). They are the baseline of this branch, not caused by W1 to W4.
- Full `npx vitest run`: see the report.

Open questions for the orchestrator:
1. Routine text location: the spec said `staffOffline`; the sentence is in `batchPay`. Changed there.
2. The statement and voucher routes depend on `GET /payees/{id}/statement` and `GET /entries/{id}` (contract, R and L). Built against the contract, not checked against the real API.
3. The Finance colours (`--fin-src-7`, `--fin-src-8`) are my choice (brown and teal). Shahriar may want other ones; they are three lines in `finance-theme.tsx`.
4. The payment records kind labels for CLASS and OTHER stay in the messages; say if you want them removed too.
