"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFile, apiRequest } from "@/lib/api/client";
import { queryString } from "@/lib/api/exam/staff-ops";
import { accountingKeys, useAccountingWrite } from "./query";
import type { CopyDraft, Entry, EntryDetail, EntryInput, EntryPage, EntryPatch } from "./types";

/** The filters of the entries list (contract 3.2). Every one is optional. */
export type EntryFilters = {
  from?: string;
  to?: string;
  groupId?: string;
  payeeId?: string;
  typeId?: string;
  kind?: string;
  accountId?: string;
  batchId?: string;
  status?: string;
  q?: string;
  sort?: string;
  page?: number;
  size?: number;
};

/** One page of entries, with the totals of every matching active row. */
export function useEntries(filters: EntryFilters, enabled = true) {
  return useQuery({
    queryKey: [...accountingKeys.all, "entries", filters],
    queryFn: ({ signal }) => apiRequest<EntryPage>(`/api/v1/accounting/entries${queryString(filters)}`, { signal }),
    enabled,
  });
}

/** Records one entry. A charge with `paidNow` answers the charge and then its payment. */
export function useCreateEntry() {
  return useAccountingWrite<EntryInput, Entry[]>((body) =>
    apiRequest<Entry[]>("/api/v1/accounting/entries", { method: "POST", body }),
  );
}

/** Records several entries at once: all or nothing (1 to 200 lines, no `paidNow`). */
export function useCreateEntries() {
  return useAccountingWrite<EntryInput[], Entry[]>((entries) =>
    apiRequest<Entry[]>("/api/v1/accounting/entries/many", { method: "POST", body: { entries } }),
  );
}

/** The drafts for "Copy last month" of one group: last month's active charges (Payees) or spending (Log), dated today. */
export function useCopySource(groupId: string, month: string) {
  return useQuery({
    queryKey: [...accountingKeys.all, "copy-source", groupId, month],
    queryFn: ({ signal }) =>
      apiRequest<CopyDraft[]>(`/api/v1/accounting/groups/${groupId}/copy-source${queryString({ month })}`, { signal }),
    enabled: Boolean(groupId && month),
  });
}

/** One entry with its paired entry, its corrections and who recorded it (contract 3.1). */
export function useEntry(id: string) {
  return useQuery({
    queryKey: [...accountingKeys.all, "entry", id],
    queryFn: ({ signal }) => apiRequest<EntryDetail>(`/api/v1/accounting/entries/${id}`, { signal }),
    enabled: Boolean(id),
  });
}

/** The fields an edit may send. `reason` is required; the version is the one the page read. */
export type EntryEditBody = Omit<EntryPatch, "reason" | "version"> & { reason: string; version: number };

/** Corrects an entry. It applies at once and the before and after values are kept. */
export function useEditEntry() {
  return useAccountingWrite<{ id: string; body: EntryEditBody }, Entry>(({ id, body }) =>
    apiRequest<Entry>(`/api/v1/accounting/entries/${id}`, { method: "PATCH", body }),
  );
}

/** Voids an entry with a reason. With `alsoVoidPaired`, the payment made with a charge is voided too. Answers the voided rows. */
export function useVoidEntry() {
  return useAccountingWrite<{ id: string; reason: string; alsoVoidPaired: boolean }, Entry[]>(({ id, reason, alsoVoidPaired }) =>
    apiRequest<Entry[]>(`/api/v1/accounting/entries/${id}/void`, { method: "POST", body: { reason, alsoVoidPaired } }),
  );
}

/** Downloads the entries of the filters as CSV (at most 20,000 rows, else the API refuses). The file name comes from the answer. */
export async function downloadAccountingEntriesCsv(filters: EntryFilters): Promise<void> {
  const { blob, fileName } = await apiFile(`/api/v1/accounting/entries.csv${queryString(filters)}`);
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName ?? "accounting-entries.csv";
  link.click();
  URL.revokeObjectURL(url);
}
