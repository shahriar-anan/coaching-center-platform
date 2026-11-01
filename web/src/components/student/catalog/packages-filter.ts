import type { CategoryNode, PackageFilter } from "@/lib/api/exam/catalog";
import { parseTypeFilter } from "./type-filter";

/** A category id is a UUID. Anything else is dropped, so a typed-in link never sends a bad id (the API answers 400). */
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** The longest search text the page sends. */
export const PACKAGE_SEARCH_MAX = 100;

/** Trims the search text and caps its length. */
function normalizeSearch(text: string): string {
  return text.slice(0, PACKAGE_SEARCH_MAX).trim();
}

/** Reads the filters from the URL (`?category=&type=&q=`). An unknown or empty value is no filter. */
export function parsePackageFilter(params: Pick<URLSearchParams, "get">): PackageFilter {
  const category = params.get("category");
  return {
    categoryId: category && UUID_PATTERN.test(category) ? category : null,
    examType: parseTypeFilter(params.get("type")),
    q: normalizeSearch(params.get("q") ?? ""),
  };
}

/**
 * Writes the filters into a copy of a query. A null or empty value is removed, and any other query value is kept.
 */
export function withPackageFilter(params: URLSearchParams, filter: PackageFilter): URLSearchParams {
  const next = new URLSearchParams(params);
  const values: [string, string | null][] = [
    ["category", filter.categoryId],
    ["type", filter.examType],
    ["q", normalizeSearch(filter.q) || null],
  ];
  for (const [name, value] of values) {
    if (value) {
      next.set(name, value);
    } else {
      next.delete(name);
    }
  }
  return next;
}

/** The category tree as one list in display order, each row with its depth, for an indented select. */
export function flattenCategories(nodes: CategoryNode[], depth = 0): { node: CategoryNode; depth: number }[] {
  return [...nodes]
    .sort((a, b) => a.position - b.position)
    .flatMap((node) => [{ node, depth }, ...flattenCategories(node.children ?? [], depth + 1)]);
}
