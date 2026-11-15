import { screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { MyAdmissionPage } from "@/components/student/my-admission/my-admission-page";
import { ResultsSection, ordinalEn } from "@/components/student/my-admission/results-section";
import { useMyAdmissionResults } from "@/lib/api/exam/my-results";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { admission, schedule } from "../my-admission/fixture";
import { absentResult, admissionResults, examResult } from "./fixture";

// The routine viewer loads a PDF in the browser. The page renders it only when the batch has one, so this file never opens it.
vi.mock("@/components/pdf/pdf-viewer", async () => {
  const React = await import("react");
  return {
    PdfViewer: ({ label }: { label?: string }) => React.createElement("div", { "aria-label": label }, "routine viewer"),
  };
});

const RESULTS = "GET /api/v1/me/admissions/adm-1/results";

/** The section alone, reading the admission's results the way the page does. */
function Harness() {
  const results = useMyAdmissionResults("adm-1");
  return <ResultsSection admissionId="adm-1" results={results} />;
}

function renderSection(exams: ReturnType<typeof examResult>[]) {
  const api = mockApi({ [RESULTS]: admissionResults(exams) });
  renderWithProviders(<Harness />, { user: fixtures.student, locale: "en", route: "/my-admission" });
  return api;
}

describe("ordinal wording for a rank", () => {
  it.each([
    [1, "1st"],
    [2, "2nd"],
    [3, "3rd"],
    [4, "4th"],
    [11, "11th"],
    [12, "12th"],
    [13, "13th"],
    [21, "21st"],
    [22, "22nd"],
    [101, "101st"],
    [111, "111th"],
  ])("%i is %s", (rank, text) => {
    expect(ordinalEn(rank)).toBe(text);
  });
});

describe("the results section (plan 7.10)", () => {
  it("shows a published exam with its total, rank, parts and pass, and links to its leaderboard", async () => {
    renderSection([examResult()]);

    expect(await screen.findByText("Constitution · Part 1")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Results" })).toBeInTheDocument();
    expect(document.getElementById("results-adm-1")).not.toBeNull();
    expect(screen.getByText("41.75 / 50")).toBeInTheDocument();
    expect(screen.getByText("3rd of 47")).toBeInTheDocument();
    expect(screen.getByText("Passed")).toBeInTheDocument();
    expect(screen.getByText("MCQ 18.75 · Written 23")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Constitution/ })).toHaveAttribute("href", "/offline-activities/results/adm-1/class-1");
  });

  it("shows the empty state when no exam is published yet", async () => {
    renderSection([]);

    expect(await screen.findByText("No results yet. They appear here when the office publishes them.")).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("tags a zero result as might be absent", async () => {
    renderSection([examResult({ total: 0, mcq: 0, written: 0, maybeAbsent: true, rank: 1, passed: false })]);

    expect(await screen.findByText("might be absent")).toBeInTheDocument();
    expect(screen.getByText("0 / 50")).toBeInTheDocument();
    expect(screen.getByText("Failed")).toBeInTheDocument();
  });

  it("shows Absent, with no total or rank, for a student with no row", async () => {
    renderSection([absentResult()]);

    expect(await screen.findByText("Absent")).toBeInTheDocument();
    expect(screen.queryByText("41.75 / 50")).not.toBeInTheDocument();
    expect(screen.queryByText(/of 47/)).not.toBeInTheDocument();
  });

  it("shows pass or fail only when the exam has a pass mark", async () => {
    renderSection([examResult({ passMark: null, passed: null })]);

    expect(await screen.findByText("41.75 / 50")).toBeInTheDocument();
    expect(screen.queryByText("Passed")).not.toBeInTheDocument();
    expect(screen.queryByText("Failed")).not.toBeInTheDocument();
  });
});

describe("where the section appears on the admission page", () => {
  it("is not shown for a pending admission, and its results are not read", async () => {
    const api = mockApi({ "GET /api/v1/me/admission": admission("SUBMITTED") });
    renderWithProviders(<MyAdmissionPage />, { user: fixtures.student, locale: "en", route: "/my-admission" });

    // A pending admission is a card with its step: nothing else of the admission page, and no results.
    expect(await screen.findByText(/Under review/)).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Results" })).not.toBeInTheDocument();
    expect(api.calls.some((call) => call.path.endsWith("/results"))).toBe(false);
  });

  it("sits after the routine for an onboarded admission, and the glance strip shows the last result", async () => {
    mockApi({
      "GET /api/v1/me/admission": admission("ONBOARDED"),
      "GET /api/v1/me/admission/fees": schedule(),
      "GET /api/v1/me/admission/items": [],
      "GET /api/v1/me/admission/routine": [],
      [RESULTS]: admissionResults([examResult()]),
    });
    renderWithProviders(<MyAdmissionPage />, { user: fixtures.student, locale: "en", route: "/my-admission" });

    const headings = (await screen.findAllByRole("heading", { level: 2 })).map((h) => h.textContent);
    expect(headings).toEqual(["Routine", "Results", "Payments", "Exams and packages", "My form"]);

    const glance = await screen.findByRole("region", { name: "At a glance" });
    await waitFor(() => expect(glance).toHaveTextContent("Last result"));
    expect(within(glance).getByText("41.75 / 50")).toBeInTheDocument();
    expect(within(glance).getByText("3rd of 47")).toBeInTheDocument();
  });
});
