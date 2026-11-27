"use client";

import { useQuery } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import { queryString } from "@/lib/api/exam/staff-ops";
import { accountingKeys, useAccountingWrite, type ArchiveAction } from "./query";
import type { Direction, Group, GroupMode } from "./types";

/** A new group sends mode and direction; a change sends the name, Count in Finance and the version (mode and direction never change). */
export type GroupBody = {
  nameEn: string;
  nameBn: string | null;
  inFinance: boolean;
  mode?: GroupMode;
  direction?: Direction;
  version?: number;
};

/** The groups in position order. Archived ones only when asked. */
export function useGroups(archived = false) {
  return useQuery({
    queryKey: [...accountingKeys.all, "groups", archived],
    queryFn: ({ signal }) =>
      apiRequest<Group[]>(`/api/v1/accounting/groups${queryString({ archived: archived ? "true" : "" })}`, { signal }),
  });
}

/** Adds a group when no id is given, otherwise changes it. A stale version is refused (409 STALE_VERSION). */
export function useSaveGroup() {
  return useAccountingWrite<{ id?: string; body: GroupBody }, Group>(({ id, body }) =>
    id
      ? apiRequest<Group>(`/api/v1/accounting/groups/${id}`, { method: "PATCH", body })
      : apiRequest<Group>("/api/v1/accounting/groups", { method: "POST", body }),
  );
}

export function useGroupAction() {
  return useAccountingWrite<{ id: string; action: ArchiveAction }, Group>(({ id, action }) =>
    apiRequest<Group>(`/api/v1/accounting/groups/${id}/${action}`, { method: "POST" }),
  );
}

/** Puts every live group in this order. The list must name each live group once. */
export function useReorderGroups() {
  return useAccountingWrite<string[], Group[]>((ids) =>
    apiRequest<Group[]>("/api/v1/accounting/groups/order", { method: "POST", body: { ids } }),
  );
}
