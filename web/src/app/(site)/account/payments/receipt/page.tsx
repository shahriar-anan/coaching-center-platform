"use client";

import { Suspense } from "react";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { useLocale } from "@/i18n/locale-context";
import { RequireStudent } from "@/lib/auth/guards";
import { useMyOrders } from "@/lib/api/exam/account";
import { useListParams } from "@/lib/url/list-params";
import { ReceiptView } from "../_components/receipt-view";

export default function ReceiptPage() {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <RequireStudent>
        <ReceiptRoute />
      </RequireStudent>
    </Suspense>
  );
}

function ReceiptRoute() {
  const { t } = useLocale();
  const [params] = useListParams({ order: "", page: 0 });
  const query = useMyOrders(params.page);

  if (query.isPending) {
    return (
      <>
        <PageHeader title={t("account.receipt.title")} />
        <Skeleton variant="card" count={2} />
      </>
    );
  }
  if (query.isError && !query.data) {
    return (
      <>
        <PageHeader title={t("account.receipt.title")} />
        <ErrorState onRetry={() => void query.refetch()} />
      </>
    );
  }
  const order = query.data?.content.find((row) => row.orderId === params.order && row.status === "PAID");
  if (!order) {
    return (
      <>
        <PageHeader title={t("account.receipt.title")} />
        <EmptyState title={t("account.receipt.notFound")} />
      </>
    );
  }
  return (
    <>
      <PageHeader title={t("account.receipt.title")} />
      <ReceiptView order={order} />
    </>
  );
}
