"use client";

import { Suspense, useState } from "react";
import { useCategoryExams } from "@/lib/api/exam/catalog";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Skeleton } from "@/components/ui/skeleton";
import { Pill } from "@/components/ui/pill";
import { doorType, type Door } from "./catalog-helpers";
import { CatalogExamRow } from "./exam-row";
import { ExamViewTabs, examViewEmptyKey, useExamViewParam } from "./exam-view-tabs";

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
/**
 * The exams of one category, below its subcategory cards (plan Q6, Q7, S2.1). A switch chooses between the whole branch,
 * which is the default, and the category's own exams; it is shown only when the category has subcategories (REQ-008). The
 * tabs (REQ-007) choose All, Free, Live or Archived from the URL. A category with nothing in it and no subcategory shows the
 * coming-soon note (decision D-b). From lg the tabs and the switch are a left column and the list sits beside it; below lg
 * they are one row above the list.
 */
export function CategoryExams(props: { categoryId: string; door: Door; hasChildren: boolean }) {
  return (
    // The tab reads the URL, so the list sits inside a Suspense boundary for the static build (as the packages page does).
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <CategoryExamsList {...props} />
    </Suspense>
  );
}

function CategoryExamsList({ categoryId, door, hasChildren }: { categoryId: string; door: Door; hasChildren: boolean }) {
  const { t } = useLocale();
  // Off by default: the category's own exams. Without subcategories the branch is the category itself, so the switch is hidden
  // and the value makes no difference.
  const [branch, setBranch] = useState(false);
  const [view, setView] = useExamViewParam();
  const query = useCategoryExams(categoryId, doorType(door), branch, view);
  const rows = query.data?.pages.flatMap((page) => page.content) ?? [];

  if (query.isError && !query.data) {
    return <ErrorState error={query.error} onRetry={() => void query.refetch()} />;
  }
  const empty = !query.isPending && rows.length === 0;
  // Nothing to show, here or below: the coming-soon note (D-b). Subcategories alone are shown above, so an empty
  // list with subcategories gets a plain empty line instead. A tab that is empty says so (below), not the coming-soon note.
  if (empty && !hasChildren && view === "ALL") {
    return <ComingSoonCard />;
  }
  const emptyText =
    view === "ALL"
      ? { title: t("catalog.categoryExams.empty"), body: t("catalog.categoryExams.emptyBody") }
      : { title: t(examViewEmptyKey(view)), body: t("catalog.examView.emptyHint") };

  // The tabs stay on screen while a new list loads, so the tab that was clicked keeps its focus.
  return (
    <section className="grid gap-3 lg:grid-cols-[16rem_minmax(0,1fr)] lg:items-start lg:gap-x-6">
      <h2 className="sr-only">{t("catalog.categoryExams.title")}</h2>
      {/* One toolbar row: the tabs on the left, the switch on the right. On a phone the switch wraps under the tabs; from lg the tabs stack above the switch in the left column. */}
      <div className="lg:sticky lg:top-20 lg:col-start-1 lg:row-start-1">
        <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 lg:flex-col lg:items-stretch lg:gap-3">
          <ExamViewTabs value={view} onChange={setView} stacked />
          {hasChildren ? (
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
          ) : null}
        </div>
      </div>

      {/* The list beside the tabs from lg. Its own gap matches the section's, so the spacing is the same on a phone. */}
      <div className="grid min-w-0 gap-3 lg:col-start-2 lg:row-start-1">
        {query.isPending ? (
          <Skeleton variant="card" count={2} />
        ) : empty ? (
          <EmptyState title={emptyText.title} body={emptyText.body} />
        ) : (
          // Two cards per row from lg, one below it (the same gap both ways), so the list needs less scrolling on a desktop.
          <ul className="grid gap-2 lg:grid-cols-2">
            {rows.map((exam) => (
              <li key={exam.id} className="min-w-0">
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
      </div>
    </section>
  );
}
