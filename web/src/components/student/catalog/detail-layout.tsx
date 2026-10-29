import type { ReactNode } from "react";

/**
 * The layout of a package page and an exam page (Phase 4, S4.3). On a desktop (lg and up) the header and the details sit in the
 * left column, and the purchase card sits in the right column and stays in view under the top bar: the cover first, then the card.
 * On a phone and a tablet the order is the header, the main action, the cover band, then the details. The DOM order is the phone
 * order, so a screen reader reads the action before the picture.
 */
export function DetailLayout({
  header,
  card,
  cover,
  children,
}: {
  header: ReactNode;
  /** The purchase card: price, discount, the main action, what is included, and the access length. */
  card: ReactNode;
  /** The cover: a band of 160 px on a phone, a 16:9 frame in the card on desktop. */
  cover: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="mt-4 grid gap-5 lg:grid-cols-[minmax(0,1fr)_320px] lg:gap-x-8">
      <div className="grid min-w-0 gap-4 lg:col-start-1 lg:row-start-1">{header}</div>
      {/* On a phone the card and the cover are rows of the page; on desktop they are one sticky column. */}
      <div className="contents lg:sticky lg:top-20 lg:col-start-2 lg:row-span-2 lg:row-start-1 lg:grid lg:content-start lg:gap-4">
        <div className="order-2 min-w-0">{card}</div>
        <div className="order-3 min-w-0 lg:order-1">{cover}</div>
      </div>
      <div className="order-4 grid min-w-0 gap-7 lg:col-start-1 lg:row-start-2">{children}</div>
    </div>
  );
}
