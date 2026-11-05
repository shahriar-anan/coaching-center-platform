"use client";

import type { ReactNode } from "react";
import { useLocale } from "@/i18n/locale-context";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { useMyPayExams, type MyPayExamRow } from "@/lib/api/exam/staff-pay-terms";
import { formatMoney, pickTitle } from "@/lib/format";

/**
 * "My exams" for an instructor (staff pay plan, phase 8): each written exam assigned to the signed-in instructor, with the
 * rate per sheet, the sheets marked and the sheets published. Read only. It mounts only for an instructor.
 */
export function MyExamsSection() {
  const { t, locale } = useLocale();
  const exams = useMyPayExams();

  const columns: Column<MyPayExamRow>[] = [
    {
      key: "exam",
      header: t("staffPay.myExams.exam"),
      cell: (row) => pickTitle(row, locale),
    },
    {
      key: "rate",
      header: t("staffPay.myExams.rate"),
      align: "end",
      nowrap: true,
      cell: (row) => <span className="tabular-nums">{formatMoney(row.sheetRate, locale) ?? ""}</span>,
    },
    {
      key: "marked",
      header: t("staffPay.myExams.marked"),
      align: "end",
      cell: (row) => <span className="tabular-nums">{row.markedCount}</span>,
    },
    {
      key: "published",
      header: t("staffPay.myExams.published"),
      align: "end",
      cell: (row) => <span className="tabular-nums">{row.publishedCount}</span>,
    },
  ];

  let body: ReactNode;
  if (exams.isError) {
    body = <ErrorState onRetry={() => void exams.refetch()} error={exams.error} />;
  } else if (!exams.data) {
    body = <Skeleton variant="rows" count={2} />;
  } else if (exams.data.rows.length === 0) {
    body = <EmptyState title={t("staffPay.myExams.empty")} body={t("staffPay.myExams.emptyBody")} />;
  } else {
    body = <Table<MyPayExamRow> columns={columns} rows={exams.data.rows} rowKey={(row) => row.examId} />;
  }

  return (
    <section className="grid gap-3" aria-label={t("staffPay.myExams.title")}>
      <h2 className="text-section text-text">{t("staffPay.myExams.title")}</h2>
      {body}
    </section>
  );
}
