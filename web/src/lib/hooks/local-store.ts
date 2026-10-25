"use client";

/**
 * localStorage as an external store, read with useSyncExternalStore. Blocked
 * storage falls back to memory for this page view. Writes notify every
 * subscriber in this tab; other tabs are told through the storage event.
 */

const listeners = new Set<() => void>();
const memory = new Map<string, string>();

function notify() {
  listeners.forEach((listener) => listener());
}

export function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  window.addEventListener("storage", listener);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", listener);
  };
}

export function readStored(storageKey: string): string | null {
  try {
    return window.localStorage.getItem(storageKey) ?? memory.get(storageKey) ?? null;
  } catch {
    return memory.get(storageKey) ?? null;
  }
}

export function writeStored(storageKey: string, raw: string): void {
  try {
    window.localStorage.setItem(storageKey, raw);
  } catch {
    memory.set(storageKey, raw);
  }
  notify();
}

export function removeStored(storageKey: string): void {
  try {
    window.localStorage.removeItem(storageKey);
  } catch {
    // Nothing stored, nothing to remove.
  }
  memory.delete(storageKey);
  notify();
}
