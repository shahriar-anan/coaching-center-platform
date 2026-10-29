"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { cn } from "@/components/ui/cn";
import { useLocale } from "@/i18n/locale-context";
import type { ExamType } from "@/lib/api/exam/types";

/** The exam type a page shows, or null for All. The value lives in the `type` query (`?type=MCQ` or `?type=WRITTEN`). */
export type TypeFilterValue = ExamType | null;

/** Reads `?type=`. Any other value is All, so a stale or typed-in link never hides rows by accident. */
export function parseTypeFilter(value: string | null): TypeFilterValue {
  return value === "MCQ" || value === "WRITTEN" ? value : null;
}

/**
 * The rows of one type. With no type, every row. `examTypeOf` says where a row's type is. A row whose type is not known yet
 * (its title still loading) is left out of a typed view and shown under All.
 */
export function filterByType<T>(
  rows: T[],
  type: TypeFilterValue,
  examTypeOf: (row: T) => ExamType | null | undefined,
): T[] {
  return type === null ? rows : rows.filter((row) => examTypeOf(row) === type);
}

/** The filter and a setter that writes it to the URL, keeping the other query values (Phase 4, A1 and A3). */
export function useTypeFilterParam(): [TypeFilterValue, (next: TypeFilterValue) => void] {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const value = parseTypeFilter(searchParams.get("type"));
  const change = (next: TypeFilterValue) => {
    const params = new URLSearchParams(searchParams.toString());
    if (next) {
      params.set("type", next);
    } else {
      params.delete("type");
    }
    const query = params.toString();
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
  };
  return [value, change];
}

/** The message key of a type's name, for empty text ("No MCQ exams yet"). */
export function typeLabelKey(type: ExamType): "catalog.typeFilter.mcq" | "catalog.typeFilter.written" {
  return type === "MCQ" ? "catalog.typeFilter.mcq" : "catalog.typeFilter.written";
}

/** "All · MCQ · Written" as one segmented control. Pressed state is announced with aria-pressed. */
export function TypeFilter({ value, onChange }: { value: TypeFilterValue; onChange: (next: TypeFilterValue) => void }) {
  const { t } = useLocale();
  const options: { value: TypeFilterValue; label: string }[] = [
    { value: null, label: t("catalog.typeFilter.all") },
    { value: "MCQ", label: t("catalog.typeFilter.mcq") },
    { value: "WRITTEN", label: t("catalog.typeFilter.written") },
  ];
  return (
    <div
      role="group"
      aria-label={t("catalog.typeFilter.label")}
      className="inline-flex flex-wrap gap-1 rounded-full border border-border bg-card p-1 text-meta"
    >
      {options.map((option) => {
        const active = option.value === value;
        return (
          <button
            key={option.value ?? "all"}
            type="button"
            aria-pressed={active}
            onClick={() => onChange(option.value)}
            className={cn(
              "min-h-9 rounded-full px-3 font-medium transition-colors duration-150",
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
