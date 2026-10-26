"use client";

import Link from "next/link";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { dhakaParts } from "@/lib/format";
import { useServerNow } from "@/lib/time/clock";
import { useQueries } from "@tanstack/react-query";
import { moduleExamsQuery, useCategoryTree, type StaffExam } from "@/lib/api/exam/staff-catalog";
import { useParticipationRows, useParticipationSummary, useOpenOrderCount, type ParticipationRow } from "@/lib/api/exam/staff-home";
import { StatusPill } from "@/components/staff/catalog/status-pill";
import { Card } from "@/components/ui/card";
import { ErrorState } from "@/components/ui/error-state";
import { Skeleton } from "@/components/ui/skeleton";

const DAY_MS = 24 * 60 * 60 * 1000;
const AGENDA_DAYS = 14;

/** The Dhaka calendar day of an instant, as a key that sorts and compares. */
function dhakaDayKey(time: number): number {
  const parts = dhakaParts(new Date(time).toISOString());
  return parts.year * 10_000 + parts.month * 100 + parts.day;
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
            <p className="text-meta text-muted">{card.label}</p>
            <p className="mt-1 text-section font-semibold tabular-nums">{formatNumber(card.value)}</p>
          </Card>
        </li>
      ))}
    </ul>
  );
}

/** Shown while the catalog has no category: the first three steps, each linked (first-run, U7). */
function FirstRun() {
  const { t } = useLocale();
  const steps = [
    { key: "category", label: t("staffCatalog.home.onboarding.category"), href: "/staff/categories/new" },
    { key: "module", label: t("staffCatalog.home.onboarding.module"), href: "/staff/modules/new" },
    { key: "exam", label: t("staffCatalog.home.onboarding.exam"), href: "/staff/modules" },
  ];
  return (
    <section aria-labelledby="first-run-heading" className="grid gap-3 rounded-card border border-border bg-card p-5">
      <h2 id="first-run-heading" className="text-card font-semibold">
        {t("staffCatalog.home.onboarding.title")}
      </h2>
      <p className="text-meta text-muted">{t("staffCatalog.home.onboarding.body")}</p>
      <ol className="grid gap-3 sm:grid-cols-3">
        {steps.map((step, index) => (
          <li key={step.key}>
            <Link href={step.href} prefetch className="grid h-full gap-1 rounded-card border border-border p-4 outline-none hover:bg-bg focus-visible:outline-accent">
              <span aria-hidden="true" className="text-meta tabular-nums text-muted">{index + 1}</span>
              <span className="text-label font-medium">{step.label}</span>
            </Link>
          </li>
        ))}
      </ol>
    </section>
  );
}

/** One exam in the agenda: time, title, module, type and how many sat it. */
function AgendaRow({ row, status }: { row: ParticipationRow; status: StaffExam["status"] | undefined }) {
  const { t, locale } = useLocale();
  const { formatNumber, formatTime } = useFormat();
  const moduleTitle = locale === "bn" ? row.moduleTitleBn : row.moduleTitleEn;
  const examTitle = locale === "bn" ? row.titleBn : row.titleEn;
  return (
    <li className="grid gap-1 border-b border-border py-3 last:border-0 sm:grid-cols-[6rem_minmax(0,1fr)_auto] sm:items-baseline sm:gap-4">
      <span className="text-meta tabular-nums text-muted">{row.liveAt ? formatTime(row.liveAt) : t("staffCatalog.dateNotSet")}</span>
      <span className="min-w-0">
        <span className="font-medium">{examTitle}</span>
        <span className="block text-meta text-muted">
          {moduleTitle} · {t(`staffCatalog.examType.${row.examType}`)}
        </span>
      </span>
      <span className="flex flex-wrap items-center gap-3 text-meta">
        {status ? <StatusPill status={status} /> : null}
        <span className="tabular-nums">{t("staffCatalog.home.agenda.participants", { n: formatNumber(row.sitting) })}</span>
        <Link href={`/staff/modules/${row.moduleId}?tab=exams`} className="text-action hover:underline">
          {t("staffCatalog.home.openModule")}
        </Link>
      </span>
    </li>
  );
}

/**
 * The next 14 days of dated exams across all modules, grouped by Dhaka day (UF6).
 * The participation rows have no exam status, so the status comes from each module's exam list,
 * read once per module that has an exam in the window.
 */
