"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { apiFile, apiRequest } from "@/lib/api/client";
import type { BatchView } from "@/lib/api/exam/offline-admin";

// The Master Admin's finance reports (plan section 6, stage 5). Read only; every route answers 403 to anyone but the Master Admin.
// Paths and field names follow the API records in FinanceController and FinanceService. Money comes as JSON numbers; dates are
// YYYY-MM-DD in Dhaka time. Keys start with "finance", so nothing else needs to know about them.

/** Income sources, then expense sources. The order here is the order the page lists them in. */
export const FINANCE_SOURCES = [
  "ONLINE_ORDERS",
  "DIRECT_ENROLMENT",
  "OFFLINE_FEES",
  "OTHER_ONLINE",
  "STAFF_PAY",
  "OFFLINE_INSTRUCTORS",
] as const;

export type FinanceSource = (typeof FINANCE_SOURCES)[number];

export const INCOME_SOURCES: readonly FinanceSource[] = ["ONLINE_ORDERS", "DIRECT_ENROLMENT", "OFFLINE_FEES", "OTHER_ONLINE"];

export const EXPENSE_SOURCES: readonly FinanceSource[] = ["STAFF_PAY", "OFFLINE_INSTRUCTORS"];

/** The sources that exist only with the offline segment (plan 9, side effect 6). */
export const OFFLINE_SOURCES: readonly FinanceSource[] = ["OFFLINE_FEES", "OFFLINE_INSTRUCTORS"];

export type Direction = "INCOME" | "EXPENSE";

/** The filter every report takes. `sources` null means every source the API offers; `batchId` narrows to the offline sources. */
export interface FinanceFilters {
  from: string;
  to: string;
  sources: readonly FinanceSource[] | null;
  batchId: string | null;
}

export interface FinanceSourceTotal {
  source: FinanceSource;
  direction: Direction;
  total: number;
  count: number;
}

export interface FinancePrevious {
  from: string;
  to: string;
  income: number;
  expense: number;
  net: number;
}

/** The change against the previous period, in percent. Null where the earlier figure was zero. */
export interface FinanceChange {
  income: number | null;
  expense: number | null;
  net: number | null;
}

export interface FinanceSummary {
  from: string;
  to: string;
  income: number;
  expense: number;
  net: number;
  /** The sources the API counted. An absent source (the offline switch off, or no batch's sources) is not in this list. */
  sources: FinanceSourceTotal[];
  previous: FinancePrevious;
  change: FinanceChange;
}

export interface FinanceMonth {
  month: string;
  income: number;
  expense: number;
  net: number;
  bySource: Partial<Record<FinanceSource, number>>;
}

export interface FinanceBatchRow {
  batchId: string;
  name: string;
  feesCollected: number;
  instructorCost: number;
  net: number;
  /** Null, or left out of the response, when no fees were collected: there is no margin to show. */
  marginPercent?: number | null;
}

/** One instructor's approved payments in the period (24 Nov: pay no longer comes from the classes, so nothing is owed or waiting). */
export interface FinanceInstructorRow {
  instructorId: string;
  name: string;
  payments: number;
  paid: number;
}

export interface FinanceEntry {
  source: FinanceSource;
  date: string;
  description: string;
  amount: number;
  direction: Direction;
  reference: string | null;
  id: string | null;
  batchId: string | null;
}

export interface FinanceEntryPage {
  items: FinanceEntry[];
  page: number;
  size: number;
  total: number;
  income: number;
  expense: number;
  /** Each stream's total and count for the same dates, batch and search, the streams left out of `sources` included. */
  bySource?: FinanceSourceTotal[];
}

/** How the entries page orders the rows: newest or oldest first, or by amount, largest or smallest first. */
export type LedgerSort = "date" | "date_asc" | "amount" | "amount_asc";

/** The entries page's own narrowing on top of the shared filter: a search (a name, a receipt or a reference) and the order. */
export interface LedgerOptions {
  q: string;
  sort: LedgerSort;
}

function ledgerQuery(filters: FinanceFilters, options: LedgerOptions): string {
  const params = new URLSearchParams(financeQuery(filters));
  if (options.q.trim()) {
    params.set("q", options.q.trim());
  }
  if (options.sort !== "date") {
    params.set("sort", options.sort);
  }
  return params.toString();
}

export const ENTRY_PAGE_SIZE = 50;

/** The API's largest page. Weekly bars read every entry, so they page through at this size. */
const API_MAX_PAGE = 200;

/** Weekly bars stop reading after this many pages (5,000 entries). The chart says so when it stops early. */
export const WEEK_MAX_PAGES = 25;

export const financeKeys = {
  all: ["finance"] as const,
  summary: (query: string) => ["finance", "summary", query] as const,
  monthly: (query: string) => ["finance", "monthly", query] as const,
  batches: (query: string) => ["finance", "batches", query] as const,
  instructors: (from: string, to: string) => ["finance", "instructors", from, to] as const,
  entries: (query: string, page: number, size: number) => ["finance", "entries", query, page, size] as const,
  allEntries: (query: string) => ["finance", "allEntries", query] as const,
  batchChoices: () => ["finance", "batchChoices"] as const,
};

/** The query string the reports share: the range, the sources the filter names (when it names some), and the batch. */
export function financeQuery(filters: FinanceFilters): string {
  const params = new URLSearchParams({ from: filters.from, to: filters.to });
  if (filters.sources && filters.sources.length > 0) {
    params.set("sources", filters.sources.join(","));
  }
  if (filters.batchId) {
    params.set("batchId", filters.batchId);
  }
  return params.toString();
}

