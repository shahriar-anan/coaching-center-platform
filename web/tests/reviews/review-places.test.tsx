import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ExamPage } from "@/components/student/catalog/exam-page";
import { ModulePage } from "@/components/student/catalog/module-page";
import { ExamViewTabs } from "@/components/student/catalog/exam-view-tabs";
import { ExamReviewsPage } from "@/components/student/reviews/exam-reviews-page";
import type { PublicReviews } from "@/lib/api/exam/reviews";
import en from "@/i18n/messages/en/reviews";
import { fixtures, mockApi, renderWithProviders } from "./../helpers";

const MODULE_ID = fixtures.module.id;
const EXAM_ID = "eeeeeeee-0000-0000-0000-000000000009";
const emptyPage = { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 };
const zeroStars = { "1": 0, "2": 0, "3": 0, "4": 0, "5": 0 };

const noReviews: PublicReviews = {
  ratingAverage: null,
  ratingCount: 0,
  stars: zeroStars,
  page: { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 },
};
const someReviews: PublicReviews = {
  ratingAverage: 4.7,
  ratingCount: 3,
  stars: { ...zeroStars, "4": 1, "5": 2 },
  page: {
    content: [{ id: "c1", name: "Farhana A.", rating: 5, comment: "Very fair paper.", createdAt: "2026-10-20T04:00:00Z" }],
    page: 0,
    size: 10,
    totalElements: 1,
    totalPages: 1,
  },
};

const offer = { price: 2500, discountPercent: null, discountEndsAt: null, effectivePrice: 2500, free: false, moduleOnly: false };

function moduleRoutes(reviews: PublicReviews, ratingCount: number) {
  return {
    [`GET /api/v1/public/live-exam-modules/${MODULE_ID}`]: {
      ...fixtures.module,
      price: 2500,
      pricing: offer,
      coverUrl: null,
      favourite: false,
      access: null,
      ratingAverage: reviews.ratingAverage,
      ratingCount,
    },
    [`GET /api/v1/public/live-exam-modules/${MODULE_ID}/exams`]: emptyPage,
    "GET /api/v1/public/live-exam-categories": fixtures.categoryTree,
    "GET /api/v1/public/exam-site/status": { checkoutEnabled: true },
    [`GET /api/v1/public/reviews/package/${MODULE_ID}`]: reviews,
  };
}

describe("the exam filter tabs", () => {
  it("are a pill row by default, and a vertical list from lg only when stacked", () => {
    const { rerender } = renderWithProviders(<ExamViewTabs value="ALL" onChange={vi.fn()} />, { user: null, locale: "en" });
    const group = screen.getByRole("group", { name: "Show exams" });
    expect(group).toHaveClass("rounded-full");
    expect(group).not.toHaveClass("lg:flex-col");

    rerender(<ExamViewTabs value="ALL" onChange={vi.fn()} stacked />);
    expect(screen.getByRole("group", { name: "Show exams" })).toHaveClass("lg:flex-col");
  });
});

describe("the package page's Reviews tab", () => {
  it("comes after About, and the reviews are in it, not at the bottom of the other tabs", async () => {
    const user = userEvent.setup();
    mockApi(moduleRoutes(someReviews, 3));
    renderWithProviders(<ModulePage door="mcq" moduleId={MODULE_ID} />, { user: null, locale: "en" });

    const tabs = within(await screen.findByRole("tablist")).getAllByRole("tab");
    expect(tabs[tabs.length - 1]).toHaveTextContent(en.tab);
    expect(screen.queryByText("Very fair paper.")).not.toBeInTheDocument();

    await user.click(tabs[tabs.length - 1]);
    expect(await screen.findByText("Very fair paper.")).toBeInTheDocument();
  });

  it("says there are no reviews yet when the package has none", async () => {
    const user = userEvent.setup();
    mockApi(moduleRoutes(noReviews, 0));
    renderWithProviders(<ModulePage door="mcq" moduleId={MODULE_ID} />, { user: null, locale: "en" });

    await user.click(within(await screen.findByRole("tablist")).getByRole("tab", { name: new RegExp(en.tab) }));
    expect(await screen.findByText(en.section.emptyTitle)).toBeInTheDocument();
  });
});

describe("the exam page's Reviews button and the reviews page", () => {
  const exam = {
    id: EXAM_ID,
    moduleId: MODULE_ID,
    titleEn: "Sample paper",
    titleBn: "নমুনা",
    descriptionEn: "",
    descriptionBn: "",
    examType: "MCQ",
    status: "PUBLISHED",
    price: 100,
    pricing: { ...offer, price: 100, effectivePrice: 100 },
    coverUrl: null,
    favourite: false,
    access: { state: "NONE", buyable: true, blockedReason: null },
    packages: [],
    ratingAverage: 4.7,
    ratingCount: 3,
  };

  it("links to the exam's own reviews page, with the count", async () => {
    mockApi({
      [`GET /api/v1/public/live-exams/${EXAM_ID}`]: exam,
      "GET /api/v1/public/live-exam-categories": fixtures.categoryTree,
      "GET /api/v1/public/exam-site/status": { checkoutEnabled: true },
    });
    renderWithProviders(<ExamPage examId={EXAM_ID} />, { user: null, locale: "en" });

    const link = await screen.findByRole("link", { name: "Reviews (3)" });
    expect(link).toHaveAttribute("href", `/exam/${EXAM_ID}/reviews`);
    // The reviews themselves are on that page, not at the bottom of this one.
    expect(screen.queryByRole("heading", { name: en.section.title })).not.toBeInTheDocument();
  });

  it("the reviews page shows the exam's reviews, and an empty note when there are none", async () => {
    mockApi({
      [`GET /api/v1/public/live-exams/${EXAM_ID}`]: exam,
      [`GET /api/v1/public/reviews/exam/${EXAM_ID}`]: someReviews,
    });
    const first = renderWithProviders(<ExamReviewsPage examId={EXAM_ID} />, { user: null, locale: "en" });
    expect(await screen.findByText("Very fair paper.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 1, name: en.page.title })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: en.page.back })).toHaveAttribute("href", `/exam/${EXAM_ID}`);
    first.unmount();

    mockApi({
      [`GET /api/v1/public/live-exams/${EXAM_ID}`]: { ...exam, ratingAverage: null, ratingCount: 0 },
      [`GET /api/v1/public/reviews/exam/${EXAM_ID}`]: noReviews,
    });
    renderWithProviders(<ExamReviewsPage examId={EXAM_ID} />, { user: null, locale: "en" });
    await waitFor(() => expect(screen.getByText(en.section.emptyTitle)).toBeInTheDocument());
  });
});
