"use client";

import { Suspense } from "react";
import { useLocale } from "@/i18n/locale-context";
import { RequireStaff } from "@/lib/auth/guards";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { CategoryTree } from "@/components/staff/catalog/category-tree";

const AUTHORS = ["MASTER_ADMIN", "SYSTEM_ADMIN"] as const;

function CategoriesPage() {
  const { t } = useLocale();
  return (
    <RequireStaff roles={[...AUTHORS]}>
      <PageHeader
        title={t("staffCatalog.categories.title")}
        breadcrumbs={[{ label: t("staffCatalog.nav.home"), href: "/staff" }, { label: t("staffCatalog.categories.title") }]}
        actions={<Button href="/staff/categories/new">{t("staffCatalog.categories.new")}</Button>}
      />
      <CategoryTree />
    </RequireStaff>
  );
}

export default function Page() {
  return (
    <Suspense fallback={<Skeleton variant="card" />}>
      <CategoriesPage />
    </Suspense>
  );
}