export function summaryPath(filters: FinanceFilters): string {
  return `/api/v1/finance/summary?${financeQuery(filters)}`;
}

/** The summary without any source named: the sources the API offers for this range and batch. */
export function availabilityFilters(filters: FinanceFilters): FinanceFilters {
  return { ...filters, sources: null };
}

export function useFinanceSummary(filters: FinanceFilters, enabled = true) {
  const query = financeQuery(filters);
  return useQuery({
    queryKey: financeKeys.summary(query),
    queryFn: ({ signal }) => apiRequest<FinanceSummary>(`/api/v1/finance/summary?${query}`, { signal }),
    enabled,
    placeholderData: keepPreviousData,
  });
}

/**
 * The batches the batch filter offers. Asked for only when an offline source is on, because the offline routes answer 404 while the
 * switch is off.
 */
export function useFinanceBatchChoices(enabled: boolean) {
  return useQuery({
    queryKey: financeKeys.batchChoices(),
    queryFn: ({ signal }) => apiRequest<BatchView[]>("/api/v1/offline-batches", { signal }),
    enabled,
    staleTime: 60_000,
  });
}

export function useFinanceMonthly(filters: FinanceFilters, enabled = true) {
  const query = financeQuery(filters);
  return useQuery({
    queryKey: financeKeys.monthly(query),
    queryFn: ({ signal }) => apiRequest<FinanceMonth[]>(`/api/v1/finance/monthly?${query}`, { signal }),
    enabled,
    placeholderData: keepPreviousData,
  });
}

export function useFinanceBatches(filters: FinanceFilters, enabled = true) {
  const query = financeQuery(filters);
  return useQuery({
    queryKey: financeKeys.batches(query),
    queryFn: ({ signal }) => apiRequest<FinanceBatchRow[]>(`/api/v1/finance/batches?${query}`, { signal }),
    enabled,
    placeholderData: keepPreviousData,
  });
}

/** Per instructor. The API takes only the dates: it has no source or batch filter for instructors. */
export function useFinanceInstructors(from: string, to: string, enabled = true) {
  const query = new URLSearchParams({ from, to }).toString();
  return useQuery({
    queryKey: financeKeys.instructors(from, to),
    queryFn: ({ signal }) => apiRequest<FinanceInstructorRow[]>(`/api/v1/finance/instructors?${query}`, { signal }),
    enabled,
    placeholderData: keepPreviousData,
  });
}

/** One page of the entries page (the ledger): the shared filter, the search and the order. */
export function useFinanceLedger(filters: FinanceFilters, options: LedgerOptions, page: number, enabled = true, size = ENTRY_PAGE_SIZE) {
  const query = ledgerQuery(filters, options);
  return useQuery({
    queryKey: ["finance", "ledger", query, page, size] as const,
    queryFn: ({ signal }) => apiRequest<FinanceEntryPage>(`/api/v1/finance/entries?${query}&page=${page}&size=${size}`, { signal }),
    enabled,
    placeholderData: keepPreviousData,
  });
}

/** One page of the entries behind the figures, newest first. */
export function useFinanceEntries(filters: FinanceFilters, page: number, enabled = true, size = ENTRY_PAGE_SIZE) {
  const query = financeQuery(filters);
  return useQuery({
    queryKey: financeKeys.entries(query, page, size),
    queryFn: ({ signal }) =>
      apiRequest<FinanceEntryPage>(`/api/v1/finance/entries?${query}&page=${page}&size=${size}`, { signal }),
    enabled,
    placeholderData: keepPreviousData,
  });
}

export interface FinanceEntryRows {
  items: FinanceEntry[];
  /** True when the range holds more entries than the weekly bars read. */
  truncated: boolean;
}

/**
 * Every entry of the filter, for the weekly bars. The API has no weekly series, so the page reads the entries and groups them
 * by week itself. It reads at most {@link WEEK_MAX_PAGES} pages.
 */
export function useFinanceEntryRows(filters: FinanceFilters, enabled = true) {
  const query = financeQuery(filters);
  return useQuery({
    queryKey: financeKeys.allEntries(query),
    queryFn: async ({ signal }): Promise<FinanceEntryRows> => {
      const items: FinanceEntry[] = [];
      let total = 0;
      for (let page = 0; page < WEEK_MAX_PAGES; page++) {
        const result = await apiRequest<FinanceEntryPage>(
          `/api/v1/finance/entries?${query}&page=${page}&size=${API_MAX_PAGE}`,
          { signal },
        );
        total = result.total;
        items.push(...result.items);
        if (result.items.length === 0 || items.length >= total) {
          return { items, truncated: false };
        }
      }
      return { items, truncated: items.length < total };
    },
    enabled,
    placeholderData: keepPreviousData,
  });
}

/**
 * Saves the CSV of the entries behind the filter. The file needs the bearer token, so it is fetched as a blob (the same path the
 * receipts use) and saved from a local link. Throws when the download fails; the caller shows the message.
 */
export async function downloadFinanceEntriesCsv(filters: FinanceFilters, options: LedgerOptions = { q: "", sort: "date" }): Promise<void> {
  const { blob, fileName } = await apiFile(`/api/v1/finance/entries.csv?${ledgerQuery(filters, options)}`);
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName ?? `finance-entries-${filters.from}-${filters.to}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}
