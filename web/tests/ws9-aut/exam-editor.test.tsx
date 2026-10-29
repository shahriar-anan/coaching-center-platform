import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ExamEditor } from "@/components/staff/catalog/exam-editor";
import type { StaffExam, StaffModule } from "@/lib/api/exam/staff-catalog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const moduleId = "aaaaaaaa-0000-0000-0000-000000000001";
const examId = "eeeeeeee-0000-0000-0000-000000000003";

const moduleFixture: StaffModule = {
  id: moduleId,
  categoryId: "cccccccc-0000-0000-0000-000000000001",
  titleEn: "19th BCS",
  titleBn: "১৯তম বিজেএস",
  descriptionEn: null,
  descriptionBn: null,
  accessMode: "MANUAL",
  status: "DRAFT",
  finallySaved: true,
  position: 1,
  price: 2500,
  instructor: null,
};

const mcqExam: StaffExam = {
  id: examId,
  moduleId,
  examType: "MCQ",
  paperLanguage: "BN",
  titleEn: "CrPC",
  titleBn: "সিআরপিসি",
  descriptionEn: "Demo description",
  descriptionBn: "ডেমো বিবরণ",
  rulesEn: "Read every question first.",
  rulesBn: "প্রতিটি প্রশ্ন আগে পড়ুন।",
  liveAt: null,
  endAt: null,
  durationMinutes: 40,
  passPercent: 40,
  maximum: 50,
  status: "DRAFT",
  finallySaved: true,
  price: null,
  questionPdf: null,
  questions: [
    { id: "q1", stem: "এক", marks: 20, explanation: null, position: 1, options: [{ id: "o1", text: "ক", correct: true, position: 1 }] },
    { id: "q2", stem: "দুই", marks: 30, explanation: null, position: 2, options: [{ id: "o2", text: "খ", correct: true, position: 1 }] },
  ],
};

const writtenExam: StaffExam = {
  ...mcqExam,
  examType: "WRITTEN",
  titleEn: "CPC",
  titleBn: "সিপিসি",
  maximum: 100,
  questionPdf: true,
  questions: null,
};

function routes(exam: StaffExam, module: StaffModule = moduleFixture) {
  return {
    [`GET /api/v1/live-exams/${exam.id}`]: exam,
    [`GET /api/v1/live-exam-modules/${moduleId}`]: module,
  };
}

describe("exam editor sections (U5)", () => {
  it("numbers the parts in journey order and links each one from the section nav", async () => {
    mockApi(routes(mcqExam));
    renderWithProviders(<ExamEditor examId={examId} />, { user: fixtures.masterAdmin });

    // The six parts of plan 7.2, in the order the work is done.
    for (const name of [/১\s*বিবরণ/, /২\s*বিবরণ ও নিয়মাবলি/, /৩\s*সময় ও নম্বর/, /৪\s*প্রশ্ন/, /৫\s*মূল্য/, /৬\s*যাচাই ও প্রকাশ/]) {
      expect(await screen.findByRole("heading", { name })).toBeInTheDocument();
    }
    expect(screen.getByRole("link", { name: /৩\s*সময় ও নম্বর/ })).toHaveAttribute("href", "#exam-schedule");
  });

  it("System Admin sees the price as text and no delete control", async () => {
    mockApi(routes(mcqExam));
    renderWithProviders(<ExamEditor examId={examId} />, { user: fixtures.systemAdmin });

    expect(await screen.findByText("মূল্য শুধু মাস্টার অ্যাডমিন ঠিক করতে পারেন।")).toBeInTheDocument();
    expect(screen.queryByLabelText("মূল্য (৳)")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "পরীক্ষা মুছুন" })).not.toBeInTheDocument();
  });

  it("System Admin can delete a question, which the server allows (EXAM_MANAGE)", async () => {
    mockApi(routes(mcqExam));
    renderWithProviders(<ExamEditor examId={examId} />, { user: fixtures.systemAdmin });

    expect((await screen.findAllByRole("button", { name: "প্রশ্ন মুছুন" })).length).toBeGreaterThan(0);
  });

  it("System Admin is told who assigns the teacher for a written exam without one", async () => {
    mockApi(routes(writtenExam));
    renderWithProviders(<ExamEditor examId={examId} />, { user: fixtures.systemAdmin });

    expect(await screen.findByText("এই প্যাকেজে এখনো শিক্ষক দেওয়া নেই। শিক্ষক দেবেন মাস্টার অ্যাডমিন।")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "শিক্ষক দিন" })).not.toBeInTheDocument();
  });

  it("Master Admin gets a link to assign the teacher for a written exam without one", async () => {
    mockApi(routes(writtenExam));
    renderWithProviders(<ExamEditor examId={examId} />, { user: fixtures.masterAdmin });

    const link = await screen.findByRole("link", { name: "শিক্ষক দিন" });
    expect(link).toHaveAttribute("href", `/staff/modules/${moduleId}?tab=instructor`);
  });
});

describe("publish summary and next step (UF7, F3)", () => {
  it("states what students get, then publishes and names the next step", async () => {
    const api = mockApi({
      ...routes(mcqExam),
      [`POST /api/v1/live-exams/${examId}/final-save`]: { ...mcqExam, finallySaved: true },
      [`POST /api/v1/live-exams/${examId}/publish`]: { ...mcqExam, status: "PUBLISHED" },
    });
    renderWithProviders(<ExamEditor examId={examId} />, { user: fixtures.masterAdmin });

    await userEvent.click(await screen.findByRole("button", { name: "প্রকাশ করুন" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("প্রকাশের আগে দেখে নিন")).toBeInTheDocument();
    expect(within(dialog).getByText("২টি প্রশ্ন · মোট ৫০ নম্বর")).toBeInTheDocument();
    expect(within(dialog).getByText("মূল্য এখনো ঠিক হয়নি")).toBeInTheDocument();
    expect(within(dialog).getByText(/প্যাকেজে, এমসিকিউ দরজায়/)).toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole("button", { name: "প্রকাশ করুন" }));
    await waitFor(() => expect(api.calls.some((call) => call.method === "POST" && call.path.endsWith("/publish"))).toBe(true));
    expect(await screen.findByText(/পরবর্তী ধাপ: সরাসরি ভর্তি করুন।/)).toBeInTheDocument();
  });

  it("System Admin has no publish button, and a note says only the Master Admin publishes", async () => {
    mockApi(routes(mcqExam));
    renderWithProviders(<ExamEditor examId={examId} />, { user: fixtures.systemAdmin });

    expect(await screen.findByText("শুধু মাস্টার অ্যাডমিন পরীক্ষা প্রকাশ করতে পারেন।")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "প্রকাশ করুন" })).not.toBeInTheDocument();
  });

  it("Master Admin cannot publish until the descriptions and rules are filled in", async () => {
    mockApi(routes({ ...mcqExam, rulesBn: null }));
    renderWithProviders(<ExamEditor examId={examId} />, { user: fixtures.masterAdmin });

    expect(await screen.findByRole("button", { name: "প্রকাশ করুন" })).toBeDisabled();
    expect(screen.getByText(/বিবরণ এবং নির্দেশনা যোগ করুন/)).toBeInTheDocument();
  });

  it("the summary can be cancelled without publishing", async () => {
    const api = mockApi(routes(mcqExam));
    renderWithProviders(<ExamEditor examId={examId} />, { user: fixtures.masterAdmin });

    await userEvent.click(await screen.findByRole("button", { name: "প্রকাশ করুন" }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "বাতিল" }));
    expect(api.calls.some((call) => call.method === "POST")).toBe(false);
  });
});
