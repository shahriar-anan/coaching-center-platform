"use client";

import { useQuery } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import { queryString } from "@/lib/api/exam/staff-ops";
import { accountingKeys, useAccountingWrite } from "./query";
import type { CopyDraft, Entry, EntryInput, EntryPage } from "./types";

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
