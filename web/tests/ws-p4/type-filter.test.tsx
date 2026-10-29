import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { MyExamsPage } from "@/components/student/catalog/my-exams-page";
import { filterByType, parseTypeFilter, typeLabelKey } from "@/components/student/catalog/type-filter";
import { ResultsHub } from "@/components/student/result/results-hub";
import { fixtures, mockApi, renderWithProviders, routerState } from "../helpers";

describe("the type filter values (Phase 4, A1 and A3)", () => {
  it("reads MCQ and WRITTEN from the URL, and anything else as All", () => {
    expect(parseTypeFilter("MCQ")).toBe("MCQ");
    expect(parseTypeFilter("WRITTEN")).toBe("WRITTEN");
    expect(parseTypeFilter("mcq")).toBeNull();
    expect(parseTypeFilter("PACKAGE")).toBeNull();
    expect(parseTypeFilter(null)).toBeNull();
  });

  it("keeps every row for All, and only the chosen type otherwise", () => {
    const rows = [{ id: "a", type: "MCQ" as const }, { id: "b", type: "WRITTEN" as const }, { id: "c", type: undefined }];
    const typeOf = (row: { type?: "MCQ" | "WRITTEN" }) => row.type;
    expect(filterByType(rows, null, typeOf).map((row) => row.id)).toEqual(["a", "b", "c"]);
    expect(filterByType(rows, "MCQ", typeOf).map((row) => row.id)).toEqual(["a"]);
    expect(filterByType(rows, "WRITTEN", typeOf).map((row) => row.id)).toEqual(["b"]);
  });

  it("names the type for empty text", () => {
    expect(typeLabelKey("MCQ")).toBe("catalog.typeFilter.mcq");
    expect(typeLabelKey("WRITTEN")).toBe("catalog.typeFilter.written");
  });
});

const resultExam = (id: string, examType: "MCQ" | "WRITTEN", titleEn: string) => ({
  id,
  moduleId: "m-1",
  titleEn,
  titleBn: titleEn,
  examType,
  liveAt: new Date(Date.now() - 3 * 60 * 60_000).toISOString(),
  myStatus: "RESULT_READY",
});

describe("results hub type filter (A3)", () => {
  it("restores the chosen type from the URL and hides the other type", async () => {
    mockApi({
      "GET /api/v1/live-exam-modules": { content: [{ id: "m-1", titleEn: "BJS", titleBn: "বিজেএস" }], page: 0, size: 50, totalElements: 1, totalPages: 1 },
      "GET /api/v1/live-exam-modules/m-1/exams": {
        content: [resultExam("ex-m", "MCQ", "MCQ paper"), resultExam("ex-w", "WRITTEN", "Written paper")],
        page: 0,
        size: 50,
        totalElements: 2,
        totalPages: 1,
      },
      "GET /api/v1/live-exams/ex-m/result": { score: 38, maximum: 50, passed: true, status: "RESULT_READY", questions: null, comment: null, evaluatedPdf: null },
      "GET /api/v1/live-exams/ex-w/result": { score: 20, maximum: 50, passed: false, status: "RESULT_READY", questions: null, comment: null, evaluatedPdf: null },
    });
    renderWithProviders(<ResultsHub />, { locale: "en", route: "/results?type=WRITTEN" });

    expect(await screen.findByText("Written paper")).toBeInTheDocument();
    expect(screen.queryByText("MCQ paper")).not.toBeInTheDocument();
    const group = screen.getByRole("group", { name: "Exam type" });
    expect(within(group).getByRole("button", { name: "Written" })).toHaveAttribute("aria-pressed", "true");
  });

  it("writes the choice to the URL when a student picks a type", async () => {
    const user = userEvent.setup();
    mockApi({
      "GET /api/v1/live-exam-modules": { content: [{ id: "m-1", titleEn: "BJS", titleBn: "বিজেএস" }], page: 0, size: 50, totalElements: 1, totalPages: 1 },
      "GET /api/v1/live-exam-modules/m-1/exams": { content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 },
    });
    renderWithProviders(<ResultsHub />, { locale: "en", route: "/results" });
    await screen.findByRole("group", { name: "Exam type" });
    await user.click(screen.getByRole("button", { name: "MCQ" }));
    expect(routerState.replace).toHaveBeenCalledWith("/results?type=MCQ", { scroll: false });
  });
});

const access = {
  exams: [
    { examId: "e1", moduleId: "m1", via: "EXAM", endsAt: null, expired: false },
    { examId: "e4", moduleId: "m1", via: "EXAM", endsAt: null, expired: false },
  ],
  modules: [{ moduleId: "m1", via: "ROSTER", endsAt: null, expired: false }],
};

const reads = {
  "GET /api/v1/public/live-exams/e1": { id: "e1", titleEn: "Written one", titleBn: "Written one", examType: "WRITTEN", moduleId: "m1" },
  "GET /api/v1/public/live-exams/e4": { id: "e4", titleEn: "MCQ one", titleBn: "MCQ one", examType: "MCQ", moduleId: "m1" },
  "GET /api/v1/public/live-exam-modules/m1": { id: "m1", titleEn: "Package one", titleBn: "Package one" },
};

describe("My exams type filter (A1)", () => {
  it("restores the chosen type from the URL on the exam tab", async () => {
    mockApi({ "GET /api/v1/me/live-exam-access": access, ...reads });
    renderWithProviders(<MyExamsPage />, { user: fixtures.student, locale: "en", route: "/my-modules?type=MCQ" });

    expect(await screen.findByText("MCQ one")).toBeInTheDocument();
    expect(screen.queryByText("Written one")).not.toBeInTheDocument();
    const group = screen.getByRole("group", { name: "Exam type" });
    expect(within(group).getByRole("button", { name: "MCQ" })).toHaveAttribute("aria-pressed", "true");
  });

  it("hides the type control on the Packages tab, because a package holds both types", async () => {
    const user = userEvent.setup();
    mockApi({ "GET /api/v1/me/live-exam-access": access, ...reads });
    renderWithProviders(<MyExamsPage />, { user: fixtures.student, locale: "en", route: "/my-modules?type=MCQ" });

    await screen.findByText("MCQ one");
    await user.click(screen.getByRole("tab", { name: /Packages/ }));
    await screen.findByText("Package one");
    expect(screen.queryByRole("group", { name: "Exam type" })).not.toBeInTheDocument();
  });
});
