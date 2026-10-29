import type { PriceView } from "@/lib/api/exam/types";

/**
 * What a price line shows (plan S2.2 and S2.5): free, a list price with a discount, one price, a price only in a package,
 * or no price at all. Built from the API's `pricing` object, which is what the order charges (NR-01, B5 to B7).
 */
export type PriceLabel =
  | { kind: "free" }
  | { kind: "discounted"; list: number; effective: number; percent: number; endsAt: string | null }
  | { kind: "price"; amount: number }
  | { kind: "moduleOnly" }
  | { kind: "none" };

export function priceLabel(view: PriceView | null | undefined): PriceLabel {
  if (!view) return { kind: "none" };
  if (view.free) return { kind: "free" };
  if (view.price === null) return view.moduleOnly ? { kind: "moduleOnly" } : { kind: "none" };
  const effective = view.effectivePrice ?? view.price;
  // A discount that has ended comes back with the effective price equal to the list price, so it shows as a plain price.
  if (view.discountPercent !== null && effective < view.price) {
    return { kind: "discounted", list: view.price, effective, percent: view.discountPercent, endsAt: view.discountEndsAt };
  }
  return { kind: "price", amount: view.price };
}

/** The amount the order charges: the effective price, or the list price when the API sent no `pricing`. Null when there is none. */
export function chargedAmount(view: PriceView | null | undefined, listPrice: number | null | undefined): number | null {
  if (view) return view.effectivePrice ?? null;
  return listPrice ?? null;
}

/** The price view of an exam or package: its `pricing` object, or one built from the bare price for an older response. */
export function pricingOf(pricing: PriceView | null | undefined, price: number | null | undefined): PriceView | null {
  if (pricing) return pricing;
  if (price === null || price === undefined) return null;
  return { price, discountPercent: null, discountEndsAt: null, effectivePrice: price, free: false, moduleOnly: false };
}
