"use client";

import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import type { CategoryTreeNode } from "@/lib/api/types";

export function CoursePlacePath({ nodes, courseId }: { nodes: CategoryTreeNode[]; courseId: string }) {
  const { t } = useLocale();
  const [language, setLanguage] = useState<"en" | "bn">("en");
  const steps = pathToCourse(nodes, courseId);
  if (!steps) {
    return null;
  }

  return (
    <div className="mt-4">
      <div className="flex gap-2">
        <button
          type="button"
          aria-pressed={language === "en"}
          className={`rounded-md px-3 py-1.5 text-sm ${language === "en" ? "bg-zinc-900 text-white" : "border border-zinc-300 text-zinc-700"}`}
          onClick={() => setLanguage("en")}
        >
          {t("courses.titleEn")}
        </button>
        <button
          type="button"
          aria-pressed={language === "bn"}
          className={`rounded-md px-3 py-1.5 text-sm ${language === "bn" ? "bg-zinc-900 text-white" : "border border-zinc-300 text-zinc-700"}`}
          onClick={() => setLanguage("bn")}
        >
          {t("courses.titleBn")}
        </button>
      </div>
      <ol aria-label={t("courses.stepCategory")} className="mt-4">
        {steps.map((step, index) => {
          const last = index === steps.length - 1;
          const label = last
            ? t("courses.thisCourse")
            : index === 0
              ? t("courses.stepCategory")
              : t("courses.subcategoryLevel", { level: index });
          const title = language === "bn" ? step.titleBn : step.titleEn;
          return (
            <li
              key={`${step.kind}-${step.id}`}
              className="relative pb-4 pl-6"
              style={{ marginLeft: `${index * 12}px` }}
            >
              <span
                className={`absolute left-0 top-1.5 h-2.5 w-2.5 rounded-full ${last ? "bg-zinc-900" : "bg-zinc-400"}`}
              />
              {last ? null : <span className="absolute bottom-0 left-1 top-4 w-px bg-zinc-300" />}
              <p className="text-xs font-medium uppercase tracking-wide text-zinc-500">{label}</p>
              <p className={`text-sm ${last ? "font-semibold text-zinc-900" : "text-zinc-800"}`}>{title}</p>
            </li>
          );
        })}
      </ol>
    </div>
  );
}

function pathToCourse(
  nodes: CategoryTreeNode[],
  courseId: string,
  trail: CategoryTreeNode[] = [],
): CategoryTreeNode[] | null {
  for (const node of nodes) {
    if (node.kind === "COURSE" && node.id === courseId) {
      return [...trail, node];
    }
    if (node.kind === "CATEGORY" && node.children?.length) {
      const found = pathToCourse(node.children, courseId, [...trail, node]);
      if (found) {
        return found;
      }
    }
  }
  return null;
}
