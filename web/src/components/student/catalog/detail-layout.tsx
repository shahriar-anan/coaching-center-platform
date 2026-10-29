import { isValidElement, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { cn } from "@/components/ui/cn";

/**
 * The frame of a package page and an exam page. The student shell keeps its 760 px column for every other page, so on a desktop
 * (lg and up) these two pages break out of it to 1280 px, centred. The 50% margin puts the frame's centre on the screen's centre;
 * `100vw` counts the scrollbar, so the frame keeps about 16 px at each side on Windows.
 */
export const detailFrame = "lg:w-[min(1280px,100vw_-_3rem)] lg:ms-[calc(50%_-_min(640px,50vw_-_1.5rem))]";

/**
 * The layout of a package page and an exam page (Phase 4, S4.3). On a phone and a tablet the order is the cover band, the header
 * (type, title, summary), the details, then the purchase card, so the buy action comes after what the page offers. On a desktop
 * (lg and up) the header and the details sit in the left two-thirds, and the purchase card sits in the right third and stays in view
 * under the top bar: the cover first, then the card. The sticky column aligns to the start, so it moves within the page.
 * The DOM order is the header, the details, the cover, then the card. The phone order comes from the `order` classes, and a screen
 * reader reads the title and the details before the buy card.
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
    <div className="mt-4 grid gap-5 lg:grid-cols-[minmax(0,2fr)_minmax(300px,1fr)] lg:gap-x-8">
      <div className="order-2 grid min-w-0 gap-4 lg:col-start-1 lg:row-start-1">{header}</div>
      <div className="order-3 grid min-w-0 gap-7 lg:col-start-1 lg:row-start-2">{children}</div>
      {/* On a phone the cover and the card are rows of the page; on desktop they are one sticky column. */}
      <div className="contents lg:sticky lg:top-20 lg:col-start-2 lg:row-span-2 lg:row-start-1 lg:grid lg:content-start lg:gap-4 lg:self-start">
        <div className="order-1 min-w-0">{cover}</div>
        <div className="order-4 min-w-0">{card}</div>
      </div>
    </div>
  );
}

/**
 * The action the phone bar repeats: the card's own button, and only when it can be pressed now. A disabled button (checkout is off,
 * or the window is not open yet) and a line of text give no bar, so the bar and the card never offer different things.
 */
export function barActionOf(action: ReactNode): ReactNode {
  return isValidElement<{ disabled?: boolean }>(action) && action.type === Button && !action.props.disabled ? action : null;
}

/**
 * The bar that repeats the buy action on a phone once the card has scrolled out of view (Phase 4, S4.3). It slides in from the
 * bottom and out again; a reduced-motion setting makes the change at once. The page drives `show` from an observer on the card.
 * It is hidden from md up, as the phone bar always was.
 */
export function ActionBar({ show, label, action, children }: { show: boolean; label: string; action: ReactNode; children: ReactNode }) {
  return (
    <div
      role="region"
      aria-label={label}
      aria-hidden={!show}
      className={cn(
        "fixed inset-x-0 bottom-[var(--tabbar-h,0px)] z-30 border-t border-border bg-card/95 px-4 py-2.5 backdrop-blur md:hidden",
        "transition-[transform,opacity,visibility] duration-200 ease-out motion-reduce:transition-none",
        show ? "visible translate-y-0 opacity-100" : "invisible translate-y-full opacity-0",
      )}
    >
      <div className="mx-auto flex max-w-[760px] items-center gap-3">
        <div className="grid min-w-0 flex-1">{children}</div>
        <div className="shrink-0">{action}</div>
      </div>
    </div>
  );
}
