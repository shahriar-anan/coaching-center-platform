"use client";

import { useQuery } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import { queryString } from "@/lib/api/exam/staff-ops";
import { accountingKeys, useAccountingWrite, type ArchiveAction } from "./query";
import type { LastPrice, Payee, PayeeRow, Statement } from "./types";

/** A payee's details as the form sends them. Aliases are for the Offline instructors group only; a user link is optional. */
export type PayeeBody = {
  name: string;
  phone: string | null;
  note: string | null;
  aliases: string[];
  userId: string | null;
  version?: number;
};

/** The payees of one group. `q` matches the name or phone; archived ones only when asked. */
export function useGroupPayees(groupId: string, filters: { q?: string; archived?: boolean } = {}) {
  return useQuery({
    queryKey: [...accountingKeys.all, "group-payees", groupId, filters],
    queryFn: ({ signal }) =>
      apiRequest<PayeeRow[]>(
        `/api/v1/accounting/groups/${groupId}/payees${queryString({ q: filters.q?.trim(), archived: filters.archived ? "true" : "" })}`,
        { signal },
      ),
    enabled: Boolean(groupId),
  });
}

export function usePayee(id: string) {
  return useQuery({
    queryKey: [...accountingKeys.all, "payee", id],
    queryFn: ({ signal }) => apiRequest<Payee>(`/api/v1/accounting/payees/${id}`, { signal }),
    enabled: Boolean(id),
  });
}

/** Adds a payee to a group, or changes one when an id is given. A stale version is refused. */
export function useSavePayee() {
  return useAccountingWrite<{ groupId: string; id?: string; body: PayeeBody }, Payee>(({ groupId, id, body }) =>
    id
      ? apiRequest<Payee>(`/api/v1/accounting/payees/${id}`, { method: "PATCH", body })
      : apiRequest<Payee>(`/api/v1/accounting/groups/${groupId}/payees`, { method: "POST", body }),
  );
}

export function usePayeeAction() {
  return useAccountingWrite<{ id: string; action: ArchiveAction }, Payee>(({ id, action }) =>
    apiRequest<Payee>(`/api/v1/accounting/payees/${id}/${action}`, { method: "POST" }),
  );
}

/** The price of the newest active charge for each type of this payee (plan A7). Only asked for when a form needs it. */
export function useLastPrices(payeeId: string | null | undefined) {
  return useQuery({
    queryKey: [...accountingKeys.all, "last-prices", payeeId],
    queryFn: ({ signal }) => apiRequest<LastPrice[]>(`/api/v1/accounting/payees/${payeeId}/last-prices`, { signal }),
    enabled: Boolean(payeeId),
  });
}

/** Site users who may be linked to a payee: System Admins and Instructors (the picker on the payee's details). */
export function useLinkableUsers(q: string, enabled = true) {
  return useQuery({
    queryKey: [...accountingKeys.all, "linkable", q.trim()],
    queryFn: ({ signal }) =>
      apiRequest<{ id: string; name: string; role: string }[]>(`/api/v1/accounting/users/linkable${queryString({ q: q.trim() })}`, {
        signal,
      }),
    enabled,
  });
}

/** One payee over a date range: the opening balance, what was charged and paid, the closing balance, and the lines (contract 3.2). */
export function usePayeeStatement(id: string, from: string, to: string) {
  return useQuery({
    queryKey: [...accountingKeys.all, "statement", id, from, to],
    queryFn: ({ signal }) =>
      apiRequest<Statement>(`/api/v1/accounting/payees/${id}/statement${queryString({ from, to })}`, { signal }),
    enabled: Boolean(id && from && to),
  });
}
