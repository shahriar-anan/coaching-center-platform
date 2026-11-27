# Accounting: the shared contract

Every route and JSON shape the API agents (L, R) serve and the web agent (W) calls. Written 27 Nov 2026. If a shape here is wrong,
say so in your report and keep to it; only the orchestrator changes this file.

## 1. Conventions

- Base path `/api/v1/accounting`. Every route needs the `ACCOUNTING` permission (Master Admin); others get 403.
- Money is a **string** with two decimals (`"1250.00"`); a deduction charge is negative (`"-500.00"`). Quantities are strings too
  (`"40"`, `"1.5"`). Dates are `YYYY-MM-DD` (Dhaka days), months `YYYY-MM`, instants ISO-8601 UTC.
- Lists that page take `page` (0-based) and `size` (default 50, at most 200) and answer `{ items, page, size, total, ... }`.
- Writes of an existing row send its `version`; a stale one is 409 `STALE_VERSION` (the code `OfflineInstructorService` already uses).
- The API **accepts** money and quantities as a JSON string or number, and always **answers** strings. Money is answered with two
  decimals; quantities without trailing zeros (`stripTrailingZeros().toPlainString()`: `"40"`, `"1.5"`).
- Unknown ids answer 404 `RESOURCE_NOT_FOUND`.
- Errors use the existing `ApiException(ErrorCode, HttpStatus, message)` and body (`code`, `message`, field details).
- Refusals: 409 `{ "code": "ACC_REFUSED", "message": "accounting.errors.<key>" }`. Keys in section 5.
- Field errors: 400 as the rest of the API (`VALIDATION_FAILED` with field messages).
- Archived rows are hidden from lists unless `archived=true` is passed.
- Bangla names may be null; the web shows the English one then.

## 2. Types (copy into `web/src/lib/api/accounting/types.ts` as is)

