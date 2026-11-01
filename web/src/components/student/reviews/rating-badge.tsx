import { Star } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { formatNumber } from "@/lib/format";

/**
 * The average next to the title of an exam or a package: a star, the average and the count, as "★ 4.6 (23)". Shows only when there
 * is an average (three ratings or more).
 */
export function RatingBadge({ average, count }: { average: number | null | undefined; count: number | undefined }) {
  const { locale } = useLocale();
  if (average === null || average === undefined || !count) return null;
  return (
    <p className="inline-flex items-center gap-1.5 text-body font-semibold text-text">
      <Star aria-hidden="true" className="size-4 fill-accent text-accent" />
      <span>
        {formatNumber(average, locale, { decimals: 1 })} ({formatNumber(count, locale)})
      </span>
    </p>
  );
}
