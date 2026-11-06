"use client";

import { useId } from "react";
import { Avatar } from "@/components/ui/file/avatar";
import { Button } from "@/components/ui/button";
import { Stars } from "@/components/ui/stars";
import { apiUrl } from "@/lib/config";
import { useLocale } from "@/i18n/locale-context";
import { usePublicReviews, type PublicReview, type ReviewKind, type StarCounts } from "@/lib/api/exam/reviews";
import { formatDate, formatNumber } from "@/lib/format";

/**
 * The approved comments of an exam or a package (reviews plan, section 7): the package's Reviews tab and the exam's reviews page.
 * The average and the 5-to-1 breakdown show from three ratings; below that only the comments show, if there are any. With no
 * ratings it is hidden, or with `showEmpty` it says so. It is hidden when the read fails.
 */
export function ReviewsSection({ kind, id, showEmpty = false }: { kind: ReviewKind; id: string; showEmpty?: boolean }) {
  const { t, locale } = useLocale();
  const headingId = useId();
  const reviews = usePublicReviews(kind, id);
  const first = reviews.data?.pages[0];

  if (!reviews.data || !first) return null;
  const comments = reviews.data.pages.flatMap((page) => page.page.content);
  const average = first.ratingAverage;
  if (first.ratingCount === 0 || (average === null && comments.length === 0)) {
    return showEmpty ? (
      <section aria-labelledby={headingId} className="grid gap-1 rounded-card border border-dashed border-border p-5 text-center">
        <h2 id={headingId} className="text-card-title font-bold">
          {t("reviews.section.emptyTitle")}
        </h2>
        <p className="text-body text-muted">{t("reviews.section.emptyBody")}</p>
      </section>
    ) : null;
  }

  const averageText = average === null ? "" : formatNumber(average, locale, { decimals: 1 });

  return (
    <section aria-labelledby={headingId} className="grid gap-4">
      <h2 id={headingId} className="text-section font-bold">
        {t("reviews.section.title")}
      </h2>

      {average !== null ? (
        <div className="grid gap-5 rounded-card border border-border bg-card p-5 md:grid-cols-[auto_minmax(0,1fr)] md:items-center md:gap-10">
          <div className="grid justify-items-center gap-1 text-center">
            <span className="text-4xl font-extrabold tabular-nums">{averageText}</span>
            <Stars value={average} label={t("reviews.stars.outOf", { n: averageText })} />
            <span className="text-meta tabular-nums text-muted">{t("reviews.section.count", { count: first.ratingCount })}</span>
          </div>
          <Breakdown counts={first.stars} total={first.ratingCount} />
        </div>
      ) : null}

      {comments.length > 0 ? (
        <ul className="grid gap-3">
          {comments.map((review) => (
            <li key={review.id}>
              <Comment review={review} />
            </li>
          ))}
        </ul>
      ) : null}

      {reviews.hasNextPage ? (
        <div className="flex justify-center">
          <Button variant="secondary" loading={reviews.isFetchingNextPage} onClick={() => void reviews.fetchNextPage()}>
            {t("reviews.section.showMore")}
          </Button>
        </div>
      ) : null}
    </section>
  );
}

/** Five bars, 5 stars at the top. Each bar is the share of ratings with that many stars. */
function Breakdown({ counts, total }: { counts: StarCounts; total: number }) {
  const { t, locale } = useLocale();
  return (
    <ol aria-label={t("reviews.section.breakdown")} className="grid gap-1.5">
      {[5, 4, 3, 2, 1].map((star) => {
        const count = counts[String(star) as keyof StarCounts] ?? 0;
        const percent = total > 0 ? Math.round((count / total) * 100) : 0;
        return (
          <li key={star} className="grid grid-cols-[2.5rem_minmax(0,1fr)_2.5rem] items-center gap-3 text-meta">
            <span className="tabular-nums">
              {formatNumber(star, locale)}
              <span className="sr-only"> {t("reviews.stars.many", { n: star })}</span>
            </span>
            <span aria-hidden="true" className="h-2 overflow-hidden rounded-full bg-border">
              <span className="block h-full rounded-full bg-accent" style={{ width: `${percent}%` }} />
            </span>
            <span className="text-end tabular-nums text-muted">{formatNumber(count, locale)}</span>
          </li>
        );
      })}
    </ol>
  );
}

/** One approved comment: the name, the stars, the date, then the text as plain text (no markup, links are not clickable). */
function Comment({ review }: { review: PublicReview }) {
  const { t, locale } = useLocale();
  return (
    <article className="grid gap-2 rounded-card border border-border bg-card p-4">
      <header className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <Avatar src={review.photoUrl ? apiUrl(review.photoUrl) : null} name={review.name} size={36} />
        <span className="font-semibold text-text">{review.name}</span>
        <Stars value={review.rating} size="size-4" label={t("reviews.stars.outOf", { n: formatNumber(review.rating, locale) })} />
        <time dateTime={review.createdAt} className="text-meta text-muted">
          {formatDate(review.createdAt, locale, { year: "auto" })}
        </time>
      </header>
      <p className="whitespace-pre-line text-body text-text">{review.comment}</p>
    </article>
  );
}
