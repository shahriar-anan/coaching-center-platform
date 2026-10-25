"use client";

import { Suspense } from "react";
import { useLocale } from "@/i18n/locale-context";
import { RequireStaff } from "@/lib/auth/guards";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { ModuleDetails } from "@/components/staff/catalog/module-details";

function NewModulePage() {
  const { t } = useLocale();
  return (
    <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
      <PageHeader
        title={t("staffCatalog.moduleForm.newTitle")}
        breadcrumbs={[
          { label: t("staffCatalog.nav.home"), href: "/staff" },
          { label: t("staffCatalog.modules.title"), href: "/staff/modules" },
          { label: t("staffCatalog.moduleForm.newTitle") },
        ]}
      />
      <ModuleDetails />
    </RequireStaff>
  );
}

export default function Page() {
  return (
    <Suspense fallback={<Skeleton variant="card" />}>
      <NewModulePage />
    </Suspense>
  );
}
