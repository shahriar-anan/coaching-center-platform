"use client";

import { Suspense, use } from "react";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pill } from "@/components/ui/pill";
import { Table, type Column } from "@/components/ui/table";
import { ALL_STAFF, RequireStaff } from "@/lib/auth/guards";
import { useLeaderboard, useStaffExam, type LeaderboardRow } from "@/lib/api/exam/staff-ops";
import { serverNow } from "@/lib/time/clock";
import { useFormat } from "@/lib/format/use-format";
import { useLocale } from "@/i18n/locale-context";

/** The five-minute delay after the window before a merit list shows (see the API's leaderboard rule). */
const OPENS_AFTER_MS = 5 * 60 * 1000;

export default function StaffLeaderboardPage({ params }: { params: Promise<{ examId: string }> }) {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <LeaderboardRoute params={params} />
    </Suspense>
  );
}

function LeaderboardRoute({ params }: { params: Promise<{ examId: string }> }) {
  const { examId } = use(params);
  return (
    <RequireStaff roles={ALL_STAFF}>
      <Leaderboard examId={examId} />
    </RequireStaff>
  );
}

function Leaderboard({ examId }: { examId: string }) {
  const { t } = useLocale();
  const format = useFormat();
  const exam = useStaffExam(examId);
  const board = useLeaderboard(examId);

  const title = exam.data ? format.pickTitle({ titleEn: exam.data.titleEn, titleBn: exam.data.titleBn }) : "";

  const columns: Column<LeaderboardRow>[] = [
    { key: "name", header: t("staffOps.leaderboard.columns.name"), cell: (row) => row.name },
    {
      key: "rank",
      header: t("staffOps.leaderboard.columns.rank"),
      cell: (row) => format.formatNumber(row.rank),
    },
    {
      key: "score",
      header: t("staffOps.leaderboard.columns.score"),
      align: "end",
      cell: (row) => format.formatNumber(row.score),
    },
    {
      key: "maximum",
      header: t("staffOps.leaderboard.columns.maximum"),
      align: "end",
      cell: (row) => format.formatNumber(row.maximum),
    },
    {
      key: "pass",
      header: t("staffOps.leaderboard.columns.pass"),
      cell: (row) => (
        <Pill tone={row.passed ? "ok" : "danger"}>
          {row.passed ? t("staffOps.leaderboard.passed") : t("staffOps.leaderboard.failed")}
        </Pill>
      ),
    },
  ];

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("staffOps.leaderboard.title")}
        breadcrumbs={[
          { label: t("common.staffNav.dashboard"), href: "/staff" },
          { label: title || t("staffOps.leaderboard.title") },
        ]}
      />
      <p className="text-meta text-muted">{t("staffOps.leaderboard.readOnly")}</p>
      {exam.data?.examType === "WRITTEN" ? (
        <p className="text-meta text-muted">{t("staffOps.leaderboard.writtenNote")}</p>
      ) : null}

      {board.isError ? (
        <ErrorState onRetry={() => board.refetch()} />
      ) : (
        <Table
          columns={columns}
          rows={board.data?.rows ?? []}
          rowKey={(row) => `${row.rank}-${row.name}`}
          loading={board.isPending}
          empty={<LeaderboardEmpty endAt={exam.data?.endAt ?? null} liveAt={exam.data?.liveAt ?? null} />}
        />
      )}
    </div>
  );
}

/** Says when the list opens, or that the date is not set. */
function LeaderboardEmpty({ liveAt, endAt }: { liveAt: string | null; endAt: string | null }) {
  const { t } = useLocale();
  const format = useFormat();
  if (!liveAt || !endAt) {
    return <EmptyState title={t("staffOps.leaderboard.noDate")} />;
  }
  const opensAt = new Date(endAt).getTime() + OPENS_AFTER_MS;
  if (serverNow() < opensAt) {
    return (
      <EmptyState title={t("staffOps.leaderboard.opensAt", { time: format.formatTime(opensAt) })} />
    );
  }
  return <EmptyState title={t("staffOps.leaderboard.empty")} />;
}
