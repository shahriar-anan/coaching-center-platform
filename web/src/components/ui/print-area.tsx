import type { ReactNode } from "react";

/**
 * The only part of the page that prints. The print rules in globals.css hide
 * everything else, so the result is a clean sheet without the shell.
 */
export function PrintArea({ children }: { children: ReactNode }) {
  return <div data-print-area="">{children}</div>;
}
