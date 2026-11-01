import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ExamPage } from "@/components/student/catalog/exam-page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const HOUR = 60 * 60_000;
const inOneHour = () => new Date(Date.now() + HOUR).toISOString();
const oneHourAgo = () => new Date(Date.now() - HOUR).toISOString();

const offer = {
  price: 400,
  discountPercent: null,
  discountEndsAt: null,
  effectivePrice: 400,
  free: false,
  moduleOnly: false,
};

/** A paid exam the signed-in student owns, with its window open now. */
function publicExam(examType: "MCQ" | "WRITTEN") {
  return {
    id: "e1",
    moduleId: "m1",
    examType,
    titleEn: examType === "MCQ" ? "CPC MCQ" : "CPC written",
    titleBn: examType === "MCQ" ? "CPC MCQ" : "CPC written",
    descriptionEn: null,
    descriptionBn: null,
    rulesEn: null,
    rulesBn: null,
    paperLanguage: "EN",
    liveAt: oneHourAgo(),
    endAt: inOneHour(),
    durationMinutes: 40,
    price: 400,
    pricing: offer,
    packages: [],
    access: { state: "EXAM", buyable: false, blockedReason: "ALREADY_HAS_ACCESS", expiresAt: null },
  };
}

/** The student's own read of the exam. `mine` holds the state under test. */
function mount(examType: "MCQ" | "WRITTEN", mine: Record<string, unknown>) {
  mockApi({
    "GET /api/v1/public/live-exams/e1": publicExam(examType),
    "GET /api/v1/public/exam-site/status": { checkoutEnabled: true },
    "GET /api/v1/live-exams/e1": {
      ...publicExam(examType),
      passPercent: 40,
      maximum: 3,
      status: "PUBLISHED",
      finallySaved: true,
      questionPdf: null,
      questions: null,
      retakeAvailable: null,
      ...mine,
    },
  });
  return renderWithProviders(<ExamPage examId="e1" />, { user: fixtures.student, locale: "en" });
}

const sitting = (submittedAt: string | null) => ({
  kind: "LIVE",
  startedAt: oneHourAgo(),
  deadlineAt: inOneHour(),
  submittedAt,
});

/** The paper while the attempt is open: no key, no explanation. */
const paper = [
  {
    id: "q1",
    stem: "Question one",
    marks: 1,
    explanation: null,
    options: [
      { id: "a", text: "one", correct: null, selected: null },
      { id: "b", text: "two", correct: null, selected: null },
    ],
  },
];

/** The review once it is open: the key and the explanation come with the questions. */
const openedQuestions = [
  {
    id: "q1",
    stem: "Question one",
    marks: 1,
    explanation: "Because",
    options: [
      { id: "a", text: "one", correct: false, selected: true },
      { id: "b", text: "two", correct: true, selected: false },
    ],
  },
];

describe("the exam page's primary action, by state", () => {
  it("an MCQ in progress offers Continue exam, to the paper", async () => {
    mount("MCQ", { myStatus: "IN_PROGRESS", sitting: sitting(null), questions: paper });
    const link = await screen.findByRole("link", { name: "Continue exam" });
    expect(link).toHaveAttribute("href", "/exam/e1/sit");
    expect(screen.queryByRole("link", { name: "View result" })).not.toBeInTheDocument();
  });

  it("an MCQ submitted while the review is closed offers View result, never the paper", async () => {
    mount("MCQ", {
      myStatus: "SUBMITTED",
      sitting: sitting(oneHourAgo()),
      reviewOpensAt: inOneHour(),
      questions: undefined,
    });
    const link = await screen.findByRole("link", { name: "View result" });
    expect(link).toHaveAttribute("href", "/exam/e1/result");
    expect(screen.queryByRole("link", { name: "Review answers" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Continue exam" })).not.toBeInTheDocument();
    expect(document.querySelector('a[href="/exam/e1/sit"]')).toBeNull();
    expect(screen.getByText(/Your answers are final\./)).toBeInTheDocument();
  });

  it("an MCQ submitted once the review is open still offers View result, never the paper", async () => {
    mount("MCQ", { myStatus: "SUBMITTED", sitting: sitting(oneHourAgo()), questions: openedQuestions });
    const link = await screen.findByRole("link", { name: "View result" });
    expect(link).toHaveAttribute("href", "/exam/e1/result");
    expect(screen.queryByRole("link", { name: "Review answers" })).not.toBeInTheDocument();
    expect(document.querySelector('a[href="/exam/e1/sit"]')).toBeNull();
  });

  it("a written paper in progress, with nothing stored, offers Continue exam", async () => {
    mount("WRITTEN", { myStatus: "IN_PROGRESS", answerStored: false, questionPdf: true, sitting: sitting(null) });
    const link = await screen.findByRole("link", { name: "Continue exam" });
    expect(link).toHaveAttribute("href", "/exam/e1/sit");
    expect(screen.queryByRole("link", { name: "View submission" })).not.toBeInTheDocument();
  });

  it("a written paper submitted, with the script stored, offers View submission, never Resume", async () => {
    mount("WRITTEN", { myStatus: "SUBMITTED", answerStored: true, questionPdf: false, sitting: sitting(null) });
    const link = await screen.findByRole("link", { name: "View submission" });
    expect(link).toHaveAttribute("href", "/exam/e1/submitted");
    expect(screen.queryByRole("link", { name: "Continue exam" })).not.toBeInTheDocument();
    expect(document.querySelector('a[href="/exam/e1/sit"]')).toBeNull();
  });

  it("a written paper that is marked offers See result", async () => {
    mount("WRITTEN", { myStatus: "RESULT_READY", answerStored: true, questionPdf: false, sitting: sitting(null) });
    const link = await screen.findByRole("link", { name: "See result" });
    expect(link).toHaveAttribute("href", "/exam/e1/result");
    expect(screen.queryByRole("link", { name: "View submission" })).not.toBeInTheDocument();
  });

  it("a written script waiting for its mark offers View submission", async () => {
    mount("WRITTEN", { myStatus: "AWAITING_RESULT", answerStored: true, questionPdf: false, sitting: sitting(null) });
    const link = await screen.findByRole("link", { name: "View submission" });
    expect(link).toHaveAttribute("href", "/exam/e1/submitted");
    expect(screen.queryByRole("link", { name: "Continue exam" })).not.toBeInTheDocument();
  });
});
