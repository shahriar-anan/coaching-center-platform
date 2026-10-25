import type { ReactNode } from "react";

/** The only part of the page that prints. Stub: plain wrapper. */
export function PrintArea({ children }: { children: ReactNode }): ReactNode {
  return <div data-print-area="">{children}</div>;
}
