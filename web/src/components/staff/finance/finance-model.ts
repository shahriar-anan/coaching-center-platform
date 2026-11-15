import {
  EXPENSE_SOURCES,
  FINANCE_SOURCES,
  INCOME_SOURCES,
  type FinanceEntry,
  type FinanceMonth,
  type FinanceSource,
  type LedgerSort,
} from "@/lib/api/exam/finance";
import { chartScale } from "@/components/staff/pay/pay-analytics-format";
import { todayInDhaka } from "@/components/staff/pay/pay-format";

// Pure helpers for the finance page (plan 8.4). No React here, so the URL rules, the date presets, the week buckets and the chart
// arithmetic can be tested directly. The API does the real money; these only keep the screen consistent with it.

export type RangePreset = "month" | "last" | "year" | "all" | "custom";
export type Grouping = "month" | "week";
export type FinanceTab = "source" | "batch" | "instructor" | "entries";

export const RANGE_PRESETS: readonly RangePreset[] = ["month", "last", "year", "all", "custom"];
export const GROUPINGS: readonly Grouping[] = ["month", "week"];
export const FINANCE_TABS: readonly FinanceTab[] = ["source", "batch", "instructor", "entries"];

/** The whole filter state. It lives in the URL; this is what the URL says, with defaults filled in. */
export interface FinanceUrlState {
  range: RangePreset;
  from: string;
  to: string;
  /** Null means every source the API offers. Otherwise the sources the filter names, in the page's order. */
  sources: FinanceSource[] | null;
  batchId: string | null;
  group: Grouping;
  tab: FinanceTab;
  page: number;
}

const DATE = /^\d{4}-\d{2}-\d{2}$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** The longest range the API answers (ten years), mirrored here so the page can say so before asking. */
export const MAX_RANGE_DAYS = 3653;

/** Where "All time" starts: the first year of records. The end is always today. */
export const ALL_TIME_START = "2020-01-01";

function isDate(value: string | null): value is string {
  return value !== null && DATE.test(value);
}

/** Year, month (1-12) and day of a YYYY-MM-DD string. */
function parts(date: string): { year: number; month: number; day: number } {
  const [year, month, day] = date.split("-").map(Number);
  return { year, month, day };
}

/** The date `days` after a YYYY-MM-DD string (negative goes back). Pure calendar arithmetic, so no time zone can shift it. */
export function addDays(date: string, days: number): string {
  const { year, month, day } = parts(date);
  const shifted = new Date(Date.UTC(year, month - 1, day + days));
  return shifted.toISOString().slice(0, 10);
}

/** Whole days from `from` to `to`, counting both ends (one day for the same date). */
export function spanDays(from: string, to: string): number {
  const start = Date.UTC(...toUtcArgs(from));
  const end = Date.UTC(...toUtcArgs(to));
  return Math.round((end - start) / 86_400_000) + 1;
}

function toUtcArgs(date: string): [number, number, number] {
  const { year, month, day } = parts(date);
  return [year, month - 1, day];
}

/** The Monday of the week that holds the date (weeks run Monday to Sunday). */
export function weekStart(date: string): string {
  const { year, month, day } = parts(date);
  const dayOfWeek = (new Date(Date.UTC(year, month - 1, day)).getUTCDay() + 6) % 7;
  return addDays(date, -dayOfWeek);
}

/** The first and last day of the month before the one that holds `today`. */
function previousMonth(today: string): { from: string; to: string } {
  const { year, month } = parts(today);
  const previousYear = month === 1 ? year - 1 : year;
  const previous = month === 1 ? 12 : month - 1;
  const lastDay = new Date(Date.UTC(previousYear, previous, 0)).getUTCDate();
  const monthText = String(previous).padStart(2, "0");
  return { from: `${previousYear}-${monthText}-01`, to: `${previousYear}-${monthText}-${String(lastDay).padStart(2, "0")}` };
}

/** The dates a preset means, in Dhaka time. The custom preset has no dates of its own, so it is not accepted here. */
export function presetRange(preset: Exclude<RangePreset, "custom">, now: number = Date.now()): { from: string; to: string } {
  const today = todayInDhaka(now);
  if (preset === "month") {
    return { from: `${today.slice(0, 8)}01`, to: today };
  }
  if (preset === "year") {
    return { from: `${today.slice(0, 4)}-01-01`, to: today };
  }
  if (preset === "all") {
    return { from: ALL_TIME_START, to: today };
  }
  return previousMonth(today);
}

/** A range the API will answer: both dates, the end not before the start, and at most ten years. */
export function rangeProblem(from: string, to: string): "invalid" | "backwards" | "long" | null {
  if (!isDate(from) || !isDate(to)) {
    return "invalid";
  }
  if (to < from) {
    return "backwards";
  }
  if (spanDays(from, to) > MAX_RANGE_DAYS) {
    return "long";
  }
  return null;
}

