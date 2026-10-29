import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { FavouritesPage } from "@/components/student/catalog/favourites-page";
import { CatalogExamRow } from "@/components/student/catalog/exam-row";
import { PopularSection } from "@/components/student/home/popular-section";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const exam = (id: string, titleEn: string, extra: Record<string, unknown> = {}) => ({
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
  ...extra,
});

const pkg = (id: string, titleEn: string, extra: Record<string, unknown> = {}) => ({
  id,
  titleEn,
  titleBn: titleEn,
  categoryId: "c1",
  position: 1,
  price: 900,
  mcqCount: 2,
  writtenCount: 1,
  coverUrl: null,
  descriptionEn: null,
  descriptionBn: null,
  pricing: { price: 900, discountPercent: null, discountEndsAt: null, effectivePrice: 900, free: false, moduleOnly: false },
  ...extra,
});

const page = (content: unknown[]) => ({ content, page: 0, size: 6, totalElements: content.length, totalPages: 1 });

describe("the Popular section on the student home (S3.1)", () => {
  it("says so when nothing is popular, instead of hiding the section", async () => {
    mockApi({
      "GET /api/v1/public/live-exams": page([]),
      "GET /api/v1/public/live-exam-modules": page([]),
    });
    renderWithProviders(<PopularSection />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByText("Nothing popular yet.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Popular" })).toBeInTheDocument();
  });

  it("lists the popular exams and packages, each with the popular marker", async () => {
    mockApi({
      "GET /api/v1/public/live-exams": page([exam("e63", "Popular MCQ", { popular: true })]),
      "GET /api/v1/public/live-exam-modules": page([pkg("m8", "Popular package", { popular: true })]),
    });
    renderWithProviders(<PopularSection />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByRole("link", { name: "Popular MCQ" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Popular package" })).toBeInTheDocument();
    expect(screen.getAllByText("Popular")).toHaveLength(3);
  });
});

describe("the popular marker on an exam row (S3.2)", () => {
  it("shows Popular on a popular exam only", () => {
    renderWithProviders(
      <ul>
        <li>
          <CatalogExamRow exam={exam("e1", "Popular exam", { popular: true }) as never} />
        </li>
        <li>
          <CatalogExamRow exam={exam("e2", "Plain exam") as never} />
        </li>
      </ul>,
      { locale: "en" },
    );
    const popular = screen.getByRole("link", { name: "Popular exam" }).closest(".group") as HTMLElement;
    const plain = screen.getByRole("link", { name: "Plain exam" }).closest(".group") as HTMLElement;
    expect(within(popular).getByText("Popular")).toBeInTheDocument();
    expect(within(plain).queryByText("Popular")).not.toBeInTheDocument();
  });
});

describe("the Wish List page (S3.4)", () => {
  it("has an empty state for each tab", async () => {
    mockApi({ "GET /api/v1/me/favourites": { exams: [], packages: [] } });
    renderWithProviders(<FavouritesPage />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByText("No exams in your Wish List yet")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("tab", { name: /Packages/ }));
    expect(await screen.findByText("No packages in your Wish List yet")).toBeInTheDocument();
    expect(screen.queryByText("No exams in your Wish List yet")).not.toBeInTheDocument();
  });

  it("lists the favourite exams and packages on their own tabs", async () => {
    mockApi({
      "GET /api/v1/me/favourites": {
        exams: [exam("e1", "Saved exam", { favourite: true })],
        packages: [pkg("m1", "Saved package", { favourite: true })],
      },
    });
    renderWithProviders(<FavouritesPage />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByText("Saved exam")).toBeInTheDocument();
    expect(screen.queryByText("Saved package")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("tab", { name: /Packages/ }));
    expect(await screen.findByText("Saved package")).toBeInTheDocument();
    expect(screen.queryByText("Saved exam")).not.toBeInTheDocument();
  });
});