function Agenda({ rows, now }: { rows: ParticipationRow[]; now: number }) {
  const { t } = useLocale();
  const { formatDate } = useFormat();
  const today = dhakaDayKey(now);
  const end = now + AGENDA_DAYS * DAY_MS;
  const dated = rows
    .filter((row) => row.liveAt !== null && new Date(row.liveAt).getTime() >= now && new Date(row.liveAt).getTime() < end)
    .sort((a, b) => new Date(a.liveAt as string).getTime() - new Date(b.liveAt as string).getTime());

  const moduleIds = [...new Set(dated.map((row) => row.moduleId))];
  const moduleExams = useQueries({ queries: moduleIds.map((moduleId) => moduleExamsQuery(moduleId)) });
  const statusByExam = new Map<string, StaffExam["status"]>();
  for (const result of moduleExams) {
    for (const exam of result.data?.content ?? []) {
      statusByExam.set(exam.id, exam.status);
    }
  }

  const groups = new Map<number, ParticipationRow[]>();
  for (const row of dated) {
    const key = dhakaDayKey(new Date(row.liveAt as string).getTime());
    groups.set(key, [...(groups.get(key) ?? []), row]);
  }

  return (
    <section aria-labelledby="agenda-heading" className="grid gap-3">
      <h2 id="agenda-heading" className="text-card font-semibold">
        {t("staffCatalog.home.agenda.title")}
      </h2>
      {groups.size === 0 ? (
        <p className="rounded-card border border-border bg-card p-4 text-meta text-muted">{t("staffCatalog.home.agenda.empty")}</p>
      ) : (
        [...groups.entries()].map(([key, list]) => (
          <div key={key} className="grid gap-1">
            <h3 className="text-label font-medium">
              {key === today ? t("staffCatalog.home.agenda.today") : formatDate(new Date(list[0].liveAt as string))}
            </h3>
            <ul className="rounded-card border border-border bg-card px-4">
              {list.map((row) => (
                <AgendaRow key={row.examId} row={row} status={statusByExam.get(row.examId)} />
              ))}
            </ul>
          </div>
        ))
      )}
    </section>
  );
}

/** Master Admin and System Admin home: counts, first-run steps while the catalog is empty, quick actions and the agenda. */
export function AdminHome({ isMasterAdmin }: { isMasterAdmin: boolean }) {
  const { t } = useLocale();
  const now = useServerNow(60_000);
  const categories = useCategoryTree();
  const rows = useParticipationRows({ sort: "date", size: 100 });
  const catalogEmpty = categories.isSuccess && categories.data.length === 0;

  return (
    <div className="grid gap-8">
      {catalogEmpty ? <FirstRun /> : null}

      <SummaryCards showPayments />

      <section aria-labelledby="quick-heading" className="grid gap-2">
        <h2 id="quick-heading" className="text-card font-semibold">
          {t("staffCatalog.home.quick.title")}
        </h2>
        <ul className="flex flex-wrap gap-2">
          <li>
            <Link href="/staff/modules/new" className="inline-flex min-h-11 items-center rounded-control border border-border bg-card px-4 text-label font-medium hover:bg-bg">
              {t("staffCatalog.home.quick.newModule")}
            </Link>
          </li>
          <li>
            <Link href="/staff/modules" className="inline-flex min-h-11 items-center rounded-control border border-border bg-card px-4 text-label font-medium hover:bg-bg">
              {t("staffCatalog.home.quick.newExam")}
            </Link>
          </li>
          <li>
            <Link href="/staff/payments" className="inline-flex min-h-11 items-center rounded-control border border-border bg-card px-4 text-label font-medium hover:bg-bg">
              {t("staffCatalog.home.quick.payments")}
            </Link>
          </li>
          <li>
            <Link href="/staff/students" className="inline-flex min-h-11 items-center rounded-control border border-border bg-card px-4 text-label font-medium hover:bg-bg">
              {t("staffCatalog.home.quick.findStudent")}
            </Link>
          </li>
        </ul>
        {!isMasterAdmin ? <p className="text-meta text-muted">{t("staffCatalog.home.quick.systemAdminNote")}</p> : null}
      </section>

      {rows.isPending ? (
        <Skeleton variant="rows" count={3} />
      ) : rows.isError && !rows.data ? (
        <ErrorState onRetry={() => rows.refetch()} message={t("staffCatalog.home.examsError")} />
      ) : (
        <Agenda rows={rows.data?.content ?? []} now={now} />
      )}
    </div>
  );
}
