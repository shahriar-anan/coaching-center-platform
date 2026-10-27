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

/** The payments list. Read only: no cell changes an order. */
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
      key: "time",
      header: t("staffOps.payments.columns.time"),
      width: "w-36",
      nowrap: true,
      cell: (row) => format.formatDateTime(row.createdAt),
    },
    {
      key: "student",
      header: t("staffOps.payments.columns.student"),
      cell: (row) => (
        <Link
          href={`/staff/students/${row.studentId}`}
          className="font-medium text-text underline-offset-4 hover:underline"
        >
          {row.studentName ?? t("staffOps.payments.unnamed")}
        </Link>
      ),
    },
    {
      key: "phone",
      header: t("staffOps.payments.columns.phone"),
      width: "w-32",
      nowrap: true,
      cell: (row) => <span className="font-latin tabular-nums">{row.studentPhone}</span>,
    },
    {
      key: "product",
      header: t("staffOps.payments.columns.product"),
      cell: (row) => <ProductName row={row} />,
    },
    {
      key: "amount",
      header: t("staffOps.payments.columns.amount"),
      align: "end",
      width: "w-24",
      nowrap: true,
      cell: (row) => format.formatMoney(row.amount),
    },
    {
      key: "method",
      header: t("staffOps.payments.columns.method"),
      width: "w-28",
      cell: (row) => row.paymentChannel ?? "—",
    },
    {
      key: "status",
      header: t("staffOps.payments.columns.status"),
      width: "w-24",
      nowrap: true,
      cell: (row) => {
        const key = paymentStatusKey(row.status);
        return <Pill tone={paymentStatusTone(key)}>{t(`staffOps.payments.status.${key}`)}</Pill>;
      },
    },
    {
      key: "tranId",
      header: t("staffOps.payments.columns.tranId"),
      width: "w-40",
      cell: (row) => <span className="break-all font-latin text-meta tabular-nums">{row.tranId}</span>,
    },
    {
      key: "menu",
      header: "",
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

/** The product is the exam for an exam order, and the module for a module order. */
function ProductName({ row }: { row: StaffOrderRow }) {
  const { pickTitle } = useFormat();
  const { t } = useLocale();
  const title =
    row.scope === "EXAM" && row.examId
      ? pickTitle({ titleEn: row.examTitleEn ?? "", titleBn: row.examTitleBn ?? "" })
      : pickTitle({ titleEn: row.moduleTitleEn, titleBn: row.moduleTitleBn });
  // The scope and what the order grants sit under the title, so the table needs one column less.
  return (
    <span className="grid gap-0.5">
      <span>{title}</span>
      <span className="text-meta text-muted">{t(`staffOps.payments.scope.${scopeKey(row.scope)}`)}</span>
      <span className="text-meta text-muted">{t(`staffOps.payments.access.${accessCopyKey(row)}`)}</span>
    </span>
  );
}
