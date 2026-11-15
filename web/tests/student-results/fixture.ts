import type {
  MyAdmissionResults,
  MyExamLeaderboard,
  MyExamResult,
  MyLeaderboardRow,
} from "@/lib/api/exam/my-results";

/** One published exam with the student's own row (the contract's `exams` item). Pass `absent` to model a student with no row. */
export function examResult(overrides: Partial<MyExamResult> = {}): MyExamResult {
  return {
    classId: "class-1",
    heldOn: "2026-11-10",
    subject: "Constitution",
    topic: "Part 1",
    examType: "MCQ_WRITTEN",
    fullMarks: 50,
    passMark: 40,
    publishedAt: "2026-11-12T04:00:00Z",
    absent: false,
    maybeAbsent: false,
    total: 41.75,
    mcq: 18.75,
    written: 23,
    rank: 3,
    rankOf: 47,
    passed: true,
    ...overrides,
  };
}

/** The absent version of an exam: no marks, rank or pass. */
export function absentResult(overrides: Partial<MyExamResult> = {}): MyExamResult {
  return examResult({
    absent: true,
    total: null,
    mcq: null,
    written: null,
    rank: null,
    passed: null,
    ...overrides,
  });
}

/** The admission's results (the contract's `GET .../results` answer). */
export function admissionResults(exams: MyExamResult[], overrides: Partial<MyAdmissionResults> = {}): MyAdmissionResults {
  return {
    batchNameEn: "Batch 3 (Evening)",
    batchNameBn: "ব্যাচ ৩ (সন্ধ্যা)",
    roll: "192001",
    exams,
    ...overrides,
  };
}

/** One leaderboard row. The default is another student, not the viewer. */
export function boardRow(overrides: Partial<MyLeaderboardRow> & { roll: string; name: string }): MyLeaderboardRow {
  return {
    rank: null,
    total: null,
    mcq: null,
    written: null,
    passed: null,
    absent: false,
    maybeAbsent: false,
    mine: false,
    ...overrides,
  };
}

/**
 * One exam's leaderboard as the contract answers it. The rows are in the order the API sends: rank, then roll, absent last. The
 * viewer's roll is 192001, and their row is `mine`.
 */
export function leaderboard(rows: MyLeaderboardRow[], overrides: Partial<MyExamLeaderboard> = {}): MyExamLeaderboard {
  return {
    classId: "class-1",
    subject: "Constitution",
    topic: "Part 1",
    heldOn: "2026-11-10",
    examType: "MCQ_WRITTEN",
    fullMarks: 50,
    passMark: 40,
    publishedAt: "2026-11-12T04:00:00Z",
    myRoll: "192001",
    rows,
    ...overrides,
  };
}

/** The class's rows used by the leaderboard tests: a tie for second, a zero row, and an absent student who sorts last. */
export function classRows(): MyLeaderboardRow[] {
  return [
    boardRow({ roll: "192003", name: "Nadia Islam", rank: 1, total: 49.5, mcq: 49.5, passed: true }),
    boardRow({ roll: "192001", name: "Rahim Uddin", rank: 2, total: 45, mcq: 45, passed: true, mine: true }),
    boardRow({ roll: "192005", name: "Karim Ahmed", rank: 2, total: 45, mcq: 45, passed: true }),
    boardRow({ roll: "192002", name: "Sumi Akter", rank: 4, total: 0, mcq: 0, passed: false, maybeAbsent: true }),
    boardRow({ roll: "192004", name: "Tanvir Hasan", rank: null, total: null, absent: true }),
  ];
}
