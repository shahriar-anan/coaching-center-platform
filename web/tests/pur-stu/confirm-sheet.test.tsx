import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ExamPage } from "@/components/student/catalog/exam-page";
import { ModulePage } from "@/components/student/catalog/module-page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const exam = {
  id: "ex-1",
  moduleId: "m-1",
  examType: "MCQ",
  titleEn: "CRPC",
  titleBn: "সিআরপিসি",
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
  price: 400,
  questionPdf: null,
  questions: null,
  myStatus: null,
  retakeAvailable: null,
  sitting: null,
};

const moduleRow = {
  id: "m-1",
  categoryId: "c-1",
  titleEn: "Evidence",
  titleBn: "সাক্ষ্য",
  descriptionEn: null,
  descriptionBn: null,
  price: 1200,
  status: "PUBLISHED",
  categoryPath: [],
};

describe("confirm sheet copy (S-005 and the sheet spec)", () => {
  it("an exam sheet says it unlocks this exam only, and its primary button goes to payment", async () => {
    mockApi({
      "GET /api/v1/public/live-exams/ex-1": exam,
      "GET /api/v1/public/exam-site/status": { checkoutEnabled: true },
    });
    renderWithProviders(<ExamPage examId="ex-1" />, { user: fixtures.student, locale: "bn" });

    await userEvent.click(await screen.findByRole("button", { name: /কিনুন/ }));

    expect(await screen.findByText("শুধু এই পরীক্ষাটি খুলবে।")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /নিরাপদে পেমেন্ট করুন/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "বাতিল" })).toBeInTheDocument();
    expect(screen.queryByText("শুধু এই পরীক্ষাটি চালু হবে।")).not.toBeInTheDocument();
  });

  it("a module sheet says it unlocks the current and future exams of the module", async () => {
    mockApi({
      "GET /api/v1/public/live-exam-modules/m-1": moduleRow,
      "GET /api/v1/public/live-exam-modules/m-1/exams": { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 },
      "GET /api/v1/public/exam-site/status": { checkoutEnabled: true },
    });
    renderWithProviders(<ModulePage door="mcq" moduleId="m-1" />, { user: fixtures.student, locale: "bn" });

    await userEvent.click(await screen.findByRole("button", { name: "পুরো মডিউল কিনুন" }));

    expect(await screen.findByText("এই মডিউলের বর্তমান ও ভবিষ্যতের সব পরীক্ষা খুলবে।")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /নিরাপদে পেমেন্ট করুন/ })).toBeInTheDocument();
  });
});
