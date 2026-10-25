// Stand-in for the clsx package (WS0 adds it). Delete after the WS0 merge.
export function clsx(...values: unknown[]): string {
  return values.filter((v) => typeof v === "string" && v).join(" ");
}
