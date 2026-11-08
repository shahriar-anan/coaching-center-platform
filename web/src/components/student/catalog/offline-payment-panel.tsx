"use client";

import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { formatDate, formatMoney, formatNumber } from "@/lib/format";
import {
  downloadStudentInstallmentReceipt,
  useStudentInstallmentPlan,
  type StudentPayment,
  type StudentScheduleRow,
} from "@/lib/api/exam/student-installments";
import { isMissing, optionalAmount, optionalDate } from "@/lib/api/missing";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { ErrorState } from "@/components/ui/error-state";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/components/ui/toast";
import { isSettled, isVoided, methodLabelKey, scheduleStatePill } from "./offline-payment";

/**
 * The payment panel of an Offline purchases card (plan section 8): the plan's figures, its schedule with each row's state, and each
 * payment with a receipt to download. Only the student's own plan is read; the API answers 404 for anyone else's.
 */
export function OfflinePaymentPanel({
  open,
  onOpenChange,
  planId,
  itemTitle,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  planId: string;
  itemTitle: string;
}) {
  const { t } = useLocale();
  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("account.payments.offline.panel.title")}
      description={itemTitle || undefined}
      footer={
        <Button variant="secondary" onClick={() => onOpenChange(false)}>
          {t("common.actions.close")}
        </Button>
      }
    >
      <PanelBody planId={planId} />
    </Dialog>
  );
}

/** Reads the plan when the panel is open. The query is not made until then, so a closed card costs nothing. */
function PanelBody({ planId }: { planId: string }) {
  const { t, locale } = useLocale();
  const detail = useStudentInstallmentPlan(planId);
  if (detail.isPending) {
    return <Skeleton variant="rows" count={3} />;
  }
  if (detail.isError) {
    return <ErrorState error={detail.error} onRetry={() => void detail.refetch()} />;
  }
  if (!detail.data) {
    return <p className="text-meta text-muted">{t("account.payments.offline.panel.loadFailed")}</p>;
  }
  const plan = detail.data.plan;
  const schedule = detail.data.schedule ?? [];
  const payments = detail.data.payments ?? [];
  const nextDate = optionalDate(plan.nextDueOn);
  const settled = isSettled(plan);
  let next: string | null = null;
  if (nextDate) {
    next = formatDate(nextDate, locale);
  } else if (settled) {
    next = t("account.payments.offline.settled");
  }

  return (
    <div className="grid gap-5">
      <dl className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        <Figure label={t("account.payments.offline.panel.total")} value={formatMoney(plan.agreedTotal, locale)} />
        <Figure label={t("account.payments.offline.panel.paid")} value={formatMoney(plan.paid, locale)} />
        <Figure label={t("account.payments.offline.panel.due")} value={formatMoney(plan.due, locale)} />
        <Figure label={t("account.payments.offline.panel.next")} value={next} />
      </dl>

      <section className="grid gap-2">
        <h3 className="text-body font-semibold text-text">{t("account.payments.offline.panel.scheduleTitle")}</h3>
        {schedule.length ? (
          <ul className="grid gap-2">
            {schedule.map((row, index) => (
              <ScheduleItem key={row.seq ?? index} row={row} />
            ))}
          </ul>
        ) : null}
      </section>

      <section className="grid gap-2">
        <h3 className="text-body font-semibold text-text">{t("account.payments.offline.panel.paymentsTitle")}</h3>
        {payments.length ? (
          <ul className="grid gap-2">
            {payments.map((payment) => (
              <PaymentItem key={payment.id} payment={payment} />
            ))}
          </ul>
        ) : (
          <p className="text-meta text-muted">{t("account.payments.offline.panel.noPayments")}</p>
        )}
      </section>
    </div>
  );
}

function Figure({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="rounded-control border border-border bg-bg p-3">
      <dt className="text-meta text-muted">{label}</dt>
      <dd className="mt-0.5 text-body font-semibold tabular-nums text-text">{value ?? "—"}</dd>
    </div>
  );
}

/** One installment: its number and date, what it asks, what has been paid on it, and its state. */
function ScheduleItem({ row }: { row: StudentScheduleRow }) {
  const { t, locale } = useLocale();
  const amount = formatMoney(row.amount, locale);
  const date = optionalDate(row.dueOn);
  const paid = optionalAmount(row.paid);
  const pill = scheduleStatePill(row.state);
  return (
    <li className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 rounded-control border border-border bg-bg px-3 py-2.5">
      <div className="grid min-w-0 gap-0.5">
        <span className="text-body font-semibold text-text">
          {t("account.payments.offline.panel.installmentNo", { no: formatNumber(row.seq, locale) })}
        </span>
        {date ? <span className="text-meta text-muted">{formatDate(date, locale)}</span> : null}
      </div>
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-meta text-muted">
        {amount ? <span className="tabular-nums text-text">{amount}</span> : null}
        {paid && paid > 0 ? (
          <span className="tabular-nums">{t("account.payments.offline.paidLine", { amount: formatMoney(paid, locale) ?? "" })}</span>
        ) : null}
        {pill ? <Pill tone={pill.tone}>{t(pill.key)}</Pill> : null}
      </div>
    </li>
  );
}

/** One payment: its receipt number, date, method and amount, whether it was voided, and the receipt to download. */
function PaymentItem({ payment }: { payment: StudentPayment }) {
  const { t, locale } = useLocale();
  const { toast } = useToast();
  const [saving, setSaving] = useState(false);
  const voided = isVoided(payment);
  const amount = formatMoney(payment.amount, locale);
  const paidOn = optionalDate(payment.paidOn);
  const methodKey = methodLabelKey(payment.method);
  const voidedOn = voided && payment.voidedAt ? formatDate(payment.voidedAt, locale) : null;
  const installment =
    isMissing(payment.installmentNo) || isMissing(payment.installmentCount)
      ? null
      : t("account.payments.offline.panel.installmentOf", {
          no: formatNumber(payment.installmentNo, locale),
          count: formatNumber(payment.installmentCount, locale),
        });
  const details = [paidOn ? formatDate(paidOn, locale) : null, methodKey ? t(methodKey) : null, installment].filter(
    (part): part is string => Boolean(part),
  );

  async function download() {
    setSaving(true);
    try {
      // The file is named by its receipt number, never by a reference or a phone number.
      await downloadStudentInstallmentReceipt(payment.id, payment.receiptNo ?? "receipt");
    } catch {
      toast({ title: t("account.payments.offline.panel.downloadFailed"), tone: "danger" });
    } finally {
      setSaving(false);
    }
  }

  return (
    <li className="grid gap-2 rounded-control border border-border bg-bg p-3">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <p className="min-w-0 text-body font-semibold text-text">
          {payment.receiptNo ? t("account.payments.offline.panel.receipt", { number: payment.receiptNo }) : null}
        </p>
        {voided ? (
          <Pill tone="warn">{t("account.payments.offline.panel.voided")}</Pill>
        ) : (
          <Pill tone="ok">{t("account.payments.offline.panel.recorded")}</Pill>
        )}
      </div>
      {details.length ? <p className="text-meta text-muted">{details.join(" · ")}</p> : null}
      {voidedOn ? <p className="text-meta text-warn">{t("account.payments.offline.panel.voidedOn", { date: voidedOn })}</p> : null}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="tabular-nums text-body font-semibold text-text">{amount ?? ""}</span>
        <Button variant="secondary" loading={saving} onClick={() => void download()}>
          {t("account.payments.offline.panel.download")}
        </Button>
      </div>
    </li>
  );
}