```ts
export type Money = string;
export type GroupMode = "PAYEES" | "LOG";
export type Direction = "EXPENSE" | "INCOME";
export type SystemKey = "OFFLINE_INSTRUCTORS" | "STAFF" | "SUPPLIERS" | "PERSONAL" | "OTHER_INCOME";
export type TypeKind = "COUNTED" | "AMOUNT" | "DEDUCTION";
export type AccountKind = "CASH" | "BKASH" | "NAGAD" | "ROCKET" | "BANK";
export type EntryKind = "CHARGE" | "PAYMENT" | "SPEND" | "INCOME";
export type EntryStatus = "ACTIVE" | "VOIDED";

export type Group = {
  id: string; nameEn: string; nameBn: string | null; mode: GroupMode; direction: Direction; systemKey: SystemKey | null;
  inFinance: boolean; position: number; archived: boolean; version: number;
  payeeCount: number;   // Payees groups; 0 for Log
  owed: Money;          // sum of positive balances (Payees); "0.00" for Log
  advance: Money;       // sum of negative balances as a positive number (Payees); "0.00" for Log
  thisMonth: Money;     // active payments (Payees) or spending/income (Log) dated this Dhaka month
};

export type AccType = {
  id: string; groupId: string; nameEn: string; nameBn: string | null; kind: TypeKind;
  unitEn: string | null; unitBn: string | null; needsPeriod: boolean; needsDescription: boolean;
  position: number; archived: boolean; used: boolean; version: number;   // used: an entry has this type (kind is then fixed)
};

export type MoneyAccount = { id: string; name: string; kind: AccountKind; note: string | null; position: number; archived: boolean; version: number };

export type PayeeRow = {
  id: string; groupId: string; groupNameEn: string; groupNameBn: string | null; name: string; phone: string | null;
  balance: Money;       // > 0 owed, < 0 advance
  paidThisMonth: Money; lastPaymentOn: string | null; lastPaymentAmount: Money | null; archived: boolean;
};

export type Payee = PayeeRow & {
  note: string | null;
  aliases: string[];                       // Offline instructors group only; [] otherwise
  userId: string | null; userName: string | null;
  staffPayBalance: Money | null;           // the linked user's staff_ledger balance; null when not linked
  totalCharged: Money; totalPaid: Money; paymentsCount: number;
  batches: { id: string; nameEn: string; nameBn: string | null }[];   // instructors: batches with a class of theirs not cancelled
  createdAt: string; version: number;
};

export type LastPrice = { typeId: string; quantity: string | null; unitPrice: Money | null; amount: Money; date: string };

export type Entry = {
  id: string; no: number; kind: EntryKind; status: EntryStatus;
  groupId: string; groupNameEn: string; groupNameBn: string | null; direction: Direction;
  payeeId: string | null; payeeName: string | null;
  typeId: string | null; typeNameEn: string | null; typeNameBn: string | null; typeKind: TypeKind | null;
  unitEn: string | null; unitBn: string | null;
  date: string; period: string | null; quantity: string | null; unitPrice: Money | null; amount: Money;
  accountId: string | null; accountName: string | null; reference: string | null; counterparty: string | null;
  batchId: string | null; batchNameEn: string | null; batchNameBn: string | null;
  liveExamId: string | null; liveExamTitleEn: string | null; liveExamTitleBn: string | null;
  description: string | null; note: string | null;
  pairedEntryId: string | null; legacy: "CLASS" | "OTHER" | null;
  recordedByName: string; recordedAt: string; correctedAt: string | null;
  voidedAt: string | null; voidedByName: string | null; voidReason: string | null;
  version: number;
};

export type EntryEdit = { at: string; byName: string; reason: string; before: Record<string, unknown>; after: Record<string, unknown> };
export type EntryDetail = Entry & { paired: Entry | null; edits: EntryEdit[] };

/** One entry to create. The server works out the amount for counted types; `amount` is then ignored. */
export type EntryInput = {
  kind: EntryKind; groupId: string; payeeId?: string | null; typeId?: string | null;
  date: string; period?: string | null; quantity?: string | null; unitPrice?: Money | null; amount?: Money | null;
  accountId?: string | null; reference?: string | null; counterparty?: string | null;
  batchId?: string | null; liveExamId?: string | null; description?: string | null; note?: string | null;
  paidNow?: { accountId: string; amount?: Money | null; reference?: string | null } | null;   // CHARGE only
  sourceEntryId?: string | null;   // set by copy-source drafts; informational
};

/** A draft from "Copy last month". alreadyThisMonth: an active entry this month in the group has the same payee, type and month. */
export type CopyDraft = EntryInput & { sourceEntryId: string; alreadyThisMonth: boolean };

export type EntryPatch = Omit<EntryInput, "kind" | "groupId" | "payeeId" | "paidNow" | "sourceEntryId"> & { reason: string; version: number };

export type EntryPage = {
  items: Entry[]; page: number; size: number; total: number;
  totals: { charged: Money; paid: Money; spent: Money; income: Money };   // active rows matching the filters, all pages
};

export type Dashboard = {
  today: string;
  owed: Money; advance: Money;
  spentThisMonth: Money; spentLastMonth: Money; incomeThisMonth: Money;
  groups: { groupId: string; nameEn: string; nameBn: string | null; mode: GroupMode; direction: Direction; owed: Money; thisMonth: Money }[];
  topOwed: PayeeRow[];            // up to 10, balance > 0, highest first
  latest: Entry[];                // up to 10 newest by recordedAt, active and voided
  months: { month: string; expense: Money; income: Money }[];   // 12, oldest first, ending this month
};

export type OwedReport = {
  total: Money; advance: Money;
  groups: { groupId: string; nameEn: string; nameBn: string | null; owed: Money; advance: Money; payees: PayeeRow[] }[];  // balance != 0
};

export type MonthlyReport = {
  months: string[];
  rows: { groupId: string; nameEn: string; nameBn: string | null; direction: Direction; amounts: Money[]; total: Money }[];
  expense: Money[]; income: Money[];
};

export type GroupSummary = {
  groupId: string; from: string; to: string;
  byType: { typeId: string; nameEn: string; nameBn: string | null; kind: TypeKind; unitEn: string | null; unitBn: string | null; quantity: string | null; amount: Money; count: number }[];
  byPayee: { payeeId: string; name: string; charged: Money; paid: Money }[];   // Payees groups only
  charged: Money; paid: Money; spent: Money; income: Money;
};

export type Statement = {
  payee: PayeeRow; from: string; to: string;
  opening: Money; charged: Money; paid: Money; closing: Money;
  lines: (Entry & { balanceAfter: Money })[];   // active entries in the range, oldest first
};
```

## 3. Routes

"Spending" in Expense means money out; "money this month" uses the entry date in Dhaka. Every list hides voided entries from totals.

### 3.1 Agent L

