"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Check, Copy } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { RequireStudent } from "@/lib/auth/guards";
import { isNotDeployed, useMyOrders, type MyOrder, type PaymentStatus } from "@/lib/api/exam/account";
import { useListParams } from "@/lib/url/list-params";
import { formatDateTime, formatMoney, pickTitle } from "@/lib/format";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { IconButton } from "@/components/ui/icon-button";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { Pill, type PillTone } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";

const STATUS_TONE: Record<PaymentStatus, PillTone> = {
  PENDING: "warn",
  SUCCESS: "ok",
  FAILED: "danger",
  CANCELLED: "muted",
};

export default function PaymentsPage() {
  return (
    <RequireStudent>
      <PaymentsView />
    </RequireStudent>
  );
}

function TranCell({ value }: { value: string | null }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const [copied, setCopied] = useState(false);
  if (!value) {
    return <span className="text-muted">—</span>;
  }
  async function copy() {
    try {
      await navigator.clipboard.writeText(value ?? "");
      setCopied(true);
      toast({ title: t("account.payments.copied"), tone: "info" });
    } catch {
      /* clipboard blocked: the value stays visible */
    }
  }
  return (
    <span className="inline-flex items-center gap-1">
      <code className="font-latin text-[13px] tabular-nums break-all">{value}</code>
      <IconButton
        label={t("common.actions.copy")}
        className="size-9"
        onClick={copy}
        icon={copied ? <Check aria-hidden="true" className="size-4" /> : <Copy aria-hidden="true" className="size-4" />}
      />
    </span>
  );
}

function PaymentsView() {
  const { t, locale } = useLocale();
  const router = useRouter();
  const [params, setParams] = useListParams({ page: 0 });
  const query = useMyOrders(params.page);
  const rows = query.data?.content ?? [];

  const statusWord = (status: PaymentStatus) =>
    ({
      PENDING: t("account.payments.statusPending"),
      SUCCESS: t("account.payments.statusSuccess"),
      FAILED: t("account.payments.statusFailed"),
      CANCELLED: t("account.payments.statusCancelled"),
    })[status];

  const columns: Column<MyOrder>[] = [
    {
      key: "product",
      header: t("account.payments.column.product"),
      mobileLabel: t("account.payments.column.product"),
      cell: (row) => (
        <span className="grid gap-0.5">
          <span className="font-semibold">{pickTitle({ titleEn: row.productTitleEn, titleBn: row.productTitleBn }, locale)}</span>
          <span className="text-sm text-muted">
            {row.kind === "MODULE" ? t("account.payments.kindModule") : t("account.payments.kindExam")}
            {row.method ? ` · ${t("account.payments.method")}: ${row.method}` : ""}
          </span>
        </span>
      ),
    },
    {
      key: "status",
      header: t("account.payments.column.status"),
      mobileLabel: t("account.payments.column.status"),
      cell: (row) => <Pill tone={STATUS_TONE[row.status]}>{statusWord(row.status)}</Pill>,
    },
    {
      key: "amount",
      header: t("account.payments.column.amount"),
      mobileLabel: t("account.payments.column.amount"),
      align: "end",
      cell: (row) => <span className="tabular-nums">{formatMoney(row.amount, locale)}</span>,
    },
    {
      key: "date",
      header: t("account.payments.column.date"),
      mobileLabel: t("account.payments.column.date"),
      cell: (row) => <span className="text-sm">{formatDateTime(row.createdAt, locale)}</span>,
    },
    {
      key: "tran",
      header: t("account.payments.tranId"),
      mobileLabel: t("account.payments.tranId"),
      cell: (row) => <TranCell value={row.tranId} />,
    },
  ];

  function openRow(row: MyOrder) {
    if (row.status === "PENDING" && row.tranId) {
      router.push(`/purchase/result?tranId=${encodeURIComponent(row.tranId)}`);
    }
  }

  return (
    <>
      <PageHeader title={t("account.payments.title")} />
      {query.isError && rows.length > 0 ? (
        <div className="mb-4">
          <ErrorState onRetry={() => void query.refetch()} />
        </div>
      ) : null}

      {query.isPending ? (
        <Skeleton variant="rows" count={4} />
      ) : query.isError && isNotDeployed(query.error) ? (
        <EmptyState title={t("account.payments.notYet")} />
      ) : query.isError ? (
        <ErrorState onRetry={() => void query.refetch()} />
      ) : (
        <div className="grid gap-4">
          <Table
            columns={columns}
            rows={rows}
            rowKey={(row) => row.orderId}
            onRowClick={openRow}
            empty={<EmptyState title={t("account.payments.empty")} />}
          />
          <Pagination
            page={params.page}
            totalPages={query.data?.totalPages ?? 0}
            onPage={(page) => setParams({ page })}
          />
        </div>
      )}
    </>
  );
}
