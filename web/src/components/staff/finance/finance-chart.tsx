"use client";

import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { formatMoney, formatNumber } from "@/lib/format";
import { axisLabel } from "@/components/staff/pay/pay-analytics-format";
import { INCOME_SOURCES, EXPENSE_SOURCES, type FinanceSource } from "@/lib/api/exam/finance";
import {
  bucketExpense,
  bucketIncome,
  chartDomain,
  yPercent,
  type ChartBucket,
} from "@/components/staff/finance/finance-model";
import { SOURCE_COLOR } from "@/components/staff/finance/finance-theme";
import { cn } from "@/components/ui/cn";

// The chart (plan 8.4): one column per month or week, an income bar and an expenditure bar side by side, each stacked by source, and a
// net line across them. Plain HTML and SVG, as the staff pay chart is. The legend toggles a source on and off in the chart only; the
// filter does not change. Every column has a title with its figures, and a table under the chart gives the same numbers, so no
// reading rests on colour alone.

/** A column is at least this wide; a long range scrolls sideways inside the chart's own box. */
const MIN_COLUMN_PX = 56;

/** Each bar is this wide, with a gap between the income and expenditure bars. */
const BAR_PX = 14;

export function FinanceChart({
  buckets,
  sources,
  periodLabel,
  weekCappedAt,
}: {
  buckets: ChartBucket[];
  /** The sources the filter selects: the legend lists them all, and the chart shows the ones not toggled off. */
  sources: FinanceSource[];
  /** The label under a column, for its key (a month or a week). */
  periodLabel: (key: string) => string;
  /** Set when the weekly bars stopped reading early: the count of entries they did read. */
  weekCappedAt?: number;
}) {
  const { t, locale } = useLocale();
  const [hidden, setHidden] = useState<Set<FinanceSource>>(() => new Set());
  const shown = sources.filter((source) => !hidden.has(source));
  const incomeShown = shown.filter((source) => INCOME_SOURCES.includes(source));
  const expenseShown = shown.filter((source) => EXPENSE_SOURCES.includes(source));
  const domain = chartDomain(buckets, shown);
  const money = (value: number) => formatMoney(value, locale) ?? "";
  const ticks = domain.min < 0 ? [domain.max, (domain.max + domain.min) / 2, domain.min] : [domain.max, domain.max / 2, 0];
  const count = Math.max(buckets.length, 1);
  const netPoints = buckets
    .map((bucket, index) => {
      const net = bucketIncome(bucket, shown) - bucketExpense(bucket, shown);
      return `${((index + 0.5) / count) * 100},${100 - yPercent(net, domain)}`;
    })
    .join(" ");

  function toggle(source: FinanceSource) {
    setHidden((current) => {
      const next = new Set(current);
      if (next.has(source)) {
        next.delete(source);
      } else {
        next.add(source);
      }
      return next;
    });
  }

  /** The stacked segments of one bar, bottom to top, each as a band of the plot's height. */
  function segments(bucket: ChartBucket, list: FinanceSource[]) {
    let running = 0;
    return list
      .map((source, index) => {
        const value = bucket.bySource[source] ?? 0;
        if (value <= 0) {
          return null;
        }
        const bottom = yPercent(running, domain);
        running += value;
        const top = yPercent(running, domain);
        return { source, index, bottom, height: top - bottom };
      })
      .filter((band): band is { source: FinanceSource; index: number; bottom: number; height: number } => band !== null);
  }

  return (
    <figure className="grid gap-3">
      <figcaption className="sr-only">{t("finance.chart.title")}</figcaption>

      {sources.length > 0 ? (
        <ul className="flex flex-wrap items-center gap-2 text-meta text-muted">
          {sources.map((source) => {
            const on = !hidden.has(source);
            return (
              <li key={source}>
                <button
                  type="button"
                  aria-pressed={on}
                  aria-label={t("finance.chart.toggle", { source: t(`finance.source.${source}`) })}
                  onClick={() => toggle(source)}
                  className="inline-flex min-h-11 items-center gap-2 rounded-control border border-border px-3 text-text aria-pressed:bg-bg aria-pressed:text-muted"
                >
                  <span
                    aria-hidden="true"
                    className="inline-block h-3 w-3 rounded-[3px]"
                    style={{ background: SOURCE_COLOR[source], opacity: on ? 1 : 0.35 }}
                  />
                  <span className={cn(!on && "line-through")}>{t(`finance.source.${source}`)}</span>
                </button>
              </li>
            );
          })}
          <li className="inline-flex min-h-11 items-center gap-2 px-3">
            <span aria-hidden="true" className="relative inline-block h-0.5 w-5 bg-text">
              <span className="absolute left-1/2 top-1/2 h-2 w-2 -translate-x-1/2 -translate-y-1/2 rounded-full bg-text" />
            </span>
            {t("finance.chart.legendNet")}
          </li>
        </ul>
      ) : null}

      {/* The top tick sits half above the plot, so the plot keeps a little room under the legend for it. */}
      <div className="mt-2 grid grid-cols-[2.75rem_minmax(0,1fr)] gap-x-2">
        <div aria-hidden="true" className="relative h-56 text-end text-meta tabular-nums text-muted">
          {ticks.map((tick) => (
            <span key={tick} className="absolute end-0 -translate-y-1/2 leading-none" style={{ bottom: `${yPercent(tick, domain)}%` }}>
              {axisLabel(tick, locale)}
            </span>
          ))}
        </div>

        <div className="min-w-0 overflow-x-auto">
          <div style={{ minWidth: `${count * MIN_COLUMN_PX}px` }}>
            <div className="relative h-56">
              {ticks.map((tick) => (
                <div
                  key={tick}
                  aria-hidden="true"
                  className={cn("absolute inset-x-0 border-t border-border", tick === 0 && "border-border-control")}
                  style={{ bottom: `${yPercent(tick, domain)}%` }}
                />
              ))}
              <div className="absolute inset-0 flex">
                {buckets.map((bucket) => {
                  const income = bucketIncome(bucket, shown);
                  const expense = bucketExpense(bucket, shown);
                  const net = income - expense;
                  const period = periodLabel(bucket.key);
                  const title = t("finance.chart.bucket", { period, income: money(income), expense: money(expense), net: money(net) });
                  return (
                    <div
                      key={bucket.key}
                      role="group"
                      aria-label={title}
                      title={title}
                      tabIndex={0}
                      className="relative h-full min-w-0 flex-1 rounded-control outline-none focus-visible:ring-2 focus-visible:ring-accent"
                    >
                      {segments(bucket, incomeShown).map((band) => (
                        <span
                          key={`i-${band.source}`}
                          aria-hidden="true"
                          className="absolute block"
                          style={{
                            left: `calc(50% - ${BAR_PX + 1}px)`,
                            width: `${BAR_PX}px`,
                            bottom: `${band.bottom}%`,
                            height: `${band.height}%`,
                            background: SOURCE_COLOR[band.source],
                            boxShadow: band.index > 0 ? "0 -2px 0 0 var(--card)" : undefined,
                          }}
                        />
                      ))}
                      {segments(bucket, expenseShown).map((band) => (
                        <span
                          key={`e-${band.source}`}
                          aria-hidden="true"
                          className="absolute block"
                          style={{
                            left: "calc(50% + 1px)",
                            width: `${BAR_PX}px`,
                            bottom: `${band.bottom}%`,
                            height: `${band.height}%`,
                            background: SOURCE_COLOR[band.source],
                            boxShadow: band.index > 0 ? "0 -2px 0 0 var(--card)" : undefined,
                          }}
                        />
                      ))}
                    </div>
                  );
                })}
              </div>
              <svg
                aria-hidden="true"
                viewBox="0 0 100 100"
                preserveAspectRatio="none"
                className="pointer-events-none absolute inset-0 h-full w-full overflow-visible"
              >
                <polyline
                  points={netPoints}
                  fill="none"
                  stroke="var(--text)"
                  strokeWidth={2}
                  strokeLinejoin="round"
                  strokeLinecap="round"
                  vectorEffect="non-scaling-stroke"
                />
              </svg>
              {buckets.map((bucket, index) => {
                const net = bucketIncome(bucket, shown) - bucketExpense(bucket, shown);
                return (
                  <span
                    key={`dot-${bucket.key}`}
                    aria-hidden="true"
                    className="pointer-events-none absolute block h-2 w-2 rounded-full bg-text"
                    style={{
                      left: `${((index + 0.5) / count) * 100}%`,
                      bottom: `${yPercent(net, domain)}%`,
                      transform: "translate(-50%, 50%)",
                      boxShadow: "0 0 0 2px var(--card)",
                    }}
                  />
                );
              })}
            </div>
            <div className="flex">
              {buckets.map((bucket) => (
                <span key={bucket.key} aria-hidden="true" className="min-w-0 flex-1 truncate pt-1.5 text-center text-meta text-muted">
                  {periodLabel(bucket.key)}
                </span>
              ))}
            </div>
          </div>
        </div>
      </div>

      {weekCappedAt !== undefined ? (
        <p role="note" className="text-meta text-muted">
          {t("finance.chart.weekCapped", { count: formatNumber(weekCappedAt, locale) })}
        </p>
      ) : null}

      <details className="text-meta">
        <summary className="inline-flex min-h-11 cursor-pointer items-center text-label font-medium text-text-accent">
          {t("finance.chart.showTable")}
        </summary>
        <div className="mt-2 overflow-x-auto">
          <table className="w-full min-w-md border-collapse">
            <thead>
              <tr className="border-b border-border text-start text-muted">
                <th scope="col" className="py-2 pe-3 text-start font-medium">
                  {t("finance.chart.period")}
                </th>
                <th scope="col" className="py-2 pe-3 text-end font-medium">{t("finance.cards.income")}</th>
                <th scope="col" className="py-2 pe-3 text-end font-medium">{t("finance.cards.expense")}</th>
                <th scope="col" className="py-2 pe-3 text-end font-medium">{t("finance.chart.legendNet")}</th>
              </tr>
            </thead>
            <tbody>
              {buckets.map((bucket) => {
                const income = bucketIncome(bucket, shown);
                const expense = bucketExpense(bucket, shown);
                return (
                  <tr key={bucket.key} className="border-b border-border/60">
                    <th scope="row" className="py-2 pe-3 text-start font-normal text-text">
                      {periodLabel(bucket.key)}
                    </th>
                    <td className="py-2 pe-3 text-end tabular-nums">{money(income)}</td>
                    <td className="py-2 pe-3 text-end tabular-nums">{money(expense)}</td>
                    <td className="py-2 pe-3 text-end tabular-nums">{money(income - expense)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </details>
    </figure>
  );
}
