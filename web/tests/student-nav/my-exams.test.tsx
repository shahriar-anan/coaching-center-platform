import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { MyExamsPage } from "@/components/student/catalog/my-exams-page";
import { accessEnd, orderedRows } from "@/components/student/catalog/my-exams-rows";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

describe("access end for a My exams row (S1.3)", () => {
  it("reads an expired grant as expired, whatever its date", () => {
    expect(accessEnd({ endsAt: "2020-01-01T00:00:00Z", expired: true })).toEqual({ kind: "expired" });
  });

  it("reads a grant with no end date as lifetime", () => {
    expect(accessEnd({ endsAt: null, expired: false })).toEqual({ kind: "lifetime" });
    expect(accessEnd({})).toEqual({ kind: "lifetime" });
  });

  it("reads a live grant with an end date as active until that date", () => {
    expect(accessEnd({ endsAt: "2099-01-01T00:00:00Z", expired: false })).toEqual({
      kind: "until",
      endsAt: "2099-01-01T00:00:00Z",
    });
  });
});

describe("row order on a tab", () => {
  it("lists the live access first and the expired rows after, each group in the API's order", () => {
    const rows = [
      { id: "a", expired: true },
      { id: "b", expired: false },
      { id: "c", expired: true },
      { id: "d" },
    ];
    expect(orderedRows(rows).map((row) => row.id)).toEqual(["b", "d", "a", "c"]);
  });
});

const access = {
  exams: [
    { examId: "e1", moduleId: "m1", via: "EXAM", endsAt: "2099-01-01T00:00:00Z", expired: false },
    { examId: "e2", moduleId: "m1", via: "EXAM", endsAt: "2020-01-01T00:00:00Z", expired: true },
    { examId: "e3", moduleId: "m2", via: "MANUAL", endsAt: null, expired: false },
  ],
  modules: [
    { moduleId: "m1", via: "ROSTER", endsAt: null, expired: false },
    { moduleId: "m2", via: "MODULE", endsAt: "2020-01-01T00:00:00Z", expired: true },
  ],
};

const exam = (id: string, titleEn: string) => ({
  id,
  titleEn,
  titleBn: titleEn,
  examType: "WRITTEN",
  moduleId: "m1",
});

/** The list row that holds a title (the breadcrumbs are list items too). */
function rowOf(title: string): HTMLElement {
  const row = screen.getByText(title).closest("li");
  if (!row) throw new Error(`no row for ${title}`);
  return row;
}

const reads = {
  "GET /api/v1/public/live-exams/e1": exam("e1", "Written one"),
  "GET /api/v1/public/live-exams/e2": exam("e2", "Written two"),
  "GET /api/v1/public/live-exams/e3": exam("e3", "Written three"),
  "GET /api/v1/public/live-exam-modules/m1": { id: "m1", titleEn: "Package one", titleBn: "Package one" },
  "GET /api/v1/public/live-exam-modules/m2": { id: "m2", titleEn: "Package two", titleBn: "Package two" },
};

describe("My exams page (S1.3)", () => {
  it("lists the exams with their access state, live first and expired last", async () => {
    mockApi({ "GET /api/v1/me/live-exam-access": access, ...reads });
    renderWithProviders(<MyExamsPage />, { user: fixtures.student, locale: "en" });

    await screen.findByText("Written one");
    const titles = screen.getAllByRole("link").map((link) => link.textContent).filter((text) => text?.startsWith("Written"));
    expect(titles).toEqual(["Written one", "Written three", "Written two"]);
    expect(within(rowOf("Written one")).getByText(/Access until/)).toBeInTheDocument();
    expect(within(rowOf("Written three")).getByText("Lifetime access")).toBeInTheDocument();
    expect(within(rowOf("Written two")).getByText("Expired")).toBeInTheDocument();
  });

  it("shows the packages on their own tab, with the expired one marked", async () => {
    mockApi({ "GET /api/v1/me/live-exam-access": access, ...reads });
    renderWithProviders(<MyExamsPage />, { user: fixtures.student, locale: "en" });

    await screen.findByText("Written one");
    await userEvent.click(screen.getByRole("tab", { name: /Packages/ }));

    await screen.findByText("Package one");
    expect(within(rowOf("Package one")).getByText("Lifetime access")).toBeInTheDocument();
    expect(within(rowOf("Package two")).getByText("Expired")).toBeInTheDocument();
    expect(screen.queryByText("Written one")).not.toBeInTheDocument();
  });

  it("shows an empty state for each tab that has no rows", async () => {
    mockApi({ "GET /api/v1/me/live-exam-access": { exams: [], modules: [] } });
    renderWithProviders(<MyExamsPage />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByText("No exams yet")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("tab", { name: /Packages/ }));
    expect(await screen.findByText("No packages yet")).toBeInTheDocument();
    expect(screen.queryByText("No exams yet")).not.toBeInTheDocument();
  });
});
