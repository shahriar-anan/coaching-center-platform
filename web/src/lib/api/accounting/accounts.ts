"use client";

import { useQuery } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import { queryString } from "@/lib/api/exam/staff-ops";
import { accountingKeys, useAccountingWrite, type ArchiveAction } from "./query";
import type { AccountKind, MoneyAccount } from "./types";

/** The contract names no fields for accounts; these are the ones MoneyAccount carries (name, kind, note) plus the version on a change. */
export type AccountBody = { name: string; kind: AccountKind; note: string | null; version?: number };

/** The money accounts in position order. Archived ones only when asked. */
export function useAccounts(archived = false) {
  return useQuery({
    queryKey: [...accountingKeys.all, "accounts", archived],
    queryFn: ({ signal }) =>
      apiRequest<MoneyAccount[]>(`/api/v1/accounting/accounts${queryString({ archived: archived ? "true" : "" })}`, { signal }),
  });
}

export function useSaveAccount() {
  return useAccountingWrite<{ id?: string; body: AccountBody }, MoneyAccount>(({ id, body }) =>
    id
      ? apiRequest<MoneyAccount>(`/api/v1/accounting/accounts/${id}`, { method: "PATCH", body })
      : apiRequest<MoneyAccount>("/api/v1/accounting/accounts", { method: "POST", body }),
  );
}

export function useAccountAction() {
  return useAccountingWrite<{ id: string; action: ArchiveAction }, MoneyAccount>(({ id, action }) =>
    apiRequest<MoneyAccount>(`/api/v1/accounting/accounts/${id}/${action}`, { method: "POST" }),
  );
}

export function useReorderAccounts() {
  return useAccountingWrite<string[], MoneyAccount[]>((ids) =>
    apiRequest<MoneyAccount[]>("/api/v1/accounting/accounts/order", { method: "POST", body: { ids } }),
  );
}
