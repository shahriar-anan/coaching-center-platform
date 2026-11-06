import { Quote } from "lucide-react";
import { Section } from "@/components/student/landing/section";
import { Avatar } from "@/components/ui/file/avatar";
import { Stars } from "@/components/ui/stars";
import { useLocale } from "@/i18n/locale-context";
import { useFeaturedReviews, type FeaturedReview } from "@/lib/api/exam/reviews";
import { apiUrl } from "@/lib/config";
import { formatNumber, pickTitle } from "@/lib/format";

/**
 * The staff-picked student reviews, as the old site's review cards in this app's theme. Three in a row from large width, two from
 * medium, and a stack down the page on phones. Nothing moves by itself. The whole section is left out when nothing is featured
 * or the read fails.
 */
export function Reviews() {
  const { t } = useLocale();
  const featured = useFeaturedReviews();
  const items = featured.data ?? [];
  if (featured.isPending || featured.isError || items.length === 0) return null;

  return (
    <Section id="landing-reviews" title={t("reviews.landing.title")} center tone="surface">
      <ul className="grid gap-4 md:grid-cols-2 md:gap-5 lg:grid-cols-3">
        {items.map((review) => (
          <li key={review.id} className="min-w-0">
            <ReviewQuote review={review} />
          </li>
        ))}
      </ul>
    </Section>
  );
}

/** One review: the initial in a circle with the name, the exam or package it is about, the stars, the comment and a quote mark. */
function ReviewQuote({ review }: { review: FeaturedReview }) {
  const { t, locale } = useLocale();
  const target = pickTitle({ titleEn: review.targetTitleEn, titleBn: review.targetTitleBn }, locale);
  return (
    <figure className="flex h-full flex-col gap-4 rounded-card border border-border bg-card p-6 text-start shadow-md">
      <div className="flex items-center justify-between gap-3">
        <Avatar src={review.photoUrl ? apiUrl(review.photoUrl) : null} name={review.name} size={48} />

        <Quote aria-hidden="true" className="size-8 shrink-0 text-accent" />
      </div>
      <figcaption className="grid gap-0.5">
        <span className="font-bold text-text">{review.name}</span>
        {target ? <span className="text-meta text-muted">{target}</span> : null}
      </figcaption>
      <Stars value={review.rating} label={t("reviews.stars.outOf", { n: formatNumber(review.rating, locale) })} />
      <blockquote className="flex-1">
        <p className="whitespace-pre-line text-body text-text">{review.comment}</p>
      </blockquote>
    </figure>
  );
}
