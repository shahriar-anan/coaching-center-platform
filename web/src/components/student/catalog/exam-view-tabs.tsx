"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { cn } from "@/components/ui/cn";
import { useLocale } from "@/i18n/locale-context";
import type { ExamView } from "@/lib/api/exam/catalog";
import { parseExamView, withExamView } from "./exam-view";

/** The message key of a tab's empty title. All has no tab text of its own, so it falls back to the category list's title. */
export function examViewEmptyKey(view: ExamView): string {
  if (view === "FREE") return "catalog.examView.emptyFree";
  if (view === "LIVE") return "catalog.examView.emptyLive";
  if (view === "ARCHIVED") return "catalog.examView.emptyArchived";
  return "catalog.categoryExams.empty";
}

/** The tab and a setter that writes it to the URL, keeping the other query values (REQ-007, like the type filter). */
export function useExamViewParam(): [ExamView, (next: ExamView) => void] {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const value = parseExamView(searchParams.get("view"));
  const change = (next: ExamView) => {
    const query = withExamView(new URLSearchParams(searchParams.toString()), next).toString();
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
  };
  return [value, change];
}

/**
 * "All · Free · Live · Archived" as one segmented control, the look of the type filter. Pressed state is announced with
 * aria-pressed. It is a pill row; with `stacked` (the category sidebar) it is a vertical list from lg.
 */
export function ExamViewTabs({
  value,
  onChange,
  stacked = false,
}: {
  value: ExamView;
  onChange: (next: ExamView) => void;
  stacked?: boolean;
}) {
  const { t } = useLocale();
  const options: { value: ExamView; label: string }[] = [
    { value: "ALL", label: t("catalog.examView.all") },
    { value: "FREE", label: t("catalog.examView.free") },
    { value: "LIVE", label: t("catalog.examView.live") },
    { value: "ARCHIVED", label: t("catalog.examView.archived") },
  ];
  return (
    <div
      role="group"
      aria-label={t("catalog.examView.label")}
      className={cn(
        "inline-flex flex-wrap gap-1 rounded-full border border-border bg-card p-1 text-meta",
        stacked && "lg:flex lg:flex-col lg:rounded-card lg:p-2",
      )}
    >
      {options.map((option) => {
        const active = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            aria-pressed={active}
            onClick={() => onChange(option.value)}
            className={cn(
              "min-h-9 rounded-full px-3 font-medium transition-colors duration-150",
              stacked && "lg:min-h-10 lg:rounded-control lg:text-left",
              active ? "bg-action text-on-action" : "text-muted hover:text-text",
            )}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
