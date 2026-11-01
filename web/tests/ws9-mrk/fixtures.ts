import type { FeedRow, MarkingPaper } from "@/lib/api/exam/staff-marking";
import type { ParticipationRow } from "@/lib/api/exam/staff-home";
import { apiError } from "../helpers";

/**
 * Shared WS9 MRK fixtures. Like the real data, the written exams sit in no package: `moduleId` is left out. Exams
 * belong to a category directly, so the tree is built from `categoryPath`.
 */

export const ROOT = { id: "c-root", titleEn: "Government jobs", titleBn: "সরকারি চাকরি" };
export const BCS = { id: "c-bcs", titleEn: "BCS", titleBn: "বিসিএস" };
export const MODULE_ID = "7d3c1f0e-2b8a-4c5d-9e1f-0a1b2c3d4e5f";

/** Three written exams: one under a sub-category, one under the root only, and one with no category. */
export const participation: ParticipationRow[] = [
  {
    examId: "e1",
    categoryId: "c-bcs",
    categoryPath: [ROOT, BCS],
    examType: "WRITTEN",
    titleEn: "Essay paper",
    titleBn: "রচনা পত্র",
    liveAt: null,
    endAt: null,
    sitting: 0,
    submitted: 6,
    failedToSubmit: 0,
    awaitingMarking: 3,
    draftsAwaitingPublish: 1,
    published: 2,
    retakes: 0,
  },
  {
    examId: "e2",
    categoryId: "c-root",
    categoryPath: [ROOT],
    examType: "WRITTEN",
    titleEn: "Written test",
    titleBn: "লিখিত পরীক্ষা",
    liveAt: null,
    endAt: null,
    sitting: 0,
    submitted: 0,
    failedToSubmit: 0,
    awaitingMarking: 0,
    draftsAwaitingPublish: 0,
    published: 0,
    retakes: 0,
  },
  {
    examId: "e3",
    moduleId: "m2",
    moduleTitleEn: "Bank Job",
    moduleTitleBn: "ব্যাংক চাকরি",
    categoryPath: [],
    examType: "WRITTEN",
    titleEn: "Loose paper",
    titleBn: "আলাদা পত্র",
    liveAt: null,
    endAt: null,
    sitting: 0,
    submitted: 1,
    failedToSubmit: 0,
    awaitingMarking: 1,
    draftsAwaitingPublish: 0,
    published: 0,
    retakes: 0,
  },
];

const essay = {
  examId: "e1",
  examTitleEn: "Essay paper",
  examTitleBn: "রচনা পত্র",
  categoryPath: [ROOT, BCS],
  kind: "LIVE" as const,
  answerPdf: true,
  publishedAt: null,
};

export const feed: FeedRow[] = [
  {
    ...essay,
    participantId: "p1",
    studentName: "রহিম উদ্দিন",
    status: "AWAITING",
    submittedAt: new Date(Date.now() - 20 * 60_000).toISOString(),
    evaluatedAt: null,
    evaluatorName: null,
    instructorName: "নাসরিন সুলতানা",
  },
  {
    ...essay,
    participantId: "p2",
    studentName: "করিম মিয়া",
    status: "DRAFT",
    submittedAt: new Date(Date.now() - 60 * 60_000).toISOString(),
    evaluatedAt: new Date().toISOString(),
    evaluatorName: "নাসরিন সুলতানা",
    instructorName: "নাসরিন সুলতানা",
  },
  {
    ...essay,
    participantId: "p3",
    studentName: "সালমা খাতুন",
    status: "PUBLISHED",
    publishedAt: new Date().toISOString(),
    submittedAt: new Date(Date.now() - 2 * 60 * 60_000).toISOString(),
    evaluatedAt: new Date().toISOString(),
    evaluatorName: "নাসরিন সুলতানা",
    instructorName: "নাসরিন সুলতানা",
  },
  {
    participantId: "p4",
    examId: "e3",
    examTitleEn: "Loose paper",
    examTitleBn: "আলাদা পত্র",
    moduleId: "m2",
    moduleTitleEn: "Bank Job",
    moduleTitleBn: "ব্যাংক চাকরি",
    categoryPath: [],
    kind: "LIVE",
    answerPdf: true,
    studentName: "তানভীর আহমেদ",
    status: "AWAITING",
    submittedAt: new Date(Date.now() - 30 * 60_000).toISOString(),
    publishedAt: null,
    evaluatedAt: null,
    evaluatorName: null,
    instructorName: null,
  },
];

/**
 * A feed handler that applies the query the way the API does: status, category (the category and every category
 * under it, which a paper's category path shows), package, exam and name search. A `undefined` or `null` id is a 400.
 */
export function serveFeed(rows: FeedRow[] = feed) {
  return ({ url }: { url: URL }) => {
    for (const name of ["categoryId", "moduleId", "examId"]) {
      const value = url.searchParams.get(name);
      if (value === "undefined" || value === "null") {
        return apiError(400, "VALIDATION_FAILED", `Parameter ${name} is invalid.`);
      }
    }
    const status = url.searchParams.get("status") ?? "";
    const categoryId = url.searchParams.get("categoryId") ?? "";
    const moduleId = url.searchParams.get("moduleId") ?? "";
    const examId = url.searchParams.get("examId") ?? "";
    const q = (url.searchParams.get("q") ?? "").toLowerCase();
    const size = Number(url.searchParams.get("size") ?? "20");
    const matched = rows.filter(
      (row) =>
        (status === "" || row.status === status) &&
        (categoryId === "" || row.categoryPath.some((ref) => ref.id === categoryId)) &&
        (moduleId === "" || row.moduleId === moduleId) &&
        (examId === "" || row.examId === examId) &&
        (q === "" || row.studentName.toLowerCase().includes(q)),
    );
    return {
      content: matched.slice(0, size),
      page: 0,
      size,
      totalElements: matched.length,
      totalPages: Math.max(1, Math.ceil(matched.length / size)),
    };
  };
}

export function paper(overrides: Partial<MarkingPaper> = {}): MarkingPaper {
  return {
    id: "p1",
    examId: "e1",
    studentId: "55555555-5555-4555-8555-555555555555",
    studentName: "রহিম উদ্দিন",
    status: "SUBMITTED",
    score: null,
    maximum: 50,
    passed: null,
    kind: "LIVE",
    answerPdf: true,
    evaluatedPdf: null,
    evaluatorName: null,
    published: false,
    comment: null,
    evaluatedFileName: null,
    examTitleEn: "Essay paper",
    examTitleBn: "রচনা পত্র",
    submittedAt: "2026-10-20T06:00:00Z",
    evaluatedAt: null,
    publishedAt: null,
    ...overrides,
  };
}

export const notAllowed = () => apiError(404, "RESOURCE_NOT_FOUND", "Resource not found.");
