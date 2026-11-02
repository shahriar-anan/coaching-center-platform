import { Suspense } from "react";
import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import LeaderboardPage from "@/app/(staff)/staff/exams/[examId]/leaderboard/page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const EXAM = "eeeeeeee-0000-4000-8000-000000000002";

/** A promise React reads at once, as Next does for route params, so `use()` does not suspend in a test. */
function resolved<T>(value: T): Promise<T> {
  const promise = Promise.resolve(value) as Promise<T> & { status?: string; value?: T };
  promise.status = "fulfilled";
  promise.value = value;
  return promise;
}

function rowFor(name: string) {
  const row = screen.getAllByRole("row").find((item) => item.textContent?.includes(name));
  if (!row) {
    throw new Error(`no row for ${name}`);
  }
  return within(row);
}

describe("staff leaderboard percent (D15)", () => {
  it("shows each attempt's score over its own maximum, and its percent to two decimals", async () => {
    mockApi({
      [`GET /api/v1/live-exams/${EXAM}`]: {
        id: EXAM,
        moduleId: "bbbbbbbb-0000-4000-8000-000000000001",
        examType: "MCQ",
        titleEn: "Penal Code",
        titleBn: "পেনাল কোড",
        liveAt: "2026-10-24T06:00:00Z",
        endAt: "2026-10-24T06:40:00Z",
        status: "PUBLISHED",
      },
      [`GET /api/v1/live-exams/${EXAM}/leaderboard`]: {
        rows: [{ name: "Rahim", rank: 1, score: 42, maximum: 50, percent: 84, passed: true }],
      },
    });
    renderWithProviders(
      <Suspense fallback={null}>
        <LeaderboardPage params={resolved({ examId: EXAM })} />
      </Suspense>,
      { user: fixtures.masterAdmin, route: `/staff/exams/${EXAM}/leaderboard`, locale: "en" },
    );

    // The score is shown once per layout (table and stacked card), so the test accepts one or more.
    expect((await screen.findAllByText("42 / 50")).length).toBeGreaterThan(0);
    expect(rowFor("Rahim").getByText("84.00%")).toBeInTheDocument();
  });

  it("shows a dash for the percent when the attempt's maximum is 0", async () => {
    mockApi({
      [`GET /api/v1/live-exams/${EXAM}`]: {
        id: EXAM,
        moduleId: "bbbbbbbb-0000-4000-8000-000000000001",
        examType: "WRITTEN",
        titleEn: "Essay",
        titleBn: "রচনা",
        liveAt: "2026-10-24T06:00:00Z",
        endAt: "2026-10-24T06:40:00Z",
        status: "PUBLISHED",
      },
      [`GET /api/v1/live-exams/${EXAM}/leaderboard`]: {
        rows: [{ name: "Nazma", rank: 1, score: 0, maximum: 0, percent: null, passed: false }],
      },
    });
    renderWithProviders(
      <Suspense fallback={null}>
        <LeaderboardPage params={resolved({ examId: EXAM })} />
      </Suspense>,
      { user: fixtures.masterAdmin, route: `/staff/exams/${EXAM}/leaderboard`, locale: "en" },
    );

    expect((await screen.findAllByText("0 / 0")).length).toBeGreaterThan(0);
    expect(rowFor("Nazma").getByText("—")).toBeInTheDocument();
    expect(screen.queryByText(/%$/)).not.toBeInTheDocument();
  });
});
