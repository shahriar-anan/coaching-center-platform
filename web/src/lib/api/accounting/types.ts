// Accounting API types: copied from docs/accounting/orchestration/contracts.md, section 2, as is (W1). Change the contract, not this file.
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
