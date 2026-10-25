export const MIN_ZOOM = 0.5;
export const MAX_ZOOM = 3;
export const ZOOM_STEP = 0.25;

/** Zoom relative to fit-to-width, kept in range and rounded so the label stays a clean percent. */
export function clampZoom(value: number): number {
  const bounded = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, value));
  return Math.round(bounded * 100) / 100;
}
