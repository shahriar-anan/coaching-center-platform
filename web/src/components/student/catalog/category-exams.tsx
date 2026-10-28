"use client";

import { useState } from "react";
import { useCategoryExams } from "@/lib/api/exam/catalog";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Skeleton } from "@/components/ui/skeleton";
import { Pill } from "@/components/ui/pill";
import { doorType, type Door } from "./catalog-helpers";
import { CatalogExamRow } from "./exam-row";

/** A category, a door or a package with nothing to show yet. */
export function ComingSoonCard() {
  const { t } = useLocale();
  return (
    <div className="rounded-card border border-border bg-card p-4">
      <Pill tone="accent">{t("catalog.comingSoon")}</Pill>
      <p className="mt-2 text-muted">{t("catalog.comingSoonBody")}</p>
    </div>
  );
}

/**
 * The exams of one category, below its subcategory cards (plan Q6, Q7, S2.1). A switch chooses between the whole branch,
 * which is the default, and the category's own exams. A category with nothing in it and no subcategory shows the coming-soon
 * note (decision D-b).
 */
export function CategoryExams({ categoryId, door, hasChildren }: { categoryId: string; door: Door; hasChildren: boolean }) {
  const { t } = useLocale();
  const [branch, setBranch] = useState(true);
  const query = useCategoryExams(categoryId, doorType(door), branch);
  const rows = query.data?.pages.flatMap((page) => page.content) ?? [];

  if (query.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  if (query.isError && !query.data) {
    return <ErrorState onRetry={() => void query.refetch()} />;
  }
  // Nothing to show, here or below: the coming-soon note (D-b). Subcategories alone are shown above, so an empty
  // list with subcategories gets a plain empty line instead.
  if (rows.length === 0 && !hasChildren) {
    return <ComingSoonCard />;
  }

  return (
    <section className="grid gap-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-section font-bold">{t("catalog.categoryExams.title")}</h2>
        <label className="inline-flex min-h-11 cursor-pointer items-center gap-2 text-body text-muted">
          <input
            type="checkbox"
            role="switch"
            checked={branch}
            onChange={(event) => setBranch(event.target.checked)}
            className="size-4 accent-current"
          />
          {t("catalog.categoryExams.branch")}
        </label>
      </div>

      {rows.length === 0 ? (
        <EmptyState title={t("catalog.categoryExams.empty")} body={t("catalog.categoryExams.emptyBody")} />
      ) : (
        <ul className="grid gap-3">
          {rows.map((exam) => (
            <li key={exam.id}>
              <CatalogExamRow exam={exam} />
            </li>
          ))}
        </ul>
      )}

      {query.hasNextPage ? (
        <div className="flex justify-center">
          <Button variant="secondary" loading={query.isFetchingNextPage} onClick={() => void query.fetchNextPage()}>
            {t("catalog.categoryExams.more")}
          </Button>
        </div>
      ) : null}
    </section>
  );
}
