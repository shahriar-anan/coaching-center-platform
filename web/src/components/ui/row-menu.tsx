import type { ReactNode } from "react";

export type RowMenuItem = {
  label: string;
  href?: string;
  onClick?: () => void;
  tone?: "danger";
};

/** A row's action menu. Stub: renders nothing until the real menu lands. */
export function RowMenu({
  items,
  label,
}: {
  items: RowMenuItem[];
  label: string;
}): ReactNode {
  return <span data-label={label} data-count={items.length} hidden />;
}
