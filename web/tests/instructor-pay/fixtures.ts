import type { BatchView } from "@/lib/api/exam/offline-admin";
import type { InstructorPayment, OfflineInstructor, PayCards, PaySummary } from "@/lib/api/exam/instructor-pay";

export const BATCH: BatchView = {
  id: "batch-1",
  nameEn: "BJS Foundation",
  nameBn: "বিজেএস ফাউন্ডেশন",
  startsOn: null,
  endsOn: "2026-12-31",
  courseFee: 1000,
  defaultInstallments: 6,
  status: "ACTIVE",
  descriptionEn: null,
  descriptionBn: null,
  photoUploaded: false,
  admissionsOpen: true,
  displayOrder: 0,
  defaultClassMinutes: 60,
  studentCount: 12,
  routineUploaded: true,
  routineFileName: null,
  routineUploadedAt: null,
  routineUploadedBy: null,
  studentsNotified: null,
};

export const MILON: OfflineInstructor = {
  id: "ins-milon",
  name: "Milon",
  phone: "01711000111",
  note: null,
  aliases: ["Milon Hossain"],
  archived: false,
  version: 3,
};

export const TUHIN: OfflineInstructor = {
  id: "ins-tuhin",
  name: "Tuhin Hossain",
  phone: null,
  note: null,
  aliases: [],
  archived: false,
  version: 1,
};

/** A payment to Milon recorded by hand (24 Nov): 4 lectures in the batch, paid by bKash. */
export function payment(overrides: Partial<InstructorPayment> = {}): InstructorPayment {
  return {
    recordKind: "OTHER",
    id: "pay-1",
    instructorId: MILON.id,
    instructorName: MILON.name,
    kind: "LECTURE",
    classCount: 4,
    batchId: BATCH.id,
    batchNameEn: BATCH.nameEn,
    batchNameBn: BATCH.nameBn,
    description: null,
    classId: null,
    classOn: null,
    amount: 2000,
    method: "BKASH",
    reference: "TX-77",
    paidOn: "2026-11-20",
    note: null,
    status: "APPROVED",
    recordedByName: "Master",
    recordedAt: "2026-11-20T05:00:00Z",
    voidReason: null,
    correctedAt: null,
    ...overrides,
  };
}

/** A class payment made before 24 Nov: its type, batch and description come from the class. */
export const OLD_CLASS_PAYMENT: InstructorPayment = payment({
  recordKind: "CLASS",
  id: "old-1",
  kind: "EXAM",
  classCount: 1,
  description: "Evidence Act",
  classId: "class-1",
  classOn: "2026-11-02",
  amount: 500,
  method: "CASH",
  reference: null,
  paidOn: "2026-11-03",
});

export const SUMMARY: PaySummary = {
  totalPaid: 2500,
  paidThisMonth: 2500,
  lastPaidOn: "2026-11-20",
  lastAmount: 2000,
  payments: 2,
  batches: [{ id: BATCH.id, nameEn: BATCH.nameEn, nameBn: BATCH.nameBn }],
};

export const CARDS: PayCards = {
  thisMonth: { amount: 2500, payments: 2 },
  lastMonth: { amount: 0, payments: 0 },
  thisYear: { amount: 2500, payments: 2 },
};

export function list(items: InstructorPayment[]) {
  return {
    items,
    total: items.filter((item) => item.status === "APPROVED").reduce((sum, item) => sum + Number(item.amount), 0),
    truncated: false,
  };
}
