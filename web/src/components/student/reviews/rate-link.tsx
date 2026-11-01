"use client";

import Link from "next/link";
import { useLocale } from "@/i18n/locale-context";

/**
 * The statuses of a sitting that was submitted and has a result page. The result page shows the review card for these; the
 * other statuses (in progress, failed to submit) have no card to go to.
 */
const RATEABLE_STATUSES = new Set(["AWAITING_RESULT", "RESULT_READY"]);

/** Whether a row can show "Rate": the student sat the exam and has not reviewed it yet. */
export function canRateSitting(status: string | null | undefined, examId: string, reviewed: Set<string>): boolean {
  return Boolean(status && RATEABLE_STATUSES.has(status)) && !reviewed.has(`exam:${examId}`);
}

/** The small "Rate" action on a row, which opens the exam's result page where the review card is. */
export function RateLink({ examId }: { examId: string }) {
  const { t } = useLocale();
  return (
    <Link
      href={`/exam/${examId}/result`}
      className="inline-flex min-h-9 items-center font-semibold text-text-accent underline underline-offset-4"
    >
      {t("reviews.rate")}
    </Link>
  );
}
