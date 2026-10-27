import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AnswerReleaseSwitch } from "@/components/staff/access/answer-release-switch";
import { fixtures, renderWithProviders } from "../helpers";
import { mockApi } from "../helpers/mock-api";
import "./copy-setup";

const examId = "eeeeeeee-0000-4000-8000-000000000001";

describe("AnswerReleaseSwitch", () => {
  it("is not shown for a written paper", async () => {
    mockApi({
      [`GET /api/v1/live-exams/${examId}`]: {
        id: examId,
        examType: "WRITTEN",
        titleEn: "CPC written",
        titleBn: "সিপিসি লিখিত",
        answersReleasedAt: null,
      },
    });
    const { calls } = mockApi({
      [`GET /api/v1/live-exams/${examId}`]: {
        id: examId,
        examType: "WRITTEN",
        titleEn: "CPC written",
        titleBn: "সিপিসি লিখিত",
        answersReleasedAt: null,
      },
    });
    renderWithProviders(<AnswerReleaseSwitch examId={examId} />, { user: fixtures.masterAdmin });

    await waitFor(() => expect(calls).toHaveLength(1));
    await waitFor(() => expect(screen.queryByRole("switch")).not.toBeInTheDocument());
    expect(screen.queryByText("উত্তর প্রকাশ")).not.toBeInTheDocument();
  });

  it("names the exam in the confirm dialog, then releases the answers", async () => {
    const { calls } = mockApi({
      [`GET /api/v1/live-exams/${examId}`]: {
        id: examId,
        examType: "MCQ",
        titleEn: "CRPC",
        titleBn: "সিআরপিসি",
        answersReleasedAt: null,
      },
      [`PUT /api/v1/live-exams/${examId}/answers-released`]: { examId, released: true, releasedAt: "2026-10-26T06:00:00Z" },
    });
    renderWithProviders(<AnswerReleaseSwitch examId={examId} />, { user: fixtures.masterAdmin });
    const user = userEvent.setup();

    expect(await screen.findByText("উত্তর এখনো প্রকাশ হয়নি")).toBeInTheDocument();
    await user.click(screen.getByRole("switch"));
    expect(await screen.findByText("সিআরপিসি এর উত্তর প্রকাশ করবেন?")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "নিশ্চিত করুন" }));

    await waitFor(() => expect(calls.find((call) => call.method === "PUT")?.body).toEqual({ released: true }));
  });
});
