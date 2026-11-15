"use client";

import type { ReactNode } from "react";
import { useLocale } from "@/i18n/locale-context";
import { isNotFoundError } from "@/lib/api/errors";
import { formatDate, formatNumber, pickTitle } from "@/lib/format";
import {
  useMyAdmissionResults,
  useMyExamLeaderboard,
  type MyExamLeaderboard,
  type MyLeaderboardRow,
} from "@/lib/api/exam/my-results";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { cn } from "@/components/ui/cn";
import { ErrorState } from "@/components/ui/error-state";
import { NotFoundState } from "@/components/ui/not-found-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { rankText } from "./results-section";
import { SectionEmpty } from "./section-card";

/** The student's own result, first: the total out of full marks, the rank, the parts and the pass or fail. */
function YourResult({ board, rankedOf }: { board: MyExamLeaderboard; rankedOf: number }) {
  const { t, locale } = useLocale();
  const mine = board.rows.find((row) => row.mine);
  let body: ReactNode;
  if (!mine) {
    body = <p className="text-body text-muted">{t("myAdmission.leaderboard.notListed")}</p>;
  } else if (mine.absent) {
    body = <Pill tone="muted">{t("myAdmission.results.absent")}</Pill>;
  } else {
    const parts = [
      mine.mcq != null ? t("myAdmission.results.mcq", { n: formatNumber(mine.mcq, locale) }) : null,
      mine.written != null ? t("myAdmission.results.written", { n: formatNumber(mine.written, locale) }) : null,
    ].filter((part): part is string => part !== null);
    body = (
      <div className="grid gap-3">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
          <span className="text-title font-bold tabular-nums text-text">
            {t("myAdmission.results.score", {
              total: formatNumber(mine.total ?? 0, locale),
              full: formatNumber(board.fullMarks, locale),
            })}
          </span>
          {mine.rank != null ? (
            <span className="text-body font-semibold tabular-nums text-text-accent">
              {t("myAdmission.results.rankOf", { rank: rankText(mine.rank, locale), of: formatNumber(rankedOf, locale) })}
            </span>
          ) : null}
          {board.passMark != null && mine.passed != null ? (
            <Pill tone={mine.passed ? "ok" : "muted"}>
              {mine.passed ? t("myAdmission.results.passed") : t("myAdmission.results.failed")}
            </Pill>
          ) : null}
          {mine.maybeAbsent ? <Pill tone="muted">{t("myAdmission.results.maybeAbsent")}</Pill> : null}
        </div>
        {parts.length > 0 ? <p className="text-body tabular-nums text-muted">{parts.join(" · ")}</p> : null}
      </div>
    );
  }
  return (
    <section aria-labelledby="your-result-title" className="grid gap-3">
      <h2 id="your-result-title" className="text-card-title font-bold text-text">
        {t("myAdmission.leaderboard.yourResult")}
      </h2>
      <Card padding="lg">{body}</Card>
    </section>
  );
}

/**
 * One row of the list, one line on a phone: the rank, then the name over the roll, then the total and the pass or fail. Absent
 * students have no rank and show "Absent". The student's own row is marked with `aria-current` and a tint, and says so in words.
 */
function LeaderRow({ row, passMark }: { row: MyLeaderboardRow; passMark: number | null }) {
  const { t, locale } = useLocale();
  return (
    <li
      aria-current={row.mine ? "true" : undefined}
      className={cn(
        "grid grid-cols-[2.25rem_minmax(0,1fr)_auto] items-center gap-x-3 px-4 py-3 sm:px-5",
        row.mine && "bg-accent/10",
      )}
    >
      <span className="text-end font-semibold tabular-nums text-text">
        {row.rank != null ? formatNumber(row.rank, locale) : "—"}
      </span>
      <span className="grid min-w-0 gap-0.5">
        <span className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
          <span className="break-words font-semibold text-text">{row.name}</span>
          {row.mine ? <span className="text-meta font-semibold text-text-accent">{t("myAdmission.leaderboard.you")}</span> : null}
          {row.maybeAbsent ? <Pill tone="muted">{t("myAdmission.results.maybeAbsent")}</Pill> : null}
        </span>
        <span className="font-latin text-meta tabular-nums text-muted">
          {t("myAdmission.leaderboard.roll")} {row.roll}
        </span>
      </span>
      <span className="grid justify-items-end gap-1 text-end">
        {row.absent ? (
          <span className="font-semibold text-muted">{t("myAdmission.results.absent")}</span>
        ) : (
          <span className="font-semibold tabular-nums text-text">{formatNumber(row.total ?? 0, locale)}</span>
        )}
        {passMark != null && !row.absent && row.passed != null ? (
          <Pill tone={row.passed ? "ok" : "muted"}>{row.passed ? t("myAdmission.results.passed") : t("myAdmission.results.failed")}</Pill>
        ) : null}
      </span>
    </li>
  );
}

