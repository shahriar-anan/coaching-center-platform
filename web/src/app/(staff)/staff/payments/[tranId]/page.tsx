"use client";

import { use } from "react";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { PaymentDetail } from "@/components/staff/ops/payment-detail";
import { RequireStaff, useRole } from "@/lib/auth/guards";
import { useStaffPaymentDetail } from "@/lib/api/exam/staff-ops";
import { useLocale } from "@/i18n/locale-context";

export default function StaffPaymentDetailPage({ params }: { params: Promise<{ tranId: string }> }) {
  const { tranId: raw } = use(params);
  return (
    <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
      <PaymentDetailView tranId={decodeURIComponent(raw)} />
    </RequireStaff>
  );
}

function PaymentDetailView({ tranId }: { tranId: string }) {
  const { t } = useLocale();
  const { isMasterAdmin } = useRole();
  const payment = useStaffPaymentDetail(tranId);

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("staffOps.paymentDetail.title")}
        breadcrumbs={[
          { label: t("common.staffNav.dashboard"), href: "/staff" },
          { label: t("staffOps.payments.title"), href: "/staff/payments" },
          { label: tranId },
        ]}
        actions={
          <Button variant="ghost" href="/staff/payments">
            {t("staffOps.paymentDetail.backToList")}
          </Button>
        }
      />

      {payment.isError ? (
        <ErrorState onRetry={() => payment.refetch()} />
      ) : payment.isPending ? (
        <Skeleton variant="card" />
      ) : payment.data ? (
        <PaymentDetail row={payment.data} canOpenRoster={isMasterAdmin} />
      ) : (
        <EmptyState title={t("staffOps.paymentDetail.notFound")} />
      )}
    </div>
  );
}
