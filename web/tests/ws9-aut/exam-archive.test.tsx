import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ExamEditor } from "@/components/staff/catalog/exam-editor";
import type { StaffExam, StaffModule } from "@/lib/api/exam/staff-catalog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const moduleId = "aaaaaaaa-0000-0000-0000-000000000001";
const examId = "eeeeeeee-0000-0000-0000-000000000005";

const moduleFixture: StaffModule = {
  id: moduleId,
  categoryId: "cccccccc-0000-0000-0000-000000000001",
  titleEn: "19th BCS",
  titleBn: "১৯তম বিজেএস",
  descriptionEn: null,
  descriptionBn: null,
  accessMode: "MANUAL",
  status: "PUBLISHED",
  finallySaved: true,
  position: 1,
  price: 2500,
  instructor: null,
};

const publishedExam: StaffExam = {
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
  status: "PUBLISHED",
  finallySaved: true,
  price: null,
  questionPdf: null,
  questions: [
    { id: "q1", stem: "এক", marks: 20, explanation: null, position: 1, options: [{ id: "o1", text: "ক", correct: true, position: 1 }] },
  ],
};

const archivedExam: StaffExam = { ...publishedExam, status: "ARCHIVED" };

describe("exam archive (decided 2026-10-27: hidden from students, Master Admin only)", () => {
  it("Master Admin archives a published exam after confirming, and the editor shows it archived", async () => {
    const api = mockApi({
      [`GET /api/v1/live-exams/${examId}`]: publishedExam,
      [`GET /api/v1/live-exam-modules/${moduleId}`]: moduleFixture,
      [`POST /api/v1/live-exams/${examId}/archive`]: archivedExam,
    });
    renderWithProviders(<ExamEditor examId={examId} />, { user: fixtures.masterAdmin });

    await userEvent.click(await screen.findByRole("button", { name: "আর্কাইভ করুন" }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "আর্কাইভ করুন" }));

    await waitFor(() =>
      expect(api.calls.some((call) => call.method === "POST" && call.path === `/api/v1/live-exams/${examId}/archive`)).toBe(true),
    );
    expect(await screen.findByText("এই পরীক্ষা আর্কাইভ করা হয়েছে। শিক্ষার্থীরা এটি দেখতে পাবে না। প্রকাশিত ফল দেখা যাবে। এটি বদলানো যাবে না।")).toBeInTheDocument();
  });

  it("System Admin sees no archive control on the exam", async () => {
    mockApi({
      [`GET /api/v1/live-exams/${examId}`]: publishedExam,
      [`GET /api/v1/live-exam-modules/${moduleId}`]: moduleFixture,
    });
    renderWithProviders(<ExamEditor examId={examId} />, { user: fixtures.systemAdmin });

    await waitFor(() => expect(screen.getAllByText("সিআরপিসি").length).toBeGreaterThan(0));
    expect(screen.queryByRole("button", { name: "আর্কাইভ করুন" })).not.toBeInTheDocument();
  });

  it("an archived exam shows its notice and offers no archive control", async () => {
    mockApi({
      [`GET /api/v1/live-exams/${examId}`]: archivedExam,
      [`GET /api/v1/live-exam-modules/${moduleId}`]: moduleFixture,
    });
    renderWithProviders(<ExamEditor examId={examId} />, { user: fixtures.masterAdmin });

    expect(await screen.findByText("এই পরীক্ষা আর্কাইভ করা হয়েছে। শিক্ষার্থীরা এটি দেখতে পাবে না। প্রকাশিত ফল দেখা যাবে। এটি বদলানো যাবে না।")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "আর্কাইভ করুন" })).not.toBeInTheDocument();
  });
});
