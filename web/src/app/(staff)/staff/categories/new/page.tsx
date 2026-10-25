"use client";

import { Suspense } from "react";
import { useLocale } from "@/i18n/locale-context";
import { RequireStaff } from "@/lib/auth/guards";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { CategoryForm } from "@/components/staff/catalog/category-form";

function NewCategoryPage() {
  const { t } = useLocale();
  return (
    <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
      <PageHeader
        title={t("staffCatalog.categoryForm.newTitle")}
        breadcrumbs={[
          { label: t("staffCatalog.nav.home"), href: "/staff" },
          { label: t("staffCatalog.categories.title"), href: "/staff/categories" },
          { label: t("staffCatalog.categoryForm.newTitle") },
        ]}
      />
      <CategoryForm />
    </RequireStaff>
  );
}

export default function Page() {
  return (
    <Suspense fallback={<Skeleton variant="card" />}>
      <NewCategoryPage />
    </Suspense>
  );
}
