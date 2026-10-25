// Stand-in for @/lib/time/clock (WS0). Delete after the WS0 merge.
import { useEffect, useState } from "react";

export function serverNow(): number {
  return Date.now();
}

export function useServerNow(intervalMs: number): number {
  const [now, setNow] = useState(() => serverNow());
  useEffect(() => {
    const id = window.setInterval(() => setNow(serverNow()), intervalMs);
    return () => window.clearInterval(id);
  }, [intervalMs]);
  return now;
}
