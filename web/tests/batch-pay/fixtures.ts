import type { BatchView } from "@/lib/api/exam/offline-admin";
import type { BatchClass, InstructorRow, RoutineView, RoutineRow, RoutineSummary } from "@/lib/api/exam/offline-batch-pay";

export const BATCH_ID = "b1";
export const ROUTINE_ID = "r1";

export const batch: BatchView = {
  id: BATCH_ID,
  nameEn: "BJS Evening",
  nameBn: "বিজেএস সন্ধ্যা",
  startsOn: "2026-11-01",
  endsOn: "2027-02-09",
  courseFee: 3000,
  defaultInstallments: 6,
  status: "ACTIVE",
  descriptionEn: null,
  descriptionBn: null,
  photoUploaded: false,
  admissionsOpen: true,
  displayOrder: 0,
  defaultClassMinutes: 60,
  studentCount: 0,
  routineUploaded: false,
  routineFileName: null,
  routineUploadedAt: null,
  routineUploadedBy: null,
  studentsNotified: null,
};

export const milon: InstructorRow = { id: "i1", name: "Milon Hossain", phone: null, note: null, aliases: [], archived: false, version: 0 };
export const rahim: InstructorRow = { id: "i2", name: "Rahim Sir", phone: null, note: null, aliases: [], archived: false, version: 0 };

function row(index: number, overrides: Partial<RoutineRow>): RoutineRow {
  return {
    index,
    date: "2026-11-02",
    day: "Monday",
    time: "5.10 PM",
    startsAt: "17:10",
    endsAt: "18:30",
    subject: "Physics",
    topic: "Motion",
    kind: "LECTURE",
    instructorText: null,
    instructorId: null,
    instructorName: null,
    flags: [],
    ...overrides,
  };
}

/** A preview with one unknown instructor name (flagged) and one matched row. */
export function previewRoutine(overrides: Partial<RoutineView> = {}): RoutineView {
  return {
    id: ROUTINE_ID,
    batchId: BATCH_ID,
    effectiveFrom: "2026-11-01",
    version: 1,
    status: "PREVIEW",
    legacyOriginal: false,
    originalName: "routine.pdf",
    studentCopy: false,
    uploadedAt: "2026-11-10T04:00:00Z",
    confirmedAt: null,
    rowCount: 2,
    flaggedCount: 1,
    unmatchedNames: [{ name: "Milon Sir", rows: 1 }],
    // A name no instructor has does not block: the confirm adds it as a new instructor.
    warnings: [],
    canConfirm: true,
    rows: [
      row(0, {
        instructorText: "Milon Sir",
        flags: [{ code: "INSTRUCTOR_UNKNOWN", reason: "New instructor: Milon Sir is added to the instructors when the routine is confirmed." }],
      }),
      row(1, {
        date: "2026-11-03",
        day: "Tuesday",
        subject: "Chemistry",
        topic: null,
        instructorText: "Rahim Sir",
        instructorId: "i2",
        instructorName: "Rahim Sir",
      }),
    ],
    difference: null,
    ...overrides,
  };
}

export function summary(overrides: Partial<RoutineSummary> = {}): RoutineSummary {
  return {
    id: ROUTINE_ID,
    effectiveFrom: "2026-11-01",
    version: 1,
    status: "CONFIRMED",
    legacyOriginal: false,
    originalName: "routine.pdf",
    studentCopy: true,
    uploadedAt: "2026-11-01T04:00:00Z",
    confirmedAt: "2026-11-01T05:00:00Z",
    rowCount: 2,
    flaggedCount: 0,
    canConfirm: false,
    ...overrides,
  };
}

export function classRow(overrides: Partial<BatchClass> & { id: string }): BatchClass {
  return {
    batchId: BATCH_ID,
    routineId: ROUTINE_ID,
    heldOn: "2026-11-02",
    startsAt: "17:10:00",
    endsAt: "18:30:00",
    subject: "Physics",
    topic: "Motion",
    kind: "LECTURE",
    instructorId: "i1",
    instructorName: "Milon Hossain",
    instructorText: "Milon Sir",
    status: "HELD",
    version: 3,
    ...overrides,
  };
}
