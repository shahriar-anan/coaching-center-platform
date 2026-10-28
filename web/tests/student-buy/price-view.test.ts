import { describe, expect, it } from "vitest";
import { chargedAmount, priceLabel } from "@/components/student/catalog/price-view";
import type { PriceView } from "@/lib/api/exam/types";

const view = (overrides: Partial<PriceView>): PriceView => ({
  price: null,
  discountPercent: null,
  discountEndsAt: null,
  effectivePrice: null,
  free: false,
  moduleOnly: false,
  ...overrides,
});

describe("price label for a list row or the detail (S2.2)", () => {
  it("a price with no discount is one price", () => {
    expect(priceLabel(view({ price: 400, effectivePrice: 400 }))).toEqual({ kind: "price", amount: 400 });
  });

  it("a discount shows the list price, the effective price, the percent and the end date", () => {
    expect(
      priceLabel(view({ price: 400, discountPercent: 25, discountEndsAt: "2026-11-30T17:59:59Z", effectivePrice: 300 })),
    ).toEqual({ kind: "discounted", list: 400, effective: 300, percent: 25, endsAt: "2026-11-30T17:59:59Z" });
  });

  it("a discount with no end date has no end", () => {
    expect(priceLabel(view({ price: 400, discountPercent: 10, effectivePrice: 360 }))).toEqual({
      kind: "discounted",
      list: 400,
      effective: 360,
      percent: 10,
      endsAt: null,
    });
  });

  it("an ended discount (effective equal to list) shows as a plain price", () => {
    expect(priceLabel(view({ price: 400, discountPercent: 25, discountEndsAt: "2020-01-01T00:00:00Z", effectivePrice: 400 }))).toEqual({
      kind: "price",
      amount: 400,
    });
  });

  it("a free exam is free, whatever else the view says", () => {
    expect(priceLabel(view({ free: true }))).toEqual({ kind: "free" });
  });

  it("an exam with no price of its own, sold in a priced package, is package only", () => {
    expect(priceLabel(view({ moduleOnly: true }))).toEqual({ kind: "moduleOnly" });
  });

  it("no price and not package only is no price; no view at all is no price", () => {
    expect(priceLabel(view({}))).toEqual({ kind: "none" });
    expect(priceLabel(null)).toEqual({ kind: "none" });
    expect(priceLabel(undefined)).toEqual({ kind: "none" });
  });
});

describe("the amount an order charges (S2.6)", () => {
  it("is the effective price, so a discounted exam charges less than its list price", () => {
    expect(chargedAmount(view({ price: 400, discountPercent: 25, effectivePrice: 300 }), 400)).toBe(300);
  });

  it("is nothing for a free exam", () => {
    expect(chargedAmount(view({ free: true }), null)).toBeNull();
  });

  it("falls back to the list price when the API sent no pricing", () => {
    expect(chargedAmount(null, 400)).toBe(400);
    expect(chargedAmount(null, null)).toBeNull();
  });
});
