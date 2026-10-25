import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ExamPage } from "@/components/student/catalog/exam-page";
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

function renderExam(status: Record<string, unknown> | "missing") {
  const routes: Record<string, unknown> = {
    "GET /api/v1/public/live-exams/ex-1": exam,
  };
  if (status === "missing") {
    // no route: the mock answers 404 like an older API
  } else {
    routes["GET /api/v1/public/exam-site/status"] = status;
  }
  mockApi(routes);
  return renderWithProviders(<ExamPage examId="ex-1" />, { user: fixtures.student, locale: "bn" });
}

describe("checkout state on the exam page (S-005)", () => {
  it("checkout on: no notice, and the buy button works", async () => {
    renderExam({ checkoutEnabled: true });
    const buy = await screen.findByRole("button", { name: /কিনুন/ });
    expect(buy).toBeEnabled();
    expect(screen.queryByText("পেমেন্ট এখন চালু নেই। কিছুক্ষণ পরে আবার চেষ্টা করুন।")).not.toBeInTheDocument();
  });

  it("checkout off: the notice shows and every buy button is disabled", async () => {
    renderExam({ checkoutEnabled: false });
    expect(await screen.findByText("পেমেন্ট এখন চালু নেই। কিছুক্ষণ পরে আবার চেষ্টা করুন।")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /কিনুন/ })).toBeDisabled();
  });

  it("a missing status endpoint (older API) counts as on and says nothing", async () => {
    renderExam("missing");
    const buy = await screen.findByRole("button", { name: /কিনুন/ });
    expect(buy).toBeEnabled();
    expect(screen.queryByText("পেমেন্ট এখন চালু নেই। কিছুক্ষণ পরে আবার চেষ্টা করুন।")).not.toBeInTheDocument();
  });
});
