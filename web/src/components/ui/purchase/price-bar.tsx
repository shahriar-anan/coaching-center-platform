import type { ReactNode } from "react";

/** The price bar at the top of an offer. Stub: placeholder markup until P-1 lands. */
export function PriceBar({
  price,
  priceNote,
  status,
  action,
  secondary,
}: {
  price: number | null;
  priceNote?: string;
  status?: ReactNode;
  action?: ReactNode;
  secondary?: ReactNode;
}) {
  return (
    <div data-price={price ?? ""}>
      {status}
      {action}
      {secondary}
      {priceNote}
    </div>
  );
}