| Method and path | Body / query | Answer |
|---|---|---|
| `GET /groups` | `archived` | `Group[]` by position |
| `POST /groups` | `{ nameEn, nameBn, mode, direction, inFinance }` | `Group` |
| `PATCH /groups/{id}` | `{ nameEn, nameBn, inFinance, version }` | `Group` |
| `POST /groups/{id}/archive`, `/restore` | – | `Group` |
| `POST /groups/order` | `{ ids: string[] }` (every live group) | `Group[]` |
| `GET /groups/{id}/types` | `archived` | `AccType[]` by position |
| `POST /groups/{id}/types` | `{ nameEn, nameBn, kind, unitEn, unitBn, needsPeriod, needsDescription }` | `AccType` |
| `PATCH /types/{id}` | same fields + `version` (kind only while not used) | `AccType` |
| `POST /types/{id}/archive`, `/restore` | – | `AccType` |
| `POST /groups/{id}/types/order` | `{ ids }` | `AccType[]` |
| `GET /accounts` | `archived` | `MoneyAccount[]` |
| `POST /accounts`, `PATCH /accounts/{id}`, `POST /accounts/{id}/archive`, `/restore`, `POST /accounts/order` | as above | `MoneyAccount` / `[]` |
| `GET /groups/{id}/payees` | `q`, `archived` | `PayeeRow[]` by name |
| `GET /payees` | `q` (name or phone, at least 1 character), `limit` (≤ 50) | `PayeeRow[]` of every live group (the quick-add picker) |
| `POST /groups/{id}/payees` | `{ name, phone, note, aliases, userId }` | `Payee` |
| `GET /payees/{id}` | – | `Payee` |
| `PATCH /payees/{id}` | `{ name, phone, note, aliases, userId, version }` | `Payee` |
| `POST /payees/{id}/archive`, `/restore` | – | `Payee` |
| `GET /payees/{id}/last-prices` | – | `LastPrice[]`, one per type, from the newest active charge |
| `GET /users/linkable` | `q` | `{ id, name, role }[]` (System Admins and Instructors, ≤ 20) |
| `POST /entries` | `EntryInput` | `Entry[]` (the entry, then its paired payment if `paidNow`) |
| `POST /entries/many` | `{ entries: EntryInput[] }` (1 to 200, no `paidNow`) | `Entry[]`; all or nothing |
| `GET /groups/{id}/copy-source` | `month` (`YYYY-MM`, default last month) | `CopyDraft[]`: the month's active charges (Payees) or spending (Log), dated today, `period` moved one month on where present, `sourceEntryId` set |
| `GET /entries/{id}` | – | `EntryDetail` |
| `PATCH /entries/{id}` | `EntryPatch` | `Entry` |
| `POST /entries/{id}/void` | `{ reason, alsoVoidPaired }` | `Entry[]` (the entry, and the paired one if voided too) |

### 3.2 Agent R

| Method and path | Query | Answer |
|---|---|---|
| `GET /dashboard` | – | `Dashboard` |
| `GET /entries` | `from`, `to`, `groupId`, `payeeId`, `typeId`, `kind`, `accountId`, `batchId`, `status`, `q`, `sort` (`date`, `-date` default, `amount`, `-amount`, `no`, `-no`), `page`, `size` | `EntryPage` |
| `GET /entries.csv` | same filters, no paging (at most 20,000 rows, else 409 `too_many_rows`) | `text/csv; charset=UTF-8` with a BOM. Columns: No, Date, Kind, Status, Group, Payee, Type, Month, Quantity, Unit, Unit price, Amount, Account, Reference, Paid to, Batch, Exam, Description, Note, Recorded by, Recorded at, Voided reason |
| `GET /reports/owed` | – | `OwedReport` |
| `GET /reports/monthly` | `from`, `to` (`YYYY-MM`, at most 36 months; default the last 12) | `MonthlyReport` |
| `GET /groups/{id}/summary` | `from`, `to` | `GroupSummary` |
| `GET /payees/{id}/statement` | `from`, `to` (default this month) | `Statement` |

R also changes Finance (`/api/v1/finance/**`, new source keys `ACCOUNTS_EXPENSE`, `ACCOUNTS_INCOME`; `OFFLINE_INSTRUCTORS` keeps its
key) and Payment records (no `CLASS` or `OTHER` kinds; no `instructorId` filter). Their JSON shapes are otherwise unchanged.

## 4. Behaviour every agent relies on

- **Balance** of a payee = sum of active charges' `amount` (deductions negative) − sum of active payments' `amount`.
- **Spent** (home, months, group `thisMonth` for expense) = active `PAYMENT` + `SPEND` in Expense groups, by entry date.
  **Income** = active `INCOME`. Home and reports count every group, whatever its `inFinance`; Finance counts only `inFinance` groups.
- Counted amount = `quantity × unitPrice`, `RoundingMode.HALF_UP` to 2 decimals (`AccMoney.counted`).
- Deduction types: the input amount is positive; the stored and answered amount is negative.

## 5. Refusal keys (`accounting.errors.<key>`; W adds en and bn text for each)

`group_archived`, `group_locked` (archiving Offline instructors), `group_mode` (kind does not fit the group's mode or direction),
`name_taken`, `type_archived`, `type_wrong_group`, `type_kind_used` (changing the kind of a used type), `type_kind_mismatch` (an edit
changes the sign), `payee_wrong_group`, `payee_required`, `account_archived`, `account_required`, `future_date`, `period_required`,
`quantity_required`, `price_required`, `description_required`, `amount_too_large`, `paid_now_too_much`, `paid_now_deduction` (paid now on a deduction),
`user_linked` (the user is already linked to another live payee), `voided` (edit or void a
voided entry), `legacy_fields` (a moved class payment: only money fields), `too_many_lines`, `too_many_rows`, `user_not_linkable`,
`alias_taken` (another instructor already has this name or spelling).