/**
 * `/offline-activities/results/[admissionId]/[classId]` (plan 7.11): one exam's leaderboard for the student's own admission. The header
 * names the exam, its date and the batch; then "Your result"; then the list, in the order the API sends it (rank, then roll; absent
 * students last).
 */
export function OfflineLeaderboardPage({ admissionId, classId }: { admissionId: string; classId: string }) {
  const { t, locale } = useLocale();
  const results = useMyAdmissionResults(admissionId);
  const board = useMyExamLeaderboard(admissionId, classId);

  const title = board.data
    ? board.data.topic
      ? `${board.data.subject} · ${board.data.topic}`
      : board.data.subject
    : t("myAdmission.leaderboard.title");
  const batch = results.data ? pickTitle({ titleEn: results.data.batchNameEn, titleBn: results.data.batchNameBn }, locale) : "";
  const meta = [board.data ? formatDate(board.data.heldOn, locale, { year: "auto" }) : null, batch || null]
    .filter((part): part is string => part !== null)
    .join(" · ");

  const header = (
    <>
      <PageHeader
        title={title}
        breadcrumbs={[
          { label: t("catalog.home"), href: "/" },
          { label: t("publicAdmission.activities.title"), href: "/offline-activities" },
          { label: title },
        ]}
        actions={
          <Button variant="secondary" size="sm" href="/offline-activities">
            {t("myAdmission.leaderboard.back")}
          </Button>
        }
      />
      {meta ? <p className="-mt-3 pb-5 text-body text-muted">{meta}</p> : null}
    </>
  );

  if (board.isPending) {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <Skeleton variant="card" count={2} />
      </div>
    );
  }
  if (board.isError || !board.data) {
    if (isNotFoundError(board.error)) {
      return (
        <div className="flex flex-col gap-4">
          {header}
          <NotFoundState
            title={t("common.notFound.title")}
            body={t("common.notFound.body")}
            backHref="/offline-activities"
            backLabel={t("myAdmission.leaderboard.back")}
          />
        </div>
      );
    }
    return (
      <div className="flex flex-col gap-4">
        {header}
        <ErrorState error={board.error} onRetry={() => void board.refetch()} />
      </div>
    );
  }

  const data = board.data;
  const rows = data.rows;
  const rankedOf = rows.filter((row) => row.rank != null).length;
  return (
    <div className="flex flex-col gap-5 pb-8">
      {header}
      <YourResult board={data} rankedOf={rankedOf} />
      <section aria-labelledby="leaderboard-title" className="overflow-hidden rounded-card border border-border bg-card text-text shadow-[var(--card-shadow)]">
        <h2 id="leaderboard-title" className="border-b border-border px-4 py-3 text-card-title font-bold sm:px-5">
          {t("myAdmission.leaderboard.list")}
        </h2>
        {rows.length === 0 ? (
          <div className="p-4 sm:p-5">
            <SectionEmpty>{t("myAdmission.leaderboard.empty")}</SectionEmpty>
          </div>
        ) : (
          <ul aria-label={t("myAdmission.leaderboard.list")} className="divide-y divide-border">
            {rows.map((row, index) => (
              <LeaderRow key={`${row.roll}-${index}`} row={row} passMark={data.passMark} />
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
