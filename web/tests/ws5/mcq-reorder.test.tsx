import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { McqQuestions } from "@/components/staff/catalog/mcq-questions";
import type { StaffExam } from "@/lib/api/exam/staff-catalog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const examId = "eeeeeeee-0000-0000-0000-000000000009";

const exam: StaffExam = {
  id: examId,
  moduleId: "aaaaaaaa-0000-0000-0000-000000000009",
  examType: "MCQ",
  paperLanguage: "BN",
  titleEn: "Reorder",
  titleBn: "পুনর্বিন্যাস",
  descriptionEn: null,
  descriptionBn: null,
  rulesEn: null,
  rulesBn: null,
  liveAt: null,
  endAt: null,
  durationMinutes: 40,
  passPercent: 40,
  maximum: 50,
  status: "PUBLISHED",
  finallySaved: true,
  price: null,
  questionPdf: null,
  questions: [
    {
      id: "q1",
      stem: "প্রশ্ন এক",
      marks: 20,
      explanation: "ব্যাখ্যা এক",
      position: 1,
      multipleAnswer: false,
      options: [
        { id: "o1", text: "ক", correct: true, position: 1 },
        { id: "o2", text: "খ", correct: false, position: 2 },
      ],
    },
    {
      id: "q2",
      stem: "প্রশ্ন দুই",
      marks: 30,
      explanation: null,
      position: 2,
      multipleAnswer: true,
      options: [
        { id: "o3", text: "গ", correct: false, position: 1 },
        { id: "o4", text: "ঘ", correct: true, position: 2 },
      ],
    },
  ],
};

describe("moving a question", () => {
  it("sends the whole question with its new position, as the editor saves it", async () => {
    const { calls } = mockApi({
      [`PATCH /api/v1/live-exams/${examId}/questions/q1`]: { id: "q1" },
      [`PATCH /api/v1/live-exams/${examId}/questions/q2`]: { id: "q2" },
    });
    renderWithProviders(<McqQuestions exam={exam} locked={false} canDelete />, { user: fixtures.masterAdmin });

    // The first question moves down: it takes position 2, the second takes position 1.
    await userEvent.click(screen.getAllByRole("button", { name: "নিচে সরান" })[0]);

    await waitFor(() => expect(calls.filter((call) => call.method === "PATCH")).toHaveLength(2));
    const [first, second] = calls.filter((call) => call.method === "PATCH");

    expect(first.path).toBe(`/api/v1/live-exams/${examId}/questions/q1`);
    expect(first.body).toEqual({
      stem: "প্রশ্ন এক",
      marks: 20,
      explanation: "ব্যাখ্যা এক",
      position: 2,
      multipleAnswer: false,
      options: [
        { text: "ক", correct: true },
        { text: "খ", correct: false },
      ],
    });

    expect(second.path).toBe(`/api/v1/live-exams/${examId}/questions/q2`);
    expect(second.body).toEqual({
      stem: "প্রশ্ন দুই",
      marks: 30,
      explanation: null,
      position: 1,
      multipleAnswer: true,
      options: [
        { text: "গ", correct: false },
        { text: "ঘ", correct: true },
      ],
    });
  });
});
