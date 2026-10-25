import { useEffect, useState } from "react";

/**
 * Offset between the API clock and this device, in milliseconds. Updated from
 * the `Date` header of every API response, so countdowns never trust the
 * phone's clock alone.
 */
let offsetMs = 0;

export function recordServerDate(dateHeader: string | null | undefined): void {
  if (!dateHeader) {
    return;
  }
  const server = Date.parse(dateHeader);
  if (Number.isNaN(server)) {
    return;
  }
  // The header has whole seconds; keep the device clock when it is within one.
  const offset = server - Date.now();
  offsetMs = Math.abs(offset) < 1000 ? 0 : offset;
}

/** For sources that send an exact server time, such as a sitting's `serverTime`. */
export function recordServerTime(iso: string | null | undefined): void {
  if (!iso) {
    return;
  }
  const server = Date.parse(iso);
  if (!Number.isNaN(server)) {
    offsetMs = server - Date.now();
  }
}

export function clockOffset(): number {
  return offsetMs;
}

export function resetClock(): void {
  offsetMs = 0;
}

export function serverNow(): number {
  return Date.now() + offsetMs;
}

/** Re-renders every `intervalMs` with the current server time. */
export function useServerNow(intervalMs = 1000): number {
  const [now, setNow] = useState(() => serverNow());
  useEffect(() => {
    const id = window.setInterval(() => setNow(serverNow()), intervalMs);
    return () => window.clearInterval(id);
  }, [intervalMs]);
  return now;
}
