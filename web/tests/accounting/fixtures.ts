import type { AccType, Group, MoneyAccount, PayeeRow, Entry } from "@/lib/api/accounting/types";
import { ACC_ACCOUNT_IDS, ACC_GROUP_IDS } from "@/lib/api/accounting/ids";

/*
 * Fixtures for the accounting tests (W). They follow docs/accounting/orchestration/contracts.md section 2 and the seed of plan 5.3.
 * Money is a string with two decimals; the API answers the same shapes the real API will.
 */

export function group(overrides: Partial<Group> = {}): Group {
  return {
    id: "g-test",
    nameEn: "Test group",
    nameBn: null,
    mode: "LOG",
    direction: "EXPENSE",
    systemKey: null,
    inFinance: true,
    position: 0,
    archived: false,
    version: 0,
    payeeCount: 0,
    owed: "0.00",
    advance: "0.00",
    thisMonth: "0.00",
    ...overrides,
  };
}

/** The five built-in groups, in seed order (plan 4.1). */
export const GROUPS: Group[] = [
  group({
    id: ACC_GROUP_IDS.OFFLINE_INSTRUCTORS,
    nameEn: "Offline instructors",
    nameBn: "অফলাইন শিক্ষক",
    mode: "PAYEES",
    systemKey: "OFFLINE_INSTRUCTORS",
    position: 0,
    payeeCount: 2,
    owed: "3000.00",
  }),
  group({ id: ACC_GROUP_IDS.STAFF, nameEn: "Staff", nameBn: "কর্মী", mode: "PAYEES", systemKey: "STAFF", position: 1 }),
  group({ id: ACC_GROUP_IDS.SUPPLIERS, nameEn: "Suppliers", nameBn: "সরবরাহকারী", mode: "PAYEES", systemKey: "SUPPLIERS", position: 2 }),
  group({
    id: ACC_GROUP_IDS.PERSONAL,
    nameEn: "Personal",
    nameBn: "ব্যক্তিগত",
    mode: "LOG",
    systemKey: "PERSONAL",
    inFinance: false,
    position: 3,
  }),
  group({
    id: ACC_GROUP_IDS.OTHER_INCOME,
    nameEn: "Other income",
    nameBn: "অন্যান্য আয়",
    mode: "LOG",
    direction: "INCOME",
    systemKey: "OTHER_INCOME",
    position: 4,
  }),
];

export function type(overrides: Partial<AccType> = {}): AccType {
  return {
    id: "t-test",
    groupId: ACC_GROUP_IDS.OFFLINE_INSTRUCTORS,
    nameEn: "Lecture",
    nameBn: "ক্লাস",
    kind: "COUNTED",
    unitEn: "class",
    unitBn: "ক্লাস",
    needsPeriod: false,
    needsDescription: false,
    position: 0,
    archived: false,
    used: false,
    version: 0,
    ...overrides,
  };
}

export const LECTURE = type({ id: "a0000000-0000-4000-8000-000000000111" });
export const OTHER_TYPE = type({
  id: "a0000000-0000-4000-8000-000000000117",
  nameEn: "Other",
  nameBn: "অন্যান্য",
  kind: "AMOUNT",
  unitEn: null,
  unitBn: null,
  needsDescription: true,
  position: 6,
});

export const ACCOUNTS: MoneyAccount[] = [
  { id: ACC_ACCOUNT_IDS.CASH, name: "Cash box", kind: "CASH", note: null, position: 0, archived: false, version: 0 },
  { id: ACC_ACCOUNT_IDS.BKASH, name: "bKash", kind: "BKASH", note: null, position: 1, archived: false, version: 0 },
  { id: ACC_ACCOUNT_IDS.NAGAD, name: "Nagad", kind: "NAGAD", note: null, position: 2, archived: false, version: 0 },
  { id: ACC_ACCOUNT_IDS.ROCKET, name: "Rocket", kind: "ROCKET", note: null, position: 3, archived: false, version: 0 },
  { id: ACC_ACCOUNT_IDS.BANK, name: "Bank", kind: "BANK", note: null, position: 4, archived: false, version: 0 },
];

export const MILON_PAYEE: PayeeRow = {
  id: "p-milon",
  groupId: ACC_GROUP_IDS.OFFLINE_INSTRUCTORS,
  groupNameEn: "Offline instructors",
  groupNameBn: "অফলাইন শিক্ষক",
  name: "Milon",
  phone: "01711000111",
  balance: "2000.00",
  paidThisMonth: "0.00",
  lastPaymentOn: null,
  lastPaymentAmount: null,
  archived: false,
};

export function entry(overrides: Partial<Entry> = {}): Entry {
  return {
    id: "e-test",
    no: 1042,
    kind: "PAYMENT",
    status: "ACTIVE",
    groupId: ACC_GROUP_IDS.OFFLINE_INSTRUCTORS,
    groupNameEn: "Offline instructors",
    groupNameBn: "অফলাইন শিক্ষক",
    direction: "EXPENSE",
    payeeId: MILON_PAYEE.id,
    payeeName: "Milon",
    typeId: null,
    typeNameEn: null,
    typeNameBn: null,
    typeKind: null,
    unitEn: null,
    unitBn: null,
    date: "2026-11-20",
    period: null,
    quantity: null,
    unitPrice: null,
    amount: "1000.00",
    accountId: ACC_ACCOUNT_IDS.BKASH,
    accountName: "bKash",
    reference: null,
    counterparty: null,
    batchId: null,
    batchNameEn: null,
    batchNameBn: null,
    liveExamId: null,
    liveExamTitleEn: null,
    liveExamTitleBn: null,
    description: null,
    note: null,
    pairedEntryId: null,
    legacy: null,
    recordedByName: "Master Admin",
    recordedAt: "2026-11-20T04:00:00Z",
    correctedAt: null,
    voidedAt: null,
    voidedByName: null,
    voidReason: null,
    version: 0,
    ...overrides,
  };
}
