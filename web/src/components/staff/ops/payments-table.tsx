"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { useLocale } from "@/i18n/locale-context";
import { Pill } from "@/components/ui/pill";
import { RowMenu } from "@/components/ui/row-menu";
import { Table, type Column } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { useFormat } from "@/lib/format/use-format";
import type { StaffOrderRow } from "@/lib/api/exam/staff-ops";
import {
  accessCopyKey,
  paymentStatusKey,
  paymentStatusTone,
  scopeKey,
} from "./ops-copy";

/**
 * The payments list. Read only: no cell changes an order. Six columns on a wide screen; below 640 px the
 * Table shows each row as a card, with the same labels.
 */
export function PaymentsTable({
  rows,
  loading,
  empty,
}: {
  rows: StaffOrderRow[];
  loading?: boolean;
  empty?: ReactNode;
}) {
  const { t } = useLocale();
  const format = useFormat();
  const { toast } = useToast();

  async function copyTranId(tranId: string) {
    try {
      await navigator.clipboard.writeText(tranId);
      toast({ title: t("common.actions.copied"), tone: "ok" });
    } catch {
      /* clipboard blocked: the id is still shown in the row */
    }
  }

  const columns: Column<StaffOrderRow>[] = [
    {
      key: "date",
      header: t("staffOps.payments.columns.date"),
      mobileLabel: t("staffOps.payments.columns.date"),
      cell: (row) => (
        <span className="grid gap-0.5">
          <span>{format.formatDateTime(row.createdAt)}</span>
          {row.status === "SUCCEEDED" && row.paidAt ? (
            <span className="text-meta text-muted">
              {t("staffOps.payments.paidLine", { time: format.formatDateTime(row.paidAt) })}
            </span>
          ) : null}
          {row.receiptNo ? <code className="break-all font-latin text-meta">{row.receiptNo}</code> : null}
        </span>
      ),
    },
    {
      key: "student",
      header: t("staffOps.payments.columns.student"),
      mobileLabel: t("staffOps.payments.columns.student"),
      cell: (row) => (
        <span className="grid gap-0.5">
          <Link
            href={`/staff/students/${row.studentId}`}
            className="font-medium text-text underline-offset-4 hover:underline"
          >
            {row.studentName ?? t("staffOps.payments.unnamed")}
          </Link>
          <span className="font-latin text-meta tabular-nums text-muted">{row.studentPhone}</span>
        </span>
      ),
    },
    {
      key: "product",
      header: t("staffOps.payments.columns.product"),
      mobileLabel: t("staffOps.payments.columns.product"),
      cell: (row) => <ProductName row={row} />,
    },
    {
      key: "amount",
      header: t("staffOps.payments.columns.amount"),
      mobileLabel: t("staffOps.payments.columns.amount"),
      align: "end",
      cell: (row) => (
        <span className="grid justify-items-end gap-0.5">
          <span className="font-medium tabular-nums">{format.formatMoney(row.amount)}</span>
          {row.paymentChannel ? <span className="text-meta text-muted">{row.paymentChannel}</span> : null}
          <span className="break-all font-latin text-meta tabular-nums text-muted">{row.tranId}</span>
        </span>
      ),
    },
    {
      key: "status",
      header: t("staffOps.payments.columns.status"),
      mobileLabel: t("staffOps.payments.columns.status"),
      cell: (row) => {
        const key = paymentStatusKey(row.status);
        return <Pill tone={paymentStatusTone(key)}>{t(`staffOps.payments.status.${key}`)}</Pill>;
      },
    },
    {
      key: "menu",
      header: "",
      mobileLabel: t("staffOps.payments.columns.actions"),
      align: "end",
      width: "w-12",
      cell: (row) => (
        <RowMenu
          label={t("staffOps.payments.rowMenu.label", { id: row.tranId })}
          items={[
            { label: t("staffOps.payments.openDetail"), href: `/staff/payments/${encodeURIComponent(row.tranId)}` },
            { label: t("staffOps.payments.rowMenu.openStudent"), href: `/staff/students/${row.studentId}` },
            { label: t("staffOps.payments.rowMenu.copyTranId"), onClick: () => void copyTranId(row.tranId) },
          ]}
        />
      ),
    },
  ];

  return (
    <Table
      columns={columns}
      rows={rows}
      rowKey={(row) => row.orderId}
      loading={loading}
      empty={empty}
    />
  );
}

/**
 * The product is the exam for an exam order, and the module for a module order. The title links to the
 * payment's detail page and wraps to at most two lines. One muted line under it gives the scope and the access.
 */
function ProductName({ row }: { row: StaffOrderRow }) {
  const { pickTitle } = useFormat();
  const { t } = useLocale();
  const title =
    row.scope === "EXAM" && row.examId
      ? pickTitle({ titleEn: row.examTitleEn ?? "", titleBn: row.examTitleBn ?? "" })
      : pickTitle({ titleEn: row.moduleTitleEn, titleBn: row.moduleTitleBn });
  const scope = t(`staffOps.payments.scope.${scopeKey(row.scope)}`);
  const access = t(`staffOps.payments.access.${accessCopyKey(row)}`);
  return (
    <span className="grid max-w-56 gap-0.5">
      <Link
        href={`/staff/payments/${encodeURIComponent(row.tranId)}`}
        className="font-medium text-text underline-offset-4 hover:underline"
      >
        <span className="line-clamp-2">{title}</span>
      </Link>
      <span className="text-meta text-muted">
        <span>{scope}</span> · <span>{access}</span>
      </span>
    </span>
  );
}
