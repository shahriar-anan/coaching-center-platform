"use client";

import { useLocale } from "@/i18n/locale-context";
import { FinanceTheme } from "@/components/staff/finance/finance-theme";
import { money, monthLabel } from "./accounting-model";

/** One month of the chart: what went out and what came in (plan 4.6, the 12-month chart). */
export type MonthPoint = { month: string; expense: string; income: string };

function shortMonth(month: string, locale: "en" | "bn"): string {
  const [year, mon] = month.split("-").map(Number);
  return new Intl.DateTimeFormat(locale === "bn" ? "bn-BD" : "en-GB", { month: "short", timeZone: "UTC" }).format(
    new Date(Date.UTC(year, mon - 1, 1)),
  );
}

/**
 * Expense and income by month, as bars, with the same figures in a table under them (no reading rests on colour alone). The Finance chart
 * is keyed to Finance sources, so the accounting months have their own small chart in the same palette.
 */
export function MonthChart({ months }: { months: MonthPoint[] }) {
  const { t, locale } = useLocale();
  const values = months.flatMap((point) => [Number(point.expense), Number(point.income)]);
  const largest = Math.max(1, ...values);

  return (
    // The bars and legend use the finance palette, which is defined on .finance-viz and only comes with FinanceTheme.
    <figure className="finance-viz grid gap-3">
      <FinanceTheme />
      <div className="flex items-center gap-4 text-meta text-muted" aria-hidden="true">
        <span className="inline-flex items-center gap-1.5">
          <span className="size-3 rounded-sm" style={{ background: "var(--fin-src-1)" }} />
          {t("accounting.home.expense")}
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="size-3 rounded-sm" style={{ background: "var(--fin-src-2)" }} />
          {t("accounting.home.income")}
        </span>
      </div>
      <div className="flex h-48 items-end gap-2 overflow-x-auto rounded-card border border-border bg-card p-3" role="img" aria-label={t("accounting.home.chartLabel")}>
        {months.map((point) => (
          <div key={point.month} className="flex h-full min-w-8 flex-1 flex-col justify-end gap-1">
            <div className="flex h-full items-end gap-0.5">
              <div
                className="w-1/2 rounded-t-sm"
                style={{ height: `${(Number(point.expense) / largest) * 100}%`, background: "var(--fin-src-1)" }}
                title={`${point.month}: ${money(point.expense, locale)}`}
              />
              <div
                className="w-1/2 rounded-t-sm"
                style={{ height: `${(Number(point.income) / largest) * 100}%`, background: "var(--fin-src-2)" }}
                title={`${point.month}: ${money(point.income, locale)}`}
              />
            </div>
            <span className="text-center text-pill text-muted">{shortMonth(point.month, locale)}</span>
          </div>
        ))}
      </div>
      <details className="text-meta text-muted">
        <summary className="cursor-pointer py-1 text-label text-accent-text">{t("accounting.home.figures")}</summary>
        <table className="mt-2 w-full text-start">
          <thead>
            <tr>
              <th className="py-1 text-start font-medium">{t("accounting.home.month")}</th>
              <th className="py-1 text-end font-medium">{t("accounting.home.expense")}</th>
              <th className="py-1 text-end font-medium">{t("accounting.home.income")}</th>
            </tr>
          </thead>
          <tbody>
            {months.map((point) => (
              <tr key={point.month} className="border-t border-border">
                <td className="py-1">{monthLabel(point.month, locale)}</td>
                <td className="py-1 text-end">{money(point.expense, locale)}</td>
                <td className="py-1 text-end">{money(point.income, locale)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </figure>
  );
}
