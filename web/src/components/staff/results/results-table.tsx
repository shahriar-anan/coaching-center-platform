"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { Pill } from "@/components/ui/pill";
import { RowMenu } from "@/components/ui/row-menu";
import { Table, type Column } from "@/components/ui/table";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import type { ResultsRow } from "@/lib/api/exam/staff-results";
import { publishedTone, resultStatusTone, resultTone } from "./results-copy";

/**
 * The results report's table. Numbers are right-aligned, status is a pill, and the row
 * menu offers only the next actions the role may take. A student link is shown only to
 * Master Admin and System Admin; an instructor sees the name and the code.
 */
export function ResultsTable({
  rows,
  loading,
  empty,
  examWritten,
  canOpenStudents,
  leaderboardHref,
}: {
  rows: ResultsRow[];
  loading?: boolean;
  empty?: ReactNode;
  examWritten: boolean;
  canOpenStudents: boolean;
  leaderboardHref: string;
}) {
  const { t } = useLocale();
  const format = useFormat();

  const columns: Column<ResultsRow>[] = [
    {
      key: "student",
      header: t("staffResults.columns.student"),
      cell: (row) => (
        <div className="grid gap-0.5">
          {canOpenStudents ? (
            <Link
              href={`/staff/students/${row.studentId}`}
              className="font-medium text-text underline-offset-4 hover:underline"
            >
              {row.studentName}
            </Link>
          ) : (
            <span className="font-medium text-text">{row.studentName}</span>
          )}
          {row.studentCode ? <span className="font-latin text-meta text-muted">{row.studentCode}</span> : null}
        </div>
      ),
    },
    {
      key: "kind",
      header: t("staffResults.columns.kind"),
      mobileHidden: true,
      cell: (row) => <Pill tone={row.kind === "LIVE" ? "accent" : "muted"}>{t(`staffResults.kind.${row.kind}`)}</Pill>,
    },
    {
      key: "status",
      header: t("staffResults.columns.status"),
      cell: (row) => (
        <Pill tone={resultStatusTone(row.status)}>{t(`staffResults.status.${row.status}`)}</Pill>
      ),
    },
    {
      key: "score",
      header: t("staffResults.columns.score"),
      align: "end",
      cell: (row) => ((row.score ?? null) === null ? t("staffResults.result.none") : format.formatNumber(row.score ?? 0)),
    },
    {
      key: "maximum",
      header: t("staffResults.columns.maximum"),
      mobileHidden: true,
      align: "end",
      cell: (row) => format.formatNumber(row.maximum),
    },
    {
      key: "result",
      header: t("staffResults.columns.result"),
      cell: (row) =>
        (row.passed ?? null) === null ? (
          t("staffResults.result.none")
        ) : (
          <Pill tone={resultTone(row.passed)}>
            {t(row.passed ? "staffResults.result.passed" : "staffResults.result.failed")}
          </Pill>
        ),
    },
    {
      key: "started",
      header: t("staffResults.columns.started"),
      nowrap: true,
      mobileHidden: true,
      cell: (row) => (row.startedAt ? format.formatDateTime(row.startedAt) : t("staffResults.result.none")),
    },
    {
      key: "submitted",
      header: t("staffResults.columns.submitted"),
      nowrap: true,
      cell: (row) => (row.submittedAt ? format.formatDateTime(row.submittedAt) : t("staffResults.result.none")),
    },
    {
      key: "published",
      header: t("staffResults.columns.published"),
      nowrap: true,
      cell: (row) => {
        if (!examWritten) {
          return <span className="text-muted">{t("staffResults.published.notWritten")}</span>;
        }
        if (row.releasePending) {
          return <Pill tone="warn">{t("staffResults.published.changeWaiting")}</Pill>;
        }
        // The API leaves out a null field, so test for a missing value as well as null.
        const published = (row.publishedAt ?? null) !== null;
        return (
          <Pill tone={publishedTone(published)}>
            {t(published ? "staffResults.published.yes" : "staffResults.published.no")}
          </Pill>
        );
      },
    },
    {
      key: "menu",
      header: "",
      align: "end",
      cell: (row) => (
        <RowMenu
          label={t("staffResults.rowMenu.label", { name: row.studentName })}
          items={[
            ...(canOpenStudents
              ? [{ label: t("staffResults.rowMenu.openStudent"), href: `/staff/students/${row.studentId}` }]
              : []),
            { label: t("staffResults.rowMenu.openLeaderboard"), href: leaderboardHref },
          ]}
        />
      ),
    },
  ];

  return (
    <Table
      columns={columns}
      rows={rows}
      rowKey={(row) => row.participantId}
      loading={loading}
      empty={empty}
    />
  );
}
