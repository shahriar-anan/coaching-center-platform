"use client";

import { Suspense } from "react";
import { useLocale } from "@/i18n/locale-context";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { FinancePage } from "@/components/staff/finance/finance-page";
import { RequireStaff } from "@/lib/auth/guards";

/** What the staff roles other than the Master Admin see here. The API refuses them too. */
function FinanceAccessMessage() {
  const { t } = useLocale();
  return <EmptyState title={t("finance.access.title")} body={t("finance.access.body")} />;
}

export default function StaffFinancePage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={3} />}>
      <RequireStaff roles={["MASTER_ADMIN"]} notFound={<FinanceAccessMessage />}>
        <FinancePage />
      </RequireStaff>
    </Suspense>
  );
}
