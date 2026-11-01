"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Pill } from "@/components/ui/pill";
import { SummaryStrip } from "@/components/ui/summary-strip";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { failureKind, groupByCategory, isTodayInDhaka, statusTone, withQuery } from "@/components/staff/marking/marking-queue";
import { useSession } from "@/lib/auth/session-context";
import { useParticipationRows, type ParticipationRow } from "@/lib/api/exam/staff-home";
import { FEED_PAGE_MAX, useFeed, useMarkedToday, type FeedRow } from "@/lib/api/exam/staff-marking";
import { useFormat } from "@/lib/format/use-format";
import { useServerNow } from "@/lib/time/clock";
import { useLocale } from "@/i18n/locale-context";

/** The address of one paper: its own page, with its exam so the queue is that exam's papers. */
function paperLink(row: Pick<FeedRow, "participantId" | "examId">): string {
  return withQuery(`/staff/submissions/${row.participantId}`, { exam: row.examId });
}

/** The browser, opened on one exam. */
function examLink(examId: string): string {
  return withQuery("/staff/submissions", { exam: examId });
}

/**
 * The instructor's home as a marking dashboard: the work waiting, the next paper to open, the most recent papers, and
 * the assigned exams grouped by category. Master Admin and System Admin never see this; they get the admin home.
 */
