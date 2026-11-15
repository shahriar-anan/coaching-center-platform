"use client";

import { useCallback, useMemo } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useLocale } from "@/i18n/locale-context";
import { PageHeader } from "@/components/ui/page-header";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { Tabs, type TabItem } from "@/components/ui/tabs";
import { ListOrdered } from "lucide-react";
import { formatDate } from "@/lib/format";
import { monthLabel } from "@/components/staff/pay/pay-analytics-format";
import {
  availabilityFilters,
  EXPENSE_SOURCES,
  INCOME_SOURCES,
  OFFLINE_SOURCES,
  useFinanceBatchChoices,
  useFinanceEntryRows,
  useFinanceMonthly,
  useFinanceSummary,
  type FinanceFilters,
  type FinanceSource,
} from "@/lib/api/exam/finance";
import {
  entriesHref,
  financeHref,
  monthBuckets,
  parseFinanceUrl,
  rangeProblem,
  selectedSources,
  weekBuckets,
  type FinanceTab,
  type FinanceUrlState,
} from "@/components/staff/finance/finance-model";
import { FinanceTheme } from "@/components/staff/finance/finance-theme";
import { FinanceFilterBar } from "@/components/staff/finance/finance-filter-bar";
import { FinanceCards } from "@/components/staff/finance/finance-cards";
import { FinanceChart } from "@/components/staff/finance/finance-chart";
import { BySourceTab, ByBatchTab, ByInstructorTab } from "@/components/staff/finance/finance-tabs";

// The Master Admin's finance page (plan 8.4). The URL holds the whole filter; this component reads it, asks the API, and writes it
// back when a control changes. The API refuses anyone but the Master Admin; the route checks the role before this renders.

export function FinancePage() {
  const { t, locale } = useLocale();
  const router = useRouter();
  const searchParams = useSearchParams();
  const state = useMemo(() => parseFinanceUrl(new URLSearchParams(searchParams?.toString() ?? "")), [searchParams]);

  const update = useCallback(
    (patch: Partial<FinanceUrlState>) => {
      // Any change starts again at the first page of entries; a page change sets its own page.
      router.replace(financeHref({ ...state, page: 0, ...patch }), { scroll: false });
    },
    [router, state],
  );

  const problem = rangeProblem(state.from, state.to);
  const valid = problem === null;
  const filters: FinanceFilters = { from: state.from, to: state.to, sources: state.sources, batchId: state.batchId };

  const summary = useFinanceSummary(filters, valid);
  // The sources the API offers for this range and batch. An absent source (the offline switch off, or an online source under a
  // batch) is not shown, so this list is what the filter bar draws from.
  const availability = useFinanceSummary(availabilityFilters(filters), valid);
  const available: FinanceSource[] = (availability.data?.sources ?? []).map((row) => row.source);
  const offlineAvailable = available.some((source) => OFFLINE_SOURCES.includes(source));
  const instructorsAvailable = available.includes("OFFLINE_INSTRUCTORS");
  const selected = selectedSources(state.sources, available);
  const batches = useFinanceBatchChoices(offlineAvailable);

  const monthly = useFinanceMonthly(filters, valid && state.group === "month");
  const weekRows = useFinanceEntryRows(filters, valid && state.group === "week");

  const tabs: TabItem[] = [
    { value: "source", label: t("finance.tabs.source") },
    ...(offlineAvailable ? [{ value: "batch", label: t("finance.tabs.batch") }] : []),
    ...(instructorsAvailable ? [{ value: "instructor", label: t("finance.tabs.instructor") }] : []),
  ];
  const tab: FinanceTab = tabs.some((item) => item.value === state.tab) ? state.tab : "source";

  const incomeSelected = selected.filter((source) => INCOME_SOURCES.includes(source));
  const expenseSelected = selected.filter((source) => EXPENSE_SOURCES.includes(source));
  const hrefs = {
    income: incomeSelected.length > 0 ? entriesHref(state, incomeSelected) : null,
    expense: expenseSelected.length > 0 ? entriesHref(state, expenseSelected) : null,
    net: selected.length > 0 ? entriesHref(state, selected) : null,
  };

  const totals = summary.data;
  const noMoney = totals !== undefined && totals.income === 0 && totals.expense === 0;

  return (
    <div className="finance-viz grid gap-6">
      <FinanceTheme />
      <PageHeader
        title={t("finance.title")}
        actions={
          <Button variant="secondary" href={entriesHref(state, selected.length > 0 ? selected : available)}>
            <ListOrdered aria-hidden="true" className="size-4" />
            {t("finance.ledger.openAll")}
          </Button>
        }
      />
      <p className="-mt-3 text-body text-muted">{t("finance.intro")}</p>

      <FinanceFilterBar
        state={state}
        selected={selected}
        available={available}
        batches={batches.data ?? []}
        offlineAvailable={offlineAvailable}
        onChange={update}
      />

      {problem ? null : summary.isPending ? (
        <Skeleton variant="card" count={2} />
      ) : summary.isError ? (
        <ErrorState onRetry={() => void summary.refetch()} message={t("finance.errors.load")} error={summary.error} />
      ) : totals ? (
        <>
          <FinanceCards summary={totals} hrefs={hrefs} />

          {noMoney ? (
            <EmptyState title={t("finance.empty.title")} />
          ) : (
            <>
              <section className="grid gap-3 rounded-card border border-border bg-card p-4">
                <h2 className="text-section text-text">{t("finance.chart.title")}</h2>
                <p className="text-meta text-muted">{t("finance.chart.body")}</p>
                {state.group === "month" ? (
                  monthly.isPending ? (
                    <Skeleton variant="card" count={1} />
                  ) : monthly.isError ? (
                    <ErrorState onRetry={() => void monthly.refetch()} message={t("finance.errors.load")} error={monthly.error} />
                  ) : (
                    <FinanceChart
                      buckets={monthBuckets(state.from, state.to, monthly.data ?? [])}
                      sources={selected}
                      periodLabel={(key) => monthLabel(key, locale)}
                    />
                  )
                ) : weekRows.isPending ? (
                  <Skeleton variant="card" count={1} />
                ) : weekRows.isError ? (
                  <ErrorState onRetry={() => void weekRows.refetch()} message={t("finance.errors.load")} error={weekRows.error} />
                ) : (
                  <FinanceChart
                    buckets={weekBuckets(state.from, state.to, weekRows.data?.items ?? [])}
                    sources={selected}
                    periodLabel={(key) => formatDate(key, locale)}
                    weekCappedAt={weekRows.data?.truncated ? weekRows.data.items.length : undefined}
                  />
                )}
              </section>

              <section className="grid gap-4">
                <Tabs
                  tabs={tabs}
                  value={tab}
                  onChange={(value) => update({ tab: value as FinanceTab })}
                  ariaLabel={t("finance.tabs.label")}
                  wrap
                />
                {tab === "source" ? <BySourceTab summary={totals} state={state} /> : null}
                {tab === "batch" ? <ByBatchTab filters={filters} state={state} /> : null}
                {tab === "instructor" ? <ByInstructorTab filters={filters} state={state} /> : null}
              </section>
            </>
          )}
        </>
      ) : null}
    </div>
  );
}
