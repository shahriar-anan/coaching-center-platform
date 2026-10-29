import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CategoryExams } from "@/components/student/catalog/category-exams";
import { catalogKeys, categoryExamsUrl } from "@/lib/api/exam/catalog";
import { mockApi, renderWithProviders } from "../helpers";

const exam = (id: string, titleEn: string) => ({
  id,
  categoryId: "c1",
  examType: "MCQ",
  mode: "LIVE",
  phase: "UPCOMING",
  titleEn,
  titleBn: titleEn,
  liveAt: "2099-01-01T00:00:00Z",
  endAt: "2099-01-01T00:20:00Z",
  popular: false,
  pricing: null,
  packages: [],
});

const page = (content: unknown[], pageNumber: number, totalPages: number) => ({
  content,
  page: pageNumber,
  size: 20,
  totalElements: content.length,
  totalPages,
});

describe("the category exam list (S2.1)", () => {
  it("starts with the whole branch, and the switch turns it to the category's own exams", async () => {
    const { calls } = mockApi({
      "GET /api/v1/public/live-exam-categories/c1/exams": () => page([exam("e1", "First exam")], 0, 1),
    });
    renderWithProviders(<CategoryExams categoryId="c1" door="mcq" hasChildren />, { locale: "en" });

    expect(await screen.findByText("First exam")).toBeInTheDocument();
    expect(calls[0].search).toContain("branch=true");
    expect(calls[0].search).toContain("examType=MCQ");

    const toggle = screen.getByRole("switch", { name: "Include exams in subcategories" });
    expect(toggle).toBeChecked();
    await userEvent.click(toggle);
    await waitFor(() => expect(calls.some((call) => call.search.includes("branch=false"))).toBe(true));
    expect(screen.getByRole("switch", { name: "Include exams in subcategories" })).not.toBeChecked();
  });

  it("loads the next page on Load more, and keeps the rows already shown", async () => {
    mockApi({
      "GET /api/v1/public/live-exam-categories/c1/exams": ({ url }: { url: URL }) =>
        url.searchParams.get("page") === "1"
          ? page([exam("e2", "Second exam")], 1, 2)
          : page([exam("e1", "First exam")], 0, 2),
    });
    renderWithProviders(<CategoryExams categoryId="c1" door="mcq" hasChildren />, { locale: "en" });

    expect(await screen.findByText("First exam")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Load more" }));
    expect(await screen.findByText("Second exam")).toBeInTheDocument();
    expect(screen.getByText("First exam")).toBeInTheDocument();
  });

  it("lays the exams out in two columns from lg, with one column below lg and the same gap both ways", async () => {
    mockApi({
      "GET /api/v1/public/live-exam-categories/c1/exams": () => page([exam("e1", "First exam"), exam("e2", "Second exam")], 0, 1),
    });
    renderWithProviders(<CategoryExams categoryId="c1" door="mcq" hasChildren />, { locale: "en" });

    const list = (await screen.findByText("First exam")).closest("ul") as HTMLElement;
    expect(list).toHaveClass("grid", "gap-3", "lg:grid-cols-2");
    expect(list).not.toHaveClass("md:grid-cols-2", "sm:grid-cols-2");
    expect(list.children).toHaveLength(2);
    expect(list.children[0]).toHaveClass("min-w-0");
  });

  it("a category with nothing in it and no subcategory shows the coming-soon note", async () => {
    mockApi({ "GET /api/v1/public/live-exam-categories/c1/exams": () => page([], 0, 1) });
    renderWithProviders(<CategoryExams categoryId="c1" door="mcq" hasChildren={false} />, { locale: "en" });

    expect(await screen.findByText("Coming soon")).toBeInTheDocument();
    expect(screen.queryByRole("switch")).not.toBeInTheDocument();
  });

  it("an empty branch with subcategories gets a plain empty line, not the coming-soon note", async () => {
    mockApi({ "GET /api/v1/public/live-exam-categories/c1/exams": () => page([], 0, 1) });
    renderWithProviders(<CategoryExams categoryId="c1" door="mcq" hasChildren />, { locale: "en" });

    expect(await screen.findByText("No exams in this category yet.")).toBeInTheDocument();
    expect(screen.queryByText("Coming soon")).not.toBeInTheDocument();
  });
});

describe("the query keys of the category list (S2.1)", () => {
  it("a different branch setting is a different list, with its own URL", () => {
    expect(catalogKeys.categoryExams("c1", "MCQ", true)).not.toEqual(catalogKeys.categoryExams("c1", "MCQ", false));
    expect(catalogKeys.categoryExams("c1", "MCQ", true)).not.toEqual(catalogKeys.categoryExams("c1", "WRITTEN", true));
    expect(categoryExamsUrl("c1", "MCQ", true, 0)).toContain("branch=true");
    expect(categoryExamsUrl("c1", "MCQ", false, 2)).toContain("branch=false");
    expect(categoryExamsUrl("c1", "MCQ", false, 2)).toContain("page=2");
  });
});
