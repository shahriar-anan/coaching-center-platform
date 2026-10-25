"use client";

import { Suspense } from "react";
import { useLocale } from "@/i18n/locale-context";
import { useRole } from "@/lib/auth/guards";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { ModuleList } from "@/components/staff/catalog/module-list";

function ModulesPage() {
  const { t } = useLocale();
  const role = useRole();
  return (
    <>
      <PageHeader
        title={t("staffCatalog.modules.title")}
        breadcrumbs={[{ label: t("staffCatalog.nav.home"), href: "/staff" }, { label: t("staffCatalog.modules.title") }]}
        actions={role.canAuthor ? <Button href="/staff/modules/new">{t("staffCatalog.modules.new")}</Button> : undefined}
      />
      <ModuleList />
    </>
  );
}

export default function Page() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <ModulesPage />
    </Suspense>
  );
}
