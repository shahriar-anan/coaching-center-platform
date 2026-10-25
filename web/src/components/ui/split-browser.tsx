"use client";

import type { ReactNode } from "react";
import { cn } from "@/components/ui/cn";

export type TreeNode = {
  id: string;
  label: string;
  count?: number;
  children?: TreeNode[];
};

function Branch({
  nodes,
  depth,
  selectedId,
  onSelect,
}: {
  nodes: TreeNode[];
  depth: number;
  selectedId: string | null;
  onSelect: (id: string) => void;
}) {
  return (
    <ul className={cn("grid gap-0.5", depth > 0 && "ps-3")}>
      {nodes.map((node) => {
        const selected = node.id === selectedId;
        return (
          <li key={node.id}>
            <button
              type="button"
              aria-pressed={selected}
              onClick={() => onSelect(node.id)}
              className={cn(
                "flex min-h-10 w-full items-center justify-between gap-2 rounded-control px-3 text-start text-label outline-none",
                selected ? "bg-action/10 font-semibold text-action" : "text-text hover:bg-bg",
              )}
            >
              <span className="min-w-0 truncate">{node.label}</span>
              {node.count !== undefined ? (
                <span className="shrink-0 text-meta tabular-nums text-muted">{node.count}</span>
              ) : null}
            </button>
            {node.children && node.children.length > 0 ? (
              <Branch nodes={node.children} depth={depth + 1} selectedId={selectedId} onSelect={onSelect} />
            ) : null}
          </li>
        );
      })}
    </ul>
  );
}

/**
 * A tree on the left and the chosen pane on the right. On phones the tree
 * stacks above the pane in one column (no separate drawer yet).
 */
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
}) {
  return (
    <div className="grid gap-4 md:grid-cols-[240px_minmax(0,1fr)]">
      <nav className="min-w-0 rounded-card border border-border bg-card p-2 md:sticky md:top-20 md:self-start">
        <Branch nodes={tree} depth={0} selectedId={selectedId} onSelect={onSelect} />
      </nav>
      <div className="min-w-0">{children}</div>
    </div>
  );
}
