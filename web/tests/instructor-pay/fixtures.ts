import type { BatchView } from "@/lib/api/exam/offline-admin";
import type { ClassRow, OfflineInstructor } from "@/lib/api/exam/instructor-pay";

export const BATCH: BatchView = {
  id: "batch-1",
  nameEn: "BJS Foundation",
  nameBn: "বিজেএস ফাউন্ডেশন",
  startsOn: null,
  endsOn: "2026-12-31",
  defaultMonthlyFee: 1000,
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

/** One class of the batch, held unless the overrides say otherwise. */
export function classRow(overrides: Partial<ClassRow> & Pick<ClassRow, "id">): ClassRow {
  return {
    batchId: BATCH.id,
    routineId: "routine-1",
    heldOn: "2026-11-10",
    startsAt: "09:00:00",
    endsAt: "10:20:00",
    subject: "Evidence Act",
    topic: null,
    kind: "LECTURE",
    instructorId: MILON.id,
    instructorName: MILON.name,
    instructorText: "Milon",
    amount: 500,
    rateOverride: null,
    status: "HELD",
    version: 1,
    payment: null,
    notTakenRequest: null,
    ...overrides,
  };
}

/** Three held classes for Milon (500 each) and one for Tuhin (400). */
export const HELD_ROWS: ClassRow[] = [
  classRow({ id: "c-1", heldOn: "2026-11-10", subject: "Evidence Act" }),
  classRow({ id: "c-2", heldOn: "2026-11-11", subject: "Contract Act" }),
  classRow({ id: "c-3", heldOn: "2026-11-12", subject: "Penal Code" }),
  classRow({
    id: "c-4",
    heldOn: "2026-11-11",
    subject: "Constitution",
    instructorId: TUHIN.id,
    instructorName: TUHIN.name,
    instructorText: "Tuhin",
    amount: 400,
  }),
];

/** Two waiting entries staff recorded: one payment and one not-taken mark. */
export const WAITING_ROWS: ClassRow[] = [
  classRow({
    id: "w-1",
    heldOn: "2026-11-09",
    subject: "Evidence Act",
    status: "PAID_PENDING",
    payment: {
      id: "pay-1",
      instructorId: MILON.id,
      amount: 500,
      method: "CASH",
      reference: null,
      paidOn: "2026-11-09",
      note: null,
      status: "PENDING",
    },
  }),
  classRow({
    id: "w-2",
    heldOn: "2026-11-09",
    subject: "Contract Act",
    status: "NOT_TAKEN_PENDING",
    notTakenRequest: { id: "dec-1", reason: "Holiday", status: "PENDING", requestedAt: "2026-11-09T10:00:00Z" },
  }),
];

export function unsettledPage(items: ClassRow[]) {
  return { items, page: 0, size: 200, total: items.length };
}
