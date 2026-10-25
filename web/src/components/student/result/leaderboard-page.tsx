"use client";

import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { useLocale } from "@/i18n/locale-context";
import { useLeaderboard, useStudentExam, type LeaderboardRow } from "@/lib/api/exam/sitting";
import { formatNumber, formatTime, pickTitle } from "@/lib/format";
import { useServerNow } from "@/lib/time/clock";

/** The merit list opens five minutes after the shared window ends. The API keeps it empty until then. */
export const OPENS_AFTER_END_MS = 5 * 60_000;

/**
 * The moment the merit list opens, or null for an undated exam. Computed from the exam's end time,
 * so the page can say when it opens rather than just "empty".
 */
export function leaderboardOpensAt(endAt: string | null, liveAt: string | null): number | null {
  if (!liveAt || !endAt) {
    return null;
  }
  return Date.parse(endAt) + OPENS_AFTER_END_MS;
}

export function LeaderboardPage({ examId }: { examId: string }) {
  const { t, locale } = useLocale();
  const n = (value: number) => formatNumber(value, locale);
  const now = useServerNow(60_000);
  const exam = useStudentExam(examId);
  const board = useLeaderboard(examId);

  const header = (
    <PageHeader
      title={exam.data ? pickTitle(exam.data, locale) : t("result.board.title")}
      breadcrumbs={[{ label: t("result.board.title") }]}
    />
  );

  if (exam.isPending) {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <Skeleton variant="card" />
      </div>
    );
  }

  if (!exam.data) {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <ErrorState onRetry={() => void exam.refetch()} />
      </div>
    );
  }

  const opensAt = leaderboardOpensAt(exam.data.endAt, exam.data.liveAt);
  if (opensAt === null) {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <EmptyState title={t("result.board.undated")} />
      </div>
    );
  }
  if (now < opensAt) {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <EmptyState
          title={t("result.board.opensAt", { time: formatTime(opensAt, locale) })}
          body={t("result.board.liveOnly")}
        />
      </div>
    );
  }

  const columns: Column<LeaderboardRow>[] = [
    { key: "rank", header: t("result.board.rank"), cell: (row) => n(row.rank), align: "end" },
    { key: "name", header: t("result.board.name"), cell: (row) => row.name, mobileLabel: t("result.board.name") },
    {
      key: "score",
      header: t("result.board.score"),
      cell: (row) => n(row.score),
      mobileLabel: t("result.board.score"),
      align: "end",
    },
    {
      key: "maximum",
      header: t("result.board.maximum"),
      cell: (row) => n(row.maximum),
      mobileLabel: t("result.board.maximum"),
      align: "end",
    },
    {
      key: "outcome",
      header: t("result.board.outcome"),
      cell: (row) => (
        <Pill tone={row.passed ? "ok" : "muted"}>{row.passed ? t("result.outcome.passed") : t("result.outcome.failed")}</Pill>
      ),
      mobileLabel: t("result.board.outcome"),
    },
  ];

  return (
    <div className="flex flex-col gap-4 pb-8">
      {header}
      {board.isError ? (
        <ErrorState onRetry={() => board.refetch()} />
      ) : (
        <Card padding="lg">
          <Table
            columns={columns}
            rows={board.data?.rows ?? []}
            rowKey={(row) => `${row.rank}-${row.name}`}
            loading={board.isPending}
            empty={<EmptyState title={t("result.board.empty")} />}
          />
        </Card>
      )}
    </div>
  );
}
