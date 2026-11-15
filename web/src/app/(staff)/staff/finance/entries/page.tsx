"use client";

import { Suspense } from "react";
import { useLocale } from "@/i18n/locale-context";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";
import { FinanceEntriesPage } from "@/components/staff/finance/finance-entries-page";
import { RequireStaff } from "@/lib/auth/guards";

/** What the staff roles other than the Master Admin see here. The API refuses them too. */
function FinanceAccessMessage() {
  const { t } = useLocale();
  return <EmptyState title={t("finance.access.title")} body={t("finance.access.body")} />;
}

/** The entries behind the finance figures (the ledger). It reads its filter from the URL, so it sits inside a Suspense boundary. */
export default function StaffFinanceEntriesPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={3} />}>
      <RequireStaff roles={["MASTER_ADMIN"]} notFound={<FinanceAccessMessage />}>
        <FinanceEntriesPage />
      </RequireStaff>
    </Suspense>
  );
}
