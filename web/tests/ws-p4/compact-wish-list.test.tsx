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

describe("the Wish List mark on rows and cards (Phase 4, S4.4, S14.2)", () => {
  it("is a normal 40 px item in a catalog row, the last item of its top row, with no corner position", () => {
    renderWithProviders(<CatalogExamRow exam={examRow} />, { user: fixtures.student, locale: "en" });
    const mark = screen.getByRole("button", { name: "Wish List" });
    expect(mark).toHaveClass("relative", "size-10");
    expect(mark).not.toHaveClass("absolute", "end-2", "top-2");
    expect(mark.parentElement?.lastElementChild).toBe(mark);
    expect(screen.getByRole("link", { name: "CRPC Mock 1" })).toBeInTheDocument();
  });

  it("is placed the same on an exam row of a package page, and it still toggles", async () => {
    const { calls } = mockApi({ "PUT /api/v1/me/favourites/exams/e1": {} });
    renderWithProviders(
      <ExamRow
        href="/exam/e1"
        title="CRPC Mock 1"
        right={<span>Buy</span>}
        favourite={<FavouriteButton kind="exam" id="e1" initial={false} next="/exam/e1" placement="inline" />}
      />,
      { user: fixtures.student, locale: "en" },
    );
    const mark = screen.getByRole("button", { name: "Wish List" });
    expect(mark).toHaveClass("relative", "size-10");
    expect(mark).not.toHaveClass("absolute");
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
    expect(within(card).getByRole("button", { name: "Wish List" })).toHaveClass("relative", "size-10");
    expect(within(card).getByRole("button", { name: "Wish List" })).not.toHaveClass("absolute");
  });
});

describe("the inline Wish List mark (FavouriteButton placement=\"inline\")", () => {
  it("renders as a normal element with the same label, and the student's toggle still works", async () => {
    mockApi({ "PUT /api/v1/me/favourites/exams/e1": {} });
    renderWithProviders(<FavouriteButton kind="exam" id="e1" initial={false} next="/exam/e1" placement="inline" />, {
      user: fixtures.student,
      locale: "en",
    });
    const mark = screen.getByRole("button", { name: "Wish List" });
    expect(mark.tagName).toBe("BUTTON");
    expect(mark).toHaveClass("relative", "inline-flex", "size-10");
    expect(mark).not.toHaveClass("absolute");
    expect(mark).toHaveAttribute("aria-pressed", "false");
    await userEvent.click(mark);
    expect(mark).toHaveAttribute("aria-pressed", "true");
  });

  it("sends a visitor to log in, with the same label and no corner position", () => {
    renderWithProviders(<FavouriteButton kind="exam" id="e1" initial={false} next="/exam/e1" placement="inline" />, { locale: "en" });
    const mark = screen.getByRole("link", { name: "Wish List" });
    expect(mark).toHaveAttribute("href", "/login?next=%2Fexam%2Fe1");
    expect(mark).toHaveClass("relative", "size-10");
    expect(mark).not.toHaveClass("absolute");
  });

  it("keeps the corner mark on a detail page by default", () => {
    renderWithProviders(<FavouriteButton kind="exam" id="e1" initial={false} next="/exam/e1" />, {
      user: fixtures.student,
      locale: "en",
    });
    expect(screen.getByRole("button", { name: "Wish List" })).toHaveClass("absolute", "end-2", "top-2", "size-11");
  });
});

describe("a catalog row in its three zones (S14.2)", () => {
  it("has the title link, the status pill, the Wish List mark, and no corner mark", () => {
    renderWithProviders(<CatalogExamRow exam={{ ...examRow, phase: "LIVE" }} />, { user: fixtures.student, locale: "en" });
    expect(screen.getByRole("link", { name: "CRPC Mock 1" })).toHaveAttribute("href", "/exam/e1");
    expect(screen.getByText("Live now")).toBeInTheDocument();
    const mark = screen.getByRole("button", { name: "Wish List" });
    expect(mark).toBeInTheDocument();
    expect(mark.closest("div.group")?.querySelector(".end-2, .top-2")).toBeNull();
  });
});
