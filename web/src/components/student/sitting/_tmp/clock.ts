// Stand-in for @/lib/time/clock (WS0). Delete after WS0 merges.
import { useEffect, useState } from "react";

let offsetMs = 0;

export function recordServerTime(iso: string | null | undefined): void {
  if (!iso) {
    return;
  }
  const server = Date.parse(iso);
  if (!Number.isNaN(server)) {
    offsetMs = server - Date.now();
  }
}

export function resetClock(): void {
  offsetMs = 0;
}

export function serverNow(): number {
  return Date.now() + offsetMs;
}

export function useServerNow(intervalMs = 1000): number {
  const [now, setNow] = useState(() => serverNow());
  useEffect(() => {
    const id = window.setInterval(() => setNow(serverNow()), intervalMs);
    return () => window.clearInterval(id);
  }, [intervalMs]);
  return now;
}
