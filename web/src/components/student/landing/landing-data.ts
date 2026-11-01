import type { CategoryNode, ExamType, PublicModule, UpcomingExam } from "@/lib/api/exam/catalog";
import {
  categoryPath,
  countBranchExams,
  countModules,
  modulePath,
  moduleTypeCount,
  typeDoor,
} from "@/components/student/catalog/catalog-helpers";
import { dhakaParts } from "@/lib/format";

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
  /** The category's page in the chosen type's door, so a card never opens the other type. */
  href: string;
  /** Exams of the chosen type in the category and the subcategories under it. */
  examCount: number;
  /** Packages that hold an exam of the chosen type, in the same branch. */
  moduleCount: number;
  /** Lowest price among the priced packages of the chosen type, or null when none is priced. */
  fromPrice: number | null;
}

/** The part of a category that belongs to one exam type: its packages and exams of that type, and the same for each subcategory. */
function ofType(node: CategoryNode, type: ExamType): CategoryNode {
  return {
    ...node,
    modules: (node.modules ?? []).filter((module) => (moduleTypeCount(module, type) ?? 0) > 0),
    exams: (node.exams ?? []).filter((exam) => exam.examType === type),
    children: (node.children ?? []).map((child) => ofType(child, type)),
  };
}

/**
 * Top-level categories for one exam type, so each card stands for a whole subject. A category shows when it has a package
 * or an exam of that type anywhere in its branch (a category with standalone exams only still shows); anything else is hidden.
 */
export function categoryCards(nodes: CategoryNode[], type: ExamType): CategoryCard[] {
  return [...nodes]
    .sort((a, b) => a.position - b.position)
    .map((node) => {
      const scoped = ofType(node, type);
      const modules = flattenModules([scoped]);
      const prices = modules
        .map((module) => module.price)
        .filter((price): price is number => typeof price === "number");
      return {
        id: node.id,
        titleEn: node.titleEn,
        titleBn: node.titleBn,
        href: categoryPath(typeDoor(type), node.id),
        examCount: countBranchExams(scoped),
        moduleCount: countModules(scoped),
        fromPrice: prices.length ? Math.min(...prices) : null,
      };
    })
    .filter((card) => card.examCount > 0 || card.moduleCount > 0);
}

export interface ModuleCard {
  module: PublicModule;
  href: string;
  mcq: number | null;
  written: number | null;
}

/**
 * The modules to feature. Chosen ids stand alone, in the order given. With none chosen, the popular packages come first, then the
 * rest in tree order. Each module appears once, and the list stops at the limit.
 */
export function featuredModules(nodes: CategoryNode[], ids: readonly string[], limit: number): ModuleCard[] {
  const all = flattenModules(nodes);
  const ranked = ids.length
    ? ids.flatMap((id) => all.filter((module) => module.id === id))
    : [...all.filter((module) => module.popular), ...all.filter((module) => !module.popular)];
  const picked = ranked.filter((module, index) => ranked.findIndex((other) => other.id === module.id) === index);
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

const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * Whole calendar days from today to the exam's date, counted in Dhaka: 0 is today, 1 is tomorrow, and a negative number is a day
 * that has passed. The clock's zone is not used, so a late evening in Dhaka is already tomorrow in UTC terms.
 */
export function daysUntil(liveAt: string, now: number): number {
  const exam = dhakaParts(liveAt);
  const today = dhakaParts(now);
  return Math.round((Date.UTC(exam.year, exam.month, exam.day) - Date.UTC(today.year, today.month, today.day)) / DAY_MS);
}
