import { describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import { SubmissionBrowser } from "@/components/staff/marking/submission-browser";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { participation, serveFeed } from "./fixtures";

const TRUNCATED = "প্রথম ১০০টি পরীক্ষা দেখানো হচ্ছে, তাই কিছু পরীক্ষা ও তার সংখ্যা বাদ পড়েছে।";

describe("the browser says when the exam list is cut at one page (P4)", () => {
  it("warns when the participation list holds more exams than one page", async () => {
    mockApi({
      "GET /api/v1/live-exam-participation": { content: participation, page: 0, size: 100, totalElements: 140, totalPages: 2 },
      "GET /api/v1/live-exam-submissions": serveFeed(),
    });
    renderWithProviders(<SubmissionBrowser />, { user: fixtures.instructor, route: "/staff/submissions" });
    expect(await screen.findByText(TRUNCATED)).toBeInTheDocument();
  });

  it("shows no warning when every exam fits on the page", async () => {
    mockApi({
      "GET /api/v1/live-exam-participation": { content: participation, page: 0, size: 100, totalElements: 3, totalPages: 1 },
      "GET /api/v1/live-exam-submissions": serveFeed(),
    });
    renderWithProviders(<SubmissionBrowser />, { user: fixtures.instructor, route: "/staff/submissions" });
    expect((await screen.findAllByText("রহিম উদ্দিন")).length).toBeGreaterThan(0);
    expect(screen.queryByText(TRUNCATED)).toBeNull();
  });
});
