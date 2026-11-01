import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ResultsHub } from "@/components/student/result/results-hub";
import type { MyExamResult } from "@/lib/api/exam/sitting";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const MINUTE = 60_000;

/** One row of `GET /api/v1/me/exam-results`: the latest attempt of an exam. */
function row(overrides: Partial<MyExamResult>): MyExamResult {
  return {
    examId: "ex-1",
    titleEn: "Exam",
    titleBn: "Exam",
    examType: "MCQ",
    liveAt: new Date(Date.now() - 60 * MINUTE).toISOString(),
    passPercent: 40,
    status: "SUBMITTED",
    detail: "SUBMITTED",
    kind: "LIVE",
    attempts: 1,
    submittedAt: new Date().toISOString(),
    resultVisible: false,
    maximum: 3,
    ...overrides,
  };
}

function page(content: MyExamResult[]) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

function renderHub(rows: MyExamResult[]) {
  mockApi({
    "GET /api/v1/me/exam-results": page(rows),
    "GET /api/v1/me/reviews": [],
  });
  return renderWithProviders(<ResultsHub />, { user: fixtures.student, locale: "en" });
}

describe("the results hub places each status", () => {
  it("an MCQ submitted with its result visible is under Results at once", async () => {
    renderHub([row({ examId: "mcq-done", titleEn: "Submitted MCQ", resultVisible: true, score: 2, passed: true })]);
    expect(await screen.findByText("Submitted MCQ")).toBeInTheDocument();
    expect(screen.getByText(/2 \/ 3/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Submitted MCQ/ })).toHaveAttribute("href", "/exam/mcq-done/result");
  });

  it("a written script submitted and not marked is under Being marked, not In progress", async () => {
    const user = userEvent.setup();
    renderHub([row({ examId: "written-sent", titleEn: "Sent script", examType: "WRITTEN", status: "SUBMITTED", detail: "SUBMITTED", resultVisible: false, score: undefined, passed: undefined })]);

    expect(await screen.findByText("No results yet")).toBeInTheDocument();
    await user.click(screen.getByRole("tab", { name: /Being marked/ }));
    expect(screen.getByText("Sent script")).toBeInTheDocument();
    expect(screen.getByText("Script pending")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Sent script/ })).toHaveAttribute("href", "/exam/written-sent/result");

    await user.click(screen.getByRole("tab", { name: /In progress/ }));
    expect(screen.queryByText("Sent script")).not.toBeInTheDocument();
    expect(screen.getByText("No exams in progress.")).toBeInTheDocument();
  });

  it("a written script waiting for its mark is under Being marked", async () => {
    const user = userEvent.setup();
    renderHub([row({ examId: "written-wait", titleEn: "Waiting script", examType: "WRITTEN", status: "AWAITING_RESULT", detail: "AWAITING_RESULT", resultVisible: false, score: undefined, passed: undefined })]);

    await user.click(await screen.findByRole("tab", { name: /Being marked/ }));
    expect(screen.getByText("Waiting script")).toBeInTheDocument();
  });

  it("an attempt still in progress is under In progress only", async () => {
    const user = userEvent.setup();
    renderHub([row({ examId: "open-now", titleEn: "Open attempt", status: "IN_PROGRESS", detail: "IN_PROGRESS", submittedAt: null, resultVisible: false, score: undefined, passed: undefined })]);

    await user.click(await screen.findByRole("tab", { name: /In progress/ }));
    expect(screen.getByText("Open attempt")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Open attempt/ })).toHaveAttribute("href", "/exam/open-now");

    await user.click(screen.getByRole("tab", { name: /Results/ }));
    expect(screen.queryByText("Open attempt")).not.toBeInTheDocument();
    await user.click(screen.getByRole("tab", { name: /Being marked/ }));
    expect(screen.queryByText("Open attempt")).not.toBeInTheDocument();
  });

  it("the summary counts the scripts being marked, including a submitted one", async () => {
    renderHub([
      row({ examId: "a", titleEn: "Done", resultVisible: true, score: 1, passed: false }),
      row({ examId: "b", titleEn: "Sent", examType: "WRITTEN", resultVisible: false, score: undefined, passed: undefined }),
      row({ examId: "c", titleEn: "Open", status: "IN_PROGRESS", detail: "IN_PROGRESS", submittedAt: null, resultVisible: false }),
    ]);
    expect(await screen.findByText("Done")).toBeInTheDocument();
    // Taken: the one result. Waiting: the submitted script. The open attempt counts for neither.
    expect(within(screen.getByText("Exams taken").parentElement as HTMLElement).getByText("1")).toBeInTheDocument();
    expect(within(screen.getByText("Results waiting").parentElement as HTMLElement).getByText("1")).toBeInTheDocument();
  });
});
