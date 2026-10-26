import type { CategoryNode, PublicModule, UpcomingExam } from "@/lib/api/exam/catalog";
import { categoryPath, modulePath, moduleTypeCount } from "@/components/student/catalog/catalog-helpers";

/** Every module in the tree, categories walked depth first, in tree order. */
export function flattenModules(nodes: CategoryNode[]): PublicModule[] {
  return nodes.flatMap((node) => [...(node.modules ?? []), ...flattenModules(node.children ?? [])]);
}

export interface Totals {
  modules: number;
  mcq: number;
  written: number;
}

export function totals(nodes: CategoryNode[]): Totals {
  const modules = flattenModules(nodes);
  return {
    modules: modules.length,
    mcq: modules.reduce((sum, module) => sum + (moduleTypeCount(module, "MCQ") ?? 0), 0),
    written: modules.reduce((sum, module) => sum + (moduleTypeCount(module, "WRITTEN") ?? 0), 0),
  };
}

export interface CategoryCard {
  id: string;
  titleEn: string;
  titleBn: string;
  href: string;
  /** The category in the MCQ list, or null when it has no MCQ exam. */
  mcqHref: string | null;
  /** The category in the written list, or null when it has no written exam. */
  writtenHref: string | null;
  moduleCount: number;
  /** Lowest price among the category's priced modules, or null when none is priced. */
  fromPrice: number | null;
}

/** Top-level categories only, so each card stands for a whole subject. */
export function categoryCards(nodes: CategoryNode[]): CategoryCard[] {
  return nodes
    .map((node) => {
      const modules = flattenModules([node]);
      const prices = modules
        .map((module) => module.price)
        .filter((price): price is number => typeof price === "number");
      const hasMcq = modules.some((module) => (moduleTypeCount(module, "MCQ") ?? 0) > 0);
      const hasWritten = modules.some((module) => (moduleTypeCount(module, "WRITTEN") ?? 0) > 0);
      return {
        id: node.id,
        titleEn: node.titleEn,
        titleBn: node.titleBn,
        href: categoryPath(hasMcq ? "mcq" : "written", node.id),
        mcqHref: hasMcq ? categoryPath("mcq", node.id) : null,
        writtenHref: hasWritten ? categoryPath("written", node.id) : null,
        moduleCount: modules.length,
        fromPrice: prices.length ? Math.min(...prices) : null,
      };
    })
    .filter((card) => card.moduleCount > 0);
}

export interface ModuleCard {
  module: PublicModule;
  href: string;
  mcq: number | null;
  written: number | null;
}

/** The modules to feature: the chosen ids in order, or the first modules in the tree. */
export function featuredModules(nodes: CategoryNode[], ids: readonly string[], limit: number): ModuleCard[] {
  const all = flattenModules(nodes);
  const picked = ids.length
    ? ids.flatMap((id) => all.filter((module) => module.id === id))
    : all;
  return picked.slice(0, limit).map((module) => {
    const mcq = moduleTypeCount(module, "MCQ");
    return {
      module,
      href: modulePath(mcq && mcq > 0 ? "mcq" : "written", module.id),
      mcq,
      written: moduleTypeCount(module, "WRITTEN"),
    };
  });
}

/** Soonest first, across both exam types, with a limit. */
export function nextExams(lists: UpcomingExam[][], limit: number): UpcomingExam[] {
  return lists
    .flat()
    .filter((exam) => exam.liveAt)
    .sort((a, b) => Date.parse(a.liveAt ?? "") - Date.parse(b.liveAt ?? ""))
    .slice(0, limit);
}
