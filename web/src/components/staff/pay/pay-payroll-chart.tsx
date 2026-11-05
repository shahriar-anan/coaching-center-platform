"use client";

import { cn } from "@/components/ui/cn";
import { useLocale } from "@/i18n/locale-context";
import type { PayPayrollMonth } from "@/lib/api/exam/staff-pay-analytics";
import { formatMoney } from "@/lib/format";
import { axisLabel, chartScale, monthLabel, percentOf } from "@/components/staff/pay/pay-analytics-format";

// The payroll chart (staff pay plan, phase 6). One stacked bar per month: salary at the bottom, marking on top, with a line for
// the amount paid that month. Plain HTML and SVG, no chart library. The categorical colours are the first three slots of the
// data-viz palette (blue, orange, aqua), with their dark-mode steps. The same figures sit in a table below the chart, and every
// month has a title with its values, so nothing rests on colour alone: the legend names each mark.

const CHART_CSS = `
.pay-viz {
  --pay-salary: #2a78d6;
  --pay-marking: #eb6834;
  --pay-paid: #1baf7a;
}
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) .pay-viz {
    --pay-salary: #3987e5;
    --pay-marking: #d95926;
    --pay-paid: #199e70;
  }
}
:root[data-theme="dark"] .pay-viz {
  --pay-salary: #3987e5;
  --pay-marking: #d95926;
  --pay-paid: #199e70;
}
`;

/** The width of one month's column at least; a long range scrolls sideways inside its own box, never the page. */
const MIN_COLUMN_PX = 40;

/** The bar is at most 24 px thick. */
const BAR_MAX_PX = 24;

