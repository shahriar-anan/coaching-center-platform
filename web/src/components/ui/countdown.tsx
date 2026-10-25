"use client";

import { useEffect, useRef } from "react";
import { cn } from "@/components/ui/cn";
import { useLocale } from "@/i18n/locale-context";
import { serverNow, useServerNow } from "@/lib/time/clock";
import { formatClock, formatNumber, formatRelative } from "@/lib/format";

/**
 * Time left until a server deadline. The clock is the server's (see `serverNow`),
 * never the phone's alone. Screen readers hear one announcement per minute,
 * not every second. `warnAt` (minutes left) turns the bar to the warning tone.
 */
export function Countdown({
  deadline,
  onEnd,
  variant = "inline",
  warnAt = [],
}: {
  deadline: string;
  onEnd?: () => void;
  variant?: "inline" | "bar";
  warnAt?: number[];
}) {
  const { t, locale } = useLocale();
  const now = useServerNow(1000);
  const target = Date.parse(deadline);
  const left = Math.max(0, target - now);
  const minutes = Math.ceil(left / 60000);
  const ended = left <= 0;

  const endedRef = useRef(false);
  useEffect(() => {
    if (ended && !endedRef.current) {
      endedRef.current = true;
      onEnd?.();
    }
  }, [ended, onEnd]);

  // The text changes only when the whole-minute count changes, so a polite live
  // region announces once a minute, not every second.
  const announced = ended
    ? t("common.countdown.ended")
    : t("common.countdown.minutesLeft", { minutes: formatNumber(minutes, locale) });

  const warning = !ended && warnAt.some((m) => minutes <= m);
  const clock = formatClock(left, locale);

  if (variant === "bar") {
    return (
      <div
        className={cn(
          "flex items-center justify-between gap-3 rounded-control px-4 py-2 tabular-nums",
          warning ? "bg-warn/15 text-warn" : "bg-card text-text",
        )}
      >
        <span className="text-sm text-muted">{t("common.countdown.left")}</span>
        <span className="text-lg font-semibold">{ended ? t("common.countdown.ended") : clock}</span>
        <span role="status" aria-live="polite" className="sr-only">
          {announced}
        </span>
      </div>
    );
  }

  return (
    <span className={cn("inline-flex items-baseline gap-1.5 tabular-nums", warning && "text-warn")}>
      {ended ? (
        <span>{t("common.countdown.ended")}</span>
      ) : (
        <>
          <span>{formatRelative(left, locale)}</span>
          <span className="sr-only">{t("common.countdown.left")}</span>
        </>
      )}
      <span role="status" aria-live="polite" className="sr-only">
        {announced}
      </span>
    </span>
  );
}

/** Exposed for callers that need the instant without a component (tests, guards). */
export function remainingMs(deadline: string): number {
  return Math.max(0, Date.parse(deadline) - serverNow());
}
