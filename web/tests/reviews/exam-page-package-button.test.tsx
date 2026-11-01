import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ExamPage } from "@/components/student/catalog/exam-page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const MODULE_ID = fixtures.module.id;
const EXAM_ID = "eeeeeeee-0000-0000-0000-000000000010";
const emptyPage = { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 };
const zeroStars = { "1": 0, "2": 0, "3": 0, "4": 0, "5": 0 };
const offer = { price: 100, discountPercent: null, discountEndsAt: null, effectivePrice: 100, free: false, moduleOnly: false };

describe("the exam page's action card", () => {
  it("shows View package above Copy link, and the package link goes to the package", async () => {
    mockApi({
      [`GET /api/v1/public/live-exams/${EXAM_ID}`]: {
        id: EXAM_ID,
        moduleId: MODULE_ID,
        titleEn: "Sample paper",
        titleBn: "নমুনা",
        descriptionEn: "",
        descriptionBn: "",
        examType: "MCQ",
        status: "PUBLISHED",
        price: 100,
        pricing: { ...offer, price: 100 },
        coverUrl: null,
        favourite: false,
        access: { state: "NONE", buyable: true, blockedReason: null },
        packages: [{ id: MODULE_ID }],
        ratingAverage: null,
        ratingCount: 0,
        ratingStars: zeroStars,
      },
      [`GET /api/v1/public/live-exam-modules/${MODULE_ID}`]: {
        ...fixtures.module,
        price: 100,
        pricing: offer,
        coverUrl: null,
        favourite: false,
        access: null,
      },
      [`GET /api/v1/public/live-exam-modules/${MODULE_ID}/exams`]: emptyPage,
      "GET /api/v1/public/live-exam-categories": fixtures.categoryTree,
      "GET /api/v1/public/exam-site/status": { checkoutEnabled: true },
    });
    renderWithProviders(<ExamPage examId={EXAM_ID} />, { user: null, locale: "en" });

    const view = await screen.findByRole("link", { name: "View package" });
    expect(view).toHaveAttribute("href", `/mcq/m/${MODULE_ID}`);
    const copy = screen.getByRole("button", { name: "Copy link" });
    // View package sits above Copy link.
    expect(view.compareDocumentPosition(copy) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });
});
