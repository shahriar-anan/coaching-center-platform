import type { BatchView } from "@/lib/api/exam/offline-admin";
import type {
  BatchExamRow,
  ResultPreview,
  ResultRow,
  ResultSheet,
  ResultSheetRow,
  ResultSheetView,
} from "@/lib/api/exam/offline-results";

/** Shared fixtures for the offline results screens, in the shapes of results-api-contract.md. */

export const BATCH_ID = "bbbbbbbb-0000-0000-0000-000000000001";
export const CLASS_PUBLISHED = "cccccccc-0000-0000-0000-000000000001";
export const CLASS_STAGED = "cccccccc-0000-0000-0000-000000000002";
export const CLASS_BOTH = "cccccccc-0000-0000-0000-000000000003";
export const CLASS_NONE = "cccccccc-0000-0000-0000-000000000004";
export const CLASS_FUTURE = "cccccccc-0000-0000-0000-000000000005";
export const CLASS_CANCELLED = "cccccccc-0000-0000-0000-000000000006";
export const SHEET_PUBLISHED = "dddddddd-0000-0000-0000-000000000001";
export const SHEET_STAGED = "dddddddd-0000-0000-0000-000000000002";

/** The past exam date and a date far ahead, so the tests do not depend on today. */
export const PAST_DATE = "2026-11-10";
export const FUTURE_DATE = "2099-01-01";

export const batch: BatchView = {
  id: BATCH_ID,
  nameEn: "20th BJS Foundation",
  nameBn: "২০তম বিজেএস ফাউন্ডেশন",
  startsOn: "2026-11-01",
  endsOn: "2027-03-31",
  defaultMonthlyFee: 3000,
  status: "ACTIVE",
  descriptionEn: null,
  descriptionBn: null,
  photoUploaded: false,
  admissionsOpen: true,
  displayOrder: 1,
  studentCount: 42,
  routineUploaded: false,
  routineFileName: null,
  routineUploadedAt: null,
  routineUploadedBy: null,
  studentsNotified: null,
  defaultClassMinutes: 60,
};

export function examRow(overrides: Partial<BatchExamRow> = {}): BatchExamRow {
  return {
    classId: CLASS_NONE,
    heldOn: PAST_DATE,
    subject: "Constitution",
    topic: "Part 1",
    instructorName: "Nasrin Sultana",
    kind: "EXAM",
    cancelled: false,
    status: "SCHEDULED",
    state: "NONE",
    publishedAt: null,
    rowCount: 0,
    ...overrides,
  };
}

export function resultRow(overrides: Partial<ResultRow> = {}): ResultRow {
  return {
    rowNumber: 6,
    roll: "192001",
    admissionId: "adm-0001",
    studentName: "Rahim Uddin",
    absent: false,
    right: 20,
    wrong: 5,
    mcq: 18.75,
    written: null,
    total: 18.75,
    rank: 1,
    passed: false,
    fileTotal: 18.75,
    fileRank: 1,
    mismatch: false,
    maybeAbsent: false,
    ...overrides,
  };
}

export function preview(overrides: Partial<ResultPreview> = {}): ResultPreview {
  return {
    examType: "MCQ",
    fullMarks: 50,
    negative: 0.25,
    passMark: 40,
    counts: { rows: 2, matched: 2, absent: 0, mismatched: 0 },
    rows: [
      resultRow({ roll: "192001", studentName: "Rahim Uddin", total: 45, mcq: 45, rank: 1, passed: true, fileTotal: 45, fileRank: 1 }),
      resultRow({ roll: "192002", studentName: "Sumi Akter", total: 30, mcq: 30, rank: 2, passed: false, fileTotal: 30, fileRank: 2 }),
    ],
    problems: [],
    warnings: [],
    ...overrides,
  };
}

export function sheetRow(overrides: Partial<ResultSheetRow> = {}): ResultSheetRow {
  return { id: `row-${overrides.roll ?? "192001"}`, ...resultRow(overrides) };
}

export function sheet(overrides: Partial<ResultSheet> = {}): ResultSheet {
  return {
    id: SHEET_PUBLISHED,
    status: "PUBLISHED",
    examType: "MCQ",
    fullMarks: 50,
    negative: 0.25,
    passMark: 40,
    fileName: "Exam-01.xlsx",
    uploadedBy: "Office Staff",
    uploadedAt: "2026-11-11T05:00:00Z",
    publishedBy: "Master Admin",
    publishedAt: "2026-11-12T04:00:00Z",
    rows: [sheetRow({ roll: "192001", studentName: "Rahim Uddin", total: 45, mcq: 45, rank: 1, passed: true })],
    ...overrides,
  };
}

export function sheetView(overrides: Partial<ResultSheetView> = {}): ResultSheetView {
  return {
    classId: CLASS_BOTH,
    batchId: BATCH_ID,
    subject: "Constitution",
    topic: "Part 1",
    heldOn: PAST_DATE,
    cancelled: false,
    published: null,
    staged: null,
    ...overrides,
  };
}
