import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CatalogExamRow, ExamRow } from "@/components/student/catalog/exam-row";
import { PackageLinkCard } from "@/components/student/catalog/exam-packages";
import { FavouriteButton } from "@/components/student/catalog/favourite-button";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const examRow = {
  id: "e1",
  titleEn: "CRPC Mock 1",
  titleBn: "CRPC Mock 1",
  examType: "MCQ" as const,
  phase: "UPCOMING",
  liveAt: "2099-01-01T00:00:00Z",
  pricing: null,
  packages: [],
  favourite: false,
};

describe("the Wish List mark on rows and cards (Phase 4, S4.4)", () => {
  it("is one 44 px icon button in the top-right corner of a catalog row, out of the text's way", () => {
    renderWithProviders(<CatalogExamRow exam={examRow} />, { user: fixtures.student, locale: "en" });
    const mark = screen.getByRole("button", { name: "Wish List" });
    expect(mark).toHaveClass("absolute", "end-2", "top-2", "size-11");
    // The text column keeps its place: the mark is not inside a column of its own.
    expect(mark.parentElement?.tagName).toBe("DIV");
    expect(screen.getByRole("link", { name: "CRPC Mock 1" })).toBeInTheDocument();
  });

  it("is placed the same on an exam row of a package page, and it still toggles", async () => {
    const { calls } = mockApi({ "PUT /api/v1/me/favourites/exams/e1": {} });
    renderWithProviders(
      <ExamRow
        href="/exam/e1"
        title="CRPC Mock 1"
        right={<span>Buy</span>}
        favourite={<FavouriteButton kind="exam" id="e1" initial={false} next="/exam/e1" />}
      />,
      { user: fixtures.student, locale: "en" },
    );
    const mark = screen.getByRole("button", { name: "Wish List" });
    expect(mark).toHaveClass("absolute", "size-11");
    await userEvent.click(mark);
    expect(mark).toHaveAttribute("aria-pressed", "true");
    expect(calls.some((call) => call.method === "PUT")).toBe(true);
  });

  it("is placed the same on a package card of the Wish List page", () => {
    renderWithProviders(
      <PackageLinkCard
        href="/mcq/m/m1"
        pkg={{ ...fixtures.module, favourite: false, pricing: null, coverUrl: null, access: null }}
        fallback={{ id: fixtures.module.id, titleEn: "BCS Preliminary", titleBn: "BCS Preliminary" }}
      />,
      { user: fixtures.student, locale: "en" },
    );
    const card = screen.getByRole("link", { name: "BCS Preliminary" }).closest("div.group") as HTMLElement;
    expect(within(card).getByRole("button", { name: "Wish List" })).toHaveClass("absolute", "size-11");
  });
});

