import type { AdmissionState, AdmissionView } from "@/lib/api/exam/offline-admission";
import type { AdmissionItem, StudentClass, StudentDue, StudentPayment, StudentSchedule } from "@/lib/api/exam/my-admission";

/** The admission as the student API answers it. */
export function admission(state: AdmissionState, overrides: Partial<AdmissionView> = {}): AdmissionView {
  return {
    id: "adm-1",
    state,
    changesReason: null,
    submittedAt: "2026-11-10T04:00:00Z",
    submitCount: 1,
    hasPhoto: false,
    form: {
      fullName: "Rahim Uddin",
      mobile: "01711000012",
      university: "Rajshahi University",
      semesterBatch: "3rd semester, batch 2025",
    },
    ...overrides,
  };
}

/** One due. `paid` is what has been paid; `overdue` is only honoured while something is still owed. */
export function due(seq: number, dueOn: string, amount: number, paid: number, overdue = false): StudentDue {
  const owed = amount - paid;
  return {
    id: `due-${seq}`,
    seq,
    dueOn,
    amount,
    paid,
    owed,
    overdue: overdue && owed > 0,
    state: owed === 0 ? "PAID" : paid === 0 ? "UNPAID" : "PART_PAID",
  };
}

/** An approved payment as the student API lists it. Pass `status` to test a row that must not be shown. */
export function payment(id: string, amount: number, receiptNo: string, status?: string): StudentPayment {
  return { id, amount, method: "CASH", reference: null, paidOn: "2026-11-15", receiptNo, ...(status ? { status } : {}) };
}

/** The schedule. The totals are worked out from the dues unless the caller gives them. */
export function schedule(overrides: Partial<StudentSchedule> = {}): StudentSchedule {
  const dues = overrides.dues ?? [due(1, "2026-12-15", 3000, 0)];
  const owed = dues.reduce((sum, item) => sum + item.owed, 0);
  const overdue = dues.filter((item) => item.overdue).reduce((sum, item) => sum + item.owed, 0);
  return {
    admissionId: "adm-1",
    admissionStatus: "ONBOARDED",
    owed,
    overdue,
    payments: [],
    ...overrides,
    dues,
  };
}

/** An exam the student may open through the admission. */
export function examItem(examId: string, titleEn: string, accessEndsAt: string | null = "2027-02-28T10:00:00Z"): AdmissionItem {
  return {
    scope: "EXAM",
    examId,
    moduleId: null,
    titleEn,
    titleBn: null,
    packageExamCount: null,
    accessEndsAt,
  };
}

/** A package the student may open through the admission, with its active exam count. */
export function packageItem(
  moduleId: string,
  titleEn: string,
  packageExamCount: number,
  accessEndsAt: string | null = "2027-02-28T10:00:00Z",
): AdmissionItem {
  return {
    scope: "MODULE",
    examId: null,
    moduleId,
    titleEn,
    titleBn: null,
    packageExamCount,
    accessEndsAt,
  };
}

/** The instructor of the batch's classes. The student routine never shows this name. */
export const ROUTINE_INSTRUCTOR = "Milon Hossain";

/**
 * The classes of one month as the student routine API answers them: a lecture, a lecture with no topic, and an exam. The API
 * sends no instructor; `instructorName` is added on purpose, as a server that leaked it would, so the tests can show the page
 * does not render it.
 */
export function routineClasses(month: string): (StudentClass & { instructorName: string })[] {
  const base = { instructorName: ROUTINE_INSTRUCTOR };
  return [
    {
      ...base,
      date: `${month}-05`,
      day: "Thursday",
      startsAt: "18:00:00",
      endsAt: "19:10:00",
      subject: "Constitutional Law",
      topic: "Fundamental rights",
      kind: "LECTURE",
    },
    {
      ...base,
      date: `${month}-07`,
      day: "Saturday",
      startsAt: "09:00:00",
      endsAt: "10:10:00",
      subject: "Evidence Act",
      topic: null,
      kind: "LECTURE",
    },
    {
      ...base,
      date: `${month}-09`,
      day: "Monday",
      startsAt: "19:00:00",
      endsAt: "20:00:00",
      subject: "MCQ Exam",
      topic: "Mock 4",
      kind: "EXAM",
    },
  ];
}
