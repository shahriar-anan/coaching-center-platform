"use client";

import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { CopyField } from "@/components/ui/copy-field";
import { Pill } from "@/components/ui/pill";
import { Card } from "@/components/ui/card";
import { useFormat } from "@/lib/format/use-format";
import type { StaffOrderRow } from "@/lib/api/exam/staff-ops";
import { paymentStatusKey, paymentStatusTone, scopeKey } from "./ops-copy";

/**
 * One payment's facts, the access line and the links. Master Admin alone gets the
 * link that opens a module's direct enrolment; there is no refund or mark-paid action.
 */
export function PaymentDetail({ row, canOpenRoster }: { row: StaffOrderRow; canOpenRoster: boolean }) {
  const { t } = useLocale();
  const format = useFormat();
  const statusKey = paymentStatusKey(row.status);
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
          <span className="text-sm text-muted">
            {t("staffOps.paymentDetail.updated")}: {format.formatDateTime(row.updatedAt)}
          </span>
        </div>
        <p className="mt-3 text-base text-text">
          {row.accessGranted
            ? t("staffOps.paymentDetail.accessGranted", { product, scope })
            : t("staffOps.paymentDetail.accessNone")}
        </p>
      </Card>

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
            <dt className="text-sm text-muted">{t("staffOps.payments.columns.phone")}</dt>
            <dd>
              <CopyField value={row.studentPhone} label={t("staffOps.paymentDetail.copyPhone")} />
            </dd>
          </div>
          <div className="grid gap-1">
            <dt className="text-sm text-muted">{t("staffOps.payments.columns.tranId")}</dt>
            <dd>
              <CopyField value={row.tranId} label={t("staffOps.payments.copyTranId")} />
            </dd>
          </div>
          {row.valId ? (
            <div className="grid gap-1">
              <dt className="text-sm text-muted">{t("staffOps.paymentDetail.valId")}</dt>
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

      {canOpenRoster ? (
        <div className="grid gap-1">
          <Button variant="ghost" href={`/staff/modules/${row.moduleId}?tab=roster`}>
            {t("staffOps.paymentDetail.rosterLink")}
          </Button>
          <p className="text-sm text-muted">{t("staffOps.paymentDetail.rosterHint")}</p>
        </div>
      ) : null}
    </div>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid gap-1">
      <dt className="text-sm text-muted">{label}</dt>
      <dd className="text-text">{value}</dd>
    </div>
  );
}
