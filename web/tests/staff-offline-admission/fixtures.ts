import type {
  AdmissionDetail,
  AdmissionQueueRow,
  FeePayment,
  FeeSchedule,
  OfflineCounts,
  OverdueRow,
} from "@/lib/api/exam/offline-admin";
import type { AdmissionState } from "@/lib/api/exam/offline-admission";

/** A page of the API's paged shape. */
export function page<T>(content: T[], totalElements = content.length) {
  return { content, page: 0, size: 20, totalElements, totalPages: totalElements > 0 ? 1 : 0 };
}

export const ADMISSION_ID = "adm-0001-0000-0000-000000000001";

export function detail(overrides: Partial<AdmissionDetail> = {}): AdmissionDetail {
  return {
    id: ADMISSION_ID,
    studentId: "stu-0001",
    state: "SUBMITTED" as AdmissionState,
    version: 3,
    submitCount: 1,
    submittedAt: "2026-11-10T04:00:00Z",
    createdAt: "2026-11-09T04:00:00Z",
    hasPhoto: false,
    photoUrl: null,
    form: {
      fullName: "Rahim Uddin",
      fatherName: "Karim Uddin",
      motherName: "Rahima Begum",
      dateOfBirth: "2001-03-15",
      email: "rahim@example.com",
      gender: "MALE",
      university: "Rajshahi University",
      semesterBatch: "3rd semester",
      nid: "1234567890",
      mobile: "01711000001",
      emergencyMobile: "01711000002",
      hallMess: "H-12",
      presentAddress: "Motihar, Rajshahi",
      permanentAddress: "Godagari, Rajshahi",
    },
    changesReason: null,
    changesRequestedAt: null,
    setup: {
      batchId: "batch-1",
      batchNameEn: "BJS Evening",
      batchNameBn: "বিজেএস সন্ধ্যা",
      monthlyFee: 3000,
      admittedOn: "2026-11-09",
      firstDueOn: "2026-12-09",
      courseEndsOn: "2027-02-09",
    },
    items: [],
    submissions: [
      { id: "sub-1", submittedAt: "2026-11-10T04:00:00Z", form: {}, returnedReason: null, returnedBy: null, returnedAt: null },
    ],
    readyProblems: [],
    readyBy: null,
    readyAt: null,
    onboardedBy: null,
    onboardedAt: null,
    closedAt: null,
    closedReason: null,
    signedCopy: null,
    overlaps: [],
    ...overrides,
  };
}

export function queueRow(overrides: Partial<AdmissionQueueRow> = {}): AdmissionQueueRow {
  return {
    id: ADMISSION_ID,
    studentId: "stu-0001",
    fullName: "Rahim Uddin",
    mobile: "01711000001",
    batchId: "batch-1",
    batchNameEn: "BJS Evening",
    batchNameBn: "বিজেএস সন্ধ্যা",
    status: "SUBMITTED",
    submitCount: 1,
    submittedAt: "2026-11-10T04:00:00Z",
    createdAt: "2026-11-09T04:00:00Z",
    hasPhoto: false,
    flags: [],
    owedTotal: 0,
    nextDueOn: null,
    activated: true,
    codeExpiresAt: null,
    ...overrides,
  };
}

export function payment(overrides: Partial<FeePayment> = {}): FeePayment {
  return {
    id: "pay-1",
    admissionId: ADMISSION_ID,
    studentId: "stu-0001",
    name: "Rahim Uddin",
    batchNameEn: "BJS Evening",
    amount: 3000,
    method: "CASH",
    reference: null,
    paidOn: "2026-12-16",
    note: null,
    status: "PENDING",
    receiptNo: null,
    recordedBy: "staff-1",
    recordedByName: "Staff Member",
    recordedAt: "2026-12-16T05:00:00Z",
    decidedAt: null,
    decisionReason: null,
    voidedAt: null,
    voidReason: null,
    ...overrides,
  };
}

export function schedule(overrides: Partial<FeeSchedule> = {}): FeeSchedule {
  return {
    admissionId: ADMISSION_ID,
    admissionStatus: "ONBOARDED",
    personName: "Rahim Uddin",
    owed: 9000,
    overdue: 0,
    dues: [],
    payments: [],
    ...overrides,
  };
}

/** The counts endpoint's answer: the tab counts, the nav badges and the dashboard cards. */
export function counts(overrides: Partial<OfflineCounts> = {}): OfflineCounts {
  return {
    awaitingForm: 2,
    submitted: 3,
    changesRequested: 1,
    ready: 2,
    onboarded: 5,
    closed: 0,
    paymentsPending: 4,
    overdue: 2,
    ...overrides,
  };
}

export function overdueRow(overrides: Partial<OverdueRow> = {}): OverdueRow {
  return {
    admissionId: ADMISSION_ID,
    studentId: "stu-0001",
    name: "Rahim Uddin",
    batchNameEn: "BJS Evening",
    dueOn: "2026-12-09",
    seq: 1,
    amount: 3000,
    daysLate: 12,
    owedTotal: 6000,
    ...overrides,
  };
}
