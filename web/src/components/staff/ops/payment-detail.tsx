"use client";

import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { CopyField } from "@/components/ui/copy-field";
import { Pill } from "@/components/ui/pill";
import { Card } from "@/components/ui/card";
import { useFormat } from "@/lib/format/use-format";
import type { StaffOrderRow } from "@/lib/api/exam/staff-ops";
import { paymentStatusKey, paymentStatusTone, refundChipKey, scopeKey } from "./ops-copy";
import { RefundDialog } from "./refund-dialog";

/**
 * One payment's facts, the access line and the links. Master Admin alone gets the
 * link that opens a module's direct enrolment, and the button that records a refund made in the SSLCommerz merchant
 * panel (plan 5.3). There is no mark-paid action.
 */
export function PaymentDetail({
  row,
  canOpenRoster,
  canRefund,
}: {
  row: StaffOrderRow;
  canOpenRoster: boolean;
  canRefund: boolean;
}) {
  const { t } = useLocale();
  const format = useFormat();
  const [refundOpen, setRefundOpen] = useState(false);
  const statusKey = paymentStatusKey(row.status);
  const refundKey = refundChipKey(row);
  const isExam = row.scope === "EXAM" && row.examId !== null;
  const product = isExam
    ? format.pickTitle({ titleEn: row.examTitleEn ?? "", titleBn: row.examTitleBn ?? "" })
    : format.pickTitle({ titleEn: row.moduleTitleEn, titleBn: row.moduleTitleBn });
  const scope = t(`staffOps.payments.scope.${scopeKey(row.scope)}`);

  return (
    <div className="grid gap-4">
      <Card>
        <div className="flex flex-wrap items-center gap-3">
          <Pill tone={paymentStatusTone(statusKey)}>{t(`staffOps.payments.status.${statusKey}`)}</Pill>
          {refundKey ? <Pill tone="muted">{t(`staffOps.payments.refundChip.${refundKey}`)}</Pill> : null}
          <span className="text-meta text-muted">
            {t("staffOps.paymentDetail.updated")}: {format.formatDateTime(row.updatedAt)}
          </span>
        </div>
        <p className="mt-3 text-body text-text">
          {row.accessGranted
            ? t("staffOps.paymentDetail.accessGranted", { product, scope })
            : t("staffOps.paymentDetail.accessNone")}
        </p>
        {row.riskLevel === "1" ? (
          <p className="mt-3 text-body text-danger">{t("staffOps.paymentDetail.riskFlagged")}</p>
        ) : null}
      </Card>

      {row.refund ? (
        <Card>
          <h2 className="text-section font-bold">{t("staffOps.paymentDetail.refund.panelTitle")}</h2>
          <dl className="mt-3 grid gap-4 sm:grid-cols-2">
            <Fact
              label={t("staffOps.paymentDetail.refund.amount")}
              value={format.formatMoney(row.refund.amount) ?? "—"}
            />
            <Fact label={t("staffOps.paymentDetail.refund.at")} value={format.formatDateTime(row.refund.at)} />
            <Fact label={t("staffOps.paymentDetail.refund.by")} value={row.refund.by ?? "—"} />
            <Fact
              label={t("staffOps.paymentDetail.refund.access")}
              value={
                row.refund.accessRevoked
                  ? t("staffOps.paymentDetail.refund.accessRemoved")
                  : t("staffOps.paymentDetail.refund.accessKept")
              }
            />
            <div className="grid gap-1">
              <dt className="text-meta text-muted">{t("staffOps.paymentDetail.refund.reference")}</dt>
              <dd>
                {row.refund.reference ? (
                  <CopyField value={row.refund.reference} label={t("staffOps.paymentDetail.refund.copyReference")} />
                ) : (
                  <span className="text-muted">{t("staffOps.paymentDetail.refund.noReference")}</span>
                )}
              </dd>
            </div>
            <Fact label={t("staffOps.paymentDetail.refund.reason")} value={row.refund.reason} />
          </dl>
          <p className="mt-3 text-meta text-muted">
            {refundKey === "refunded"
              ? t("staffOps.paymentDetail.refund.fullNote")
              : t("staffOps.paymentDetail.refund.partlyNote")}
          </p>
        </Card>
      ) : null}

      <Card>
        <dl className="grid gap-4 sm:grid-cols-2">
          <Fact label={t("staffOps.payments.columns.time")} value={format.formatDateTime(row.createdAt)} />
          <Fact label={t("staffOps.payments.columns.amount")} value={format.formatMoney(row.amount) ?? "—"} />
          <Fact label={t("staffOps.payments.columns.method")} value={row.paymentChannel ?? "—"} />
          <Fact label={t("staffOps.paymentDetail.product")} value={`${product} · ${scope}`} />
          <Fact
            label={t("staffOps.payments.columns.student")}
            value={row.studentName ?? t("staffOps.payments.unnamed")}
          />
          <div className="grid gap-1">
            <dt className="text-meta text-muted">{t("staffOps.payments.columns.phone")}</dt>
            <dd>
              <CopyField value={row.studentPhone} label={t("staffOps.paymentDetail.copyPhone")} />
            </dd>
          </div>
          <div className="grid gap-1">
            <dt className="text-meta text-muted">{t("staffOps.payments.columns.tranId")}</dt>
            <dd>
              <CopyField value={row.tranId} label={t("staffOps.payments.copyTranId")} />
            </dd>
          </div>
          {row.valId ? (
            <div className="grid gap-1">
              <dt className="text-meta text-muted">{t("staffOps.paymentDetail.valId")}</dt>
              <dd>
                <CopyField value={row.valId} label={t("staffOps.paymentDetail.copyValId")} />
              </dd>
            </div>
          ) : null}
        </dl>
      </Card>

      <div className="flex flex-wrap gap-2">
        <Button variant="secondary" href={`/staff/students/${row.studentId}`}>
          {t("staffOps.paymentDetail.openStudent")}
        </Button>
        <Button variant="secondary" href={`/staff/modules/${row.moduleId}`}>
          {t("staffOps.paymentDetail.openModule")}
        </Button>
        {isExam && row.examId ? (
          <Button variant="secondary" href={`/staff/exams/${row.examId}`}>
            {t("staffOps.paymentDetail.openExam")}
          </Button>
        ) : null}
      </div>

      {canRefund && row.status === "SUCCEEDED" && !row.refund ? (
        <div className="grid gap-1">
          <Button variant="secondary" onClick={() => setRefundOpen(true)}>
            {t("staffOps.paymentDetail.refund.record")}
          </Button>
          <p className="text-meta text-muted">{t("staffOps.paymentDetail.refund.recordHint")}</p>
        </div>
      ) : null}
      {refundOpen ? <RefundDialog row={row} onClose={() => setRefundOpen(false)} /> : null}

      {canOpenRoster ? (
        <div className="grid gap-1">
          <Button variant="ghost" href={`/staff/modules/${row.moduleId}?tab=roster`}>
            {t("staffOps.paymentDetail.rosterLink")}
          </Button>
          <p className="text-meta text-muted">{t("staffOps.paymentDetail.rosterHint")}</p>
        </div>
      ) : null}
    </div>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid gap-1">
      <dt className="text-meta text-muted">{label}</dt>
      <dd className="text-text">{value}</dd>
    </div>
  );
}
