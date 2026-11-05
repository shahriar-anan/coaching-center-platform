"use client";

import type { UseQueryResult } from "@tanstack/react-query";
import { useLocale } from "@/i18n/locale-context";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Skeleton } from "@/components/ui/skeleton";
import { Stat, StatRow } from "@/components/ui/stat";
import { Table, type Column } from "@/components/ui/table";
import { useMyMarking, usePersonMarking, type MarkingExamRow, type MarkingView } from "@/lib/api/exam/staff-pay-marking";
import { formatMoney, pickTitle } from "@/lib/format";

/**
 * Marking pay for an instructor (staff pay plan, phase 4): the figures across all exams, and a table per exam. The instructor
 * sees it on "My pay"; the Master Admin sees the same for one instructor on that person's page. Read only.
 */
export function MyMarkingPay() {
  const marking = useMyMarking();
  return <MarkingBody query={marking} />;
}

export function PersonMarkingPay({ userId }: { userId: string }) {
  const marking = usePersonMarking(userId);
  return <MarkingBody query={marking} />;
}

function MarkingBody({ query }: { query: UseQueryResult<MarkingView> }) {
  const { t, locale } = useLocale();

  if (query.isError) {
    return <ErrorState onRetry={() => void query.refetch()} error={query.error} />;
  }
  if (!query.data) {
    return <Skeleton variant="rows" count={2} />;
  }

  const { sheetRate, totals, exams } = query.data;
  const money = (value: number) => formatMoney(value, locale) ?? "";

  const columns: Column<MarkingExamRow>[] = [
    {
      key: "exam",
      header: t("staffPay.marking.exam"),
      cell: (row) => pickTitle(row, locale),
    },
    {
      key: "marked",
      header: t("staffPay.marking.marked"),
      align: "end",
      cell: (row) => <span className="tabular-nums">{row.marked}</span>,
    },
    {
      key: "waiting",
      header: t("staffPay.marking.waiting"),
      align: "end",
      cell: (row) => <span className="tabular-nums">{row.waiting}</span>,
    },
    {
      key: "ready",
      header: t("staffPay.marking.ready"),
      align: "end",
      mobileHidden: true,
      cell: (row) => <span className="tabular-nums">{row.ready}</span>,
    },
    {
      key: "published",
      header: t("staffPay.marking.published"),
      align: "end",
      cell: (row) => <span className="tabular-nums">{row.published}</span>,
    },
    {
      key: "earned",
      header: t("staffPay.marking.earned"),
      align: "end",
      nowrap: true,
      cell: (row) => <span className="tabular-nums">{money(row.earned)}</span>,
    },
    {
      key: "expected",
      header: t("staffPay.marking.expected"),
      align: "end",
      nowrap: true,
      mobileHidden: true,
      cell: (row) => <span className="tabular-nums">{money(row.expected)}</span>,
    },
  ];

  return (
    <section className="grid gap-3" aria-label={t("staffPay.marking.title")}>
      <Card>
        <h2 className="text-section text-text">{t("staffPay.marking.title")}</h2>
        <p className="mt-1 text-meta text-muted">{t("staffPay.marking.rate", { rate: money(sheetRate) })}</p>
        <StatRow className="mt-4">
          <Stat value={String(totals.marked)} label={t("staffPay.marking.marked")} />
          <Stat value={String(totals.waiting)} label={t("staffPay.marking.waiting")} />
          <Stat value={String(totals.ready)} label={t("staffPay.marking.ready")} />
          <Stat value={String(totals.published)} label={t("staffPay.marking.published")} />
          <Stat value={money(totals.earned)} label={t("staffPay.marking.earned")} />
          <Stat value={money(totals.expected)} label={t("staffPay.marking.expected")} />
        </StatRow>
        <p className="mt-3 text-meta text-muted">{t("staffPay.marking.expectedHint")}</p>
      </Card>

      {exams.length === 0 ? (
        <EmptyState title={t("staffPay.marking.empty")} body={t("staffPay.marking.emptyBody")} />
      ) : (
        <Table<MarkingExamRow> columns={columns} rows={exams} rowKey={(row) => row.examId} />
      )}
    </section>
  );
}
