"use client";

import { useCallback } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import {
  useCategoryTree,
  usePackages,
  type ExamType,
  type PackageFilter,
  type PublicModule,
} from "@/lib/api/exam/catalog";
import { formatNumber, pickText, pickTitle } from "@/lib/format";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PackageCard } from "@/components/ui/package/package-card";
import { coverSrc } from "@/components/ui/package/package-cover";
import { PageHeader } from "@/components/ui/page-header";
import { SearchInput } from "@/components/ui/search-input";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { firstLine, modulePath, typeDoor, type Door } from "./catalog-helpers";
import { TypeFilter } from "./type-filter";
import { flattenCategories, parsePackageFilter, withPackageFilter } from "./packages-filter";

/** Indent for a subcategory in the select. Native options ignore padding, so non-breaking spaces stand in. */
const CATEGORY_INDENT = "    ";

const NO_FILTER: PackageFilter = { categoryId: null, examType: null, q: "" };

/**
 * One package in the grid. Its door is the type the list shows; with no type chosen, the first type the package holds
 * (the same rule as the landing page). The cover is a plain placeholder when the package holds both types.
 */
function PackageItem({ module, examType }: { module: PublicModule; examType: ExamType | null }) {
  const { locale } = useLocale();
  const mcq = module.mcqCount ?? 0;
  const written = module.writtenCount ?? 0;
  const door: Door = examType ? typeDoor(examType) : mcq > 0 ? "mcq" : "written";
  const type = mcq > 0 && written > 0 ? null : mcq > 0 ? "MCQ" : written > 0 ? "WRITTEN" : null;
  const summary = firstLine(pickText({ ...module }, "description", locale));
  return (
    <PackageCard
      href={modulePath(door, module.id)}
      cover={coverSrc(module.coverUrl)}
      type={type}
      title={pickTitle(module, locale)}
      summary={summary || null}
      examCount={mcq + written}
      price={module.price}
      pricing={module.pricing}
    />
  );
}

/**
 * The public package list with its filters (PKG-1). The category, type and search text live in the URL, so a filtered list can
 * be shared and Back returns to it.
 */
export function PackagesPage() {
  const { t, locale } = useLocale();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const filter = parsePackageFilter(searchParams);
  const active = filter.categoryId !== null || filter.examType !== null || filter.q !== "";

  // Replaces the URL rather than pushing a history entry for each change. Other query values are kept.
  const update = useCallback(
    (next: PackageFilter) => {
      const query = withPackageFilter(new URLSearchParams(searchParams.toString()), next).toString();
      router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
    },
    [pathname, router, searchParams],
  );

  const tree = useCategoryTree(null);
  const categories = flattenCategories(tree.data ?? []);
  const packages = usePackages(filter);
  const rows = packages.data?.pages.flatMap((page) => page.content) ?? [];
  const total = packages.data?.pages[0]?.totalElements ?? 0;

  const title = t("catalog.packages.title");
  const breadcrumbs = [{ label: t("catalog.home"), href: "/" }, { label: title }];

  return (
    <>
      <PageHeader title={title} breadcrumbs={breadcrumbs} />

      <div className="grid gap-3">
        <SearchInput
          id="packages-search"
          value={filter.q}
          onChange={(q) => update({ ...filter, q })}
          placeholder={t("catalog.packages.searchPlaceholder")}
        />
        <div className="flex flex-wrap items-end gap-3">
          <label className="grid min-w-0 flex-1 gap-1.5 text-meta font-medium text-muted sm:max-w-xs">
            {t("catalog.packages.category")}
            <Select
              value={filter.categoryId ?? ""}
              onChange={(event) => update({ ...filter, categoryId: event.target.value || null })}
            >
              <option value="">{t("catalog.packages.allCategories")}</option>
              {categories.map(({ node, depth }) => (
                <option key={node.id} value={node.id}>
                  {CATEGORY_INDENT.repeat(depth)}
                  {pickTitle(node, locale)}
                </option>
              ))}
            </Select>
          </label>
          <TypeFilter value={filter.examType} onChange={(examType) => update({ ...filter, examType })} />
        </div>
      </div>

      <div className="mt-5 grid gap-5">
        {packages.isPending ? (
          <Skeleton variant="card" count={4} />
        ) : packages.isError && !packages.data ? (
          <ErrorState error={packages.error} onRetry={() => void packages.refetch()} />
        ) : rows.length === 0 ? (
          <EmptyState
            title={active ? t("catalog.packages.empty") : t("catalog.packages.none")}
            body={active ? t("catalog.packages.emptyBody") : t("catalog.packages.noneBody")}
            action={
              active ? (
                <Button variant="secondary" onClick={() => update(NO_FILTER)}>
                  {t("catalog.packages.clear")}
                </Button>
              ) : undefined
            }
          />
        ) : (
          <>
            <p className="text-meta tabular-nums text-muted">
              {t("catalog.countModules", { n: formatNumber(total, locale) })}
            </p>
            <ul className="grid gap-4 sm:grid-cols-2">
              {rows.map((module) => (
                <li key={module.id} className="min-w-0">
                  <PackageItem module={module} examType={filter.examType} />
                </li>
              ))}
            </ul>
            {packages.hasNextPage ? (
              <div className="flex justify-center">
                <Button
                  variant="secondary"
                  loading={packages.isFetchingNextPage}
                  onClick={() => void packages.fetchNextPage()}
                >
                  {t("catalog.packages.more")}
                </Button>
              </div>
            ) : null}
          </>
        )}
      </div>
    </>
  );
}
