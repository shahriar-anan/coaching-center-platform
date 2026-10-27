import { describe, expect, it } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { SubmittedPage } from "@/components/student/sitting/submitted-page";
import { fixtures, mockApi, renderWithProviders, routerState } from "../helpers";

function exam(extra: Record<string, unknown>) {
  return {
    id: "e1",
    moduleId: "m1",
    examType: "WRITTEN",
    titleEn: "Written paper",
    titleBn: "রিটেন পত্র",
    sitting: { kind: "LIVE", startedAt: "2026-10-27T10:00:00Z", deadlineAt: "2099-01-01T10:00:00Z", submittedAt: null },
    ...extra,
  };
}

describe("submitted page", () => {
  it("tells the student the script was received and is final, even while time remains", async () => {
    mockApi({ "GET /api/v1/live-exams/e1": exam({ answerStored: true }) });
    renderWithProviders(<SubmittedPage examId="e1" />, { user: fixtures.student, route: "/exam/e1/submitted" });

    expect(await screen.findByRole("heading", { name: "আপনার খাতা জমা হয়েছে" })).toBeInTheDocument();
    expect(screen.getByText(/রিটেন পত্র/)).toBeInTheDocument();
    expect(screen.getByText("আপনার খাতা চূড়ান্ত, এটি আর বদলানো যাবে না।")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "খাতা বদলে দিন" })).toBeNull();
    expect(routerState.replace).not.toHaveBeenCalled();
  });

  it("says the script is final once time has ended, with no replace link", async () => {
    mockApi({
      "GET /api/v1/live-exams/e1": exam({
        answerStored: true,
        sitting: { kind: "LIVE", startedAt: "2020-01-01T10:00:00Z", deadlineAt: "2020-01-01T11:00:00Z", submittedAt: null },
      }),
    });
    renderWithProviders(<SubmittedPage examId="e1" />, { user: fixtures.student, route: "/exam/e1/submitted" });

    expect(await screen.findByText("আপনার খাতা চূড়ান্ত, এটি আর বদলানো যাবে না।")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "খাতা বদলে দিন" })).toBeNull();
  });

  it("sends a student with no stored script back to the exam", async () => {
    mockApi({ "GET /api/v1/live-exams/e1": exam({ answerStored: false }) });
    renderWithProviders(<SubmittedPage examId="e1" />, { user: fixtures.student, route: "/exam/e1/submitted" });

    await waitFor(() => expect(routerState.replace).toHaveBeenCalledWith("/exam/e1/sit"));
    expect(screen.queryByRole("heading", { name: "আপনার খাতা জমা হয়েছে" })).toBeNull();
  });
});