export function InstructorHome() {
  const { t } = useLocale();
  const format = useFormat();
  const router = useRouter();
  const { session } = useSession();
  const name = session.status === "authenticated" ? session.user.fullName : undefined;
  const now = useServerNow(60_000);

  // The assigned exams come from the participation list, which already holds only this instructor's exams. A
  // written exam with no package still shows here, so the page does not depend on the module list.
  const written = useParticipationRows({ type: "WRITTEN", sort: "liveAt", size: FEED_PAGE_MAX });
  const oldestWaiting = useFeed({ categoryId: "", status: "AWAITING", moduleId: "", examId: "", q: "", sort: "submittedAt,asc", page: 0, size: 1 });
  const recent = useFeed({ categoryId: "", status: "", moduleId: "", examId: "", q: "", sort: "submittedAt,desc", page: 0, size: 10 });
  const today = useMarkedToday(name, (iso) => isTodayInDhaka(iso, now));

  const rows = useMemo(() => written.data?.content ?? [], [written.data]);
  const totals = useMemo(
    () => ({
      awaiting: sum(rows, (row) => row.awaitingMarking),
      drafts: sum(rows, (row) => row.draftsAwaitingPublish),
      corrections: sum(rows, (row) => row.correctionsWaiting ?? null),
      published: sum(rows, (row) => row.published),
    }),
    [rows],
  );
  const groups = useMemo(
    () => groupByCategory<ParticipationRow>(rows, format.pickTitle, t("staffMarking.browser.uncategorised")),
    [rows, format.pickTitle, t],
  );

  if (written.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  if (written.isError && !written.data) {
    const failure = failureKind(written.error);
    return (
      <ErrorState onRetry={() => written.refetch()} message={failure ? t(`staffMarking.errors.${failure}`) : undefined} error={written.error} />
    );
  }
  if (rows.length === 0) {
    return <EmptyState title={t("staffMarking.home.emptyTitle")} body={t("staffMarking.home.emptyBody")} />;
  }

  const oldest = oldestWaiting.data?.content[0];
  const recentRows = recent.data?.content ?? [];

  const columns: Column<FeedRow>[] = [
    { key: "student", header: t("staffMarking.browser.student"), cell: (row) => row.studentName },
    {
      key: "exam",
      header: t("staffMarking.browser.exam"),
      cell: (row) => format.pickTitle({ titleEn: row.examTitleEn, titleBn: row.examTitleBn }),
    },
    {
      key: "submitted",
      header: t("staffMarking.browser.submitted"),
      cell: (row) => submittedText(row.submittedAt),
    },
    {
      key: "status",
      header: t("staffMarking.browser.status"),
      cell: (row) => <Pill tone={statusTone(row.status)}>{t(`staffMarking.statusWord.${row.status}`)}</Pill>,
    },
  ];

  function submittedText(iso: string | null): string {
    if (!iso) {
      return "—";
    }
    const age = now - Date.parse(iso);
    if (age >= 0 && age < 86_400_000) {
      return t("staffMarking.time.ago", { duration: format.formatRelative(age) });
    }
    return format.formatDateTime(iso);
  }

  return (
    <div className="grid gap-6">
      <section className="grid gap-4">
        <SummaryStrip
          loading={written.isPending}
          items={[
            {
              label: t("staffMarking.home.awaiting"),
              value: format.formatNumber(totals.awaiting),
              tone: totals.awaiting > 0 ? "warn" : "muted",
              href: "/staff/submissions",
            },
            { label: t("staffMarking.home.drafts"), value: format.formatNumber(totals.drafts), tone: "muted" },
            { label: t("staffMarking.home.corrections"), value: format.formatNumber(totals.corrections), tone: "muted" },
            { label: t("staffMarking.home.published"), value: format.formatNumber(totals.published), tone: "ok" },
          ]}
        />

        <Card>
          <div className="grid gap-3 sm:flex sm:items-center sm:justify-between">
            <div className="grid gap-1">
              <Button disabled={!oldest} href={oldest ? paperLink(oldest) : undefined}>
                {t("staffMarking.home.start")}
              </Button>
              {oldest || oldestWaiting.isPending ? null : (
                <p className="text-meta text-muted">{t("staffMarking.home.nothingWaiting")}</p>
              )}
            </div>
            {today.count !== null && !today.isPending ? (
              <p className="text-label font-medium text-text">
                {t("staffMarking.home.today", { count: format.formatNumber(today.count) })}
              </p>
            ) : null}
          </div>
        </Card>
      </section>

      <section className="grid gap-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-section font-semibold text-text">{t("staffMarking.home.recent")}</h2>
          <Link href="/staff/submissions" className="text-label font-medium text-action hover:underline">
            {t("staffMarking.home.allPapers")}
          </Link>
        </div>
        {recent.isError ? (
          <ErrorState onRetry={() => recent.refetch()} error={recent.error} />
        ) : (
          <Table
            columns={columns}
            rows={recentRows}
            rowKey={(row) => row.participantId}
            loading={recent.isPending}
            onRowClick={(row) => router.push(paperLink(row))}
            empty={<EmptyState title={t("staffMarking.home.recentEmpty")} />}
          />
        )}
      </section>

      <section className="grid gap-4">
        <h2 className="text-section font-semibold text-text">{t("staffMarking.home.exams")}</h2>
        {groups.map((group) => (
          <Card key={group.key} className="grid gap-3">
            <div className="grid gap-1">
              <h3 className="text-card-title font-semibold text-text">{group.label}</h3>
              <p className="text-meta text-muted">
                {t("staffMarking.home.groupWaiting", { count: format.formatNumber(sum(group.items, (row) => row.awaitingMarking)) })}
              </p>
            </div>
            <ul className="grid gap-2">
              {group.items.map((exam) => (
                <li key={exam.examId} className="flex flex-wrap items-center justify-between gap-2">
                  <span className="min-w-0 text-body text-text">{format.pickTitle(exam)}</span>
                  <span className="flex items-center gap-3 text-meta">
                    <span className="tabular-nums text-muted">
                      {t("staffMarking.home.examWaiting", { count: format.formatNumber(exam.awaitingMarking ?? 0) })}
                    </span>
                    <Link href={examLink(exam.examId)} className="font-medium text-action hover:underline">
                      {t("staffMarking.home.openExam")}
                    </Link>
                  </span>
                </li>
              ))}
            </ul>
          </Card>
        ))}
      </section>
    </div>
  );
}

function sum<T>(items: T[], pick: (item: T) => number | null): number {
  return items.reduce((total, item) => total + (pick(item) ?? 0), 0);
}
