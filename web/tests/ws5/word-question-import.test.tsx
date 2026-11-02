import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { WordQuestionImport } from "@/components/staff/catalog/word-question-import";
import type { QuestionFilePreview, StaffExam } from "@/lib/api/exam/staff-catalog";
import { fixtures, renderWithProviders } from "../helpers";

const previewFile = vi.fn<(file: File) => Promise<QuestionFilePreview>>();

vi.mock("@/lib/api/exam/staff-catalog", async (original) => ({
  ...(await original<typeof import("@/lib/api/exam/staff-catalog")>()),
  useQuestionFilePreview: () => ({ mutateAsync: previewFile, isPending: false }),
  useApplyQuestionFile: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));

const exam = { id: "eeeeeeee-0000-0000-0000-000000000031", status: "DRAFT", started: false, publishedAt: null, questions: [] } as unknown as StaffExam;

const preview: QuestionFilePreview = {
  header: [],
  fullMarks: 2,
  durationMinutes: 5,
  questions: [
    {
      number: 1,
      stem: "প্রথম প্রশ্ন",
      explanation: "প্রথম প্রশ্নের ব্যাখ্যা",
      options: [
        { text: "এক", correct: false },
        { text: "দুই", correct: true },
      ],
    },
    {
      number: 2,
      stem: "দ্বিতীয় প্রশ্ন",
      options: [
        { text: "তিন", correct: true },
        { text: "চার", correct: false },
      ],
    },
  ],
  problems: [],
  warnings: [],
  currentQuestions: 0,
  staged: false,
};

describe("the Word upload preview", () => {
  beforeEach(() => {
    previewFile.mockReset();
    previewFile.mockResolvedValue(preview);
  });

  async function open() {
    const { container } = renderWithProviders(<WordQuestionImport exam={exam} />, { user: fixtures.masterAdmin });
    const input = container.querySelector("input[type=file]") as HTMLInputElement;
    await userEvent.upload(input, new File(["x"], "paper.docx"));
    await screen.findByText(/প্রথম প্রশ্ন$/);
  }

  it("shows a question's explanation, and nothing for a question without one", async () => {
    await open();
    expect(screen.getByText("ব্যাখ্যা: প্রথম প্রশ্নের ব্যাখ্যা")).toBeInTheDocument();
    expect(screen.getAllByText(/^ব্যাখ্যা:/)).toHaveLength(1);
  });

  it("labels the options ক খ and marks the correct one", async () => {
    await open();
    expect(screen.getAllByText("ক.")).toHaveLength(2);
    expect(screen.getAllByText("খ.")).toHaveLength(2);
    expect(screen.getAllByText("সঠিক উত্তর")).toHaveLength(2);
  });
});
