"use client";

import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { RatingBadge } from "@/components/student/reviews/rating-badge";
import { ReviewCard } from "@/components/student/reviews/review-card";
import { ReviewsSection } from "@/components/student/reviews/reviews-section";
import { ErrorState } from "@/components/ui/error-state";
import { Skeleton } from "@/components/ui/skeleton";
import { useLocale } from "@/i18n/locale-context";
import { usePublicExam } from "@/lib/api/exam/catalog";
import { useSession } from "@/lib/auth/session-context";
import { pickTitle } from "@/lib/format";
import { examPath } from "@/components/student/catalog/catalog-helpers";

/**
 * The reviews of one exam, on a page of their own (the exam page's Reviews button). Anyone can read it. A student who sat the
 * exam also sees the card to rate it.
 */
export function ExamReviewsPage({ examId }: { examId: string }) {
  const { t, locale } = useLocale();
  const { session } = useSession();
  const signedIn = session.status === "authenticated";
  const exam = usePublicExam(examId, signedIn);

  if (exam.isPending) return <Skeleton variant="card" count={2} />;
  if (exam.isError || !exam.data) return <ErrorState error={exam.error} onRetry={() => void exam.refetch()} />;

  const title = pickTitle(exam.data, locale);
  return (
    <div className="mx-auto grid max-w-3xl gap-5">
      <Link
        href={examPath(examId)}
        className="inline-flex min-h-11 items-center gap-1 text-label font-semibold text-text-accent hover:underline"
      >
        <ChevronLeft aria-hidden="true" className="size-4 rtl:rotate-180" />
        {t("reviews.page.back")}
      </Link>
      <header className="grid gap-1">
        <p className="text-body text-muted">{title}</p>
        <h1 className="text-2xl font-extrabold leading-tight tracking-tight md:text-3xl">{t("reviews.page.title")}</h1>
        <RatingBadge average={exam.data.ratingAverage} count={exam.data.ratingCount} />
      </header>
      <ReviewCard kind="exam" id={examId} />
      <ReviewsSection kind="exam" id={examId} showEmpty />
    </div>
  );
}
