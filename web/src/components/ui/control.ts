/** Shared look for text-like inputs, so every control has the same height, radius and focus. */
export const controlBase =
  "w-full min-h-11 rounded-control border bg-card px-3.5 text-[15px] text-text placeholder:text-muted focus:border-accent disabled:opacity-60";

export function controlBorder(invalid?: boolean): string {
  return invalid ? "border-danger" : "border-border";
}
