"use client";

import { useState } from "react";
import { useFavourites } from "@/lib/api/exam/favourites";
import { useLocale } from "@/i18n/locale-context";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs } from "@/components/ui/tabs";
import { CatalogExamRow } from "./exam-row";
import { PackageLinkCard } from "./exam-packages";
import { modulePath } from "./catalog-helpers";

type Tab = "exams" | "packages";

/**
 * The student's favourite exams and packages, on two tabs (plan S3.4). Each tab has its own empty state. A favourite is removed with
 * the same star as everywhere else.
 */
export function FavouritesPage() {
  const { t } = useLocale();
  const [tab, setTab] = useState<Tab>("exams");
  const favourites = useFavourites(true);
  const exams = favourites.data?.exams ?? [];
  const packages = favourites.data?.packages ?? [];
  const crumbs = [{ label: t("catalog.home"), href: "/" }, { label: t("catalog.favourites.title") }];

  if (favourites.isPending) {
    return (
      <>
        <PageHeader title={t("catalog.favourites.title")} breadcrumbs={crumbs} />
        <Skeleton variant="card" count={3} />
      </>
    );
  }
  if (favourites.isError && !favourites.data) {
    return (
      <>
        <PageHeader title={t("catalog.favourites.title")} breadcrumbs={crumbs} />
        <ErrorState onRetry={() => void favourites.refetch()} />
      </>
    );
  }

  return (
    <>
      <PageHeader title={t("catalog.favourites.title")} breadcrumbs={crumbs} />
      <Tabs
        ariaLabel={t("catalog.favourites.title")}
        tabs={[
          { value: "exams", label: t("catalog.favourites.tabExams"), badge: exams.length },
          { value: "packages", label: t("catalog.favourites.tabPackages"), badge: packages.length },
        ]}
        value={tab}
        onChange={(value) => setTab(value as Tab)}
      />

      <div className="mt-4" role="tabpanel">
        {tab === "exams" ? (
          exams.length === 0 ? (
            <EmptyState title={t("catalog.favourites.emptyExams")} body={t("catalog.favourites.emptyExamsBody")} />
          ) : (
            <ul className="grid gap-3">
              {exams.map((exam) => (
                <li key={exam.id}>
                  <CatalogExamRow exam={exam} />
                </li>
              ))}
            </ul>
          )
        ) : packages.length === 0 ? (
          <EmptyState title={t("catalog.favourites.emptyPackages")} body={t("catalog.favourites.emptyPackagesBody")} />
        ) : (
          <ul className="grid gap-3">
            {packages.map((pkg) => (
              <li key={pkg.id}>
                <PackageLinkCard
                  href={modulePath("mcq", pkg.id)}
                  pkg={pkg}
                  fallback={{ id: pkg.id, titleEn: pkg.titleEn, titleBn: pkg.titleBn }}
                  popular={pkg.popular}
                />
              </li>
            ))}
          </ul>
        )}
      </div>
    </>
  );
}