/** The sources a list of names holds, known ones only, unique, in the page's order. An empty result is "no sources named" (null). */
export function normaliseSources(names: readonly string[] | null): FinanceSource[] | null {
  if (names === null) {
    return null;
  }
  const wanted = new Set(names.map((name) => name.trim().toUpperCase()));
  const found = FINANCE_SOURCES.filter((source) => wanted.has(source));
  return found.length > 0 ? found : null;
}

/** Reads the filter from the URL. Anything unknown or malformed falls back to the default, never to an error. */
export function parseFinanceUrl(params: URLSearchParams, now: number = Date.now()): FinanceUrlState {
  const rawRange = params.get("range");
  const range: RangePreset = RANGE_PRESETS.includes(rawRange as RangePreset) ? (rawRange as RangePreset) : "month";
  const preset = range === "custom" ? presetRange("month", now) : presetRange(range, now);
  const urlFrom = params.get("from");
  const urlTo = params.get("to");
  const from = isDate(urlFrom) ? urlFrom : preset.from;
  const to = isDate(urlTo) ? urlTo : preset.to;
  const rawSources = params.get("sources");
  const batch = params.get("batch")?.trim() ?? "";
  const rawGroup = params.get("group");
  const rawTab = params.get("tab");
  const rawPage = Number.parseInt(params.get("page") ?? "0", 10);
  return {
    range,
    from,
    to,
    sources: normaliseSources(rawSources === null ? null : rawSources.split(",")),
    batchId: UUID.test(batch) ? batch : null,
    group: GROUPINGS.includes(rawGroup as Grouping) ? (rawGroup as Grouping) : "month",
    tab: FINANCE_TABS.includes(rawTab as FinanceTab) ? (rawTab as FinanceTab) : "source",
    page: Number.isFinite(rawPage) && rawPage > 0 ? rawPage : 0,
  };
}

/** The URL for a filter state. Every field is written, so a link shows exactly what it opens. */
export function financeHref(state: FinanceUrlState): string {
  const params = new URLSearchParams({ range: state.range, from: state.from, to: state.to, group: state.group, tab: state.tab });
  if (state.sources !== null) {
    params.set("sources", state.sources.join(","));
  }
  if (state.batchId) {
    params.set("batch", state.batchId);
  }
  if (state.page > 0) {
    params.set("page", String(state.page));
  }
  return `/staff/finance?${params.toString()}`;
}

/** A link to the entries behind a figure: it opens the entries page with the same dates and batch, and only these sources. */
export function entriesHref(state: FinanceUrlState, sources: readonly FinanceSource[], batchId: string | null = state.batchId): string {
  return ledgerHref({ range: state.range, from: state.from, to: state.to, sources: [...sources], batchId, q: "", sort: "date", page: 0 });
}

// ---- the entries page (the ledger) --------------------------------------------------------------------------------

export const LEDGER_SORTS: readonly LedgerSort[] = ["date", "date_asc", "amount", "amount_asc"];

/** The entries page's filter. It lives in the URL, so a filtered view can be shared or opened from a figure on the finance page. */
export interface LedgerUrlState {
  range: RangePreset;
  from: string;
  to: string;
  /** Null means every source the API offers. */
  sources: FinanceSource[] | null;
  batchId: string | null;
  /** A name, a receipt or a reference; empty for none. */
  q: string;
  sort: LedgerSort;
  page: number;
}

/** Reads the entries page's filter from the URL. The shared fields read as the finance page reads them. */
export function parseLedgerUrl(params: URLSearchParams, now: number = Date.now()): LedgerUrlState {
  const shared = parseFinanceUrl(params, now);
  const rawSort = params.get("sort");
  return {
    range: shared.range,
    from: shared.from,
    to: shared.to,
    sources: shared.sources,
    batchId: shared.batchId,
    q: (params.get("q") ?? "").slice(0, 100),
    sort: LEDGER_SORTS.includes(rawSort as LedgerSort) ? (rawSort as LedgerSort) : "date",
    page: shared.page,
  };
}

/** The URL of the entries page for a filter. */
export function ledgerHref(state: LedgerUrlState): string {
  const params = new URLSearchParams({ range: state.range, from: state.from, to: state.to });
  if (state.sources !== null) {
    params.set("sources", state.sources.join(","));
  }
  if (state.batchId) {
    params.set("batch", state.batchId);
  }
  if (state.q.trim()) {
    params.set("q", state.q.trim());
  }
  if (state.sort !== "date") {
    params.set("sort", state.sort);
  }
  if (state.page > 0) {
    params.set("page", String(state.page));
  }
  return `/staff/finance/entries?${params.toString()}`;
}

/** Money in, money out, or both: which way the selected streams go. "both" when they go both ways. */
export function ledgerDirection(selected: readonly FinanceSource[]): "all" | "income" | "expense" {
  if (selected.length > 0 && selected.every((source) => INCOME_SOURCES.includes(source))) {
    return "income";
  }
  if (selected.length > 0 && selected.every((source) => EXPENSE_SOURCES.includes(source))) {
    return "expense";
  }
  return "all";
}

