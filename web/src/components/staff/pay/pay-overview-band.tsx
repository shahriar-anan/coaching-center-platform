"use client";

import Link from "next/link";
import { cn } from "@/components/ui/cn";
import { ErrorState } from "@/components/ui/error-state";
import { Skeleton } from "@/components/ui/skeleton";
import { useLocale } from "@/i18n/locale-context";
import { usePayOverview, type PayOverview, type PayOwingRow } from "@/lib/api/exam/staff-pay-analytics";
import { formatDate, formatMoney } from "@/lib/format";

/** How many people the "who you owe next" list shows. The table below lists everyone. */
const NEXT_LIST_SIZE = 5;

/**
 * The band above the Master Admin's people table (staff pay plan, phase 6): four figures and "who you owe next". It loads on
 * its own, so a failed overview never hides the people table below it.
 */
export function PayOverviewBand() {
  const { locale } = useLocale();
  const overview = usePayOverview();

  if (overview.isError) {
    return <ErrorState onRetry={() => void overview.refetch()} error={overview.error} />;
  }
  if (!overview.data) {
    return <Skeleton variant="rows" count={2} />;
  }
  return <OverviewContent data={overview.data} money={(value) => formatMoney(value, locale) ?? ""} />;
}

function OverviewContent({ data, money }: { data: PayOverview; money: (value: number) => string }) {
  const { t, locale } = useLocale();
  const tiles: { key: string; label: string; value: string; body: string; tone?: "danger" }[] = [
    {
      key: "owed",
      label: t("staffPayOverview.overview.owedNow"),
      value: money(data.owedNow.total),
      body: t("staffPayOverview.overview.owedNowBody", {
        salaries: money(data.owedNow.salaries),
        marking: money(data.owedNow.marking),
      }),
    },
    {
      key: "overdue",
      label: t("staffPayOverview.overview.overdue"),
      value: money(data.overdue.total),
      body: t("staffPayOverview.overview.overdueBody"),
      tone: data.overdue.total > 0 ? "danger" : undefined,
    },
    {
      key: "paid",
      label: t("staffPayOverview.overview.paidThisMonth"),
      value: money(data.paidThisMonth),
      body: t("staffPayOverview.overview.paidThisMonthBody"),
    },
    {
      key: "due",
      label: t("staffPayOverview.overview.dueSoon"),
      value: money(data.dueNext30Days.amount),
      body: t("staffPayOverview.overview.dueSoonBody", { date: formatDate(data.dueNext30Days.to, locale) }),
    },
  ];
  const next = data.owing.slice(0, NEXT_LIST_SIZE);

  return (
    <section aria-labelledby="pay-overview-title" className="grid gap-4">
      <h2 id="pay-overview-title" className="text-section font-bold text-text">
        {t("staffPayOverview.overview.title")}
      </h2>
      <dl className="grid grid-cols-2 gap-3 md:grid-cols-4">
        {tiles.map((tile) => (
          <div key={tile.key} className="grid content-start gap-1 rounded-card border border-border bg-card px-4 py-3">
            <dt className="text-meta text-muted">{tile.label}</dt>
            <dd className={cn("text-section font-semibold tabular-nums", tile.tone === "danger" ? "text-danger" : "text-text")}>
              {tile.value}
            </dd>
            <dd className="text-meta text-muted">{tile.body}</dd>
          </div>
        ))}
      </dl>

      <div className="grid gap-2 rounded-card border border-border bg-card px-4 py-4">
        <h3 className="text-label font-bold text-text">{t("staffPayOverview.overview.nextTitle")}</h3>
        <p className="text-meta text-muted">{t("staffPayOverview.overview.nextBody")}</p>
        {next.length === 0 ? (
          <p className="text-body text-muted">{t("staffPayOverview.overview.nextEmpty")}</p>
        ) : (
          <ul className="grid gap-2">
            {next.map((row) => (
              <NextRow key={row.userId} row={row} money={money} />
            ))}
          </ul>
        )}
        {data.owing.length > NEXT_LIST_SIZE ? (
          <p className="text-meta text-muted">{t("staffPayOverview.overview.seeTable")}</p>
        ) : null}
      </div>
    </section>
  );
}

function NextRow({ row, money }: { row: PayOwingRow; money: (value: number) => string }) {
  const { t, locale } = useLocale();
  return (
    <li className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 border-b border-border pb-2 last:border-b-0">
      <Link
        href={`/staff/pay/${row.userId}`}
        className="font-medium text-accent underline-offset-4 hover:underline"
      >
        {row.fullName || t("staffPay.people.unnamed")}
      </Link>
      <span className="flex flex-wrap items-baseline gap-x-3 text-meta text-muted">
        <span className="tabular-nums text-text">{money(row.owedNow.total)}</span>
        {row.overdue.total > 0 ? (
          <span className="tabular-nums text-danger">
            {t("staffPayOverview.overview.overdueAmount", { amount: money(row.overdue.total) })}
          </span>
        ) : null}
        {row.nextDueOn ? <span>{t("staffPayOverview.overview.nextDue", { date: formatDate(row.nextDueOn, locale) })}</span> : null}
      </span>
    </li>
  );
}
