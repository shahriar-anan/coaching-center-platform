import type { FeedRow, MarkingModule, MarkingPaper } from "@/lib/api/exam/staff-marking";
import type { ParticipationRow } from "@/lib/api/exam/staff-home";
import { apiError } from "../helpers";

/** Shared WS9 MRK fixtures: two modules under one category, one written exam each, and their papers. */

export const ROOT = { id: "c-root", titleEn: "Government jobs", titleBn: "সরকারি চাকরি" };
export const BCS = { id: "c-bcs", titleEn: "BCS", titleBn: "বিসিএস" };

export const modules: MarkingModule[] = [
  {
    id: "m1",
    titleEn: "BCS Preliminary",
    titleBn: "বিসিএস প্রিলিমিনারি",
    categoryPath: [ROOT, BCS],
    instructor: { id: "44444444-4444-4444-4444-444444444444", name: "নাসরিন সুলতানা", phone: "01711000002" },
  },
  {
    id: "m2",
    titleEn: "Bank Job",
    titleBn: "ব্যাংক চাকরি",
    categoryPath: [ROOT],
    instructor: null,
  },
];

export const participation: ParticipationRow[] = [
  {
    examId: "e1",
    moduleId: "m1",
    moduleTitleEn: "BCS Preliminary",
    moduleTitleBn: "বিসিএস প্রিলিমিনারি",
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
    moduleId: "m2",
    moduleTitleEn: "Bank Job",
    moduleTitleBn: "ব্যাংক চাকরি",
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
];

const base = {
  examTitleEn: "Essay paper",
  examTitleBn: "রচনা পত্র",
  moduleTitleEn: "BCS Preliminary",
  moduleTitleBn: "বিসিএস প্রিলিমিনারি",
  categoryPath: [ROOT, BCS],
  kind: "LIVE" as const,
  answerPdf: true,
  publishedAt: null,
};

export const feed: FeedRow[] = [
  {
    ...base,
    participantId: "p1",
    examId: "e1",
    moduleId: "m1",
    studentName: "রহিম উদ্দিন",
    status: "AWAITING",
    submittedAt: new Date(Date.now() - 20 * 60_000).toISOString(),
    evaluatedAt: null,
    evaluatorName: null,
  },
  {
    ...base,
    participantId: "p2",
    examId: "e1",
    moduleId: "m1",
    studentName: "করিম মিয়া",
    status: "DRAFT",
    submittedAt: new Date(Date.now() - 60 * 60_000).toISOString(),
    evaluatedAt: new Date().toISOString(),
    evaluatorName: "নাসরিন সুলতানা",
  },
  {
    ...base,
    participantId: "p3",
    examId: "e1",
    moduleId: "m1",
    studentName: "সালমা খাতুন",
    status: "PUBLISHED",
    publishedAt: new Date().toISOString(),
    submittedAt: new Date(Date.now() - 2 * 60 * 60_000).toISOString(),
    evaluatedAt: new Date().toISOString(),
    evaluatorName: "নাসরিন সুলতানা",
  },
];

/** A feed handler that applies the query the way the API does: status, module, exam and name search. */
export function serveFeed(rows: FeedRow[] = feed) {
  return ({ url }: { url: URL }) => {
    const status = url.searchParams.get("status") ?? "";
    const moduleId = url.searchParams.get("moduleId") ?? "";
    const examId = url.searchParams.get("examId") ?? "";
    const q = (url.searchParams.get("q") ?? "").toLowerCase();
    const size = Number(url.searchParams.get("size") ?? "20");
    const matched = rows.filter(
      (row) =>
        (status === "" || row.status === status) &&
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
    moduleId: "m1",
    moduleTitleEn: "BCS Preliminary",
    moduleTitleBn: "বিসিএস প্রিলিমিনারি",
    examTitleEn: "Essay paper",
    examTitleBn: "রচনা পত্র",
    submittedAt: "2026-10-20T06:00:00Z",
    evaluatedAt: null,
    publishedAt: null,
    ...overrides,
  };
}

export const notAllowed = () => apiError(404, "RESOURCE_NOT_FOUND", "Resource not found.");
