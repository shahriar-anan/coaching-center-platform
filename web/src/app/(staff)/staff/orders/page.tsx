"use client";

import { Suspense } from "react";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { PendingOrders } from "@/components/staff/orders/pending-orders";
import { RequireStaff } from "@/lib/auth/guards";
import { useLocale } from "@/i18n/locale-context";

export default function StaffOrdersPage() {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
        <OrdersPage />
      </RequireStaff>
    </Suspense>
  );
}

function OrdersPage() {
  const { t } = useLocale();

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("staffAdmin.orders.title")}
        breadcrumbs={[{ label: t("common.nav.home"), href: "/staff" }, { label: t("staffAdmin.orders.title") }]}
      />
      <PendingOrders />
    </div>
  );
}
