"use client";

import Link from "next/link";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { dhakaParts } from "@/lib/format";
import { useServerNow } from "@/lib/time/clock";
import { useParticipationRows, useParticipationSummary, useOpenOrderCount, type ParticipationRow } from "@/lib/api/exam/staff-home";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Skeleton } from "@/components/ui/skeleton";

function sameDhakaDay(iso: string | null, now: number): boolean {
  if (!iso) {
    return false;
  }
  const a = dhakaParts(iso);
  const b = dhakaParts(now);
  return a.year === b.year && a.month === b.month && a.day === b.day;
}

/** Three cards: the two participation counts and the payments waiting at the gateway. */
function SummaryCards({ showPayments }: { showPayments: boolean }) {
  const { t } = useLocale();
  const { formatNumber } = useFormat();
  const summary = useParticipationSummary();
  const open = useOpenOrderCount();

  if (summary.isPending) {
    return <Skeleton variant="card" count={showPayments ? 3 : 2} />;
  }
  if (summary.isError && !summary.data) {
    return <ErrorState onRetry={() => summary.refetch()} message={t("staffCatalog.home.summaryError")} />;
  }

  const cards = [
    {
      key: "marking",
      label: t("staffCatalog.home.awaitingMarking"),
      value: summary.data?.awaitingMarking ?? 0,
      href: "/staff/participation",
    },
    {
      key: "publish",
      label: t("staffCatalog.home.awaitingPublish"),
      value: summary.data?.draftsAwaitingPublish ?? 0,
      href: "/staff/participation",
    },
    ...(showPayments
      ? [
          {
            key: "payments",
            label: t("staffCatalog.home.paymentPending"),
            value: open.data ?? 0,
            href: "/staff/payments?status=INITIATED",
          },
        ]
      : []),
  ];

  return (
    <ul className={`grid gap-3 ${showPayments ? "sm:grid-cols-3" : "sm:grid-cols-2"}`}>
      {cards.map((card) => (
        <li key={card.key}>
          <Card href={card.href} className="h-full">
            <p className="text-sm text-muted">{card.label}</p>
            <p className="mt-1 text-3xl font-bold tabular-nums">{formatNumber(card.value)}</p>
          </Card>
        </li>
      ))}
    </ul>
  );
}

function ExamRow({ row }: { row: ParticipationRow }) {
  const { t, locale } = useLocale();
  const { formatNumber, formatDateTime } = useFormat();
  const isWritten = row.examType === "WRITTEN";
  const count = (value: number | null) => (isWritten && value !== null ? formatNumber(value) : "—");
  const moduleTitle = locale === "bn" ? row.moduleTitleBn : row.moduleTitleEn;
  const examTitle = locale === "bn" ? row.titleBn : row.titleEn;

  return (
    <li className="grid gap-2 border-b border-border py-3 last:border-0">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <div className="min-w-0">
          <p className="font-medium">{examTitle}</p>
          <p className="text-sm text-muted">
            {moduleTitle} · {t(`staffCatalog.examType.${row.examType}`)} ·{" "}
            {row.liveAt ? formatDateTime(row.liveAt) : t("staffCatalog.dateNotSet")}
          </p>
        </div>
        <Link href={`/staff/modules/${row.moduleId}?tab=exams`} className="text-sm text-action hover:underline">
          {t("staffCatalog.home.openModule")}
        </Link>
      </div>
      <dl className="grid grid-cols-3 gap-x-4 gap-y-1 text-sm sm:grid-cols-6">
        <div>
          <dt className="text-muted">{t("staffCatalog.home.counts.sitting")}</dt>
          <dd className="tabular-nums">{formatNumber(row.sitting)}</dd>
        </div>
        <div>
          <dt className="text-muted">{t("staffCatalog.home.counts.submitted")}</dt>
          <dd className="tabular-nums">{formatNumber(row.submitted)}</dd>
        </div>
        <div>
          <dt className="text-muted">{t("staffCatalog.home.counts.failed")}</dt>
          <dd className="tabular-nums">{formatNumber(row.failedToSubmit)}</dd>
        </div>
        <div>
          <dt className="text-muted">{t("staffCatalog.home.counts.awaiting")}</dt>
          <dd className="tabular-nums">
            {isWritten && row.awaitingMarking !== null ? (
              <Link href={`/staff/submissions/m/${row.moduleId}?examId=${row.examId}`} className="text-action hover:underline">
                {formatNumber(row.awaitingMarking)}
              </Link>
            ) : (
              count(null)
            )}
          </dd>
        </div>
        <div>
          <dt className="text-muted">{t("staffCatalog.home.counts.drafts")}</dt>
          <dd className="tabular-nums">{count(row.draftsAwaitingPublish)}</dd>
        </div>
        <div>
          <dt className="text-muted">{t("staffCatalog.home.counts.published")}</dt>
          <dd className="tabular-nums">{count(row.published)}</dd>
        </div>
      </dl>
    </li>
  );
}

