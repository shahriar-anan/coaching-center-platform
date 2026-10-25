"use client";

import Link from "next/link";
import { ChevronRight } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";

export type Crumb = { label: string; href?: string };

export function Breadcrumbs({ items }: { items: Crumb[] }) {
  const { t } = useLocale();
  return (
    <nav aria-label={t("common.breadcrumbs.label")}>
      <ol className="flex flex-wrap items-center gap-1 text-meta text-muted">
        {items.map((item, i) => {
          const last = i === items.length - 1;
          return (
            <li key={`${item.label}-${i}`} className="flex items-center gap-1">
              {item.href && !last ? (
                <Link href={item.href} prefetch className="hover:text-text hover:underline">
                  {item.label}
                </Link>
              ) : (
                <span aria-current={last ? "page" : undefined} className={last ? "text-text" : undefined}>
                  {item.label}
                </span>
              )}
              {last ? null : <ChevronRight aria-hidden="true" className="size-4" />}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
