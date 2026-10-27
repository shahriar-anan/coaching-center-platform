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
import { groupByCategory, isTodayInDhaka, statusTone } from "@/components/staff/marking/marking-queue";
import { useSession } from "@/lib/auth/session-context";
import { useParticipationRows, type ParticipationRow } from "@/lib/api/exam/staff-home";
import {
  FEED_PAGE_MAX,
  useFeed,
  useMarkedToday,
  useMarkingModules,
  type FeedRow,
  type MarkingModule,
} from "@/lib/api/exam/staff-marking";
import { useFormat } from "@/lib/format/use-format";
import { useServerNow } from "@/lib/time/clock";
import { useLocale } from "@/i18n/locale-context";

/**
 * The instructor's home as a marking dashboard: the work waiting, the next paper to open,
 * the most recent papers, and the assigned modules grouped by category. Master Admin and
 * System Admin never see this; they get the admin home.
 */
export function InstructorHome() {
  const { t } = useLocale();
  const format = useFormat();
  const router = useRouter();
  const { session } = useSession();
  const name = session.status === "authenticated" ? session.user.fullName : undefined;
  const now = useServerNow(60_000);

  const modules = useMarkingModules();
  const written = useParticipationRows({ type: "WRITTEN", sort: "liveAt", size: FEED_PAGE_MAX });
  const oldestWaiting = useFeed({ status: "AWAITING", moduleId: "", examId: "", q: "", sort: "submittedAt,asc", page: 0, size: 1 });
  const recent = useFeed({ status: "", moduleId: "", examId: "", q: "", sort: "submittedAt,desc", page: 0, size: 10 });
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

  if (modules.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  if (modules.isError && !modules.data) {
    return <ErrorState onRetry={() => modules.refetch()} message={t("staffMarking.home.modulesError")} />;
  }

  const list = modules.data?.content ?? [];
  if (list.length === 0) {
    return <EmptyState title={t("staffMarking.home.emptyTitle")} body={t("staffMarking.home.emptyBody")} />;
  }

  const oldest = oldestWaiting.data?.content[0];
  const recentRows = recent.data?.content ?? [];
  const groups = groupByCategory(list, format.pickTitle);

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
              <Button
                disabled={!oldest}
                href={oldest ? `/staff/submissions/${oldest.participantId}?m=${oldest.moduleId}` : undefined}
              >
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
          <ErrorState onRetry={() => recent.refetch()} />
        ) : (
          <Table
            columns={columns}
            rows={recentRows}
            rowKey={(row) => row.participantId}
            loading={recent.isPending}
            onRowClick={(row) => router.push(`/staff/submissions/${row.participantId}?m=${row.moduleId}`)}
            empty={<EmptyState title={t("staffMarking.home.recentEmpty")} />}
          />
        )}
      </section>

      <section className="grid gap-4">
        <h2 className="text-section font-semibold text-text">{t("staffMarking.home.modules")}</h2>
        {groups.map((group) => (
          <div key={group.key} className="grid gap-3">
            <h3 className="text-meta font-medium text-muted">{group.label}</h3>
            <ul className="grid gap-4 sm:grid-cols-2">
              {group.modules.map((module) => (
                <li key={module.id}>
                  <ModuleCard module={module} rows={rows} />
                </li>
              ))}
            </ul>
          </div>
        ))}
      </section>
    </div>
  );
}

/** One assigned module: its written exams, each with its own waiting count and a link to its papers. */
function ModuleCard({ module, rows }: { module: MarkingModule; rows: ParticipationRow[] }) {
  const { t } = useLocale();
  const format = useFormat();
  const exams = rows.filter((row) => row.moduleId === module.id);
  const waiting = sum(exams, (row) => row.awaitingMarking);

  return (
    <Card className="grid h-full gap-3">
      <div className="grid gap-1">
        <h4 className="text-card-title font-semibold text-text">{format.pickTitle(module)}</h4>
        <p className="text-meta text-muted">
          {t("staffMarking.home.moduleWaiting", { count: format.formatNumber(waiting) })}
        </p>
      </div>
      {exams.length === 0 ? (
        <p className="text-meta text-muted">{t("staffMarking.home.noWritten")}</p>
      ) : (
        <ul className="grid gap-2">
          {exams.map((exam) => (
            <li key={exam.examId} className="flex flex-wrap items-center justify-between gap-2">
              <span className="min-w-0 text-body text-text">{format.pickTitle(exam)}</span>
              <span className="flex items-center gap-3 text-meta">
                <span className="tabular-nums text-muted">
                  {t("staffMarking.home.examWaiting", { count: format.formatNumber(exam.awaitingMarking ?? 0) })}
                </span>
                <Link
                  href={`/staff/submissions?module=${module.id}&exam=${exam.examId}`}
                  className="font-medium text-action hover:underline"
                >
                  {t("staffMarking.home.openExam")}
                </Link>
              </span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function sum<T>(items: T[], pick: (item: T) => number | null): number {
  return items.reduce((total, item) => total + (pick(item) ?? 0), 0);
}
