"use client";

import { useEffect } from "react";
import Link from "next/link";
import type { UseQueryResult } from "@tanstack/react-query";
import { ChevronRight, Trophy } from "lucide-react";
import { useLocale, type Locale } from "@/i18n/locale-context";
import { formatDate, formatNumber } from "@/lib/format";
import { offlineResultPath, type MyAdmissionResults, type MyExamResult } from "@/lib/api/exam/my-results";
import { ErrorState } from "@/components/ui/error-state";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { IconTile, SectionCard, SectionEmpty } from "./section-card";

/** "1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st": the English ordinal of a rank. */
export function ordinalEn(rank: number): string {
  const lastTwo = rank % 100;
  if (lastTwo >= 11 && lastTwo <= 13) return `${rank}th`;
  const last = rank % 10;
  if (last === 1) return `${rank}st`;
  if (last === 2) return `${rank}nd`;
  if (last === 3) return `${rank}rd`;
  return `${rank}th`;
}

/** A rank as the page writes it: an English ordinal ("5th"), or the Bangla digits. The "of N" wording is the message's. */
export function rankText(rank: number, locale: Locale): string {
  return locale === "bn" ? formatNumber(rank, locale) : ordinalEn(rank);
}

/** The id of one admission's results card: distinct for each admission on the page. */
export function resultsSectionId(admissionId: string): string {
  return `results-${admissionId}`;
}

/** The exams newest first. Dates are yyyy-MM-dd, so a string comparison orders them. */
export function sortNewest(exams: MyExamResult[]): MyExamResult[] {
  return [...exams].sort((a, b) => (a.heldOn < b.heldOn ? 1 : a.heldOn > b.heldOn ? -1 : 0));
}

/** The newest exam this student has marks in (not absent), or null. The glance strip shows it as the last result. */
export function lastMarkedExam(exams: MyExamResult[]): MyExamResult | null {
  return sortNewest(exams).find((exam) => !exam.absent && exam.total != null) ?? null;
}

/** One published exam: the title and date, the total out of full marks, the rank, and the optional pass or fail and parts. */
function ResultRow({ admissionId, exam }: { admissionId: string; exam: MyExamResult }) {
  const { t, locale } = useLocale();
  const title = exam.topic ? `${exam.subject} · ${exam.topic}` : exam.subject;
  const hasRank = exam.rank != null && exam.rankOf != null;
  const parts = [
    exam.mcq != null ? t("myAdmission.results.mcq", { n: formatNumber(exam.mcq, locale) }) : null,
    exam.written != null ? t("myAdmission.results.written", { n: formatNumber(exam.written, locale) }) : null,
  ].filter((part): part is string => part !== null);

  return (
    <li>
      <Link
        href={offlineResultPath(admissionId, exam.classId)}
        className="flex min-h-14 items-center gap-3 px-4 py-3 transition-colors hover:bg-text/[0.04] focus-visible:-outline-offset-2 focus-visible:outline-2 focus-visible:outline-accent sm:px-5"
      >
        <IconTile icon={Trophy} tint="results" size="sm" />
        <span className="grid min-w-0 flex-1 gap-1">
          <span className="break-words font-semibold text-text">{title}</span>
          <span className="text-meta tabular-nums text-muted">{formatDate(exam.heldOn, locale, { year: "auto" })}</span>
          {exam.absent ? (
            <span className="text-body font-semibold text-muted">{t("myAdmission.results.absent")}</span>
          ) : (
            <span className="flex flex-wrap items-center gap-x-2 gap-y-1 text-body">
              {exam.total != null ? (
                <span className="font-semibold tabular-nums text-text">
                  {t("myAdmission.results.score", { total: formatNumber(exam.total, locale), full: formatNumber(exam.fullMarks, locale) })}
                </span>
              ) : null}
              {hasRank ? (
                <span className="tabular-nums text-text-accent">
                  {t("myAdmission.results.rankOf", {
                    rank: rankText(exam.rank ?? 0, locale),
                    of: formatNumber(exam.rankOf ?? 0, locale),
                  })}
                </span>
              ) : null}
              {exam.passMark != null && exam.passed != null ? (
                <Pill tone={exam.passed ? "ok" : "muted"}>
                  {exam.passed ? t("myAdmission.results.passed") : t("myAdmission.results.failed")}
                </Pill>
              ) : null}
              {exam.maybeAbsent ? <Pill tone="muted">{t("myAdmission.results.maybeAbsent")}</Pill> : null}
            </span>
          )}
          {parts.length > 0 && !exam.absent ? <span className="text-meta tabular-nums text-muted">{parts.join(" · ")}</span> : null}
        </span>
        <ChevronRight aria-hidden="true" className="size-4 shrink-0 text-muted" />
      </Link>
    </li>
  );
}

/**
 * The results card (plan 7.10): the batch's published exams, newest first, each a row that opens its leaderboard. Shown for an
 * onboarded or closed admission only. Each card's id is `results-` and the admission id, so a student with two batches has two
 * distinct ids; the glance strip links to its own card.
 *
 * The notice link is `/offline-activities#results`. The section renders after its data loads, so the jump waits for it: once the first
 * results card on the page has its data (or an error), it is scrolled into view and the hash is cleared, so a reload does not jump again.
 */
export function ResultsSection({
  admissionId,
  results,
}: {
  admissionId: string;
  results: UseQueryResult<MyAdmissionResults>;
}) {
  const { t } = useLocale();
  const sectionId = resultsSectionId(admissionId);
  const settled = !results.isPending;

  useEffect(() => {
    if (!settled || window.location.hash !== "#results") {
      return;
    }
    const first = document.querySelector<HTMLElement>('section[id^="results-"]');
    if (first?.id !== sectionId) {
      return;
    }
    first.scrollIntoView({ block: "start" });
    window.history.replaceState(null, "", `${window.location.pathname}${window.location.search}`);
  }, [settled, sectionId]);

  let body;
  if (results.isPending) {
    body = (
      <div className="p-4 sm:p-5">
        <Skeleton variant="card" />
      </div>
    );
  } else if (results.isError || !results.data) {
    body = (
      <div className="p-4 sm:p-5">
        <ErrorState error={results.error} onRetry={() => void results.refetch()} />
      </div>
    );
  } else if (results.data.exams.length === 0) {
    body = (
      <div className="p-4 sm:p-5">
        <SectionEmpty>{t("myAdmission.results.empty")}</SectionEmpty>
      </div>
    );
  } else {
    body = (
      <ul className="divide-y divide-border">
        {sortNewest(results.data.exams).map((exam) => (
          <ResultRow key={exam.classId} admissionId={admissionId} exam={exam} />
        ))}
      </ul>
    );
  }
  return (
    <SectionCard id={sectionId} icon={Trophy} tint="results" title={t("myAdmission.results.title")} flush>
      {body}
    </SectionCard>
  );
}
