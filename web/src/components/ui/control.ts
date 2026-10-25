/** Shared look for text-like inputs, so every control has the same height, radius and focus. */
export const controlBase =
  "w-full min-h-11 rounded-control border bg-card px-3.5 text-body text-text placeholder:text-muted hover:border-muted focus:border-accent disabled:cursor-not-allowed disabled:opacity-60";

export function controlBorder(invalid?: boolean): string {
  return invalid ? "border-danger" : "border-border";
}
