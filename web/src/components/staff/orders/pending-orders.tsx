"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Pagination } from "@/components/ui/pagination";
import { SearchInput } from "@/components/ui/search-input";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useRole } from "@/lib/auth/guards";
import {
  useConfirmOrder,
  usePendingOrders,
  useRejectOrder,
  type PendingOrder,
} from "@/lib/api/exam/staff-admin";
import { adminErrorText } from "@/components/staff/access/admin-error";
import { ReasonDialog } from "@/components/staff/access/reason-dialog";

const PAGE_SIZE = 20;

type Decision = { order: PendingOrder; action: "confirm" | "reject" };

/**
 * Orders waiting for a decision (status INITIATED). Master Admin confirms or
 * marks them failed, each with a reason. System Admin sees the list and who can
 * decide. The gateway is checked by hand before the decision.
 */
export function PendingOrders() {
  const { t } = useLocale();
  const { formatMoney, formatRelative, pickTitle } = useFormat();
  const { toast } = useToast();
  const { isMasterAdmin } = useRole();
  const [page, setPage] = useState(0);
  const [tranId, setTranId] = useState("");
  const [decision, setDecision] = useState<Decision | null>(null);

  const orders = usePendingOrders({ page, size: PAGE_SIZE, tranId });
  const confirm = useConfirmOrder();
  const reject = useRejectOrder();

  function productTitle(order: PendingOrder) {
    if (order.scope === "EXAM") {
      return t("staffAdmin.orders.scopeExam", {
        title: pickTitle({ titleEn: order.examTitleEn, titleBn: order.examTitleBn }),
      });
    }
    return t("staffAdmin.orders.scopeModule", {
      title: pickTitle({ titleEn: order.moduleTitleEn, titleBn: order.moduleTitleBn }),
    });
  }

  const columns: Column<PendingOrder>[] = [
    {
      key: "tranId",
      header: t("staffAdmin.orders.columns.tranId"),
      cell: (row) => <span className="font-latin">{row.tranId}</span>,
    },
    {
      key: "student",
      header: t("staffAdmin.orders.columns.student"),
      cell: (row) => (
        <span className="grid">
          <span>{row.studentName}</span>
          <span className="text-meta text-muted">{row.studentPhone ?? "—"}</span>
        </span>
      ),
    },
    {
      key: "product",
      header: t("staffAdmin.orders.columns.product"),
      cell: productTitle,
    },
    {
      key: "amount",
      header: t("staffAdmin.orders.columns.amount"),
      cell: (row) => formatMoney(row.amount) ?? "—",
    },
    {
      key: "age",
      header: t("staffAdmin.orders.columns.age"),
      cell: (row) =>
        t("staffAdmin.orders.age", {
          age: formatRelative(Math.max(0, Date.now() - Date.parse(row.createdAt))),
        }),
    },
  ];
  if (isMasterAdmin) {
    columns.push({
      key: "action",
      header: t("staffAdmin.orders.columns.action"),
      align: "end",
      cell: (row) => (
        <span className="flex flex-wrap justify-end gap-2">
          <Button variant="secondary" size="sm" onClick={() => setDecision({ order: row, action: "confirm" })}>
            {t("staffAdmin.orders.confirm")}
          </Button>
          <Button variant="danger" size="sm" onClick={() => setDecision({ order: row, action: "reject" })}>
            {t("staffAdmin.orders.reject")}
          </Button>
        </span>
      ),
    });
  }

  const rows = orders.data?.content ?? [];
  const active = decision?.action ?? "confirm";
  const deciding = active === "confirm" ? confirm : reject;

  return (
    <section aria-labelledby="pending-orders-title" className="grid gap-4">
      <div className="grid gap-1">
        <h2 id="pending-orders-title" className="text-section font-semibold text-text">
          {t("staffAdmin.orders.title")}
        </h2>
        <p className="text-meta text-muted">{t("staffAdmin.orders.intro")}</p>
        {isMasterAdmin ? null : <p className="text-meta text-muted">{t("staffAdmin.orders.sysAdminNote")}</p>}
      </div>

      <div className="max-w-sm">
        <SearchInput
          id="pending-orders-search"
          value={tranId}
          placeholder={t("staffAdmin.orders.searchPlaceholder")}
          onChange={(value) => {
            setTranId(value);
            setPage(0);
          }}
        />
      </div>

      {orders.isError ? (
        <ErrorState message={t("staffAdmin.orders.loadError")} onRetry={() => void orders.refetch()} />
      ) : orders.isPending ? (
        <Skeleton variant="rows" count={4} />
      ) : rows.length === 0 ? (
        <EmptyState title={t("staffAdmin.orders.empty")} />
      ) : (
        <Table
          columns={columns}
          rows={rows}
          rowKey={(row) => row.orderId}
          loading={orders.isFetching}
        />
      )}

      {orders.data && orders.data.totalPages > 1 ? (
        <Pagination page={page} totalPages={orders.data.totalPages} onPage={setPage} />
      ) : null}

      <ReasonDialog
        open={decision !== null}
        onOpenChange={(open) => {
          if (!open) {
            setDecision(null);
          }
        }}
        title={
          active === "confirm" ? t("staffAdmin.orders.confirmTitle") : t("staffAdmin.orders.rejectTitle")
        }
        body={
          decision
            ? active === "confirm"
              ? t("staffAdmin.orders.confirmBody", { tranId: decision.order.tranId, name: decision.order.studentName })
              : t("staffAdmin.orders.rejectBody", { tranId: decision.order.tranId })
            : undefined
        }
        label={t("staffAdmin.orders.reason")}
        requiredMessage={t("staffAdmin.orders.reasonRequired")}
        confirmLabel={active === "confirm" ? t("staffAdmin.orders.confirm") : t("staffAdmin.orders.reject")}
        tone={active === "reject" ? "danger" : undefined}
        onConfirm={async (reason) => {
          const current = decision;
          if (!current) {
            return;
          }
          try {
            await deciding.mutateAsync({ orderId: current.order.orderId, reason });
            toast({ title: t("staffAdmin.orders.done"), tone: "ok" });
          } catch (e) {
            throw new Error(adminErrorText(e, t));
          }
        }}
      />
    </section>
  );
}
