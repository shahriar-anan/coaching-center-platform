import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { QuestionPage } from "@/components/staff/questions/question-page";
import type { StaffExam } from "@/lib/api/exam/staff-catalog";
import { fixtures, mockApi, renderWithProviders, resetRouter, routerState } from "../helpers";

const examId = "eeeeeeee-0000-0000-0000-000000000021";

function examWith(overrides: Partial<StaffExam> = {}): StaffExam {
  return {
    id: examId,
    moduleId: "aaaaaaaa-0000-0000-0000-000000000021",
    examType: "MCQ",
    paperLanguage: "BN",
    titleEn: "Editor",
    titleBn: "সম্পাদনা",
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
        explanation: null,
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
    ...overrides,
  };
}

const exam = examWith();

beforeEach(() => {
  resetRouter(`/staff/exams/${examId}/questions`);
});

describe("question editor page", () => {
  it("opens the first question with its stem, options and the running total", async () => {
    mockApi({ [`GET /api/v1/live-exams/${examId}`]: exam });
    renderWithProviders(<QuestionPage examId={examId} />, { user: fixtures.masterAdmin });

    expect(await screen.findByRole("heading", { name: /প্রশ্ন ১ \/ ২/ })).toBeInTheDocument();
    expect(screen.getByDisplayValue("প্রশ্ন এক")).toBeInTheDocument();
    expect(screen.getByText(/মোট ৫০ নম্বর/)).toBeInTheDocument();
  });

  it("saves an edited question and moves on to the next one with Save & next", async () => {
    const { calls } = mockApi({
      [`GET /api/v1/live-exams/${examId}`]: exam,
      [`PATCH /api/v1/live-exams/${examId}/questions/q1`]: exam.questions![0],
    });
    renderWithProviders(<QuestionPage examId={examId} />, { user: fixtures.masterAdmin });

    const stem = await screen.findByDisplayValue("প্রশ্ন এক");
    await userEvent.clear(stem);
    await userEvent.type(stem, "সংশোধিত প্রশ্ন");
    await userEvent.click(screen.getByRole("button", { name: "সংরক্ষণ ও পরেরটি" }));

    await waitFor(() => expect(calls.filter((call) => call.method === "PATCH")).toHaveLength(1));
    const [save] = calls.filter((call) => call.method === "PATCH");
    expect(save.body).toMatchObject({ stem: "সংশোধিত প্রশ্ন", position: 1 });
    await waitFor(() => expect(routerState.replace).toHaveBeenCalledWith("?q=q2", expect.anything()));
  });

  it("refuses an empty stem and sends nothing", async () => {
    const { calls } = mockApi({ [`GET /api/v1/live-exams/${examId}`]: exam });
    renderWithProviders(<QuestionPage examId={examId} />, { user: fixtures.masterAdmin });

    await userEvent.clear(await screen.findByDisplayValue("প্রশ্ন এক"));
    await userEvent.click(screen.getByRole("button", { name: "প্রশ্ন সেভ করুন" }));

    expect(await screen.findByText("প্রশ্ন লিখুন।")).toBeInTheDocument();
    expect(calls.filter((call) => call.method !== "GET")).toHaveLength(0);
  });

  it("keeps exactly one correct option on a single-answer question", async () => {
    mockApi({ [`GET /api/v1/live-exams/${examId}`]: exam });
    renderWithProviders(<QuestionPage examId={examId} />, { user: fixtures.masterAdmin });

    await screen.findByDisplayValue("প্রশ্ন এক");
    const radios = screen.getAllByRole("radio");
    expect(radios[0]).toBeChecked();
    await userEvent.click(radios[1]);
    expect(radios[0]).not.toBeChecked();
    expect(radios[1]).toBeChecked();
  });

  it("keeps adding a question after a student has started, with no lock notice (D11)", async () => {
    mockApi({ [`GET /api/v1/live-exams/${examId}`]: examWith({ started: true }) });
    renderWithProviders(<QuestionPage examId={examId} />, { user: fixtures.masterAdmin });

    expect(await screen.findByRole("button", { name: "নতুন প্রশ্ন" })).toBeInTheDocument();
    expect(screen.queryByText(/শিক্ষার্থী এই পরীক্ষা শুরু করেছে/)).not.toBeInTheDocument();
    expect(screen.getByDisplayValue("প্রশ্ন এক")).toBeInTheDocument();
  });

  it("lets a System Admin delete a question, which the server allows (EXAM_MANAGE)", async () => {
    mockApi({ [`GET /api/v1/live-exams/${examId}`]: exam });
    renderWithProviders(<QuestionPage examId={examId} />, { user: fixtures.systemAdmin });

    expect((await screen.findAllByRole("button", { name: "প্রশ্ন মুছুন" })).length).toBeGreaterThan(0);
  });

  it("moves a question with one reorder call that names the whole new order", async () => {
    const { calls } = mockApi({
      [`GET /api/v1/live-exams/${examId}`]: exam,
      [`POST /api/v1/live-exams/${examId}/questions/reorder`]: { status: 204 },
    });
    renderWithProviders(<QuestionPage examId={examId} />, { user: fixtures.masterAdmin });

    await userEvent.click(await screen.findByRole("button", { name: "নিচে সরান" }));

    await waitFor(() => expect(calls.filter((call) => call.method === "POST")).toHaveLength(1));
    const [reorder] = calls.filter((call) => call.method === "POST");
    expect(reorder.body).toEqual({ questionIds: ["q2", "q1"] });
  });

  it("shows the student view of the draft when the preview is switched on", async () => {
    mockApi({ [`GET /api/v1/live-exams/${examId}`]: exam });
    renderWithProviders(<QuestionPage examId={examId} />, { user: fixtures.masterAdmin });

    await screen.findByDisplayValue("প্রশ্ন এক");
    expect(screen.queryByText("শিক্ষার্থীর দৃশ্য")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("switch", { name: /পূর্বরূপ দেখুন/ }));
    expect(screen.getByText("শিক্ষার্থীর দৃশ্য")).toBeInTheDocument();
  });

  it("sends a written exam to the exam page, since it keeps one question PDF", async () => {
    mockApi({ [`GET /api/v1/live-exams/${examId}`]: examWith({ examType: "WRITTEN", questions: null, questionPdf: true }) });
    renderWithProviders(<QuestionPage examId={examId} />, { user: fixtures.masterAdmin });

    expect(await screen.findByText(/প্রশ্নপত্র PDF থাকে/)).toBeInTheDocument();
  });
});
