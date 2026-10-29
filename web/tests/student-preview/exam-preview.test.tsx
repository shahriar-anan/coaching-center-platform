import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ExamPreview } from "@/components/student/catalog/exam-preview";
import { apiError, fixtures, mockApi, renderWithProviders } from "../helpers";

/**
 * The preview body carries the same fields a sitting paper does, plus the key on purpose: the correct flag and the explanation
 * must never reach the page, so the test looks for the explanation text in the DOM.
 */
const mcqPaper = {
  id: "e1",
  examType: "MCQ",
  paperLanguage: "EN",
  titleEn: "Preview MCQ",
  titleBn: "প্রিভিউ এমসিকিউ",
  descriptionEn: null,
  descriptionBn: null,
  rulesEn: null,
  rulesBn: null,
  durationMinutes: 20,
  passPercent: 40,
  maximum: 10,
  mode: "LIVE",
  phase: "ENDED",
  hasPaper: null,
  questions: [
    {
      id: "q1",
      stem: "Which section defines murder?",
      marks: 5,
      position: 0,
      multipleAnswer: false,
      explanation: "SECRET-EXPLANATION",
      options: [
        { id: "o1", text: "Section 300", position: 0, correct: true },
        { id: "o2", text: "Section 378", position: 1, correct: false },
      ],
    },
    {
      id: "q2",
      stem: "Which section defines theft?",
      marks: 5,
      position: 1,
      multipleAnswer: false,
      explanation: null,
      options: [
        { id: "o3", text: "Section 378", position: 0, correct: true },
        { id: "o4", text: "Section 300", position: 1, correct: false },
      ],
    },
  ],
};

function mount(body: unknown, status?: number) {
  mockApi({
    "GET /api/v1/live-exams/e1/preview": status ? apiError(status, "RESOURCE_NOT_FOUND", "Resource not found.") : body,
  });
  return renderWithProviders(<ExamPreview examId="e1" />, { user: fixtures.student, locale: "en" });
}

describe("the paper preview page", () => {
  it("shows the title, the facts and each question with its options and marks, and no answer", async () => {
    const { container } = mount(mcqPaper);

    expect(await screen.findByRole("heading", { name: "Preview MCQ", level: 1 })).toBeInTheDocument();
    expect(screen.getByText("20 minutes")).toBeInTheDocument();
    expect(screen.getByText("Full marks")).toBeInTheDocument();
    expect(screen.getByText("Which section defines murder?")).toBeInTheDocument();
    expect(screen.getByText("Which section defines theft?")).toBeInTheDocument();
    expect(screen.getByText("Question 1")).toBeInTheDocument();
    expect(screen.getAllByText("5 marks")).toHaveLength(2);

    const first = screen.getByRole("heading", { name: "Question 1" }).closest("li") as HTMLElement;
    const options = within(first).getAllByRole("listitem");
    expect(options.map((item) => item.textContent)).toEqual(["ASection 300", "BSection 378"]);

    // The key and the explanation are in the response, but the page draws neither.
    expect(container.textContent).not.toContain("SECRET-EXPLANATION");
  });

  it("offers the question paper as a PDF button for a written exam that has one", async () => {
    mount({ ...mcqPaper, examType: "WRITTEN", questions: null, maximum: 50, hasPaper: true });
    expect(await screen.findByRole("button", { name: "Open question paper (PDF)" })).toBeInTheDocument();
    expect(screen.queryByText("Which section defines murder?")).not.toBeInTheDocument();
  });

  it("says a written exam has no question paper yet when it has none", async () => {
    mount({ ...mcqPaper, examType: "WRITTEN", questions: null, maximum: 50, hasPaper: false });
    expect(await screen.findByText("The question paper has not been added yet.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Open question paper (PDF)" })).not.toBeInTheDocument();
  });

  it("a paper that is not previewable gives the not-found state with a link back to the exam", async () => {
    mount(undefined, 404);
    expect(await screen.findByRole("link", { name: "Back to the exam" })).toHaveAttribute("href", "/exam/e1");
    expect(screen.queryByRole("heading", { name: "Preview MCQ", level: 1 })).not.toBeInTheDocument();
  });
});
