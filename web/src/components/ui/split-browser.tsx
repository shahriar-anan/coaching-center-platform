"use client";

import { useMemo, useState, type ReactNode } from "react";
import { ChevronDown, ChevronRight } from "lucide-react";
import { cn } from "@/components/ui/cn";

export type TreeNode = {
  id: string;
  label: string;
  count?: number;
  children?: TreeNode[];
};

/** The ids from the top of the tree down to `id`, both ends included; null when `id` is not in the tree. */
function pathTo(nodes: TreeNode[], id: string, trail: string[] = []): string[] | null {
  for (const node of nodes) {
    const here = [...trail, node.id];
    if (node.id === id) {
      return here;
    }
    const inside = pathTo(node.children ?? [], id, here);
    if (inside) {
      return inside;
    }
  }
  return null;
}

/**
 * One level of the tree. A group with children has a chevron that shows or hides them. The
 * top level starts open; every other group starts closed unless it holds the selection.
 */
function Branch({
  nodes,
  depth,
  selectedId,
  open,
  selectedPath,
  onToggle,
  onSelect,
  toggleLabel,
}: {
  nodes: TreeNode[];
  depth: number;
  selectedId: string | null;
  open: Record<string, boolean>;
  selectedPath: Set<string>;
  onToggle: (id: string, next: boolean) => void;
  onSelect: (id: string) => void;
  toggleLabel: (label: string, open: boolean) => string;
}) {
  // One column that can shrink, so a long label is cut short instead of pushing the count out of the box.
  return (
    <ul className={cn("grid min-w-0 grid-cols-[minmax(0,1fr)] gap-0.5", depth > 0 && "ms-3.5 border-s border-border ps-1")}>
      {nodes.map((node) => {
        const selected = node.id === selectedId;
        const hasChildren = (node.children?.length ?? 0) > 0;
        // A group the user has not touched opens when it is the top level or holds the selection.
        const expanded = open[node.id] ?? (depth === 0 || selectedPath.has(node.id));
        return (
          <li key={node.id} className="min-w-0">
            <div className="flex items-start gap-0.5">
              {hasChildren ? (
                <button
                  type="button"
                  aria-expanded={expanded}
                  aria-label={toggleLabel(node.label, expanded)}
                  onClick={() => onToggle(node.id, !expanded)}
                  className="flex h-10 w-7 shrink-0 items-center justify-center rounded-control text-muted hover:bg-bg hover:text-text"
                >
                  {expanded ? <ChevronDown aria-hidden="true" className="size-4" /> : <ChevronRight aria-hidden="true" className="size-4" />}
                </button>
              ) : (
                <span aria-hidden="true" className="h-10 w-7 shrink-0" />
              )}
              <button
                type="button"
                aria-pressed={selected}
                title={node.label}
                onClick={() => onSelect(node.id)}
                className={cn(
                  "flex min-h-10 min-w-0 flex-1 items-center justify-between gap-2 rounded-control px-2 py-2 text-start text-label leading-snug outline-none",
                  selected ? "bg-action/10 font-semibold text-action" : "text-text hover:bg-bg",
                )}
              >
                {/* Long names wrap onto a second line rather than being cut off. */}
                <span className="min-w-0 break-words">{node.label}</span>
                {node.count !== undefined ? (
                  <span className="shrink-0 rounded-full bg-bg px-2 text-meta tabular-nums text-muted">{node.count}</span>
                ) : null}
              </button>
            </div>
            {hasChildren && expanded ? (
              <Branch
                nodes={node.children!}
                depth={depth + 1}
                selectedId={selectedId}
                open={open}
                selectedPath={selectedPath}
                onToggle={onToggle}
                onSelect={onSelect}
                toggleLabel={toggleLabel}
              />
            ) : null}
          </li>
        );
      })}
    </ul>
  );
}

/**
 * A tree on the left and the chosen pane on the right. Groups open and close with their
 * chevron, and the path to the selected node opens by itself. On phones the tree stacks
 * above the pane in one column (no separate drawer yet).
 */
export function SplitBrowser({
  tree,
  selectedId,
  onSelect,
  heading,
  toggleLabel = (label, expanded) => `${expanded ? "Hide" : "Show"} ${label}`,
  children,
}: {
  tree: TreeNode[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  heading?: ReactNode;
  toggleLabel?: (label: string, expanded: boolean) => string;
  children: ReactNode;
}) {
  // Only the groups the user has opened or closed are recorded here; the rest follow the selection.
  const [open, setOpen] = useState<Record<string, boolean>>({});
  const selectedPath = useMemo(
    () => new Set(selectedId ? (pathTo(tree, selectedId) ?? []) : []),
    [tree, selectedId],
  );

  function onToggle(id: string, next: boolean) {
    setOpen((current) => ({ ...current, [id]: next }));
  }

  return (
    <div className="grid gap-4 md:grid-cols-[18rem_minmax(0,1fr)]">
      <nav className="min-w-0 rounded-card border border-border bg-card p-2 md:sticky md:top-20 md:self-start">
        {heading ? <p className="px-2 pb-2 text-meta text-muted">{heading}</p> : null}
        <Branch
          nodes={tree}
          depth={0}
          selectedId={selectedId}
          open={open}
          selectedPath={selectedPath}
          onToggle={onToggle}
          onSelect={onSelect}
          toggleLabel={toggleLabel}
        />
      </nav>
      <div className="min-w-0">{children}</div>
    </div>
  );
}
