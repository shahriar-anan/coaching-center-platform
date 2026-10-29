import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ExamPrice, priceCell } from "@/components/staff/exams/exam-price";
import type { PriceView } from "@/lib/api/exam/staff-catalog";
import { renderWithProviders } from "../helpers";

const NO_PRICE: PriceView = { price: null, discountPercent: null, discountEndsAt: null, effectivePrice: null, free: false, moduleOnly: false };

function priced(overrides: Partial<PriceView>): PriceView {
  return { ...NO_PRICE, price: 1000, effectivePrice: 1000, ...overrides };
}

describe("priceCell", () => {
  it("has no price when none is set, and says it is free for a free exam", () => {
    expect(priceCell(NO_PRICE)).toEqual({ kind: "none" });
    expect(priceCell(null)).toEqual({ kind: "none" });
    expect(priceCell({ ...NO_PRICE, free: true })).toEqual({ kind: "free" });
  });

  it("shows a plain price when there is no discount", () => {
    expect(priceCell(priced({}))).toEqual({ kind: "plain", amount: 1000 });
  });

  it("shows the list price, the effective price and the percent when the discount is running", () => {
    expect(
      priceCell(priced({ discountPercent: 20, discountEndsAt: "2026-11-30T00:00:00Z", effectivePrice: 800 })),
    ).toEqual({ kind: "discount", list: 1000, effective: 800, percent: 20 });
  });

  it("shows a plain price, with no strike-through and no percent, once the discount has ended", () => {
    // The API gives the list price as the effective price after the end, so the cell takes it as it is.
    expect(
      priceCell(priced({ discountPercent: 20, discountEndsAt: "2026-10-01T00:00:00Z", effectivePrice: 1000 })),
    ).toEqual({ kind: "plain", amount: 1000 });
  });
});

describe("ExamPrice", () => {
  it("shows the no-price text when no price is set", () => {
    const { container } = renderWithProviders(<ExamPrice pricing={NO_PRICE} />, { locale: "en" });

    expect(screen.getByText("No price")).toBeInTheDocument();
    expect(container.querySelector("s")).toBeNull();
  });

  it("shows a plain price with the shared money formatter", () => {
    const { container } = renderWithProviders(<ExamPrice pricing={priced({})} />, { locale: "en" });

    expect(container).toHaveTextContent("৳1,000.00");
    expect(container.querySelector("s")).toBeNull();
  });

  it("strikes through the list price and shows the effective price and the percent beside it", () => {
    const { container } = renderWithProviders(
      <ExamPrice pricing={priced({ discountPercent: 20, discountEndsAt: "2026-11-30T00:00:00Z", effectivePrice: 800 })} />,
      { locale: "en" },
    );

    expect(container.querySelector("s")).toHaveTextContent("৳1,000.00");
    expect(screen.getByText("৳800.00")).toBeInTheDocument();
    expect(screen.getByText("20% off")).toBeInTheDocument();
  });

  it("shows the Bangla digits for a discount in Bangla", () => {
    const { container } = renderWithProviders(
      <ExamPrice pricing={priced({ discountPercent: 20, discountEndsAt: "2026-11-30T00:00:00Z", effectivePrice: 800 })} />,
      { locale: "bn" },
    );

    expect(container.querySelector("s")).toHaveTextContent("৳১,০০০");
    expect(container).toHaveTextContent("২০% ছাড়");
  });

  it("shows no strike-through and no percent after the discount has ended", () => {
    const { container } = renderWithProviders(
      <ExamPrice pricing={priced({ discountPercent: 20, discountEndsAt: "2026-10-01T00:00:00Z", effectivePrice: 1000 })} />,
      { locale: "en" },
    );

    expect(container.querySelector("s")).toBeNull();
    expect(container).toHaveTextContent("৳1,000.00");
    expect(container).not.toHaveTextContent("off");
  });
});
