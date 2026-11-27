"use client";

import { useQuery } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import { queryString } from "@/lib/api/exam/staff-ops";
import { accountingKeys, useAccountingWrite, type ArchiveAction } from "./query";
import type { AccType, TypeKind } from "./types";

/** Types of one group. Kind is sent on a new type; on a change it is sent only while the type is not used (the API refuses otherwise). */
export type TypeBody = {
  nameEn: string;
  nameBn: string | null;
  kind: TypeKind;
  unitEn: string | null;
  unitBn: string | null;
  needsPeriod: boolean;
  needsDescription: boolean;
  version?: number;
};

/** The types of a group by position. Archived ones only when asked. */
export function useGroupTypes(groupId: string, archived = false) {
  return useQuery({
    queryKey: [...accountingKeys.all, "types", groupId, archived],
    queryFn: ({ signal }) =>
      apiRequest<AccType[]>(`/api/v1/accounting/groups/${groupId}/types${queryString({ archived: archived ? "true" : "" })}`, {
        signal,
      }),
    enabled: Boolean(groupId),
  });
}

export function useSaveType() {
  return useAccountingWrite<{ groupId: string; id?: string; body: TypeBody }, AccType>(({ groupId, id, body }) =>
    id
      ? apiRequest<AccType>(`/api/v1/accounting/types/${id}`, { method: "PATCH", body })
      : apiRequest<AccType>(`/api/v1/accounting/groups/${groupId}/types`, { method: "POST", body }),
  );
}

export function useTypeAction() {
  return useAccountingWrite<{ id: string; action: ArchiveAction }, AccType>(({ id, action }) =>
    apiRequest<AccType>(`/api/v1/accounting/types/${id}/${action}`, { method: "POST" }),
  );
}

export function useReorderTypes() {
  return useAccountingWrite<{ groupId: string; ids: string[] }, AccType[]>(({ groupId, ids }) =>
    apiRequest<AccType[]>(`/api/v1/accounting/groups/${groupId}/types/order`, { method: "POST", body: { ids } }),
  );
}