export function PayrollChart({ rows }: { rows: PayPayrollMonth[] }) {
  const { t, locale } = useLocale();
  const money = (value: number) => formatMoney(value, locale) ?? "";
  const scale = chartScale(rows.flatMap((row) => [row.salary + row.marking, row.paid]));
  const count = Math.max(rows.length, 1);
  const paidPoints = rows.map((row, index) => ({
    x: ((index + 0.5) / count) * 100,
    y: percentOf(row.paid, scale),
  }));
  const linePoints = paidPoints.map((point) => `${point.x},${100 - point.y}`).join(" ");

  return (
    <figure className="pay-viz grid gap-3">
      <style>{CHART_CSS}</style>
      <figcaption className="sr-only">{t("staffPayOverview.analytics.chartLabel")}</figcaption>

      <ul className="flex flex-wrap gap-x-5 gap-y-2 text-meta text-muted">
        <li className="flex items-center gap-2">
          <span aria-hidden="true" className="inline-block h-3 w-3 rounded-[3px]" style={{ background: "var(--pay-salary)" }} />
          {t("staffPayOverview.analytics.legendSalary")}
        </li>
        <li className="flex items-center gap-2">
          <span aria-hidden="true" className="inline-block h-3 w-3 rounded-[3px]" style={{ background: "var(--pay-marking)" }} />
          {t("staffPayOverview.analytics.legendMarking")}
        </li>
        <li className="flex items-center gap-2">
          <span aria-hidden="true" className="relative inline-block h-0.5 w-5" style={{ background: "var(--pay-paid)" }}>
            <span
              className="absolute left-1/2 top-1/2 h-2 w-2 -translate-x-1/2 -translate-y-1/2 rounded-full"
              style={{ background: "var(--pay-paid)" }}
            />
          </span>
          {t("staffPayOverview.analytics.legendPaid")}
        </li>
      </ul>

      {/* The top tick sits half above the plot, so the plot keeps a little room under the legend for it. */}
      <div className="mt-3 grid grid-cols-[2.75rem_minmax(0,1fr)] gap-x-2">
        <div aria-hidden="true" className="relative h-56 text-end text-meta tabular-nums text-muted">
          {[0, 50, 100].map((position) => (
            <span
              key={position}
              className="absolute end-0 -translate-y-1/2 leading-none"
              style={{ bottom: `${position}%` }}
            >
              {axisLabel((scale * position) / 100, locale)}
            </span>
          ))}
        </div>

        <div className="min-w-0 overflow-x-auto">
          <div style={{ minWidth: `${count * MIN_COLUMN_PX}px` }}>
            <div className="relative h-56">
              <div aria-hidden="true" className="absolute inset-x-0 top-0 border-t border-border" />
              <div aria-hidden="true" className="absolute inset-x-0 top-1/2 border-t border-border" />
              <div className="absolute inset-0 flex">
                {rows.map((row) => {
                  const title = t("staffPayOverview.analytics.monthTitle", {
                    month: monthLabel(row.month, locale),
                    salary: money(row.salary),
                    marking: money(row.marking),
                    paid: money(row.paid),
                  });
                  const segments = [
                    { key: "marking", value: row.marking, color: "var(--pay-marking)" },
                    { key: "salary", value: row.salary, color: "var(--pay-salary)" },
                  ].filter((segment) => segment.value > 0);
                  return (
                    <div
                      key={row.month}
                      role="group"
                      aria-label={title}
                      title={title}
                      tabIndex={0}
                      className="flex min-w-0 flex-1 flex-col items-center justify-end rounded-control outline-none focus-visible:ring-2 focus-visible:ring-accent"
                    >
                      <div
                        className="flex w-full flex-col gap-0.5"
                        style={{ height: `${percentOf(row.salary + row.marking, scale)}%`, maxWidth: `${BAR_MAX_PX}px` }}
                      >
                        {segments.map((segment, index) => (
                          <span
                            key={segment.key}
                            aria-hidden="true"
                            className={cn("block min-h-0 w-full", index === 0 && "rounded-t-[4px]")}
                            style={{ flexGrow: segment.value, flexBasis: 0, background: segment.color }}
                          />
                        ))}
                      </div>
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
                  points={linePoints}
                  fill="none"
                  stroke="var(--pay-paid)"
                  strokeWidth={2}
                  strokeLinejoin="round"
                  strokeLinecap="round"
                  vectorEffect="non-scaling-stroke"
                />
              </svg>
              {paidPoints.map((point, index) => (
                <span
                  key={rows[index].month}
                  aria-hidden="true"
                  className="pointer-events-none absolute block h-2 w-2 rounded-full"
                  style={{
                    left: `${point.x}%`,
                    bottom: `${point.y}%`,
                    transform: "translate(-50%, 50%)",
                    background: "var(--pay-paid)",
                    boxShadow: "0 0 0 2px var(--card)",
                  }}
                />
              ))}
            </div>
            <div className="flex">
              {rows.map((row) => (
                <span key={row.month} aria-hidden="true" className="min-w-0 flex-1 pt-1.5 text-center text-meta text-muted">
                  {monthLabel(row.month, locale)}
                </span>
              ))}
            </div>
          </div>
        </div>
      </div>
    </figure>
  );
}

/** The same figures as the chart, for anyone who wants the numbers rather than the bars. */
export function PayrollTable({ rows }: { rows: PayPayrollMonth[] }) {
  const { t, locale } = useLocale();
  const money = (value: number) => formatMoney(value, locale) ?? "";
  const headers = [
    t("staffPayOverview.analytics.salary"),
    t("staffPayOverview.analytics.marking"),
    t("staffPayOverview.analytics.adjustments"),
    t("staffPayOverview.analytics.earned"),
    t("staffPayOverview.analytics.paid"),
  ];
  return (
    <details className="text-meta">
      <summary className="inline-flex min-h-11 cursor-pointer items-center text-label font-medium text-text-accent">
        {t("staffPayOverview.analytics.showTable")}
      </summary>
      <div className="mt-2 overflow-x-auto">
        <table className="w-full min-w-[32rem] border-collapse">
          <thead>
            <tr className="border-b border-border text-start text-muted">
              <th scope="col" className="py-2 pe-3 text-start font-medium">
                {t("staffPayOverview.analytics.month")}
              </th>
              {headers.map((header) => (
                <th key={header} scope="col" className="py-2 pe-3 text-end font-medium">
                  {header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.month} className="border-b border-border/60">
                <th scope="row" className="py-2 pe-3 text-start font-normal text-text">
                  {monthLabel(row.month, locale)}
                </th>
                <td className="py-2 pe-3 text-end tabular-nums">{money(row.salary)}</td>
                <td className="py-2 pe-3 text-end tabular-nums">{money(row.marking)}</td>
                <td className="py-2 pe-3 text-end tabular-nums">{money(row.adjustments)}</td>
                <td className="py-2 pe-3 text-end tabular-nums">{money(row.earned)}</td>
                <td className="py-2 pe-3 text-end tabular-nums">{money(row.paid)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  );
}
