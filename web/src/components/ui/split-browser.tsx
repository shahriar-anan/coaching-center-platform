import type { ReactNode } from "react";

export type TreeNode = {
  id: string;
  label: string;
  count?: number;
  children?: TreeNode[];
};

/** A tree on the left and the chosen pane on the right. Stub: the right pane only. */
export function SplitBrowser({
  tree,
  selectedId,
  onSelect,
  children,
}: {
  tree: TreeNode[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  children: ReactNode;
}): ReactNode {
  return (
    <div data-selected={selectedId ?? undefined} data-count={tree.length} data-select={typeof onSelect}>
      {children}
    </div>
  );
}
