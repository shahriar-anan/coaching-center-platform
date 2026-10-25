"use client";

import { Suspense } from "react";
import { useParams } from "next/navigation";
import { useLocale } from "@/i18n/locale-context";
import { RequireStaff } from "@/lib/auth/guards";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { CategoryForm } from "@/components/staff/catalog/category-form";
import { useCategory } from "@/lib/api/exam/staff-catalog";

function EditCategoryPage() {
  const { t, locale } = useLocale();
  const { id } = useParams<{ id: string }>();
  const category = useCategory(id);
  const name = category.data ? (locale === "bn" ? category.data.titleBn : category.data.titleEn) : "";
  return (
    <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
      <PageHeader
        title={name || t("staffCatalog.categoryForm.editTitle")}
        breadcrumbs={[
          { label: t("staffCatalog.nav.home"), href: "/staff" },
          { label: t("staffCatalog.categories.title"), href: "/staff/categories" },
          { label: name || t("staffCatalog.categoryForm.editTitle") },
        ]}
      />
      <CategoryForm id={id} />
    </RequireStaff>
  );
}

export default function Page() {
  return (
    <Suspense fallback={<Skeleton variant="card" />}>
      <EditCategoryPage />
    </Suspense>
  );
}
