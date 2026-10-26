"use client";

import Link from "next/link";
import { useState } from "react";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Pagination } from "@/components/ui/pagination";
import { Pill } from "@/components/ui/pill";
import { Table, type Column } from "@/components/ui/table";
import { useLocale } from "@/i18n/locale-context";
import { useStudentExamHistory, type ExamHistoryRow } from "@/lib/api/exam/staff-results";
import { useFormat } from "@/lib/format/use-format";
import { publishedTone, resultStatusTone, resultTone } from "./results-copy";

/**
 * A student's sittings across exams, newest first, on the staff student page. Each exam
 * title opens that exam's results report.
 */
export function StudentExamHistory({ studentId }: { studentId: string }) {
  const { t } = useLocale();
  const format = useFormat();
  const [page, setPage] = useState(0);
  const history = useStudentExamHistory(studentId, page);

  const columns: Column<ExamHistoryRow>[] = [
    {
      key: "exam",
      header: t("staffResults.history.columns.exam"),
      cell: (row) => (
        <Link
          href={`/staff/exams/${row.examId}/results`}
          className="font-medium text-text underline-offset-4 hover:underline"
        >
          {format.pickTitle({ titleEn: row.examTitleEn, titleBn: row.examTitleBn })}
        </Link>
      ),
    },
    {
      key: "module",
      header: t("staffResults.history.columns.module"),
      cell: (row) => format.pickTitle({ titleEn: row.moduleTitleEn, titleBn: row.moduleTitleBn }),
    },
    {
      key: "kind",
      header: t("staffResults.history.columns.kind"),
      cell: (row) => <Pill tone={row.kind === "LIVE" ? "accent" : "muted"}>{t(`staffResults.kind.${row.kind}`)}</Pill>,
    },
    {
      key: "status",
      header: t("staffResults.history.columns.status"),
      cell: (row) => <Pill tone={resultStatusTone(row.status)}>{t(`staffResults.status.${row.status}`)}</Pill>,
    },
    {
      key: "score",
      header: t("staffResults.history.columns.score"),
      align: "end",
      cell: (row) =>
        row.score === null
          ? t("staffResults.result.none")
          : `${format.formatNumber(row.score)} / ${format.formatNumber(row.maximum)}`,
    },
    {
      key: "result",
      header: t("staffResults.history.columns.result"),
      cell: (row) =>
        row.passed === null ? (
          t("staffResults.result.none")
        ) : (
          <Pill tone={resultTone(row.passed)}>
            {t(row.passed ? "staffResults.result.passed" : "staffResults.result.failed")}
          </Pill>
        ),
    },
    {
      key: "submitted",
      header: t("staffResults.history.columns.submitted"),
      cell: (row) => (row.submittedAt ? format.formatDateTime(row.submittedAt) : t("staffResults.result.none")),
    },
    {
      key: "published",
      header: t("staffResults.history.columns.published"),
      cell: (row) => (
        <Pill tone={publishedTone(row.published)}>
          {t(row.published ? "staffResults.published.yes" : "staffResults.published.no")}
        </Pill>
      ),
    },
  ];

  if (history.isError) {
    return <ErrorState onRetry={() => history.refetch()} />;
  }

  return (
    <div className="grid gap-3">
      <p className="text-meta text-muted">{t("staffResults.history.readOnly")}</p>
      <Table
        columns={columns}
        rows={history.data?.content ?? []}
        rowKey={(row) => row.participantId}
        loading={history.isPending}
        empty={<EmptyState title={t("staffResults.history.empty")} />}
      />
      {history.data ? (
        <Pagination page={history.data.page} totalPages={history.data.totalPages} onPage={setPage} />
      ) : null}
    </div>
  );
}
