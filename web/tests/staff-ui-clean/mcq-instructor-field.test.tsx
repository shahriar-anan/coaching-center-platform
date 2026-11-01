import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ExamEditor } from "@/components/staff/catalog/exam-editor";
import type { StaffExam, StaffModule } from "@/lib/api/exam/staff-catalog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const moduleId = "aaaaaaaa-0000-0000-0000-000000000041";
const examId = "eeeeeeee-0000-0000-0000-000000000041";

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

const baseExam: StaffExam = {
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
    { id: "q1", stem: "One", marks: 20, explanation: null, position: 1, options: [{ id: "o1", text: "A", correct: true, position: 1 }] },
  ],
};

const writtenExam: StaffExam = {
  ...baseExam,
  examType: "WRITTEN",
  titleEn: "CPC",
  titleBn: "সিপিসি",
  maximum: 100,
  questionPdf: true,
  questions: null,
};

function routes(exam: StaffExam) {
  return {
    [`GET /api/v1/live-exams/${exam.id}`]: exam,
    [`GET /api/v1/live-exam-modules/${moduleId}`]: moduleFixture,
  };
}

describe("the instructor field in the exam details", () => {
  it("an MCQ exam hides the instructor field and says it has no instructor", async () => {
    mockApi(routes(baseExam));
    renderWithProviders(<ExamEditor examId={examId} />, { user: fixtures.masterAdmin, locale: "en" });

    expect(await screen.findByText("An MCQ exam has no instructor.")).toBeInTheDocument();
    expect(screen.queryByLabelText("Instructor")).toBeNull();
    expect(screen.queryByRole("button", { name: "Assign" })).toBeNull();
  });

  it("a written exam keeps the instructor field for the Master Admin", async () => {
    mockApi(routes(writtenExam));
    renderWithProviders(<ExamEditor examId={examId} />, { user: fixtures.masterAdmin, locale: "en" });

    expect(await screen.findByLabelText("Instructor")).toBeInTheDocument();
    expect(screen.queryByText("An MCQ exam has no instructor.")).toBeNull();
  });
});
