import type { AccType, Entry, EntryPage, LastPrice, Payee, PayeeRow } from "@/lib/api/accounting/types";
import { ACC_ACCOUNT_IDS, ACC_GROUP_IDS } from "@/lib/api/accounting/ids";
import { MILON_PAYEE, GROUPS, LECTURE, entry, type } from "./fixtures";

/*
 * Fixtures for the entry, payee and group tests (W2): the people of the Offline instructors group, the types that need a unit or a
 * month or a description, last prices, and the pages the list endpoints answer.
 */

export const TUHIN_PAYEE: PayeeRow = {
  ...MILON_PAYEE,
  id: "p-tuhin",
  name: "Tuhin Hossain",
  phone: null,
  balance: "0.00",
  archived: false,
};

/** Counted, with the unit "answer sheet" (plan 4.2, the seeded Answer sheet evaluation type). */
export const ANSWER_SHEET: AccType = type({
  id: "a0000000-0000-4000-8000-000000000113",
  nameEn: "Answer sheet evaluation",
  nameBn: "উত্তরপত্র মূল্যায়ন",
  kind: "COUNTED",
  unitEn: "answer sheet",
  unitBn: "উত্তরপত্র",
  position: 2,
});

/** A plain amount that needs a month (the shape of Festival bonus or Monthly salary). */
export const MONTHLY: AccType = type({
  id: "a0000000-0000-4000-8000-000000000119",
  nameEn: "Monthly salary",
  nameBn: "মাসিক বেতন",
  kind: "AMOUNT",
  unitEn: null,
  unitBn: null,
  needsPeriod: true,
  position: 8,
});

/** A plain amount that needs a description (the seeded Other). */
export const OTHER_AMOUNT: AccType = type({
  id: "a0000000-0000-4000-8000-000000000117",
  nameEn: "Other",
  nameBn: "অন্যান্য",
  kind: "AMOUNT",
  unitEn: null,
  unitBn: null,
  needsDescription: true,
  position: 6,
});

/** The offline group's types, as the settings and the forms read them. */
export const OFFLINE_TYPES: AccType[] = [LECTURE, ANSWER_SHEET, MONTHLY, OTHER_AMOUNT];

/** Milon's newest charge of the answer sheet type: 40 sheets at 50. */
export const LAST_PRICES_MILON: LastPrice[] = [
  { typeId: ANSWER_SHEET.id, quantity: "40", unitPrice: "50.00", amount: "2000.00", date: "2026-11-01" },
];

export const EMPTY_LIVE_EXAMS = { content: [], page: 0, size: 200, totalElements: 0, totalPages: 1 };

/** A page of entries as the list answers it. */
export function entryPage(items: Entry[], overrides: Partial<EntryPage> = {}): EntryPage {
  return {
    items,
    page: 0,
    size: 50,
    total: items.length,
    totals: { charged: "0.00", paid: "0.00", spent: "0.00", income: "0.00" },
    ...overrides,
  };
}

/** A payee with the fields the payee page reads (the list rows carry fewer). */
export function payeeOf(overrides: Partial<Payee> = {}): Payee {
  return {
    ...MILON_PAYEE,
    note: "bKash 017…, pays on Fridays",
    aliases: ["Milon Hossain"],
    userId: null,
    userName: null,
    staffPayBalance: null,
    totalCharged: "2500.00",
    totalPaid: "500.00",
    paymentsCount: 1,
    batches: [{ id: "batch-1", nameEn: "BJS Foundation", nameBn: "বিজেএস ফাউন্ডেশন" }],
    createdAt: "2026-11-01T04:00:00Z",
    version: 2,
    ...overrides,
  };
}

/** The Payees group of the offline instructors, and the Log group for tests of the Log page. */
export const OFFLINE_GROUP = GROUPS[0];
export const PERSONAL_GROUP = GROUPS[3];
export const OFFLINE_ID = ACC_GROUP_IDS.OFFLINE_INSTRUCTORS;
export const CASH_ID = ACC_ACCOUNT_IDS.CASH;

/** A charge and a payment of Milon, newest first (the order the list answers). */
export const MILON_ENTRIES: Entry[] = [
  entry({ id: "e-pay", no: 1043, kind: "PAYMENT", amount: "500.00", date: "2026-11-20", accountName: "bKash", accountId: ACC_ACCOUNT_IDS.BKASH }),
  entry({
    id: "e-charge",
    no: 1042,
    kind: "CHARGE",
    amount: "2500.00",
    date: "2026-11-10",
    accountId: null,
    accountName: null,
    typeId: ANSWER_SHEET.id,
    typeNameEn: ANSWER_SHEET.nameEn,
    typeNameBn: ANSWER_SHEET.nameBn,
    typeKind: "COUNTED",
    unitEn: "answer sheet",
    unitBn: "উত্তরপত্র",
    quantity: "50",
    unitPrice: "50.00",
  }),
];
