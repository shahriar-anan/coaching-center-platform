import { useSyncExternalStore } from "react";

/** A navigation the guard stopped, waiting for the user to choose. */
export type PendingLeave = { message?: string; onConfirm: () => void };

let pending: PendingLeave | null = null;
const listeners = new Set<() => void>();

function emit() {
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** Holds the navigation until the dialog is answered. One prompt at a time: the latest wins. */
export function askBeforeLeaving(next: PendingLeave): void {
  pending = next;
  emit();
}

/** Answers the dialog. Confirming runs the held navigation; either way the prompt closes. */
export function settleLeaving(confirm: boolean): void {
  const current = pending;
  pending = null;
  emit();
  if (confirm) current?.onConfirm();
}

/** The prompt the shell's dialog shows, or null. */
export function usePendingLeave(): PendingLeave | null {
  return useSyncExternalStore(subscribe, () => pending, () => null);
}
