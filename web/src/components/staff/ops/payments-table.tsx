"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { useLocale } from "@/i18n/locale-context";
import { CopyField } from "@/components/ui/copy-field";
import { Pill } from "@/components/ui/pill";
import { Table, type Column } from "@/components/ui/table";
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

  const columns: Column<StaffOrderRow>[] = [
    {
      key: "time",
      header: t("staffOps.payments.columns.time"),
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
      cell: (row) => <span className="font-latin tabular-nums">{row.studentPhone}</span>,
    },
    {
      key: "product",
      header: t("staffOps.payments.columns.product"),
      cell: (row) => <ProductName row={row} />,
    },
    {
      key: "scope",
      header: t("staffOps.payments.columns.scope"),
      cell: (row) => t(`staffOps.payments.scope.${scopeKey(row.scope)}`),
    },
    {
      key: "amount",
      header: t("staffOps.payments.columns.amount"),
      align: "end",
      cell: (row) => format.formatMoney(row.amount),
    },
    {
      key: "method",
      header: t("staffOps.payments.columns.method"),
      cell: (row) => row.paymentChannel ?? "—",
    },
    {
      key: "status",
      header: t("staffOps.payments.columns.status"),
      cell: (row) => {
        const key = paymentStatusKey(row.status);
        return <Pill tone={paymentStatusTone(key)}>{t(`staffOps.payments.status.${key}`)}</Pill>;
      },
    },
    {
      key: "access",
      header: t("staffOps.payments.columns.access"),
      cell: (row) => t(`staffOps.payments.access.${accessCopyKey(row)}`),
    },
    {
      key: "tranId",
      header: t("staffOps.payments.columns.tranId"),
      cell: (row) => (
        <div className="grid gap-1">
          <CopyField value={row.tranId} label={t("staffOps.payments.copyTranId")} />
          <Link
            href={`/staff/payments/${encodeURIComponent(row.tranId)}`}
            className="text-sm text-action underline-offset-4 hover:underline"
          >
            {t("staffOps.payments.openDetail")}
          </Link>
        </div>
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
  if (row.scope === "EXAM" && row.examId) {
    return (
      <span>
        {pickTitle({ titleEn: row.examTitleEn ?? "", titleBn: row.examTitleBn ?? "" })}
      </span>
    );
  }
  return <span>{pickTitle({ titleEn: row.moduleTitleEn, titleBn: row.moduleTitleBn })}</span>;
}
