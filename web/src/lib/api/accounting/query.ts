"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";

/*
 * Query keys of the accounting API (docs/accounting/accounting-plan.md, 6). Every key starts with "accounting".
 */
export const accountingKeys = {
  all: ["accounting"] as const,
};

/**
 * Runs an accounting write, then refreshes every accounting query and the Finance ones: a payment, spending or income moves money in
 * Finance, and a group's Count in Finance switch changes what Finance reads. Refreshes after a failed write too, so a stale list is
 * fixed on screen.
 */
export function useAccountingWrite<TVars, TResult>(run: (vars: TVars) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation<TResult, Error, TVars>({
    mutationFn: run,
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: accountingKeys.all });
      void queryClient.invalidateQueries({ queryKey: ["finance"] });
    },
  });
}

/** The archive and restore actions share one shape: a POST with no body on the item's own path. */
export type ArchiveAction = "archive" | "restore";
