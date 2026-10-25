/** Minutes left at which the paper announces a warning: 10, 5 and 1. */
export const WARN_AT_MINUTES = [10, 5, 1] as const;

/**
 * Which warning, if any, has just been crossed. `previousMs` is the last reading, so each threshold
 * fires once, on the reading that first goes under it.
 */
export function crossedWarning(previousMs: number | null, remainingMs: number): number | null {
  for (const minutes of WARN_AT_MINUTES) {
    const line = minutes * 60_000;
    if (remainingMs <= line && (previousMs === null || previousMs > line)) {
      return minutes;
    }
  }
  return null;
}

/** The last minute turns the bar to the warning tone. */
export function inFinalMinute(remainingMs: number): boolean {
  return remainingMs > 0 && remainingMs <= 60_000;
}