function ExamList({ title, rows, empty }: { title: string; rows: ParticipationRow[]; empty: string }) {
  return (
    <section aria-label={title} className="grid gap-2">
      <h2 className="text-base font-semibold">{title}</h2>
      {rows.length === 0 ? (
        <p className="rounded-card border border-border bg-card p-4 text-sm text-muted">{empty}</p>
      ) : (
        <ul className="rounded-card border border-border bg-card px-4">
          {rows.map((row) => (
            <ExamRow key={row.examId} row={row} />
          ))}
        </ul>
      )}
    </section>
  );
}

/** Master Admin and System Admin home: counts, quick actions, today's exams and the next dated exams. */
export function AdminHome({ isMasterAdmin }: { isMasterAdmin: boolean }) {
  const { t } = useLocale();
  const now = useServerNow(60_000);
  const rows = useParticipationRows({ sort: "date", size: 20 });

  const list = rows.data?.content ?? [];
  const today = list.filter((row) => sameDhakaDay(row.liveAt, now));
  const upcoming = list.filter((row) => row.liveAt === null || new Date(row.liveAt).getTime() >= now).slice(0, 10);

  return (
    <div className="grid gap-8">
      <SummaryCards showPayments />

      <section aria-labelledby="quick-heading" className="grid gap-2">
        <h2 id="quick-heading" className="text-base font-semibold">
          {t("staffCatalog.home.quick.title")}
        </h2>
        <ul className="flex flex-wrap gap-2">
          <li>
            <Link href="/staff/modules/new" className="inline-flex min-h-11 items-center rounded-control border border-border bg-card px-4 text-sm font-medium hover:bg-bg">
              {t("staffCatalog.home.quick.newModule")}
            </Link>
          </li>
          <li>
            <Link href="/staff/modules" className="inline-flex min-h-11 items-center rounded-control border border-border bg-card px-4 text-sm font-medium hover:bg-bg">
              {t("staffCatalog.home.quick.newExam")}
            </Link>
          </li>
          <li>
            <Link href="/staff/payments" className="inline-flex min-h-11 items-center rounded-control border border-border bg-card px-4 text-sm font-medium hover:bg-bg">
              {t("staffCatalog.home.quick.payments")}
            </Link>
          </li>
          <li>
            <Link href="/staff/students" className="inline-flex min-h-11 items-center rounded-control border border-border bg-card px-4 text-sm font-medium hover:bg-bg">
              {t("staffCatalog.home.quick.findStudent")}
            </Link>
          </li>
        </ul>
        {!isMasterAdmin ? <p className="text-sm text-muted">{t("staffCatalog.home.quick.systemAdminNote")}</p> : null}
      </section>

      {rows.isPending ? (
        <Skeleton variant="rows" count={3} />
      ) : rows.isError && !rows.data ? (
        <ErrorState onRetry={() => rows.refetch()} message={t("staffCatalog.home.examsError")} />
      ) : list.length === 0 ? (
        <EmptyState title={t("staffCatalog.home.noExamsTitle")} body={t("staffCatalog.home.noExamsBody")} />
      ) : (
        <>
          <ExamList title={t("staffCatalog.home.todayTitle")} rows={today} empty={t("staffCatalog.home.todayEmpty")} />
          <ExamList title={t("staffCatalog.home.upcomingTitle")} rows={upcoming} empty={t("staffCatalog.home.upcomingEmpty")} />
        </>
      )}
    </div>
  );
}
