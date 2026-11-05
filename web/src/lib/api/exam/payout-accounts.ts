"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";

// Payout accounts (staff pay plan, phase 2). Each route acts on the signed-in person's own accounts only; the API has no
// route for another person's. Keys live in this file.

/** How a payout account is paid into. A bank takes the bank fields; the three wallets take a mobile number. */
export type PayoutAccountKind = "BANK" | "BKASH" | "NAGAD" | "ROCKET";

export const PAYOUT_ACCOUNT_KINDS: PayoutAccountKind[] = ["BANK", "BKASH", "NAGAD", "ROCKET"];

/** One account as the API sends it. The numbers are in full; a list shows `maskedNumber` instead. */
export interface PayoutAccount {
  id: string;
  kind: PayoutAccountKind;
  accountName: string | null;
  accountNumber: string | null;
  bankName: string | null;
  branch: string | null;
  routingNumber: string | null;
  walletNumber: string | null;
  /** The last four digits behind a fixed prefix, such as "•••• 4821". */
  maskedNumber: string;
  preferred: boolean;
  createdAt: string;
  updatedAt: string;
}

/** What the owner sends. Only the fields the kind uses are read; the rest may be left out. */
export interface PayoutAccountInput {
  kind: PayoutAccountKind;
  accountName?: string;
  accountNumber?: string;
  bankName?: string;
  branch?: string;
  routingNumber?: string;
  walletNumber?: string;
}

/** A person keeps at most this many live accounts. The API refuses a sixth. */
export const PAYOUT_ACCOUNT_LIMIT = 5;

export const payoutAccountKeys = {
  all: ["payoutAccounts"] as const,
  list: () => ["payoutAccounts", "list"] as const,
};

export const PAYOUT_ACCOUNTS_PATH = "/api/v1/me/payout-accounts";

/** The signed-in person's accounts, preferred first. Asked only for pay roles (`enabled`). */
export function usePayoutAccounts(enabled: boolean) {
  return useQuery({
    queryKey: payoutAccountKeys.list(),
    queryFn: ({ signal }) => apiRequest<PayoutAccount[]>(PAYOUT_ACCOUNTS_PATH, { signal }),
    enabled,
  });
}

function invalidatePayoutAccounts(client: ReturnType<typeof useQueryClient>) {
  return client.invalidateQueries({ queryKey: payoutAccountKeys.all });
}

/** Adds an account when there is no id, or changes the given one. */
export function useSavePayoutAccount() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: PayoutAccountInput }) =>
      id
        ? apiRequest<PayoutAccount>(`${PAYOUT_ACCOUNTS_PATH}/${id}`, { method: "PUT", body: input })
        : apiRequest<PayoutAccount>(PAYOUT_ACCOUNTS_PATH, { method: "POST", body: input }),
    retry: false,
    onSettled: () => invalidatePayoutAccounts(client),
  });
}

/** Removes one of the owner's accounts. The row is kept on the server; it simply stops showing. */
export function useRemovePayoutAccount() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiRequest<void>(`${PAYOUT_ACCOUNTS_PATH}/${id}`, { method: "DELETE" }),
    retry: false,
    onSettled: () => invalidatePayoutAccounts(client),
  });
}

/** Makes one of the owner's accounts the preferred one. */
export function useMakePreferredPayoutAccount() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      apiRequest<PayoutAccount>(`${PAYOUT_ACCOUNTS_PATH}/${id}/preferred`, { method: "POST", body: {} }),
    retry: false,
    onSettled: () => invalidatePayoutAccounts(client),
  });
}
