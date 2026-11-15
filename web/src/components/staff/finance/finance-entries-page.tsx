"use client";

import { useCallback, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { ArrowDownLeft, ArrowUpRight, Download, Scale, Rows3 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/components/ui/cn";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { SearchInput } from "@/components/ui/search-input";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { TextInput } from "@/components/ui/text-input";
import { useLocale } from "@/i18n/locale-context";
import { formatDate, formatMoney, formatNumber } from "@/lib/format";
import {
  EXPENSE_SOURCES,
  INCOME_SOURCES,
  OFFLINE_SOURCES,
  availabilityFilters,
  downloadFinanceEntriesCsv,
  useFinanceBatchChoices,
  useFinanceLedger,
  useFinanceSummary,
  type FinanceEntry,
  type FinanceFilters,
  type FinanceSource,
  type LedgerSort,
} from "@/lib/api/exam/finance";
import {
  LEDGER_SORTS,
  RANGE_PRESETS,
  financeHref,
  ledgerDirection,
  ledgerHref,
  parseFinanceUrl,
  parseLedgerUrl,
  presetRange,
  rangeProblem,
  selectedSources,
  toggleSource,
  type LedgerUrlState,
  type RangePreset,
} from "@/components/staff/finance/finance-model";
import { FinanceTheme, SOURCE_COLOR } from "@/components/staff/finance/finance-theme";

/** The streams in their groups, the order the page lists them in. */
const STREAM_GROUPS: { key: "online" | "offline" | "staff"; sources: FinanceSource[] }[] = [
  { key: "online", sources: ["ONLINE_ORDERS", "DIRECT_ENROLMENT", "OTHER_ONLINE"] },
  { key: "offline", sources: ["OFFLINE_FEES", "OFFLINE_INSTRUCTORS"] },
  { key: "staff", sources: ["STAFF_PAY"] },
];

/** One figure of the totals strip: an icon, the label, and the amount. */
function Total({ icon: Icon, label, value, tone }: { icon: typeof Scale; label: string; value: string; tone: "ok" | "danger" | "neutral" }) {
  const edge = tone === "ok" ? "border-t-ok" : tone === "danger" ? "border-t-danger" : "border-t-accent";
  const tint = tone === "ok" ? "bg-ok/10 text-ok-text" : tone === "danger" ? "bg-danger/10 text-danger" : "bg-accent/15 text-text-accent";
  return (
    <div className={cn("grid min-w-0 content-start gap-2 rounded-card border border-border border-t-4 bg-card p-4 shadow-[var(--card-shadow)]", edge)}>
      <div className="flex items-center justify-between gap-2">
        <span className="text-meta font-medium text-muted">{label}</span>
        <span aria-hidden="true" className={cn("flex size-8 items-center justify-center rounded-control", tint)}>
          <Icon className="size-4" strokeWidth={1.9} />
        </span>
      </div>
      <span className="break-words text-section font-bold tabular-nums text-text">{value}</span>
    </div>
  );
}

/** The amount of an entry with its sign: money in is green and "+", money out is red and "−". */
function Amount({ entry }: { entry: FinanceEntry }) {
  const { locale } = useLocale();
  const income = entry.direction === "INCOME";
  return (
    <span className="whitespace-nowrap font-semibold tabular-nums text-text">
      {income ? "+" : "−"}
      {formatMoney(Math.abs(entry.amount), locale)}
    </span>
  );
}

function Stream({ source }: { source: FinanceSource }) {
  const { t } = useLocale();
  return (
    <span className="inline-flex min-w-0 items-center gap-2">
      <span aria-hidden="true" className="inline-block size-2.5 shrink-0 rounded-full" style={{ background: SOURCE_COLOR[source] }} />
      <span className="truncate">{t(`finance.source.${source}`)}</span>
    </span>
  );
}

/**
 * The entries page (the ledger): every amount behind the finance figures, one row each. The filter lives in the URL: the dates, money
 * in or out, the streams (as chips with their totals), the batch, a search over the details and the reference, and the order. The
 * totals strip sums exactly what is filtered, and Export CSV downloads exactly those rows. Master Admin only (the route checks it).
 */
export function FinanceEntriesPage() {
  const { t, locale } = useLocale();
  const router = useRouter();
  const searchParams = useSearchParams();
  const params = useMemo(() => new URLSearchParams(searchParams?.toString() ?? ""), [searchParams]);
  const state = useMemo(() => parseLedgerUrl(params), [params]);
  const [exporting, setExporting] = useState(false);
  const [exportFailed, setExportFailed] = useState(false);

  const update = useCallback(
    (patch: Partial<LedgerUrlState>) => router.replace(ledgerHref({ ...state, page: 0, ...patch }), { scroll: false }),
    [router, state],
  );

  const problem = rangeProblem(state.from, state.to);
  const valid = problem === null;
  const filters: FinanceFilters = { from: state.from, to: state.to, sources: state.sources, batchId: state.batchId };
  const options = { q: state.q, sort: state.sort };

  // The streams the API offers for these dates and batch: the ones the chips and the direction control draw from.
  const availability = useFinanceSummary(availabilityFilters(filters), valid);
  const available: FinanceSource[] = (availability.data?.sources ?? []).map((row) => row.source);
  const selected = selectedSources(state.sources, available);
  const offlineAvailable = available.some((source) => OFFLINE_SOURCES.includes(source));
  const batches = useFinanceBatchChoices(offlineAvailable);
  const ledger = useFinanceLedger(filters, options, state.page, valid);

  const direction = ledgerDirection(selected.length === available.length ? [] : selected);
  function setDirection(next: string) {
    const wanted = next === "income" ? INCOME_SOURCES : next === "expense" ? EXPENSE_SOURCES : null;
    const sources = wanted === null ? null : available.filter((source) => wanted.includes(source));
    update({ sources: sources && sources.length > 0 ? sources : null });
  }

  function setRange(preset: RangePreset) {
    if (preset === "custom") {
      update({ range: "custom" });
      return;
    }
    update({ range: preset, ...presetRange(preset) });
  }

  async function exportCsv() {
    setExporting(true);
    setExportFailed(false);
    try {
      await downloadFinanceEntriesCsv(filters, options);
    } catch {
      setExportFailed(true);
    } finally {
      setExporting(false);
    }
  }

  const page = ledger.data;
  const streamTotals = new Map((page?.bySource ?? []).map((row) => [row.source, row]));
  const filtered = state.sources !== null || state.batchId !== null || state.q.trim() !== "" || state.sort !== "date";
  const backToFinance = financeHref({
    ...parseFinanceUrl(params),
    range: state.range,
    from: state.from,
    to: state.to,
    sources: state.sources,
    batchId: state.batchId,
    tab: "source",
    page: 0,
  });

  return (
    <div className="finance-viz grid gap-5">
      <FinanceTheme />
      <PageHeader
        title={t("finance.ledger.title")}
        breadcrumbs={[
          { label: t("common.staffNav.dashboard"), href: "/staff" },
          { label: t("finance.title"), href: backToFinance },
          { label: t("finance.ledger.title") },
        ]}
        actions={
          <Button variant="secondary" onClick={() => void exportCsv()} disabled={exporting || !valid}>
            <Download aria-hidden="true" className="size-4" />
            {exporting ? t("finance.entries.exporting") : t("finance.entries.exportCsv")}
          </Button>
        }
      />
      <p className="-mt-3 text-meta text-muted">{t("finance.ledger.intro")}</p>
      {exportFailed ? (
        <p role="alert" className="text-meta text-danger">
          {t("finance.errors.export")}
        </p>
      ) : null}

      {/* The filters */}
      <section aria-label={t("finance.filters.label")} className="grid gap-4 rounded-card border border-border bg-card p-4 shadow-[var(--card-shadow)]">
        <div className="flex flex-wrap items-end gap-3">
          <div className="grid gap-1.5">
            <span className="text-meta font-medium text-muted">{t("finance.filters.range")}</span>
            <SegmentedControl
              options={RANGE_PRESETS.map((preset) => ({ value: preset, label: t(`finance.filters.presets.${preset}`) }))}
              value={state.range}
              onChange={(value) => setRange(value as RangePreset)}
              ariaLabel={t("finance.filters.range")}
            />
          </div>
          <label className="grid gap-1.5 text-meta font-medium text-muted">
            {t("finance.filters.from")}
            <TextInput type="date" className="w-full min-w-0" value={state.from} onChange={(event) => update({ range: "custom", from: event.target.value })} />
          </label>
          <label className="grid gap-1.5 text-meta font-medium text-muted">
            {t("finance.filters.to")}
            <TextInput type="date" className="w-full min-w-0" value={state.to} onChange={(event) => update({ range: "custom", to: event.target.value })} />
          </label>
        </div>
        {problem ? (
          <p role="alert" className="text-meta text-danger">
            {t(`finance.filters.problem.${problem}`)}
          </p>
        ) : null}

        <div className="grid gap-3 sm:grid-cols-[auto_minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)] sm:items-end">
          <div className="grid gap-1.5">
            <span className="text-meta font-medium text-muted">{t("finance.ledger.direction.label")}</span>
            <SegmentedControl
              options={(["all", "income", "expense"] as const).map((value) => ({ value, label: t(`finance.ledger.direction.${value}`) }))}
              value={direction}
              onChange={setDirection}
              ariaLabel={t("finance.ledger.direction.label")}
            />
          </div>
          <label className="grid min-w-0 gap-1.5 text-meta font-medium text-muted">
            {t("finance.ledger.search")}
            <SearchInput value={state.q} onChange={(q) => update({ q })} placeholder={t("finance.ledger.searchPlaceholder")} className="w-full" />
          </label>
          {offlineAvailable ? (
            <label className="grid min-w-0 gap-1.5 text-meta font-medium text-muted">
              {t("finance.ledger.batch")}
              <Select value={state.batchId ?? ""} onChange={(event) => update({ batchId: event.target.value || null })}>
                <option value="">{t("finance.ledger.allBatches")}</option>
                {(batches.data ?? []).map((batch) => (
                  <option key={batch.id} value={batch.id}>
                    {locale === "bn" ? batch.nameBn || batch.nameEn : batch.nameEn || batch.nameBn}
                  </option>
                ))}
              </Select>
            </label>
          ) : (
            <span />
          )}
          <label className="grid min-w-0 gap-1.5 text-meta font-medium text-muted">
            {t("finance.ledger.sort.label")}
            <Select value={state.sort} onChange={(event) => update({ sort: event.target.value as LedgerSort })}>
              {LEDGER_SORTS.map((sort) => (
                <option key={sort} value={sort}>
                  {t(`finance.ledger.sort.${sort}`)}
                </option>
              ))}
            </Select>
          </label>
        </div>

        {/* The streams, in their groups: each chip shows its total and turns the stream on or off. */}
        <div className="grid gap-2 border-t border-border pt-3">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <span className="text-label font-bold text-text">{t("finance.ledger.streams")}</span>
            <span className="text-meta text-muted">{t("finance.ledger.streamsHint")}</span>
          </div>
          <div className="grid gap-3 md:grid-cols-3">
            {STREAM_GROUPS.map((group) => {
              const sources = group.sources.filter((source) => available.includes(source));
              if (sources.length === 0) {
                return null;
              }
              return (
                <div key={group.key} className="grid content-start gap-1.5">
                  <span className="text-meta font-semibold uppercase tracking-wide text-muted">{t(`finance.ledger.groups.${group.key}`)}</span>
                  {sources.map((source) => {
                    const on = selected.includes(source);
                    const total = streamTotals.get(source);
                    return (
                      <button
                        key={source}
                        type="button"
                        aria-pressed={on}
                        onClick={() => update({ sources: toggleSource(selected, available, source) })}
                        className={cn(
                          "flex min-h-11 items-center justify-between gap-3 rounded-control border px-3 py-2 text-start transition-colors",
                          on ? "border-accent bg-accent/10" : "border-border bg-bg opacity-70 hover:opacity-100",
                        )}
                      >
                        <span className="grid min-w-0 gap-0.5">
                          <span className="text-body text-text">
                            <Stream source={source} />
                          </span>
                          <span className="text-meta tabular-nums text-muted">
                            {t("finance.ledger.streamCount", { count: formatNumber(total?.count ?? 0, locale) })}
                          </span>
                        </span>
                        <span
                          className={cn(
                            "shrink-0 font-semibold tabular-nums",
                            INCOME_SOURCES.includes(source) ? "text-ok-text" : "text-danger",
                          )}
                        >
                          {formatMoney(total?.total ?? 0, locale)}
                        </span>
                      </button>
                    );
                  })}
                </div>
              );
            })}
          </div>
        </div>

        {filtered ? (
          <div>
            <Button size="sm" variant="ghost" className="px-0 text-text-accent" onClick={() => update({ sources: null, batchId: null, q: "", sort: "date" })}>
              {t("finance.ledger.clear")}
            </Button>
          </div>
        ) : null}
      </section>

      {/* The totals of exactly what is filtered */}
      {page ? (
        <div className="grid gap-3 sm:grid-cols-4">
          <Total icon={ArrowDownLeft} label={t("finance.ledger.totals.income")} value={formatMoney(page.income, locale) ?? "—"} tone="ok" />
          <Total icon={ArrowUpRight} label={t("finance.ledger.totals.expense")} value={formatMoney(page.expense, locale) ?? "—"} tone="danger" />
          <Total
            icon={Scale}
            label={t("finance.ledger.totals.net")}
            value={formatMoney(page.income - page.expense, locale) ?? "—"}
            tone={page.income - page.expense < 0 ? "danger" : "ok"}
          />
          <Total icon={Rows3} label={t("finance.ledger.totals.count")} value={formatNumber(page.total, locale)} tone="neutral" />
        </div>
      ) : null}

      {/* The rows */}
      {!valid ? null : ledger.isPending ? (
        <Skeleton variant="rows" count={6} />
      ) : ledger.isError || !page ? (
        <ErrorState onRetry={() => void ledger.refetch()} message={t("finance.errors.load")} error={ledger.error} />
      ) : page.total === 0 ? (
        <EmptyState title={t("finance.ledger.empty")} body={t("finance.ledger.emptyBody")} />
      ) : (
        <section aria-label={t("finance.ledger.title")} className="grid gap-3">
          {/* Wide screens: a table. */}
          <div className="hidden overflow-hidden rounded-card border border-border bg-card shadow-[var(--card-shadow)] md:block">
            <table className="w-full border-collapse text-body">
              <thead>
                <tr className="border-b border-border bg-bg text-start text-meta text-muted">
                  <th scope="col" className="px-4 py-2.5 text-start font-semibold">{t("finance.ledger.columns.date")}</th>
                  <th scope="col" className="px-4 py-2.5 text-start font-semibold">{t("finance.ledger.columns.stream")}</th>
                  <th scope="col" className="px-4 py-2.5 text-start font-semibold">{t("finance.ledger.columns.description")}</th>
                  <th scope="col" className="px-4 py-2.5 text-start font-semibold">{t("finance.ledger.columns.reference")}</th>
                  <th scope="col" className="px-4 py-2.5 text-end font-semibold">{t("finance.ledger.columns.amount")}</th>
                </tr>
              </thead>
              <tbody>
                {page.items.map((entry, index) => (
                  <tr key={`${entry.source}-${entry.id ?? index}`} className="border-b border-border align-top last:border-0 hover:bg-text/[0.03]">
                    <td className="whitespace-nowrap px-4 py-3 tabular-nums text-muted">{formatDate(entry.date, locale, { year: "auto" })}</td>
                    <td className="px-4 py-3 text-text">
                      <Stream source={entry.source} />
                    </td>
                    <td className="max-w-md break-words px-4 py-3 text-text">{entry.description}</td>
                    <td className="break-all px-4 py-3 text-meta text-muted">{entry.reference ?? "—"}</td>
                    <td className="px-4 py-3 text-end">
                      <Amount entry={entry} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Phones: one card per entry. */}
          <ul className="grid gap-2 md:hidden">
            {page.items.map((entry, index) => (
              <li key={`${entry.source}-${entry.id ?? index}`} className="grid gap-1.5 rounded-card border border-border bg-card p-3 shadow-[var(--card-shadow)]">
                <div className="flex items-start justify-between gap-3">
                  <span className="text-meta text-muted">
                    <Stream source={entry.source} />
                  </span>
                  <Amount entry={entry} />
                </div>
                <p className="break-words text-body text-text">{entry.description}</p>
                <div className="flex flex-wrap items-center justify-between gap-2 text-meta text-muted">
                  <span className="tabular-nums">{formatDate(entry.date, locale, { year: "auto" })}</span>
                  {entry.reference ? <span className="break-all">{entry.reference}</span> : null}
                </div>
              </li>
            ))}
          </ul>

          <Pager page={page.page} size={page.size} total={page.total} onPage={(next) => router.replace(ledgerHref({ ...state, page: next }), { scroll: true })} />
        </section>
      )}
    </div>
  );
}

function Pager({ page, size, total, onPage }: { page: number; size: number; total: number; onPage: (page: number) => void }) {
  const { t, locale } = useLocale();
  const pages = Math.max(1, Math.ceil(total / size));
  const first = total === 0 ? 0 : page * size + 1;
  const last = Math.min(total, (page + 1) * size);
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <p className="text-meta tabular-nums text-muted">
        {t("finance.entries.showing", {
          from: formatNumber(first, locale),
          to: formatNumber(last, locale),
          total: formatNumber(total, locale),
        })}
      </p>
      <div className="flex gap-2">
        <Button size="sm" variant="secondary" disabled={page <= 0} onClick={() => onPage(page - 1)}>
          {t("finance.ledger.previous")}
        </Button>
        <Button size="sm" variant="secondary" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}>
          {t("finance.ledger.next")}
        </Button>
      </div>
    </div>
  );
}
