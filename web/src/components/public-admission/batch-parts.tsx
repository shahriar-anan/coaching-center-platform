"use client";

import { apiUrl } from "@/lib/config";
import { formatDate, formatNumber, pickTitle } from "@/lib/format";
import type { Locale } from "@/i18n/locale-context";
import { cn } from "@/components/ui/cn";
import type { PublicBatch } from "@/lib/api/exam/public-admission";

/** A batch's title in the reader's language. */
export function batchName(batch: Pick<PublicBatch, "nameEn" | "nameBn">, locale: Locale): string {
  return pickTitle({ titleEn: batch.nameEn, titleBn: batch.nameBn }, locale);
}

/** "1 Jan to 31 Dec", or "Ends 31 Dec" when the batch has no start date. */
export function batchDatesLine(batch: PublicBatch, locale: Locale, t: (key: string, params?: Record<string, string | number>) => string): string {
  const end = formatDate(batch.endsOn, locale, { year: "auto" });
  if (!batch.startsOn) {
    return t("publicAdmission.card.endsOn", { end });
  }
  return t("publicAdmission.card.dates", { start: formatDate(batch.startsOn, locale, { year: "auto" }), end });
}

/**
 * The course fee as students see it, paid month by month: "Course fee: ৳3,000 a month", or "৳3,000 a month" under a label that
 * already says "Course fee" ({@code labelled} false).
 */
export function batchFeeLine(
  batch: PublicBatch,
  locale: Locale,
  t: (key: string, params?: Record<string, string | number>) => string,
  labelled = true,
): string {
  // Whole taka with a separator, as the card shows it; a fee with paisa is written out in full.
  const value = Number(batch.monthlyFee);
  const amount = `৳${formatNumber(value, locale, { decimals: Number.isInteger(value) ? 0 : 2 })}`;
  return t(labelled ? "publicAdmission.card.fee" : "publicAdmission.card.perMonth", { amount });
}

/**
 * The batch photo in a 16:9 frame. The photo is served without sign-in, so it is a plain image. Without one, a plain header
 * shows the title on the brand gradient.
 */
export function BatchCover({
  photoUrl,
  title,
  className,
  alt,
  plainLabel,
  slim = false,
}: {
  photoUrl: string | null;
  title: string;
  className?: string;
  alt: string;
  plainLabel: string;
  /** A small thumbnail (14 rem wide at most, 16:9) for a page where the description is the main content. */
  slim?: boolean;
}) {
  return (
    <div
      className={cn(
        "relative overflow-hidden bg-band text-on-band aspect-video",
        slim ? "w-64 max-w-full rounded-control" : "w-full",
        className,
      )}
    >
      {photoUrl ? (
        // The photo comes from the API's own host, so it is not run through the image optimiser.
        // eslint-disable-next-line @next/next/no-img-element
        <img src={apiUrl(photoUrl)} alt={alt} loading="lazy" className="absolute inset-0 size-full object-cover" />
      ) : (
        <div aria-hidden="true" className="absolute inset-0 bg-gradient-to-br from-band via-band to-accent/70">
          <span className="absolute -end-8 -top-8 size-40 rounded-full bg-accent/20" />
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-1.5 px-4 text-center">
            <span className="line-clamp-2 max-w-full break-words text-card-title text-on-band sm:line-clamp-3">{title}</span>
            <span className="text-meta font-semibold uppercase tracking-widest text-on-band/80">{plainLabel}</span>
          </div>
        </div>
      )}
    </div>
  );
}
