"use client";

import { cn } from "@/components/ui/cn";
import { useLocale } from "@/i18n/locale-context";

/**
 * A placeholder shaped like the content it stands for. It is hidden from the
 * accessibility tree; one "loading" sentence is announced instead.
 */
export function Skeleton({
  variant,
  count = 1,
  height,
  className,
}: {
  variant: "text" | "card" | "rows";
  count?: number;
  /** A Tailwind height class, such as "h-40", so the placeholder matches the card it stands for. */
  height?: string;
  className?: string;
}) {
  const { t } = useLocale();
  const pulse = "animate-pulse rounded-control bg-border motion-reduce:animate-none";
  return (
    <div role="status" aria-live="polite" className={cn("grid gap-3", className)}>
      <span className="sr-only">{t("common.states.loading")}</span>
      <div aria-hidden="true" className="grid gap-3">
        {Array.from({ length: count }, (_, i) => {
          if (variant === "text") {
            return <div key={i} className={cn(pulse, height ?? "h-4", "w-3/4")} />;
          }
          if (variant === "card") {
            return <div key={i} className={cn(pulse, height ?? "h-24", "rounded-card")} />;
          }
          return <div key={i} className={cn(pulse, height ?? "h-12")} />;
        })}
      </div>
    </div>
  );
}