/** The sources the filter selects among the ones the API offers, in the page's order. */
export function selectedSources(requested: FinanceSource[] | null, available: readonly FinanceSource[]): FinanceSource[] {
  return available.filter((source) => requested === null || requested.includes(source));
}

/**
 * The filter after one source checkbox is ticked or cleared. A selection that is every available source becomes "all" (null), so
 * the URL stays short. The last ticked source cannot be cleared: the page keeps at least one source on, so it never shows nothing by
 * accident. Returns the same selection when the change is not allowed.
 */
export function toggleSource(
  current: FinanceSource[],
  available: readonly FinanceSource[],
  source: FinanceSource,
): FinanceSource[] | null {
  const isOn = current.includes(source);
  if (isOn && current.length === 1) {
    return current;
  }
  const next = available.filter((candidate) => (candidate === source ? !isOn : current.includes(candidate)));
  return next.length === available.length ? null : next;
}

/** The arrow and the size of a change against the previous period. `none` when there is no earlier figure to compare with. */
export function changeParts(percent: number | null): { direction: "up" | "down" | "flat" | "none"; size: number } {
  if (percent === null) {
    return { direction: "none", size: 0 };
  }
  if (percent > 0) {
    return { direction: "up", size: percent };
  }
  if (percent < 0) {
    return { direction: "down", size: Math.abs(percent) };
  }
  return { direction: "flat", size: 0 };
}

// ---- chart -----------------------------------------------------------------------------------------------------------

/** One column of the chart: a month or a week, with the amount of each source in it. */
export interface ChartBucket {
  key: string;
  bySource: Partial<Record<FinanceSource, number>>;
}

function sumOver(bucket: ChartBucket, sources: readonly FinanceSource[]): number {
  return sources.reduce((total, source) => total + (bucket.bySource[source] ?? 0), 0);
}

/** The income a column shows: only the income sources the chart is showing. */
export function bucketIncome(bucket: ChartBucket, shown: readonly FinanceSource[]): number {
  return sumOver(bucket, shown.filter((source) => INCOME_SOURCES.includes(source)));
}

/** The expenditure a column shows: only the expense sources the chart is showing. */
export function bucketExpense(bucket: ChartBucket, shown: readonly FinanceSource[]): number {
  return sumOver(bucket, shown.filter((source) => EXPENSE_SOURCES.includes(source)));
}

/** Every month of the range as a column, with zero where the API had no entries. */
export function monthBuckets(from: string, to: string, months: readonly FinanceMonth[]): ChartBucket[] {
  const found = new Map(months.map((month) => [month.month, month.bySource]));
  const buckets: ChartBucket[] = [];
  let year = Number(from.slice(0, 4));
  let month = Number(from.slice(5, 7));
  const endYear = Number(to.slice(0, 4));
  const endMonth = Number(to.slice(5, 7));
  while (year < endYear || (year === endYear && month <= endMonth)) {
    const key = `${year}-${String(month).padStart(2, "0")}`;
    buckets.push({ key, bySource: found.get(key) ?? {} });
    month += 1;
    if (month > 12) {
      month = 1;
      year += 1;
    }
  }
  return buckets;
}

/** Every week of the range (Mondays), with each entry added to the week it was paid in. */
export function weekBuckets(from: string, to: string, entries: readonly FinanceEntry[]): ChartBucket[] {
  const keys: string[] = [];
  for (let start = weekStart(from); start <= to; start = addDays(start, 7)) {
    keys.push(start);
  }
  const byWeek = new Map<string, Partial<Record<FinanceSource, number>>>(keys.map((key) => [key, {}]));
  for (const entry of entries) {
    const bucket = byWeek.get(weekStart(entry.date));
    if (bucket) {
      bucket[entry.source] = (bucket[entry.source] ?? 0) + entry.amount;
    }
  }
  return keys.map((key) => ({ key, bySource: byWeek.get(key) ?? {} }));
}

/**
 * The one scale the chart uses, from the largest income or expenditure column (and the net when it is positive) down to the most
 * negative net. Zero is always on the scale, so a loss has room below the axis.
 */
export function chartDomain(buckets: readonly ChartBucket[], shown: readonly FinanceSource[]): { min: number; max: number } {
  let largest = 0;
  let lowest = 0;
  for (const bucket of buckets) {
    const income = bucketIncome(bucket, shown);
    const expense = bucketExpense(bucket, shown);
    largest = Math.max(largest, income, expense, income - expense);
    lowest = Math.min(lowest, income - expense);
  }
  const max = chartScale([largest]);
  const min = lowest < 0 ? -chartScale([-lowest]) : 0;
  return { min, max };
}

/** Where a value sits on the chart, from the bottom, as a percentage of the plot's height. */
export function yPercent(value: number, domain: { min: number; max: number }): number {
  const span = domain.max - domain.min;
  return ((value - domain.min) / span) * 100;
}
