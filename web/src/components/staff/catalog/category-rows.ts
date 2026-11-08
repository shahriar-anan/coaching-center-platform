import type { CategoryTreeNode } from "@/lib/api/exam/staff-catalog";

/** Category names with their depth, in tree order, for a category select. */
export function categoryRows(nodes: CategoryTreeNode[] | undefined, depth = 0): { id: string; depth: number; titleEn: string; titleBn: string }[] {
  return (nodes ?? []).flatMap((node) => [
    { id: node.id, depth, titleEn: node.titleEn, titleBn: node.titleBn },
    ...categoryRows(node.children, depth + 1),
  ]);
}
